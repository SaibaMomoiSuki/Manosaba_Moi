// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin;

import com.example.item.ModItems;
import com.example.item.custom.BloodiedBrokenBottleItem;
import com.example.item.custom.BrokenBottleItem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void cleanBloodNanokaHairband(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!self.level().isClientSide() && self.isInWater() && self.getItem().is(ModItems.BLOOD_NANOKA_HAIRBAND)) {
            self.setItem(self.getItem().transmuteCopy(ModItems.NANOKA_HAIRBAND));
        }
    }

    @Inject(method = "playerTouch", at = @At("TAIL"))
    private void onPlayerTouch(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (player.level().isClientSide) return;
        // Only trigger when item was actually picked up (entity discarded)
        if (self.isAlive()) return;
        ItemStack stack = self.getItem();
        if (stack.is(ModItems.BROKEN_BOTTLE)) {
            BrokenBottleItem.onPickupEvent(player);
        }
        if (stack.is(ModItems.BLOODIED_BROKEN_BOTTLE)) {
            BloodiedBrokenBottleItem.onPickupEvent(player);
        }
    }

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void requireCrouchingEmptyHands(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getItem().is(ModItems.REVOLVER_BULLET) || self.getItem().is(ModItems.SHELL_CASING)) {
            if (!player.isCrouching() || !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) ci.cancel();
        }
    }
}
