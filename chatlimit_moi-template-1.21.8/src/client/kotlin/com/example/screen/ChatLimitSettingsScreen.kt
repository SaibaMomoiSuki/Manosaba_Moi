package com.example.screen

import com.example.config.ChatLimitConfig
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class ChatLimitSettingsScreen(
    private val config: ChatLimitConfig,
) : Screen(Component.literal("ChatLimit 设置")) {

    private var enableButton: Button? = null
    private var disableButton: Button? = null
    private var silenceButton: Button? = null

    companion object {
        private const val BUTTON_WIDTH = 160
        private const val BUTTON_HEIGHT = 20
        private const val TITLE_Y = 30
        private const val FIRST_BUTTON_Y = 60
        private const val BUTTON_SPACING = 28
    }

    override fun init() {
        super.init()
        val centerX = width / 2
        val buttonX = centerX - BUTTON_WIDTH / 2

        // [启用] — Green
        enableButton = Button.builder(Component.literal("▶ 开启距离限制").withStyle(ChatFormatting.GREEN), this::onEnablePress)
                .bounds(buttonX, FIRST_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build()
        addRenderableWidget(enableButton!!)

        // [禁用] — Red
        disableButton = Button.builder(Component.literal("■ 关闭距离限制").withStyle(ChatFormatting.RED), this::onDisablePress)
                .bounds(buttonX, FIRST_BUTTON_Y + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build()
        addRenderableWidget(disableButton!!)

        silenceButton = Button.builder(Component.literal("♟ 玩家沉默+关闭距离").withStyle(ChatFormatting.YELLOW), this::onPlayerSilencePress)
                .bounds(buttonX, FIRST_BUTTON_Y + BUTTON_SPACING * 2, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("player 标签玩家禁言15秒，同时关闭距离限制")))
                .build()
        addRenderableWidget(silenceButton!!)
        updateToggleButtons()

        // [聊天距离设置] — Gold
        addRenderableWidget(
            Button.builder(Component.literal("◆ 聊天距离设置").withStyle(ChatFormatting.GOLD), this::onChatDistancePress)
                .bounds(buttonX, FIRST_BUTTON_Y + BUTTON_SPACING * 3, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("设置清晰/模糊/略有/听不见的聊天距离范围")))
                .build()
        )

        // [穿透物品设置] — Gold
        addRenderableWidget(
            Button.builder(Component.literal("◆ 穿透物品设置").withStyle(ChatFormatting.GOLD), this::onPenetratePress)
                .bounds(buttonX, FIRST_BUTTON_Y + BUTTON_SPACING * 4, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("设置不完整方块/密度小/中/高方块的穿透格数")))
                .build()
        )

        // [轴点倍数设置] — Gold
        addRenderableWidget(
            Button.builder(Component.literal("◆ 轴点倍数设置").withStyle(ChatFormatting.GOLD), this::onAxisMultiplierPress)
                .bounds(buttonX, FIRST_BUTTON_Y + BUTTON_SPACING * 5, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("设置Y/X/Z轴的距离限制倍数")))
                .build()
        )

        // [小声说话范围] — Green
        addRenderableWidget(
            Button.builder(Component.literal("♪ 小声说话范围").withStyle(ChatFormatting.GREEN), this::onQuietRangePress)
                .bounds(buttonX, FIRST_BUTTON_Y + BUTTON_SPACING * 6, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("设置小声说话时X/Y/Z轴的听觉范围(格)")))
                .build()
        )

        // [大喊增大声音范围] — Green
        addRenderableWidget(
            Button.builder(Component.literal("♫ 大喊增大声音范围").withStyle(ChatFormatting.GREEN), this::onShoutRangePress)
                .bounds(buttonX, FIRST_BUTTON_Y + BUTTON_SPACING * 7, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("设置大喊时的声音传播倍率与方块穿透倍率")))
                .build()
        )

        // [心声设置] — Yellow
        addRenderableWidget(
            Button.builder(Component.literal("♥ 心声设置").withStyle(ChatFormatting.YELLOW), this::onHeartTagsPress)
                .bounds(buttonX, FIRST_BUTTON_Y + BUTTON_SPACING * 8, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("管理心声标签：持有标签的玩家才能听到心声")))
                .build()
        )

        // [麦克风设置] — Light blue
        addRenderableWidget(
            Button.builder(Component.literal("● 麦克风设置").withStyle(ChatFormatting.AQUA), this::onMicrophonePress)
                .bounds(buttonX, FIRST_BUTTON_Y + BUTTON_SPACING * 9, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("选择语音输入使用的系统麦克风")))
                .build()
        )

        // [取消设置] — Dark Red
        addRenderableWidget(
            Button.builder(Component.literal("✖ 取消设置").withStyle(ChatFormatting.DARK_RED), this::onCancelPress)
                .bounds(buttonX, FIRST_BUTTON_Y + BUTTON_SPACING * 10, BUTTON_WIDTH, BUTTON_HEIGHT).build()
        )
    }

    private fun onEnablePress(button: Button) {
        if (config.enabled) return
        sendCommand("chatlimit start")
    }

    private fun onDisablePress(button: Button) {
        if (!config.enabled) return
        sendCommand("chatlimit stop")
    }

    private fun onPlayerSilencePress(button: Button) {
        if (!config.enabled) return
        sendCommand("chatlimit player_silence")
    }

    private fun updateToggleButtons() {
        enableButton?.active = !config.enabled
        disableButton?.active = config.enabled
        silenceButton?.active = config.enabled
    }

    private fun onChatDistancePress(button: Button) {
        minecraft?.setScreen(ChatDistanceSettingsScreen(config, this))
    }

    private fun onPenetratePress(button: Button) {
        minecraft?.setScreen(PenetrationSettingsScreen(config, this))
    }

    private fun onAxisMultiplierPress(button: Button) {
        minecraft?.setScreen(AxisMultiplierSettingsScreen(config, this))
    }

    private fun onQuietRangePress(button: Button) {
        minecraft?.setScreen(QuietRangeSettingsScreen(config, this))
    }

    private fun onShoutRangePress(button: Button) {
        minecraft?.setScreen(ShoutRangeSettingsScreen(config, this))
    }

    private fun onHeartTagsPress(button: Button) {
        minecraft?.setScreen(HeartTagsSettingsScreen(this))
    }

    private fun onMicrophonePress(button: Button) {
        minecraft?.setScreen(MicrophoneSettingsScreen(this))
    }

    private fun onCancelPress(button: Button) = onClose()

    private fun sendCommand(cmd: String) {
        minecraft?.player?.connection?.sendCommand(cmd)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, delta)
        super.render(guiGraphics, mouseX, mouseY, delta)
        guiGraphics.drawCenteredString(
            font,
            Component.literal("ChatLimit 聊天距离限制设置").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            width / 2, TITLE_Y, 0xFFFFFF
        )
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        guiGraphics.fill(0, 0, width, height, 0xC0101010.toInt())
    }

    fun updateConfig(newConfig: ChatLimitConfig) {
        config.enabled = newConfig.enabled
        config.playerSilenceEnabled = newConfig.playerSilenceEnabled
        updateToggleButtons()
        config.chatLevelOneMin = newConfig.chatLevelOneMin
        config.chatLevelOneMax = newConfig.chatLevelOneMax
        config.chatLevelTwoMin = newConfig.chatLevelTwoMin
        config.chatLevelTwoMax = newConfig.chatLevelTwoMax
        config.chatLevelThreeMin = newConfig.chatLevelThreeMin
        config.chatLevelThreeMax = newConfig.chatLevelThreeMax
        config.chatLevelFour = newConfig.chatLevelFour
        config.penetrateLevelOne = newConfig.penetrateLevelOne
        config.penetrateLevelTwo = newConfig.penetrateLevelTwo
        config.penetrateLevelThree = newConfig.penetrateLevelThree
        config.penetrateLevelFour = newConfig.penetrateLevelFour
        config.yMultiplier = newConfig.yMultiplier
        config.xMultiplier = newConfig.xMultiplier
        config.zMultiplier = newConfig.zMultiplier
        config.quietRangeX = newConfig.quietRangeX
        config.quietRangeY = newConfig.quietRangeY
        config.quietRangeZ = newConfig.quietRangeZ
        config.shoutRangeMultiplier = newConfig.shoutRangeMultiplier
        config.shoutPenetrationMultiplier = newConfig.shoutPenetrationMultiplier
    }
}
