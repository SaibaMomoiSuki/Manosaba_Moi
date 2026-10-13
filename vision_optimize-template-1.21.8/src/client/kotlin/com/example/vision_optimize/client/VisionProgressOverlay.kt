package com.example.vision_optimize.client

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft

/**
 * Renders the block vision charge progress bar.
 * Compression effect: bar shrinks from right to left as progress increases.
 * Visible while charging or showing completed state.
 */
object VisionProgressOverlay {

    fun register() {
        HudRenderCallback.EVENT.register { drawContext, _ ->
            val client = Minecraft.getInstance()
            val w = client.window.guiScaledWidth
            val h = client.window.guiScaledHeight
            renderBar(drawContext, client, w, h)
        }
    }

    private fun renderBar(
        g: net.minecraft.client.gui.GuiGraphics,
        client: Minecraft,
        w: Int, h: Int
    ) {
        if (!ClientVisionState.blockVisionCharging && !ClientVisionState.playerVisionCharging) return

        val bw = 120; val bh = 14
        val x = (w - bw) / 2
        val y = h / 2 + 24

        if (ClientVisionState.blockVisionCharging) {
            val progress = ClientVisionState.blockVisionProgress.coerceIn(0f, 1f)
            // Background
            g.fill(x, y, x + bw, y + bh, 0xCC333333.toInt())
            // Progress fill (shrinks from right = compression)
            val fillW = (bw * (1f - progress)).toInt()
            val color = when { progress < 0.33f -> 0xFF00CC00.toInt(); progress < 0.66f -> 0xFFCCCC00.toInt(); else -> 0xFFCC4400.toInt() }
            g.fill(x, y, x + fillW, y + bh, color)
            // Border
            g.renderOutline(x, y, bw, bh, 0xFFFFFFFF.toInt())
            // Percentage
            g.drawCenteredString(client.font, "${(progress * 100).toInt()}%", x + bw / 2, y + bh + 3, 0xFFFFFFFF.toInt())
        }

        if (ClientVisionState.playerVisionCharging) {
            val pprogress = ClientVisionState.playerVisionProgress.coerceIn(0f, 1f)
            // Background
            g.fill(x, y, x + bw, y + bh, 0xCC333333.toInt())
            // Progress fill (shrinks from right)
            val pfillW = (bw * (1f - pprogress)).toInt()
            val pcolor = when {
                pprogress < 0.33f -> 0xFF00CC00.toInt()
                pprogress < 0.66f -> 0xFFCCCC00.toInt()
                else -> 0xFFCC4400.toInt()
            }
            g.fill(x, y, x + pfillW, y + bh, pcolor)
            // Border
            g.renderOutline(x, y, bw, bh, 0xFFFFFFFF.toInt())
            // Percentage + target name
            val targetName = ClientVisionState.playerVisionTargetName ?: ""
            g.drawCenteredString(client.font, "人物幻视 $targetName ${(pprogress * 100).toInt()}%", x + bw / 2, y + bh + 3, 0xFFFFFFFF.toInt())
        }
    }
}
