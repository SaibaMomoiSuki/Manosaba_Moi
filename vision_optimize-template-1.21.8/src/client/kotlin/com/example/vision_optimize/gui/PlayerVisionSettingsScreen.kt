package com.example.vision_optimize.gui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Main player vision settings sub-screen with two buttons.
 */
class PlayerVisionSettingsScreen(private val parent: Screen) : Screen(Component.literal("人物幻视设置")) {

    override fun init() {
        super.init()
        val centerX = this.width / 2
        var y = 40

        // [幻视文本设置] - Yellow
        addRenderableWidget(
            Button.Builder(
                Component.literal("幻视文本设置").withStyle { it.withColor(0xFFFF55) }
            ) {
                minecraft?.setScreen(PlayerVisionTagSettingsScreen(this))
            }.pos(centerX - 100, y).size(200, 20).build()
        )
        y += 30

        // [幻视使用设置] - Green
        addRenderableWidget(
            Button.Builder(
                Component.literal("幻视使用设置").withStyle { it.withColor(0x55FF55) }
            ) {
                minecraft?.setScreen(PlayerVisionUsageSettingsScreen(this))
            }.pos(centerX - 100, y).size(200, 20).build()
        )
        y += 35

        // 返回
        addRenderableWidget(
            Button.Builder(Component.literal("返回")) {
                minecraft?.setScreen(parent)
            }.pos(centerX - 50, y).size(100, 20).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(guiGraphics, mouseX, mouseY, delta)
        guiGraphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF.toInt())
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }
}
