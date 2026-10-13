package com.example.vision_optimize.tracking

import com.example.vision_optimize.attachment.ModAttachments
import com.example.vision_optimize.attachment.PlayerPathData
import com.example.vision_optimize.config.ModConfigManager
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer

/**
 * Data class for a player match in a path search query.
 */
data class PathMatch(
    val playerName: String,
    val latestTimestamp: Long  // most recent timestamp within the search area+time
)

/**
 * High-level interface for querying player path data.
 */
object PlayerPathTracker {

    /**
     * Record a player's current position.
     */
    fun recordPosition(player: ServerPlayer, timestamp: Long = System.currentTimeMillis()) {
        if (ModConfigManager.isPlayerExcluded(player.displayName!!.string)) return

        val pathData = player.getAttachedOrCreate(ModAttachments.PLAYER_PATH) { PlayerPathData() }
        val worldKey = player.level().dimension().location().toString()
        val pos = player.position()

        pathData.addRecord(PathRecord(
            x = pos.x,
            y = pos.y,
            z = pos.z,
            worldKey = worldKey,
            timestamp = timestamp
        ))
    }

    /**
     * Query players who passed through a cubic area within the specified time window.
     * Only checks online players.
     *
     * @param requestingPlayer The player making the query (excluded from results)
     * @param center Center block position of the search area
     * @param scopeX X half-extent
     * @param scopeY Y half-extent
     * @param scopeZ Z half-extent
     * @param afterTimeSec Only include records more recent than this many seconds ago
     * @return List of PathMatch sorted by most recent first
     */
    fun queryPlayersInArea(
        requestingPlayer: ServerPlayer,
        center: BlockPos,
        scopeX: Int,
        scopeY: Int,
        scopeZ: Int,
        afterTimeSec: Int,
        currentTime: Long = System.currentTimeMillis()
    ): List<PathMatch> {
        val worldKey = requestingPlayer.level().dimension().location().toString()
        val server = requestingPlayer.server ?: return emptyList()

        return server.playerList.players
            .filter { it != requestingPlayer && !ModConfigManager.isPlayerExcluded(it.displayName!!.string) }
            .mapNotNull { player ->
                val pathData = player.getAttached(ModAttachments.PLAYER_PATH) ?: return@mapNotNull null
                val matchingRecords = pathData.queryInArea(
                    center, scopeX, scopeY, scopeZ,
                    afterTimeSec, currentTime, worldKey
                )
                if (matchingRecords.isNotEmpty()) {
                    PathMatch(
                        playerName = player.displayName!!.string,
                        latestTimestamp = matchingRecords.first().timestamp
                    )
                } else null
            }
            .sortedByDescending { it.latestTimestamp }
    }

    /**
     * Query all player path records for all online players in an area.
     * Returns raw PathRecord list with player info.
     */
    data class DetailedPathMatch(
        val playerName: String,
        val record: PathRecord
    )

    fun queryDetailedInArea(
        requestingPlayer: ServerPlayer,
        center: BlockPos,
        scopeX: Int,
        scopeY: Int,
        scopeZ: Int,
        afterTimeSec: Int,
        currentTime: Long = System.currentTimeMillis()
    ): List<DetailedPathMatch> {
        val worldKey = requestingPlayer.level().dimension().location().toString()
        val server = requestingPlayer.server ?: return emptyList()

        return server.playerList.players
            .filter { it != requestingPlayer && !ModConfigManager.isPlayerExcluded(it.displayName!!.string) }
            .flatMap { player ->
                val pathData = player.getAttached(ModAttachments.PLAYER_PATH) ?: return@flatMap emptyList()
                pathData.queryInArea(
                    center, scopeX, scopeY, scopeZ,
                    afterTimeSec, currentTime, worldKey
                ).map { record ->
                    DetailedPathMatch(
                        playerName = player.displayName!!.string,
                        record = record
                    )
                }
            }
            .sortedByDescending { it.record.timestamp }
    }

    /**
     * Clear path data for a specific player.
     */
    fun clearPlayerPath(player: ServerPlayer) {
        player.removeAttached(ModAttachments.PLAYER_PATH)
    }

    /**
     * Clear path data for all online players.
     */
    fun clearAllPaths(server: net.minecraft.server.MinecraftServer) {
        server.playerList.players.forEach { player ->
            player.removeAttached(ModAttachments.PLAYER_PATH)
        }
    }
}
