package com.example.vision_optimize.vision.item

import com.example.vision_optimize.config.ModConfig
import com.example.vision_optimize.tracking.HolderRecord
import com.example.vision_optimize.util.ManosabaCharacters
import com.example.vision_optimize.util.TextBuilder
import net.minecraft.server.level.ServerPlayer

/**
 * Item Vision Level 2:
 * Shows holders from the last [itemL2VisionTime] seconds.
 * At minimum shows what Level 1 would show.
 */
object ItemVisionLevel2 {

    fun display(player: ServerPlayer, history: List<HolderRecord>, config: ModConfig) {
        val now = System.currentTimeMillis()
        val windowMs = config.itemL2VisionTime * 1000L
        val threeDaysMs = 3L * 24 * 60 * 60 * 1000

        // Get recent holders within the vision window
        val recentHistory = history
            .filter { (now - it.timestamp) < windowMs }
            .sortedByDescending { it.timestamp }

        // If no recent holders, fall back to Level 1 display
        if (recentHistory.isEmpty()) {
            ItemVisionLevel1.display(player, history, config)
            return
        }

        // Also ensure at minimum Level 1's count
        val allRecent = history
            .filter { (now - it.timestamp) < threeDaysMs }
            .sortedByDescending { it.timestamp }
            .take(maxOf(recentHistory.size, config.itemL1MaxDisplay))

        // Display header
        player.sendSystemMessage(TextBuilder.itemVisionHeader())

        // Display each holder with manosaba character name resolution
        val server = player.server
        for (record in allRecent) {
            val displayName = if (server != null) {
                ManosabaCharacters.resolveManosabaNameServerColored(record.playerName, server)
            } else {
                TextBuilder.playerName(record.playerName)
            }
            player.sendSystemMessage(displayName)
        }
    }
}
