package com.example.screen

import com.example.ChatLimit_MoiClient
import com.example.network.ChatLimitClientNetwork
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

class HeartTagsSettingsScreen(
    private val parent: Screen,
) : Screen(Component.literal("心声设置")) {

    private lateinit var inputField: EditBox
    private lateinit var addButton: Button
    private lateinit var deleteButton: Button

    private val tags = mutableListOf<String>()
    private var selectedIndex = -1

    private val listY = 85
    private val rowH = 22

    private fun listX(): Int = width / 2 - 110
    private fun listW(): Int = 220

    override fun init() {
        super.init()
        tags.clear()
        tags.addAll(ChatLimit_MoiClient.heartTags)

        val cx = width / 2
        val inputY = 50

        inputField = EditBox(font, cx - 110, inputY, 150, 20, Component.empty())
        inputField.setMaxLength(32)
        inputField.setHint(Component.literal("输入标签..."))
        addRenderableWidget(inputField)

        addButton = Button.builder(Component.literal("+"), this::onAddPress)
            .bounds(cx + 46, inputY, 24, 20).build()
        addRenderableWidget(addButton)

        deleteButton = Button.builder(Component.literal("删除").withStyle(ChatFormatting.RED), this::onDeletePress)
            .bounds(cx - 70, height - 50, 60, 20).build()
        addRenderableWidget(deleteButton)

        addRenderableWidget(Button.builder(Component.literal("取消").withStyle(ChatFormatting.DARK_RED), this::onCancelPress)
            .bounds(cx + 10, height - 50, 60, 20).build())

        updateDeleteButton()

        // Pull the latest tag list from the server
        ChatLimitClientNetwork.requestHeartTags()
    }

    private fun onAddPress(button: Button) {
        val tag = inputField.value.trim()
        if (tag.isEmpty()) return
        if (tag !in tags) {
            tags.add(tag)
            syncTags()
        }
        inputField.value = ""
        selectedIndex = -1
        updateDeleteButton()
    }

    private fun onDeletePress(button: Button) {
        if (selectedIndex !in tags.indices) return
        tags.removeAt(selectedIndex)
        selectedIndex = -1
        syncTags()
        updateDeleteButton()
    }

    private fun onCancelPress(button: Button) {
        minecraft?.setScreen(parent)
    }

    private fun syncTags() {
        ChatLimit_MoiClient.heartTags.clear()
        ChatLimit_MoiClient.heartTags.addAll(tags)
        ChatLimitClientNetwork.sendHeartTags(tags.toList())
    }

    private fun updateDeleteButton() {
        deleteButton.active = selectedIndex in tags.indices
    }

    fun refreshTags(newTags: List<String>) {
        tags.clear()
        tags.addAll(newTags)
        selectedIndex = -1
        updateDeleteButton()
    }

    private fun hitTestTag(mouseX: Int, mouseY: Int): Int {
        if (mouseX < listX() || mouseX > listX() + listW()) return -1
        val rel = mouseY - listY
        if (rel < 0) return -1
        val idx = rel / rowH
        return if (idx in tags.indices) idx else -1
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val handled = super.mouseClicked(mouseX, mouseY, button)
        if (button == 0) {
            val idx = hitTestTag(mouseX.toInt(), mouseY.toInt())
            if (idx != -1) {
                selectedIndex = idx
                updateDeleteButton()
                return true
            }
        }
        return handled
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, delta)
        super.render(guiGraphics, mouseX, mouseY, delta)

        val cx = width / 2
        val white = 0xFFFFFFFF.toInt()
        val gray = 0xFFAAAAAA.toInt()

        guiGraphics.drawCenteredString(font, Component.literal("心声设置").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), cx, 20, white)
        guiGraphics.drawCenteredString(
            font,
            Component.literal("持有标签的玩家才能听到「心声」"),
            cx, 36, gray
        )

        guiGraphics.drawString(font, Component.literal("标签:"), listX() - 36, 50 + 6, white)

        if (tags.isEmpty()) {
            guiGraphics.drawString(font, Component.literal("暂无标签，请在上方输入后点击 + 添加"), listX(), listY + 4, gray)
        } else {
            for (i in tags.indices) {
                val y = listY + i * rowH
                val selected = i == selectedIndex
                if (selected) {
                    guiGraphics.fill(listX(), y, listX() + listW(), y + rowH - 2, 0x80FFDD55.toInt())
                }
                val color = if (selected) 0xFFFFFF00.toInt() else white
                guiGraphics.drawString(font, Component.literal(tags[i]), listX() + 4, y + 4, color)
            }
        }
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        guiGraphics.fill(0, 0, width, height, 0xC0101010.toInt())
    }
}
