// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item.custom;

import com.example.component.ModDataComponents;
import com.example.event.ModPlayerState;
import com.example.item.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PoisonItem extends Item {
    public PoisonItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack food = player.getItemInHand(otherHand);
        if (!food.isEmpty() && food.has(DataComponents.FOOD)) {
            if (!level.isClientSide()) {
                ItemStack poisoned = food.copyWithCount(1);
                poisoned.set(ModDataComponents.POISONED_COUNT, 1);
                poisoned.set(ModDataComponents.POISONED, Unit.INSTANCE);
                food.shrink(1);
                if (!player.isCreative()) {
                    player.getItemInHand(hand).shrink(1);
                    ItemStack bottle = new ItemStack(ModItems.EMPTY_GLASS_BOTTLE);
                    if (!player.getInventory().add(bottle)) player.drop(bottle, false);
                }
                if (!player.getInventory().add(poisoned)) player.drop(poisoned, false);
            }
            return InteractionResult.SUCCESS;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player && !level.isClientSide()) {
            ModPlayerState.poison.put(player.getUUID(),
                new ModPlayerState.PoisonState(level.getGameTime(), false, false, false));
            if (!player.isCreative()) return new ItemStack(ModItems.EMPTY_GLASS_BOTTLE);
        }
        return stack;
    }
}
