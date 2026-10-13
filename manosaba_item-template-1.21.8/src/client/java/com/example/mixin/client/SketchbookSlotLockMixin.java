// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin.client;

import com.example.item.custom.AnanBookItem;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Locks inventory slot switching when the player is holding an open sketchbook.
 * Shows a yellow warning message when the player attempts to switch items.
 */
@Mixin(LocalPlayer.class)
public class SketchbookSlotLockMixin {

    @Unique
    private int sketchbookLockedSlot = -1;
    @Unique
    private boolean sketchbookWasLocked = false;
    @Unique
    private boolean sketchbookInOffhand = false;
    @Unique
    private long lastWarnTime = 0;
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void onAiStep(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        int currentSlot = self.getInventory().getSelectedSlot();
        if (!sketchbookWasLocked) {
            if (AnanBookItem.isOpenSketchbook(self.getInventory().getItem(currentSlot))) {
                sketchbookLockedSlot = currentSlot;
                sketchbookWasLocked = true;
                sketchbookInOffhand = false;
            } else if (AnanBookItem.isOpenSketchbook(self.getOffhandItem())) {
                sketchbookLockedSlot = currentSlot;
                sketchbookWasLocked = true;
                sketchbookInOffhand = true;
            }
            return;
        }

        // Keep tracking the original slot even after the user scrolls away from it.
        boolean bookStillOpen = sketchbookInOffhand
            ? AnanBookItem.isOpenSketchbook(self.getOffhandItem())
            : AnanBookItem.isOpenSketchbook(self.getInventory().getItem(sketchbookLockedSlot));
        if (!bookStillOpen) {
            sketchbookWasLocked = false;
            sketchbookLockedSlot = -1;
            sketchbookInOffhand = false;
            return;
        }

        if (currentSlot != sketchbookLockedSlot) {
            self.getInventory().setSelectedSlot(sketchbookLockedSlot);
            long now = System.currentTimeMillis();
            if (now - lastWarnTime > 2000) {
                lastWarnTime = now;
                self.displayClientMessage(
                    Component.translatable("message.manosaba_item.sketchbook_locked").withStyle(ChatFormatting.YELLOW),
                    true
                );
            }
        }
    }
}
