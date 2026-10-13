package com.example.vision_optimize.vision.block

import com.example.vision_optimize.config.ModConfig
import com.example.vision_optimize.network.BlockVisionResultEntry
import com.example.vision_optimize.network.BlockVisionResultS2CPacket
import com.example.vision_optimize.tracking.PlayerPathTracker
import com.example.vision_optimize.util.ManosabaCharacters
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer

/**
 * Block Vision Level 1:
 * Search 5x5x5 area for players who passed within last [blockL1AfterTime] seconds.
 * Single player found: show separately
 * Multiple players within [blockL1PlayerInterval] seconds: group as "together"
 */
object BlockVisionLevel1 {

    fun search(
        player: ServerPlayer,
        blockPos: BlockPos,
        config: ModConfig,
        currentTime: Long
    ): BlockVisionResultS2CPacket {
        val scopeX = config.blockL1ScopeX
        val scopeY = config.blockL1ScopeY
        val scopeZ = config.blockL1ScopeZ
        val afterTime = config.blockL1AfterTime
        val playerInterval = config.blockL1PlayerInterval

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
        if (matches.isEmpty()) {
            return BlockVisionResultS2CPacket(1, emptyList())
        }

        // Resolve manosaba character names server-side (checks vanilla /tag too)
        val resolvedNames = if (server != null) {
            matches.associate { it.playerName to ManosabaCharacters.resolveManosabaNameServer(it.playerName, server) }
        } else emptyMap()

        // Group players who entered within [playerInterval] seconds of each other
        val entries = mutableListOf<BlockVisionResultEntry>()
        val timeWindow = playerInterval * 1000L

        // Sort by latest timestamp descending
        val sorted = matches.sortedByDescending { it.latestTimestamp }

        var groupId = 0
        var i = 0

        while (i < sorted.size) {
            val current = sorted[i]
            val groupMembers = mutableListOf(current)

            // Check subsequent players
            var j = i + 1
            while (j < sorted.size) {
                val next = sorted[j]
                // If this player entered within the interval of the current player
                if (kotlin.math.abs(current.latestTimestamp - next.latestTimestamp) <= timeWindow) {
                    groupMembers.add(next)
                } else {
                    break
                }
                j++
            }

            // Add entries
            if (groupMembers.size == 1) {
                entries.add(BlockVisionResultEntry(
                    playerName = resolvedNames[current.playerName] ?: current.playerName,
                    timeSeconds = (currentTime - current.latestTimestamp) / 1000L,
                    groupId = -1
                ))
            } else {
                groupMembers.forEach { member ->
                    entries.add(BlockVisionResultEntry(
                        playerName = resolvedNames[member.playerName] ?: member.playerName,
                        timeSeconds = (currentTime - member.latestTimestamp) / 1000L,
                        groupId = groupId
                    ))
                }
                groupId++
            }

            i = j
        }

        return BlockVisionResultS2CPacket(1, entries)
    }
}
