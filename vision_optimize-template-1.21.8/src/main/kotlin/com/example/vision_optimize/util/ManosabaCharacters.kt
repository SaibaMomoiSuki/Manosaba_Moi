package com.example.vision_optimize.util

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

/**
 * Hardcoded character color mappings for the "魔法少女の魔女审判" feature.
 * When a player has one of these tags, their name in vision messages
 * will be replaced with the colored character name.
 */
object ManosabaCharacters {

    data class CharacterInfo(
        val tag: String,
        val displayName: String,
        val colors: List<Int>  // one color per character, for composite names like noa
    )

    /** Maps from player tag → character info */
    val tagToInfo: Map<String, CharacterInfo> = mapOf(
        "ema" to CharacterInfo("ema", "樱羽艾玛", listOf(0xfc8eac)),
        "cero" to CharacterInfo("cero", "二阶堂希罗", listOf(0x8b0000)),
        "nnk" to CharacterInfo("nnk", "黑部奈叶香", listOf(0x555555)),      // DARK_GRAY
        "mago" to CharacterInfo("mago", "宝生玛格", listOf(0xAA00AA)),      // DARK_PURPLE
        "leiya" to CharacterInfo("leiya", "莲见蕾雅", listOf(0xFFAA00)),      // GOLD
        "milya" to CharacterInfo("milya", "佐伯米莉亚", listOf(0xFFFF55)),      // YELLOW
        "sherry" to CharacterInfo("sherry", "橘雪莉", listOf(0x1e90ff)),
        "yalisa" to CharacterInfo("yalisa", "紫藤亚里沙", listOf(0xB1B7AC)),
        "noa" to CharacterInfo("noa", "城崎诺亚", listOf(
            0x55FFFF,  // 城 - AQUA
            0xFF5555,  // 崎 - RED
            0xFFFF55,  // 诺 - YELLOW
            0xFF55FF   // 亚 - LIGHT_PURPLE
        )),
        "anan" to CharacterInfo("anan", "夏目安安", listOf(0x240090)),
        "yuki" to CharacterInfo("yuki", "月代雪", listOf(0xe0ffff)),
        "mll" to CharacterInfo("mll", "冰上梅露露", listOf(0xddb6ff)),
        "coco" to CharacterInfo("coco", "泽渡可可", listOf(0xff6700)),
        "hanna" to CharacterInfo("hanna", "远野汉娜", listOf(0x5f9e3f))
    )

    /** All registered character tags */
    val allTags: Set<String> get() = tagToInfo.keys

    /** All registered character infos */
    val allInfos: Collection<CharacterInfo> get() = tagToInfo.values

    /**
     * Get the first character tag found in the player's tag set, or null.
     * Matching is case-insensitive.
     */
    fun findMatchingTag(playerTags: Set<String>): String? {
        return playerTags.firstOrNull { playerTag ->
            tagToInfo.keys.any { it.equals(playerTag, ignoreCase = true) }
        }?.let { matchedTag ->
            // Return the canonical (lowercase) key from tagToInfo
            tagToInfo.keys.firstOrNull { it.equals(matchedTag, ignoreCase = true) }
        }
    }

    /**
     * Build a colored MutableComponent for a character name.
     * For most characters this is a single-color name.
     * For "noa" it is a 4-color composite: 城(水色)崎(赤色)诺(黄色)亚(薄紫色)
     */
    fun buildColoredName(tag: String): MutableComponent? {
        val info = tagToInfo[tag] ?: return null
        val name = info.displayName
        val colors = info.colors

        if (colors.size == 1) {
            // Single color for whole name
            return Component.literal(name).withStyle { it.withColor(colors[0]) }
        }

        // Multi-color: one color per character
        val result = Component.empty()
        for (i in name.indices) {
            val colorIndex = i.coerceAtMost(colors.size - 1)
            result.append(
                Component.literal(name[i].toString()).withStyle { it.withColor(colors[colorIndex]) }
            )
        }
        return result
    }

    /**
     * Get the display name for a character tag, or null.
     */
    fun getCharacterName(tag: String): String? = tagToInfo[tag]?.displayName

    /**
     * Get the primary color for a character tag, or null.
     */
    fun getCharacterColor(tag: String): Int? = tagToInfo[tag]?.colors?.firstOrNull()

    /**
     * Server-side: resolve a player's display name string for manosaba.
     * Checks both config.playerTags AND vanilla scoreboard tags (from /tag command).
     * Returns the character display name if manosaba is enabled and a matching tag is found,
     * otherwise returns the original playerName unchanged.
     */
    fun resolveManosabaNameServer(playerName: String, server: net.minecraft.server.MinecraftServer): String {
        val config = com.example.vision_optimize.config.ModConfigManager.get()
        if (!config.manosabaEnabled) return playerName

        // 1. Check config playerTags
        val configTags = config.playerTags[playerName]
        if (configTags != null) {
            val match = findMatchingTag(configTags)
            if (match != null) return getCharacterName(match) ?: playerName
        }

        // 2. Check vanilla scoreboard tags (from /tag command)
        val onlinePlayer = server.playerList.getPlayerByName(playerName)
        if (onlinePlayer != null) {
            val vanillaTags = onlinePlayer.tags
            if (vanillaTags.isNotEmpty()) {
                val match = findMatchingTag(vanillaTags)
                if (match != null) return getCharacterName(match) ?: playerName
            }
        }

        return playerName
    }

    /**
     * Server-side: resolve a player's name to a colored Component for manosaba.
     * Checks both config.playerTags AND vanilla scoreboard tags.
     * Returns a colored character name Component if manosaba is enabled and a matching tag is found,
     * otherwise returns a plain Component of the original playerName.
     */
    fun resolveManosabaNameServerColored(playerName: String, server: net.minecraft.server.MinecraftServer): net.minecraft.network.chat.MutableComponent {
        val config = com.example.vision_optimize.config.ModConfigManager.get()
        if (!config.manosabaEnabled) return Component.literal(playerName)

        // Try to find a matching tag
        val configTags = config.playerTags[playerName] ?: emptySet()
        val onlinePlayer = server.playerList.getPlayerByName(playerName)
        val vanillaTags = onlinePlayer?.tags ?: emptySet()
        val allTags = configTags + vanillaTags

        val matchingTag = findMatchingTag(allTags) ?: return Component.literal(playerName)
        return buildColoredName(matchingTag) ?: Component.literal(playerName)
    }
}
