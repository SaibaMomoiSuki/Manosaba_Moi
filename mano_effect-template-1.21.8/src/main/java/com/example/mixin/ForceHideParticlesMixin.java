// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces all mano_effect status effects to never show particles.
 */
@Mixin(LivingEntity.class)
public abstract class ForceHideParticlesMixin {

    @Unique
    private static final String[] INJURY_IDS = {"dislocated_bone", "fracture", "twisted_ankle", "manosaba_seriously_injured"};

    @Unique
    private static boolean isOurEffect(MobEffectInstance instance) {
        if (instance == null) return false;
        Holder<MobEffect> holder = instance.getEffect();
        ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(holder.value());
        return key != null && "mano_effect".equals(key.getNamespace());
    }

    @Unique
    private static boolean isInjuryEffect(MobEffectInstance instance) {
        if (instance == null) return false;
        Holder<MobEffect> holder = instance.getEffect();
        ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(holder.value());
        if (key == null || !"mano_effect".equals(key.getNamespace())) return false;
        for (String id : INJURY_IDS) {
            if (id.equals(key.getPath())) return true;
        }
        return false;
    }

    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    private void hideParticles(MobEffectInstance instance, CallbackInfoReturnable<Boolean> cir) {
        if (instance == null) return;

        // Mutual exclusivity for injury effects: when adding one, remove all others
        if (isInjuryEffect(instance)) {
            removeAllOtherInjuries(instance);
        }

        if (isOurEffect(instance) && instance.isVisible()) {
            cir.cancel();
            MobEffectInstance hidden = new MobEffectInstance(
                instance.getEffect(),
                instance.getDuration(),
                instance.getAmplifier(),
                instance.isAmbient(),
                false,
                instance.showIcon()
            );
            cir.setReturnValue(callAddEffect(hidden));
        }
    }

    @Unique
    private void removeAllOtherInjuries(MobEffectInstance newEffect) {
        LivingEntity entity = (LivingEntity) (Object) this;
        ResourceLocation newKey = BuiltInRegistries.MOB_EFFECT.getKey(newEffect.getEffect().value());
        if (newKey == null) return;

        for (String id : INJURY_IDS) {
            if (id.equals(newKey.getPath())) continue; // don't remove the one being added
            java.util.Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(
                ResourceLocation.fromNamespaceAndPath("mano_effect", id)
            );
            if (holder.isPresent()) {
                entity.removeEffect(holder.get());
            }
        }
    }

    @Invoker("addEffect")
    abstract boolean callAddEffect(MobEffectInstance instance);
}
