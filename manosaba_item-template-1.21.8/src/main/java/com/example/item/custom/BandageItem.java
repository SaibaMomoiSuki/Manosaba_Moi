// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item.custom;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

public class BandageItem extends Item {
    public BandageItem(Properties properties) { super(properties); }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 40; }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.BOW; }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.getHealth() < player.getMaxHealth() || hasBleedingEffect(player)) {
            player.startUsingItem(hand);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof Player player) {
            boolean consumed = false;
            if (player.getHealth() < player.getMaxHealth()) {
                player.heal(5.0F);
                consumed = true;
            }
            var bleeding = BuiltInRegistries.MOB_EFFECT.get(
                ResourceLocation.fromNamespaceAndPath("manosaba_item", "bleeding"));
            if (bleeding.isPresent() && player.hasEffect(bleeding.get())) {
                player.removeEffect(bleeding.get());
                consumed = true;
            }
            if (consumed && !player.isCreative()) stack.shrink(1);
        }
        return stack;
    }

    private boolean hasBleedingEffect(Player player) {
        var bleeding = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("manosaba_item", "bleeding"));
        return bleeding.isPresent() && player.hasEffect(bleeding.get());
    }
}
