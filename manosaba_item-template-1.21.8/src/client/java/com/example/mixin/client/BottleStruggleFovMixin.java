// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin.client;

import com.example.event.BottleStruggleClientHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Narrows the FOV during a bottle struggle to create a tense, focused feeling.
 */
@Mixin(GameRenderer.class)
public class BottleStruggleFovMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onGetFov(Camera camera, float partialTicks, boolean useFOVSetting,
                          CallbackInfoReturnable<Float> cir) {
        if (BottleStruggleClientHandler.INSTANCE.getAttackerId() != null
        || BottleStruggleClientHandler.INSTANCE.getVictimId() != null) {
            // Reduce FOV by ~35% for a claustrophobic effect
            float originalFov = cir.getReturnValue();
            cir.setReturnValue(originalFov * 0.65f);
        }
    }
}
