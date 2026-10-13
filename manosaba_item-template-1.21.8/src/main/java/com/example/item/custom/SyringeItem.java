// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item.custom;

import com.example.event.ModPlayerState;
import com.example.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SyringeItem extends Item {
    private final SyringeType syringeType;

    public SyringeItem(Properties properties, SyringeType syringeType) {
        super(properties);
        this.syringeType = syringeType;
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                              LivingEntity entity, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.SUCCESS;
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 60; }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.BOW; }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player) || level.isClientSide()) return stack;
        LivingEntity target = findTargetEntity(player, level);
        LivingEntity subject = target != null ? target : player;
        subject.hurt(target != null ? level.damageSources().playerAttack(player) : level.damageSources().generic(), 2.0F);
        applyEffect(subject, level);
        if (subject instanceof Player injectedPlayer) {
            injectedPlayer.displayClientMessage(
                Component.translatable("message.manosaba_item.injection_pain").withStyle(ChatFormatting.RED), true);
        }
        return player.isCreative() ? stack : new ItemStack(ModItems.SYRINGE);
    }

    private LivingEntity findTargetEntity(Player player, Level level) {
        double reach = 5.0;
        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 end = eye.add(direction.scale(reach));
        var blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, player));
        double blockDistance = blockHit.getType() == HitResult.Type.BLOCK
            ? eye.distanceTo(blockHit.getLocation()) : Double.MAX_VALUE;
        LivingEntity closest = null;
        double closestT = reach;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
            new AABB(eye, end).inflate(1.0), e -> e != player && e.isAlive())) {
            double t = Math.clamp(candidate.position().subtract(eye).dot(direction), 0.0, reach);
            double distance = eye.add(direction.scale(t)).distanceTo(candidate.position());
            if (distance < candidate.getBoundingBox().getSize() * 1.5 && t < closestT && t < blockDistance) {
                closestT = t;
                closest = candidate;
            }
        }
        return closest;
    }

    private void applyEffect(LivingEntity entity, Level level) {
        if (!(entity instanceof Player player)) return;
        switch (syringeType) {
            case TLDJM -> ModPlayerState.tldjm.put(player.getUUID(),
                new ModPlayerState.TldjmState(level.getGameTime() + 1200));
            case SLEEPING -> ModPlayerState.sleepingPills.put(player.getUUID(),
                new ModPlayerState.SleepingPillsState(level.getGameTime(), false, false, false));
            case POISON -> ModPlayerState.poison.put(player.getUUID(),
                new ModPlayerState.PoisonState(level.getGameTime(), false, false, false));
        }
    }
}
