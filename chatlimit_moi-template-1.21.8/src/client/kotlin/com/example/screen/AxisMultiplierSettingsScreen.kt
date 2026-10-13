package com.example.screen

import com.example.config.ChatLimitConfig
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

class AxisMultiplierSettingsScreen(
    private val config: ChatLimitConfig,
    private val parent: Screen,
) : Screen(Component.literal("轴点倍数设置")) {

    private lateinit var yField: EditBox
    private lateinit var xField: EditBox
    private lateinit var zField: EditBox
    private lateinit var applyButton: Button
    private var errorMessage: String? = null

    private val ox get() = ((width - 347) / 2).coerceAtLeast(0)
    private val labelX get() = ox + 12
    private val hintX get() = ox + 235
    private val fieldX get() = ox + 255
    private val fieldW = 80; private val fieldH = 20
    private val startY = 48; private val rowGap = 32

    private val originalValues: Map<String, String> by lazy {
        mapOf("y" to fmt(config.yMultiplier), "x" to fmt(config.xMultiplier), "z" to fmt(config.zMultiplier))
    }

    override fun init() {
        super.init()
        val y1 = startY; val y2 = y1 + rowGap; val y3 = y2 + rowGap
        yField = makeField(y1, config.yMultiplier)
        xField = makeField(y2, config.xMultiplier)
        zField = makeField(y3, config.zMultiplier)

        val btnY = y3 + rowGap
        applyButton = Button.builder(Component.literal("应用"), this::onApplyPress)
            .bounds(width / 2 + 10, btnY, 60, 20).build(); applyButton.active = false
        addRenderableWidget(applyButton)
        addRenderableWidget(Button.builder(Component.literal("取消").withStyle(ChatFormatting.RED), this::onCancelPress)
            .bounds(width / 2 - 70, btnY, 60, 20).build())
        updateState()
    }

    private fun makeField(y: Int, initVal: Double): EditBox {
        val f = EditBox(font, fieldX, y, fieldW, fieldH, Component.empty())
        f.value = fmt(initVal); f.setMaxLength(10)
        f.setResponder { errorMessage = null; updateState() }
        f.setFilter { it.isEmpty() || (it.all { c -> c.isDigit() || c == '.' } && it.count { c -> c == '.' } <= 1) }
        addRenderableWidget(f); return f
    }

    private fun updateState() {
        val cur = vals(); applyButton.active = cur.any { (k, v) -> originalValues[k] != v } && validate() == null
    }
    private fun vals() = mapOf(
        "y" to yField.value.ifBlank { fmt(config.yMultiplier) },
        "x" to xField.value.ifBlank { fmt(config.xMultiplier) },
        "z" to zField.value.ifBlank { fmt(config.zMultiplier) },
    )

    private fun validate(): String? {
        val v = vals()
        val vy = v["y"]!!.toDoubleOrNull() ?: return "§cy轴距离限制倍数: 无效数值"
        val vx = v["x"]!!.toDoubleOrNull() ?: return "§cx轴距离限制倍数: 无效数值"
        val vz = v["z"]!!.toDoubleOrNull() ?: return "§cz轴距离限制倍数: 无效数值"
        if (vy < 0) return "§cy轴倍数不能为负数"
        if (vy > 100) return "§cy轴倍数不能大于 100"
        if (vx < 0) return "§cx轴倍数不能为负数"
        if (vx > 100) return "§cx轴倍数不能大于 100"
        if (vz < 0) return "§cz轴倍数不能为负数"
        if (vz > 100) return "§cz轴倍数不能大于 100"
        return null
    }

    private fun onApplyPress(button: Button) {
        val err = validate(); if (err != null) { errorMessage = err; updateState(); return }
        val v = vals(); val mc = minecraft ?: return
        mc.player?.connection?.sendCommand("chatlimit y_limit ${v["y"]!!.ifBlank { "infinity" }}")
        mc.player?.connection?.sendCommand("chatlimit x_limit ${v["x"]!!.ifBlank { "infinity" }}")
        mc.player?.connection?.sendCommand("chatlimit z_limit ${v["z"]!!.ifBlank { "infinity" }}")
        config.yMultiplier = v["y"]!!.toDoubleOrNull() ?: config.yMultiplier
        config.xMultiplier = v["x"]!!.toDoubleOrNull() ?: config.xMultiplier
        config.zMultiplier = v["z"]!!.toDoubleOrNull() ?: config.zMultiplier
        mc.setScreen(parent)
    }

    private fun onCancelPress(button: Button) { minecraft?.setScreen(parent) }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, delta)
        super.render(guiGraphics, mouseX, mouseY, delta)
        val cx = width / 2; val y1 = startY; val y2 = y1 + rowGap; val y3 = y2 + rowGap
        val white = 0xFFFFFFFF.toInt()

        guiGraphics.drawCenteredString(font, Component.literal("轴点倍数设置").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), cx, 15, white)
        guiGraphics.drawCenteredString(font, Component.literal("数值越小该轴限制越严格，默认Y=0.5使垂直距离权重加倍"), cx, 28, white)

        guiGraphics.drawString(font, Component.literal("y轴距离限制倍数"), labelX, y1 + 6, white)
        guiGraphics.drawString(font, Component.literal("倍数"), hintX, y1 + 6, white)
        guiGraphics.drawString(font, Component.literal("x轴距离限制倍数"), labelX, y2 + 6, white)
        guiGraphics.drawString(font, Component.literal("倍数"), hintX, y2 + 6, white)
        guiGraphics.drawString(font, Component.literal("z轴距离限制倍数"), labelX, y3 + 6, white)
        guiGraphics.drawString(font, Component.literal("倍数"), hintX, y3 + 6, white)

        if (errorMessage != null) guiGraphics.drawCenteredString(font, Component.literal(errorMessage!!), cx, height - 40, 0xFFFF5555.toInt())
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        guiGraphics.fill(0, 0, width, height, 0xC0101010.toInt())
    }

    private fun fmt(v: Double): String = if (v.isInfinite()) "" else v.toBigDecimal().stripTrailingZeros().toPlainString()
}
