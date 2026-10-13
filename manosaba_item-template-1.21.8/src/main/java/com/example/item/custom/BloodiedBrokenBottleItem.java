// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item.custom;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BloodiedBrokenBottleItem extends Item {
    public static final CompanionAdapter Companion = new CompanionAdapter();
    private static final Map<UUID, Long> PICKUP_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SWITCH_COOLDOWNS = new ConcurrentHashMap<>();

    public BloodiedBrokenBottleItem(Properties properties) { super(properties); }

    private static void safeCut(Player player, float amount) {
        if (player.getHealth() > 1.0F)
            player.hurt(player.damageSources().generic(), Math.min(amount, player.getHealth() - 1.0F));
    }

    public static void onSwitchToEvent(Player player) {
        long now = player.level().getGameTime();
        Long last = SWITCH_COOLDOWNS.put(player.getUUID(), now);
        if (last != null && now - last < 10) return;
        if (player.getRandom().nextFloat() < 0.3F) safeCut(player, 1.0F);
    }

    public static void onDropEvent(Player player) {
        if (player.getRandom().nextFloat() < 0.4F) safeCut(player, 3.0F);
    }

    public static void onPickupEvent(Player player) {
        long now = player.level().getGameTime();
        Long last = PICKUP_COOLDOWNS.put(player.getUUID(), now);
        if (last != null && now - last < 10) return;
        if (player.getRandom().nextFloat() < 0.25F) safeCut(player, 2.0F);
    }

    public static final class CompanionAdapter {
        public void onSwitchToEvent(Player player) { BloodiedBrokenBottleItem.onSwitchToEvent(player); }
        public void onPickupEvent(Player player) { BloodiedBrokenBottleItem.onPickupEvent(player); }
        public void onDropEvent(Player player) { BloodiedBrokenBottleItem.onDropEvent(player); }
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return InteractionResult.FAIL;
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                              LivingEntity entity, InteractionHand hand) {
        return InteractionResult.FAIL;
    }
}
