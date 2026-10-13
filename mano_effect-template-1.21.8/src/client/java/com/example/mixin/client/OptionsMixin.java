// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin.client;

import com.example.Mano_effectClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

/**
 * Controls blur radius + mouse sensitivity dynamically via getMenuBackgroundBlurriness().
 * Called every frame from GameRenderer.render() → GlobalSettingsUniform.update().
 */
@Mixin(Options.class)
public class OptionsMixin {
    @Unique
    private static long dizzinessGainedTime = 0;
    @Unique
    private static long dizzinessLostTime = 0;
    @Unique
    private static boolean wasActive = false;
    @Unique
    private static final Random RANDOM = new Random();
    @Unique
    private static final long FADE_IN_MS = 1000;
    @Unique
    private static final long FADE_OUT_MS = 2000;
    @Unique
    private static final float MIN_BLUR = 3f;
    @Unique
    private static final float MAX_BLUR = 10f;
    @Unique
    private static float sensitivityTarget = 0f;
    @Unique
    private static long lastSensitivityChange = 0;
    @Unique
    private static final long SENSITIVITY_INTERVAL_MS = 250; // 5 ticks = 250ms
    @Unique
    private static float fadeOutStartRadius = 0f;
    @Unique
    private static float fadeOutStartSensitivity = 0f;

    @Inject(method = "getMenuBackgroundBlurriness", at = @At("RETURN"), cancellable = true)
    private void modifyBlurriness(CallbackInfoReturnable<Integer> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var effectKey = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mano_effect", "dizziness");
        var holder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(effectKey);
        boolean hasDizziness = holder.isPresent() && mc.player.hasEffect(holder.get());

        // If fracture blur is active, return its blur radius directly
        if (Mano_effectClient.fractureBlurActive) {
            cir.setReturnValue(Math.round(Mano_effectClient.smoothBlurRadius));
            return;
        }

        long now = System.currentTimeMillis();

        if (hasDizziness) {
            if (!wasActive) {
                dizzinessGainedTime = now;
                dizzinessLostTime = 0;
                Mano_effectClient.targetBlurRadius = MIN_BLUR + RANDOM.nextFloat() * (MAX_BLUR - MIN_BLUR);
                Mano_effectClient.blurChangeTimer = 0;
                Mano_effectClient.smoothBlurRadius = 0f;
                Mano_effectClient.blurActive = true;
                sensitivityTarget = 0f;
                lastSensitivityChange = now;
            }

            long elapsed = now - dizzinessGainedTime;
            if (elapsed < FADE_IN_MS) {
                float t = (float) elapsed / FADE_IN_MS;
                float eased = t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
                Mano_effectClient.smoothBlurRadius = eased * Mano_effectClient.targetBlurRadius;
                // Sensitivity ramps up with fade-in
                Mano_effectClient.sensitivityNoise = eased * sensitivityTarget;
            } else {
                // Blur random walk
                Mano_effectClient.blurChangeTimer++;
                if (Mano_effectClient.blurChangeTimer >= 40) {
                    Mano_effectClient.blurChangeTimer = 0;
                    Mano_effectClient.targetBlurRadius = MIN_BLUR + RANDOM.nextFloat() * (MAX_BLUR - MIN_BLUR);
                }
                Mano_effectClient.smoothBlurRadius = Mth.lerp(
                    0.05f, Mano_effectClient.smoothBlurRadius, Mano_effectClient.targetBlurRadius
                );

                // Sensitivity random walk every 5 ticks (250ms)
                if (now - lastSensitivityChange >= SENSITIVITY_INTERVAL_MS) {
                    lastSensitivityChange = now;
                    sensitivityTarget = RANDOM.nextFloat() * 2f - 1f; // -1 to 1
                }
                Mano_effectClient.sensitivityNoise = Mth.lerp(
                    0.06f, Mano_effectClient.sensitivityNoise, sensitivityTarget
                );
            }

            wasActive = true;
            cir.setReturnValue(Math.round(Mano_effectClient.smoothBlurRadius));
            return;
        }

        if (wasActive && dizzinessLostTime == 0) {
            dizzinessLostTime = now;
            dizzinessGainedTime = 0;
            fadeOutStartRadius = Mano_effectClient.smoothBlurRadius;
            fadeOutStartSensitivity = Mano_effectClient.sensitivityNoise;
        }

        // Smooth fade-out
        if (!hasDizziness && dizzinessLostTime > 0) {
            long elapsed = now - dizzinessLostTime;
            if (elapsed < FADE_OUT_MS) {
                float t = (float) elapsed / FADE_OUT_MS;
                float eased = 1f - (float) Math.pow(1f - t, 3);
                Mano_effectClient.smoothBlurRadius = fadeOutStartRadius * (1f - eased);
                Mano_effectClient.sensitivityNoise = fadeOutStartSensitivity * (1f - eased);
                cir.setReturnValue(Math.round(Mano_effectClient.smoothBlurRadius));
                return;
            } else {
                Mano_effectClient.smoothBlurRadius = 0f;
                Mano_effectClient.sensitivityNoise = 0f;
                Mano_effectClient.blurActive = false;
                dizzinessLostTime = 0;
                wasActive = false;
            }
        }

        if (hasDizziness) wasActive = true;
    }
}
