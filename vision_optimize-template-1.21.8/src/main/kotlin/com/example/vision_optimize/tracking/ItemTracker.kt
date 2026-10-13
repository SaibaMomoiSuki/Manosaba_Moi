package com.example.vision_optimize.tracking

import com.example.vision_optimize.config.ModConfigManager
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.slf4j.LoggerFactory
import java.io.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Serializable data class for item history persistence.
 */
data class ItemHistoryData(
    val items: MutableMap<String, MutableList<HolderRecord>> = mutableMapOf(),
    val deathTimestamps: MutableMap<String, Long> = mutableMapOf()
)

/**
 * Global registry tracking which players held which items.
 * Key: tracking UUID (from ItemTrackingComponent)
 * Value: ordered list of HolderRecord (oldest first)
 *
 * Persisted to <world>/data/vision_optimize_items.json on server stop.
 */
object ItemTracker {
    private val logger = LoggerFactory.getLogger("vision_optimize:items")
    private val history: ConcurrentHashMap<UUID, MutableList<HolderRecord>> = ConcurrentHashMap()

    // Death timestamps per player name
    private val deathTimestamps: ConcurrentHashMap<String, Long> = ConcurrentHashMap()

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Record that a player held an item.
     * Each player appears only ONCE per item.
     * If the player already has a record, remove the old one and add a fresh
     * entry at the end, effectively refreshing their position in the history.
     */
    fun recordHold(trackingId: UUID, playerName: String, timestamp: Long = System.currentTimeMillis()) {
        if (ModConfigManager.isPlayerExcluded(playerName)) return
        val list = history.computeIfAbsent(trackingId) { mutableListOf() }
        synchronized(list) {
            // Remove any existing record for this player (one player = one entry)
            list.removeAll { it.playerName == playerName }
            // Add fresh record at the end
            list.add(HolderRecord(playerName, timestamp))
        }
    }

    /**
     * Get the holder history for a specific item tracking ID.
     * Returns list in chronological order (oldest first).
     */
    fun getHistory(trackingId: UUID): List<HolderRecord> {
        return history[trackingId]?.toList() ?: emptyList()
    }

    /**
     * Record a player's death timestamp.
     */
    fun recordPlayerDeath(playerName: String, timestamp: Long = System.currentTimeMillis()) {
        deathTimestamps[playerName] = timestamp
    }

    /**
     * Get a player's last death timestamp, or null if never died.
     */
    fun getPlayerDeathTime(playerName: String): Long? = deathTimestamps[playerName]

    /**
     * Check if item vision for this player is currently interfered due to recent death.
     */
    fun isDeathInterfered(playerName: String): Boolean {
        val deathTime = deathTimestamps[playerName] ?: return false
        val interferenceSeconds = ModConfigManager.get().itemDeathFalseTime
        return (System.currentTimeMillis() - deathTime) < (interferenceSeconds * 1000L)
    }

    /**
     * Prune records older than the specified max age in seconds.
     */
    fun pruneOldRecords(maxAgeSeconds: Int, currentTime: Long = System.currentTimeMillis()) {
        val cutoff = currentTime - (maxAgeSeconds * 1000L)
        history.forEach { (_, list) ->
            synchronized(list) {
                list.removeAll { it.timestamp < cutoff }
            }
        }
        history.entries.removeIf { it.value.isEmpty() }
    }

    /**
     * Add a holder record without de-duplicating by player name.
     * Used when merging item histories from different source stacks,
     * so that if the same player held two separate stacks, they appear
     * twice in the merged history ("输出两次").
     */
    fun addHistoryRecord(trackingId: UUID, record: HolderRecord) {
        if (ModConfigManager.isPlayerExcluded(record.playerName)) return
        val list = history.computeIfAbsent(trackingId) { mutableListOf() }
        synchronized(list) {
            list.add(record)
        }
    }

    /**
     * Remove all history for a specific tracking UUID.
     */
    fun clearHistory(trackingId: UUID) {
        history.remove(trackingId)
    }

    /**
     * Clear all item holder history.
     */
    fun clearAll() {
        history.clear()
    }

    /**
     * Clear death timestamps for all players.
     */
    fun clearDeathTimestamps() {
        deathTimestamps.clear()
    }

    /**
     * Save to JSON file in the world data directory.
     */
    fun save(worldDir: File) {
        try {
            val dataDir = File(worldDir, "data")
            if (!dataDir.exists()) dataDir.mkdirs()
            val file = File(dataDir, "vision_optimize_items.json")

            // Convert UUID-keyed maps to string-keyed maps for JSON serialization
            val data = ItemHistoryData(
                items = mutableMapOf<String, MutableList<HolderRecord>>().apply {
                    history.forEach { (uuid, records) ->
                        this[uuid.toString()] = records
                    }
                },
                deathTimestamps = mutableMapOf<String, Long>().apply {
                    deathTimestamps.forEach { (name, ts) ->
                        this[name] = ts
                    }
                }
            )

            val json = gson.toJson(data)
            file.writeText(json, Charsets.UTF_8)
            logger.debug("Item tracker saved to {}", file)
        } catch (e: Exception) {
            logger.error("Failed to save item tracker data", e)
        }
    }

    /**
     * Load from JSON file in the world data directory.
     */
    fun load(worldDir: File) {
        try {
            val file = File(worldDir, "data/vision_optimize_items.json")
            if (!file.exists()) {
                logger.info("No item tracker data file found, starting fresh")
                return
            }

            val json = file.readText(Charsets.UTF_8)
            val data = gson.fromJson(json, ItemHistoryData::class.java)

            data.items.forEach { (uuidStr, records) ->
                val uuid = UUID.fromString(uuidStr)
                if (records.isNotEmpty()) {
                    history[uuid] = records
                }
            }

            data.deathTimestamps.forEach { (name, ts) ->
                deathTimestamps[name] = ts
            }

            logger.info("Item tracker loaded: {} items tracked, {} death records", history.size, deathTimestamps.size)
        } catch (e: Exception) {
            logger.error("Failed to load item tracker data", e)
        }
    }
}
