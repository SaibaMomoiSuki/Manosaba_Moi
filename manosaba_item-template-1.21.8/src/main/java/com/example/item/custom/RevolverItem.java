// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item.custom;

import com.example.component.ModDataComponents;
import com.example.item.ModItems;
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

import java.util.HashSet;
import java.util.Set;

public class RevolverItem extends Item {
    public static final int CAPACITY = 6;
    private static final Set<java.util.UUID> RELOADING = new HashSet<>();
    public RevolverItem(Properties properties) { super(properties.stacksTo(1)); }

    public static int ammo(ItemStack stack) { return stack.getOrDefault(ModDataComponents.REVOLVER_AMMO, 0); }
    public static int shells(ItemStack stack) { return stack.getOrDefault(ModDataComponents.REVOLVER_SHELLS, 0); }
    public static void setReloading(Player player, boolean value) {
        if (value) RELOADING.add(player.getUUID()); else RELOADING.remove(player.getUUID());
    }
    public static boolean isReloading(Player player) { return RELOADING.contains(player.getUUID()); }
    public static void clearReloading(java.util.UUID id) { RELOADING.remove(id); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.CROSSBOW; }
    @Override public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        return true;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player) || level.isClientSide()) return stack;
        int elapsed = getUseDuration(stack, entity) - player.getUseItemRemainingTicks();
        if (elapsed < 20 || ammo(stack) <= 0) return stack;
        fire(stack, level, player);
        return stack;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide() || !(entity instanceof Player player)) return;
        if (isReloading(player)) return;
        int elapsed = getUseDuration(stack, entity) - remainingUseDuration;
        if (ammo(stack) <= 0) {
            if (elapsed >= 10) {
                RevolverSounds.play(level, player, "needreload" + (level.random.nextInt(3) + 1), 2.0, 1.0F, false);
                player.stopUsingItem();
            }
            return;
        }
        if (elapsed >= 20 && elapsed % 20 == 0 && ammo(stack) > 0) {
            fire(stack, level, player);
            player.stopUsingItem();
        }
    }

    private void fire(ItemStack stack, Level level, Player player) {
        int count = ammo(stack);
        if (count <= 0) return;
        stack.set(ModDataComponents.REVOLVER_AMMO, count - 1);
        stack.set(ModDataComponents.REVOLVER_SHELLS, shells(stack) + 1);
        hitTarget(level, player);
        RevolverSounds.play(level, player, "get_fire", 25.0, 1.0F, true);
    }

    private void hitTarget(Level level, Player player) {
        // Start slightly below the camera so the muzzle line matches the held weapon.
        Vec3 eye = player.getEyePosition().subtract(0.0, 0.3, 0.0);
        Vec3 end = eye.add(player.getLookAngle().scale(50.0));
        var blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, player));
        if (blockHit.getType() == HitResult.Type.BLOCK) end = blockHit.getLocation();

        LivingEntity closest = null;
        Vec3 hitPoint = null;
        double closestDistance = Double.MAX_VALUE;
        for (var candidate : level.getEntities(player, new AABB(eye, end).inflate(1.0), e -> e instanceof LivingEntity)) {
            if (!(candidate instanceof LivingEntity living)) continue;
            var hit = living.getBoundingBox().clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < closestDistance) {
                closestDistance = eye.distanceToSqr(hit.get());
                closest = living;
                hitPoint = hit.get();
            }
        }
        if (closest == null || hitPoint == null) return;
        double relativeY = (hitPoint.y - closest.getY()) / Math.max(0.1, closest.getBbHeight());
        float ratio = relativeY >= 0.82 ? 1.0F : relativeY >= 0.52 ? 0.8F : relativeY >= 0.30 ? 0.8F : 0.4F;
        closest.hurt(player.damageSources().playerAttack(player), closest.getMaxHealth() * ratio);
    }

}
