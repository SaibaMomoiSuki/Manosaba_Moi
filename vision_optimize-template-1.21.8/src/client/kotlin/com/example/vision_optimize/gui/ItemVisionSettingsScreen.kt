package com.example.vision_optimize.gui

import com.example.vision_optimize.client.ClientVisionState
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class ItemVisionSettingsScreen(private val parent: Screen) : Screen(Component.literal("物品幻视设置")) {

    private data class Label(val x: Int, val y: Int, val text: String, val color: Int)
    private val labels = mutableListOf<Label>()
    private val fields = mutableMapOf<String, EditBox>()

    private val WHITE = 0xFFFFFFFF.toInt()
    private val RED = 0xFFFF5555.toInt()
    private val ERROR_RED = 0xFFFF5555.toInt()
    private val NORMAL_TEXT = 0xE0E0E0 or (0xFF shl 24)
    private var errorMessage: String? = null
    private val errorFields = mutableSetOf<String>()
    private var applyButton: Button? = null

    override fun init() {
        super.init()
        labels.clear()
        fields.clear()
        errorFields.clear()
        errorMessage = null
        val cx = width / 2
        var y = 30

        // 计分板
        addLabel(cx - 150, y - 12, "计分板", WHITE)
        fields["item_scoreboard"] = addField(cx - 150, y, 300, 20, "计分板名称")
        y += 28

        // Level1
        addLabel(cx - 150, y - 12, "Level1", WHITE)
        addLabel(cx - 100, y - 12, "[infinity]", RED)
        fields["nbt_l1_min"] = addField(cx - 100, y, 80, 18, "L1 min")
        addLabel(cx - 14, y + 2, "---", WHITE)
        fields["nbt_l1_max"] = addField(cx + 20, y, 80, 18, "L1 max")
        addRenderableWidget(
            Button.builder(Component.literal("重置").withStyle({ it.withColor(0xFF5555) })) {
                VisionSettingsScreen.sendConfigUpdate("nbt_level_one_reset", "")
                fields["nbt_l1_min"]?.value = ""
                fields["nbt_l1_max"]?.value = ""
                revalidate()
            }.pos(cx + 110, y).size(45, 18).build()
        )
        y += 32

        // Level2
        addLabel(cx - 150, y - 12, "Level2", WHITE)
        addLabel(cx - 100, y - 12, "[infinity]", RED)
        fields["nbt_l2_min"] = addField(cx - 100, y, 80, 18, "L2 min")
        addLabel(cx - 14, y + 2, "---", WHITE)
        fields["nbt_l2_max"] = addField(cx + 20, y, 80, 18, "L2 max")
        addRenderableWidget(
            Button.builder(Component.literal("重置").withStyle({ it.withColor(0xFF5555) })) {
                VisionSettingsScreen.sendConfigUpdate("nbt_level_two_reset", "")
                fields["nbt_l2_min"]?.value = ""
                fields["nbt_l2_max"]?.value = ""
                revalidate()
            }.pos(cx + 110, y).size(45, 18).build()
        )
        y += 32

        // Level1可显示玩家数量
        addLabel(cx - 150, y - 12, "Level1可显示玩家数量", 0xFF55FF55.toInt())
        fields["item_l1_display"] = addField(cx - 50, y, 100, 18, "display count")
        y += 28

        // Level2判定时间{秒}
        addLabel(cx - 150, y - 12, "Level2判定时间{秒}", WHITE)
        fields["item_l2_time"] = addField(cx - 50, y, 100, 18, "vision time")
        y += 28

        // Level2干扰时间{分}
        addLabel(cx - 150, y - 12, "Level2干扰时间{分}", WHITE)
        fields["item_death_false"] = addField(cx - 50, y, 100, 18, "interference time")
        y += 28

        // 物品冷却时间
        addLabel(cx - 150, y - 12, "物品冷却时间[单位秒]", WHITE)
        fields["item_cooldown"] = addField(cx - 50, y, 100, 18, "冷却时间")
        y += 28

        // 物品幻视初始成功率
        addLabel(cx - 150, y - 12, "物品幻视初始成功率[0~100%]", WHITE)
        fields["nbt_vision_percent"] = addField(cx - 50, y, 100, 18, "成功率")
        y += 28

        // 物品幻视失败后提升概率
        addLabel(cx - 150, y - 12, "物品幻视失败后提升概率[0~100%]", WHITE)
        fields["nbt_vision_fail_add"] = addField(cx - 50, y, 100, 18, "提升概率")
        y += 36

        // Buttons
        val btn = Button.builder(Component.literal("应用设置")) {
            tryApplySettings()
        }.pos(cx - 60, y).size(120, 20).build()
        applyButton = btn
        addRenderableWidget(btn)
        y += 25

        addRenderableWidget(
            Button.builder(Component.literal("返回")) {
                minecraft?.setScreen(parent)
            }.pos(cx - 50, y).size(100, 20).build()
        )

        // Set up change listeners for scoreboard fields
        setupValidationListeners()

        loadValues()
        revalidate()
    }

    private fun setupValidationListeners() {
        val sbFields = listOf("nbt_l1_min", "nbt_l1_max", "nbt_l2_min", "nbt_l2_max")
        for (key in sbFields) {
            fields[key]?.setResponder { revalidate() }
        }
    }

    private fun revalidate() {
        errorFields.clear()
        errorMessage = validateScoreboardRanges()
        applyButton?.active = (errorMessage == null)
        for ((key, field) in fields) {
            field.setTextColor(if (key in errorFields) ERROR_RED else NORMAL_TEXT)
        }
    }

    private fun tryApplySettings() {
        revalidate()
        if (errorMessage != null) return
        for ((key, editBox) in fields) {
            val value = editBox.value.trim()
            if (value.isNotEmpty()) {
                VisionSettingsScreen.sendConfigUpdate(key, value)
            }
        }
        minecraft?.setScreen(parent)
    }

    private fun loadValues() {
        val c = ClientVisionState.cachedConfig ?: return
        fields["item_scoreboard"]?.value = c.itemL1Scoreboard
        fields["nbt_l1_min"]?.value = c.itemL1ScoreMin?.fmt() ?: ""
        fields["nbt_l1_max"]?.value = c.itemL1ScoreMax?.fmt() ?: ""
        fields["nbt_l2_min"]?.value = c.itemL2ScoreMin?.fmt() ?: ""
        fields["nbt_l2_max"]?.value = c.itemL2ScoreMax?.fmt() ?: ""
        fields["item_l1_display"]?.value = c.itemL1MaxDisplay.toString()
        fields["item_l2_time"]?.value = c.itemL2VisionTime.toString()
        fields["item_death_false"]?.value = (c.itemDeathFalseTime / 60).toString()
        fields["item_cooldown"]?.value = c.itemCooldownTime.toString()
        fields["nbt_vision_percent"]?.value = c.itemVisionPercent.toString()
        fields["nbt_vision_fail_add"]?.value = c.itemVisionFailAdd.toString()
    }

    private fun addField(x: Int, y: Int, w: Int, h: Int, hint: String): EditBox {
        val box = EditBox(font, x, y, w, h, Component.literal(hint))
        addRenderableWidget(box)
        return box
    }

    // ===== Validation =====

    private fun validateScoreboardRanges(): String? {
        val l1Min = fieldDouble("nbt_l1_min") ?: ClientVisionState.cachedConfig?.itemL1ScoreMin
        val l1Max = fieldDouble("nbt_l1_max") ?: ClientVisionState.cachedConfig?.itemL1ScoreMax
        val l2Min = fieldDouble("nbt_l2_min") ?: ClientVisionState.cachedConfig?.itemL2ScoreMin
        val l2Max = fieldDouble("nbt_l2_max") ?: ClientVisionState.cachedConfig?.itemL2ScoreMax

        // 1. Each level: min < max
        if (l1Min != null && l1Max != null && l1Min >= l1Max) {
            markError("nbt_l1_min", "nbt_l1_max")
            return "计分板范围错误: Level1 min($l1Min) 必须小于 max($l1Max)"
        }
        if (l2Min != null && l2Max != null && l2Min >= l2Max) {
            markError("nbt_l2_min", "nbt_l2_max")
            return "计分板范围错误: Level2 min($l2Min) 必须小于 max($l2Max)"
        }
        // 2. No overlap between levels (adjacent boundaries OK — upper bound exclusive)
        if ((l1Min != null || l1Max != null) && (l2Min != null || l2Max != null)) {
            val aLo = l1Min ?: Double.NEGATIVE_INFINITY
            val aHi = l1Max ?: Double.POSITIVE_INFINITY
            val bLo = l2Min ?: Double.NEGATIVE_INFINITY
            val bHi = l2Max ?: Double.POSITIVE_INFINITY
            if (maxOf(aLo, bLo) < minOf(aHi, bHi)) {
                markError("nbt_l1_min", "nbt_l1_max", "nbt_l2_min", "nbt_l2_max")
                return "计分板范围重叠: Level1 与 Level2 存在共同数值"
            }
        }
        return null
    }

    private fun fieldDouble(key: String): Double? = fields[key]?.value?.trim()?.toDoubleOrNull()
    private fun markError(vararg keys: String) { keys.forEach { errorFields.add(it) } }
    private fun Double.fmt(): String = if (this == toLong().toDouble()) toLong().toString() else toString()

    // ===== Rendering =====

    private fun addLabel(x: Int, y: Int, text: String, color: Int) {
        labels.add(Label(x, y, text, color))
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(guiGraphics, mouseX, mouseY, delta)
        guiGraphics.drawCenteredString(font, title, width / 2, 8, WHITE)
        guiGraphics.drawCenteredString(
            font, "注: 计分板 min<max 且范围不可重叠(相邻边界除外)",
            width / 2, height - 26, 0xFFFFAA00.toInt()
        )
        if (errorMessage != null) {
            guiGraphics.drawCenteredString(
                font, "§c错误: $errorMessage",
                width / 2, height - 14, 0xFFFF5555.toInt()
            )
        }
        for (lbl in labels) {
            guiGraphics.drawString(font, lbl.text, lbl.x, lbl.y, lbl.color)
        }
    }
}
