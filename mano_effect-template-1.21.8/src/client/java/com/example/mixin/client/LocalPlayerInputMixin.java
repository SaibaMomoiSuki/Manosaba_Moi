// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.Random;

/**
 * Reverses movement keys when the player has dizziness.
 * Injects AFTER ClientInput.tick() so the negation isn't overwritten.
 * Every second: 1/3 chance to reverse for 10 ticks (0.5s).
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerInputMixin {
    @Unique
    private int reversalTicks = 0;
    @Unique
    private int tickCounter = 0;
    @Unique
    private static final Random RANDOM = new Random();

    /**
     * Inject AFTER ClientInput.tick() so moveVector is already computed
     * from current key presses before we negate it.
     */
    @Inject(method = "aiStep", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/player/ClientInput;tick()V",
        shift = At.Shift.AFTER
    ))
    private void afterInputTick(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;

        ResourceLocation effectKey = ResourceLocation.fromNamespaceAndPath("mano_effect", "dizziness");
        Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(effectKey);

        if (holder.isPresent() && player.hasEffect(holder.get())) {
            tickCounter++;

            // Every second (20 ticks), 1/3 chance to start reversal
            if (tickCounter % 20 == 0 && reversalTicks <= 0) {
                if (RANDOM.nextFloat() < 1.0f / 4.0f) {
                    reversalTicks = 4;  // 0.2 seconds at 20 tps
                }
            }

            // Apply reversal: negate the move vector
            if (reversalTicks > 0) {
                ClientInput input = player.input;
                Vec2 moveVec = input.getMoveVector();
                ((ClientInputAccessor) (Object) input).setMoveVector(
                    new Vec2(-moveVec.x, -moveVec.y)
                );
                reversalTicks--;
            }
        } else {
            reversalTicks = 0;
            tickCounter = 0;
        }
    }
}
