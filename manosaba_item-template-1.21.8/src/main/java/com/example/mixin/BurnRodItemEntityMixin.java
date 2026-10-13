// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin;

import com.example.item.ModItems;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles burn rod transformations for dropped item entities:
 * - Cooled burn rod on fire/campfire for 10s → burn rod
 * - Burn rod NOT on fire for 60s → cooled burn rod
 */
@Mixin(ItemEntity.class)
public class BurnRodItemEntityMixin {

    @Unique
    private static final Map<UUID, Integer> ticksInFire = new HashMap<>();
    @Unique
    private static final Map<UUID, Integer> ticksOutOfFire = new HashMap<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.level().isClientSide) return;
        if (!self.isAlive()) return;

        ItemStack stack = self.getItem();
        UUID id = self.getUUID();

        boolean inFire = isEntityInFire(self);

        if (stack.is(ModItems.IRON_HOE)) {
            // Cooled Burn Rod: when in fire, heat up → Burn Rod
            if (inFire) {
                int ticks = ticksInFire.getOrDefault(id, 0) + 1;
                ticksInFire.put(id, ticks);
                ticksOutOfFire.remove(id);

                if (ticks >= 200) { // 10 seconds
                    ticksInFire.remove(id);
                    self.setItem(stack.transmuteCopy(ModItems.GOLDEN_HOE));
                }
            } else {
                ticksInFire.remove(id);
            }
        } else if (stack.is(ModItems.GOLDEN_HOE)) {
            // Burn Rod: when out of fire, cool down → Cooled Burn Rod
            if (!inFire) {
                int ticks = ticksOutOfFire.getOrDefault(id, 0) + 1;
                ticksOutOfFire.put(id, ticks);
                ticksInFire.remove(id);

                if (ticks >= 1200) { // 60 seconds
                    ticksOutOfFire.remove(id);
                    self.setItem(stack.transmuteCopy(ModItems.IRON_HOE));
                }
            } else {
                ticksOutOfFire.remove(id);
            }
        }
    }

    @Unique
    private static boolean isEntityInFire(ItemEntity entity) {
        if (entity.isOnFire()) return true;
        var level = entity.level();
        var pos = entity.blockPosition();

        // Check the block the item is on and adjacent blocks
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    var checkPos = pos.offset(dx, dy, dz);
                    var block = level.getBlockState(checkPos);
                    if (block.is(Blocks.FIRE)) return true;
                    if (block.is(Blocks.SOUL_FIRE)) return true;
                    if (block.is(Blocks.CAMPFIRE) && block.getValue(CampfireBlock.LIT)) return true;
                    if (block.is(Blocks.SOUL_CAMPFIRE) && block.getValue(CampfireBlock.LIT)) return true;
                    if (block.is(Blocks.LAVA)) return true;
                }
            }
        }
        return false;
    }
}
