// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * A weapon hit from a player that deals more than 2.5 HP while aimed at
 * another player's head applies 40 seconds of dizziness.
 *
 * Injects into LivingEntity.actuallyHurt() — detects headshot by comparing
 * the attacker's look direction to the vector pointing at the victim's head.
 * Uses getEyePosition() to correctly account for sneaking/swimming poses.
 */
@Mixin(LivingEntity.class)
public abstract class HeadshotMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("mano_effect:headshot");

    @Unique
    private static final double HEAD_HITBOX_EXPANSION = 0.18;

    @Unique
    private static final float MIN_HEADSHOT_DAMAGE = 2.5f;

    @Unique
    private static final int DIZZINESS_DURATION = 40 * 20;

    /**
     * Inject into actuallyHurt — the final damage application.
     * Mojang name: actuallyHurt(ServerLevel, DamageSource, float)
     */
    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void onActuallyHurt(ServerLevel world, DamageSource source, float amount, CallbackInfo ci) {
        LivingEntity victim = (LivingEntity) (Object) this;
        if (!(victim instanceof Player)) return;

        Entity attackerEntity = source.getEntity();
        if (!(attackerEntity instanceof Player attacker)) return;

        Entity directEntity = source.getDirectEntity();
        boolean weaponDamage = directEntity != attacker || !attacker.getMainHandItem().isEmpty();
        if (amount <= MIN_HEADSHOT_DAMAGE || !weaponDamage) return;

        if (!isHeadshot(attacker, (Player) victim, directEntity)) return;

        LOGGER.info("HEADSHOT! {} -> {}, damage={}", attacker.getName().getString(),
            victim.getName().getString(), amount);

        Optional<Holder.Reference<MobEffect>> dizziness = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("mano_effect", "dizziness")
        );

        if (dizziness.isPresent()) {
            MobEffectInstance instance = new MobEffectInstance(
                dizziness.get(),
                DIZZINESS_DURATION,
                0,
                false,
                false,
                true
            );
            victim.addEffect(instance);
        } else {
            LOGGER.warn("Dizziness effect not found in registry!");
        }
    }

    @Unique
    private static boolean isHeadshot(Player attacker, Player victim, Entity directEntity) {
        AABB bodyBox = victim.getBoundingBox();
        double headBottom = victim.getEyeY() - Math.max(0.22, bodyBox.getYsize() * 0.16);
        AABB headBox = new AABB(
            bodyBox.minX,
            headBottom,
            bodyBox.minZ,
            bodyBox.maxX,
            bodyBox.maxY + 0.12,
            bodyBox.maxZ
        ).inflate(HEAD_HITBOX_EXPANSION, 0.08, HEAD_HITBOX_EXPANSION);

        if (directEntity != attacker) {
            Vec3 impactPosition = directEntity.position();
            return headBox.contains(impactPosition)
                || headBox.distanceToSqr(impactPosition) <= HEAD_HITBOX_EXPANSION * HEAD_HITBOX_EXPANSION;
        }

        Vec3 rayStart = attacker.getEyePosition();
        double rayLength = rayStart.distanceTo(victim.getEyePosition()) + 1.0;
        Vec3 rayEnd = rayStart.add(attacker.getViewVector(1.0f).scale(rayLength));
        return headBox.clip(rayStart, rayEnd).isPresent();
    }
}
