package com.example.vision_optimize.attachment

import com.example.vision_optimize.tracking.PathRecord
import net.minecraft.core.BlockPos

/**
 * Per-player path history data.
 * Stored as a defaulted (non-persistent) Attachment on ServerPlayer entities.
 * Data survives dimension changes but is NOT serialized to NBT — eliminating
 * the serialization overhead on every player save (auto-save, disconnect, etc.).
 * Since the max lookback is 5 minutes and server restarts take longer, losing
 * data on restart is acceptable.
 *
 * Automatically prunes records older than the maximum possible lookback time,
 * and deduplicates stationary positions to reduce memory usage.
 */
class PlayerPathData(
    val records: MutableList<PathRecord> = mutableListOf()
) {
    companion object {
        /** Maximum lookback any block vision level could need (Level 2 SE4 max = 180s, plus buffer). */
        private const val MAX_LOOKBACK_SECONDS = 300 // 5 minutes — well above any configured max
    }

    /**
     * Add a new position record, with automatic pruning and deduplication.
     *
     * Deduplication: if the player hasn't moved from their last recorded position
     * (same block-aligned coordinates within 1-block tolerance), skip the record.
     * This significantly reduces data volume for stationary players (AFK, building, etc.).
     *
     * Auto-pruning: when the record count exceeds the threshold, purge records older
     * than MAX_LOOKBACK_SECONDS to keep memory bounded.
     */
    fun addRecord(record: PathRecord) {
        // Deduplication: skip if position hasn't changed significantly from last record
        val last = records.lastOrNull()
        if (last != null &&
            last.worldKey == record.worldKey &&
            kotlin.math.abs(last.x - record.x) < 1.0 &&
            kotlin.math.abs(last.y - record.y) < 1.0 &&
            kotlin.math.abs(last.z - record.z) < 1.0
        ) {
            // Position unchanged (within 1-block tolerance) — skip duplicate record
            return
        }

        records.add(record)

        // Auto-prune: when list exceeds safe size, remove stale records.
        // At 4 records/sec (every 5 ticks) with 5min lookback = ~1200 records max per player.
        // We prune at 2000 to have headroom above worst-case.
        if (records.size > 2000) {
            val cutoff = record.timestamp - (MAX_LOOKBACK_SECONDS * 1000L)
            records.removeAll { it.timestamp < cutoff }
        }
    }

    /**
     * Remove records older than the specified seconds from the given current time.
     */
    fun pruneOlderThan(maxAgeSeconds: Int, currentTime: Long = System.currentTimeMillis()) {
        val cutoff = currentTime - (maxAgeSeconds * 1000L)
        records.removeAll { it.timestamp < cutoff }
    }

    /**
     * Query records within a cubic area centered at [center] with given half-extents [scopeX]/[scopeY]/[scopeZ],
     * and within the time range [afterTimeSec] seconds ago to now.
     * Returns list of matching PathRecord sorted by most recent first.
     */
    fun queryInArea(
        center: BlockPos,
        scopeX: Int,
        scopeY: Int,
        scopeZ: Int,
        afterTimeSec: Int,
        currentTime: Long = System.currentTimeMillis(),
        worldKey: String
    ): List<PathRecord> {
        val cutoff = currentTime - (afterTimeSec * 1000L)
        val halfX = scopeX / 2
        val halfY = scopeY / 2
        val halfZ = scopeZ / 2

        return records.filter { record ->
            record.timestamp >= cutoff &&
            record.worldKey == worldKey &&
            record.x >= center.x - halfX && record.x <= center.x + halfX &&
            record.y >= center.y - halfY && record.y <= center.y + halfY &&
            record.z >= center.z - halfZ && record.z <= center.z + halfZ
        }.sortedByDescending { it.timestamp }
    }

    /**
     * Remove all records.
     */
    fun clear() {
        records.clear()
    }

    /**
     * Get the latest record if any.
     */
    fun latestRecord(): PathRecord? = records.maxByOrNull { it.timestamp }
}
