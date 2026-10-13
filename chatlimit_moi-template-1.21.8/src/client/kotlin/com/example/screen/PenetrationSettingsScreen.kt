package com.example.screen

import com.example.config.ChatLimitConfig
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

class PenetrationSettingsScreen(
    private val config: ChatLimitConfig,
    private val parent: Screen,
) : Screen(Component.literal("穿透物品设置")) {

    private lateinit var f1: EditBox
    private lateinit var f2: EditBox
    private lateinit var f3: EditBox
    private lateinit var f4: EditBox
    private lateinit var applyButton: Button
    private var errorMessage: String? = null

    private val ox get() = ((width - 347) / 2).coerceAtLeast(0)
    private val labelX get() = ox + 12
    private val hintX get() = ox + 235
    private val fieldX get() = ox + 255
    private val fieldW = 80; private val fieldH = 20
    private val startY = 48; private val rowGap = 32

    private val originalValues: Map<String, String> by lazy {
        mapOf("one" to fmt(config.penetrateLevelOne), "two" to fmt(config.penetrateLevelTwo),
              "three" to fmt(config.penetrateLevelThree), "four" to fmt(config.penetrateLevelFour))
    }

    override fun init() {
        super.init()
        val y1 = startY; val y2 = y1 + rowGap; val y3 = y2 + rowGap; val y4 = y3 + rowGap
        f1 = makeField(y1, config.penetrateLevelOne)
        f2 = makeField(y2, config.penetrateLevelTwo)
        f3 = makeField(y3, config.penetrateLevelThree)
        f4 = makeField(y4, config.penetrateLevelFour)

        val btnY = y4 + rowGap
        applyButton = Button.builder(Component.literal("应用"), this::onApplyPress)
            .bounds(width / 2 + 10, btnY, 60, 20).build(); applyButton.active = false
        addRenderableWidget(applyButton)
        addRenderableWidget(Button.builder(Component.literal("取消").withStyle(ChatFormatting.RED), this::onCancelPress)
            .bounds(width / 2 - 70, btnY, 60, 20).build())
        updateState()
    }

    private fun makeField(y: Int, initVal: Double): EditBox {
        val f = EditBox(font, fieldX, y, fieldW, fieldH, Component.empty())
        f.value = fmt(initVal); f.setMaxLength(20)
        f.setResponder { errorMessage = null; updateState() }
        f.setFilter { it.isEmpty() || "infinity".startsWith(it.lowercase()) || (it.all { c -> c.isDigit() || c == '.' || c == '-' } && it.count { c -> c == '.' } <= 1) }
        addRenderableWidget(f); return f
    }

    private fun updateState() {
        val cur = vals(); applyButton.active = cur.any { (k, v) -> originalValues[k] != v } && validate() == null
    }
    private fun vals() = mapOf(
        "one" to f1.value.ifBlank { fmt(config.penetrateLevelOne) },
        "two" to f2.value.ifBlank { fmt(config.penetrateLevelTwo) },
        "three" to f3.value.ifBlank { fmt(config.penetrateLevelThree) },
        "four" to f4.value.ifBlank { fmt(config.penetrateLevelFour) },
    )

    private fun validate(): String? {
        val v = vals()
        val v1 = parseD(v["one"]!!) ?: return "§c可穿透不完整方块格数: 无效数值"
        val v2 = parseD(v["two"]!!) ?: return "§c可穿透小密度方块格数: 无效数值"
        val v3 = parseD(v["three"]!!) ?: return "§c可穿透中密度方块格数: 无效数值"
        val v4 = parseD(v["four"]!!) ?: return "§c可穿透大密度方块格数: 无效数值"
        if (v1 < v2) return "§c不完整方块穿透 >= 小密度方块穿透"
        if (v2 < v3) return "§c小密度方块穿透 >= 中密度方块穿透"
        if (v3 < v4) return "§c中密度方块穿透 >= 大密度方块穿透"
        return null
    }

    private fun onApplyPress(button: Button) {
        val err = validate(); if (err != null) { errorMessage = err; updateState(); return }
        val v = vals(); val mc = minecraft ?: return
        mc.player?.connection?.sendCommand("chatlimit penetrate level one ${v["one"]!!.ifBlank { "infinity" }}")
        mc.player?.connection?.sendCommand("chatlimit penetrate level two ${v["two"]!!.ifBlank { "infinity" }}")
        mc.player?.connection?.sendCommand("chatlimit penetrate level three ${v["three"]!!.ifBlank { "infinity" }}")
        mc.player?.connection?.sendCommand("chatlimit penetrate level four ${v["four"]!!.ifBlank { "infinity" }}")
        config.penetrateLevelOne = parseD(v["one"]!!) ?: config.penetrateLevelOne
        config.penetrateLevelTwo = parseD(v["two"]!!) ?: config.penetrateLevelTwo
        config.penetrateLevelThree = parseD(v["three"]!!) ?: config.penetrateLevelThree
        config.penetrateLevelFour = parseD(v["four"]!!) ?: config.penetrateLevelFour
        mc.setScreen(parent)
    }

    private fun onCancelPress(button: Button) { minecraft?.setScreen(parent) }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, delta)
        super.render(guiGraphics, mouseX, mouseY, delta)
        val cx = width / 2; val y1 = startY; val y2 = y1 + rowGap; val y3 = y2 + rowGap; val y4 = y3 + rowGap
        val white = 0xFFFFFFFF.toInt()

        guiGraphics.drawCenteredString(font, Component.literal("穿透物品设置").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), cx, 15, white)
        guiGraphics.drawCenteredString(font, Component.literal("数值越大穿透能力越强，infinity 则该类型方块不阻挡声音"), cx, 28, white)

        guiGraphics.drawString(font, Component.literal("可穿透不完整方块格数"), labelX, y1 + 6, white)
        guiGraphics.drawString(font, Component.literal("格数"), hintX, y1 + 6, white)
        guiGraphics.drawString(font, Component.literal("可穿透小密度方块格数"), labelX, y2 + 6, white)
        guiGraphics.drawString(font, Component.literal("格数"), hintX, y2 + 6, white)
        guiGraphics.drawString(font, Component.literal("可穿透中密度方块格数 [几乎无用]"), labelX, y3 + 6, white)
        guiGraphics.drawString(font, Component.literal("格数"), hintX, y3 + 6, white)
        guiGraphics.drawString(font, Component.literal("可穿透大密度方块格数"), labelX, y4 + 6, white)
        guiGraphics.drawString(font, Component.literal("格数"), hintX, y4 + 6, white)

        if (errorMessage != null) guiGraphics.drawCenteredString(font, Component.literal(errorMessage!!), cx, height - 40, 0xFFFF5555.toInt())
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        guiGraphics.fill(0, 0, width, height, 0xC0101010.toInt())
    }

    private fun fmt(v: Double): String = if (v.isInfinite()) "" else v.toBigDecimal().stripTrailingZeros().toPlainString()
    private fun parseD(s: String): Double? = if (s.isBlank() || s.equals("infinity", ignoreCase = true)) Double.POSITIVE_INFINITY else s.toDoubleOrNull()
}
