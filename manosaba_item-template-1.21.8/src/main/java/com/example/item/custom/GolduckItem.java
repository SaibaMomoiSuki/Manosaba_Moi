// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item.custom;

import com.example.event.ModPlayerState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class GolduckItem extends Item {
    public GolduckItem(Properties properties) { super(properties); }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player && !level.isClientSide()) {
            ModPlayerState.golduck.put(player.getUUID(),
                new ModPlayerState.GolduckState(level.getGameTime()));
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
