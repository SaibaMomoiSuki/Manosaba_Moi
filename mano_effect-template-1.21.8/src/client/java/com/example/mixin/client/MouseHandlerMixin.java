// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin.client;

import com.example.Mano_effectClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Randomly fluctuates mouse sensitivity within ±0.1 of the player's setting
 * while dizziness is active. Modifies accumulated mouse delta at the start
 * of turnPlayer() to simulate sensitivity changes without touching the setting.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Shadow
    private double accumulatedDX;
    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void modifyMouseSensitivity(CallbackInfo ci) {
        if (!Mano_effectClient.blurActive) return;
        if (Mano_effectClient.sensitivityNoise <= 0f) return;

        // sensitivityNoise ranges -1 to 1; maps to ±0.1 sensitivity change
        // factor ≈ 1.0 ± ~0.12 (simulates ±0.1 sensitivity shift)
        double factor = 1.0 + Mano_effectClient.sensitivityNoise * 0.12;
        accumulatedDX *= factor;
        accumulatedDY *= factor;
    }
}
