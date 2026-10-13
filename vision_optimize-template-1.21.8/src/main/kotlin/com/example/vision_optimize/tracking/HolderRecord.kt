package com.example.vision_optimize.tracking

/**
 * Record of an item being held by a player at a specific time.
 */
data class HolderRecord(
    val playerName: String,
    val timestamp: Long  // System.currentTimeMillis()
)
