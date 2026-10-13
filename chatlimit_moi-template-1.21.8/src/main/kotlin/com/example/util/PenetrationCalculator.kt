package com.example.util

import com.example.config.ChatLimitConfig
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.sqrt

data class PenetrationResult(
    val canHear: Boolean,
    val distance: Double,
    val score: Double,
    val incompleteBlockCount: Int,
    val softBlockCount: Int,
    val ironHardBlockCount: Int,
    val hardBlockCount: Int,
)

object PenetrationCalculator {

    const val IRON_ORE_HARDNESS = 3.0f

    /**
     * Calculate if a receiver can hear a sender using raycasting through blocks.
     *
     * Score formula: Sum(count_i / penetrateLevel_i) for each classification.
     * If score > 1.0, the sound is blocked.
     * If a penetrateLevel is 0 and there are blocks of that type, score = infinity (blocked).
     * If a penetrateLevel is infinite, blocks of that type contribute 0.
     *
     * @param penetrationMultiplier Multiplies all four penetration levels.
     *       1.0 = unchanged, 2.0 = twice as many blocks can be penetrated.
     */
    fun calculate(
        sender: ServerPlayer,
        receiver: ServerPlayer,
        config: ChatLimitConfig,
        penetrationMultiplier: Double = 1.0,
    ): PenetrationResult {
        val senderEye = sender.eyePosition
        val receiverEye = receiver.eyePosition
        val distance = senderEye.distanceTo(receiverEye)

        val level: Level = sender.level()
        val start = senderEye
        val end = receiverEye

        var incompleteCount = 0
        var softCount = 0
        var ironCount = 0
        var hardCount = 0

        val direction = end.subtract(start)
        val totalDist = direction.length()

        if (totalDist < 0.001) {
            return PenetrationResult(true, distance, 0.0, 0, 0, 0, 0)
        }

        val dirNorm = direction.normalize()
        val stepSize = 0.1
        val steps = (totalDist / stepSize).toInt().coerceAtLeast(1)
        val stepVec = dirNorm.scale(stepSize)

        val visitedBlocks = mutableSetOf<BlockPos>()

        for (i in 0..steps) {
            val pos = start.add(stepVec.scale(i.toDouble()))
            val blockPos = BlockPos.containing(pos)

            if (blockPos !in visitedBlocks) {
                visitedBlocks.add(blockPos)
                val blockState: BlockState = level.getBlockState(blockPos)

                if (!blockState.isAir) {
                    when (classifyBlock(blockState, level, blockPos)) {
                        BlockClass.INCOMPLETE -> incompleteCount++
                        BlockClass.SOFT -> softCount++
                        BlockClass.IRON_HARD -> ironCount++
                        BlockClass.HARD -> hardCount++
                        BlockClass.SKIP -> { /* air or fluid */ }
                    }
                }
            }
        }

        val score = computeScore(
            incompleteCount, softCount, ironCount, hardCount,
            config.penetrateLevelOne * penetrationMultiplier,
            config.penetrateLevelTwo * penetrationMultiplier,
            config.penetrateLevelThree * penetrationMultiplier,
            config.penetrateLevelFour * penetrationMultiplier,
        )

        return PenetrationResult(
            canHear = score <= 1.0,
            distance = distance,
            score = score,
            incompleteBlockCount = incompleteCount,
            softBlockCount = softCount,
            ironHardBlockCount = ironCount,
            hardBlockCount = hardCount,
        )
    }

    private fun classifyBlock(
        blockState: BlockState,
        level: Level,
        pos: BlockPos,
    ): BlockClass {
        if (blockState.isAir) return BlockClass.SKIP

        // Check if it's an incomplete (non-full-cube) block
        if (!isFullCollisionBlock(blockState, level, pos)) {
            return BlockClass.INCOMPLETE
        }

        // Classify by hardness (destroy time)
        val hardness = getBlockHardness(blockState)

        return when {
            hardness < 0f -> BlockClass.SKIP // unbreakable (bedrock, etc.) -> treat as air for sound
            hardness < IRON_ORE_HARDNESS -> BlockClass.SOFT
            hardness == IRON_ORE_HARDNESS -> BlockClass.IRON_HARD
            else -> BlockClass.HARD // hardness > IRON_ORE_HARDNESS
        }
    }

    /**
     * Check if a block's collision shape fills the full 1x1x1 block space.
     */
    private fun isFullCollisionBlock(
        blockState: BlockState,
        level: Level,
        pos: BlockPos,
    ): Boolean {
        val shape = blockState.getCollisionShape(level, pos)
        if (shape.isEmpty) return false

        for (aabb in shape.toAabbs()) {
            if (aabb.minX <= 0.001 && aabb.minY <= 0.001 && aabb.minZ <= 0.001 &&
                aabb.maxX >= 0.999 && aabb.maxY >= 0.999 && aabb.maxZ >= 0.999
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Get the block's base hardness (destroy time).
     */
    private fun getBlockHardness(blockState: BlockState): Float {
        return blockState.block.defaultDestroyTime()
    }

    private fun computeScore(
        incomplete: Int, soft: Int, iron: Int, hard: Int,
        pen1: Double, pen2: Double, pen3: Double, pen4: Double,
    ): Double {
        var score = 0.0

        if (pen1 > 0.0 && !pen1.isInfinite()) score += incomplete / pen1
        else if (pen1 == 0.0 && incomplete > 0) return Double.POSITIVE_INFINITY

        if (pen2 > 0.0 && !pen2.isInfinite()) score += soft / pen2
        else if (pen2 == 0.0 && soft > 0) return Double.POSITIVE_INFINITY

        if (pen3 > 0.0 && !pen3.isInfinite()) score += iron / pen3
        else if (pen3 == 0.0 && iron > 0) return Double.POSITIVE_INFINITY

        if (pen4 > 0.0 && !pen4.isInfinite()) score += hard / pen4
        else if (pen4 == 0.0 && hard > 0) return Double.POSITIVE_INFINITY

        return score
    }

    private enum class BlockClass {
        SKIP,
        INCOMPLETE,
        SOFT,
        IRON_HARD,
        HARD,
    }
}
