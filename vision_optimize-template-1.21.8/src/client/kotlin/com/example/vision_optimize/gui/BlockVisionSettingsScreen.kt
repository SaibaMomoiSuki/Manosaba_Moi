package com.example.vision_optimize.gui

import com.example.vision_optimize.client.ClientVisionState
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Block vision settings sub-screen.
 * Each EditBox is keyed so "应用设置" sends all values to the server.
 * SideEffect and scoreboard overlap validation with red error highlighting.
 */
class BlockVisionSettingsScreen(private val parent: Screen) : Screen(Component.literal("方块幻视设置")) {

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
        var y = 28

        // 计分板
        addLabel(cx - 150, y - 12, "计分板", WHITE)
        fields["block_scoreboard"] = addField(cx - 150, y, 300, 18, "计分板")
        y += 26

        // Level rows
        addLevelRow(cx, y, "Level1", "block_l1"); y += 26
        addLevelRow(cx, y, "Level2", "block_l2"); y += 26
        addLevelRow(cx, y, "Level3", "block_l3"); y += 26

        // 搜索时间
        addLabel(cx - 150, y - 12, "搜索时间(秒)", WHITE)
        fields["search_time"] = addField(cx - 150, y, 200, 18, "搜索时间")
        y += 26

        // 方块冷却时间
        addLabel(cx - 150, y - 12, "方块冷却时间[单位秒]", WHITE)
        fields["block_cooldown"] = addField(cx - 150, y, 200, 18, "冷却时间")
        y += 26

        // 方块幻视初始成功率
        addLabel(cx - 150, y - 12, "方块幻视初始成功率[0~100%]", WHITE)
        fields["block_vision_percent"] = addField(cx - 150, y, 200, 18, "成功率")
        y += 26

        // 方块幻视失败后提升概率
        addLabel(cx - 150, y - 12, "方块幻视失败后提升概率[0~100%]", WHITE)
        fields["block_vision_fail_add"] = addField(cx - 150, y, 200, 18, "提升概率")
        y += 26

        // Scope rows
        addScopeRow(cx, y, "l1_scope"); y += 26
        addScopeRow(cx, y, "l2_scope"); y += 26
        addScopeRow(cx, y, "l3_scope"); y += 26

        // Time settings
        addTimeRow(cx, y, "l1_after_time"); y += 26
        addTimeRow(cx, y, "l1_player_interval"); y += 26
        addTimeRow(cx, y, "l2_se1"); y += 26
        addMinMaxRow(cx, y, "l2_se2"); y += 26
        addMinMaxRow(cx, y, "l2_se3"); y += 26
        addMinMaxRow(cx, y, "l2_se4"); y += 26
        addTimeRow(cx, y, "l3_after_time"); y += 28

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

        // Set up change listeners on SideEffect and scoreboard fields for live validation
        setupValidationListeners()

        loadValues()
        // Initial validation
        revalidate()
    }

    /** Attach change listeners to fields that affect validation. */
    private fun setupValidationListeners() {
        val seFields = listOf("l2_se1", "l2_se2_min", "l2_se2_max", "l2_se3_min", "l2_se3_max", "l2_se4_min", "l2_se4_max")
        val sbFields = listOf("block_l1_min", "block_l1_max", "block_l2_min", "block_l2_max", "block_l3_min", "block_l3_max")
        for (key in seFields + sbFields) {
            fields[key]?.setResponder { revalidate() }
        }
    }

    /** Re-run validation and update button/field states. */
    private fun revalidate() {
        errorFields.clear()
        errorMessage = validateSideEffectOrder() ?: validateScoreboardRanges()
        applyButton?.active = (errorMessage == null)
        // Color fields
        for ((key, field) in fields) {
            field.setTextColor(if (key in errorFields) ERROR_RED else NORMAL_TEXT)
        }
    }

    private fun tryApplySettings() {
        revalidate()
        if (errorMessage != null) return  // button should be disabled, but double-check
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
        fields["block_scoreboard"]?.value = c.blockL1Scoreboard
        fields["search_time"]?.value = c.blockSearchTime.toString()
        fields["block_cooldown"]?.value = c.blockCooldownTime.toString()
        fields["block_vision_percent"]?.value = c.blockVisionPercent.toString()
        fields["block_vision_fail_add"]?.value = c.blockVisionFailAdd.toString()
        fields["block_l1_min"]?.value = c.blockL1ScoreMin?.fmt() ?: ""
        fields["block_l1_max"]?.value = c.blockL1ScoreMax?.fmt() ?: ""
        fields["block_l2_min"]?.value = c.blockL2ScoreMin?.fmt() ?: ""
        fields["block_l2_max"]?.value = c.blockL2ScoreMax?.fmt() ?: ""
        fields["block_l3_min"]?.value = c.blockL3ScoreMin?.fmt() ?: ""
        fields["block_l3_max"]?.value = c.blockL3ScoreMax?.fmt() ?: ""
        fields["l1_scope_x"]?.value = c.blockL1ScopeX.toString()
        fields["l1_scope_y"]?.value = c.blockL1ScopeY.toString()
        fields["l1_scope_z"]?.value = c.blockL1ScopeZ.toString()
        fields["l2_scope_x"]?.value = c.blockL2ScopeX.toString()
        fields["l2_scope_y"]?.value = c.blockL2ScopeY.toString()
        fields["l2_scope_z"]?.value = c.blockL2ScopeZ.toString()
        fields["l3_scope_x"]?.value = c.blockL3ScopeX.toString()
        fields["l3_scope_y"]?.value = c.blockL3ScopeY.toString()
        fields["l3_scope_z"]?.value = c.blockL3ScopeZ.toString()
        fields["l1_after_time"]?.value = c.blockL1AfterTime.toString()
        fields["l1_player_interval"]?.value = c.blockL1PlayerInterval.toString()
        fields["l2_se1"]?.value = c.blockL2SideEffect1.toString()
        fields["l2_se2_min"]?.value = c.blockL2SideEffect2Min?.fmt() ?: ""
        fields["l2_se2_max"]?.value = c.blockL2SideEffect2Max?.fmt() ?: ""
        fields["l2_se3_min"]?.value = c.blockL2SideEffect3Min?.fmt() ?: ""
        fields["l2_se3_max"]?.value = c.blockL2SideEffect3Max?.fmt() ?: ""
        fields["l2_se4_min"]?.value = c.blockL2SideEffect4Min?.fmt() ?: ""
        fields["l2_se4_max"]?.value = c.blockL2SideEffect4Max?.fmt() ?: ""
        fields["l3_after_time"]?.value = c.blockL3AfterTime.toString()
    }

    private fun addField(x: Int, y: Int, w: Int, h: Int, hint: String): EditBox {
        val box = EditBox(font, x, y, w, h, Component.literal(hint))
        addRenderableWidget(box)
        return box
    }

    private fun addLevelRow(cx: Int, y: Int, name: String, prefix: String) {
        addLabel(cx - 150, y - 12, name, WHITE)
        addLabel(cx - 100, y - 12, "[infinity]", RED)
        fields["${prefix}_min"] = addField(cx - 100, y, 80, 18, "$name min")
        addLabel(cx - 14, y + 2, "---", WHITE)
        fields["${prefix}_max"] = addField(cx + 20, y, 80, 18, "$name max")
        addRenderableWidget(
            Button.builder(Component.literal("重置").withStyle({ it.withColor(0xFF5555) })) {
                VisionSettingsScreen.sendConfigUpdate("${prefix}_reset", "")
                fields["${prefix}_min"]?.value = ""
                fields["${prefix}_max"]?.value = ""
                revalidate()
            }.pos(cx + 110, y).size(40, 18).build()
        )
    }

    private fun addScopeRow(cx: Int, y: Int, prefix: String) {
        val label = when (prefix) {
            "l1_scope" -> "Level1方块幻视范围 X / Y / Z"
            "l2_scope" -> "Level2方块幻视范围 X / Y / Z"
            else -> "Level3方块幻视范围 X / Y / Z"
        }
        addLabel(cx - 150, y - 12, label, WHITE)
        fields["${prefix}_x"] = addField(cx - 150, y, 50, 18, "X")
        addLabel(cx - 94, y + 2, "---", WHITE)
        fields["${prefix}_y"] = addField(cx - 90, y, 50, 18, "Y")
        addLabel(cx - 34, y + 2, "---", WHITE)
        fields["${prefix}_z"] = addField(cx - 30, y, 50, 18, "Z")
    }

    private fun addTimeRow(cx: Int, y: Int, key: String) {
        val label = when (key) {
            "l1_after_time" -> "Level1玩家经过判定时间(秒)"
            "l1_player_interval" -> "Level1玩家在一起判定时间(秒)"
            "l2_se1" -> "Level2附近判定时间(秒) [0 ~ SE1]"
            "l3_after_time" -> "Level3玩家判定时间范围(秒)"
            else -> key
        }
        addLabel(cx - 150, y - 12, label, WHITE)
        fields[key] = addField(cx - 150, y, 200, 18, label)
    }

    private fun addMinMaxRow(cx: Int, y: Int, prefix: String) {
        val label = when (prefix) {
            "l2_se2" -> "Level2不久前判定时间(秒) [SE2_min ~ SE2_max]"
            "l2_se3" -> "Level2最近判定时间(秒) [SE3_min ~ SE3_max]"
            else -> "Level2曾经判定时间(秒) [SE4_min ~ SE4_max]"
        }
        addLabel(cx - 150, y - 12, label, WHITE)
        fields["${prefix}_min"] = addField(cx - 150, y, 80, 18, "min")
        addLabel(cx - 64, y + 2, "---", WHITE)
        fields["${prefix}_max"] = addField(cx - 60, y, 80, 18, "max")
    }

    // ===== Validation =====

    private fun validateSideEffectOrder(): String? {
        val se1 = fieldDouble("l2_se1") ?: ClientVisionState.cachedConfig?.blockL2SideEffect1?.toDouble()
        val se2Min = fieldDouble("l2_se2_min") ?: ClientVisionState.cachedConfig?.blockL2SideEffect2Min
        val se2Max = fieldDouble("l2_se2_max") ?: ClientVisionState.cachedConfig?.blockL2SideEffect2Max
        val se3Min = fieldDouble("l2_se3_min") ?: ClientVisionState.cachedConfig?.blockL2SideEffect3Min
        val se3Max = fieldDouble("l2_se3_max") ?: ClientVisionState.cachedConfig?.blockL2SideEffect3Max
        val se4Min = fieldDouble("l2_se4_min") ?: ClientVisionState.cachedConfig?.blockL2SideEffect4Min
        val se4Max = fieldDouble("l2_se4_max") ?: ClientVisionState.cachedConfig?.blockL2SideEffect4Max

        if (se1 == null) { markError("l2_se1"); return "附近(SE1) 必须填写数值" }
        if (se2Min == null) { markError("l2_se2_min"); return "不久前 min 必须填写数值" }
        if (se2Max == null) { markError("l2_se2_max"); return "不久前 max 必须填写数值" }
        if (se3Min == null) { markError("l2_se3_min"); return "最近 min 必须填写数值" }
        if (se3Max == null) { markError("l2_se3_max"); return "最近 max 必须填写数值" }
        if (se4Min == null) { markError("l2_se4_min"); return "曾经 min 必须填写数值" }
        if (se4Max == null) { markError("l2_se4_max"); return "曾经 max 必须填写数值" }

        // Internal: each interval must have min < max
        if (se2Min >= se2Max) { markError("l2_se2_min", "l2_se2_max"); return "不久前: min($se2Min) 必须 < max($se2Max)" }
        if (se3Min >= se3Max) { markError("l2_se3_min", "l2_se3_max"); return "最近: min($se3Min) 必须 < max($se3Max)" }
        if (se4Min >= se4Max) { markError("l2_se4_min", "l2_se4_max"); return "曾经: min($se4Min) 必须 < max($se4Max)" }
        // Adjacent boundaries: may be equal (value belongs to higher range)
        if (se1 > se2Min) { markError("l2_se1", "l2_se2_min"); return "附近($se1) 不能大于 不久前 min($se2Min)" }
        if (se2Max > se3Min) { markError("l2_se2_max", "l2_se3_min"); return "不久前 max($se2Max) 不能大于 最近 min($se3Min)" }
        if (se3Max > se4Min) { markError("l2_se3_max", "l2_se4_min"); return "最近 max($se3Max) 不能大于 曾经 min($se4Min)" }
        return null
    }

    private fun validateScoreboardRanges(): String? {
        val ranges = listOf(
            Triple("Level1", fieldDouble("block_l1_min") ?: ClientVisionState.cachedConfig?.blockL1ScoreMin, fieldDouble("block_l1_max") ?: ClientVisionState.cachedConfig?.blockL1ScoreMax),
            Triple("Level2", fieldDouble("block_l2_min") ?: ClientVisionState.cachedConfig?.blockL2ScoreMin, fieldDouble("block_l2_max") ?: ClientVisionState.cachedConfig?.blockL2ScoreMax),
            Triple("Level3", fieldDouble("block_l3_min") ?: ClientVisionState.cachedConfig?.blockL3ScoreMin, fieldDouble("block_l3_max") ?: ClientVisionState.cachedConfig?.blockL3ScoreMax)
        ).filter { it.second != null || it.third != null }  // only ranges with at least one bound
        // 1. Each level: min < max
        for ((name, min, max) in ranges) {
            if (min != null && max != null && min >= max) {
                markLevelError(name)
                return "计分板范围错误: $name min($min) 必须小于 max($max)"
            }
        }
        // 2. No overlap between levels (adjacent boundaries OK — upper bound exclusive)
        for (i in ranges.indices) {
            for (j in i + 1 until ranges.size) {
                val (na, minA, maxA) = ranges[i]
                val (nb, minB, maxB) = ranges[j]
                val aLo = minA ?: Double.NEGATIVE_INFINITY
                val aHi = maxA ?: Double.POSITIVE_INFINITY
                val bLo = minB ?: Double.NEGATIVE_INFINITY
                val bHi = maxB ?: Double.POSITIVE_INFINITY
                if (maxOf(aLo, bLo) < minOf(aHi, bHi)) {
                    markLevelError(na); markLevelError(nb)
                    return "计分板范围重叠: $na 与 $nb 存在共同数值"
                }
            }
        }
        return null
    }

    private fun markLevelError(level: String) {
        when (level) {
            "Level1" -> markError("block_l1_min", "block_l1_max")
            "Level2" -> markError("block_l2_min", "block_l2_max")
            "Level3" -> markError("block_l3_min", "block_l3_max")
        }
    }

    private fun fieldDouble(key: String): Double? = fields[key]?.value?.trim()?.toDoubleOrNull()
    private fun markError(vararg keys: String) { keys.forEach { errorFields.add(it) } }
    /** Format Double: show as integer if whole number, otherwise keep decimal. */
    private fun Double.fmt(): String = if (this == toLong().toDouble()) toLong().toString() else toString()

    // ===== Rendering =====

    private fun addLabel(x: Int, y: Int, text: String, color: Int) {
        labels.add(Label(x, y, text, color))
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(guiGraphics, mouseX, mouseY, delta)
        guiGraphics.drawCenteredString(font, title, width / 2, 4, WHITE)
        guiGraphics.drawCenteredString(
            font, "约束: 附近 < 不久前区间 < 最近区间 < 曾经区间 | 计分板 min<max 且范围不可重叠(相邻边界除外)",
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
