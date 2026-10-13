package com.example.vision_optimize.gui

import com.example.vision_optimize.client.ClientVisionState
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Player vision usage settings sub-screen with scoreboard support.
 * Follows the same pattern as BlockVisionSettingsScreen.
 */
class PlayerVisionUsageSettingsScreen(private val parent: Screen) : Screen(Component.literal("幻视使用设置")) {

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

        // ===== 计分板设置 =====
        addLabel(cx - 150, y - 12, "计分板", WHITE)
        fields["pv_scoreboard"] = addField(cx - 150, y, 300, 18, "计分板")
        y += 26

        // Level 1/2/3 min/max rows
        addPvLevelRow(cx, y, "Level1", "pv_l1"); y += 26
        addPvLevelRow(cx, y, "Level2", "pv_l2"); y += 26
        addPvLevelRow(cx, y, "Level3", "pv_l3"); y += 28

        // ===== 范围设置 =====
        addLabel(cx - 150, y - 12, "人物幻视范围 X / Y / Z (单位格)", WHITE)
        fields["pv_range_x"] = addField(cx - 150, y, 50, 18, "X")
        addLabel(cx - 94, y + 2, "---", WHITE)
        fields["pv_range_y"] = addField(cx - 90, y, 50, 18, "Y")
        addLabel(cx - 34, y + 2, "---", WHITE)
        fields["pv_range_z"] = addField(cx - 30, y, 50, 18, "Z")
        y += 28

        // 人物可幻视距离(单位格) - legacy
        addLabel(cx - 150, y - 12, "人物可幻视距离(单位格)", WHITE)
        fields["pv_distance"] = addField(cx - 150, y, 200, 18, "距离")
        y += 26

        // 被发现反应时间(单位秒)
        addLabel(cx - 150, y - 12, "被发现反应时间(单位秒)", WHITE)
        fields["pv_reaction_time"] = addField(cx - 150, y, 200, 18, "反应时间")
        y += 26

        // 人物幻视失败冷却(时间单位分)
        addLabel(cx - 150, y - 12, "人物幻视失败冷却(时间单位分)", WHITE)
        fields["pv_fail_cooldown"] = addField(cx - 150, y, 200, 18, "冷却")
        y += 26

        // 人物幻视成功冷却(时间单位分)
        addLabel(cx - 150, y - 12, "人物幻视成功冷却(时间单位分)", WHITE)
        fields["pv_success_cooldown"] = addField(cx - 150, y, 200, 18, "冷却")
        y += 26

        // 视野内进行人物幻视时间(单位刻 1秒=20刻)
        addLabel(cx - 150, y - 12, "视野内人物幻视时间(单位刻 1秒=20刻)", WHITE)
        fields["pv_raid_time"] = addField(cx - 150, y, 200, 18, "时间")
        y += 26

        // 视野外进行人物幻视时间(单位刻 1秒=20刻)
        addLabel(cx - 150, y - 12, "视野外人物幻视时间(单位刻 1秒=20刻)", WHITE)
        fields["pv_sneak_time"] = addField(cx - 150, y, 200, 18, "时间")
        y += 26

        // 第三者发现所需时间(单位刻 1秒=20刻)
        addLabel(cx - 150, y - 12, "第三者发现所需时间(单位刻 1秒=20刻)", WHITE)
        fields["pv_discover_time"] = addField(cx - 150, y, 200, 18, "时间")
        y += 26

        // 第三者视野内最远距离(单位格)
        addLabel(cx - 150, y - 12, "第三者视野内最远距离(单位格)", WHITE)
        fields["pv_third_perspective"] = addField(cx - 150, y, 200, 18, "距离")
        y += 28

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

        // Validation listeners on scoreboard fields
        for (key in listOf("pv_l1_min", "pv_l1_max", "pv_l2_min", "pv_l2_max", "pv_l3_min", "pv_l3_max")) {
            fields[key]?.setResponder { revalidate() }
        }

        loadValues()
        revalidate()
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
        fields["pv_scoreboard"]?.value = c.playerVisionL1Scoreboard
        fields["pv_l1_min"]?.value = c.playerVisionL1ScoreMin?.fmt() ?: ""
        fields["pv_l1_max"]?.value = c.playerVisionL1ScoreMax?.fmt() ?: ""
        fields["pv_l2_min"]?.value = c.playerVisionL2ScoreMin?.fmt() ?: ""
        fields["pv_l2_max"]?.value = c.playerVisionL2ScoreMax?.fmt() ?: ""
        fields["pv_l3_min"]?.value = c.playerVisionL3ScoreMin?.fmt() ?: ""
        fields["pv_l3_max"]?.value = c.playerVisionL3ScoreMax?.fmt() ?: ""
        fields["pv_range_x"]?.value = c.playerVisionRangeX.toString()
        fields["pv_range_y"]?.value = c.playerVisionRangeY.toString()
        fields["pv_range_z"]?.value = c.playerVisionRangeZ.toString()
        fields["pv_distance"]?.value = c.playerVisionDistance.toString()
        fields["pv_reaction_time"]?.value = c.playerVisionReactionTime.toString()
        fields["pv_fail_cooldown"]?.value = c.playerVisionFailCooldown.toString()
        fields["pv_success_cooldown"]?.value = c.playerVisionSuccessCooldown.toString()
        fields["pv_raid_time"]?.value = c.playerVisionRaidTime.toString()
        fields["pv_sneak_time"]?.value = c.playerVisionSneakTime.toString()
        fields["pv_discover_time"]?.value = c.playerVisionDiscoverTime.toString()
        fields["pv_third_perspective"]?.value = c.playerVisionThirdPerspectiveDistance.toString()
    }

    private fun addPvLevelRow(cx: Int, y: Int, name: String, prefix: String) {
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

    private fun addField(x: Int, y: Int, w: Int, h: Int, hint: String): EditBox {
        val box = EditBox(font, x, y, w, h, Component.literal(hint))
        addRenderableWidget(box)
        return box
    }

    private fun addLabel(x: Int, y: Int, text: String, color: Int) {
        labels.add(Label(x, y, text, color))
    }

    // ===== Scoreboard Validation =====

    private fun validateScoreboardRanges(): String? {
        val ranges = listOf(
            Triple("Level1", fieldDouble("pv_l1_min") ?: ClientVisionState.cachedConfig?.playerVisionL1ScoreMin, fieldDouble("pv_l1_max") ?: ClientVisionState.cachedConfig?.playerVisionL1ScoreMax),
            Triple("Level2", fieldDouble("pv_l2_min") ?: ClientVisionState.cachedConfig?.playerVisionL2ScoreMin, fieldDouble("pv_l2_max") ?: ClientVisionState.cachedConfig?.playerVisionL2ScoreMax),
            Triple("Level3", fieldDouble("pv_l3_min") ?: ClientVisionState.cachedConfig?.playerVisionL3ScoreMin, fieldDouble("pv_l3_max") ?: ClientVisionState.cachedConfig?.playerVisionL3ScoreMax)
        ).filter { it.second != null || it.third != null }

        // Each level: min < max
        for ((name, min, max) in ranges) {
            if (min != null && max != null && min >= max) {
                markLevelError(name)
                return "计分板范围错误: $name min($min) 必须小于 max($max)"
            }
        }
        // No overlap
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
            "Level1" -> markError("pv_l1_min", "pv_l1_max")
            "Level2" -> markError("pv_l2_min", "pv_l2_max")
            "Level3" -> markError("pv_l3_min", "pv_l3_max")
        }
    }

    private fun fieldDouble(key: String): Double? = fields[key]?.value?.trim()?.toDoubleOrNull()
    private fun markError(vararg keys: String) { keys.forEach { errorFields.add(it) } }
    private fun Double.fmt(): String = if (this == toLong().toDouble()) toLong().toString() else toString()

    // ===== Rendering =====

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(guiGraphics, mouseX, mouseY, delta)
        guiGraphics.drawCenteredString(font, title, width / 2, 4, WHITE)
        guiGraphics.drawCenteredString(
            font, "计分板: min<max 且范围不可重叠(相邻边界除外)",
            width / 2, height - 26, 0xFFFFAA00.toInt()
        )
        if (errorMessage != null) {
            guiGraphics.drawCenteredString(
                font, "错误: $errorMessage",
                width / 2, height - 14, 0xFFFF5555.toInt()
            )
        }
        for (lbl in labels) {
            guiGraphics.drawString(font, lbl.text, lbl.x, lbl.y, lbl.color)
        }
    }
}
