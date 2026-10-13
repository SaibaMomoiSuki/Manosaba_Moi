package com.example.vision_optimize.config

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName

/**
 * Access mode for the mod.
 */
enum class AccessMode {
    @SerializedName("all_members")
    ALL_MEMBERS,
    @SerializedName("tag_member")
    TAG_MEMBER
}

/**
 * Vision level for players.
 */
enum class VisionLevel {
    NONE,
    BLOCK_LEVEL_1,
    BLOCK_LEVEL_2,
    BLOCK_LEVEL_3,
    ITEM_LEVEL_1,
    ITEM_LEVEL_2,
    PLAYER_VISION_LEVEL_1,
    PLAYER_VISION_LEVEL_2,
    PLAYER_VISION_LEVEL_3
}

/**
 * Central configuration for the vision_optimize mod.
 * All fields use `var` to allow mutation by commands/GUI at runtime.
 */
data class ModConfig(
    // ===== Global State =====
    var enabled: Boolean = true,
    var mode: AccessMode = AccessMode.ALL_MEMBERS,

    // ===== Block Vision - Level 1 =====
    var blockL1ScopeX: Int = 5,
    var blockL1ScopeY: Int = 5,
    var blockL1ScopeZ: Int = 5,
    var blockL1AfterTime: Int = 60,          // 1 minute in seconds
    var blockL1PlayerInterval: Int = 2,      // "together" threshold in seconds
    var blockL1Scoreboard: String = "",      // empty = no scoreboard requirement
    var blockL1ScoreMin: Double? = null,     // null = -infinity
    var blockL1ScoreMax: Double? = null,     // null = +infinity

    // ===== Block Vision - Level 2 =====
    var blockL2ScopeX: Int = 5,
    var blockL2ScopeY: Int = 5,
    var blockL2ScopeZ: Int = 5,
    var blockL2SideEffect1: Int = 10,               // "near me" threshold in seconds
    var blockL2SideEffect2Min: Double? = 10.0,      // "recently passed" lower bound
    var blockL2SideEffect2Max: Double? = 60.0,      // "recently passed" upper bound
    var blockL2SideEffect3Min: Double? = 60.0,      // "lately passed" lower bound
    var blockL2SideEffect3Max: Double? = 120.0,     // "lately passed" upper bound
    var blockL2SideEffect4Min: Double? = 120.0,     // "once passed" lower bound
    var blockL2SideEffect4Max: Double? = 180.0,     // "once passed" upper bound (3 min)
    var blockL2Scoreboard: String = "",
    var blockL2ScoreMin: Double? = null,
    var blockL2ScoreMax: Double? = null,

    // ===== Block Vision - Level 3 =====
    var blockL3ScopeX: Int = 5,
    var blockL3ScopeY: Int = 5,
    var blockL3ScopeZ: Int = 5,
    var blockL3AfterTime: Int = 180,            // 3 minutes
    var blockL3Scoreboard: String = "",
    var blockL3ScoreMin: Double? = null,
    var blockL3ScoreMax: Double? = null,

    // ===== Block Vision - Common =====
    var blockSearchTime: Int = 3,               // progress bar duration in seconds
    var blockCooldownTime: Int = 3,             // cooldown after block vision scan (seconds)
    var blockVisionPercent: Int = 70,           // initial success probability for block vision (0-100)
    var blockVisionFailAdd: Int = 15,           // probability increase per block vision failure (0-100)

    // ===== Item Vision =====
    var itemCooldownTime: Int = 5,              // cooldown after item vision use (seconds)
    var itemVisionPercent: Int = 70,            // initial success probability for item vision (0-100)
    var itemVisionFailAdd: Int = 15,            // probability increase per item vision failure (0-100)
    var itemL1MaxDisplay: Int = 10,             // max displayed previous holders for level 1
    var itemL2VisionTime: Int = 180,            // lookback in seconds (3 min)
    var itemDeathFalseTime: Int = 1800,         // death interference in seconds (30 min)
    var itemL1Scoreboard: String = "",
    var itemL1ScoreMin: Double? = null,
    var itemL1ScoreMax: Double? = null,
    var itemL2Scoreboard: String = "",
    var itemL2ScoreMin: Double? = null,
    var itemL2ScoreMax: Double? = null,

    // ===== Player Vision =====
    var playerVisionDistance: Int = 3,               // range in blocks (legacy, kept for compatibility)
    var playerVisionRangeX: Int = 3,                 // vision range X (blocks from caster center)
    var playerVisionRangeY: Int = 3,                 // vision range Y
    var playerVisionRangeZ: Int = 3,                 // vision range Z
    var playerVisionReactionTime: Int = 3,           // discovery reaction window in seconds
    var playerVisionFailCooldown: Int = 6,           // fail cooldown in minutes
    var playerVisionSuccessCooldown: Int = 12,       // success cooldown in minutes
    var playerVisionRaidTime: Int = 30,              // in-view charge time in ticks
    var playerVisionSneakTime: Int = 60,             // out-of-view charge time in ticks
    var playerVisionDiscoverTime: Int = 60,          // third-party discovery time in ticks
    var playerVisionThirdPerspectiveDistance: Int = 5,  // max distance (blocks) for third-party witness
    var playerVisionTags: MutableMap<String, Int> = mutableMapOf(),           // tag text -> color ARGB
    var playerVisionPlayerTags: MutableMap<String, String> = mutableMapOf(),  // player name -> tag text

    // ===== Player Vision - Scoreboard =====
    var playerVisionL1Scoreboard: String = "",
    var playerVisionL1ScoreMin: Double? = null,
    var playerVisionL1ScoreMax: Double? = null,
    var playerVisionL2Scoreboard: String = "",
    var playerVisionL2ScoreMin: Double? = null,
    var playerVisionL2ScoreMax: Double? = null,
    var playerVisionL3Scoreboard: String = "",
    var playerVisionL3ScoreMin: Double? = null,
    var playerVisionL3ScoreMax: Double? = null,

    // ===== Manosaba =====
    var manosabaEnabled: Boolean = false,

    // ===== Gamerules =====
    var pathClearOnDeath: Boolean = true,
    var visionInterferenceOnDeath: Boolean = true,

    // ===== Exclusion List =====
    var excludedPlayers: MutableSet<String> = mutableSetOf(),

    // ===== Tag Mode =====
    var accessTags: MutableSet<String> = mutableSetOf(),
    /** Tags for which vision (幻视) is enabled. A tag must be in this set for its members to use vision. */
    var tagVisionEnabled: MutableSet<String> = mutableSetOf(),
    /** Maps player name → set of tag names assigned to that player. */
    var playerTags: MutableMap<String, MutableSet<String>> = mutableMapOf(),

    // ===== Player-specific overrides (player name -> VisionLevel) =====
    var playerBlockLevels: MutableMap<String, VisionLevel> = mutableMapOf(),
    var playerItemLevels: MutableMap<String, VisionLevel> = mutableMapOf(),
    var playerPlayerLevels: MutableMap<String, VisionLevel> = mutableMapOf()
) {
    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().create()

        fun fromJson(json: String): ModConfig = gson.fromJson(json, ModConfig::class.java)

        fun toJson(config: ModConfig): String = gson.toJson(config)
    }

    /**
     * Validate SideEffect ordering: 附近(0~SE1) < 不久前(SE2_min~SE2_max) < 最近(SE3_min~SE3_max) < 曾经(SE4_min~SE4_max)
     * Constraint: SE1 < SE2_min < SE2_max < SE3_min < SE3_max < SE4_min < SE4_max
     * Returns null if valid, or an error message string if invalid.
     */
    fun validateSideEffects(): String? {
        val se1 = blockL2SideEffect1.toDouble()
        val se2Min = blockL2SideEffect2Min ?: return "不久前(SE2) min 不能为空"
        val se2Max = blockL2SideEffect2Max ?: return "不久前(SE2) max 不能为空"
        val se3Min = blockL2SideEffect3Min ?: return "最近(SE3) min 不能为空"
        val se3Max = blockL2SideEffect3Max ?: return "最近(SE3) max 不能为空"
        val se4Min = blockL2SideEffect4Min ?: return "曾经(SE4) min 不能为空"
        val se4Max = blockL2SideEffect4Max ?: return "曾经(SE4) max 不能为空"

        // Each interval: min < max
        if (se2Min >= se2Max) return "不久前(SE2): min($se2Min) 必须小于 max($se2Max)"
        if (se3Min >= se3Max) return "最近(SE3): min($se3Min) 必须小于 max($se3Max)"
        if (se4Min >= se4Max) return "曾经(SE4): min($se4Min) 必须小于 max($se4Max)"

        // Chain ordering: SE1 <= SE2_min < SE2_max <= SE3_min < SE3_max <= SE4_min < SE4_max
        // Adjacent boundaries may be equal (e.g. SE1=10, SE2_min=10); value belongs to higher range
        if (se1 > se2Min) return "附近($se1) 不能大于 不久前 min($se2Min)"
        if (se2Max > se3Min) return "不久前 max($se2Max) 不能大于 最近 min($se3Min)"
        if (se3Max > se4Min) return "最近 max($se3Max) 不能大于 曾经 min($se4Min)"
        return null
    }

    /**
     * Validate that each scoreboard range's min is strictly less than its max.
     * Returns null if valid, or an error message string if invalid.
     */
    fun validateScoreboardRanges(): String? {
        val blockRanges = listOf(
            Triple("Block Level1", blockL1ScoreMin, blockL1ScoreMax),
            Triple("Block Level2", blockL2ScoreMin, blockL2ScoreMax),
            Triple("Block Level3", blockL3ScoreMin, blockL3ScoreMax)
        )
        val itemRanges = listOf(
            Triple("Item Level1", itemL1ScoreMin, itemL1ScoreMax),
            Triple("Item Level2", itemL2ScoreMin, itemL2ScoreMax)
        )
        for ((name, min, max) in blockRanges + itemRanges) {
            if (min != null && max != null && min >= max) {
                return "计分板范围错误: $name min($min) 必须小于 max($max)"
            }
        }
        return null
    }

    /**
     * Validate that block vision scoreboard ranges (L1, L2, L3) do not overlap.
     * Upper bounds are exclusive: adjacent ranges like [1,10) and [10,20) are valid
     * (boundary value belongs to higher level), but [1,10) and [5,15) overlap (5-9 in both).
     * Returns null if valid, or an error message string if overlap detected.
     */
    fun validateBlockScoreboardOverlap(): String? {
        val ranges = listOf(
            Triple("Level1", blockL1ScoreMin, blockL1ScoreMax),
            Triple("Level2", blockL2ScoreMin, blockL2ScoreMax),
            Triple("Level3", blockL3ScoreMin, blockL3ScoreMax)
        ).filter { it.second != null || it.third != null }

        for (i in ranges.indices) {
            for (j in i + 1 until ranges.size) {
                val (nameA, minA, maxA) = ranges[i]
                val (nameB, minB, maxB) = ranges[j]
                if (rangesOverlap(minA, maxA, minB, maxB)) {
                    return "计分板范围重叠: $nameA [${minA ?: "-∞"}, ${maxA ?: "+∞"}) 与 $nameB [${minB ?: "-∞"}, ${maxB ?: "+∞"}) 存在共同数值"
                }
            }
        }
        return null
    }

    /**
     * Validate that item vision scoreboard ranges (L1, L2) do not overlap.
     * Upper bounds are exclusive: adjacent ranges like [1,10) and [10,20) are valid.
     * Returns null if valid, or an error message string if overlap detected.
     */
    fun validateItemScoreboardOverlap(): String? {
        val l1Min = itemL1ScoreMin
        val l1Max = itemL1ScoreMax
        val l2Min = itemL2ScoreMin
        val l2Max = itemL2ScoreMax

        if ((l1Min != null || l1Max != null) && (l2Min != null || l2Max != null)) {
            if (rangesOverlap(l1Min, l1Max, l2Min, l2Max)) {
                return "计分板范围重叠: Level1 [${l1Min ?: "-∞"}, ${l1Max ?: "+∞"}) 与 Level2 [${l2Min ?: "-∞"}, ${l2Max ?: "+∞"}) 存在共同数值"
            }
        }
        return null
    }

    /**
     * Check if two half-open ranges [min, max) overlap.
     * Uses < (strict): [1,10) and [10,20) do NOT overlap, but [1,10) and [5,15) DO.
     * null = infinity.
     */
    private fun rangesOverlap(minA: Double?, maxA: Double?, minB: Double?, maxB: Double?): Boolean {
        val aLo = minA ?: Double.NEGATIVE_INFINITY
        val aHi = maxA ?: Double.POSITIVE_INFINITY
        val bLo = minB ?: Double.NEGATIVE_INFINITY
        val bHi = maxB ?: Double.POSITIVE_INFINITY
        // Two half-open ranges overlap if: max(aLo, bLo) < min(aHi, bHi)
        return maxOf(aLo, bLo) < minOf(aHi, bHi)
    }

    /**
     * Get effective vision level for block vision, considering scoreboard override.
     * If player's score falls in a scoreboard range → that Level is enforced.
     * Upper bound is exclusive: Level1 [min, max) → boundary value belongs to higher level.
     * If score is outside all ranges → player's manually assigned level is used.
     */
    fun getEffectiveBlockLevel(playerName: String, score: Double?): VisionLevel? {
        // If score is provided, check scoreboard ranges first (enforced)
        if (score != null) {
            val l1Min = blockL1ScoreMin; val l1Max = blockL1ScoreMax
            val l2Min = blockL2ScoreMin; val l2Max = blockL2ScoreMax
            val l3Min = blockL3ScoreMin; val l3Max = blockL3ScoreMax
            if (blockL1Scoreboard.isNotEmpty()) {
                val minOk = l1Min == null || score >= l1Min
                val maxOk = l1Max == null || score < l1Max
                if (minOk && maxOk) return VisionLevel.BLOCK_LEVEL_1
            }
            if (blockL2Scoreboard.isNotEmpty()) {
                val minOk = l2Min == null || score >= l2Min
                val maxOk = l2Max == null || score < l2Max
                if (minOk && maxOk) return VisionLevel.BLOCK_LEVEL_2
            }
            if (blockL3Scoreboard.isNotEmpty()) {
                val minOk = l3Min == null || score >= l3Min
                val maxOk = l3Max == null || score < l3Max
                if (minOk && maxOk) return VisionLevel.BLOCK_LEVEL_3
            }
        }
        // Score is outside all ranges (or no scoreboard) → use manual assignment
        return playerBlockLevels[playerName]
    }

    /**
     * Get effective vision level for item vision, considering scoreboard override.
     * If player's score falls in a scoreboard range → that Level is enforced.
     * Upper bound is exclusive: Level1 [min, max) → boundary value belongs to higher level.
     * If score is outside all ranges → player's manually assigned level is used.
     */
    fun getEffectiveItemLevel(playerName: String, score: Double?): VisionLevel? {
        if (score != null) {
            val l1Min = itemL1ScoreMin; val l1Max = itemL1ScoreMax
            val l2Min = itemL2ScoreMin; val l2Max = itemL2ScoreMax
            if (itemL1Scoreboard.isNotEmpty()) {
                val minOk = l1Min == null || score >= l1Min
                val maxOk = l1Max == null || score < l1Max
                if (minOk && maxOk) return VisionLevel.ITEM_LEVEL_1
            }
            if (itemL2Scoreboard.isNotEmpty()) {
                val minOk = l2Min == null || score >= l2Min
                val maxOk = l2Max == null || score < l2Max
                if (minOk && maxOk) return VisionLevel.ITEM_LEVEL_2
            }
        }
        return playerItemLevels[playerName]
    }

    /**
     * Check if a player has permission in TAG_MEMBER mode.
     * Player must have at least one of the accessTags assigned to them.
     * If accessTags is empty, no one has access in TAG_MEMBER mode.
     */
    fun hasTagAccess(playerName: String): Boolean {
        if (accessTags.isEmpty()) return false
        val playerTags = playerTags[playerName] ?: return false
        return playerTags.any { it in accessTags }
    }

    /**
     * Check if a player has vision (幻视) access in TAG_MEMBER mode.
     * Player must have at least one tag that is both an access tag AND vision-enabled.
     * Existing tags are automatically added to tagVisionEnabled on config load (migration).
     */
    fun hasVisionAccess(playerName: String): Boolean {
        if (accessTags.isEmpty()) return false
        val playerTags = playerTags[playerName] ?: return false
        // Player must have at least one tag that is both an access tag AND vision-enabled
        return playerTags.any { it in accessTags && it in tagVisionEnabled }
    }

    /**
     * Whether a player holding the Vision (幻视) magic tag should automatically
     * receive full vision permissions while the magical-girl witch trial
     * (魔女审判 / manosaba) is enabled. Matches the "Vision" vanilla scoreboard
     * tag case-insensitively.
     */
    fun hasVisionMagicFullAccess(tags: Set<String>): Boolean =
        manosabaEnabled && tags.any { it.equals("Vision", ignoreCase = true) }

    /**
     * Get effective vision level for player vision, considering scoreboard override.
     * If player's score falls in a scoreboard range → that Level is enforced.
     * Upper bound is exclusive: Level1 [min, max) → boundary value belongs to higher level.
     * If score is outside all ranges → player's manually assigned level is used.
     */
    fun getEffectivePlayerVisionLevel(playerName: String, score: Double?): VisionLevel? {
        if (score != null) {
            val l1Min = playerVisionL1ScoreMin; val l1Max = playerVisionL1ScoreMax
            val l2Min = playerVisionL2ScoreMin; val l2Max = playerVisionL2ScoreMax
            val l3Min = playerVisionL3ScoreMin; val l3Max = playerVisionL3ScoreMax
            if (playerVisionL1Scoreboard.isNotEmpty()) {
                val minOk = l1Min == null || score >= l1Min
                val maxOk = l1Max == null || score < l1Max
                if (minOk && maxOk) return VisionLevel.PLAYER_VISION_LEVEL_1
            }
            if (playerVisionL2Scoreboard.isNotEmpty()) {
                val minOk = l2Min == null || score >= l2Min
                val maxOk = l2Max == null || score < l2Max
                if (minOk && maxOk) return VisionLevel.PLAYER_VISION_LEVEL_2
            }
            if (playerVisionL3Scoreboard.isNotEmpty()) {
                val minOk = l3Min == null || score >= l3Min
                val maxOk = l3Max == null || score < l3Max
                if (minOk && maxOk) return VisionLevel.PLAYER_VISION_LEVEL_3
            }
        }
        return playerPlayerLevels[playerName]
    }

    /**
     * Validate that player vision scoreboard ranges (L1, L2) do not overlap.
     * Upper bounds are exclusive: adjacent ranges like [1,10) and [10,20) are valid.
     * Returns null if valid, or an error message string if overlap detected.
     */
    fun validatePlayerVisionScoreboardOverlap(): String? {
        val ranges = listOf(
            Triple("PlayerVision Level1", playerVisionL1ScoreMin, playerVisionL1ScoreMax),
            Triple("PlayerVision Level2", playerVisionL2ScoreMin, playerVisionL2ScoreMax),
            Triple("PlayerVision Level3", playerVisionL3ScoreMin, playerVisionL3ScoreMax)
        ).filter { it.second != null || it.third != null }

        for (i in ranges.indices) {
            for (j in i + 1 until ranges.size) {
                val (nameA, minA, maxA) = ranges[i]
                val (nameB, minB, maxB) = ranges[j]
                if (rangesOverlap(minA, maxA, minB, maxB)) {
                    return "计分板范围重叠: $nameA [${minA ?: "-∞"}, ${maxA ?: "+∞"}) 与 $nameB [${minB ?: "-∞"}, ${maxB ?: "+∞"}) 存在共同数值"
                }
            }
        }
        return null
    }

    /**
     * Check if a player has player vision (人物幻视) access.
     * Same permission model as item vision — uses hasVisionAccess.
     */
    fun hasPlayerVisionAccess(playerName: String): Boolean {
        if (mode == AccessMode.ALL_MEMBERS) return true
        val itemLevel = playerItemLevels[playerName]
        if (itemLevel != null && itemLevel != VisionLevel.NONE) return true
        return hasVisionAccess(playerName)
    }
}
