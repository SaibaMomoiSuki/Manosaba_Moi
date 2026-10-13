// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * When a player receives regeneration or instant_health, removes all injury effects.
 * This catches instant_health (which is applied and immediately removed by vanilla)
 * before it disappears from the effect list.
 */
@Mixin(LivingEntity.class)
public abstract class HealingRemovesInjuryMixin {

    @Unique
    private static final String[] INJURY_IDS = {
        "dislocated_bone", "fracture", "twisted_ankle", "manosaba_seriously_injured"
    };

    @Unique
    private static boolean isHealingEffect(MobEffectInstance instance) {
        if (instance == null) return false;
        Holder<MobEffect> holder = instance.getEffect();
        ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(holder.value());
        if (key == null) return false;
        String path = key.getPath();
        return "minecraft".equals(key.getNamespace()) &&
               ("regeneration".equals(path) || "instant_health".equals(path));
    }

    /**
     * Inject after addEffect is called. If the added effect is regeneration or instant_health,
     * remove all injury effects from the entity.
     */
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z", at = @At("RETURN"))
    private void onAddHealingEffect(MobEffectInstance instance, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return; // effect not actually applied
        if (!isHealingEffect(instance)) return;

        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof Player)) return;

        removeAllInjuries((Player) entity);
    }

    @Unique
    private static void removeAllInjuries(Player player) {
        for (String id : INJURY_IDS) {
            Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(
                ResourceLocation.fromNamespaceAndPath("mano_effect", id)
            );
            if (holder.isPresent()) {
                player.removeEffect(holder.get());
            }
        }
    }
}
