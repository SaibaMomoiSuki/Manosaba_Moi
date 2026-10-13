// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin.client;

import com.example.Mano_effectClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.Random;

/**
 * Handles dizziness post-effect shader + fade state tracking for radiation/boost.
 * Radiation and radiation_boost visuals are rendered via GuiEffectOverlayMixin.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Unique
    private static final ResourceLocation DIZZINESS_EFFECT = effectId("dizziness");
    @Unique
    private static final ResourceLocation RADIATION_EFFECT = effectId("radiation");
    @Unique
    private static final ResourceLocation RADIATION_BOOST_EFFECT = effectId("radiation_boost");
    @Unique
    private static final ResourceLocation DISLOCATED_BONE_EFFECT = effectId("dislocated_bone");
    @Unique
    private static final ResourceLocation FRACTURE_EFFECT = effectId("fracture");
    @Unique
    private static final ResourceLocation TWISTED_ANKLE_EFFECT = effectId("twisted_ankle");
    @Unique
    private static final ResourceLocation SPLINTED_EFFECT = effectId("splinted");
    @Unique
    private static final ResourceLocation SERIOUSLY_INJURED_EFFECT = effectId("manosaba_seriously_injured");
    @Unique
    private static final ResourceLocation DIZZINESS_SHADER =
        ResourceLocation.fromNamespaceAndPath("mano_effect", "dizziness");
    @Unique
    private static final long FADE_OUT_MS = 2000;

    // Radiation state
    @Unique
    private static long radiationLostTime = 0;
    @Unique
    private static boolean wasRadiationActive = false;

    // Boost state
    @Unique
    private static long boostLostTime = 0;
    @Unique
    private static boolean wasBoostActive = false;

    // Injury state (dislocated_bone, fracture, seriously_injured — for edge dust)
    @Unique
    private static long injuryDustLostTime = 0;
    @Unique
    private static boolean wasAnyInjuryActive = false;

    // Twisted ankle / dislocated bone black border state
    @Unique
    private static long injuryBorderLostTime = 0;
    @Unique
    private static boolean wasInjuryBorderActive = false;

    // Seriously injured dust state
    @Unique
    private static long seriouslyInjuredDustLostTime = 0;
    @Unique
    private static boolean wasSeriouslyInjuredActive = false;

    // Fracture blur state (fade in/out)
    @Unique
    private static long fractureBlurGainedTime = 0;
    @Unique
    private static long fractureBlurLostTime = 0;
    @Unique
    private static boolean wasFractureBlurActive = false;
    @Unique
    private static final long FRACTURE_BLUR_FADE_IN_MS = 1000;
    @Unique
    private static final long FRACTURE_BLUR_FADE_OUT_MS = 2000;
    @Unique
    private static final Random FRACTURE_RANDOM = new Random();

    @Unique
    private static long currentTimeMs() {
        return System.currentTimeMillis();
    }

    @Inject(method = "render", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V",
        shift = At.Shift.AFTER
    ))
    private void beforePostEffectProcessing(
            net.minecraft.client.DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        GameRenderer self = (GameRenderer) (Object) this;
        Minecraft mc = self.getMinecraft();
        LivingEntity living = mc.player;

        long now = currentTimeMs();

        boolean hasDizziness = living != null && hasEffect(living, DIZZINESS_EFFECT);
        boolean hasRadiation = living != null && hasEffect(living, RADIATION_EFFECT);
        boolean hasBoost = living != null && hasEffect(living, RADIATION_BOOST_EFFECT);
        Mano_effectClient.dizzinessActive = hasDizziness;

        // Update fade state for radiation (visuals rendered in GuiEffectOverlayMixin)
        updateRadiationFade(hasRadiation, now);
        Mano_effectClient.radiationActive = hasRadiation;

        // Update radiation diagonal lines
        updateRadiationLines(hasRadiation, now);

        // Update fade state for boost (visuals rendered in GuiEffectOverlayMixin)
        updateBoostFade(hasBoost, now);

        // Track injury effects (mutually exclusive in practice)
        boolean hasDislocatedBone = living != null && hasEffect(living, DISLOCATED_BONE_EFFECT);
        boolean hasFracture = living != null && hasEffect(living, FRACTURE_EFFECT);
        boolean hasTwistedAnkle = living != null && hasEffect(living, TWISTED_ANKLE_EFFECT);
        boolean hasSplinted = living != null && hasEffect(living, SPLINTED_EFFECT);
        Optional<Holder.Reference<MobEffect>> seriouslyInjuredHolder = getEffect(SERIOUSLY_INJURED_EFFECT);
        boolean hasSeriouslyInjured = living != null && seriouslyInjuredHolder.isPresent()
            && living.hasEffect(seriouslyInjuredHolder.get());
        // Track amplifier level for dust scaling
        if (hasSeriouslyInjured) {
            if (seriouslyInjuredHolder.isPresent()) {
                var instance = living.getEffect(seriouslyInjuredHolder.get());
                Mano_effectClient.seriouslyInjuredAmplifier = instance != null ? instance.getAmplifier() : 0;
            }
        } else {
            Mano_effectClient.seriouslyInjuredAmplifier = 0;
        }
        // Injury edge dust: fracture, seriously_injured, and dislocated_bone
        boolean anyInjuryDust = hasDislocatedBone || hasFracture || hasSeriouslyInjured || hasDizziness;

        Mano_effectClient.dislocatedBoneActive = hasDislocatedBone;
        Mano_effectClient.fractureActive = hasFracture;
        Mano_effectClient.twistedAnkleActive = hasTwistedAnkle;
        Mano_effectClient.splintedActive = hasSplinted;
        Mano_effectClient.seriouslyInjuredActive = hasSeriouslyInjured;

        // Injury edge dust fade (fracture, seriously_injured)
        updateInjuryDustFade(anyInjuryDust, now);

        // Black border vignette for twisted_ankle and dislocated_bone
        boolean anyInjuryBorder = hasTwistedAnkle || hasDislocatedBone;
        updateInjuryBorderFade(anyInjuryBorder, now);

        // Seriously injured dust fade
        updateSeriouslyInjuredDustFade(hasSeriouslyInjured, now);

        // Fracture blur
        updateFractureBlur(hasFracture, now);

        // Fire ash dust management
        updateFireDust(now, living);

        // Rain droplet overlay management
        updateWetScreen(now);

        // Dizziness still uses post-effect shader
        // Fracture blur uses the same dizziness shader
        boolean shouldUsePostEffect = hasDizziness || Mano_effectClient.blurActive
            || Mano_effectClient.fractureBlurActive;
        GameRendererAccessor accessor = (GameRendererAccessor) self;
        boolean dizzinessPostEffectLoaded = DIZZINESS_SHADER.equals(accessor.getPostEffectId())
            && accessor.isEffectActive();
        if (shouldUsePostEffect && !dizzinessPostEffectLoaded) {
            ((GameRendererAccessor) self).invokeSetPostEffect(DIZZINESS_SHADER);
        } else if (!shouldUsePostEffect && dizzinessPostEffectLoaded) {
            self.clearPostEffect();
        }
    }

    @Unique
    private static ResourceLocation effectId(String name) {
        return ResourceLocation.fromNamespaceAndPath("mano_effect", name);
    }

    @Unique
    private static Optional<Holder.Reference<MobEffect>> getEffect(ResourceLocation key) {
        return BuiltInRegistries.MOB_EFFECT.get(key);
    }

    @Unique
    private static boolean hasEffect(LivingEntity living, ResourceLocation key) {
        Optional<Holder.Reference<MobEffect>> holder = getEffect(key);
        return holder.isPresent() && living.hasEffect(holder.get());
    }

    // ===== Radiation fade =====

    @Unique
    private static void updateRadiationFade(boolean active, long now) {
        if (active) {
            if (!wasRadiationActive) {
                radiationLostTime = 0;
                // Reset dust for new effect application
                spawnNewDust(now);
            }
            if (wasRadiationActive && radiationLostTime > 0) {
                // Was fading out, effect re-applied
                radiationLostTime = 0;
            }
            Mano_effectClient.smoothRadiationAlpha = 1.0f;
            Mano_effectClient.radiationActive = true;
            wasRadiationActive = true;

            // Update dust lifecycle
            updateDustLifecycle(now);
        } else if (wasRadiationActive) {
            if (radiationLostTime == 0) {
                radiationLostTime = now;
            }
            long elapsed = now - radiationLostTime;
            if (elapsed >= FADE_OUT_MS) {
                Mano_effectClient.smoothRadiationAlpha = 0.0f;
                Mano_effectClient.radiationActive = false;
                Mano_effectClient.dustAlpha = 0.0f;
                wasRadiationActive = false;
                radiationLostTime = 0;
            } else {
                float t = (float) elapsed / FADE_OUT_MS;
                float alpha = (float) Math.pow(1f - t, 3);
                Mano_effectClient.smoothRadiationAlpha = alpha;
                // Still update dust lifecycle during fade-out so existing dust fades
                updateDustLifecycle(now);
            }
        }
    }

    // ===== Dust particle lifecycle =====

    @Unique
    private static final long DUST_FADE_IN_MS = 500;
    @Unique
    private static final long DUST_HOLD_MS = 600;
    @Unique
    private static final long DUST_FADE_OUT_MS = 500;
    @Unique
    private static final long DUST_TOTAL_MS = DUST_FADE_IN_MS + DUST_HOLD_MS + DUST_FADE_OUT_MS;
    @Unique
    private static final Random DUST_RANDOM = new Random();

    @Unique
    private static void spawnNewDust(long now) {
        // Random position, size ~1/6 of screen area
        Mano_effectClient.dustX = 0.15f + DUST_RANDOM.nextFloat() * 0.7f;
        Mano_effectClient.dustY = 0.15f + DUST_RANDOM.nextFloat() * 0.7f;
        Mano_effectClient.dustSize = 0.22f + DUST_RANDOM.nextFloat() * 0.12f; // ~1/6 area
        Mano_effectClient.dustTextureIndex = 3 + DUST_RANDOM.nextInt(7);
        Mano_effectClient.dustSpawnTime = now;
        Mano_effectClient.dustAlpha = 0.0f;
    }

    @Unique
    private static void updateDustLifecycle(long now) {
        long elapsed = now - Mano_effectClient.dustSpawnTime;

        if (elapsed >= DUST_TOTAL_MS) {
            // Current dust finished, spawn new one
            spawnNewDust(now);
            return;
        }

        if (elapsed < DUST_FADE_IN_MS) {
            // Fade in
            float t = (float) elapsed / DUST_FADE_IN_MS;
            Mano_effectClient.dustAlpha = t; // linear fade in
        } else if (elapsed < DUST_FADE_IN_MS + DUST_HOLD_MS) {
            // Hold at full
            Mano_effectClient.dustAlpha = 1.0f;
        } else {
            // Fade out
            float t = (float) (elapsed - DUST_FADE_IN_MS - DUST_HOLD_MS) / DUST_FADE_OUT_MS;
            Mano_effectClient.dustAlpha = 1.0f - t;
        }
    }

    // ===== Radiation diagonal white lines (25° right tilt, flash in/out) =====

    @Unique
    private static final java.util.Random RAD_LINE_RANDOM = new java.util.Random();

    @Unique
    private static void updateRadiationLines(boolean hasRadiation, long now) {
        for (int i = 0; i < Mano_effectClient.MAX_RAD_LINES; i++) {
            int phase = Mano_effectClient.radLinePhase[i];

            if (!hasRadiation) {
                // No radiation — fade out and stop
                if (phase != 2) {
                    Mano_effectClient.radLinePhase[i] = 2;
                    Mano_effectClient.radLineSpawnTime[i] = now;
                    Mano_effectClient.radLineFadeOutMs[i] = 300;
                }
                if (phase == 2) {
                    long elapsed = now - Mano_effectClient.radLineSpawnTime[i];
                    if (elapsed >= Mano_effectClient.radLineFadeOutMs[i]) {
                        Mano_effectClient.radLineAlpha[i] = 0f;
                    } else {
                        Mano_effectClient.radLineAlpha[i] = Math.max(0f, Mano_effectClient.radLineAlpha[i] - 0.02f);
                    }
                }
                continue;
            }

            switch (phase) {
                case 0: { // Fade in
                    long elapsed = now - Mano_effectClient.radLineSpawnTime[i];
                    long fadeInMs = Mano_effectClient.radLineFadeInMs[i];
                    if (elapsed >= fadeInMs) {
                        Mano_effectClient.radLineAlpha[i] = 1.0f;
                        Mano_effectClient.radLinePhase[i] = 1;
                        Mano_effectClient.radLineSpawnTime[i] = now;
                    } else {
                        Mano_effectClient.radLineAlpha[i] = (float) elapsed / fadeInMs;
                    }
                    break;
                }
                case 1: { // Hold
                    long elapsed = now - Mano_effectClient.radLineSpawnTime[i];
                    long holdMs = Mano_effectClient.radLineHoldMs[i];
                    if (elapsed >= holdMs) {
                        Mano_effectClient.radLinePhase[i] = 2;
                        Mano_effectClient.radLineSpawnTime[i] = now;
                    }
                    break;
                }
                case 2: { // Fade out → respawn
                    long elapsed = now - Mano_effectClient.radLineSpawnTime[i];
                    long fadeOutMs = Mano_effectClient.radLineFadeOutMs[i];
                    if (elapsed >= fadeOutMs) {
                        // Spawn new line
                        spawnRadLine(i, now);
                    } else {
                        Mano_effectClient.radLineAlpha[i] = 1.0f - (float) elapsed / fadeOutMs;
                    }
                    break;
                }
            }
        }
    }

    @Unique
    private static void spawnRadLine(int index, long now) {
        Mano_effectClient.radLineX[index] = RAD_LINE_RANDOM.nextFloat();
        Mano_effectClient.radLineY[index] = RAD_LINE_RANDOM.nextFloat();
        // Length: 0.06 to 0.35 of screen width
        Mano_effectClient.radLineLength[index] = 0.06f + RAD_LINE_RANDOM.nextFloat() * 0.29f;
        Mano_effectClient.radLineAlpha[index] = 0f;
        Mano_effectClient.radLineSpawnTime[index] = now;
        Mano_effectClient.radLineFadeInMs[index] = 100 + RAD_LINE_RANDOM.nextInt(200);
        Mano_effectClient.radLineHoldMs[index] = 300 + RAD_LINE_RANDOM.nextInt(700);
        Mano_effectClient.radLineFadeOutMs[index] = 200 + RAD_LINE_RANDOM.nextInt(400);
        Mano_effectClient.radLinePhase[index] = 0;
        // Random thin thickness: 0.5–2.0 px
        Mano_effectClient.radLineThickness[index] = 0.5f + RAD_LINE_RANDOM.nextFloat() * 1.5f;
    }

    // ===== Injury edge dust fade =====

    @Unique
    private static final long INJURY_DUST_FADE_IN_MS = 500;
    @Unique
    private static final long INJURY_DUST_HOLD_MS = 600;
    @Unique
    private static final long INJURY_DUST_FADE_OUT_MS = 500;
    @Unique
    private static final long INJURY_DUST_TOTAL_MS = INJURY_DUST_FADE_IN_MS + INJURY_DUST_HOLD_MS + INJURY_DUST_FADE_OUT_MS;
    @Unique
    private static final Random INJURY_DUST_RANDOM = new Random();

    @Unique
    private static void updateInjuryDustFade(boolean anyInjury, long now) {
        if (anyInjury) {
            if (!wasAnyInjuryActive) {
                injuryDustLostTime = 0;
                spawnNewInjuryDust(now);
            }
            if (wasAnyInjuryActive && injuryDustLostTime > 0) {
                injuryDustLostTime = 0;
            }
            Mano_effectClient.smoothInjuryDustAlpha = 1.0f;
            wasAnyInjuryActive = true;

            updateInjuryDustLifecycle(now);
        } else if (wasAnyInjuryActive) {
            if (injuryDustLostTime == 0) {
                injuryDustLostTime = now;
            }
            long elapsed = now - injuryDustLostTime;
            if (elapsed >= FADE_OUT_MS) {
                Mano_effectClient.smoothInjuryDustAlpha = 0.0f;
                Mano_effectClient.injuryDustAlpha = 0.0f;
                wasAnyInjuryActive = false;
                injuryDustLostTime = 0;
            } else {
                float t = (float) elapsed / FADE_OUT_MS;
                float alpha = (float) Math.pow(1f - t, 3);
                Mano_effectClient.smoothInjuryDustAlpha = alpha;
                updateInjuryDustLifecycle(now);
            }
        }
    }

    @Unique
    private static void spawnNewInjuryDust(long now) {
        spawnEdgeDust(INJURY_DUST_RANDOM, false);
        Mano_effectClient.injuryDustSize = 0.18f + INJURY_DUST_RANDOM.nextFloat() * 0.10f;
        Mano_effectClient.injuryDustTextureIndex = 3 + INJURY_DUST_RANDOM.nextInt(7);
        Mano_effectClient.injuryDustSpawnTime = now;
        Mano_effectClient.injuryDustAlpha = 0.0f;
    }

    @Unique
    private static void updateInjuryDustLifecycle(long now) {
        long elapsed = now - Mano_effectClient.injuryDustSpawnTime;

        if (elapsed >= INJURY_DUST_TOTAL_MS) {
            spawnNewInjuryDust(now);
            return;
        }

        if (elapsed < INJURY_DUST_FADE_IN_MS) {
            float t = (float) elapsed / INJURY_DUST_FADE_IN_MS;
            Mano_effectClient.injuryDustAlpha = t;
        } else if (elapsed < INJURY_DUST_FADE_IN_MS + INJURY_DUST_HOLD_MS) {
            Mano_effectClient.injuryDustAlpha = 1.0f;
        } else {
            float t = (float) (elapsed - INJURY_DUST_FADE_IN_MS - INJURY_DUST_HOLD_MS) / INJURY_DUST_FADE_OUT_MS;
            Mano_effectClient.injuryDustAlpha = 1.0f - t;
        }
    }

    // ===== Injury black border fade (twisted_ankle + dislocated_bone) =====

    @Unique
    private static final long INJURY_BORDER_FADE_IN_MS = 500;
    @Unique
    private static final long INJURY_BORDER_FADE_OUT_MS = 2000;

    @Unique
    private static void updateInjuryBorderFade(boolean active, long now) {
        if (active) {
            if (!wasInjuryBorderActive) {
                injuryBorderLostTime = 0;
            }
            if (wasInjuryBorderActive && injuryBorderLostTime > 0) {
                injuryBorderLostTime = 0;
            }
            // Fade in
            if (injuryBorderLostTime == 0) {
                Mano_effectClient.twistedAnkleBorderAlpha = Math.min(1.0f,
                    Mano_effectClient.twistedAnkleBorderAlpha + 0.02f);
            }
            wasInjuryBorderActive = true;
        } else if (wasInjuryBorderActive) {
            if (injuryBorderLostTime == 0) {
                injuryBorderLostTime = now;
            }
            long elapsed = now - injuryBorderLostTime;
            if (elapsed >= INJURY_BORDER_FADE_OUT_MS) {
                Mano_effectClient.twistedAnkleBorderAlpha = 0.0f;
                wasInjuryBorderActive = false;
                injuryBorderLostTime = 0;
            } else {
                float t = (float) elapsed / INJURY_BORDER_FADE_OUT_MS;
                float alpha = 1f - (float) Math.pow(1f - t, 3);
                Mano_effectClient.twistedAnkleBorderAlpha = 1.0f - alpha;
            }
        }
    }

    // ===== Seriously injured dust fade =====

    @Unique
    private static final long SI_DUST_FADE_IN_MS = 500;
    @Unique
    private static final long SI_DUST_HOLD_MS = 600;
    @Unique
    private static final long SI_DUST_FADE_OUT_MS = 500;
    @Unique
    private static final long SI_DUST_TOTAL_MS = SI_DUST_FADE_IN_MS + SI_DUST_HOLD_MS + SI_DUST_FADE_OUT_MS;
    @Unique
    private static final Random SI_DUST_RANDOM = new Random();

    @Unique
    private static void updateSeriouslyInjuredDustFade(boolean active, long now) {
        if (active) {
            if (!wasSeriouslyInjuredActive) {
                seriouslyInjuredDustLostTime = 0;
                spawnNewSeriouslyInjuredDust(now);
            }
            if (wasSeriouslyInjuredActive && seriouslyInjuredDustLostTime > 0) {
                seriouslyInjuredDustLostTime = 0;
            }
            Mano_effectClient.smoothSeriouslyInjuredDustAlpha = 1.0f;
            wasSeriouslyInjuredActive = true;

            updateSeriouslyInjuredDustLifecycle(now);
        } else if (wasSeriouslyInjuredActive) {
            if (seriouslyInjuredDustLostTime == 0) {
                seriouslyInjuredDustLostTime = now;
            }
            long elapsed = now - seriouslyInjuredDustLostTime;
            if (elapsed >= FADE_OUT_MS) {
                Mano_effectClient.smoothSeriouslyInjuredDustAlpha = 0.0f;
                Mano_effectClient.seriouslyInjuredDustAlpha = 0.0f;
                wasSeriouslyInjuredActive = false;
                seriouslyInjuredDustLostTime = 0;
            } else {
                float t = (float) elapsed / FADE_OUT_MS;
                float alpha = (float) Math.pow(1f - t, 3);
                Mano_effectClient.smoothSeriouslyInjuredDustAlpha = alpha;
                updateSeriouslyInjuredDustLifecycle(now);
            }
        }
    }

    @Unique
    private static void spawnNewSeriouslyInjuredDust(long now) {
        spawnEdgeDust(SI_DUST_RANDOM, true);
        Mano_effectClient.seriouslyInjuredDustSize = 0.18f + SI_DUST_RANDOM.nextFloat() * 0.10f;
        Mano_effectClient.seriouslyInjuredDustTextureIndex = 3 + SI_DUST_RANDOM.nextInt(7);
        Mano_effectClient.seriouslyInjuredDustSpawnTime = now;
        Mano_effectClient.seriouslyInjuredDustAlpha = 0.0f;
    }

    @Unique
    private static void spawnEdgeDust(Random random, boolean seriouslyInjured) {
        float edgeDepth = 0.04f + random.nextFloat() * 0.10f;
        float alongEdge = 0.08f + random.nextFloat() * 0.84f;
        float x;
        float y;
        switch (random.nextInt(4)) {
            case 0 -> {
                x = alongEdge;
                y = edgeDepth;
            }
            case 1 -> {
                x = alongEdge;
                y = 1f - edgeDepth;
            }
            case 2 -> {
                x = edgeDepth;
                y = alongEdge;
            }
            default -> {
                x = 1f - edgeDepth;
                y = alongEdge;
            }
        }
        if (seriouslyInjured) {
            Mano_effectClient.seriouslyInjuredDustX = x;
            Mano_effectClient.seriouslyInjuredDustY = y;
        } else {
            Mano_effectClient.injuryDustX = x;
            Mano_effectClient.injuryDustY = y;
        }
    }

    @Unique
    private static void updateSeriouslyInjuredDustLifecycle(long now) {
        long elapsed = now - Mano_effectClient.seriouslyInjuredDustSpawnTime;

        if (elapsed >= SI_DUST_TOTAL_MS) {
            spawnNewSeriouslyInjuredDust(now);
            return;
        }

        if (elapsed < SI_DUST_FADE_IN_MS) {
            float t = (float) elapsed / SI_DUST_FADE_IN_MS;
            Mano_effectClient.seriouslyInjuredDustAlpha = t;
        } else if (elapsed < SI_DUST_FADE_IN_MS + SI_DUST_HOLD_MS) {
            Mano_effectClient.seriouslyInjuredDustAlpha = 1.0f;
        } else {
            float t = (float) (elapsed - SI_DUST_FADE_IN_MS - SI_DUST_HOLD_MS) / SI_DUST_FADE_OUT_MS;
            Mano_effectClient.seriouslyInjuredDustAlpha = 1.0f - t;
        }
    }

    // ===== Fracture blur =====

    @Unique
    private static void updateFractureBlur(boolean hasFracture, long now) {
        if (hasFracture) {
            if (!wasFractureBlurActive || fractureBlurLostTime > 0) {
                fractureBlurGainedTime = now;
                fractureBlurLostTime = 0;
                Mano_effectClient.fractureBlurTargetRadius = 3f + FRACTURE_RANDOM.nextFloat() * 5f;
                Mano_effectClient.fractureBlurChangeTimer = 0;
                Mano_effectClient.smoothBlurRadius = 0f;
                Mano_effectClient.fractureBlurActive = true;
            }

            long elapsed = now - fractureBlurGainedTime;
            if (elapsed < FRACTURE_BLUR_FADE_IN_MS) {
                float t = (float) elapsed / FRACTURE_BLUR_FADE_IN_MS;
                float eased = t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
                Mano_effectClient.smoothBlurRadius = eased * Mano_effectClient.fractureBlurTargetRadius;
            } else {
                Mano_effectClient.fractureBlurChangeTimer++;
                if (Mano_effectClient.fractureBlurChangeTimer >= 40) {
                    Mano_effectClient.fractureBlurChangeTimer = 0;
                    Mano_effectClient.fractureBlurTargetRadius = 3f + FRACTURE_RANDOM.nextFloat() * 5f;
                }
                Mano_effectClient.smoothBlurRadius = net.minecraft.util.Mth.lerp(
                    0.05f, Mano_effectClient.smoothBlurRadius, Mano_effectClient.fractureBlurTargetRadius
                );
            }

            wasFractureBlurActive = true;
        } else if (Mano_effectClient.fractureBlurActive) {
            if (fractureBlurLostTime == 0) {
                fractureBlurLostTime = now;
                Mano_effectClient.fractureBlurTargetRadius = Mano_effectClient.smoothBlurRadius;
            }
            fractureBlurGainedTime = 0;
            long elapsed = now - fractureBlurLostTime;
            if (elapsed < FRACTURE_BLUR_FADE_OUT_MS) {
                float t = (float) elapsed / FRACTURE_BLUR_FADE_OUT_MS;
                Mano_effectClient.smoothBlurRadius = Mano_effectClient.fractureBlurTargetRadius
                    * (float) Math.pow(1f - t, 3);
            } else {
                Mano_effectClient.smoothBlurRadius = 0f;
                Mano_effectClient.fractureBlurActive = false;
                fractureBlurLostTime = 0;
                wasFractureBlurActive = false;
            }
        }
    }

    // ===== Fire ash dust lifecycle =====

    @Unique
    private static final java.util.Random FIRE_DUST_RANDOM = new java.util.Random();
    @Unique
    private static long lastFireEnvironmentCheck = 0;
    @Unique
    private static boolean nearFire = false;

    @Unique
    private static void updateFireDust(long now, LivingEntity living) {
        if (living != null && (lastFireEnvironmentCheck == 0 || now - lastFireEnvironmentCheck >= 250)) {
            nearFire = isNearScreenEffectFire(living);
            lastFireEnvironmentCheck = now;
        }
        boolean onFire = living != null && (living.isOnFire() || nearFire);
        boolean startingFireEffect = onFire && Mano_effectClient.fireDustTargetAlpha < 0.5f;
        Mano_effectClient.fireDustTargetAlpha = onFire ? 1.0f : 0.0f;

        if (startingFireEffect) {
            Mano_effectClient.fireDustAlpha = Math.max(Mano_effectClient.fireDustAlpha, 0.35f);
            for (int i = 0; i < Mano_effectClient.MAX_FIRE_PARTICLES; i++) {
                spawnFireParticle(i, now - FIRE_DUST_RANDOM.nextInt(700));
            }
        }

        // Smooth fade in/out
        float speed = onFire ? 0.04f : 0.02f;
        Mano_effectClient.fireDustAlpha += (Mano_effectClient.fireDustTargetAlpha - Mano_effectClient.fireDustAlpha) * speed;
        if (Math.abs(Mano_effectClient.fireDustTargetAlpha - Mano_effectClient.fireDustAlpha) < 0.005f) {
            Mano_effectClient.fireDustAlpha = Mano_effectClient.fireDustTargetAlpha;
        }

        float overallAlpha = Mano_effectClient.fireDustAlpha;
        if (overallAlpha < 0.01f && !onFire) {
            // All done, mark particles dead
            for (int i = 0; i < Mano_effectClient.MAX_FIRE_PARTICLES; i++) {
                Mano_effectClient.fireParticlePhase[i] = 2;
                Mano_effectClient.fireParticleAlpha[i] = 0f;
            }
            return;
        }

        for (int i = 0; i < Mano_effectClient.MAX_FIRE_PARTICLES; i++) {
            int phase = Mano_effectClient.fireParticlePhase[i];

            switch (phase) {
                case 0: { // Rising from bottom
                    long elapsed = now - Mano_effectClient.fireParticleSpawnTime[i];
                    long life = Mano_effectClient.fireParticleLifeMs[i];
                    if (elapsed >= life) {
                        Mano_effectClient.fireParticlePhase[i] = 1; // start fading
                        Mano_effectClient.fireParticleSpawnTime[i] = now;
                        Mano_effectClient.fireParticleLifeMs[i] = 400 + FIRE_DUST_RANDOM.nextInt(600);
                    } else {
                        float t = (float) elapsed / life;
                        // Rise from bottom (y=1.0) to y=0.75~0.90
                        float targetY = 1.0f - (0.10f + (i * 0.03f)); // each particle stops at diff height
                        Mano_effectClient.fireParticleY[i] = 1.0f + (targetY - 1.0f) * t;
                        // Smooth sine wobble (NO random jitter)
                        float baseX = Mano_effectClient.fireParticleBaseX[i];
                        float wobble = (float) Math.sin(now * 0.0008f + i * 2.7f) * 0.015f;
                        Mano_effectClient.fireParticleX[i] = baseX + wobble;
                        // Clamp
                        if (Mano_effectClient.fireParticleX[i] < 0f) Mano_effectClient.fireParticleX[i] = 0f;
                        if (Mano_effectClient.fireParticleX[i] > 1f) Mano_effectClient.fireParticleX[i] = 1f;
                        // Alpha: stronger, peaks at 30% of life then holds
                        float peak = t < 0.3f ? t / 0.3f : 1.0f;
                        Mano_effectClient.fireParticleAlpha[i] = peak * overallAlpha;
                    }
                    break;
                }
                case 1: { // Fading out
                    long elapsed = now - Mano_effectClient.fireParticleSpawnTime[i];
                    long fadeMs = Mano_effectClient.fireParticleLifeMs[i];
                    if (elapsed >= fadeMs) {
                        Mano_effectClient.fireParticleAlpha[i] = 0f;
                        Mano_effectClient.fireParticlePhase[i] = 2;
                    } else {
                        float t = (float) elapsed / fadeMs;
                        Mano_effectClient.fireParticleAlpha[i] = overallAlpha * (1.0f - t);
                    }
                    break;
                }
                case 2: { // Dead — respawn if on fire
                    if (onFire) {
                        spawnFireParticle(i, now);
                    }
                    break;
                }
            }
        }
    }

    @Unique
    private static boolean isNearScreenEffectFire(LivingEntity living) {
        var origin = living.blockPosition();
        for (int y = -1; y <= 1; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BlockState state = living.level().getBlockState(origin.offset(x, y, z));
                    if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || isLitCampfire(state)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Unique
    private static boolean isLitCampfire(BlockState state) {
        return state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
    }

    @Unique
    private static void spawnFireParticle(int index, long now) {
        float bx = 0.08f + FIRE_DUST_RANDOM.nextFloat() * 0.84f;
        Mano_effectClient.fireParticleBaseX[index] = bx;
        Mano_effectClient.fireParticleX[index] = bx;
        Mano_effectClient.fireParticleY[index] = 0.92f;
        Mano_effectClient.fireParticleSize[index] = 10 + FIRE_DUST_RANDOM.nextInt(18);
        Mano_effectClient.fireParticleTextureIndex[index] = 3 + FIRE_DUST_RANDOM.nextInt(7);
        Mano_effectClient.fireParticleAlpha[index] = 0.25f;
        Mano_effectClient.fireParticleSpawnTime[index] = now;
        Mano_effectClient.fireParticleLifeMs[index] = 1500 + FIRE_DUST_RANDOM.nextInt(2500);
        Mano_effectClient.fireParticlePhase[index] = 0;
    }

    // ===== Rain droplet lifecycle (dynamic sliding droplets with trails) =====

    @Unique
    private static final java.util.Random RAIN_RANDOM = new java.util.Random();
    @Unique
    private static long lastRainDropUpdate = 0;
    @Unique
    private static final long RAIN_UPDATE_INTERVAL_MS = 33; // ~30fps update
    @Unique
    private static boolean wasUnderwater = false;
    @Unique
    private static boolean wasWet = false;

    @Unique
    private static void updateWetScreen(long now) {
        Minecraft mc = Minecraft.getInstance();
        boolean raining = mc.level != null
            && mc.level.isRainingAt(mc.gameRenderer.getMainCamera().getBlockPosition());
        var camera = mc.gameRenderer.getMainCamera();
        boolean underwater = camera.getFluidInCamera() == FogType.WATER
            || (mc.player != null && mc.player.isUnderWater());
        boolean wet = raining || underwater;

        if (underwater && !wasUnderwater) {
            Mano_effectClient.waterEnterTime = now;
            Mano_effectClient.waterExitTime = 0L;
        } else if (!underwater && wasUnderwater) {
            Mano_effectClient.waterExitTime = now;
        }
        wasUnderwater = underwater;

        Mano_effectClient.isRaining = wet;
        Mano_effectClient.underwater = underwater;

        float targetMuffle = underwater ? 1f : (raining ? 0.25f : 0f);
        float muffleSpeed = targetMuffle > Mano_effectClient.soundMuffleStrength ? 0.08f : 0.035f;
        Mano_effectClient.soundMuffleStrength +=
            (targetMuffle - Mano_effectClient.soundMuffleStrength) * muffleSpeed;
        if (Math.abs(targetMuffle - Mano_effectClient.soundMuffleStrength) < 0.003f) {
            Mano_effectClient.soundMuffleStrength = targetMuffle;
        }

        // Wet lens and sound state share a smooth transition.
        float targetRainAlpha = wet ? 1f : 0f;
        float fadeSpeed = wet ? 0.055f : 0.018f;
        Mano_effectClient.rainFadeAlpha += (targetRainAlpha - Mano_effectClient.rainFadeAlpha) * fadeSpeed;
        if (Math.abs(targetRainAlpha - Mano_effectClient.rainFadeAlpha) < 0.005f) {
            Mano_effectClient.rainFadeAlpha = targetRainAlpha;
        }

        // Smooth rain intensity
        float targetIntensity = 0f;
        if (wet && mc.level != null) {
            targetIntensity = underwater ? 1f : Math.max(0.35f, mc.level.getRainLevel(1.0f));
        }
        Mano_effectClient.rainIntensity += (targetIntensity - Mano_effectClient.rainIntensity) * 0.05f;
        if (Math.abs(targetIntensity - Mano_effectClient.rainIntensity) < 0.005f) {
            Mano_effectClient.rainIntensity = targetIntensity;
        }

        // Stay within the fixed storage and keep the overlay legible at high intensity.
        Mano_effectClient.wetScreenAlpha = Mano_effectClient.rainFadeAlpha;
        int targetCount;
        if (underwater) {
            targetCount = 3 + Math.round(Mano_effectClient.rainIntensity * 2f);
        } else if (raining) {
            targetCount = 6 + Math.round(Mano_effectClient.rainIntensity * 4f);
        } else {
            targetCount = 0;
        }
        if (wet && !wasWet) {
            Mano_effectClient.rainActiveCount = targetCount;
            for (int i = 0; i < targetCount; i++) {
                spawnVisibleRainDroplet(i, now - RAIN_RANDOM.nextInt(350));
            }
        } else {
            Mano_effectClient.rainActiveCount += (targetCount - Mano_effectClient.rainActiveCount) * 0.03f;
            if (Math.abs(targetCount - Mano_effectClient.rainActiveCount) < 0.5f) {
                Mano_effectClient.rainActiveCount = targetCount;
            }
        }
        wasWet = wet;
        int activeSlots = Math.round(Mano_effectClient.rainActiveCount);

        // Rain blur: misty softness (radius~3, no color separation, smooth fade)
        Mano_effectClient.rainTargetBlur = Mano_effectClient.rainIntensity * 3.0f;
        Mano_effectClient.rainBlurRadius += (Mano_effectClient.rainTargetBlur - Mano_effectClient.rainBlurRadius) * 0.06f;

        // Throttle droplet updates; prevent massive delta on first frame
        if (lastRainDropUpdate == 0) lastRainDropUpdate = now;
        long deltaMs = now - lastRainDropUpdate;
        if (deltaMs < RAIN_UPDATE_INTERVAL_MS) return;
        if (deltaMs > 200) deltaMs = 200; // clamp to prevent jumps after lag
        float dt = deltaMs / 1000f;
        lastRainDropUpdate = now;

        for (int i = 0; i < Mano_effectClient.MAX_RAIN_DROPLETS; i++) {
            int phase = Mano_effectClient.rainDropLifePhase[i];

            // Inactive slots beyond active count
            if (i >= activeSlots && phase != 4 && phase != 3) {
                Mano_effectClient.rainDropLifePhase[i] = 3;
                Mano_effectClient.rainDropSpawnTime[i] = now;
                Mano_effectClient.rainDropFadeOutMs[i] = 300;
            }

            if (!wet && phase == 4) {
                Mano_effectClient.rainDropAlpha[i] = 0f;
                continue;
            }

            switch (phase) {
                case 0: { // Appearing — quick fade in (rain hits glass)
                    long elapsed = now - Mano_effectClient.rainDropSpawnTime[i];
                    long appearMs = Mano_effectClient.rainDropPhaseDurationMs[i];
                    if (elapsed >= appearMs) {
                        Mano_effectClient.rainDropAlpha[i] = 0.6f;
                        Mano_effectClient.rainDropLifePhase[i] = 1; // start growing
                        Mano_effectClient.rainDropSpawnTime[i] = now;
                        Mano_effectClient.rainDropPhaseDurationMs[i] = 400 + RAIN_RANDOM.nextInt(1201);
                        Mano_effectClient.rainDropSize[i] = Mano_effectClient.rainDropTargetSize[i] * 0.35f;
                    } else {
                        float t = (float) elapsed / appearMs;
                        Mano_effectClient.rainDropAlpha[i] = t * 0.6f;
                        Mano_effectClient.rainDropSize[i] = Mano_effectClient.rainDropTargetSize[i] * (0.20f + 0.15f * t);
                    }
                    break;
                }
                case 1: { // Growing — stay in place, slowly get bigger
                    long elapsed = now - Mano_effectClient.rainDropSpawnTime[i];
                    float growDurationMs = Mano_effectClient.rainDropPhaseDurationMs[i];
                    float t = Math.min(1f, elapsed / growDurationMs);
                    float targetSize = Mano_effectClient.rainDropTargetSize[i];
                    Mano_effectClient.rainDropSize[i] = targetSize * (0.35f + 0.65f * t);
                    Mano_effectClient.rainDropAlpha[i] = 0.7f + t * 0.3f;

                    // Slight downward creep due to gravity
                    Mano_effectClient.rainDropPrevY[i] = Mano_effectClient.rainDropY[i];
                    Mano_effectClient.rainDropY[i] += dt * 0.003f * t; // barely moves until big

                    // When fully grown, start running
                    if (t >= 1.0f) {
                        Mano_effectClient.rainDropLifePhase[i] = 2;
                        Mano_effectClient.rainDropSpawnTime[i] = now;
                        Mano_effectClient.rainDropPrevX[i] = Mano_effectClient.rainDropX[i];
                        Mano_effectClient.rainDropPrevY[i] = Mano_effectClient.rainDropY[i];
                    }
                    break;
                }
                case 2: { // Running — dash downward quickly, leave visible trail
                    Mano_effectClient.rainDropPrevX[i] = Mano_effectClient.rainDropX[i];
                    Mano_effectClient.rainDropPrevY[i] = Mano_effectClient.rainDropY[i];

                    float speed = Mano_effectClient.rainDropRunSpeed[i];
                    Mano_effectClient.rainDropY[i] += speed * dt;
                    // Slight horizontal wobble while running
                    Mano_effectClient.rainDropX[i] += Math.sin(now * 0.003f + i * 1.1f) * 0.001f;

                    Mano_effectClient.rainDropAlpha[i] = 1.0f;

                    // Fell off bottom → fade
                    if (Mano_effectClient.rainDropY[i] > 1.05f) {
                        Mano_effectClient.rainDropLifePhase[i] = 3;
                        Mano_effectClient.rainDropSpawnTime[i] = now;
                        Mano_effectClient.rainDropFadeOutMs[i] = 500 + RAIN_RANDOM.nextInt(600);
                    }
                    break;
                }
                case 3: { // Fading out (trail dissipating)
                    long elapsed = now - Mano_effectClient.rainDropSpawnTime[i];
                    long fadeMs = Mano_effectClient.rainDropFadeOutMs[i];
                    if (elapsed >= fadeMs) {
                        Mano_effectClient.rainDropAlpha[i] = 0f;
                        Mano_effectClient.rainDropLifePhase[i] = 4;
                        Mano_effectClient.rainDropSpawnTime[i] = now;
                        Mano_effectClient.rainDropFadeOutMs[i] = 300 + RAIN_RANDOM.nextInt(1500);
                    } else {
                        float t = (float) elapsed / fadeMs;
                        Mano_effectClient.rainDropAlpha[i] = 1.0f - t;
                    }
                    break;
                }
                case 4: { // Respawning — wait, then appear somewhere new
                    if (!wet) {
                        Mano_effectClient.rainDropAlpha[i] = Math.max(0f, Mano_effectClient.rainDropAlpha[i] - dt * 0.5f);
                        continue;
                    }
                    long elapsed = now - Mano_effectClient.rainDropSpawnTime[i];
                    if (elapsed >= Mano_effectClient.rainDropFadeOutMs[i]) {
                        spawnRainDroplet(i, now);
                    }
                    break;
                }
            }
        }
    }

    @Unique
    private static void spawnRainDroplet(int index, long now) {
        // Appear at random screen position
        float sx = 0.05f + RAIN_RANDOM.nextFloat() * 0.9f;
        float sy = 0.05f + RAIN_RANDOM.nextFloat() * 0.7f;
        Mano_effectClient.rainDropX[index] = sx;
        Mano_effectClient.rainDropY[index] = sy;
        Mano_effectClient.rainDropPrevX[index] = sx;
        Mano_effectClient.rainDropPrevY[index] = sy;
        // Start with tiny but non-zero size to avoid visual pop
        float target = 0.006f + RAIN_RANDOM.nextFloat() * 0.016f;
        Mano_effectClient.rainDropTargetSize[index] = target;
        Mano_effectClient.rainDropSize[index] = target * 0.20f;
        Mano_effectClient.rainDropAlpha[index] = 0.25f;
        Mano_effectClient.rainDropRunSpeed[index] = 0.08f + RAIN_RANDOM.nextFloat() * 0.4f;
        Mano_effectClient.rainDropSpawnTime[index] = now;
        Mano_effectClient.rainDropPhaseDurationMs[index] = 80 + RAIN_RANDOM.nextInt(151);
        Mano_effectClient.rainDropLifePhase[index] = 0;
    }

    @Unique
    private static void spawnVisibleRainDroplet(int index, long now) {
        spawnRainDroplet(index, now);
        Mano_effectClient.rainDropSize[index] = Mano_effectClient.rainDropTargetSize[index]
            * (0.55f + RAIN_RANDOM.nextFloat() * 0.35f);
        Mano_effectClient.rainDropAlpha[index] = 0.75f;
        Mano_effectClient.rainDropSpawnTime[index] = now;
        Mano_effectClient.rainDropPhaseDurationMs[index] = 500 + RAIN_RANDOM.nextInt(900);
        Mano_effectClient.rainDropLifePhase[index] = 1;
    }

    // ===== Boost fade =====

    @Unique
    private static void updateBoostFade(boolean active, long now) {
        if (active) {
            if (!wasBoostActive) {
                boostLostTime = 0;
            }
            if (wasBoostActive && boostLostTime > 0) {
                boostLostTime = 0;
            }
            Mano_effectClient.smoothBoostAlpha = 1.0f;
            Mano_effectClient.boostActive = true;
            wasBoostActive = true;
        } else if (wasBoostActive) {
            if (boostLostTime == 0) {
                boostLostTime = now;
            }
            long elapsed = now - boostLostTime;
            if (elapsed >= FADE_OUT_MS) {
                Mano_effectClient.smoothBoostAlpha = 0.0f;
                Mano_effectClient.boostActive = false;
                wasBoostActive = false;
                boostLostTime = 0;
            } else {
                float t = (float) elapsed / FADE_OUT_MS;
                float alpha = (float) Math.pow(1f - t, 3);
                Mano_effectClient.smoothBoostAlpha = alpha;
            }
        }
    }
}
