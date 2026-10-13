// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item.custom;

import com.example.event.ModPlayerState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ZhangXuefengItem extends Item {
    public ZhangXuefengItem(Properties properties) { super(properties); }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player && !level.isClientSide()) {
            var speed = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.withDefaultNamespace("speed"));
            speed.ifPresent(holder -> player.addEffect(new MobEffectInstance(holder, 600, 1)));
            ModPlayerState.zhangXuefeng.put(player.getUUID(),
                new ModPlayerState.ZhangXuefengState(true, level.getGameTime(), 0.0,
                    player.getX(), player.getZ()));
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
