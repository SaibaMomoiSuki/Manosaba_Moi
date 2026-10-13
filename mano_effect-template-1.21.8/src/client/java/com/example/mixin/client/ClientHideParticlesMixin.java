// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin.client;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side safety net: forces isVisible() = false for mano_effect effects
 * so the client never renders ambient swirl particles for them.
 */
@Mixin(MobEffectInstance.class)
public class ClientHideParticlesMixin {

    @Unique
    private static boolean isManoEffect(MobEffectInstance self) {
        Holder<MobEffect> holder = self.getEffect();
        ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(holder.value());
        return key != null && "mano_effect".equals(key.getNamespace());
    }

    @Inject(method = "isVisible", at = @At("RETURN"), cancellable = true)
    private void hideOurParticles(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && isManoEffect((MobEffectInstance) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
