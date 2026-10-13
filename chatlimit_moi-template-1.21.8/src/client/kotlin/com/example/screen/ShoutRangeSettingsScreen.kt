package com.example.screen

import com.example.config.ChatLimitConfig
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

class ShoutRangeSettingsScreen(
    private val config: ChatLimitConfig,
    private val parent: Screen,
) : Screen(Component.literal("大喊增大声音范围设置")) {

    private lateinit var rangeMultiplier: EditBox
    private lateinit var penetrationMultiplier: EditBox
    private lateinit var applyButton: Button
    private var errorMessage: String? = null

    private val ox get() = ((width - 282) / 2).coerceAtLeast(0)
    private val labelX get() = ox + 12
    private val fieldX get() = ox + 210
    private val fieldW = 60
    private val fieldH = 20
    private val startY = 48
    private val rowGap = 32

    private val originalValues by lazy {
        mapOf(
            "range" to fmt(config.shoutRangeMultiplier),
            "pen" to fmt(config.shoutPenetrationMultiplier),
        )
    }

    override fun init() {
        super.init()
        val y1 = startY; val y2 = y1 + rowGap

        rangeMultiplier = makeField(fieldX, y1, config.shoutRangeMultiplier)
        penetrationMultiplier = makeField(fieldX, y2, config.shoutPenetrationMultiplier)

        val btnY = y2 + rowGap
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
        "range" to rangeMultiplier.value.ifBlank { fmt(config.shoutRangeMultiplier) },
        "pen" to penetrationMultiplier.value.ifBlank { fmt(config.shoutPenetrationMultiplier) },
    )

    private fun validate(): String? {
        val v = values()
        val range = v["range"]!!.toDoubleOrNull() ?: return "§c声音范围倍率: 请输入数字"
        val pen = v["pen"]!!.toDoubleOrNull() ?: return "§c方块穿透倍率: 请输入数字"
        if (range < 0) return "§c声音范围倍率不能为负数"
        if (pen < 0) return "§c方块穿透倍率不能为负数"
        return null
    }

    private fun onApplyPress(button: Button) {
        val err = validate(); if (err != null) { errorMessage = err; updateApplyButtonState(); return }
        val v = values()
        val mc = minecraft ?: return
        mc.player?.connection?.sendCommand("chatlimit shout_spread ${v["range"]!!}")
        mc.player?.connection?.sendCommand("chatlimit shout_penetrate ${v["pen"]!!}")
        config.shoutRangeMultiplier = v["range"]!!.toDouble()
        config.shoutPenetrationMultiplier = v["pen"]!!.toDouble()
        mc.setScreen(parent)
    }

    private fun onCancelPress(button: Button) { minecraft?.setScreen(parent) }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, delta)
        super.render(guiGraphics, mouseX, mouseY, delta)
        val cx = width / 2; val white = 0xFFFFFFFF.toInt()
        val y1 = startY; val y2 = y1 + rowGap

        guiGraphics.drawCenteredString(font, Component.literal("大喊增大声音范围设置").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), cx, 15, white)
        guiGraphics.drawCenteredString(font, Component.literal("设置大喊时的声音传播倍率与穿透倍率"), cx, 28, white)

        guiGraphics.drawString(font, Component.literal("声音传播范围倍率(乘以):"), labelX, y1 + 6, white)
        guiGraphics.drawString(font, Component.literal("方块穿透格数倍率(乘以):"), labelX, y2 + 6, white)

        if (errorMessage != null) guiGraphics.drawCenteredString(font, Component.literal(errorMessage!!), cx, height - 40, 0xFFFF5555.toInt())
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        guiGraphics.fill(0, 0, width, height, 0xC0101010.toInt())
    }

    private fun fmt(v: Double): String = if (v.isInfinite()) "" else v.toBigDecimal().stripTrailingZeros().toPlainString()
}
