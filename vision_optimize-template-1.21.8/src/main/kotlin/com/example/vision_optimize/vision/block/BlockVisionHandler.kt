package com.example.vision_optimize.vision.block

import com.example.vision_optimize.config.ModConfigManager
import com.example.vision_optimize.config.VisionLevel
import com.example.vision_optimize.network.BlockVisionResultS2CPacket
import com.example.vision_optimize.util.TextBuilder
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Handler for block vision requests.
 * The client handles the charge/progress bar locally; the server
 * simply executes the search immediately when a request arrives
 * and sends back the results.
 *
 * Probability system:
 * - Default success rate: 70% (configurable)
 * - On failure: nausea 3s + red action bar text, block gets 2min cooldown
 * - On success: normal display, probability resets to initial
 * - Each failure adds failAdd% (default 15%) to the player's current probability
 */
object BlockVisionHandler {
    private val logger = LoggerFactory.getLogger("vision_optimize:block_vision")

    /** Per-player current block vision success probability (0-100). */
    private val playerProbabilities: ConcurrentHashMap<String, Int> = ConcurrentHashMap()

    /** Per-block fail cooldown end timestamps: "playerName:x:y:z" -> epoch millis. */
    private val blockFailCooldowns: ConcurrentHashMap<String, Long> = ConcurrentHashMap()

    /**
     * Handle a block vision request from a player.
     */
    fun handleRequest(player: ServerPlayer, blockPos: BlockPos) {
        // Check if mod is enabled
        if (!ModConfigManager.isEnabled()) return

        // Check player exclusion
        if (ModConfigManager.isPlayerExcluded(player.displayName!!.string)) return

        // Silenced tag check: players with the "Silenced" tag cannot use vision
        if (player.tags.contains("Silenced")) {
            player.sendSystemMessage(TextBuilder.visionSilenced())
            return
        }

        val config = ModConfigManager.get()

        // Determine effective block vision level
        val blockLevel = getBlockVisionLevel(player)
        if (blockLevel == VisionLevel.NONE) return

        // Check if this specific block is on fail cooldown for this player
        val blockKey = "${player.displayName!!.string}:${blockPos.x}:${blockPos.y}:${blockPos.z}"
        val blockCooldownEnd = blockFailCooldowns[blockKey]
        if (blockCooldownEnd != null && System.currentTimeMillis() < blockCooldownEnd) {
            player.displayClientMessage(TextBuilder.visionRetryHint(), true)
            return // Block is on cooldown, do not consume normal cooldown
        }

        logger.debug("Block vision request from {} at {}", player.displayName!!.string, blockPos)

        // Probability roll
        val currentPercent = playerProbabilities.getOrDefault(player.displayName!!.string, config.blockVisionPercent)
        val roll = Random.nextInt(100) // 0-99
        val success = roll < currentPercent

        if (!success) {
            // Failure: apply nausea, show message, set block cooldown, increase probability
            player.addEffect(MobEffectInstance(MobEffects.NAUSEA, 140, 0)) // 7 seconds nausea
            player.displayClientMessage(TextBuilder.blockVisionFail(), true)

            // Set 2-minute cooldown for this specific block
            blockFailCooldowns[blockKey] = System.currentTimeMillis() + 120_000L

            // Increase probability
            val newPercent = (currentPercent + config.blockVisionFailAdd).coerceAtMost(100)
            playerProbabilities[player.displayName!!.string] = newPercent

            logger.info("Block vision FAIL for {} at {} | prob {}% -> {}%", player.displayName!!.string, blockPos, currentPercent, newPercent)
            return
        }

        // Success: reset probability to initial
        playerProbabilities.remove(player.displayName!!.string)

        // Execute the search and send results
        try {
            val result = executeSearch(player, blockPos, blockLevel)
            ServerPlayNetworking.send(player, result)
        } catch (e: Exception) {
            logger.error("Error executing block vision search for {}", player.displayName!!.string, e)
        }
    }

    /**
     * Get the effective block vision level for a player.
     * In ALL_MEMBERS mode, defaults to BLOCK_LEVEL_1 if no specific level is set.
     */
    fun getBlockVisionLevel(player: ServerPlayer): VisionLevel {
        val config = ModConfigManager.get()
        val name = player.displayName!!.string

        // Manosaba (魔女审判) override: while the witch trial is enabled, a player
        // with the "Vision" magic tag automatically has full vision permissions.
        if (config.hasVisionMagicFullAccess(player.tags)) {
            val level = VisionLevel.BLOCK_LEVEL_3
            com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyBlockVision(player, level)
            return level
        }

        // 1. Fetch player's score from configured scoreboards
        val score = getBlockScoreboardScore(player, config)

        // 2. Check scoreboard-based overrides (enforced if score in range)
        val scoreLevel = config.getEffectiveBlockLevel(name, score)
        if (scoreLevel != null && scoreLevel != VisionLevel.NONE) {
            com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyBlockVision(player, scoreLevel)
            return scoreLevel
        }

        // 3. Check player-specific manual level assignment
        val playerLevel = config.playerBlockLevels[name]
        if (playerLevel != null && playerLevel != VisionLevel.NONE) {
            com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyBlockVision(player, playerLevel)
            return playerLevel
        }

        // 4. In ALL_MEMBERS mode, default to BLOCK_LEVEL_1
        if (config.mode == com.example.vision_optimize.config.AccessMode.ALL_MEMBERS) {
            val defaultLevel = VisionLevel.BLOCK_LEVEL_1
            com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyBlockVision(player, defaultLevel)
            return defaultLevel
        }

        // 5. In TAG_MEMBER mode, check if player has any vision-enabled access tag
        if (config.mode == com.example.vision_optimize.config.AccessMode.TAG_MEMBER) {
            if (config.hasVisionAccess(player.displayName!!.string)) {
                val defaultLevel = VisionLevel.BLOCK_LEVEL_1
                com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyBlockVision(player, defaultLevel)
                return defaultLevel
            }
        }

        val noneLevel = VisionLevel.NONE
        com.example.vision_optimize.vision.VisionLevelTracker.checkAndNotifyBlockVision(player, noneLevel)
        return noneLevel
    }

    /**
     * Get the player's score from any configured block vision scoreboard.
     * Returns the score value from the first matching scoreboard, or null if none configured/matched.
     */
    private fun getBlockScoreboardScore(player: ServerPlayer, config: com.example.vision_optimize.config.ModConfig): Double? {
        val scoreboard = player.server?.scoreboard ?: return null
        // Try each level's scoreboard in order
        val objectives = listOf(
            config.blockL1Scoreboard,
            config.blockL2Scoreboard,
            config.blockL3Scoreboard
        ).filter { it.isNotEmpty() }
        if (objectives.isEmpty()) return null
        // Use the first configured scoreboard to get the score
        val objectiveName = objectives.first()
        val objective = scoreboard.getObjective(objectiveName) ?: return null
        return com.example.vision_optimize.util.ScoreboardChecker.getScore(player, objective)
    }

    private fun executeSearch(
        player: ServerPlayer,
        blockPos: BlockPos,
        level: VisionLevel
    ): BlockVisionResultS2CPacket {
        val config = ModConfigManager.get()
        val now = System.currentTimeMillis()

        return when (level) {
            VisionLevel.BLOCK_LEVEL_1 -> BlockVisionLevel1.search(player, blockPos, config, now)
            VisionLevel.BLOCK_LEVEL_2 -> BlockVisionLevel2.search(player, blockPos, config, now)
            VisionLevel.BLOCK_LEVEL_3 -> BlockVisionLevel3.search(player, blockPos, config, now)
            else -> BlockVisionResultS2CPacket(0, emptyList())
        }
    }

    /**
     * Clear probability for a player (e.g., on disconnect).
     */
    fun clearPlayerProbability(playerName: String) {
        playerProbabilities.remove(playerName)
    }
}
