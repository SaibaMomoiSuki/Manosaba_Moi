package com.example.vision_optimize.vision.item

import com.example.vision_optimize.config.ModConfigManager
import com.example.vision_optimize.config.VisionLevel
import com.example.vision_optimize.network.ItemVisionResultS2CPacket
import com.example.vision_optimize.tracking.ItemTracker
import com.example.vision_optimize.tracking.ItemTrackingComponent
import com.example.vision_optimize.util.TextBuilder
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Central handler for item vision requests.
 *
 * Probability system:
 * - Default success rate: 70% (configurable)
 * - On failure: darkness 3s + yellow action bar text, item gets 2min cooldown
 * - On success: normal display, probability resets to initial
 * - Each failure adds failAdd% (default 15%) to the player's current probability
 */
object ItemVisionHandler {

    /** Per-player current item vision success probability (0-100). */
    private val playerProbabilities: ConcurrentHashMap<String, Int> = ConcurrentHashMap()

    /** Per-item fail cooldown end timestamps: "playerName:trackingId" -> epoch millis. */
    private val itemFailCooldowns: ConcurrentHashMap<String, Long> = ConcurrentHashMap()

    /**
     * Handle an item vision request.
     */
    fun handleRequest(player: ServerPlayer, stack: ItemStack) {
        if (!ModConfigManager.isEnabled()) return
        if (ModConfigManager.isPlayerExcluded(player.displayName!!.string)) return
        if (stack.isEmpty) return

        // Silenced tag check: players with the "Silenced" tag cannot use vision
        if (player.tags.contains("Silenced")) {
            player.sendSystemMessage(TextBuilder.visionSilenced())
            return
        }

        val config = ModConfigManager.get()

        // Determine effective item vision level
        val itemLevel = getItemVisionLevel(player)
        if (itemLevel == VisionLevel.NONE) return

        // Ensure item has a tracking UUID (create if missing)
        val trackingId = ItemTrackingComponent.getOrCreateTrackingId(stack)
        // Always record the current holder
        ItemTracker.recordHold(trackingId, player.displayName!!.string)

        // Check if this specific item is on fail cooldown for this player
        val itemKey = "${player.displayName!!.string}:$trackingId"
        val itemCooldownEnd = itemFailCooldowns[itemKey]
        if (itemCooldownEnd != null && System.currentTimeMillis() < itemCooldownEnd) {
            player.displayClientMessage(TextBuilder.visionRetryHint(), true)
            return // Item is on cooldown, do not consume normal cooldown
        }

        // Probability roll
        val currentPercent = playerProbabilities.getOrDefault(player.displayName!!.string, config.itemVisionPercent)
        val roll = Random.nextInt(100) // 0-99
        val success = roll < currentPercent

        if (!success) {
            // Failure: apply darkness, show message, set item cooldown, increase probability
            player.addEffect(MobEffectInstance(MobEffects.DARKNESS, 60, 0)) // 3 seconds darkness
            player.displayClientMessage(TextBuilder.itemVisionFail(), true)

            // Set 2-minute cooldown for this specific item
            itemFailCooldowns[itemKey] = System.currentTimeMillis() + 120_000L

            // Increase probability
            val newPercent = (currentPercent + config.itemVisionFailAdd).coerceAtMost(100)
            playerProbabilities[player.displayName!!.string] = newPercent

            return
        }

        // Success: reset probability to initial
        playerProbabilities.remove(player.displayName!!.string)

        // Get history, excluding the requesting player's own records
        val requestingName = player.displayName!!.string
        val history = ItemTracker.getHistory(trackingId)
            .filter { it.playerName != requestingName }

        if (history.isEmpty()) {
            // If the player died within the last 10 minutes → show red nausea warning
            if (config.visionInterferenceOnDeath) {
                val deathTime = ItemTracker.getPlayerDeathTime(player.displayName!!.string)
                if (deathTime != null && (System.currentTimeMillis() - deathTime) < 600_000L) {
                    player.sendSystemMessage(TextBuilder.itemDeathNausea())
                    return
                }
            }
            player.sendSystemMessage(TextBuilder.itemNoPrevious())
            return
        }

        when (itemLevel) {
            VisionLevel.ITEM_LEVEL_1 -> ItemVisionLevel1.display(player, history, config)
            VisionLevel.ITEM_LEVEL_2 -> ItemVisionLevel2.display(player, history, config)
            else -> {}
        }
    }

    fun getItemVisionLevel(player: ServerPlayer): VisionLevel {
        val config = ModConfigManager.get()
        val name = player.displayName!!.string

        // Manosaba (魔女审判) override: while the witch trial is enabled, a player
        // with the "Vision" magic tag automatically has full vision permissions.
        if (config.hasVisionMagicFullAccess(player.tags)) {
            val level = VisionLevel.ITEM_LEVEL_2
            com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyItemVision(player, level)
            return level
        }

        // 1. Fetch player's score from configured scoreboards
        val score = getItemScoreboardScore(player, config)

        // 2. Check scoreboard-based overrides (enforced if score in range)
        val scoreLevel = config.getEffectiveItemLevel(name, score)
        if (scoreLevel != null && scoreLevel != VisionLevel.NONE) {
            com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyItemVision(player, scoreLevel)
            return scoreLevel
        }

        // 3. Check player-specific manual level assignment
        val playerLevel = config.playerItemLevels[name]
        if (playerLevel != null && playerLevel != VisionLevel.NONE) {
            com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyItemVision(player, playerLevel)
            return playerLevel
        }

        // 4. In ALL_MEMBERS mode, default to ITEM_LEVEL_1
        if (config.mode == com.example.vision_optimize.config.AccessMode.ALL_MEMBERS) {
            val defaultLevel = VisionLevel.ITEM_LEVEL_1
            com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyItemVision(player, defaultLevel)
            return defaultLevel
        }

        // 5. In TAG_MEMBER mode, check if player has any vision-enabled access tag
        if (config.mode == com.example.vision_optimize.config.AccessMode.TAG_MEMBER) {
            if (config.hasVisionAccess(player.displayName!!.string)) {
                val defaultLevel = VisionLevel.ITEM_LEVEL_1
                com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyItemVision(player, defaultLevel)
                return defaultLevel
            }
        }

        val noneLevel = VisionLevel.NONE
        com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyItemVision(player, noneLevel)
        return noneLevel
    }

    /**
     * Get the player's score from any configured item vision scoreboard.
     */
    private fun getItemScoreboardScore(player: ServerPlayer, config: com.example.vision_optimize.config.ModConfig): Double? {
        val scoreboard = player.server?.scoreboard ?: return null
        val objectives = listOf(
            config.itemL1Scoreboard,
            config.itemL2Scoreboard
        ).filter { it.isNotEmpty() }
        if (objectives.isEmpty()) return null
        val objectiveName = objectives.first()
        val objective = scoreboard.getObjective(objectiveName) ?: return null
        return com.example.vision_optimize.util.ScoreboardChecker.getScore(player, objective)
    }

    /**
     * Clear probability for a player (e.g., on disconnect).
     */
    fun clearPlayerProbability(playerName: String) {
        playerProbabilities.remove(playerName)
    }
}
