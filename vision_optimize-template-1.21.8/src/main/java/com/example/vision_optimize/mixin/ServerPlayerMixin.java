/*
 * Copyright (c) 2026 Momoi. Proprietary - Internal Use Only.
 * 内部私有，严禁任何形式使用（包括游玩、复制、分发）。
 */
package com.example.vision_optimize.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for ServerPlayer.
 *
 * Per-tick path recording, item tracking, and death detection have been moved
 * to {@link com.example.vision_optimize.event.ServerEventHandlers} where they
 * run as a batched server-level loop instead of per-player tick injection.
 * This eliminates the N×20/sec overhead (N = player count) and keeps the tick
 * hot-path clean.
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {

    /**
     * When a player drops an item, do NOT clear its tracking UUID —
     * the holder history chain must persist so the next player can see
     * who held the item before them.
     * Target: ServerPlayer.drop(ItemStack, boolean, boolean) -> ItemEntity
     */
    @Inject(at = @At("HEAD"), method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;")
    private void visionOptimize$onDropItem(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        // Tracking UUID is intentionally preserved — do not remove it.
        // History chain: Player A → drop → Player B picks up → same UUID → history visible.
    }
}
