// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item.custom;

import com.example.event.ModPlayerState;
import com.example.item.ModItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SleepingPillsItem extends Item {
    public SleepingPillsItem(Properties properties) { super(properties); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player && !level.isClientSide()) {
            ModPlayerState.sleepingPills.put(player.getUUID(),
                new ModPlayerState.SleepingPillsState(level.getGameTime(), false, false, false));
            if (!player.isCreative()) return new ItemStack(ModItems.EMPTY_GLASS_BOTTLE);
        }
        return stack;
    }
}
