package com.example.vision_optimize

import com.example.vision_optimize.client.ItemTooltipHandler
import com.example.vision_optimize.client.VisionKeyBindings
import com.example.vision_optimize.client.VisionProgressOverlay
import com.example.vision_optimize.event.ClientEventHandlers
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import com.example.vision_optimize.network.BlockVisionRequestC2SPacket
import com.example.vision_optimize.gui.VisionSettingsScreen
import com.example.vision_optimize.network.ItemVisionRequestC2SPacket
import com.example.vision_optimize.network.PlayerVisionRequestC2SPacket
import com.example.vision_optimize.network.PlayerVisionChargeStartC2SPacket
import com.example.vision_optimize.network.PlayerVisionChargeCancelC2SPacket
import com.example.vision_optimize.client.ClientVisionState
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player as McPlayer
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object Vision_optimizeClient : ClientModInitializer {
    val logger = org.slf4j.LoggerFactory.getLogger("vision_optimize:client")

    override fun onInitializeClient() {
        logger.info("Vision Optimize client initializing...")

        // Register client event handlers and packet receivers
        ClientEventHandlers.register()
        logger.info("Client event handlers registered")

        // Register key bindings
        VisionKeyBindings.register()
        logger.info("Key bindings registered")

        // Register tooltip handler
        ItemTooltipHandler.register()
        logger.info("Tooltip handler registered")

        // Register progress overlay
        VisionProgressOverlay.register()
        logger.info("Progress overlay registered")

        // Client tick handler for key presses and crouch+right-click detection
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.player == null) return@register

            // Silenced debuff cooldown tick
            if (ClientVisionState.silencedCooldown > 0) {
                ClientVisionState.silencedCooldown--
            }

            // Silenced check helper for vision functions
            fun checkSilenced(client: Minecraft): Boolean {
                if (ClientVisionState.isSilenced()) {
                    if (ClientVisionState.silencedCooldown <= 0) {
                        ClientVisionState.silencedCooldown = 30 * 20  // 30 seconds
                        client.player?.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("因为某种援引幻视失效了...")
                                .withStyle { it.withColor(0xFF5555) },
                            true
                        )
                    }
                    return true
                }
                return false
            }

            // KP_3 -> Open settings screen (OP only)
            while (VisionKeyBindings.settingsKey.consumeClick()) {
                if (ClientVisionState.isOp) {
                    VisionSettingsScreen.open()
                } else {
                    client.player?.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("你没有权限打开设置页面")
                            .withStyle { it.withColor(0xFF5555) },
                        true
                    )
                }
            }

            // P key -> Player vision select screen (Level 2/3 only)
            while (VisionKeyBindings.playerSelectKey.consumeClick()) {
                if (ClientVisionState.hasPlayerVisionPermission()) {
                    val level = ClientVisionState.cachedPlayerVisionLevel
                        ?: ClientVisionState.getPlayerVisionLevel()
                        ?: com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_1
                    if (level == com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_2 ||
                        level == com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_3
                    ) {
                        if (ClientVisionState.playerVisionCooldown > 0) {
                            val totalSec = (ClientVisionState.playerVisionCooldown + 19) / 20
                            client.player?.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("人物幻视冷却还剩 ${totalSec}秒")
                                    .withStyle { it.withColor(0xFF5555) },
                                true
                            )
                        } else if (hasAnyPlayerInFov(client)) {
                            openPlayerSelectScreen(client)
                        } else {
                            client.player?.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("范围内没有人可以幻视...")
                                    .withStyle { it.withColor(0xFF5555) },
                                true
                            )
                        }
                    }
                }
            }

            // Item vision cooldown tick
            if (ClientVisionState.itemVisionCooldown > 0) {
                ClientVisionState.itemVisionCooldown--
            }

            // V key: dual-use based on hand state
            val stack = client.player?.mainHandItem ?: net.minecraft.world.item.ItemStack.EMPTY
            if (!stack.isEmpty) {
                // Main hand has item → Item Vision (press once, existing behavior)
                // Permission check: skip if player doesn't have item vision permission
                if (!ClientVisionState.hasItemVisionPermission()) {
                    while (VisionKeyBindings.itemVisionKey.consumeClick()) { }
                } else {
                while (VisionKeyBindings.itemVisionKey.consumeClick()) {
                    if (checkSilenced(client)) continue
                    if (ClientVisionState.itemVisionCooldown <= 0) {
                        ClientPlayNetworking.send(ItemVisionRequestC2SPacket(-1))
                        val cooldownSec = ClientVisionState.cachedConfig?.itemCooldownTime ?: 5
                        ClientVisionState.itemVisionCooldown = cooldownSec * 20
                    } else {
                        // Show cooldown message on action bar
                        val secRemaining = (ClientVisionState.itemVisionCooldown + 19) / 20  // ceil to seconds
                        if (secRemaining != ClientVisionState.itemVisionLastCooldownSec) {
                            ClientVisionState.itemVisionLastCooldownSec = secRemaining
                            val msg = net.minecraft.network.chat.Component.literal("物品幻视冷却还剩 ${secRemaining} 秒")
                                .withStyle { it.withColor(0xFF5555) }
                            client.player?.displayClientMessage(msg, true)
                        }
                    }
                }
                }
            } else {
                // Main hand empty → Player Vision (hold mechanic, handled below)
                // Consume any pending V key clicks to prevent item vision from
                // triggering when the player cancels player vision and switches to an item.
                while (VisionKeyBindings.itemVisionKey.consumeClick()) { }
            }

            // Block vision: client-driven charge — hold right-click + crouch on a block
            handleBlockVisionCharge(client)

            // Player vision: client-driven charge — hold V with empty hand while looking at a player
            handlePlayerVisionCharge(client)
        }

        logger.info("Vision Optimize client initialized!")
    }

    /**
     * Client-driven block vision charge mechanic.
     *
     * Crouch + hold right-click on a block → bar appears, fills 0%→100%
     *   → at 100%: send request, bar disappears, cooldown starts
     *
     * Cooldown counts down every tick regardless of player activity.
     * Player cannot charge again until cooldown reaches 0.
     *
     * Release mid-charge → cancel, bar disappears, no cooldown.
     */
    private fun handleBlockVisionCharge(client: Minecraft) {
        val player = client.player ?: return

        // Silenced check: block vision is disabled for Silenced players
        if (ClientVisionState.isSilenced()) {
            if (ClientVisionState.blockVisionLocked) {
                ClientVisionState.resetBlockVision()
            }
            return
        }

        // Tick down block vision cooldown every tick (regardless of what player is doing)
        if (ClientVisionState.blockVisionCooldown > 0) {
            ClientVisionState.blockVisionCooldown--
        }

        // Only show bar if player has block vision permission
        if (!ClientVisionState.hasBlockVisionPermission(player.displayName!!.string)) return
        val useDown = client.options.keyUse.isDown
        val crouching = player.isCrouching
        val hit = client.hitResult
        val onBlock = hit != null && hit.type == net.minecraft.world.phys.HitResult.Type.BLOCK
        val blockPos = if (onBlock) (hit as net.minecraft.world.phys.BlockHitResult).blockPos else null

        // Charge duration from config (blockSearchTime in seconds), fallback 3s
        val searchTimeSec = ClientVisionState.cachedConfig?.blockSearchTime ?: 3
        val chargeTicks = (searchTimeSec * 20).coerceIn(20, 1200)  // 1s ~ 60s
        val inc = 1f / chargeTicks
        val cooldownReady = ClientVisionState.blockVisionCooldown <= 0

        // Show cooldown message when player tries to charge but is on cooldown
        if (useDown && crouching && onBlock && !ClientVisionState.blockVisionLocked && !cooldownReady) {
            val secRemaining = (ClientVisionState.blockVisionCooldown + 19) / 20  // ceil to seconds
            if (secRemaining != ClientVisionState.blockVisionLastCooldownSec) {
                ClientVisionState.blockVisionLastCooldownSec = secRemaining
                val msg = net.minecraft.network.chat.Component.literal("方块幻视冷却还剩 ${secRemaining} 秒")
                    .withStyle { it.withColor(0xFF5555) }
                player.displayClientMessage(msg, true)
            }
        } else {
            ClientVisionState.blockVisionLastCooldownSec = -1
        }

        if (useDown && crouching && onBlock && !ClientVisionState.blockVisionLocked && cooldownReady) {
            // Start charging
            if (!ClientVisionState.blockVisionCharging) {
                ClientVisionState.blockVisionCharging = true
                ClientVisionState.blockVisionProgress = 0f
                ClientVisionState.blockVisionTargetPos = blockPos
            }

            ClientVisionState.blockVisionProgress = (ClientVisionState.blockVisionProgress + inc).coerceIn(0f, 1f)

            if (ClientVisionState.blockVisionProgress >= 1f) {
                // Complete — send request, start cooldown, lock until key released
                val pos = ClientVisionState.blockVisionTargetPos ?: blockPos
                if (pos != null) {
                    ClientPlayNetworking.send(BlockVisionRequestC2SPacket(pos, pos.x, pos.y, pos.z))
                }
                ClientVisionState.blockVisionCharging = false
                ClientVisionState.blockVisionProgress = 0f
                ClientVisionState.blockVisionLocked = true
                // Start cooldown (configurable duration in seconds, consumed by doing anything)
                val cooldownSec = ClientVisionState.cachedConfig?.blockCooldownTime ?: 3
                ClientVisionState.blockVisionCooldown = cooldownSec * 20
            }
        } else if (!useDown) {
            // Key released — unlock for next charge
            ClientVisionState.resetBlockVision()
        }
    }

    /**
     * Client-driven player vision charge mechanic (Level 1 only).
     * Crouch + hold V with empty hand while facing another player → progress bar appears.
     * Charge time depends on whether caster is estimated to be in target's FOV.
     *
     * Levels 2 & 3 use the player selection screen (P key) instead of the charge mechanic.
     */
    private fun handlePlayerVisionCharge(client: Minecraft) {
        val player = client.player ?: return

        // Tick down cooldown every tick
        if (ClientVisionState.playerVisionCooldown > 0) {
            ClientVisionState.playerVisionCooldown--
        }

        // Permission check
        if (!ClientVisionState.hasPlayerVisionPermission()) return

        // L2/L3 can use V-key for close-range vision (in addition to P-key remote)
        val pvLevel = ClientVisionState.cachedPlayerVisionLevel
            ?: ClientVisionState.getPlayerVisionLevel()
        val isL2OrL3 = pvLevel == com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_2 ||
                pvLevel == com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_3

        val useDown = VisionKeyBindings.itemVisionKey.isDown
        val crouching = player.isCrouching
        val stack = player.mainHandItem
        val handEmpty = stack.isEmpty

        // Get the entity player is looking at
        val hit = client.hitResult
        val lookingAtPlayer = hit != null && hit.type == HitResult.Type.ENTITY
        val targetEntity = if (lookingAtPlayer) (hit as EntityHitResult).entity else null
        val targetIsPlayer = targetEntity is McPlayer
        val targetPlayer = if (targetIsPlayer) targetEntity else null

        // Determine local FOV estimate (is caster in target's view?)
        var localInView = false
        if (targetPlayer != null) {
            val targetLook = targetPlayer.lookAngle
            val toCaster = Vec3(
                player.x - targetPlayer.x,
                player.eyeY - targetPlayer.eyeY,
                player.z - targetPlayer.z
            ).normalize()
            localInView = targetLook.dot(toCaster) > 0.2
        }

        // Charge time: L2/L3=0.5s (10 ticks); L1 based on FOV estimate
        val chargeTicks = if (isL2OrL3) {
            10  // 0.5 seconds for L2/L3
        } else if (localInView) {
            (ClientVisionState.cachedConfig?.playerVisionRaidTime ?: 30).coerceIn(10, 1200)
        } else {
            (ClientVisionState.cachedConfig?.playerVisionSneakTime ?: 60).coerceIn(10, 1200)
        }
        val inc = 1f / chargeTicks
        val cooldownReady = ClientVisionState.playerVisionCooldown <= 0
        val canCharge = useDown && crouching && handEmpty && targetIsPlayer && !ClientVisionState.playerVisionLocked && cooldownReady

        // Show cooldown message
        if (useDown && crouching && handEmpty && targetIsPlayer && !ClientVisionState.playerVisionLocked && !cooldownReady
            && !ClientVisionState.isTellrawSuppressed()) {
            val totalMin = ClientVisionState.playerVisionCooldown / (20 * 60)
            val totalSec = (ClientVisionState.playerVisionCooldown / 20) % 60
            val secRemaining = (ClientVisionState.playerVisionCooldown + 19) / 20
            if (secRemaining != ClientVisionState.playerVisionLastCooldownSec) {
                ClientVisionState.playerVisionLastCooldownSec = secRemaining
                val msg = net.minecraft.network.chat.Component.literal("人物幻视冷却还剩 ${totalMin}分${totalSec}秒")
                    .withStyle { it.withColor(0xFF5555) }
                player.displayClientMessage(msg, true)
            }
        } else {
            ClientVisionState.playerVisionLastCooldownSec = -1
        }

        // Main charge logic — requires crouching + V key + empty hand + looking at player
        if (canCharge) {
            val target = targetPlayer ?: return

            // Box range check using configured X/Y/Z
            if (!isInPlayerVisionRange(player, target)) {
                // Out of range: for in-view charge this is a fail; for out-of-view charge, silent cancel
                if (ClientVisionState.playerVisionCharging) {
                    val wasInView = ClientVisionState.playerVisionInView
                    ClientPlayNetworking.send(PlayerVisionChargeCancelC2SPacket(target.displayName!!.string))
                    ClientVisionState.resetPlayerVision()
                    if (wasInView) {
                        // Fail for in-view out-of-range
                        triggerPlayerVisionFail(player)
                    }
                } else {
                    ClientVisionState.resetPlayerVision()
                }
                return
            }

            // Start charging
            if (!ClientVisionState.playerVisionCharging) {
                ClientVisionState.playerVisionCharging = true
                ClientVisionState.playerVisionProgress = 0f
                ClientVisionState.playerVisionTargetName = target.displayName!!.string
                ClientVisionState.playerVisionInView = localInView

                // Notify server that charge started
                ClientPlayNetworking.send(
                    PlayerVisionChargeStartC2SPacket(target.displayName!!.string, localInView)
                )
            }

            // Update progress
            ClientVisionState.playerVisionProgress = (ClientVisionState.playerVisionProgress + inc).coerceIn(0f, 1f)

            // Fail check: damage taken
            if (player.hurtTime > 0) {
                ClientPlayNetworking.send(
                    PlayerVisionChargeCancelC2SPacket(ClientVisionState.playerVisionTargetName ?: "")
                )
                ClientVisionState.resetPlayerVision()
                triggerPlayerVisionFail(player)
                return
            }

            // Discovery check: was out-of-view but now target sees caster
            if (!ClientVisionState.playerVisionInView && localInView) {
                // Target now sees caster → discovery!
                ClientPlayNetworking.send(
                    PlayerVisionChargeCancelC2SPacket(
                        ClientVisionState.playerVisionTargetName ?: target.displayName!!.string
                    )
                )
                // Send request with wasInView=false — server will detect inView=true → discovery
                ClientPlayNetworking.send(
                    PlayerVisionRequestC2SPacket(
                        ClientVisionState.playerVisionTargetName ?: target.displayName!!.string,
                        false
                    )
                )
                ClientVisionState.resetPlayerVision()
                return
            }

            // Progress complete → send request
            if (ClientVisionState.playerVisionProgress >= 1f) {
                val targetName = ClientVisionState.playerVisionTargetName
                if (targetName != null) {
                    ClientPlayNetworking.send(
                        PlayerVisionRequestC2SPacket(targetName, ClientVisionState.playerVisionInView)
                    )
                }
                ClientVisionState.resetPlayerVision()
                ClientVisionState.playerVisionLocked = true
            }
        } else if (!useDown) {
            // Key released → cancel charge
            if (ClientVisionState.playerVisionCharging) {
                ClientPlayNetworking.send(
                    PlayerVisionChargeCancelC2SPacket(ClientVisionState.playerVisionTargetName ?: "")
                )
            }
            ClientVisionState.resetPlayerVision()
        }
    }

    /**
     * Check if target is within the configured box range centered on caster.
     */
    private fun isInPlayerVisionRange(caster: net.minecraft.world.entity.player.Player, target: net.minecraft.world.entity.player.Player): Boolean {
        // Determine level for range: L3=14, L2=7, L1=config box
        val level = ClientVisionState.cachedPlayerVisionLevel
            ?: ClientVisionState.getPlayerVisionLevel()
        if (level == com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_3) {
            return caster.distanceTo(target) <= 14.0
        }
        if (level == com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_2) {
            return caster.distanceTo(target) <= 7.0
        }
        val config = ClientVisionState.cachedConfig ?: return true
        val dx = Math.abs(target.x - caster.x)
        val dy = Math.abs(target.eyeY - caster.eyeY)
        val dz = Math.abs(target.z - caster.z)
        val halfX = config.playerVisionRangeX / 2.0
        val halfY = config.playerVisionRangeY / 2.0
        val halfZ = config.playerVisionRangeZ / 2.0
        return dx <= halfX && dy <= halfY && dz <= halfZ
    }

    /**
     * Trigger a fail with tellraw suppression and cooldown.
     */
    private fun triggerPlayerVisionFail(player: net.minecraft.client.player.LocalPlayer) {
        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal("幻视被阻挠了...")
                .withStyle { it.withColor(0xFF5555) },
            true
        )
        // Suppress tellraw for 3 seconds
        ClientVisionState.suppressTellraw(3)
        // Apply fail cooldown
        val failCooldownMin = ClientVisionState.cachedConfig?.playerVisionFailCooldown ?: 6
        ClientVisionState.playerVisionCooldown = failCooldownMin * 60 * 20
    }

    /**
     * Open the player selection screen for Level 2/3 player vision.
     * Scans online players within a spherical radius (L2=7, L3=14) in the caster's 80° FOV.
     */
    private fun openPlayerSelectScreen(client: Minecraft) {
        val caster = client.player ?: return
        val world = client.level ?: return
        val config = ClientVisionState.cachedConfig ?: return

        val level = ClientVisionState.cachedPlayerVisionLevel
            ?: ClientVisionState.getPlayerVisionLevel()
        // L3=14, L2=7
        val maxDist = if (level == com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_3) 14.0 else 7.0
        val maxDistSq = maxDist * maxDist

        val playersInFov = world.players().filter { other ->
            other != caster &&
            caster.distanceToSqr(other) <= maxDistSq &&
            isInClientFov(caster, other)
        }.map { player ->
            val tagText = config.playerVisionPlayerTags[player.displayName!!.string] ?: "无"
            com.example.vision_optimize.gui.PlayerVisionSelectScreen.PlayerFovEntry(
                playerName = player.displayName!!.string,
                tagText = tagText
            )
        }

        // Send look notification to server (triggers target warnings)
        if (playersInFov.isNotEmpty()) {
            ClientPlayNetworking.send(
                com.example.vision_optimize.network.PlayerVisionLookC2SPacket(
                    playersInFov.map { it.playerName }
                )
            )
        }

        com.example.vision_optimize.gui.PlayerVisionSelectScreen.open(playersInFov)
    }

    /** Client-side FOV check: dot product > -0.2 (~102 degree cone, wider than server for usability). */
    private fun isInClientFov(caster: net.minecraft.world.entity.player.Player, target: net.minecraft.world.entity.player.Player): Boolean {
        val lookDir = caster.lookAngle
        val toTarget = net.minecraft.world.phys.Vec3(
            target.x - caster.x,
            target.eyeY - caster.eyeY,
            target.z - caster.z
        ).normalize()
        return lookDir.dot(toTarget) > 0.766  // 80° cone (±40° from center, cos(40°)≈0.766)
    }

    /** Check if any other player is currently within range (L2=7, L3=14) and in the caster's FOV. */
    private fun hasAnyPlayerInFov(client: Minecraft): Boolean {
        val caster = client.player ?: return false
        val world = client.level ?: return false
        val level = ClientVisionState.cachedPlayerVisionLevel
            ?: ClientVisionState.getPlayerVisionLevel()
        val maxDist = if (level == com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_3) 14.0 else 7.0
        val maxDistSq = maxDist * maxDist

        return world.players().any { other ->
            other != caster &&
            caster.distanceToSqr(other) <= maxDistSq &&
            isInClientFov(caster, other)
        }
    }

}
