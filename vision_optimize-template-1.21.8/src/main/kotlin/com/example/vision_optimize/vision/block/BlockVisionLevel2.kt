package com.example.vision_optimize.vision.block

import com.example.vision_optimize.config.ModConfig
import com.example.vision_optimize.network.BlockVisionResultEntry
import com.example.vision_optimize.network.BlockVisionResultS2CPacket
import com.example.vision_optimize.tracking.PlayerPathTracker
import com.example.vision_optimize.util.ManosabaCharacters
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer

/**
 * Block Vision Level 2:
 * Search area for players who passed within 3 minutes.
 * 4 time categories:
 * - groupId 0: <10 secs: "在我附近" (RED)
 * - groupId 1: 10s-1min: "不久前经过这里..." (YELLOW)
 * - groupId 2: 1min-2min: "最近经过这里..." (CYAN)
 * - groupId 3: 2min-3min: "曾近经过这里..." (GREEN)
 */
object BlockVisionLevel2 {

    fun search(
        player: ServerPlayer,
        blockPos: BlockPos,
        config: ModConfig,
        currentTime: Long
    ): BlockVisionResultS2CPacket {
        val scopeX = config.blockL2ScopeX
        val scopeY = config.blockL2ScopeY
        val scopeZ = config.blockL2ScopeZ

        // Level 2 max lookback is SideEffect4 max or 180 seconds
        val maxLookback = (config.blockL2SideEffect4Max ?: 180.0).toInt()

        val matches = PlayerPathTracker.queryPlayersInArea(
            requestingPlayer = player,
            center = blockPos,
            scopeX = scopeX,
            scopeY = scopeY,
            scopeZ = scopeZ,
            afterTimeSec = maxLookback,
            currentTime = currentTime
        )

        val server = player.server
        if (matches.isEmpty()) {
            return BlockVisionResultS2CPacket(2, emptyList())
        }

        // Resolve manosaba character names server-side
        val resolvedNames = if (server != null) {
            matches.associate { it.playerName to ManosabaCharacters.resolveManosabaNameServer(it.playerName, server) }
        } else emptyMap()

        val entries = mutableListOf<BlockVisionResultEntry>()

        val se1 = config.blockL2SideEffect1.toLong()
        val se2Max = (config.blockL2SideEffect2Max ?: 60.0).toLong()
        val se3Max = (config.blockL2SideEffect3Max ?: 120.0).toLong()
        val se4Max = (config.blockL2SideEffect4Max ?: 180.0).toLong()

        for (match in matches.sortedByDescending { it.latestTimestamp }) {
            val secondsAgo = (currentTime - match.latestTimestamp) / 1000L

            // Classify: each range is [lower, upper) — left-closed right-open, last is [lower, upper]
            // 附近: [0, SE1)  不久前: [SE1, SE2_max)  最近: [SE2_max, SE3_max)  曾经: [SE3_max, SE4_max]
            val category = when {
                secondsAgo < se1 -> 0   // 附近: 0 ~ 无限接近SE1
                secondsAgo < se2Max -> 1  // 不久前: SE1 ~ 无限接近SE2_max
                secondsAgo < se3Max -> 2  // 最近: SE2_max ~ 无限接近SE3_max
                secondsAgo <= se4Max -> 3  // 曾经: SE3_max ~ SE4_max (闭区间)
                else -> continue
            }

            entries.add(BlockVisionResultEntry(
                playerName = resolvedNames[match.playerName] ?: match.playerName,
                timeSeconds = secondsAgo,
                groupId = category
            ))
        }

        return BlockVisionResultS2CPacket(2, entries)
    }
}
