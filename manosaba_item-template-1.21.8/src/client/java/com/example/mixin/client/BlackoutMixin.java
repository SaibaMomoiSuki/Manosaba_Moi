// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class BlackoutMixin {

    private static float currentFade = 0f;
    private static final float FADE_SPEED = 0.025f;

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics guiGraphics, DeltaTracker tickDelta, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        var sleepingHolder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("manosaba_item", "sleeping")
        );

        var sleeping = sleepingHolder.map(client.player::getEffect).orElse(null);
        boolean shouldDarken = sleeping != null;

        if (shouldDarken) {
            // Fade in instead of jumping directly to opaque black.
            currentFade = Math.min(1.0f, currentFade + 0.025f);
        } else {
            currentFade = Math.max(0.0f, currentFade - FADE_SPEED);
        }

        if (currentFade > 0.001f) {
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            guiGraphics.fill(0, 0, screenWidth, screenHeight, 0xFF000000);
        }
    }
}
