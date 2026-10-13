package com.example.vision_optimize.util

/**
 * Utility for formatting time values.
 */
object TimeFormatter {

    /**
     * Format seconds into a human-readable relative time string.
     */
    fun formatRelativeTime(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}秒前"
            seconds < 3600 -> "${seconds / 60}分钟${if (seconds % 60 > 0) "${seconds % 60}秒" else ""}前"
            seconds < 86400 -> "${seconds / 3600}小时前"
            else -> "${seconds / 86400}天前"
        }
    }

    /**
     * Get the time in seconds between now and a past timestamp.
     */
    fun secondsAgo(timestamp: Long, currentTime: Long = System.currentTimeMillis()): Long {
        return (currentTime - timestamp) / 1000L
    }
}
