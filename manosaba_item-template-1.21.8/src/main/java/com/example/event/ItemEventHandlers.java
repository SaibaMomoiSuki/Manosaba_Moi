// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.event;

import com.example.effect.ModEffects;
import com.example.item.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class ItemEventHandlers {
    private ItemEventHandlers() {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!level.isClientSide() && entity instanceof LivingEntity living) {
                var sleeping = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(ModEffects.SLEEPING);
                if (player.hasEffect(sleeping)) return InteractionResult.FAIL;
                if (player.getItemInHand(hand).is(ModItems.GOLDEN_HOE)) living.setRemainingFireTicks(200);
            }
            return InteractionResult.PASS;
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (damageTaken > 5.0F && source.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
                entity.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(ModEffects.BLEEDING),
                    600, 0, false, false, true), player);
            }
        });
    }
}
