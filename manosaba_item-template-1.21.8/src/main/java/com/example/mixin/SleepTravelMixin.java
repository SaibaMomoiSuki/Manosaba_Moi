// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zeroes xxa/zza BEFORE LivingEntity.aiStep() → travel() processes them.
 * Injecting at the INVOKE of super.aiStep() from Player.aiStep() means
 * input has already been processed but travel hasn't run yet.
 */
@Mixin(Player.class)
public class SleepTravelMixin {

    @Inject(
        method = "aiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;aiStep()V"
        )
    )
    private void beforeSuperAiStep(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        var sleepingHolder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("manosaba_item", "sleeping")
        );
        if (sleepingHolder.isPresent() && self.hasEffect(sleepingHolder.get())) {
            // Zero movement BEFORE LivingEntity.aiStep() processes travel()
            self.xxa = 0f;
            self.zza = 0f;
            self.setDeltaMovement(0.0, self.getDeltaMovement().y() <= 0 ? self.getDeltaMovement().y() : 0.0, 0.0);
        }
    }
}
