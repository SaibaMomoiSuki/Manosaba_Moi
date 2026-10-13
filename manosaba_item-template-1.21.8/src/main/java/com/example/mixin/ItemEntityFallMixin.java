// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin;

import com.example.event.ModPlayerState;
import com.example.item.ModItems;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks item entity fall distance. When glass bottles or potions
 * fall 5+ blocks, they break into broken glass bottles and leave
 * colored particle effects.
 */
@Mixin(ItemEntity.class)
public class ItemEntityFallMixin {

    @Unique
    private static final Map<Integer, Double> fallStartY = new ConcurrentHashMap<>();

    @Unique
    private static boolean isBreakableItem(ItemStack stack) {
        return stack.is(ModItems.EMPTY_GLASS_BOTTLE)
            || stack.is(ModItems.POISON)
            || stack.is(ModItems.SLEEPING_PILLS)
            || stack.is(ModItems.YUKIS_SEXUAL_FLUIDS);
    }

    /** Returns particle type for the item: 0=sleeping(blue), 1=poison(dark red), 2=tldjm(light gray), -1=none */
    @Unique
    private static int getParticleType(ItemStack stack) {
        if (stack.is(ModItems.SLEEPING_PILLS)) return 0;
        if (stack.is(ModItems.POISON)) return 1;
        if (stack.is(ModItems.YUKIS_SEXUAL_FLUIDS)) return 2;
        return -1;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.level().isClientSide) return;
        if (!self.isAlive()) {
            fallStartY.remove(self.getId());
            return;
        }

        Integer trackedId = self.getId();
        Double startY = fallStartY.get(trackedId);

        if (startY == null) {
            fallStartY.put(trackedId, self.getY());
            return;
        }

        if (self.getY() > startY) {
            fallStartY.put(trackedId, self.getY());
        }

        if (self.onGround() && Math.abs(self.getDeltaMovement().y) < 0.1) {
            double fallDistance = startY - self.getY();
            if (fallDistance >= 5.0) {
                ItemStack stack = self.getItem();
                if (isBreakableItem(stack)) {
                    self.setItem(new ItemStack(ModItems.BROKEN_BOTTLE, stack.getCount()));

                    // Spawn break particles
                    int particleType = getParticleType(stack);
                    if (particleType >= 0) {
                        long expiryTime = self.level().getGameTime() + 100; // 5 seconds = 100 ticks
                        ModPlayerState.INSTANCE.addBreakParticle(
                            self.level().dimension(),
                            self.getX(), self.getY(), self.getZ(),
                            expiryTime, particleType
                        );
                    }
                }
            }
            fallStartY.remove(trackedId);
        }
    }
}
