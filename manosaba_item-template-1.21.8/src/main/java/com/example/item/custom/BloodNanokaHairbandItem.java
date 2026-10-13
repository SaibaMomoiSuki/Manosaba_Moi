// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item.custom;

import com.example.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class BloodNanokaHairbandItem extends HairRibbonItem {
    public BloodNanokaHairbandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionResult result = super.use(level, player, hand);
        if (result == InteractionResult.SUCCESS && !level.isClientSide()) {
            player.displayClientMessage(Component.literal("好像有点脏了...").withStyle(ChatFormatting.YELLOW), false);
        }
        return result;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof Player player) || !player.isInWater()) return;
        ItemStack cleaned = stack.transmuteCopy(ModItems.NANOKA_HAIRBAND);
        if (slot != null) {
            player.setItemSlot(slot, cleaned);
            return;
        }
        int inventorySlot = player.getInventory().findSlotMatchingItem(stack);
        if (inventorySlot >= 0) player.getInventory().setItem(inventorySlot, cleaned);
    }
}
