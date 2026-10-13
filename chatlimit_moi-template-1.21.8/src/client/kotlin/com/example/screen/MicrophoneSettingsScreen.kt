package com.example.screen

import com.example.audio.ModelInstaller
import com.example.audio.ModelSources
import com.example.audio.VoiceInputManager
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.net.URI

/**
 * Settings for voice input, split into two pages (tabs):
 *
 *  - 麦克风: choose which system microphone (mixer) is used for recording.
 *  - 语音模型: choose which speech model is used, install the ones that are
 *    missing (downloading them, or unpacking the copies embedded in the
 *    `build2` jar), and open the model directory in the OS file manager.
 *
 * The lists are drawn and hit-tested by hand rather than built from widgets,
 * because they are re-scanned once a second and rebuilding widgets that often
 * would fight with the player's clicks.
 */
class MicrophoneSettingsScreen(
    private val parent: Screen,
) : Screen(Component.literal("麦克风设置")) {

    /** An "install this model" row under the model list. */
    private class InstallAction(val label: String, val run: () -> Unit)

    /** Microphone names; row 0 is the system default. */
    private val micRows: MutableList<String> = mutableListOf()
    private var micSelected = 0

    /** Speech models; row 0 selects "pick automatically". */
    private var modelRows: List<VoiceInputManager.SpeechModel> = emptyList()
    private var modelSelected = 0

    /** Models embedded in this jar — fixed for the life of the process. */
    private var bundled: List<String> = emptyList()

    private var actions: List<InstallAction> = emptyList()

    /** 0 = 麦克风 tab, 1 = 语音模型 tab. */
    private var tab = 0

    /** Drives the periodic model re-scan in [tick]. */
    private var ticks = 0

    private val listY = 85
    private val rowH = 22
    private val actionH = 20

    /** Left-side "模型下载" panel — links that open the model zip pages in the browser. */
    private val downloadX = 16
    private val downloadLabelY = 85
    private val downloadBtnW = 160
    private val downloadBtnH = 20

    private fun listX(): Int = width / 2 - 130
    private fun listW(): Int = 260

    /**
     * Number of clickable rows on the active tab. With no models installed the
     * model tab shows help text instead of a list, so nothing is clickable.
     */
    private fun rowCount(): Int = when {
        tab == 0 -> micRows.size
        modelRows.isEmpty() -> 0
        else -> modelRows.size + 1 // +1 for the "自动选择" row
    }

    /** Top of the install-action rows, just below the model list. */
    private fun actionsY(): Int = listY + rowCount() * rowH + 10

    override fun init() {
        super.init()
        val cx = width / 2

        micRows.clear()
        micRows.add("默认麦克风")
        micRows.addAll(VoiceInputManager.getMicrophones())

        val savedMic = VoiceInputManager.getSelectedMicrophone()
        micSelected = if (savedMic == null) 0 else micRows.indexOf(savedMic).let { if (it < 0) 0 else it }

        bundled = ModelInstaller.listBundled()
        modelRows = VoiceInputManager.listModels()
        syncModelSelection()
        rebuildActions()

        // Tab switcher.
        addRenderableWidget(tabButton("麦克风", 0, cx - 110, 40))
        addRenderableWidget(tabButton("语音模型", 1, cx + 10, 40))

        if (tab == 1) {
            addRenderableWidget(
                Button.builder(
                    Component.literal("大模型[约1.2GB]").withStyle(ChatFormatting.AQUA)
                ) { openModelUrl(VoiceInputManager.LARGE_MODEL_URL) }
                    .bounds(downloadX, downloadLabelY + 20, downloadBtnW, downloadBtnH)
                    .build()
            )
            addRenderableWidget(
                Button.builder(
                    Component.literal("小模型[约42MB]").withStyle(ChatFormatting.AQUA)
                ) { openModelUrl(VoiceInputManager.SMALL_MODEL_URL) }
                    .bounds(downloadX, downloadLabelY + 20 + downloadBtnH + 4, downloadBtnW, downloadBtnH)
                    .build()
            )

            addRenderableWidget(
                Button.builder(
                    Component.literal("打开语音模型文件").withStyle(ChatFormatting.AQUA),
                    this::onOpenModelFolder
                )
                    .bounds(cx - 70, height - 90, 140, 20)
                    .build()
            )
        }

        addRenderableWidget(
            Button.builder(Component.literal("取消").withStyle(ChatFormatting.DARK_RED), this::onCancelPress)
                .bounds(cx - 30, height - 50, 60, 20)
                .build()
        )
    }

    /** Points [modelSelected] at the saved model, or at 自动选择 if it is gone. */
    private fun syncModelSelection() {
        val saved = VoiceInputManager.getSelectedModel()
        // +1 because row 0 is "自动选择".
        modelSelected = if (saved == null) 0
        else modelRows.indexOfFirst { it.id == saved }.let { if (it < 0) 0 else it + 1 }
    }

    /** One action per known model that is not installed yet. */
    private fun rebuildActions() {
        val list = mutableListOf<InstallAction>()
        for (source in ModelSources.ALL) {
            if (VoiceInputManager.isModelInstalled(source.name)) continue
            if (bundled.contains(source.name)) {
                list.add(InstallAction("释放内置：${source.label}") {
                    ModelInstaller.installBundled(source.name)
                })
            } else {
                list.add(InstallAction("下载：${source.label} · ${source.sizeText}") {
                    ModelInstaller.download(source)
                })
            }
        }
        actions = list
    }

    /**
     * Re-scans the model directory about once a second, so a model extracted
     * while the game is running shows up without reopening the screen — the
     * player is told to drop one in via the 打开语音模型文件 button, and comes
     * straight back to this screen expecting to see it. This also picks up
     * models as an install finishes.
     */
    override fun tick() {
        super.tick()
        if (++ticks % 20 != 0) return
        val found = VoiceInputManager.listModels()
        if (found != modelRows) {
            modelRows = found
            syncModelSelection()
            rebuildActions()
        }
    }

    private fun tabButton(label: String, index: Int, x: Int, y: Int): Button {
        val text = if (tab == index) {
            Component.literal("[$label]").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
        } else {
            Component.literal(label).withStyle(ChatFormatting.GRAY)
        }
        return Button.builder(text) { onTabPress(index) }
            .bounds(x, y, 100, 20)
            .build()
    }

    private fun onTabPress(index: Int) {
        if (tab == index) return
        tab = index
        rebuildWidgets()
    }

    private fun onOpenModelFolder(button: Button) {
        VoiceInputManager.openModelFolder()
    }

    /** Opens a model download page in the system browser. */
    private fun openModelUrl(url: String) {
        try {
            Util.getPlatform().openUri(URI.create(url))
        } catch (_: Throwable) {
            // Best-effort — nothing sensible to do if the OS refuses to open it.
        }
    }

    private fun onCancelPress(button: Button) {
        minecraft?.setScreen(parent)
    }

    private fun hitTest(mouseX: Int, mouseY: Int): Int {
        if (mouseX < listX() || mouseX > listX() + listW()) return -1
        val rel = mouseY - listY
        if (rel < 0) return -1
        val idx = rel / rowH
        return if (idx in 0 until rowCount()) idx else -1
    }

    /** Index of the install action under the cursor, or -1. */
    private fun hitTestAction(mouseX: Int, mouseY: Int): Int {
        if (tab != 1 || actions.isEmpty() || ModelInstaller.isBusy()) return -1
        if (mouseX < listX() || mouseX > listX() + listW()) return -1
        val rel = mouseY - actionsY()
        if (rel < 0) return -1
        val idx = rel / actionH
        return if (idx in actions.indices) idx else -1
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val handled = super.mouseClicked(mouseX, mouseY, button)
        if (button != 0) return handled

        val action = hitTestAction(mouseX.toInt(), mouseY.toInt())
        if (action != -1) {
            ModelInstaller.clearError()
            actions[action].run()
            return true
        }

        val idx = hitTest(mouseX.toInt(), mouseY.toInt())
        if (idx != -1) {
            if (tab == 0) {
                micSelected = idx
                VoiceInputManager.setSelectedMicrophone(if (idx == 0) null else micRows[idx])
            } else {
                modelSelected = idx
                // Row 0 is "自动选择"; the rest are shifted by one.
                VoiceInputManager.setSelectedModel(if (idx == 0) null else modelRows[idx - 1].id)
            }
            return true
        }
        return handled
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, delta)
        super.render(guiGraphics, mouseX, mouseY, delta)

        val cx = width / 2
        val white = 0xFFFFFFFF.toInt()
        val gray = 0xFFAAAAAA.toInt()

        guiGraphics.drawCenteredString(
            font,
            Component.literal("麦克风设置").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
            cx, 15, white
        )

        if (tab == 0) {
            renderMicTab(guiGraphics, cx, white, gray)
        } else {
            renderModelTab(guiGraphics, cx, mouseX, mouseY, white, gray)
        }
    }

    private fun renderMicTab(guiGraphics: GuiGraphics, cx: Int, white: Int, gray: Int) {
        guiGraphics.drawCenteredString(
            font,
            Component.literal("点击选择语音输入使用的麦克风（蓝色为当前选中）"),
            cx, 64, gray
        )

        if (micRows.isEmpty()) {
            guiGraphics.drawString(
                font,
                Component.literal("未检测到可用的麦克风").withStyle(ChatFormatting.RED),
                listX(), listY + 4, gray
            )
            return
        }
        for (i in micRows.indices) {
            drawRow(guiGraphics, i, micRows[i], i == micSelected, white)
        }
    }

    private fun renderModelTab(
        guiGraphics: GuiGraphics,
        cx: Int,
        mouseX: Int,
        mouseY: Int,
        white: Int,
        gray: Int,
    ) {
        guiGraphics.drawString(
            font,
            Component.literal("模型下载:").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
            downloadX, downloadLabelY, white
        )

        if (modelRows.isEmpty()) {
            guiGraphics.drawCenteredString(
                font,
                Component.literal("尚未安装语音模型").withStyle(ChatFormatting.RED),
                cx, 64, gray
            )
        } else {
            guiGraphics.drawCenteredString(
                font,
                Component.literal("点击选择语音识别使用的模型（蓝色为当前选中）"),
                cx, 64, gray
            )
            drawRow(guiGraphics, 0, "自动选择（第一个模型）", modelSelected == 0, white)
            for (i in modelRows.indices) {
                drawRow(guiGraphics, i + 1, modelRows[i].label, modelSelected == i + 1, white)
            }
        }

        val progress = ModelInstaller.getProgress()
        if (progress != null) {
            drawProgress(guiGraphics, cx, progress, actionsY(), white, gray)
        } else {
            drawActions(guiGraphics, mouseX, mouseY, white)
            val error = ModelInstaller.getLastError()
            if (error != null) {
                guiGraphics.drawCenteredString(
                    font,
                    Component.literal(error).withStyle(ChatFormatting.RED),
                    cx, actionsY() + actions.size * actionH + 4, gray
                )
            }
        }

        guiGraphics.drawCenteredString(
            font,
            Component.literal("模型目录: " + VoiceInputManager.getModelDir()),
            cx, height - 64, gray
        )
    }

    private fun drawActions(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, white: Int) {
        val hovered = hitTestAction(mouseX, mouseY)
        for (i in actions.indices) {
            val y = actionsY() + i * actionH
            val on = i == hovered
            guiGraphics.fill(listX(), y, listX() + listW(), y + actionH - 2, if (on) 0xFF3A6B3A.toInt() else 0xFF2B2B2B.toInt())
            guiGraphics.drawCenteredString(
                font,
                Component.literal(actions[i].label).withStyle(ChatFormatting.GREEN),
                width / 2, y + 4, white
            )
        }
    }

    private fun drawProgress(
        guiGraphics: GuiGraphics,
        cx: Int,
        progress: ModelInstaller.Progress,
        y: Int,
        white: Int,
        gray: Int,
    ) {
        guiGraphics.drawCenteredString(
            font,
            Component.literal("${progress.label} · ${progress.phase}").withStyle(ChatFormatting.YELLOW),
            cx, y, white
        )
        val barY = y + 13
        val w = listW()
        guiGraphics.fill(listX(), barY, listX() + w, barY + 8, 0xFF2B2B2B.toInt())
        val filled = (w * progress.fraction).toInt()
        if (filled > 0) {
            guiGraphics.fill(listX(), barY, listX() + filled, barY + 8, 0xFF4CAF50.toInt())
        }
        guiGraphics.drawCenteredString(
            font,
            Component.literal("${progress.sizeText}   ${(progress.fraction * 100).toInt()}%"),
            cx, barY + 12, gray
        )
    }

    /** Draws one list row, highlighted when [selected]. */
    private fun drawRow(guiGraphics: GuiGraphics, index: Int, label: String, selected: Boolean, white: Int) {
        val y = listY + index * rowH
        if (selected) {
            guiGraphics.fill(listX(), y, listX() + listW(), y + rowH - 2, 0x8040A0FF.toInt())
        }
        val color = if (selected) 0xFF55AAFF.toInt() else white
        guiGraphics.drawString(font, Component.literal(label), listX() + 4, y + 4, color)
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        guiGraphics.fill(0, 0, width, height, 0xC0101010.toInt())
    }
}
