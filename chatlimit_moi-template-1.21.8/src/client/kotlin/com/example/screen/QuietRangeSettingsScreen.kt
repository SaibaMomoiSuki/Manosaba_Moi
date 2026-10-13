package com.example.screen

import com.example.config.ChatLimitConfig
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

class QuietRangeSettingsScreen(
    private val config: ChatLimitConfig,
    private val parent: Screen,
) : Screen(Component.literal("小声说话范围设置")) {

    private lateinit var rangeX: EditBox
    private lateinit var rangeY: EditBox
    private lateinit var rangeZ: EditBox
    private lateinit var applyButton: Button
    private var errorMessage: String? = null

    private val ox get() = ((width - 152) / 2).coerceAtLeast(0)
    private val labelX get() = ox + 12
    private val fieldX get() = ox + 80
    private val fieldW = 60
    private val fieldH = 20
    private val startY = 48
    private val rowGap = 32

    private val originalValues by lazy {
        mapOf(
            "x" to fmt(config.quietRangeX),
            "y" to fmt(config.quietRangeY),
            "z" to fmt(config.quietRangeZ),
        )
    }

    override fun init() {
        super.init()
        val y1 = startY; val y2 = y1 + rowGap; val y3 = y2 + rowGap

        rangeX = makeField(fieldX, y1, config.quietRangeX)
        rangeY = makeField(fieldX, y2, config.quietRangeY)
        rangeZ = makeField(fieldX, y3, config.quietRangeZ)

        val btnY = y3 + rowGap
        applyButton = Button.builder(Component.literal("应用"), this::onApplyPress)
            .bounds(width / 2 + 10, btnY, 60, 20).build()
        applyButton.active = false
        addRenderableWidget(applyButton)
        addRenderableWidget(Button.builder(Component.literal("取消").withStyle(ChatFormatting.RED), this::onCancelPress)
            .bounds(width / 2 - 70, btnY, 60, 20).build())
        updateApplyButtonState()
    }

    private fun makeField(x: Int, y: Int, initVal: Double): EditBox {
        val f = EditBox(font, x, y, fieldW, fieldH, Component.empty())
        f.value = fmt(initVal)
        f.setMaxLength(10)
        f.setResponder { errorMessage = null; updateApplyButtonState() }
        f.setFilter { it.isEmpty() || it.all { c -> c.isDigit() || c == '.' } && it.count { c -> c == '.' } <= 1 }
        addRenderableWidget(f)
        return f
    }

    private fun updateApplyButtonState() {
        val cur = values()
        applyButton.active = cur.any { (k, v) -> originalValues[k] != v } && validate() == null
    }

    private fun values() = mapOf(
        "x" to rangeX.value.ifBlank { fmt(config.quietRangeX) },
        "y" to rangeY.value.ifBlank { fmt(config.quietRangeY) },
        "z" to rangeZ.value.ifBlank { fmt(config.quietRangeZ) },
    )

    private fun validate(): String? {
        val v = values()
        val x = v["x"]!!.toDoubleOrNull() ?: return "§cX轴范围: 请输入数字"
        val y = v["y"]!!.toDoubleOrNull() ?: return "§cY轴范围: 请输入数字"
        val z = v["z"]!!.toDoubleOrNull() ?: return "§cZ轴范围: 请输入数字"
        if (x < 0) return "§cX轴范围不能为负数"
        if (y < 0) return "§cY轴范围不能为负数"
        if (z < 0) return "§cZ轴范围不能为负数"
        return null
    }

    private fun onApplyPress(button: Button) {
        val err = validate(); if (err != null) { errorMessage = err; updateApplyButtonState(); return }
        val v = values()
        val mc = minecraft ?: return
        mc.player?.connection?.sendCommand("chatlimit quiet ${v["x"]!!} ${v["y"]!!} ${v["z"]!!}")
        config.quietRangeX = v["x"]!!.toDouble()
        config.quietRangeY = v["y"]!!.toDouble()
        config.quietRangeZ = v["z"]!!.toDouble()
        mc.setScreen(parent)
    }

    private fun onCancelPress(button: Button) { minecraft?.setScreen(parent) }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, delta)
        super.render(guiGraphics, mouseX, mouseY, delta)
        val cx = width / 2; val white = 0xFFFFFFFF.toInt(); val gray = 0xFFAAAAAA.toInt()
        val y1 = startY; val y2 = y1 + rowGap; val y3 = y2 + rowGap

        guiGraphics.drawCenteredString(font, Component.literal("小声说话范围设置").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), cx, 15, white)
        guiGraphics.drawCenteredString(font, Component.literal("玩家小声说话时，只有在此范围内的玩家可以听到"), cx, 28, white)

        guiGraphics.drawString(font, Component.literal("X轴范围(格):"), labelX, y1 + 6, white)
        guiGraphics.drawString(font, Component.literal("Y轴范围(格):"), labelX, y2 + 6, white)
        guiGraphics.drawString(font, Component.literal("Z轴范围(格):"), labelX, y3 + 6, white)

        if (errorMessage != null) guiGraphics.drawCenteredString(font, Component.literal(errorMessage!!), cx, height - 40, 0xFFFF5555.toInt())
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        guiGraphics.fill(0, 0, width, height, 0xC0101010.toInt())
    }

    private fun fmt(v: Double): String = if (v.isInfinite()) "" else v.toBigDecimal().stripTrailingZeros().toPlainString()
}
