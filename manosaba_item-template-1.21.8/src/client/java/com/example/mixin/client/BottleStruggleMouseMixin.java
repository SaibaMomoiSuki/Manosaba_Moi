// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin.client;

import com.example.event.BottleStruggleClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zeroes mouse delta during bottle struggle so the player cannot turn the camera.
 */
@Mixin(MouseHandler.class)
public class BottleStruggleMouseMixin {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void onTurnPlayer(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (BottleStruggleClientHandler.INSTANCE.getAttackerId() != null
        || BottleStruggleClientHandler.INSTANCE.getVictimId() != null) {
            this.accumulatedDX = 0;
            this.accumulatedDY = 0;
        }
    }
}
