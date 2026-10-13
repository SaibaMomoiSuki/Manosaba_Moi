// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.item;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * 简易固定器 - 长按右键使用，替代骨折/脱骨折/崴脚效果为固定器效果
 * 使用后获得与原受伤效果同等时间的固定器效果
 */
public class SplintItem extends Item {

    private static final ResourceLocation DISLOCATED_BONE_KEY =
        ResourceLocation.fromNamespaceAndPath("mano_effect", "dislocated_bone");
    private static final ResourceLocation FRACTURE_KEY =
        ResourceLocation.fromNamespaceAndPath("mano_effect", "fracture");
    private static final ResourceLocation TWISTED_ANKLE_KEY =
        ResourceLocation.fromNamespaceAndPath("mano_effect", "twisted_ankle");
    private static final ResourceLocation SPLINTED_KEY =
        ResourceLocation.fromNamespaceAndPath("mano_effect", "splinted");

    private static final ResourceLocation[] INJURY_KEYS = {
        DISLOCATED_BONE_KEY, FRACTURE_KEY, TWISTED_ANKLE_KEY
    };

    public SplintItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 40; // 2 seconds hold
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (hasAnyInjury(player)) {
            player.startUsingItem(usedHand);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (!(livingEntity instanceof Player player)) return stack;

        InjuryInfo info = getActiveInjury(player);
        if (info == null) return stack;

        player.removeEffect(info.holder);

        Optional<Holder.Reference<MobEffect>> splintedHolder = BuiltInRegistries.MOB_EFFECT.get(SPLINTED_KEY);
        if (splintedHolder.isPresent()) {
            player.addEffect(new MobEffectInstance(
                splintedHolder.get(),
                info.remainingDuration,
                0,
                false,
                false,
                true
            ));
        }

        stack.consume(1, player);
        return stack;
    }

    private static boolean hasAnyInjury(Player player) {
        for (ResourceLocation key : INJURY_KEYS) {
            Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(key);
            if (holder.isPresent() && player.hasEffect(holder.get())) {
                return true;
            }
        }
        return false;
    }

    private static InjuryInfo getActiveInjury(Player player) {
        for (ResourceLocation key : INJURY_KEYS) {
            Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(key);
            if (holder.isPresent()) {
                MobEffectInstance instance = player.getEffect(holder.get());
                if (instance != null) {
                    return new InjuryInfo(holder.get(), instance.getDuration());
                }
            }
        }
        return null;
    }

    private static class InjuryInfo {
        final Holder<MobEffect> holder;
        final int remainingDuration;

        InjuryInfo(Holder<MobEffect> holder, int remainingDuration) {
            this.holder = holder;
            this.remainingDuration = remainingDuration;
        }
    }
}
