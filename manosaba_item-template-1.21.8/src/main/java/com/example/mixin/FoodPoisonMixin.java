// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin;

import com.example.component.ModDataComponents;
import com.example.event.ModPlayerState;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

@Mixin(ItemStack.class)
public class FoodPoisonMixin {

    private static final Random RANDOM = new Random();

    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void onFinishUsingItem(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        if (level.isClientSide) return;
        if (!(entity instanceof Player player)) return;
        ItemStack self = (ItemStack) (Object) this;

        Integer poisonedCount = self.get(ModDataComponents.INSTANCE.getPOISONED_COUNT());
        if (poisonedCount == null || poisonedCount <= 0) return;

        int totalCount = self.getCount();
        double probability = (double) poisonedCount / (double) totalCount;

        boolean consumedPoisonedFood = RANDOM.nextDouble() < probability;
        if (consumedPoisonedFood) {
            ModPlayerState.INSTANCE.getPoison().put(
                player.getUUID(),
                new ModPlayerState.PoisonState(level.getGameTime(), false, false, false)
            );
            var redDust = new DustParticleOptions(0xCC0000, 1.0f);
            ((ServerLevel) level).sendParticles(
                redDust,
                entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ(),
                3, 0.15, 0.1, 0.15, 0.02
            );
        }

        int newCount = poisonedCount - (consumedPoisonedFood ? 1 : 0);
        if (newCount > 0) {
            self.set(ModDataComponents.INSTANCE.getPOISONED_COUNT(), newCount);
        } else {
            self.remove(ModDataComponents.INSTANCE.getPOISONED_COUNT());
            self.remove(ModDataComponents.INSTANCE.getPOISONED());
            self.remove(ModDataComponents.INSTANCE.getORIGINAL_FOOD());
        }
    }
}
