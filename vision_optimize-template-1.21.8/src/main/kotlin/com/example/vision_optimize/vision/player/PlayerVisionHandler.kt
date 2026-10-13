package com.example.vision_optimize.vision.player

import com.example.vision_optimize.config.ModConfigManager
import com.example.vision_optimize.config.VisionLevel
import com.example.vision_optimize.network.PlayerVisionRequestC2SPacket
import com.example.vision_optimize.network.PlayerVisionResultS2CPacket
import com.example.vision_optimize.network.PlayerVisionWarningS2CPacket
import com.example.vision_optimize.network.PlayerVisionTellrawSuppressS2CPacket
import com.example.vision_optimize.util.ManosabaCharacters
import com.example.vision_optimize.util.ManosabaMagics
import com.example.vision_optimize.util.TextBuilder
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Server-side handler for player vision (人物幻视).
 * Manages cooldowns, FOV checks, discovery timers, third-party witnessing,
 * active charges, and the discovery reaction window.
 */
object PlayerVisionHandler {
    private val logger = LoggerFactory.getLogger("vision_optimize:player_vision")

    /** Cooldown end timestamps (epoch millis) per player name. */
    private val cooldowns: ConcurrentHashMap<String, Long> = ConcurrentHashMap()

    /** Active charge states keyed by caster name. */
    private val activeCharges: ConcurrentHashMap<String, ActiveCharge> = ConcurrentHashMap()

    /** Discovery reaction states keyed by caster name. */
    private val discoveryStates: ConcurrentHashMap<String, DiscoveryState> = ConcurrentHashMap()

    /** Witness tick accumulators keyed by "casterName:witnessName". */
    private val witnessTicks: ConcurrentHashMap<String, Int> = ConcurrentHashMap()

    /** Neck damage schedules keyed by target name. */
    private val neckDamageStates: ConcurrentHashMap<String, NeckDamageState> = ConcurrentHashMap()

    // ===== Charge Start / Cancel =====

    /**
     * Handle a charge start notification from a client.
     * Sends in-view warnings and third-party messages at the moment the key is pressed.
     */
    fun handleChargeStart(caster: ServerPlayer, targetName: String, wasInView: Boolean) {
        val config = ModConfigManager.get()
        if (!config.enabled) return
        if (ModConfigManager.isPlayerExcluded(caster.displayName!!.string)) return

        val server = caster.server ?: return
        val target = server.playerList.getPlayerByName(targetName) ?: return

        val casterName = caster.displayName!!.string

        // Determine caster's level for different warning messages
        val casterLevel = getPlayerVisionLevel(caster)
        val isL2OrL3 = casterLevel == VisionLevel.PLAYER_VISION_LEVEL_2 ||
                casterLevel == VisionLevel.PLAYER_VISION_LEVEL_3

        // Record active charge (L1 only; L2/L3 don't need charge tracking since no discovery)
        if (!isL2OrL3) {
            activeCharges[casterName] = ActiveCharge(
                casterName = casterName,
                targetName = target.displayName!!.string,
                startTick = server.tickCount,
                wasInView = wasInView
            )
        }

        if (wasInView && !isL2OrL3) {
            // L1: red action bar warning to target at charge start
            target.displayClientMessage(
                TextBuilder.playerVisionInViewWarning(resolveVisionName(caster)), true
            )
            // Immediately notify third-party witnesses (yellow raid text)
            sendThirdPartyRaidWarning(server, caster, target)
        }
        // L2/L3 warnings fire at completion (handleRequest), not at charge start
        // For out-of-view charges, third-party sneak messages are handled
        // periodically in tickActiveCharges() after the discoverTime threshold.

        logger.info(
            "Player vision charge start: {} -> {} | inView={}",
            caster.displayName!!.string, target.displayName!!.string, wasInView
        )
    }

    /**
     * Handle a charge cancel notification from a client.
     * Cleans up active charge and witness tracking.
     */
    fun handleChargeCancel(caster: ServerPlayer, targetName: String) {
        val casterName = caster.displayName!!.string
        activeCharges.remove(casterName)
        // Remove witness tick entries for this caster
        witnessTicks.keys.removeAll { it.startsWith("$casterName:") }
        logger.info("Player vision charge cancelled: {} -> {}", caster.displayName!!.string, targetName)
    }

    // ===== Main Request Handling =====

    /**
     * Handle a player vision request from a client (charge completed).
     */
    fun handleRequest(caster: ServerPlayer, packet: PlayerVisionRequestC2SPacket) {
        val config = ModConfigManager.get()
        if (!config.enabled) return
        if (ModConfigManager.isPlayerExcluded(caster.displayName!!.string)) return

        // Silenced casters can still perform vision: the target feels it, but the
        // caster receives no information (magic / hotbar). Suppression happens at the
        // success paths below (magic broadcast + result packet info).
        val silenced = caster.tags.contains("Silenced")

        val server = caster.server ?: return
        val target = server.playerList.getPlayerByName(packet.targetName) ?: return

        val casterName = caster.displayName!!.string
        val targetName = target.displayName!!.string

        // Determine effective player vision level (with scoreboard support)
        val playerVisionLevel = getPlayerVisionLevel(caster)
        if (playerVisionLevel == VisionLevel.NONE) return

        // Determine effective player vision level (with scoreboard support)
        val isLevel2Or3 = playerVisionLevel == VisionLevel.PLAYER_VISION_LEVEL_2 ||
                playerVisionLevel == VisionLevel.PLAYER_VISION_LEVEL_3

        // Cooldown check (L1 only; L2/L3 have no cooldown)
        if (!isLevel2Or3) {
            val cooldownEnd = cooldowns[casterName]
            if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
                val remainingSec = ((cooldownEnd - System.currentTimeMillis()) / 1000).toInt()
                caster.displayClientMessage(
                    Component.literal("人物幻视冷却还剩 ${remainingSec}秒")
                        .withStyle { it.withColor(0xFF5555.toInt()) },
                    true
                )
                return
            }
        }

        // Range check (L1: config box range; L2: 7-block; L3: 14-block spherical radius)
        if (!isInRange(caster, target, config, playerVisionLevel)) {
            // Out of range — cleanup active charge, no fail message
            activeCharges.remove(casterName)
            witnessTicks.keys.removeAll { it.startsWith("$casterName:") }
            return
        }

        // Check caster is facing toward target (L1 only; L2/L3 can select from list)
        if (!isLevel2Or3) {
            if (!isFacingTarget(caster, target, config.playerVisionRangeX + config.playerVisionRangeY + config.playerVisionRangeZ)) return
        }

        // Server-authoritative FOV check: is caster currently in target's view?
        val casterInTargetView = isInFov(target, caster)

        // Cleanup active charge tracking
        val activeCharge = activeCharges.remove(casterName)
        witnessTicks.keys.removeAll { it.startsWith("$casterName:") }

        // Determine result
        val result: PlayerVisionResult
        val resultType: Int

        if (isLevel2Or3) {
            // Level 2 & 3: always succeed, no discovery fail, no target chat message
            result = PlayerVisionResult.SUCCESS
            resultType = 2
            // Probabilistic action bar warnings to target at completion
            if (casterInTargetView) {
                if (packet.fromPKey) {
                    // P-key front: 60% red "<caster>好像默默的盯着你,眼神让你发寒"
                    if (kotlin.random.Random.nextInt(100) < 60) {
                        target.displayClientMessage(
                            TextBuilder.playerVisionPKeyWarningFront(resolveVisionName(caster)), true
                        )
                    }
                } else {
                    // V-key front: 70% red "<caster>用一种古怪的眼神盯着你"
                    if (kotlin.random.Random.nextInt(100) < 70) {
                        target.displayClientMessage(
                            TextBuilder.playerVisionVKeyWarningFront(resolveVisionName(caster)), true
                        )
                    }
                }
            } else {
                if (packet.fromPKey) {
                    // P-key behind: 45% yellow "你感到一道冰冷的视线盯着你..."
                    if (kotlin.random.Random.nextInt(100) < 45) {
                        target.displayClientMessage(
                            TextBuilder.playerVisionPKeyWarningBehind(), true
                        )
                    }
                } else {
                    // V-key behind: 25% yellow "你感到背后传来喘息的热气..."
                    if (kotlin.random.Random.nextInt(100) < 25) {
                        target.displayClientMessage(
                            TextBuilder.playerVisionVKeyWarningBehind(), true
                        )
                    }
                }
            }
            // Show tag only to the caster (private); silenced casters get no info
            if (!silenced) {
                val tagText = getPlayerTag(target)
                val tagColor = getPlayerTagColor(target)
                val tagMsg = TextBuilder.playerVisionBroadcast(resolveVisionName(target), tagText, tagColor)
                caster.sendSystemMessage(tagMsg)
            }
            applyCooldown(caster.displayName!!.string, config.playerVisionSuccessCooldown)
        } else if (casterInTargetView && !packet.wasInView) {
            // Out-of-view charge completed, but target now sees caster → DISCOVERY!
            result = PlayerVisionResult.FAIL_DISCOVERED
            resultType = 1

            // Enter discovery reaction state
            val reactionTicks = config.playerVisionReactionTime * 20
            discoveryStates[casterName] = DiscoveryState(
                casterName = casterName,
                targetName = targetName,
                startTick = server.tickCount,
                reactionTicks = reactionTicks
            )

            // Send discovery messages
            target.displayClientMessage(TextBuilder.playerVisionDiscovered(resolveVisionName(caster)), true)
            caster.displayClientMessage(TextBuilder.playerVisionDiscoveredCaster(resolveVisionName(target)), true)

            // Suppress tellraw for both during reaction window
            val suppressPacket = PlayerVisionTellrawSuppressS2CPacket(config.playerVisionReactionTime)
            ServerPlayNetworking.send(caster, suppressPacket)
            ServerPlayNetworking.send(target, suppressPacket)

            // Note: fail cooldown is NOT applied yet — it will be applied on reaction timeout or success
        } else if (casterInTargetView && packet.wasInView) {
            // In-view charge completed → SUCCESS (target sees random red text)
            result = PlayerVisionResult.SUCCESS
            resultType = 2
            applyPlayerVisionSuccess(caster, target, config, msgType = SuccessMessageType.IN_VIEW)
        } else if (!casterInTargetView && !packet.wasInView) {
            // Out-of-view charge completed, target still doesn't see caster → SUCCESS (sneak)
            result = PlayerVisionResult.SUCCESS
            resultType = 2
            applyPlayerVisionSuccess(caster, target, config)
        } else {
            // !casterInTargetView && packet.wasInView — shouldn't happen normally
            // (was charging in-view but target no longer sees caster)
            result = PlayerVisionResult.SUCCESS
            resultType = 2
            applyPlayerVisionSuccess(caster, target, config)
        }

        // Send result to caster (with hotbar items for L2/L3)
        val hotbarItemNames = when {
            result == PlayerVisionResult.SUCCESS && playerVisionLevel == VisionLevel.PLAYER_VISION_LEVEL_3 -> {
                (0..2).map { itemDataString(target.inventory.getItem(it), server.registryAccess()) }
            }
            result == PlayerVisionResult.SUCCESS && playerVisionLevel == VisionLevel.PLAYER_VISION_LEVEL_2 -> {
                listOf(itemDataString(target.inventory.getItem(0), server.registryAccess()))
            }
            else -> emptyList()
        }
        val levelInt = when (playerVisionLevel) {
            VisionLevel.PLAYER_VISION_LEVEL_3 -> 3
            VisionLevel.PLAYER_VISION_LEVEL_2 -> 2
            else -> 1
        }
        val resultPacket = PlayerVisionResultS2CPacket(
            success = result == PlayerVisionResult.SUCCESS,
            resultType = resultType,
            targetName = target.displayName!!.string,
            tagText = if (!silenced && result == PlayerVisionResult.SUCCESS) getPlayerTag(target) else "",
            visionLevel = levelInt,
            hotbarItemNames = if (silenced) emptyList() else hotbarItemNames
        )
        ServerPlayNetworking.send(caster, resultPacket)

        logger.info(
            "Player vision: {} -> {} | inView={} wasInView={} result={}",
            caster.displayName!!.string, target.displayName!!.string, casterInTargetView, packet.wasInView, result.name
        )
    }

    /** Message type for applyPlayerVisionSuccess target text. */
    private enum class SuccessMessageType { NORMAL, IN_VIEW, DISCOVERY }

    /**
     * Apply success: send messages, broadcast tag, apply cooldown.
     * @param msgType Which message to send to the target:
     *   NORMAL – yellow "好像被谁轻握住了手臂..."
     *   IN_VIEW – random red "\<caster\>[抓住了/握住了]你的[部位]"
     *   DISCOVERY – random red "\<caster\>[顺势/快速的/不觉间][抓住了/握住了/摸到了/碰到]你的[部位]"
     */
    private fun applyPlayerVisionSuccess(caster: ServerPlayer, target: ServerPlayer, config: com.example.vision_optimize.config.ModConfig, msgType: SuccessMessageType = SuccessMessageType.NORMAL, applyCooldown: Boolean = true) {
        // Target gets chat message; random-part messages also carry neck-damage flag
        val targetMsg: net.minecraft.network.chat.Component
        when (msgType) {
            SuccessMessageType.IN_VIEW -> {
                val result = TextBuilder.playerVisionInViewSuccess(resolveVisionName(caster))
                targetMsg = result.component
                if (result.shouldDamage) {
                    scheduleNeckDamage(target)
                }
            }
            SuccessMessageType.DISCOVERY -> {
                val result = TextBuilder.playerVisionDiscoverySuccess(resolveVisionName(caster))
                targetMsg = result.component
                if (result.shouldDamage) {
                    scheduleNeckDamage(target)
                }
            }
            SuccessMessageType.NORMAL -> {
                targetMsg = TextBuilder.playerVisionTargetSuccess()
            }
        }
        target.sendSystemMessage(targetMsg)

        // Show tag only to the caster (private, not broadcast); silenced casters get no info
        if (!caster.tags.contains("Silenced")) {
            val tagText = getPlayerTag(target)
            val tagColor = getPlayerTagColor(target)
            val tagMsg = TextBuilder.playerVisionBroadcast(resolveVisionName(target), tagText, tagColor)
            caster.sendSystemMessage(tagMsg)
        }

        if (applyCooldown) {
            applyCooldown(caster.displayName!!.string, config.playerVisionSuccessCooldown)
        }
    }

    // ===== Discovery Reaction =====

    /**
     * Called when a player attacks another player.
     * Checks if the attacker is in a discovery state and the victim is the target.
     * If so, the vision succeeds with discovery-specific messages.
     */
    fun onPlayerAttack(attacker: ServerPlayer, victim: ServerPlayer) {
        val discoveryState = discoveryStates[attacker.displayName!!.string] ?: return
        if (discoveryState.targetName != victim.displayName!!.string) return

        // Discovery success! Caster attacked target during reaction window.
        val config = ModConfigManager.get()
        discoveryStates.remove(attacker.displayName!!.string)

        // Apply vision success with discovery-specific target message (random red text with adverb)
        applyPlayerVisionSuccess(attacker, victim, config, msgType = SuccessMessageType.DISCOVERY)

        // Send success result to caster (with hotbar items for L2/L3)
        val attLevel = getPlayerVisionLevel(attacker)
        val hotbarItems = when {
            attLevel == VisionLevel.PLAYER_VISION_LEVEL_3 -> {
                (0..2).map { itemDataString(victim.inventory.getItem(it), attacker.server!!.registryAccess()) }
            }
            attLevel == VisionLevel.PLAYER_VISION_LEVEL_2 -> {
                listOf(itemDataString(victim.inventory.getItem(0), attacker.server!!.registryAccess()))
            }
            else -> emptyList()
        }
        val levelInt = when (attLevel) {
            VisionLevel.PLAYER_VISION_LEVEL_3 -> 3
            VisionLevel.PLAYER_VISION_LEVEL_2 -> 2
            else -> 1
        }
        val silenced = attacker.tags.contains("Silenced")
        val resultPacket = PlayerVisionResultS2CPacket(
            success = true,
            resultType = 2,
            targetName = victim.displayName!!.string,
            tagText = if (silenced) "" else getPlayerTag(victim),
            visionLevel = levelInt,
            hotbarItemNames = if (silenced) emptyList() else hotbarItems
        )
        ServerPlayNetworking.send(attacker, resultPacket)

        logger.info("Player vision discovery success: {} attacked {} during reaction window", attacker.displayName!!.string, victim.displayName!!.string)
    }

    /**
     * Called when a player takes damage from another player.
     * Checks if the victim is currently performing player vision (active charge or discovery state).
     * If so, the vision fails and the attacker receives an interrupt message.
     */
    fun onPlayerAttacked(victim: ServerPlayer, attacker: ServerPlayer) {
        // Case 1: Victim is actively charging player vision
        val activeCharge = activeCharges[victim.displayName!!.string]
        if (activeCharge != null) {
            activeCharges.remove(victim.displayName!!.string)
            witnessTicks.keys.removeAll { it.startsWith("${victim.displayName!!.string}:") }

            // Attacker gets yellow interrupt message
            attacker.sendSystemMessage(TextBuilder.playerVisionInterrupted(resolveVisionName(victim)))

            // Apply fail
            handleClientFail(victim)
            logger.info("Player vision interrupted: {} was attacked by {} while charging", victim.displayName!!.string, attacker.displayName!!.string)
            return
        }

        // Case 2: Victim is in discovery reaction window
        val discoveryState = discoveryStates[victim.displayName!!.string] ?: return

        val config = ModConfigManager.get()
        discoveryStates.remove(victim.displayName!!.string)

        val target = victim.server?.playerList?.getPlayerByName(discoveryState.targetName)

        // Send fail messages to both caster and target
        target?.displayClientMessage(TextBuilder.playerVisionFail(), true)
        victim.displayClientMessage(TextBuilder.playerVisionFail(), true)

        // Attacker gets yellow interrupt message
        attacker.sendSystemMessage(TextBuilder.playerVisionInterrupted(resolveVisionName(victim)))

        // Suppress tellraw for both during fail cooldown
        val suppressPacket = PlayerVisionTellrawSuppressS2CPacket(3)
        ServerPlayNetworking.send(victim, suppressPacket)
        target?.let { ServerPlayNetworking.send(it, suppressPacket) }

        // Apply fail cooldown
        applyCooldown(victim.displayName!!.string, config.playerVisionFailCooldown)

        // Send fail result to caster
        val resultPacket = PlayerVisionResultS2CPacket(
            success = false,
            resultType = 0,
            targetName = target?.name?.string ?: "",
            tagText = ""
        )
        ServerPlayNetworking.send(victim, resultPacket)

        logger.info("Player vision discovery failed: {} was attacked by {} during reaction window", victim.displayName!!.string, attacker.displayName!!.string)
    }

    /**
     * Tick discovery states — check for expired reaction windows.
     * Called from the server tick loop (every tick).
     * Bails early if no discovery states to avoid unnecessary iteration.
     */
    fun tickDiscoveryStates(server: net.minecraft.server.MinecraftServer) {
        // Fast path: skip if no active discovery states
        if (discoveryStates.isEmpty()) return

        val currentTick = server.tickCount
        val expired = discoveryStates.entries.filter {
            currentTick - it.value.startTick >= it.value.reactionTicks
        }
        for ((casterName, state) in expired) {
            discoveryStates.remove(casterName)

            val caster = server.playerList.getPlayerByName(casterName) ?: continue
            val target = server.playerList.getPlayerByName(state.targetName) ?: continue

            val config = ModConfigManager.get()

            // Reaction window expired → FAIL
            target.displayClientMessage(TextBuilder.playerVisionFail(), true)
            caster.displayClientMessage(TextBuilder.playerVisionFail(), true)

            // Suppress tellraw for 3 more seconds
            val suppressPacket = PlayerVisionTellrawSuppressS2CPacket(3)
            ServerPlayNetworking.send(caster, suppressPacket)
            ServerPlayNetworking.send(target, suppressPacket)

            // Apply fail cooldown
            applyCooldown(casterName, config.playerVisionFailCooldown)

            // Send fail result to caster
            val resultPacket = PlayerVisionResultS2CPacket(
                success = false,
                resultType = 0,
                targetName = target.displayName!!.string,
                tagText = ""
            )
            ServerPlayNetworking.send(caster, resultPacket)

            logger.info("Player vision discovery expired: {} -> {}", caster.displayName!!.string, target.displayName!!.string)
        }
    }

    // ===== Neck Damage (when random body part is "脖子") =====

    /**
     * Schedule neck damage for the target: 0.5 hearts every 10 ticks, twice (total 1 heart).
     * Also applies Darkness (2s) + Slowness III (5s) and sends suffocation messages.
     */
    private fun scheduleNeckDamage(target: ServerPlayer) {
        val server = target.server ?: return
        val currentTick = server.tickCount

        // Apply potion effects: Darkness 2s (40 ticks), Slowness III 5s (100 ticks)
        target.addEffect(net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.DARKNESS, 40, 0, false, false, true
        ))
        target.addEffect(net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.SLOWNESS, 100, 2, false, false, true
        ))

        // Yellow action bar: suffocation starting
        target.displayClientMessage(TextBuilder.playerVisionNeckSuffocationStart(), true)

        neckDamageStates[target.displayName!!.string] = NeckDamageState(
            targetName = target.displayName!!.string,
            startTick = currentTick,
            damageCount = 0,
            maxDamage = 2,
            suffocationEndTick = currentTick + 40  // 2 seconds = Darkness duration
        )
        logger.info("Neck damage + suffocation scheduled for {} (player vision neck grab)", target.displayName!!.string)
    }

    /**
     * Tick neck damage states — apply damage every 10 ticks up to 2 times.
     * Called from the server tick loop (every tick).
     * Bails early if no neck damage states to avoid unnecessary iteration.
     */
    fun tickNeckDamage(server: net.minecraft.server.MinecraftServer) {
        // Fast path: skip if no active neck damage states
        if (neckDamageStates.isEmpty()) return

        val currentTick = server.tickCount
        val completed = mutableListOf<String>()

        for ((targetName, state) in neckDamageStates) {
            val ticksElapsed = currentTick - state.startTick

            // Apply damage every 10 ticks (stop at half heart to avoid killing)
            val expectedDamageCount = (ticksElapsed / 10).coerceAtMost(state.maxDamage)
            if (expectedDamageCount > state.damageCount) {
                val target = server.playerList.getPlayerByName(targetName)
                if (target != null && target.isAlive && target.health > 1f) {
                    val damageSource = target.damageSources().magic()
                    target.hurt(damageSource, 1f)
                    state.damageCount++
                    logger.info("Neck damage tick {} for {} (player vision)", state.damageCount, target.displayName!!.string)
                } else if (target != null && target.isAlive) {
                    // Target too low — skip remaining damage ticks
                    state.damageCount = state.maxDamage
                }
            }

            // Send green suffocation-end message after Darkness expires (2s)
            if (!state.suffocationEnded && currentTick >= state.suffocationEndTick) {
                state.suffocationEnded = true
                val target = server.playerList.getPlayerByName(targetName)
                target?.sendSystemMessage(TextBuilder.playerVisionNeckSuffocationEnd())
            }

            // Cleanup when all damage dealt AND suffocation message sent
            if (state.damageCount >= state.maxDamage && state.suffocationEnded) {
                completed.add(targetName)
            }
        }

        for (name in completed) {
            neckDamageStates.remove(name)
        }
    }

    /**
     * Tick active charges — handle third-party sneak witness accumulation.
     * Called from the server tick loop (every 5 ticks).
     * Bails early if no active charges to avoid unnecessary config reads and iteration.
     */
    fun tickActiveCharges(server: net.minecraft.server.MinecraftServer) {
        // Fast path: skip config read if no active charges
        if (activeCharges.isEmpty()) return

        val config = ModConfigManager.get()
        val currentTick = server.tickCount
        val discoverThreshold = config.playerVisionDiscoverTime

        val toRemove = mutableListOf<String>()

        for ((casterName, charge) in activeCharges) {
            // Don't process in-view charges for third-party sneak
            if (charge.wasInView) continue

            // Timeout stale charges (e.g., client disconnected)
            if (currentTick - charge.startTick > config.playerVisionSneakTime + 100) {
                toRemove.add(casterName)
                continue
            }

            val caster = server.playerList.getPlayerByName(casterName) ?: continue
            val target = server.playerList.getPlayerByName(charge.targetName) ?: continue

            // Check each potential witness
            for (witness in server.playerList.players) {
                if (witness == caster || witness == target) continue

                // Check distance and line-of-sight from witness to caster
                if (!canWitnessSee(witness, caster, config)) continue

                val seesCaster = isInFov(witness, caster)
                val seesTarget = isInFov(witness, target)
                val key = "$casterName:${witness.displayName!!.string}"

                if (seesCaster && seesTarget) {
                    // Witness sees both — accumulate ticks (called every 5 ticks, +5 per call)
                    val accumulated = witnessTicks.getOrDefault(key, 0) + 5
                    witnessTicks[key] = accumulated

                    if (accumulated >= discoverThreshold) {
                        // Threshold reached — send blue sneak message
                        val msg = TextBuilder.playerVisionThirdPartySneak(resolveVisionName(caster), resolveVisionName(target))
                        witness.sendSystemMessage(msg)
                        // Remove entry so the message is only sent once per charge
                        witnessTicks.remove(key)
                    }
                } else {
                    // Witness lost sight — reset accumulator
                    witnessTicks.remove(key)
                }
            }
        }

        // Cleanup stale charges
        for (name in toRemove) {
            activeCharges.remove(name)
            witnessTicks.keys.removeAll { it.startsWith("$name:") }
        }
    }

    /**
     * Handle client-side fail notification (damage taken, out of range during raid).
     * Applies fail cooldown and sends tellraw suppression.
     */
    fun handleClientFail(caster: ServerPlayer) {
        val config = ModConfigManager.get()
        val casterName = caster.displayName!!.string

        // Cleanup active charge
        activeCharges.remove(casterName)
        witnessTicks.keys.removeAll { it.startsWith("$casterName:") }

        // Apply fail cooldown
        applyCooldown(casterName, config.playerVisionFailCooldown)

        // Send tellraw suppression for 3 seconds
        ServerPlayNetworking.send(caster, PlayerVisionTellrawSuppressS2CPacket(3))

        // Send fail result
        val resultPacket = PlayerVisionResultS2CPacket(
            success = false,
            resultType = 0,
            targetName = "",
            tagText = ""
        )
        ServerPlayNetworking.send(caster, resultPacket)
    }

    // ===== FOV Helpers =====

    /** Check if [viewer] has [subject] in their field of view. */
    fun isInFov(viewer: ServerPlayer, subject: ServerPlayer): Boolean {
        val lookDir = viewer.lookAngle
        val toSubject = Vec3(
            subject.x - viewer.x,
            subject.eyeY - viewer.eyeY,
            subject.z - viewer.z
        ).normalize()
        val dot = lookDir.dot(toSubject)
        return dot > 0.2 // ~78 degree FOV cone
    }

    /**
     * Check if a third-party witness can see the caster for vision witnessing.
     * Requirements:
     * 1. Caster is within the configured perspective distance
     * 2. Caster is in front of the witness (dot product > 0)
     * 3. Line of sight is clear (no solid blocks obstructing)
     */
    private fun canWitnessSee(witness: ServerPlayer, caster: ServerPlayer, config: com.example.vision_optimize.config.ModConfig): Boolean {
        // Distance check
        val dist = witness.distanceTo(caster)
        if (dist > config.playerVisionThirdPerspectiveDistance) return false

        // Forward-facing check: caster must be in front of witness
        val lookDir = witness.lookAngle
        val toCaster = Vec3(
            caster.x - witness.x,
            caster.eyeY - witness.eyeY,
            caster.z - witness.z
        ).normalize()
        if (lookDir.dot(toCaster) <= 0) return false

        // Line-of-sight check: no solid blocks between witness and caster
        val eyePos = witness.eyePosition
        val targetPos = caster.eyePosition
        val hit = witness.level().clip(
            ClipContext(
                eyePos,
                targetPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                witness
            )
        )
        return hit.type == HitResult.Type.MISS
    }

    /** Check if [caster] is facing toward [target] within rough range. */
    private fun isFacingTarget(caster: ServerPlayer, target: ServerPlayer, maxDist: Int): Boolean {
        val lookDir = caster.lookAngle
        val toTarget = Vec3(
            target.x - caster.x,
            target.eyeY - caster.eyeY,
            target.z - caster.z
        )
        val dist = toTarget.length()
        if (dist > maxDist + 2) return false
        val normalized = toTarget.normalize()
        val dot = lookDir.dot(normalized)
        return dot > 0.3
    }

    // ===== Level Determination =====

    /**
     * Get the effective player vision level for a player, considering scoreboard overrides.
     */
    fun getPlayerVisionLevel(player: ServerPlayer): com.example.vision_optimize.config.VisionLevel {
        val config = ModConfigManager.get()
        val name = player.displayName!!.string

        // Manosaba (魔女审判) override: while the witch trial is enabled, a player
        // with the "Vision" magic tag automatically has full vision permissions.
        if (config.hasVisionMagicFullAccess(player.tags)) {
            val level = com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_3
            com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyPlayerVision(player, level)
            return level
        }

        // 1. Fetch player's score from configured scoreboards
        val score = getPlayerVisionScoreboardScore(player, config)

        // 2. Check scoreboard-based overrides (enforced if score in range)
        val scoreLevel = config.getEffectivePlayerVisionLevel(name, score)
        if (scoreLevel != null && scoreLevel != com.example.vision_optimize.config.VisionLevel.NONE) {
            com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyPlayerVision(player, scoreLevel)
            return scoreLevel
        }

        // 3. Check player-specific manual level assignment
        val playerLevel = config.playerPlayerLevels[name]
        if (playerLevel != null && playerLevel != com.example.vision_optimize.config.VisionLevel.NONE) {
            com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyPlayerVision(player, playerLevel)
            return playerLevel
        }

        // 4. In ALL_MEMBERS mode, default to PLAYER_VISION_LEVEL_1
        if (config.mode == com.example.vision_optimize.config.AccessMode.ALL_MEMBERS) {
            val defaultLevel = com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_1
            com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyPlayerVision(player, defaultLevel)
            return defaultLevel
        }

        // 5. In TAG_MEMBER mode, check if player has any vision-enabled access tag
        if (config.mode == com.example.vision_optimize.config.AccessMode.TAG_MEMBER) {
            if (config.hasVisionAccess(player.displayName!!.string)) {
                val defaultLevel = com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_1
                com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyPlayerVision(player, defaultLevel)
                return defaultLevel
            }
        }

        val noneLevel = com.example.vision_optimize.config.VisionLevel.NONE
        com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyPlayerVision(player, noneLevel)
        return noneLevel
    }

    /**
     * Get the player's score from any configured player vision scoreboard.
     */
    private fun getPlayerVisionScoreboardScore(player: ServerPlayer, config: com.example.vision_optimize.config.ModConfig): Double? {
        val scoreboard = player.server?.scoreboard ?: return null
        val objectives = listOf(
            config.playerVisionL1Scoreboard,
            config.playerVisionL2Scoreboard,
            config.playerVisionL3Scoreboard
        ).filter { it.isNotEmpty() }
        if (objectives.isEmpty()) return null
        val objectiveName = objectives.first()
        val objective = scoreboard.getObjective(objectiveName) ?: return null
        return com.example.vision_optimize.util.ScoreboardChecker.getScore(player, objective)
    }

    // ===== Range Check =====

    /**
     * Check if [target] is within range centered on [caster].
     * Level 1: config box range (playerVisionRangeX/Y/Z).
     * Level 2/3: 7-block spherical radius.
     */
    fun isInRange(caster: ServerPlayer, target: ServerPlayer, config: com.example.vision_optimize.config.ModConfig, level: com.example.vision_optimize.config.VisionLevel = com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_1): Boolean {
        if (level == com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_3) {
            // 14-block spherical radius for L3
            return caster.distanceTo(target) <= 14.0
        }
        if (level == com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_2) {
            // 7-block spherical radius for L2
            return caster.distanceTo(target) <= 7.0
        }
        // L1: box range
        val dx = Math.abs(target.x - caster.x)
        val dy = Math.abs(target.eyeY - caster.eyeY)
        val dz = Math.abs(target.z - caster.z)
        val halfX = config.playerVisionRangeX / 2.0
        val halfY = config.playerVisionRangeY / 2.0
        val halfZ = config.playerVisionRangeZ / 2.0
        return dx <= halfX && dy <= halfY && dz <= halfZ
    }

    // ===== Third-Party Witnessing =====

    /** Send yellow raid message to all third-party witnesses who see both players. */
    private fun sendThirdPartyRaidWarning(
        server: net.minecraft.server.MinecraftServer,
        caster: ServerPlayer,
        target: ServerPlayer
    ) {
        val config = ModConfigManager.get()
        for (witness in server.playerList.players) {
            if (witness == caster || witness == target) continue

            // Check distance and line-of-sight from witness to caster
            if (!canWitnessSee(witness, caster, config)) continue

            val seesCaster = isInFov(witness, caster)
            val seesTarget = isInFov(witness, target)

            if (seesCaster && seesTarget) {
                val msg = TextBuilder.playerVisionThirdPartyRaid(resolveVisionName(caster), resolveVisionName(target))
                witness.sendSystemMessage(msg)
            }
        }
    }

    // ===== Cooldowns =====

    private fun applyCooldown(playerName: String, minutes: Int) {
        cooldowns[playerName] = System.currentTimeMillis() + (minutes * 60_000L)
    }

    /**
     * Check if a player is currently on cooldown for player vision.
     */
    fun isOnCooldown(playerName: String): Boolean {
        val end = cooldowns[playerName] ?: return false
        return System.currentTimeMillis() < end
    }

    /**
     * Get remaining cooldown in milliseconds, or 0 if not on cooldown.
     */
    fun getCooldownRemainingMs(playerName: String): Long {
        val end = cooldowns[playerName] ?: return 0L
        val remaining = end - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }

    /**
     * Clear cooldown for a player (for admin use).
     */
    fun clearCooldown(playerName: String) {
        cooldowns.remove(playerName)
    }

    // ===== Tags =====

    /**
     * Get the magic tag text assigned to a player.
     * Priority: hardcoded magic tags (vanilla /tag scoreboard tags) > config.playerVisionPlayerTags.
     */
    private fun getPlayerTag(player: ServerPlayer): String {
        val config = ModConfigManager.get()
        // Priority 1: hardcoded magic tags (vanilla scoreboard tags)
        val magicTag = ManosabaMagics.findMatchingTag(player.tags)
        if (magicTag != null) return ManosabaMagics.getMagicName(magicTag) ?: "无"
        // Priority 2: config-based player vision tags
        return config.playerVisionPlayerTags[player.displayName!!.string] ?: "无"
    }

    /**
     * Get the color for a player's assigned magic tag.
     * Priority: hardcoded magic tags (vanilla /tag scoreboard tags) > config.playerVisionTags.
     */
    private fun getPlayerTagColor(player: ServerPlayer): Int {
        val config = ModConfigManager.get()
        // Priority 1: hardcoded magic tags (vanilla scoreboard tags)
        val magicTag = ManosabaMagics.findMatchingTag(player.tags)
        if (magicTag != null) return ManosabaMagics.getMagicColor(magicTag) ?: 0xFFFFFFFF.toInt()
        // Priority 2: config-based player vision tags
        val tagText = config.playerVisionPlayerTags[player.displayName!!.string] ?: return 0xFFFFFFFF.toInt()
        return config.playerVisionTags[tagText] ?: 0xFFFFFFFF.toInt()
    }

    // ===== Manosaba Name Resolution =====

    /**
     * Resolve a player's display name for vision messages.
     * If manosaba is enabled and the player has a matching character tag
     * (from config.playerTags OR vanilla /tag scoreboard tags),
     * returns the colored character name. Otherwise returns the player's raw name Component.
     */
    private fun resolveVisionName(player: ServerPlayer): Component {
        val config = ModConfigManager.get()
        if (!config.manosabaEnabled) return player.name

        // Check config playerTags AND vanilla scoreboard tags
        val configTags = config.playerTags[player.displayName!!.string] ?: emptySet()
        val vanillaTags = player.tags
        val allTags = configTags + vanillaTags

        val matchingTag = ManosabaCharacters.findMatchingTag(allTags) ?: return player.name
        return ManosabaCharacters.buildColoredName(matchingTag) ?: player.name
    }

    // ===== Look Warnings (L2/L3 Selection Screen) =====

    /**
     * Handle a "look" notification from a L2/L3 caster who just opened the selection screen.
     * No longer sends warnings here — warnings fire at completion time (handleRequest).
     */
    fun handleLook(caster: ServerPlayer, targetNames: List<String>) {
        // Warnings moved to handleRequest success path (fire when player is actually selected)
    }

    /** Serialize an ItemStack to packet string: "registryKey|count" or "empty" */
    private fun itemDataString(stack: net.minecraft.world.item.ItemStack, @Suppress("UNUSED_PARAMETER") registryAccess: net.minecraft.core.HolderLookup.Provider): String {
        if (stack.isEmpty) return "empty"
        val key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.item).toString()
        return "$key|${stack.count}"
    }

    // ===== Data Classes =====

    data class ActiveCharge(
        val casterName: String,
        val targetName: String,
        val startTick: Int,
        val wasInView: Boolean
    )

    data class DiscoveryState(
        val casterName: String,
        val targetName: String,
        val startTick: Int,
        val reactionTicks: Int
    )

    data class NeckDamageState(
        val targetName: String,
        val startTick: Int,
        var damageCount: Int,
        val maxDamage: Int,
        val suffocationEndTick: Int,  // tick when Darkness expires → send green follow-up
        var suffocationEnded: Boolean = false
    )

    enum class PlayerVisionResult {
        SUCCESS,
        FAIL,
        FAIL_DISCOVERED
    }
}
