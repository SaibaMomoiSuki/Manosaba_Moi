package com.example.vision_optimize.tracking

/**
 * Record of a player's position at a specific time.
 */
data class PathRecord(
    val x: Double,
    val y: Double,
    val z: Double,
    val worldKey: String,   // World ResourceLocation string
    val timestamp: Long     // System.currentTimeMillis()
)
