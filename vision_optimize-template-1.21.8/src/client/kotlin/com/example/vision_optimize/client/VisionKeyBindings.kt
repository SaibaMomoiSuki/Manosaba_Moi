package com.example.vision_optimize.client

import com.example.vision_optimize.VisionOptimizeIds
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.KeyMapping
import com.mojang.blaze3d.platform.InputConstants
import org.lwjgl.glfw.GLFW

/**
 * Registers custom key bindings for the mod.
 */
object VisionKeyBindings {
    lateinit var itemVisionKey: KeyMapping
        private set
    lateinit var settingsKey: KeyMapping
        private set
    lateinit var selectKey: KeyMapping
        private set
    lateinit var playerSelectKey: KeyMapping
        private set

    fun register() {
        itemVisionKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping(
                VisionOptimizeIds.KEY_ITEM_VISION_NAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                VisionOptimizeIds.KEY_CATEGORY
            )
        )

        settingsKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping(
                VisionOptimizeIds.KEY_SETTINGS_NAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_3,
                VisionOptimizeIds.KEY_CATEGORY
            )
        )

        selectKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping(
                VisionOptimizeIds.KEY_SELECT_NAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                VisionOptimizeIds.KEY_CATEGORY
            )
        )

        playerSelectKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping(
                VisionOptimizeIds.KEY_PLAYER_SELECT_NAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                VisionOptimizeIds.KEY_CATEGORY
            )
        )
    }
}
