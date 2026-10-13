package com.example.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import kotlin.math.abs
import kotlin.math.sqrt

data class ChatLimitConfig(
    var enabled: Boolean = true,
    var playerSilenceEnabled: Boolean = false,
    // Chat distance levels (in blocks)
    var chatLevelOneMin: Double = 0.0,
    var chatLevelOneMax: Double = 8.0,
    var chatLevelTwoMin: Double = 8.0,
    var chatLevelTwoMax: Double = 15.0,
    var chatLevelThreeMin: Double = 15.0,
    var chatLevelThreeMax: Double = 20.0,
    var chatLevelFour: Double = 20.0,
    // Penetration levels (count of blocks that can be penetrated)
    var penetrateLevelOne: Double = 3.0,   // Incomplete blocks (non-full cubes)
    var penetrateLevelTwo: Double = 2.0,   // Blocks with hardness < iron ore (3.0)
    var penetrateLevelThree: Double = 1.0, // Blocks with hardness == iron ore (3.0)
    var penetrateLevelFour: Double = 0.0,  // Blocks with hardness > iron ore (3.0)
    // Axis distance multipliers (scales effective distance on each axis)
    var yMultiplier: Double = 0.5,  // Y-axis: 0.5x → vertical distances count 2x more
    var xMultiplier: Double = 1.0,  // X-axis: 1.0x → normal
    var zMultiplier: Double = 1.0,  // Z-axis: 1.0x → normal
    // Quiet voice box range (per-axis, in blocks). Player only hears quiet voice within this box.
    var quietRangeX: Double = 3.0,
    var quietRangeY: Double = 2.0,
    var quietRangeZ: Double = 3.0,
    // Shout multipliers
    var shoutRangeMultiplier: Double = 2.0,        // Divides effective distance → 2.0 = 2× range
    var shoutPenetrationMultiplier: Double = 1.0,   // Multiplies penetration levels → 1.0 = unchanged
) {
    companion object {
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
        private val CONFIG_FILE: File = File(
            FabricLoader.getInstance().configDir.toFile(), "chatlimit_moi.json"
        )

        fun load(): ChatLimitConfig {
            if (CONFIG_FILE.exists()) {
                try {
                    return GSON.fromJson(FileReader(CONFIG_FILE), ChatLimitConfig::class.java)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return ChatLimitConfig().also { it.save() }
        }

        fun save(config: ChatLimitConfig) {
            try {
                FileWriter(CONFIG_FILE).use { writer -> GSON.toJson(config, writer) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun fromJson(json: String): ChatLimitConfig =
            GSON.fromJson(json, ChatLimitConfig::class.java)
    }

    fun save() = Companion.save(this)
    fun toJson(): String = GSON.toJson(this)

    /**
     * Validate chat distance level settings.
     */
    fun validateChatLevels(): String? {
        if (chatLevelOneMin >= chatLevelOneMax)
            return "§c清晰声音传播: 最小值必须小于最大值"
        if (chatLevelTwoMin >= chatLevelTwoMax)
            return "§c模糊声音传播: 最小值必须小于最大值"
        if (chatLevelThreeMin >= chatLevelThreeMax)
            return "§c略有声音传播: 最小值必须小于最大值"
        if (chatLevelOneMax > chatLevelTwoMin)
            return "§c清晰声音传播范围必须 < 模糊声音传播范围"
        if (chatLevelTwoMax > chatLevelThreeMin)
            return "§c模糊声音传播范围必须 < 略有声音传播范围"
        if (chatLevelThreeMax > chatLevelFour)
            return "§c略有声音传播范围必须 < 听不见范围"
        return null
    }

    /**
     * Validate penetration level settings.
     */
    fun validatePenetrateLevels(): String? {
        if (penetrateLevelOne < penetrateLevelTwo)
            return "§c不完整方块穿透格数必须 >= 密度小方块穿透格数"
        if (penetrateLevelTwo < penetrateLevelThree)
            return "§c密度小方块穿透格数必须 >= 密度中等方块穿透格数"
        if (penetrateLevelThree < penetrateLevelFour)
            return "§c密度中等方块穿透格数必须 >= 密度高方块穿透格数"
        return null
    }

    /**
     * Validate axis multiplier values (must be ≤ 100, must not be negative).
     */
    fun validateAxisMultipliers(): String? {
        if (yMultiplier < 0 || yMultiplier > 100)
            return "§cY轴距离限制倍数必须在 0 ~ 100 之间"
        if (xMultiplier < 0 || xMultiplier > 100)
            return "§cX轴距离限制倍数必须在 0 ~ 100 之间"
        if (zMultiplier < 0 || zMultiplier > 100)
            return "§cZ轴距离限制倍数必须在 0 ~ 100 之间"
        return null
    }

    /**
     * Validate quiet voice box range values (must not be negative).
     */
    fun validateQuietRange(): String? {
        if (quietRangeX < 0) return "§c小声X轴范围不能为负数"
        if (quietRangeY < 0) return "§c小声Y轴范围不能为负数"
        if (quietRangeZ < 0) return "§c小声Z轴范围不能为负数"
        return null
    }

    /**
     * Validate shout multiplier values (must be >= 0).
     */
    fun validateShoutMultipliers(): String? {
        if (shoutRangeMultiplier < 0) return "§c大喊声音范围倍率不能为负数"
        if (shoutPenetrationMultiplier < 0) return "§c大喊穿透倍率不能为负数"
        return null
    }

    /**
     * Get the chat degradation level for a given axis-scaled effective distance.
     *
     * Formula: effectiveDist = sqrt((dx/xMult)² + (dy/yMult)² + (dz/zMult)²)
     * Then compare effectiveDist against the chat level thresholds.
     *
     * Example: chatLevelOneMax=8, yMult=0.5 → Y distance of 4 blocks counts as 8 effective.
     *
     * @param deltaX absolute X distance between players
     * @param deltaY absolute Y distance between players
     * @param deltaZ absolute Z distance between players
     * @return 1=full text, 2=random spaces, 3=lots of spaces, 4=blocked
     */
    /** Compute axis-scaled effective distance between two points. */
    fun effectiveDistance(deltaX: Double, deltaY: Double, deltaZ: Double): Double {
        val xm = if (xMultiplier > 0.0) xMultiplier else 0.001
        val ym = if (yMultiplier > 0.0) yMultiplier else 0.001
        val zm = if (zMultiplier > 0.0) zMultiplier else 0.001
        return sqrt(
            (abs(deltaX) / xm) * (abs(deltaX) / xm) +
            (abs(deltaY) / ym) * (abs(deltaY) / ym) +
            (abs(deltaZ) / zm) * (abs(deltaZ) / zm)
        )
    }

    /**
     * Get the chat degradation level for a given axis-scaled effective distance.
     * @return 1=full text, 2=random spaces, 3=lots of spaces, 4=blocked
     */
    fun getChatDistanceLevel(deltaX: Double, deltaY: Double, deltaZ: Double): Int {
        val d = effectiveDistance(deltaX, deltaY, deltaZ)
        if (d >= chatLevelOneMin && d <= chatLevelOneMax) return 1
        if (d >= chatLevelTwoMin && d <= chatLevelTwoMax) return 2
        if (d >= chatLevelThreeMin && d <= chatLevelThreeMax) return 3
        return 4
    }

    /**
     * Continuous degradation factor: 0.0 = clear (white, no ?) → 1.0 = max degradation (black, all ?).
     * Returns -1.0 if the distance is completely beyond audible range (blocked).
     *
     * The factor maps linearly from chatLevelOneMax (0.0) to chatLevelThreeMax (1.0).
     */
    fun getDegradationFactor(deltaX: Double, deltaY: Double, deltaZ: Double): Double {
        val d = effectiveDistance(deltaX, deltaY, deltaZ)
        // Beyond level 4 cutoff → completely blocked
        if (d > chatLevelFour) return -1.0
        // Within clear range → no degradation
        if (d <= chatLevelOneMax) return 0.0
        // At or beyond max degradation range → full degradation
        if (d >= chatLevelThreeMax) return 1.0
        // Linear interpolation between clear and max degradation
        return (d - chatLevelOneMax) / (chatLevelThreeMax - chatLevelOneMax)
    }

    /** Legacy convenience — uses simple Euclidean distance. */
    @Deprecated("Use getChatDistanceLevel(deltaX, deltaY, deltaZ) instead")
    fun getChatDistanceLevel(distance: Double): Int =
        getChatDistanceLevel(distance, distance, distance)
}
