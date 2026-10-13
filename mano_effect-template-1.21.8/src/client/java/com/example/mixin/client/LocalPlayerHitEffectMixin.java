// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin.client;

import com.example.Mano_effect;
import com.example.Mano_effectClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detects player damage and low-health thresholds.
 * - Hit: any health decrease triggers gray edge vignette
 * - &gt;5 HP: no low-health effect
 * - 3-5 HP: subtle red vignette + blood splats
 * - ≤3 HP: full red vignette (same strength as hit) + blood splats + heartbeat sound
 * Red vignette is persistent while health is low, fades out above 5 HP.
 * Heartbeat plays when health ≤3 (1.5 hearts), with volume oscillation (loud↔soft)
 * and faster beats at lower health.
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerHitEffectMixin {

    @Unique
    private float lastHealth = -1f;
    @Unique
    private boolean initialized = false;

    @Unique
    private int currentTier = 0;

    // Heartbeat state
    @Unique
    private long lastHeartbeatTime = 0;
    @Unique
    private float heartbeatPhase = 0f;
    @Unique
    private boolean heartbeatActive = false;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void detectDamageAndLowHealth(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;

        float currentHealth = player.getHealth();

        if (!initialized) {
            lastHealth = currentHealth;
            initialized = true;
        } else if (currentHealth < lastHealth - 0.001f) {
            float damageTaken = lastHealth - currentHealth;
            Mano_effectClient.hitEffectActive = true;
            Mano_effectClient.hitEffectStartTime = System.currentTimeMillis();
            if (damageTaken > 6f) {
                Mano_effectClient.bloodDamageBurstActive = true;
                Mano_effectClient.bloodDamageBurstEndTime = System.currentTimeMillis() + 5000L;
            }
        }

        lastHealth = currentHealth;

        // ---- Low-health tier ----
        int newTier;
        if (currentHealth <= 5f) {
            newTier = 2; // full red + blood + heartbeat
        } else {
            newTier = 0; // none
        }

        if (newTier != currentTier) {
            currentTier = newTier;
        }

        Mano_effectClient.lowHealthTier = currentTier;

        // Target vignette alpha
        float targetVignette;
        if (currentTier == 0) {
            targetVignette = 0f;
        } else if (currentTier == 1) {
            targetVignette = 0.62f;
        } else {
            targetVignette = 1.0f;  // full (same as hit effect)
        }

        // Low health must be immediately noticeable; only healing fades the warning out.
        Mano_effectClient.lowHealthAlpha = currentTier > 0
            ? targetVignette
            : lerp(Mano_effectClient.lowHealthAlpha, 0f, 0.06f);

        long now = System.currentTimeMillis();
        if (Mano_effectClient.bloodDamageBurstActive
                && now >= Mano_effectClient.bloodDamageBurstEndTime) {
            Mano_effectClient.bloodDamageBurstActive = false;
        }

        float targetBlood = (currentTier >= 2 || Mano_effectClient.bloodDamageBurstActive) ? 1.0f : 0f;
        Mano_effectClient.bloodSplatAlpha = lerp(Mano_effectClient.bloodSplatAlpha, targetBlood, 0.06f);

        // ---- Heartbeat sound ----
        if (currentHealth <= 5f && currentHealth > 0f) {
            handleHeartbeat(currentHealth);
        } else {
            stopHeartbeat();
        }
    }

    // ===== Heartbeat sound (≤3 HP, volume oscillates loud↔soft) =====

    @Unique
    private void handleHeartbeat(float health) {
        long now = System.currentTimeMillis();

        // Lower health → faster heartbeat
        // 3 HP → ~900ms, 1 HP → ~350ms
        long interval = (long) (350 + (health / 5f) * 550);

        if (now - lastHeartbeatTime >= interval) {
            lastHeartbeatTime = now;
            heartbeatPhase += 0.6f;

            // Volume oscillation: loud ↔ soft using sinusoidal wave
            // Range: 0.25 ~ 1.0
            float volume = 0.25f + 0.75f * Math.abs((float) Math.sin(heartbeatPhase));

            // Pitch variation: slightly higher at lower health (more urgent)
            float pitch = 0.9f + (1f - health / 5f) * 0.3f;

            Minecraft client = Minecraft.getInstance();
            if (client.getSoundManager() != null) {
                client.getSoundManager().play(
                    SimpleSoundInstance.forUI(Mano_effect.HEARTBEAT, pitch, volume)
                );
                heartbeatActive = true;
            }
        }
    }

    @Unique
    private void stopHeartbeat() {
        if (heartbeatActive) {
            heartbeatActive = false;
            lastHeartbeatTime = 0;
            heartbeatPhase = 0f;
        }
    }

    @Unique
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
