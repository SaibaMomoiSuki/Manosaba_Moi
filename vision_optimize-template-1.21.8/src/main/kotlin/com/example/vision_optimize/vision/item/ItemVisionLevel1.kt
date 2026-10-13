package com.example.vision_optimize.vision.item

import com.example.vision_optimize.config.ModConfig
import com.example.vision_optimize.tracking.HolderRecord
import com.example.vision_optimize.util.ManosabaCharacters
import com.example.vision_optimize.util.TextBuilder
import net.minecraft.server.level.ServerPlayer

/**
 * Item Vision Level 1:
 * Shows all previous holders from most recent to oldest.
 * Headers in yellow, names in original color.
 * If no one held it in 3 real days: "没有前人拿过这件物品"
 */
object ItemVisionLevel1 {

    fun display(player: ServerPlayer, history: List<HolderRecord>, config: ModConfig) {
        val now = System.currentTimeMillis()
        val threeDaysMs = 3L * 24 * 60 * 60 * 1000

        // Filter to last 3 real days and sort most recent first
        val recentHistory = history
            .filter { (now - it.timestamp) < threeDaysMs }
            .sortedByDescending { it.timestamp }
            .take(config.itemL1MaxDisplay)

        if (recentHistory.isEmpty()) {
            player.sendSystemMessage(TextBuilder.itemNoPrevious())
            return
        }

        // Display header
        player.sendSystemMessage(TextBuilder.itemVisionHeader())

        // Display each holder with manosaba character name resolution
        val server = player.server
        for (record in recentHistory) {
            val displayName = if (server != null) {
                ManosabaCharacters.resolveManosabaNameServerColored(record.playerName, server)
            } else {
                TextBuilder.playerName(record.playerName)
            }
            player.sendSystemMessage(displayName)
        }
    }
}
