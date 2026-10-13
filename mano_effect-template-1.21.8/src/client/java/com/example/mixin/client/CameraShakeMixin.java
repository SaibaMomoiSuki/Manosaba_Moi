// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Slow, gentle camera sway (not jitter) when the player has dizziness.
 * Creates a floating/drifting sensation like Farmer's Delight "tipsy" effect.
 */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {
    @Unique
    private static float swayTime = 0.0f;
    @Unique
    private static float currentIntensity = 0.0f;
    @Unique
    private static long dizzinessStartedAt = 0L;

    @Inject(method = "setup", at = @At("TAIL"))
    private void onCameraSetup(CallbackInfo ci) {
        Camera camera = (Camera) (Object) this;
        Entity entity = camera.getEntity();

        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        ResourceLocation effectKey = ResourceLocation.fromNamespaceAndPath("mano_effect", "dizziness");
        Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(effectKey);

        if (holder.isPresent() && living.hasEffect(holder.get())) {
            int amplifier = living.getEffect(holder.get()).getAmplifier();
            if (dizzinessStartedAt == 0L) {
                dizzinessStartedAt = System.currentTimeMillis();
            }

            float fadeIn = Mth.clamp((System.currentTimeMillis() - dizzinessStartedAt) / 1800.0f, 0.0f, 1.0f);
            fadeIn = fadeIn * fadeIn * (3.0f - 2.0f * fadeIn);
            float targetIntensity = (0.16f + amplifier * 0.03f) * fadeIn;

            currentIntensity = Mth.lerp(0.08f, currentIntensity, targetIntensity);
            swayTime += 0.025f;

            double swayX = (Mth.sin(swayTime * 0.75f) + Mth.sin(swayTime * 1.9f) * 0.25f)
                * currentIntensity * 0.08;
            double swayY = (Mth.cos(swayTime * 0.62f) + Mth.sin(swayTime * 1.4f) * 0.2f)
                * currentIntensity * 0.055;
            double swayZ = Mth.sin(swayTime * 0.5f + 1.0f) * currentIntensity * 0.045;

            Vec3 currentPos = camera.getPosition();
            ((CameraAccessor) camera).invokeSetPosition(
                currentPos.x + swayX,
                currentPos.y + swayY,
                currentPos.z + swayZ
            );

            float yawSway = (Mth.sin(swayTime * 0.7f) + Mth.sin(swayTime * 1.65f) * 0.3f)
                * currentIntensity * 1.8f;
            float pitchSway = (Mth.cos(swayTime * 0.6f) + Mth.sin(swayTime * 1.3f) * 0.25f)
                * currentIntensity * 1.35f;

            ((CameraAccessor) camera).invokeSetRotation(
                camera.getYRot() + yawSway,
                camera.getXRot() + pitchSway
            );
        } else {
            currentIntensity = Mth.lerp(0.03f, currentIntensity, 0.0f);
            swayTime = 0.0f;
            dizzinessStartedAt = 0L;
        }
    }
}
