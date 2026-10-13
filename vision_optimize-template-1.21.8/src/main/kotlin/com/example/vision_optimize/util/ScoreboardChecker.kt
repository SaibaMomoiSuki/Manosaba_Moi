package com.example.vision_optimize.util

import net.minecraft.server.ServerScoreboard
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.ScoreHolder
import net.minecraft.world.scores.Scoreboard

/**
 * Utility for scoreboard-based permission checking.
 */
object ScoreboardChecker {

    /**
     * Check if a player's score on the given objective is within the specified range.
     * @param player The player to check
     * @param objectiveName The name of the scoreboard objective
     * @param min Minimum value (null = -infinity)
     * @param max Maximum value (null = +infinity)
     * @return true if the player's score is in range, false otherwise
     */
    fun isScoreInRange(
        player: ServerPlayer,
        objectiveName: String,
        min: Double?,
        max: Double?
    ): Boolean {
        if (objectiveName.isEmpty()) return false

        val scoreboard = player.server?.scoreboard ?: return false
        val objective = scoreboard.getObjective(objectiveName) ?: return false
        val score = getPlayerScore(scoreboard, player, objective) ?: return false

        val minOk = min == null || score >= min.toInt()
        val maxOk = max == null || score <= max.toInt()

        return minOk && maxOk
    }

    /**
     * Get a player's score on a specific objective.
     */
    fun getPlayerScore(scoreboard: net.minecraft.server.ServerScoreboard, player: ServerPlayer, objective: Objective): Int? {
        return try {
            val scoreHolder = ScoreHolder.forNameOnly(player.scoreboardName)
            val scoreAccess = scoreboard.getPlayerScoreInfo(scoreHolder, objective)
            scoreAccess?.value()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get a player's score on a specific objective (convenience method).
     * Returns the score as Double, or null if not found.
     */
    fun getScore(player: ServerPlayer, objective: Objective): Double? {
        val scoreboard = player.server?.scoreboard ?: return null
        return getPlayerScore(scoreboard, player, objective)?.toDouble()
    }
}
