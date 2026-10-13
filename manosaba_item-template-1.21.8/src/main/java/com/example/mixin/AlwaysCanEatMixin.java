// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows the player to eat food even when their hunger bar is full.
 */
@Mixin(Player.class)
public class AlwaysCanEatMixin {

    @Inject(method = "canEat", at = @At("HEAD"), cancellable = true)
    private void onCanEat(boolean ignoreHunger, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
