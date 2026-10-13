package com.example.vision_optimize.gui

import com.example.vision_optimize.client.ClientVisionState
import com.example.vision_optimize.util.ManosabaCharacters
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * "魔法少女の魔女审判" feature settings screen.
 * Contains a red toggle button to enable/disable character name coloring,
 * with a tooltip listing all 14 characters in their respective colors.
 */
class ManosabaSettingsScreen(private val parent: Screen) : Screen(Component.literal("特殊文本输出")) {

    override fun init() {
        val centerX = width / 2
        var y = 40

        // Toggle button — red label like the spec requests
        val manosabaEnabled = ClientVisionState.cachedConfig?.manosabaEnabled == true
        val toggleLabel = if (manosabaEnabled) "魔法少女的魔女审判: ✓ 开启" else "魔法少女的魔女审判: ✕ 关闭"
        val toggleColor = if (manosabaEnabled) 0x55FF55 else 0xFF5555

        // Build tooltip with all character names in their colors
        val tooltipLines = ManosabaCharacters.allInfos.map { info ->
            Component.literal("${info.tag}---${info.displayName}")
                .withStyle { it.withColor(info.colors.first()) }
        }
        val tooltipComponent = Component.empty()
        for (i in tooltipLines.indices) {
            if (i > 0) tooltipComponent.append(Component.literal("\n"))
            tooltipComponent.append(tooltipLines[i])
        }

        val toggleBtn = Button.Builder(
            Component.literal(toggleLabel).withStyle { it.withColor(toggleColor) }
        ) {
            if (manosabaEnabled) {
                VisionSettingsScreen.sendConfigUpdate("manosaba_disable", "")
                ClientVisionState.cachedConfig?.manosabaEnabled = false
            } else {
                VisionSettingsScreen.sendConfigUpdate("manosaba_enable", "")
                ClientVisionState.cachedConfig?.manosabaEnabled = true
            }
            rebuildWidgets()
        }.pos(centerX - 110, y).size(220, 20)
            .tooltip(Tooltip.create(tooltipComponent))
            .build()
        addRenderableWidget(toggleBtn)
        y += 28

        // Back button
        val backBtn = Button.Builder(
            Component.literal("返回")
        ) {
            minecraft?.setScreen(parent)
        }.pos(centerX - 50, height - 30).size(100, 20).build()
        addRenderableWidget(backBtn)
    }

    override fun render(guiGraphics: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(guiGraphics, mouseX, mouseY, delta)
        guiGraphics.drawCenteredString(font, title, width / 2, 8, 0xFFFF55)
    }
}
