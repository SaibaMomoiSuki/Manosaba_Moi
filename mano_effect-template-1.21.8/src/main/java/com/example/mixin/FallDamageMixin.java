// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin;

import com.example.Mano_effect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Players take no fall damage (no HP loss) below 12 blocks; the vanilla fall
 * damage is cancelled so the player never loses HP from a fall in that range.
 *
 * When /mano_effect falldamage is false, falls instead apply custom injury
 * effects (still no HP loss below 12 blocks):
 *
 * Without splinted — Fall height → Effect:
 *   4-6   blocks → twisted_ankle (6.5 min)
 *   6-9   blocks → dislocated_bone (10 min)
 *   9-12  blocks → fracture (15 min)
 *   12+   blocks → instant death
 *
 * With splinted — Fall height → Effect:
 *   6-12  blocks → seriously_injured (5 min), or upgrade existing 重创 level
 *   12+   blocks → instant death
 *
 * 重创 upgrade: If player already has 重创 when falling 6-12 blocks,
 * the duration resets and the amplifier increases by 1 (I→II→III...).
 * Each level reduces movement speed by 10% (I=90%, II=80%, III=70%...).
 *
 * Effects are mutually exclusive; applying one removes the others.
 * Players with slow_falling or levitation skip this logic.
 */
@Mixin(LivingEntity.class)
public abstract class FallDamageMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("mano_effect:falldamage");

    // Thresholds are raw fall distances (before vanilla -3 reduction)
    @Unique
    private static final double TWISTED_ANKLE_MIN = 4.0;
    @Unique
    private static final double DISLOCATED_BONE_MIN = 6.0;
    @Unique
    private static final double FRACTURE_MIN = 9.0;
    @Unique
    private static final double DEATH_MIN = 12.0;
    @Unique
    private static final double DIZZINESS_MIN = 8.0;

    // Effect durations in ticks
    @Unique
    private static final int TWISTED_ANKLE_DURATION = 7800;   // 6.5 min = 390s * 20
    @Unique
    private static final int DISLOCATED_BONE_DURATION = 12000; // 10 min = 600s * 20
    @Unique
    private static final int FRACTURE_DURATION = 18000;        // 15 min = 900s * 20
    @Unique
    private static final int SERIOUSLY_INJURED_DURATION = 6000; // 5 min = 300s * 20
    @Unique
    private static final int DIZZINESS_DURATION = 35 * 20;

    /**
     * Inject into causeFallDamage to make players immune to fall damage.
     */
    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void onCauseFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;

        // Only apply to players
        if (!(entity instanceof Player player)) return;

        // Skip if player has slow_falling, levitation, creative/spectator, or elytra
        if (hasExternalProtection(player)) return;

        double rawFallDistance = entity.fallDistance;
        if (rawFallDistance >= DIZZINESS_MIN) {
            applyDizziness(player);
        }

        // Players never take fall damage (HP loss) at any height.
        // When fall damage is enabled, just cancel the vanilla damage without
        // applying any injury effects.
        if (Mano_effect.getFallDamageEnabled()) {
            cir.setReturnValue(false);
            return;
        }

        boolean isSplinted = hasSplinted(player);

        if (isSplinted) {
            // ---- Splinted fall logic ----
            // Fall < 6 blocks: nothing (skip)
            if (rawFallDistance < DISLOCATED_BONE_MIN) {
                cir.setReturnValue(false);
                return;
            }

            // 12+ blocks: instant death
            if (rawFallDistance >= DEATH_MIN) {
                LOGGER.info("{} fell {} blocks (splinted) → instant death", player.getName().getString(), rawFallDistance);
                player.hurt(damageSource, Float.MAX_VALUE);
                cir.setReturnValue(true);
                return;
            }

            // 6-12 blocks: seriously_injured
            ResourceLocation siKey = ResourceLocation.fromNamespaceAndPath("mano_effect", "manosaba_seriously_injured");
            Optional<Holder.Reference<MobEffect>> siHolder = BuiltInRegistries.MOB_EFFECT.get(siKey);

            if (siHolder.isEmpty()) {
                LOGGER.warn("Effect manosaba_seriously_injured not found in registry!");
                cir.setReturnValue(false);
                return;
            }

            Holder<MobEffect> siEffect = siHolder.get();

            // Check if player already has 重创 — upgrade it
            if (player.hasEffect(siEffect)) {
                MobEffectInstance existingSi = player.getEffect(siEffect);
                int currentAmp = existingSi != null ? existingSi.getAmplifier() : 0;
                int newAmp = currentAmp + 1;

                // Remove all existing injury effects including old 重创 (mutual exclusivity)
                removeAllInjuries(player);

                // Apply upgraded 重创 with reset duration
                MobEffectInstance upgraded = new MobEffectInstance(
                    siEffect,
                    SERIOUSLY_INJURED_DURATION,
                    newAmp,
                    false,
                    false,
                    true
                );
                player.addEffect(upgraded);

                LOGGER.info("{} fell {} blocks (splinted, had 重创 Lv.{}) → upgraded to 重创 Lv.{} for {} ticks",
                    player.getName().getString(), rawFallDistance, currentAmp + 1, newAmp + 1, SERIOUSLY_INJURED_DURATION);

                // Cancel vanilla fall damage
                cir.setReturnValue(false);
                return;
            }

            // No existing 重创 — apply fresh Lv.I
            LOGGER.info("{} fell {} blocks (splinted) → applying seriously_injured for {} ticks",
                player.getName().getString(), rawFallDistance, SERIOUSLY_INJURED_DURATION);

            // Remove all existing injury effects (mutual exclusivity)
            removeAllInjuries(player);

            MobEffectInstance instance = new MobEffectInstance(
                siEffect,
                SERIOUSLY_INJURED_DURATION,
                0,
                false,
                false,
                true
            );
            player.addEffect(instance);

            // Cancel vanilla fall damage
            cir.setReturnValue(false);
            return;
        }

        // ---- Non-splinted fall logic ----
        // Only trigger for falls >= 4 blocks
        if (rawFallDistance < TWISTED_ANKLE_MIN) {
            cir.setReturnValue(false);
            return;
        }

        // 12+ blocks: instant death
        if (rawFallDistance >= DEATH_MIN) {
            LOGGER.info("{} fell {} blocks → instant death", player.getName().getString(), rawFallDistance);
            // Apply massive damage to kill (respects totems)
            player.hurt(damageSource, Float.MAX_VALUE);
            cir.setReturnValue(true);
            return;
        }

        // Determine which effect to apply
        String effectId;
        int duration;

        if (rawFallDistance >= FRACTURE_MIN) {
            effectId = "fracture";
            duration = FRACTURE_DURATION;
        } else if (rawFallDistance >= DISLOCATED_BONE_MIN) {
            effectId = "dislocated_bone";
            duration = DISLOCATED_BONE_DURATION;
        } else {
            effectId = "twisted_ankle";
            duration = TWISTED_ANKLE_DURATION;
        }

        LOGGER.info("{} fell {} blocks → applying {} for {} ticks",
            player.getName().getString(), rawFallDistance, effectId, duration);

        // Apply the effect
        ResourceLocation effectKey = ResourceLocation.fromNamespaceAndPath("mano_effect", effectId);
        Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(effectKey);

        if (holder.isPresent()) {
            // Remove all existing injury effects (mutual exclusivity)
            removeAllInjuries(player);

            MobEffectInstance instance = new MobEffectInstance(
                holder.get(),
                duration,
                0,
                false,
                false,
                true
            );
            player.addEffect(instance);

            // Cancel vanilla fall damage
            cir.setReturnValue(false);
        } else {
            LOGGER.warn("Effect {} not found in registry!", effectKey);
            cir.setReturnValue(false);
        }
    }

    @Unique
    private static boolean hasSplinted(Player player) {
        Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("mano_effect", "splinted")
        );
        return holder.isPresent() && player.hasEffect(holder.get());
    }

    @Unique
    private static void applyDizziness(Player player) {
        Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("mano_effect", "dizziness")
        );
        holder.ifPresent(effect -> player.addEffect(new MobEffectInstance(
            effect,
            DIZZINESS_DURATION,
            0,
            false,
            false,
            true
        )));
    }

    @Unique
    private static boolean hasExternalProtection(Player player) {
        // Check for slow falling or levitation
        Optional<Holder.Reference<MobEffect>> slowFalling = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.withDefaultNamespace("slow_falling")
        );
        Optional<Holder.Reference<MobEffect>> levitation = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.withDefaultNamespace("levitation")
        );

        if (slowFalling.isPresent() && player.hasEffect(slowFalling.get())) return true;
        if (levitation.isPresent() && player.hasEffect(levitation.get())) return true;

        // Also skip if creative/spectator
        if (player.isCreative() || player.isSpectator()) return true;

        // Skip if elytra flying
        if (player.isFallFlying()) return true;

        return false;
    }

    @Unique
    private static void removeAllInjuries(Player player) {
        String[] injuries = {"dislocated_bone", "fracture", "twisted_ankle", "manosaba_seriously_injured"};
        for (String id : injuries) {
            Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(
                ResourceLocation.fromNamespaceAndPath("mano_effect", id)
            );
            if (holder.isPresent()) {
                player.removeEffect(holder.get());
            }
        }
    }
}
