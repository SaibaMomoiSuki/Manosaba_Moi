// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item.custom;

import com.example.effect.ModEffects;
import com.example.event.ModPlayerState;
import com.example.item.ModItems;
import com.example.network.BottleStruggleStartPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BrokenBottleItem extends Item {
    public static final CompanionAdapter Companion = new CompanionAdapter();
    private static final Map<UUID, Long> PICKUP_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SWITCH_COOLDOWNS = new ConcurrentHashMap<>();

    public BrokenBottleItem(Properties properties) { super(properties); }

    private static void safeCut(Player player, float amount) {
        if (player.getHealth() > 1.0F)
            player.hurt(player.damageSources().generic(), Math.min(amount, player.getHealth() - 1.0F));
    }

    public static void onSwitchToEvent(Player player) {
        long now = player.level().getGameTime();
        Long last = SWITCH_COOLDOWNS.put(player.getUUID(), now);
        if (last != null && now - last < 10) return;
        if (player.getRandom().nextFloat() < 0.5F) safeCut(player, 1.0F);
    }

    public static void onDropEvent(Player player) {
        if (player.getRandom().nextFloat() < 0.5F) {
            safeCut(player, 3.0F);
            player.displayClientMessage(Component.translatable("message.manosaba_item.glass_cut")
                .withStyle(ChatFormatting.RED), true);
        }
    }

    public static void onPickupEvent(Player player) {
        long now = player.level().getGameTime();
        Long last = PICKUP_COOLDOWNS.put(player.getUUID(), now);
        if (last != null && now - last < 10) return;
        if (player.getRandom().nextFloat() < 0.3F) {
            safeCut(player, 3.0F);
            player.displayClientMessage(Component.translatable("message.manosaba_item.glass_cut")
                .withStyle(ChatFormatting.RED), true);
        } else {
            player.displayClientMessage(Component.translatable("message.manosaba_item.glass_pickup_safe")
                .withStyle(ChatFormatting.GREEN), true);
        }
    }

    public static boolean isInStruggle(UUID uuid) {
        if (ModPlayerState.bottleStruggle.containsKey(uuid)) return true;
        return ModPlayerState.bottleStruggle.values().stream().anyMatch(state -> state.victimId.equals(uuid));
    }

    public static Player findTargetPlayer(Player player, Level level) {
        double reach = 5.0;
        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 end = eye.add(direction.scale(reach));
        var blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, player));
        double blockDistance = blockHit.getType() == HitResult.Type.BLOCK
            ? eye.distanceTo(blockHit.getLocation()) : Double.MAX_VALUE;
        Player closest = null;
        double closestT = reach;
        for (Player candidate : level.getEntitiesOfClass(Player.class, new AABB(eye, end).inflate(1.0),
            other -> other != player && other.isAlive())) {
            double t = Math.clamp(candidate.position().subtract(eye).dot(direction), 0.0, reach);
            double distance = eye.add(direction.scale(t)).distanceTo(candidate.position());
            if (distance < candidate.getBoundingBox().getSize() * 1.5 && t < closestT && t < blockDistance) {
                closestT = t;
                closest = candidate;
            }
        }
        return closest;
    }

    public static void onStruggleAttackerWin(Player attacker, Player victim, Level level) {
        victim.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(ModEffects.BLEEDING),
            600, 0, false, false, true), attacker);
        ModPlayerState.bottleStabDamage.put(victim.getUUID(),
            new ModPlayerState.BottleStabDamageState(level.getGameTime(), level.getGameTime()));
        ItemStack bottle = new ItemStack(ModItems.BLOODIED_BROKEN_BOTTLE);
        if (!attacker.getInventory().add(bottle)) attacker.drop(bottle, false);
    }

    public static void onStruggleFail(Player attacker, Player victim) {
    }

    public static final class CompanionAdapter {
        public void onSwitchToEvent(Player player) { BrokenBottleItem.onSwitchToEvent(player); }
        public void onPickupEvent(Player player) { BrokenBottleItem.onPickupEvent(player); }
        public void onDropEvent(Player player) { BrokenBottleItem.onDropEvent(player); }
        public void onStruggleAttackerWin(Player attacker, Player victim, Level level) {
            BrokenBottleItem.onStruggleAttackerWin(attacker, victim, level);
        }
        public void onStruggleFail(Player attacker, Player victim) {
            BrokenBottleItem.onStruggleFail(attacker, victim);
        }
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 60; }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.BOW; }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                              LivingEntity entity, InteractionHand hand) {
        if (entity instanceof Player) {
            player.startUsingItem(hand);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer attacker) || level.isClientSide()) return stack;
        Player target = findTargetPlayer(attacker, level);
        if (!(target instanceof ServerPlayer victim) || isInStruggle(attacker.getUUID())
            || isInStruggle(victim.getUUID())) return stack;
        ModPlayerState.bottleStruggle.put(attacker.getUUID(),
            new ModPlayerState.BottleStruggleState(attacker.getUUID(), victim.getUUID(), level.getGameTime()));
        ServerPlayNetworking.send(attacker, new BottleStruggleStartPayload(attacker.getUUID(), victim.getUUID()));
        ServerPlayNetworking.send(victim, new BottleStruggleStartPayload(attacker.getUUID(), victim.getUUID()));
        stack.shrink(1);
        return stack;
    }
}
