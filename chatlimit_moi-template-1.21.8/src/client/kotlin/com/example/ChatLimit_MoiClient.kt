package com.example

import com.example.audio.VoiceInputManager
import com.example.config.ChatLimitConfig
import com.example.network.ChatLimitClientNetwork
import com.example.screen.ChatLimitSettingsScreen
import com.example.screen.HeartTagsSettingsScreen
import com.example.state.SpeakModeState
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.KeyMapping
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW
import java.util.Collections
import java.util.WeakHashMap

object ChatLimit_MoiClient : ClientModInitializer {
    var clientConfig: ChatLimitConfig = ChatLimitConfig()

    /** Cached list of this player's 心声 (heart voice) tags, synced from the server. */
    var heartTags: MutableList<String> = mutableListOf()
    @Volatile
    var adminAccess: Boolean = false

    private val statusScreens: MutableSet<Screen> =
        Collections.newSetFromMap(WeakHashMap())

    private val openSettingsKey: KeyMapping = KeyBindingHelper.registerKeyBinding(
        KeyMapping(
            "key.chatlimit_moi.settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_6,
            "key.category.chatlimit_moi"
        )
    )
    private var adminAccessSyncTicks: Int = 0

    /** Toggles 正常 ↔ 心声 while the chat screen is open (rebindable in controls). */
    private val toggleSpeakModeKey: KeyMapping = KeyBindingHelper.registerKeyBinding(
        KeyMapping(
            "key.chatlimit_moi.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.category.chatlimit_moi"
        )
    )

    /** True when the given key/scan code matches the toggle binding (handles rebinds). */
    fun isToggleKey(keyCode: Int, scanCode: Int): Boolean =
        toggleSpeakModeKey.matches(keyCode, scanCode)

    /** Human-readable name of the toggle key, e.g. "V" — shown in the hover tooltip. */
    fun getToggleKeyName(): String = toggleSpeakModeKey.translatedKeyMessage.string

    override fun onInitializeClient() {
        // Create the config/model folders so the player has an obvious place to
        // drop an extracted Vosk model into.
        VoiceInputManager.ensureDirectories()

        HudElementRegistry.addLast(
            ResourceLocation.fromNamespaceAndPath("chatlimit_moi", "model_status")
        ) { graphics, _ ->
            if (Minecraft.getInstance().screen == null) renderModelStatus(graphics)
        }
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (statusScreens.add(screen)) {
                ScreenEvents.afterRender(screen).register { _, graphics, _, _, _ ->
                    renderModelStatus(graphics)
                }
            }
        }

        // Enumerate audio devices, build the pinyin tables and load the speech
        // model now, on a background thread, rather than paying for it on the
        // render thread the first time the player opens chat.
        VoiceInputManager.warmUp()

        // Note: Payload types are registered in ChatLimit_Moi (server entrypoint).
        // In the dev environment both sides share a JVM, so calling registerCommon()
        // here would cause "already registered" errors.

        // Register S2C packet receiver — server sends config → open GUI
        ChatLimitClientNetwork.registerClientReceiver { config ->
            clientConfig = config
            val mc = Minecraft.getInstance()
            mc.execute {
                val currentScreen = mc.screen
                if (currentScreen is ChatLimitSettingsScreen) {
                    currentScreen.updateConfig(config)
                } else {
                    mc.setScreen(ChatLimitSettingsScreen(config))
                }
            }
        }

        // Register heart tags sync receiver — server sends this player's tags
        ChatLimitClientNetwork.registerHeartTagsReceiver { tags ->
            heartTags.clear()
            heartTags.addAll(tags)
            val mc = Minecraft.getInstance()
            mc.execute {
                val currentScreen = mc.screen
                if (currentScreen is HeartTagsSettingsScreen) {
                    currentScreen.refreshTags(tags)
                }
            }
        }

        ChatLimitClientNetwork.registerRestrictionReceiver { maxPosition, remainingMillis ->
            SpeakModeState.updateRestriction(maxPosition, remainingMillis)
        }

        ChatLimitClientNetwork.registerAdminAccessReceiver { allowed ->
            adminAccess = allowed
            SpeakModeState.ensureAvailableMode()
        }

        // Keybinding KP_6 → request config from server → GUI opens on response
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.player == null) {
                adminAccess = false
                adminAccessSyncTicks = 0
            } else if (++adminAccessSyncTicks >= 40) {
                adminAccessSyncTicks = 0
                ChatLimitClientNetwork.requestAdminAccess()
            }
            SpeakModeState.ensureAvailableMode()
            while (openSettingsKey.consumeClick()) {
                if (client.player != null) {
                    ChatLimitClientNetwork.requestConfigFromServer()
                }
            }
        }
    }

    private fun renderModelStatus(graphics: GuiGraphics) {
        val text = VoiceInputManager.getModelStatusText() ?: return
        val mc = Minecraft.getInstance()
        val component = Component.literal(text)
        val x = 4
        val y = mc.window.guiScaledHeight - 18
        val width = mc.font.width(component) + 8
        graphics.fill(x, y, x + width, y + 14, 0xB0101010.toInt())
        graphics.drawString(mc.font, component, x + 4, y + 3, 0xFFFFFF55.toInt(), false)
    }
}
