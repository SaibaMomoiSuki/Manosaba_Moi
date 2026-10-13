package com.example.vision_optimize.event

import com.example.vision_optimize.config.ModConfigManager
import com.example.vision_optimize.network.ModPackets
import com.example.vision_optimize.tracking.ItemTracker
import com.example.vision_optimize.tracking.ItemTrackingComponent
import com.example.vision_optimize.tracking.PlayerPathTracker
import com.example.vision_optimize.vision.player.PlayerVisionHandler
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Registers all server-side event handlers.
 *
 * Tick-based work has been consolidated into a single server-level tick event
 * instead of per-player tick injection. This eliminates the N×20/sec overhead
 * (N = player count) and keeps the player tick hot-path clean.
 *
 * Path recording and item tracking: throttled to every 5 ticks (4/sec at 20 TPS),
 * batched across all online players in a single loop.
 *
 * Death detection: checked every 20 ticks (1/sec) as a fallback for edge cases
 * not caught by the AFTER_DAMAGE event (already registered below).
 */
object ServerEventHandlers {
    private val logger = LoggerFactory.getLogger("vision_optimize:events")

    /** Per-player tick counter for throttling path recording (keyed by player name). */
    private val playerTickCounters: ConcurrentHashMap<String, Int> = ConcurrentHashMap()

    /** Tracks which players have already had their death processed (to avoid re-triggering). */
    private val processedDeaths: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Path recording interval in ticks. Must be >= 1. */
    private const val PATH_RECORD_INTERVAL = 5

    fun register() {
        // Sync config to players when they join
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            ModPackets.syncConfigToPlayer(handler.player)
            // Sync OP status to client so settings GUI can check permission
            val isOp = handler.player.hasPermissions(2)
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                handler.player,
                com.example.vision_optimize.network.OpStatusSyncS2CPacket(isOp)
            )
            // Also sync player vision level to client so P-key works immediately
            val pvLevel = com.example.vision_optimize.vision.player.PlayerVisionHandler.getPlayerVisionLevel(handler.player)
            val levelInt = when (pvLevel) {
                com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_3 -> 3
                com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_2 -> 2
                else -> 1
            }
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                handler.player,
                com.example.vision_optimize.network.PlayerVisionLevelSyncS2CPacket(levelInt)
            )
        }

        // Consolidated server tick: config sync, pruning, path recording, player vision ticking
        ServerTickEvents.END_SERVER_TICK.register { server ->
            val tickNum = server.tickCount

            // Cache config reference once per tick to avoid repeated read-lock acquisitions.
            // tickActiveCharges and tickDiscoveryStates both read config internally;
            // the read lock is cheap but avoiding repeated calls reduces contention.
            if (tickNum % 100L == 0L) {
                // Sync config to all players every 5 seconds (keeps clients in sync)
                ModPackets.syncConfigToAll(server)
            }
            if (tickNum % 1200L == 0L) { // Every 60 seconds at 20 TPS
                val config = ModConfigManager.get()
                val maxAge = maxOf(
                    config.blockL1AfterTime,
                    180,  // Level 2 is 3 min
                    config.blockL3AfterTime,
                    config.itemL2VisionTime
                ) + 60 // Add buffer
                ItemTracker.pruneOldRecords(maxAge)
            }

            // Every 20 ticks (1 sec): poll scoreboard-based level changes for instant notifications
            if (tickNum % 20L == 0L) {
                com.example.vision_optimize.vision.VisionLevelTracker.tickLevelChecks(server)
            }

            // Every 5 ticks: tick active charges + batched path recording + item tracking
            if (tickNum % 5L == 0L) {
                PlayerVisionHandler.tickActiveCharges(server)
                tickPathRecording(server)
            }
            PlayerVisionHandler.tickDiscoveryStates(server)
            PlayerVisionHandler.tickNeckDamage(server)

            // Every 20 ticks: fallback death detection for edge cases
            // (AFTER_DAMAGE handles most deaths; this catches any remaining)
            if (tickNum % 20L == 0L) {
                tickDeathDetection(server)
            }
        }

        // Load item tracker when server starts
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            val worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).parent.toFile()
            ItemTracker.load(worldDir)
            logger.info("Item tracker data loaded")
        }

        // Save item tracker when server stops
        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            val worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).parent.toFile()
            ItemTracker.save(worldDir)
            logger.info("Item tracker data saved")
        }

        // Detect attack-during-discovery for player vision (caster attacks → success, caster attacked → fail)
        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, _, _, _ ->
            if (entity is ServerPlayer && source.entity is ServerPlayer) {
                val attacker = source.entity as ServerPlayer
                val victim = entity
                PlayerVisionHandler.onPlayerAttack(attacker, victim)
                PlayerVisionHandler.onPlayerAttacked(victim, attacker)
            }

            // Death detection via damage: catches all death causes including /kill and void
            // (all go through LivingEntity.hurt() → AFTER_DAMAGE)
            if (entity is ServerPlayer && !entity.isAlive) {
                onPlayerDeath(entity, entity.server ?: return@register)
            }
        }
    }

    /**
     * Batched path recording + item tracking for all online players.
     * Throttled per-player to every PATH_RECORD_INTERVAL ticks (default 5 = 4 records/sec).
     *
     * This replaces the per-player tick injection in ServerPlayerMixin.
     * Instead of N×20 tick callbacks/sec, we have one batched loop every 5 ticks
     * that only does work for players whose throttle counter has elapsed.
     */
    private fun tickPathRecording(server: MinecraftServer) {
        // Fast path: skip if mod is disabled
        if (!ModConfigManager.isEnabled()) return

        val now = System.currentTimeMillis()
        val excludedSet = ModConfigManager.get().excludedPlayers

        for (player in server.playerList.players) {
            val playerName = player.displayName?.string ?: continue
            if (playerName in excludedSet) continue

            // Don't track dead players
            if (!player.isAlive) continue

            // Per-player throttle: increment counter, only record when threshold reached
            val counter = playerTickCounters.getOrDefault(playerName, 0) + 1
            if (counter < PATH_RECORD_INTERVAL) {
                playerTickCounters[playerName] = counter
                continue
            }
            playerTickCounters[playerName] = 0

            // Record position
            PlayerPathTracker.recordPosition(player, now)

            // Ensure main hand and offhand items have tracking UUIDs
            checkAndRecordItem(player.mainHandItem, playerName, now)
            checkAndRecordItem(player.offhandItem, playerName, now)
        }
    }

    /**
     * Ensure the item stack has a tracking UUID and record that the player holds it.
     */
    private fun checkAndRecordItem(stack: net.minecraft.world.item.ItemStack, playerName: String, timestamp: Long) {
        if (stack.isEmpty) return
        val trackingId = ItemTrackingComponent.getOrCreateTrackingId(stack)
        ItemTracker.recordHold(trackingId, playerName, timestamp)
    }

    /**
     * Fallback death detection: poll all online players and detect newly dead players.
     * Runs every 20 ticks (1/sec). Most deaths are caught instantly by AFTER_DAMAGE;
     * this is a safety net for any death causes that might bypass the damage event.
     */
    private fun tickDeathDetection(server: MinecraftServer) {
        for (player in server.playerList.players) {
            val playerName = player.displayName?.string ?: continue

            if (!player.isAlive && processedDeaths.add(playerName)) {
                onPlayerDeath(player, server)
            }

            // Clean up the death flag when player respawns
            if (player.isAlive) {
                processedDeaths.remove(playerName)
            }
        }
    }

    /**
     * Called when a player dies (health hits 0).
     * Clears all players' paths and resets item holder records per gamerule config.
     */
    private fun onPlayerDeath(player: ServerPlayer, server: MinecraftServer) {
        val config = ModConfigManager.get()

        // Clear ALL players' paths (same as /vision_optimize PathClear)
        if (config.pathClearOnDeath) {
            PlayerPathTracker.clearAllPaths(server)
        }

        // Clear all item holder records and record this death
        // (does NOT touch item tracking UUIDs — only clears the holder history)
        if (config.visionInterferenceOnDeath) {
            ItemTracker.clearAll()
            ItemTracker.clearDeathTimestamps()
            ItemTracker.recordPlayerDeath(player.displayName?.string ?: return, System.currentTimeMillis())
        }
    }
}
