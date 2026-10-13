// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin.client;

import com.example.event.BottleStruggleClientHandler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class BottleStruggleHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics guiGraphics, DeltaTracker tickDelta, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        boolean hasState = BottleStruggleClientHandler.INSTANCE.getAttackerId() != null
            || BottleStruggleClientHandler.INSTANCE.getVictimId() != null;
        if (!hasState) return;

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        float progress = BottleStruggleClientHandler.INSTANCE.getProgress();
        int attPresses = BottleStruggleClientHandler.INSTANCE.getAttackerPresses();
        int vicPresses = BottleStruggleClientHandler.INSTANCE.getVictimPresses();
        boolean ended = BottleStruggleClientHandler.INSTANCE.getStruggleEnded();
        boolean attWon = BottleStruggleClientHandler.INSTANCE.getAttackerWon();
        boolean damagePhase = BottleStruggleClientHandler.INSTANCE.getReachedThreshold();

        int barWidth = 240;
        int barHeight = 10;
        int barX = (screenWidth - barWidth) / 2;
        int barY = screenHeight / 2 + 35;

        // === Background panel ===
        int panelPad = 12;
        int panelTop = barY - panelPad - 16;
        guiGraphics.fill(
            barX - panelPad, panelTop,
            barX + barWidth + panelPad, barY + barHeight + panelPad,
            0xCC000000
        );

        // === Title ===
        String title;
        int titleColor;
        if (ended) {
            title = attWon ? "对方被你刺伤了" : "进攻被抵抗了";
            titleColor = attWon ? 0xFFFF4444 : 0xFFCCCC00;
        } else if (damagePhase) {
            title = "刺伤中...";
            titleColor = 0xFFFF2222;
        } else {
            title = "厮打中...";
            titleColor = 0xFFFFAA00;
        }
        int titleW = client.font.width(title);
        guiGraphics.drawString(client.font, title,
            barX + (barWidth - titleW) / 2, barY - panelPad - 12, titleColor, false);

        // === Progress bar border (pulses red in damage phase) ===
        int borderColor = damagePhase
            ? ((System.currentTimeMillis() / 200 % 2 == 0) ? 0xFFFF4444 : 0xFFAA2222)
            : 0xFF888888;
        guiGraphics.fill(barX - 2, barY - 2, barX + barWidth + 2, barY + barHeight + 2, borderColor);

        // === Progress bar background ===
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);

        // === Gradient fill ===
        int fillWidth = (int) (progress * barWidth);
        if (fillWidth > 0) {
            int thresholdX = barX + (int) (barWidth * 0.7f);
            for (int i = 0; i < fillWidth; i++) {
                float t = (float) i / barWidth;
                int r, g, b;
                if (i >= thresholdX - barX) {
                    // Past 70%: intense red (damage zone)
                    float dt = (float) (i - (thresholdX - barX)) / (barWidth - (thresholdX - barX));
                    r = (int) (220 + 35 * dt);
                    g = (int) (20 * (1 - dt));
                    b = (int) (10 * (1 - dt));
                } else {
                    // Before 70%: lighter red gradient
                    r = (int) (180 + 75 * t);
                    g = (int) (50 * (1 - t));
                    b = (int) (50 * (1 - t));
                }
                int color = 0xFF000000 | (r << 16) | (g << 8) | b;
                guiGraphics.fill(barX + i, barY, barX + i + 1, barY + barHeight, color);
            }
        }

        // === Center line ===
        int centerX = barX + barWidth / 2;
        guiGraphics.fill(centerX - 1, barY - 2, centerX + 1, barY + barHeight + 2, 0xFFFFFFFF);

        // === 70% threshold line ===
        int thresholdX = barX + (int) (barWidth * 0.7f);
        int lineColor = damagePhase ? 0xFFFF0000 : 0xFFFFDD00;
        guiGraphics.fill(thresholdX - 1, barY - 4, thresholdX + 1, barY + barHeight + 4, lineColor);

        // === Progress percentage ===
        int pct = (int) (progress * 100);
        String pctText = pct + "%";
        int pctColor;
        if (damagePhase) {
            pctColor = (int) (System.currentTimeMillis() / 300 % 2 == 0 ? 0xFFFF3333 : 0xFFFF8888);
        } else if (progress >= 0.7f) {
            pctColor = 0xFFFF3333;
        } else {
            pctColor = 0xFFFFFFFF;
        }
        int pctW = client.font.width(pctText);
        guiGraphics.drawString(client.font, pctText,
            centerX - pctW / 2, barY + 1, pctColor, false);

        // === Press counts ===
        String attCount = String.valueOf(attPresses);
        guiGraphics.drawString(client.font, attCount,
            barX - panelPad, barY + barHeight + 4, 0xFFCC4444, false);
        String vicCount = String.valueOf(vicPresses);
        guiGraphics.drawString(client.font, vicCount,
            barX + barWidth - client.font.width(vicCount) + panelPad, barY + barHeight + 4, 0xFF4466CC, false);

        // === Instruction ===
        if (!ended) {
            String instruction = damagePhase ? "得赶紧挣扎...(受伤中)" : "得赶紧挣扎...(方向键)";
            int instrColor = (int) (System.currentTimeMillis() / 400 % 2 == 0 ? 0xFFFFAA00 : 0xFFFF6600);
            int instrW = client.font.width(instruction);
            guiGraphics.drawString(client.font, instruction,
                centerX - instrW / 2, barY - panelPad - 28, instrColor, false);
        }

        // === End result ===
        if (ended) {
            String resultText;
            int resultColor;
            boolean iAmAttacker = client.player.getUUID().equals(
                BottleStruggleClientHandler.INSTANCE.getAttackerId());
            if (attWon) {
                resultText = iAmAttacker ? "你刺伤了他" : "你被刺伤了...";
                resultColor = iAmAttacker ? 0xFF44FF44 : 0xFFFF4444;
            } else {
                resultText = iAmAttacker ? "他抵抗了你的攻击..." : "你抵抗了攻击！";
                resultColor = iAmAttacker ? 0xFFFF4444 : 0xFF44FF44;
            }
            int textW = client.font.width(resultText);
            guiGraphics.drawString(client.font, resultText,
                (screenWidth - textW) / 2, barY - panelPad - 44, resultColor, false);
        }
    }
}
