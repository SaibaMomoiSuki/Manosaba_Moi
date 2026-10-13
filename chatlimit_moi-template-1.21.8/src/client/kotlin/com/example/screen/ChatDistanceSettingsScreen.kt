package com.example.screen

import com.example.config.ChatLimitConfig
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

class ChatDistanceSettingsScreen(
    private val config: ChatLimitConfig,
    private val parent: Screen,
) : Screen(Component.literal("聊天距离设置")) {

    private lateinit var levelOneMin: EditBox
    private lateinit var levelOneMax: EditBox
    private lateinit var levelTwoMin: EditBox
    private lateinit var levelTwoMax: EditBox
    private lateinit var levelThreeMin: EditBox
    private lateinit var levelThreeMax: EditBox
    private lateinit var levelFour: EditBox
    private lateinit var applyButton: Button
    private var errorMessage: String? = null

    // Layout positions — the whole field group is horizontally centered.
    private val ox get() = ((width - 362) / 2).coerceAtLeast(0)
    private val labelX get() = ox + 12
    private val hintMinX get() = ox + 155
    private val fieldMinX get() = ox + 175
    private val sepX get() = ox + 242
    private val hintMaxX get() = ox + 270
    private val fieldMaxX get() = ox + 290
    private val fieldW = 60
    private val fieldH = 20
    private val startY = 48
    private val rowGap = 32

    private val originalValues: Map<String, String> by lazy {
        mapOf(
            "oneMin" to fmt(config.chatLevelOneMin),
            "oneMax" to fmt(config.chatLevelOneMax),
            "twoMin" to fmt(config.chatLevelTwoMin),
            "twoMax" to fmt(config.chatLevelTwoMax),
            "threeMin" to fmt(config.chatLevelThreeMin),
            "threeMax" to fmt(config.chatLevelThreeMax),
            "four" to fmt(config.chatLevelFour),
        )
    }

    override fun init() {
        super.init()
        val y1 = startY; val y2 = y1 + rowGap; val y3 = y2 + rowGap; val y4 = y3 + rowGap

        levelOneMin = makeField(fieldMinX, y1, config.chatLevelOneMin)
        levelOneMax = makeField(fieldMaxX, y1, config.chatLevelOneMax)

        levelTwoMin = makeField(fieldMinX, y2, config.chatLevelTwoMin)
        levelTwoMax = makeField(fieldMaxX, y2, config.chatLevelTwoMax)

        levelThreeMin = makeField(fieldMinX, y3, config.chatLevelThreeMin)
        levelThreeMax = makeField(fieldMaxX, y3, config.chatLevelThreeMax)

        levelFour = makeField(fieldMinX, y4, config.chatLevelFour)

        // Buttons
        val btnY = y4 + rowGap
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
        f.value = fmt(initVal) // set value BEFORE responder to avoid lateinit access in callback
        f.setMaxLength(20)
        f.setResponder { errorMessage = null; updateApplyButtonState() }
        f.setFilter { it.isEmpty() || "infinity".startsWith(it.lowercase()) || (it.all { c -> c.isDigit() || c == '.' || c == '-' } && it.count { c -> c == '.' } <= 1) }
        addRenderableWidget(f)
        return f
    }

    private fun updateApplyButtonState() {
        val cur = values(); applyButton.active = cur.any { (k, v) -> originalValues[k] != v } && validate() == null
    }
    private fun values() = mapOf(
        "oneMin" to levelOneMin.value.ifBlank { fmt(config.chatLevelOneMin) },
        "oneMax" to levelOneMax.value.ifBlank { fmt(config.chatLevelOneMax) },
        "twoMin" to levelTwoMin.value.ifBlank { fmt(config.chatLevelTwoMin) },
        "twoMax" to levelTwoMax.value.ifBlank { fmt(config.chatLevelTwoMax) },
        "threeMin" to levelThreeMin.value.ifBlank { fmt(config.chatLevelThreeMin) },
        "threeMax" to levelThreeMax.value.ifBlank { fmt(config.chatLevelThreeMax) },
        "four" to levelFour.value.ifBlank { fmt(config.chatLevelFour) },
    )

    private fun validate(): String? {
        val v = values()
        val oMin = parseD(v["oneMin"]!!) ?: return "§c清楚声音范围: 无效的最小值"
        val oMax = parseD(v["oneMax"]!!) ?: return "§c清楚声音范围: 无效的最大值"
        val tMin = parseD(v["twoMin"]!!) ?: return "§c模糊声音范围: 无效的最小值"
        val tMax = parseD(v["twoMax"]!!) ?: return "§c模糊声音范围: 无效的最大值"
        val thMin = parseD(v["threeMin"]!!) ?: return "§c略有声音范围: 无效的最小值"
        val thMax = parseD(v["threeMax"]!!) ?: return "§c略有声音范围: 无效的最大值"
        val fVal = parseD(v["four"]!!) ?: return "§c听不见声音范围: 无效的数值"
        if (oMin >= oMax) return "§c清楚声音范围: 最小值 < 最大值"
        if (tMin >= tMax) return "§c模糊声音范围: 最小值 < 最大值"
        if (thMin >= thMax) return "§c略有声音范围: 最小值 < 最大值"
        if (oMax > tMin) return "§c清楚范围 < 模糊范围"
        if (tMax > thMin) return "§c模糊范围 < 略有范围"
        if (thMax > fVal) return "§c略有范围 < 听不见范围"
        return null
    }

    private fun onApplyPress(button: Button) {
        val err = validate(); if (err != null) { errorMessage = err; updateApplyButtonState(); return }
        val v = values()
        val mc = minecraft ?: return
        mc.player?.connection?.sendCommand("chatlimit chat level one ${v["oneMin"]!!.ifBlank { "infinity" }} ${v["oneMax"]!!.ifBlank { "infinity" }}")
        mc.player?.connection?.sendCommand("chatlimit chat level two ${v["twoMin"]!!.ifBlank { "infinity" }} ${v["twoMax"]!!.ifBlank { "infinity" }}")
        mc.player?.connection?.sendCommand("chatlimit chat level three ${v["threeMin"]!!.ifBlank { "infinity" }} ${v["threeMax"]!!.ifBlank { "infinity" }}")
        mc.player?.connection?.sendCommand("chatlimit chat level four ${v["four"]!!.ifBlank { "infinity" }}")
        config.chatLevelOneMin = parseD(v["oneMin"]!!) ?: config.chatLevelOneMin
        config.chatLevelOneMax = parseD(v["oneMax"]!!) ?: config.chatLevelOneMax
        config.chatLevelTwoMin = parseD(v["twoMin"]!!) ?: config.chatLevelTwoMin
        config.chatLevelTwoMax = parseD(v["twoMax"]!!) ?: config.chatLevelTwoMax
        config.chatLevelThreeMin = parseD(v["threeMin"]!!) ?: config.chatLevelThreeMin
        config.chatLevelThreeMax = parseD(v["threeMax"]!!) ?: config.chatLevelThreeMax
        config.chatLevelFour = parseD(v["four"]!!) ?: config.chatLevelFour
        mc.setScreen(parent)
    }

    private fun onCancelPress(button: Button) { minecraft?.setScreen(parent) }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, delta)
        super.render(guiGraphics, mouseX, mouseY, delta)

        val cx = width / 2; val y1 = startY; val y2 = y1 + rowGap; val y3 = y2 + rowGap; val y4 = y3 + rowGap
        val white = 0xFFFFFFFF.toInt(); val gray = 0xFFAAAAAA.toInt()

        guiGraphics.drawCenteredString(font, Component.literal("聊天距离设置").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), cx, 15, white)
        guiGraphics.drawCenteredString(font, Component.literal("越远文字越模糊且越暗，超出最大范围则无法听见"), cx, 28, white)

        // Row 1
        guiGraphics.drawString(font, Component.literal("清楚声音范围"), labelX, y1 + 6, white)
        guiGraphics.drawString(font, Component.literal("最小"), hintMinX, y1 + 6, white)
        guiGraphics.drawString(font, Component.literal("最大"), hintMaxX, y1 + 6, white)
        guiGraphics.drawString(font, "---", sepX, y1 + 6, gray)
        // Row 2
        guiGraphics.drawString(font, Component.literal("模糊声音范围"), labelX, y2 + 6, white)
        guiGraphics.drawString(font, Component.literal("最小"), hintMinX, y2 + 6, white)
        guiGraphics.drawString(font, Component.literal("最大"), hintMaxX, y2 + 6, white)
        guiGraphics.drawString(font, "---", sepX, y2 + 6, gray)
        // Row 3
        guiGraphics.drawString(font, Component.literal("略有声音范围"), labelX, y3 + 6, white)
        guiGraphics.drawString(font, Component.literal("最小"), hintMinX, y3 + 6, white)
        guiGraphics.drawString(font, Component.literal("最大"), hintMaxX, y3 + 6, white)
        guiGraphics.drawString(font, "---", sepX, y3 + 6, gray)
        // Row 4
        guiGraphics.drawString(font, Component.literal("听不见声音范围"), labelX, y4 + 6, white)
        guiGraphics.drawString(font, Component.literal("距离"), hintMinX, y4 + 6, white)
        guiGraphics.drawString(font, "--- infinity", sepX, y4 + 6, gray)

        if (errorMessage != null) guiGraphics.drawCenteredString(font, Component.literal(errorMessage!!), cx, height - 40, 0xFFFF5555.toInt())
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        guiGraphics.fill(0, 0, width, height, 0xC0101010.toInt())
    }

    private fun fmt(v: Double): String = if (v.isInfinite()) "" else v.toBigDecimal().stripTrailingZeros().toPlainString()
    private fun parseD(s: String): Double? = if (s.isBlank() || s.equals("infinity", ignoreCase = true)) Double.POSITIVE_INFINITY else s.toDoubleOrNull()
}
