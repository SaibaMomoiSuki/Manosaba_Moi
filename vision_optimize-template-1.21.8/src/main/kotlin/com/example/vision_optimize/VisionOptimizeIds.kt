package com.example.vision_optimize

import net.minecraft.resources.ResourceLocation

/**
 * Central constants for the vision_optimize mod.
 */
object VisionOptimizeIds {
    const val MOD_ID = "vision_optimize"

    fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)

    // Packet channel IDs
    val CONFIG_SYNC = id("config_sync")
    val BLOCK_VISION_REQUEST = id("block_vision_request")
    val BLOCK_VISION_RESULT = id("block_vision_result")
    val BLOCK_VISION_PROGRESS = id("block_vision_progress")
    val ITEM_VISION_REQUEST = id("item_vision_request")
    val ITEM_VISION_RESULT = id("item_vision_result")
    val PLAYER_VISION_REQUEST = id("player_vision_request")
    val PLAYER_VISION_RESULT = id("player_vision_result")
    val PLAYER_VISION_COOLDOWN_RESET = id("player_vision_cooldown_reset")
    val PLAYER_VISION_CHARGE_START = id("player_vision_charge_start")
    val PLAYER_VISION_CHARGE_CANCEL = id("player_vision_charge_cancel")
    val PLAYER_VISION_WARNING = id("player_vision_warning")
    val PLAYER_VISION_TELLRAW_SUPPRESS = id("player_vision_tellraw_suppress")
    val PLAYER_VISION_LOOK = id("player_vision_look")
    val PLAYER_VISION_LEVEL_SYNC = id("player_vision_level_sync")
    val CONFIG_UPDATE = id("config_update")
    val OP_STATUS_SYNC = id("op_status_sync")
    val KEY_PRESS = id("key_press")

    // Attachment IDs
    val PLAYER_PATH = id("player_path")
    val VISION_LEVEL = id("vision_level")
    val BLOCK_LEVEL = id("block_level")
    val ITEM_LEVEL = id("item_level")
    val DEATH_TIMESTAMP = id("death_timestamp")

    // Data component
    val TRACKING_ID = id("tracking_id")

    // Key binding IDs
    val KEY_ITEM_VISION = id("item_vision")
    val KEY_SETTINGS = id("settings")
    val KEY_SELECT = id("select")
    val KEY_PLAYER_SELECT = id("player_select")

    // Translation keys
    const val KEY_CATEGORY = "key.category.vision_optimize"
    const val KEY_ITEM_VISION_NAME = "key.vision_optimize.item_vision"
    const val KEY_SETTINGS_NAME = "key.vision_optimize.settings"
    const val KEY_SELECT_NAME = "key.vision_optimize.select"
    const val KEY_PLAYER_SELECT_NAME = "key.vision_optimize.player_select"
}
