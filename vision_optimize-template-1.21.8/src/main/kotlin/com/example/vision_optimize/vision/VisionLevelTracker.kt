package com.example.vision_optimize.vision

import com.example.vision_optimize.config.VisionLevel
import com.example.vision_optimize.network.PlayerVisionLevelSyncS2CPacket
import com.example.vision_optimize.util.TextBuilder
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks the effective vision level for each player across all three vision types
 * and sends level-up (green) / level-down (red) notifications when a change is detected.
 *
 * Integration points:
 * - Command handlers (VisionCommand): call notify*() after updating player level config.
 * - Vision handlers (getXxxVisionLevel): call notify*() when the effective level changes
 *   (covers both manual assignment and scoreboard-based auto-detection).
 */
object VisionLevelTracker {

    /** playerName -> VisionLevel */
    private val playerVisionLevels: ConcurrentHashMap<String, VisionLevel> = ConcurrentHashMap()
    private val itemVisionLevels: ConcurrentHashMap<String, VisionLevel> = ConcurrentHashMap()
    private val blockVisionLevels: ConcurrentHashMap<String, VisionLevel> = ConcurrentHashMap()

    /** Timestamp of last downgrade notification per player per type (epoch millis). Suppresses re-trigger for 3s. */
    private val downgradeCooldownPlayer: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
    private val downgradeCooldownItem: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
    private val downgradeCooldownBlock: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
    /** Global downgrade cooldown per player: only one "能力好像减弱了..." message per player per 3s across all types. */
    private val downgradeCooldownGlobal: ConcurrentHashMap<String, Long> = ConcurrentHashMap()

    // ===== Player Vision =====

    /**
     * Check for a player vision level change and send notification if needed.
     * Only sends messages if the player has general vision access.
     * @param player The server player.
     * @param newLevel The newly computed effective player vision level.
     */
    fun checkAndNotifyPlayerVision(player: ServerPlayer, newLevel: VisionLevel) {
        val name = player.displayName!!.string
        val oldLevel = playerVisionLevels[name] ?: VisionLevel.NONE

        if (oldLevel == newLevel) return

        // Skip notifications for players without general vision access
        if (!hasGeneralVisionAccess(player)) {
            playerVisionLevels[name] = newLevel
            return
        }

        // Downgrade (any → lower level, including → NONE)
        if (isDowngrade(oldLevel, newLevel)) {
            val now = System.currentTimeMillis()
            val lastDowngrade = downgradeCooldownPlayer.getOrDefault(name, 0L)
            val lastGlobal = downgradeCooldownGlobal.getOrDefault(name, 0L)
            if (now - lastDowngrade < 3000L || now - lastGlobal < 3000L) {
                playerVisionLevels[name] = newLevel
                return
            }
            downgradeCooldownPlayer[name] = now
            downgradeCooldownGlobal[name] = now
            player.sendSystemMessage(TextBuilder.visionLevelDown())
            syncLevelToClient(player, newLevel)
            playerVisionLevels[name] = newLevel
            return
        }

        // Upgrade
        when {
            oldLevel == VisionLevel.PLAYER_VISION_LEVEL_1 && newLevel == VisionLevel.PLAYER_VISION_LEVEL_2 -> {
                player.sendSystemMessage(TextBuilder.playerVisionLevelUpEnhanced())
                player.sendSystemMessage(TextBuilder.playerVisionLevelUpKeyHint())
            }
            oldLevel == VisionLevel.PLAYER_VISION_LEVEL_1 && newLevel == VisionLevel.PLAYER_VISION_LEVEL_3 -> {
                player.sendSystemMessage(TextBuilder.playerVisionLevelUpEnhanced())
                player.sendSystemMessage(TextBuilder.playerVisionLevelUpKeyHint())
            }
            oldLevel == VisionLevel.PLAYER_VISION_LEVEL_2 && newLevel == VisionLevel.PLAYER_VISION_LEVEL_3 -> {
                player.sendSystemMessage(TextBuilder.playerVisionLevelUp2to3())
            }
            // oldLevel NONE → any player vision level: no notification (initial unlock)
        }

        syncLevelToClient(player, newLevel)
        playerVisionLevels[name] = newLevel
    }

    // ===== Item Vision =====

    fun checkAndNotifyItemVision(player: ServerPlayer, newLevel: VisionLevel) {
        val name = player.displayName!!.string
        val oldLevel = itemVisionLevels[name] ?: VisionLevel.NONE

        if (oldLevel == newLevel) return

        if (!hasGeneralVisionAccess(player)) {
            itemVisionLevels[name] = newLevel
            return
        }

        // Downgrade
        if (isDowngrade(oldLevel, newLevel)) {
            val now = System.currentTimeMillis()
            val lastDowngrade = downgradeCooldownItem.getOrDefault(name, 0L)
            val lastGlobal = downgradeCooldownGlobal.getOrDefault(name, 0L)
            if (now - lastDowngrade < 3000L || now - lastGlobal < 3000L) {
                itemVisionLevels[name] = newLevel
                return
            }
            downgradeCooldownItem[name] = now
            downgradeCooldownGlobal[name] = now
            player.sendSystemMessage(TextBuilder.visionLevelDown())
            itemVisionLevels[name] = newLevel
            return
        }

        // Upgrade
        when {
            oldLevel == VisionLevel.ITEM_LEVEL_1 && newLevel == VisionLevel.ITEM_LEVEL_2 -> {
                player.sendSystemMessage(TextBuilder.itemVisionLevelUp1to2())
            }
            // NONE → ITEM_LEVEL_1: no notification (initial unlock)
        }

        itemVisionLevels[name] = newLevel
    }

    // ===== Block Vision =====

    fun checkAndNotifyBlockVision(player: ServerPlayer, newLevel: VisionLevel) {
        val name = player.displayName!!.string
        val oldLevel = blockVisionLevels[name] ?: VisionLevel.NONE

        if (oldLevel == newLevel) return

        if (!hasGeneralVisionAccess(player)) {
            blockVisionLevels[name] = newLevel
            return
        }

        // Downgrade
        if (isDowngrade(oldLevel, newLevel)) {
            val now = System.currentTimeMillis()
            val lastDowngrade = downgradeCooldownBlock.getOrDefault(name, 0L)
            val lastGlobal = downgradeCooldownGlobal.getOrDefault(name, 0L)
            if (now - lastDowngrade < 3000L || now - lastGlobal < 3000L) {
                blockVisionLevels[name] = newLevel
                return
            }
            downgradeCooldownBlock[name] = now
            downgradeCooldownGlobal[name] = now
            player.sendSystemMessage(TextBuilder.visionLevelDown())
            blockVisionLevels[name] = newLevel
            return
        }

        // Upgrade
        when {
            oldLevel == VisionLevel.BLOCK_LEVEL_1 && newLevel == VisionLevel.BLOCK_LEVEL_2 -> {
                player.sendSystemMessage(TextBuilder.blockVisionLevelUp1to2())
            }
            oldLevel == VisionLevel.BLOCK_LEVEL_1 && newLevel == VisionLevel.BLOCK_LEVEL_3 -> {
                player.sendSystemMessage(TextBuilder.blockVisionLevelUpTo3_line1())
                player.sendSystemMessage(TextBuilder.blockVisionLevelUpTo3_line2())
                player.sendSystemMessage(TextBuilder.blockVisionLevelUpTo3_line3())
            }
            oldLevel == VisionLevel.BLOCK_LEVEL_2 && newLevel == VisionLevel.BLOCK_LEVEL_3 -> {
                player.sendSystemMessage(TextBuilder.blockVisionLevelUpTo3_line1())
                player.sendSystemMessage(TextBuilder.blockVisionLevelUpTo3_line2())
                player.sendSystemMessage(TextBuilder.blockVisionLevelUpTo3_line3())
            }
            // NONE → BLOCK_LEVEL_1: no notification (initial unlock)
        }

        blockVisionLevels[name] = newLevel
    }

    // ===== Helpers =====

    /** Check if a player has general vision access (same logic as VisionCommand.hasGeneralVisionAccess). */
    private fun hasGeneralVisionAccess(player: ServerPlayer): Boolean {
        val config = com.example.vision_optimize.config.ModConfigManager.get()
        if (!config.enabled) return false
        val name = player.displayName!!.string
        if (config.excludedPlayers.contains(name)) return false
        if (config.mode == com.example.vision_optimize.config.AccessMode.ALL_MEMBERS) return true
        return config.hasVisionAccess(name)
    }

    /**
     * Determine if [newLevel] is a downgrade from [oldLevel] within the same vision type.
     * Only compares levels of the same category (player/item/block).
     * Returns true when:
     * - oldLevel is a higher level than newLevel within the same category
     * - newLevel is NONE (removed access)
     */
    private fun isDowngrade(oldLevel: VisionLevel, newLevel: VisionLevel): Boolean {
        // If new level is NONE (and old wasn't), it's a downgrade (access removed)
        if (newLevel == VisionLevel.NONE && oldLevel != VisionLevel.NONE) return true

        val oldRank = levelRank(oldLevel)
        val newRank = levelRank(newLevel)
        // Only compare if they're in the same category
        if (oldRank.category != 0 && oldRank.category == newRank.category) {
            return newRank.rank < oldRank.rank
        }
        return false
    }

    /**
     * Assign a category and ranking to each VisionLevel for comparison.
     * category: 1=player, 2=item, 3=block, 0=NONE
     * rank: 1-3 within category
     */
    private fun levelRank(level: VisionLevel): LevelRank = when (level) {
        VisionLevel.NONE -> LevelRank(0, 0)
        VisionLevel.PLAYER_VISION_LEVEL_1 -> LevelRank(1, 1)
        VisionLevel.PLAYER_VISION_LEVEL_2 -> LevelRank(1, 2)
        VisionLevel.PLAYER_VISION_LEVEL_3 -> LevelRank(1, 3)
        VisionLevel.ITEM_LEVEL_1 -> LevelRank(2, 1)
        VisionLevel.ITEM_LEVEL_2 -> LevelRank(2, 2)
        VisionLevel.BLOCK_LEVEL_1 -> LevelRank(3, 1)
        VisionLevel.BLOCK_LEVEL_2 -> LevelRank(3, 2)
        VisionLevel.BLOCK_LEVEL_3 -> LevelRank(3, 3)
    }

    private data class LevelRank(val category: Int, val rank: Int)

    /** Sync the player's effective vision level to their client. */
    private fun syncLevelToClient(player: ServerPlayer, level: VisionLevel) {
        val levelInt = when (level) {
            VisionLevel.PLAYER_VISION_LEVEL_3 -> 3
            VisionLevel.PLAYER_VISION_LEVEL_2 -> 2
            else -> 1
        }
        ServerPlayNetworking.send(player, PlayerVisionLevelSyncS2CPacket(levelInt))
    }

    // ===== Periodic Scoreboard Polling =====

    /**
     * Periodically check all online players' effective vision levels against tracked levels.
     * Called every 20 ticks from the server tick loop.
     * This ensures scoreboard-based level changes trigger notifications within ~1 second,
     * rather than only when the player next uses a vision ability.
     */
    fun tickLevelChecks(server: net.minecraft.server.MinecraftServer) {
        val config = com.example.vision_optimize.config.ModConfigManager.get()
        if (!config.enabled) return

        for (player in server.playerList.players) {
            val name = player.displayName!!.string
            if (config.excludedPlayers.contains(name)) continue

            try {
                // Each getXxxVisionLevel() internally calls checkAndNotifyXxxVision()
                com.example.vision_optimize.vision.player.PlayerVisionHandler.getPlayerVisionLevel(player)
                com.example.vision_optimize.vision.block.BlockVisionHandler.getBlockVisionLevel(player)
                com.example.vision_optimize.vision.item.ItemVisionHandler.getItemVisionLevel(player)
            } catch (_: Exception) {
                // Silently skip players that cause errors
            }
        }
    }

    // ===== Cleanup =====

    fun clearPlayer(playerName: String) {
        playerVisionLevels.remove(playerName)
        itemVisionLevels.remove(playerName)
        blockVisionLevels.remove(playerName)
    }
}
