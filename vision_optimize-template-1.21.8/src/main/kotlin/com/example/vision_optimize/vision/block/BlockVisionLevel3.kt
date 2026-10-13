package com.example.vision_optimize.vision.block

import com.example.vision_optimize.config.ModConfig
import com.example.vision_optimize.network.BlockVisionResultEntry
import com.example.vision_optimize.network.BlockVisionResultS2CPacket
import com.example.vision_optimize.tracking.PlayerPathTracker
import com.example.vision_optimize.util.ManosabaCharacters
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer

/**
 * Block Vision Level 3:
 * Search area for players who passed within [blockL3AfterTime] seconds.
 * Display exact time: "<name>在<seconds>秒之前经过这里"
 */
object BlockVisionLevel3 {

    fun search(
        player: ServerPlayer,
        blockPos: BlockPos,
        config: ModConfig,
        currentTime: Long
    ): BlockVisionResultS2CPacket {
        val scopeX = config.blockL3ScopeX
        val scopeY = config.blockL3ScopeY
        val scopeZ = config.blockL3ScopeZ
        val afterTime = config.blockL3AfterTime

        val matches = PlayerPathTracker.queryPlayersInArea(
            requestingPlayer = player,
            center = blockPos,
            scopeX = scopeX,
            scopeY = scopeY,
            scopeZ = scopeZ,
            afterTimeSec = afterTime,
            currentTime = currentTime
        )

        val server = player.server
        val entries = matches.map { match ->
            val resolved = if (server != null) ManosabaCharacters.resolveManosabaNameServer(match.playerName, server) else match.playerName
            BlockVisionResultEntry(
                playerName = resolved,
                timeSeconds = (currentTime - match.latestTimestamp) / 1000L,
                groupId = -1
            )
        }

        return BlockVisionResultS2CPacket(3, entries)
    }
}
