// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin.client;

import com.example.Mano_effectClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detects jumping and landing when the player has dislocated_bone effect.
 * Triggers the injury hit effect (screen-edge blur) on jump and land.
 * 0.2s fade in, 3s fade out.
 * Includes a 500ms cooldown to prevent rapid re-triggering that causes stutter.
 */
@Mixin(LocalPlayer.class)
public class InjuryJumpHitMixin {

    @Unique
    private static boolean wasOnGround = true;
    @Unique
    private static boolean initialized = false;
    @Unique
    private static long lastTriggerTime = 0;
    @Unique
    private static final long TRIGGER_COOLDOWN_MS = 3200;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void detectJumpAndLand(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;

        if (!Mano_effectClient.dislocatedBoneActive) {
            wasOnGround = player.onGround();
            initialized = true;
            return;
        }

        if (!initialized) {
            wasOnGround = player.onGround();
            initialized = true;
            return;
        }

        boolean nowOnGround = player.onGround();

        long now = System.currentTimeMillis();
        boolean cooldownOver = !Mano_effectClient.injuryHitEffectActive
            && (now - lastTriggerTime) >= TRIGGER_COOLDOWN_MS;

        // Detect jump: was on ground, now in air
        if (wasOnGround && !nowOnGround && cooldownOver) {
            triggerHitEffect();
        }

        // Detect land: was in air, now on ground
        if (!wasOnGround && nowOnGround && cooldownOver) {
            triggerHitEffect();
        }

        wasOnGround = nowOnGround;
    }

    @Unique
    private static void triggerHitEffect() {
        Mano_effectClient.injuryHitEffectActive = true;
        Mano_effectClient.injuryHitEffectStartTime = System.currentTimeMillis();
        lastTriggerTime = Mano_effectClient.injuryHitEffectStartTime;
    }
}
