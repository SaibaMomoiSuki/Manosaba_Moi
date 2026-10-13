package com.example.vision_optimize.gui

import com.example.vision_optimize.client.ClientVisionState
import com.example.vision_optimize.config.AccessMode
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Main settings screen with 7 buttons.
 * Button states reflect the current mod mode:
 * - disabled → [禁用] grayed out
 * - enabled  → [启用] grayed out
 * - ALL_MEMBERS → [全员使用] + [特定标签设置] grayed out
 * - TAG_MEMBER  → [特定标签使用] grayed out
 *
 * Opened via KP_3 key binding.
 */
class VisionSettingsScreen : Screen(Component.literal("Vision Optimize 设置")) {

    override fun init() {
        super.init()
        val cached = ClientVisionState.cachedConfig
        val modEnabled = cached?.enabled ?: true
        val modMode = cached?.mode ?: AccessMode.ALL_MEMBERS

        val centerX = this.width / 2
        var y = 40

        // [禁用] - Red, grayed when already disabled
        val disableBtn = Button.Builder(
            Component.literal("禁用").withStyle { it.withColor(if (modEnabled) 0xFF5555 else 0x555555) }
        ) {
            if (modEnabled) { sendConfigUpdate("disable", ""); updateLocal { enabled = false }; rebuild() }
        }.pos(centerX - 100, y).size(200, 20).build()
        disableBtn.active = modEnabled
        addRenderableWidget(disableBtn)
        y += 25

        // [启用] - Green, grayed when already enabled
        val enableBtn = Button.Builder(
            Component.literal("启用").withStyle { it.withColor(if (modEnabled) 0x55FF55 else 0x555555) }
        ) {
            if (!modEnabled) { sendConfigUpdate("enable", ""); updateLocal { enabled = true }; rebuild() }
        }.pos(centerX - 100, y).size(200, 20).build()
        enableBtn.active = !modEnabled
        addRenderableWidget(enableBtn)
        y += 25

        // [全员使用] - Yellow, grayed in ALL_MEMBERS mode
        val isAllMembers = modMode == AccessMode.ALL_MEMBERS
        val allMemberBtn = Button.Builder(
            Component.literal("全员使用").withStyle { it.withColor(if (isAllMembers) 0x555555 else 0xFFFF55) }
        ) {
            if (!isAllMembers) { sendConfigUpdate("AllMember", ""); updateLocal { mode = AccessMode.ALL_MEMBERS }; rebuild() }
        }.pos(centerX - 100, y).size(200, 20).build()
        allMemberBtn.active = !isAllMembers
        addRenderableWidget(allMemberBtn)
        y += 25

        // [特定标签使用] - Gray, grayed in TAG_MEMBER mode
        val isTagMember = modMode == AccessMode.TAG_MEMBER
        val tagMemberBtn = Button.Builder(
            Component.literal("特定标签使用").withStyle { it.withColor(if (isTagMember) 0x555555 else 0xAAAAAA) }
        ) {
            if (!isTagMember) { sendConfigUpdate("TagMember", ""); updateLocal { mode = AccessMode.TAG_MEMBER }; rebuild() }
        }.pos(centerX - 100, y).size(200, 20).build()
        tagMemberBtn.active = !isTagMember
        addRenderableWidget(tagMemberBtn)
        y += 30

        // [特定标签设置] - Gray, grayed in ALL_MEMBERS mode (meaningless without tag mode)
        val tagSettingsBtn = Button.Builder(
            Component.literal("特定标签设置").withStyle { it.withColor(if (isAllMembers) 0x555555 else 0xAAAAAA) }
        ) {
            if (!isAllMembers) minecraft?.setScreen(TagSettingsScreen(this))
        }.pos(centerX - 100, y).size(200, 20).build()
        tagSettingsBtn.active = !isAllMembers
        addRenderableWidget(tagSettingsBtn)
        y += 30

        // [物品幻视设置] - Yellow
        addRenderableWidget(
            Button.Builder(
                Component.literal("物品幻视设置").withStyle { it.withColor(0xFFFF55) }
            ) {
                minecraft?.setScreen(ItemVisionSettingsScreen(this))
            }.pos(centerX - 100, y).size(200, 20).build()
        )
        y += 25

        // [方块幻视设置] - Yellow
        addRenderableWidget(
            Button.Builder(
                Component.literal("方块幻视设置").withStyle { it.withColor(0xFFFF55) }
            ) {
                minecraft?.setScreen(BlockVisionSettingsScreen(this))
            }.pos(centerX - 100, y).size(200, 20).build()
        )
        y += 30

        // [人物幻视设置] - Yellow
        addRenderableWidget(
            Button.Builder(
                Component.literal("人物幻视设置").withStyle { it.withColor(0xFFFF55) }
            ) {
                minecraft?.setScreen(PlayerVisionSettingsScreen(this))
            }.pos(centerX - 100, y).size(200, 20).build()
        )
        y += 30

        // [特殊文本输出] - Yellow
        addRenderableWidget(
            Button.Builder(
                Component.literal("特殊文本输出").withStyle { it.withColor(0xFFFF55) }
            ) {
                minecraft?.setScreen(ManosabaSettingsScreen(this))
            }.pos(centerX - 100, y).size(200, 20).build()
        )
        y += 30

        y += 5

        // Close button
        addRenderableWidget(
            Button.Builder(Component.literal("关闭")) {
                onClose()
            }.pos(centerX - 50, y).size(100, 20).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(guiGraphics, mouseX, mouseY, delta)
        val cx = this.width / 2

        guiGraphics.drawCenteredString(font, "Vision Optimize 设置", cx, 8, 0xFFFFFFFF.toInt())

        // Show current access tags
        val config = ClientVisionState.cachedConfig
        val accessTagsStr = config?.accessTags?.joinToString(", ") ?: "无"
        guiGraphics.drawCenteredString(
            font, "当前权限标签: $accessTagsStr", cx, 22, 0xFFFF55.toInt()
        )

        // Show player-tag assignments summary
        val playerTagsMap = config?.playerTags ?: emptyMap()
        val playerAssignments = playerTagsMap
            .filter { it.value.isNotEmpty() }
            .flatMap { (player, tags) -> tags.map { "$player[$it]" } }
            .joinToString(", ")
        if (playerAssignments.isNotEmpty()) {
            guiGraphics.drawCenteredString(
                font, "玩家标签分配: $playerAssignments",
                cx, this.height - 28, 0xFFAAAAAA.toInt()
            )
        }

        // Mode hint
        val modeStr = when (config?.mode) {
            com.example.vision_optimize.config.AccessMode.ALL_MEMBERS -> "全员模式"
            com.example.vision_optimize.config.AccessMode.TAG_MEMBER -> "标签模式"
            else -> "未知"
        }
        guiGraphics.drawCenteredString(
            font, "当前模式: $modeStr", cx, this.height - 14, 0xFFAAAAAA.toInt()
        )
    }

    private fun updateLocal(block: com.example.vision_optimize.config.ModConfig.() -> Unit) {
        val config = ClientVisionState.cachedConfig
        if (config != null) {
            block(config)
        }
    }

    private fun rebuild() {
        clearWidgets()
        init()
    }

    override fun onClose() {
        minecraft?.setScreen(null)
    }

    companion object {
        fun open() {
            Minecraft.getInstance().setScreen(VisionSettingsScreen())
        }

        fun sendConfigUpdate(key: String, value: String) {
            val packet = com.example.vision_optimize.network.ConfigUpdateC2SPacket(key, value)
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(packet)
        }
    }
}
