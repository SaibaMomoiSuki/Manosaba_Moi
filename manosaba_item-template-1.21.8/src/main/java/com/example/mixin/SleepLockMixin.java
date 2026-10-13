// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-side mixin to lock the player in SLEEPING pose
 * and freeze movement when under sleeping pills effect.
 * Uses the custom "sleeping" mob effect as the signal.
 */
@Mixin(Player.class)
public class SleepLockMixin {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void onAiStep(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide) return;

        var sleepingHolder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("manosaba_item", "sleeping")
        );
        if (sleepingHolder.isPresent() && self.hasEffect(sleepingHolder.get())) {
            // Force SLEEPING pose before vanilla checks
            self.setPose(Pose.SLEEPING);
            // Freeze all movement server-side
            self.xxa = 0f;
            self.zza = 0f;
            self.setDeltaMovement(0.0, self.getDeltaMovement().y() <= 0 ? self.getDeltaMovement().y() : 0.0, 0.0);
            self.setSprinting(false);
        }
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void onAiStepTail(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide) return;

        var sleepingHolder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("manosaba_item", "sleeping")
        );
        if (sleepingHolder.isPresent() && self.hasEffect(sleepingHolder.get())) {
            // Re-apply SLEEPING pose in case vanilla code reset it
            self.setPose(Pose.SLEEPING);
        }
    }
}
