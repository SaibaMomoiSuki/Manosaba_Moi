// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/** Interrupts sprinting with a 40% roll for each block travelled while dizzy. */
@Mixin(Player.class)
public abstract class DizzinessSprintMixin {
    @Unique
    private double manoEffect$lastX;
    @Unique
    private double manoEffect$lastZ;
    @Unique
    private double manoEffect$sprintDistance;
    @Unique
    private boolean manoEffect$positionInitialized;

    @Inject(method = "tick", at = @At("TAIL"))
    private void manoEffect$interruptDizzySprint(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        double currentX = player.getX();
        double currentZ = player.getZ();

        if (!manoEffect$positionInitialized) {
            manoEffect$lastX = currentX;
            manoEffect$lastZ = currentZ;
            manoEffect$positionInitialized = true;
            return;
        }

        double movedX = currentX - manoEffect$lastX;
        double movedZ = currentZ - manoEffect$lastZ;
        manoEffect$lastX = currentX;
        manoEffect$lastZ = currentZ;

        if (player.level().isClientSide() || !player.isSprinting() || !manoEffect$hasDizziness(player)) {
            manoEffect$sprintDistance = 0.0;
            return;
        }

        manoEffect$sprintDistance += Math.sqrt(movedX * movedX + movedZ * movedZ);
        while (manoEffect$sprintDistance >= 1.0) {
            manoEffect$sprintDistance -= 1.0;
            if (player.getRandom().nextFloat() < 0.4f) {
                player.setSprinting(false);
                manoEffect$sprintDistance = 0.0;
                break;
            }
        }
    }

    @Unique
    private static boolean manoEffect$hasDizziness(Player player) {
        Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("mano_effect", "dizziness")
        );
        return holder.isPresent() && player.hasEffect(holder.get());
    }
}
