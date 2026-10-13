package com.example.vision_optimize.util

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

/**
 * Hardcoded magic tag color mappings for the "魔法少女の魔女审判" feature.
 * When a player has one of these vanilla scoreboard tags, their magic in the
 * person vision (人物幻视) "摸索" message is replaced with the colored magic name.
 *
 * This mapping takes priority over config.playerVisionTags / config.playerVisionPlayerTags.
 */
object ManosabaMagics {

    data class MagicInfo(
        val tag: String,
        val displayName: String,
        val color: Int  // RGB (24-bit)
    )

    /** Maps from magic tag → magic info */
    val tagToInfo: Map<String, MagicInfo> = mapOf(
        "WitchSlayer" to MagicInfo("WitchSlayer", "魔杀", 0xFF5EC4),
        "Reversal" to MagicInfo("Reversal", "回溯", 0xD80A26),
        "Floating" to MagicInfo("Floating", "漂浮", 0x11D600),
        "Power" to MagicInfo("Power", "怪力", 0x1944D3),
        "BrainWash" to MagicInfo("BrainWash", "洗脑", 0x1B00D1),
        "Imitation" to MagicInfo("Imitation", "模仿", 0xBF14CE),
        "Heal" to MagicInfo("Heal", "治愈", 0xC7C8CC),
        "VisionControl" to MagicInfo("VisionControl", "视线诱导", 0xFF8C00),
        "Clairvoyance" to MagicInfo("Clairvoyance", "千里眼", 0xFF5D00),
        "FireControl" to MagicInfo("FireControl", "点火", 0xFF0010),
        "LiquidControl" to MagicInfo("LiquidControl", "液体操纵", 0x00FFFA),
        "Swap" to MagicInfo("Swap", "互换", 0xFFFF0A),
        "Vision" to MagicInfo("Vision", "幻视", 0x343435),
        "Sandevistan" to MagicInfo("Sandevistan", "过载", 0x5CBBC4),
        "Perception" to MagicInfo("Perception", "感知", 0x265AC1),
        "Intervention" to MagicInfo("Intervention", "介入过去", 0x6DBF6D),
        "Through" to MagicInfo("Through", "穿墙", 0x758CAD),
        "MindReading" to MagicInfo("MindReading", "窃密", 0x825951),
        "GravityControl" to MagicInfo("GravityControl", "重力", 0x00E0AF)
    )

    /** All registered magic tags */
    val allTags: Set<String> get() = tagToInfo.keys

    /** All registered magic infos */
    val allInfos: Collection<MagicInfo> get() = tagToInfo.values

    /**
     * Get the first magic tag found in the player's tag set, or null.
     * Matching is case-insensitive.
     */
    fun findMatchingTag(playerTags: Set<String>): String? {
        return playerTags.firstOrNull { playerTag ->
            tagToInfo.keys.any { it.equals(playerTag, ignoreCase = true) }
        }?.let { matchedTag ->
            // Return the canonical key from tagToInfo
            tagToInfo.keys.firstOrNull { it.equals(matchedTag, ignoreCase = true) }
        }
    }

    /**
     * Get the display name for a magic tag, or null.
     */
    fun getMagicName(tag: String): String? = tagToInfo[tag]?.displayName

    /**
     * Get the color for a magic tag, or null.
     */
    fun getMagicColor(tag: String): Int? = tagToInfo[tag]?.color

    /**
     * Build a colored MutableComponent for a magic name.
     */
    fun buildColoredMagicName(tag: String): MutableComponent? {
        val info = tagToInfo[tag] ?: return null
        return Component.literal(info.displayName).withStyle { it.withColor(info.color) }
    }
}
