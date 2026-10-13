// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zeroes accumulated mouse delta when under sleeping pills effect,
 * preventing the player from turning the camera.
 */
@Mixin(MouseHandler.class)
public class MouseLockMixin {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void onTurnPlayer(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        var sleepingHolder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("manosaba_item", "sleeping")
        );
        if (sleepingHolder.isPresent() && client.player.hasEffect(sleepingHolder.get())) {
            this.accumulatedDX = 0;
            this.accumulatedDY = 0;
        }
    }
}
