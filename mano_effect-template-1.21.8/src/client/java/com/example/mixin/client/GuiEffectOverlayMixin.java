// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin.client;

import com.example.Mano_effectClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/**
 * Renders radiation and radiation_boost screen overlays on the GUI layer.
 * All three effects (dizziness, radiation, radiation_boost) render simultaneously.
 */
@Mixin(Gui.class)
public class GuiEffectOverlayMixin {

    @Unique
    private static final Random RANDOM = new Random();

    @Unique
    private static final ResourceLocation VIGNETTE_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/misc/vignette.png");
    @Unique
    private static final ResourceLocation[] SPLAT_TEXTURES = {
        ResourceLocation.fromNamespaceAndPath("mano_effect", "blood_1"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "blood_2"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "blood_3"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "blood_4"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "blood_5"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "blood_6"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "blood_7"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "blood_8"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "blood_9"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "blood_10")
    };
    @Unique
    private static final ResourceLocation[] DUST_TEXTURES = {
        ResourceLocation.fromNamespaceAndPath("mano_effect", "dust_1"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "dust_2"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "dust_3"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "dust_4"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "dust_5"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "dust_6"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "dust_7"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "dust_8"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "dust_9"),
        ResourceLocation.fromNamespaceAndPath("mano_effect", "dust_10")
    };
    @Unique
    private static final int FIRST_CUSTOM_TEXTURE = 3;
    @Unique
    private static final int CUSTOM_TEXTURE_COUNT = 7;
    @Unique
    private static final int BLOOD_POOL_SIZE = 10;
    @Unique
    private static final float[] bloodPoolX = new float[BLOOD_POOL_SIZE];
    @Unique
    private static final float[] bloodPoolY = new float[BLOOD_POOL_SIZE];
    @Unique
    private static final float[] bloodPoolSize = new float[BLOOD_POOL_SIZE];
    @Unique
    private static final int[] bloodPoolTexture = new int[BLOOD_POOL_SIZE];
    @Unique
    private static final long[] bloodPoolSpawnTime = new long[BLOOD_POOL_SIZE];
    @Unique
    private static int nextBloodPoolSlot = 0;
    @Unique
    private static final int DUST_POOL_SIZE = 8;
    @Unique
    private static final float[][] dustPoolX = new float[3][DUST_POOL_SIZE];
    @Unique
    private static final float[][] dustPoolY = new float[3][DUST_POOL_SIZE];
    @Unique
    private static final float[][] dustPoolSize = new float[3][DUST_POOL_SIZE];
    @Unique
    private static final int[][] dustPoolTexture = new int[3][DUST_POOL_SIZE];
    @Unique
    private static final long[][] dustPoolSpawnTime = new long[3][DUST_POOL_SIZE];
    @Unique
    private static final long[] lastCapturedDustSpawn = new long[3];
    @Unique
    private static final int[] nextDustPoolSlot = new int[3];
    @Unique
    private static final long[] lastDustPoolSpawnTime = new long[3];
    @Unique
    private static final long POOLED_SPLAT_LIFE_MS = 9000;
    @Unique
    private static final long DUST_NEXT_SPAWN_MS = 6000;

    // Hit effect timing constants
    @Unique
    private static final long HIT_FADE_IN_MS = 300;
    @Unique
    private static final long HIT_HOLD_MS = 500;
    @Unique
    private static final long HIT_FADE_OUT_MS = 2500;
    @Unique
    private static final long HIT_TOTAL_MS = HIT_FADE_IN_MS + HIT_HOLD_MS + HIT_FADE_OUT_MS;

    @Inject(method = "render", at = @At("TAIL"))
    private void renderEffectOverlays(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float radAlpha = Mano_effectClient.smoothRadiationAlpha;
        float boostAlpha = Mano_effectClient.smoothBoostAlpha;
        float injuryDustAlpha = Mano_effectClient.smoothInjuryDustAlpha;
        float twistedBorderAlpha = Mano_effectClient.twistedAnkleBorderAlpha;
        float seriouslyInjuredDustAlpha = Mano_effectClient.smoothSeriouslyInjuredDustAlpha;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // ---- Radiation: noise + irregular dust + diagonal lines ----
        if (radAlpha > 0.005f) {
            renderNoise(graphics, sw, sh, radAlpha);
            renderDustPool(graphics, sw, sh, 0, radAlpha, Mano_effectClient.radiationActive,
                Mano_effectClient.dustSpawnTime, Mano_effectClient.dustX, Mano_effectClient.dustY,
                Mano_effectClient.dustSize, Mano_effectClient.dustTextureIndex, 0x202020, 110);
            renderRadiationLines(graphics, sw, sh, radAlpha);
        } else if (!Mano_effectClient.radiationActive) {
            clearDustPool(0);
        }

        // ---- Radiation Boost: single edge line per side, no white overlay ----
        if (boostAlpha > 0.005f) {
            renderEdgeLines(graphics, sw, sh, boostAlpha);
        }

        // ---- Injury: edge dust (dislocated_bone, fracture, seriously_injured) ----
        if (injuryDustAlpha > 0.005f) {
            boolean injuryActive = Mano_effectClient.dislocatedBoneActive
                || Mano_effectClient.fractureActive || Mano_effectClient.seriouslyInjuredActive
                || Mano_effectClient.dizzinessActive;
            renderDustPool(graphics, sw, sh, 1, injuryDustAlpha, injuryActive,
                Mano_effectClient.injuryDustSpawnTime, Mano_effectClient.injuryDustX, Mano_effectClient.injuryDustY,
                Mano_effectClient.injuryDustSize, Mano_effectClient.injuryDustTextureIndex, 0x171717, 175);
        } else if (!Mano_effectClient.dislocatedBoneActive
                && !Mano_effectClient.fractureActive && !Mano_effectClient.seriouslyInjuredActive
                && !Mano_effectClient.dizzinessActive) {
            clearDustPool(1);
        }

        // ---- Twisted Ankle: black border vignette (replaces edge dust) ----
        if (twistedBorderAlpha > 0.005f) {
            renderBlackBorderVignette(graphics, sw, sh, twistedBorderAlpha);
        }

        // ---- Seriously Injured: edge dust (additional dust layer) ----
        if (seriouslyInjuredDustAlpha > 0.005f) {
            int amp = Math.min(Mano_effectClient.seriouslyInjuredAmplifier, 3);
            renderDustPool(graphics, sw, sh, 2, seriouslyInjuredDustAlpha,
                Mano_effectClient.seriouslyInjuredActive,
                Mano_effectClient.seriouslyInjuredDustSpawnTime,
                Mano_effectClient.seriouslyInjuredDustX, Mano_effectClient.seriouslyInjuredDustY,
                Mano_effectClient.seriouslyInjuredDustSize * (1f + amp * 0.12f),
                Mano_effectClient.seriouslyInjuredDustTextureIndex, 0x111111, 115 + amp * 10);
        } else if (!Mano_effectClient.seriouslyInjuredActive) {
            clearDustPool(2);
        }

        // ---- Injury Hit: jump/land blur for dislocated_bone ----
        if (Mano_effectClient.injuryHitEffectActive) {
            long elapsed = System.currentTimeMillis() - Mano_effectClient.injuryHitEffectStartTime;
            // 0.2s fade in + 3s fade out
            long hitFadeInMs = 200;
            long hitFadeOutMs = 3000;
            long hitTotalMs = hitFadeInMs + hitFadeOutMs;
            if (elapsed >= hitTotalMs) {
                Mano_effectClient.injuryHitEffectActive = false;
            } else {
                float hitAlpha;
                if (elapsed < hitFadeInMs) {
                    hitAlpha = (float) elapsed / hitFadeInMs;
                } else {
                    hitAlpha = 1.0f - (float)(elapsed - hitFadeInMs) / hitFadeOutMs;
                }
                renderHitVignette(graphics, sw, sh, hitAlpha);
            }
        }

        // ---- Player Hit: screen-edge black dust (fade in → hold → fade out) ----
        if (Mano_effectClient.hitEffectActive) {
            long elapsed = System.currentTimeMillis() - Mano_effectClient.hitEffectStartTime;
            if (elapsed >= HIT_TOTAL_MS) {
                Mano_effectClient.hitEffectActive = false;
            } else {
                float hitAlpha;
                if (elapsed < HIT_FADE_IN_MS) {
                    hitAlpha = (float) elapsed / HIT_FADE_IN_MS;
                } else if (elapsed < HIT_FADE_IN_MS + HIT_HOLD_MS) {
                    hitAlpha = 1.0f;
                } else {
                    float t = (float)(elapsed - HIT_FADE_IN_MS - HIT_HOLD_MS) / HIT_FADE_OUT_MS;
                    hitAlpha = (1.0f - t) * (1.0f - t);
                }
                renderHitVignette(graphics, sw, sh, hitAlpha);
            }
        }

        // ---- Low Health: red edge vignette (persistent) + blood splats ----
        float lhAlpha = Mano_effectClient.lowHealthAlpha;
        float bsAlpha = Mano_effectClient.bloodSplatAlpha;
        int lhTier = Mano_effectClient.lowHealthTier;

        if (lhAlpha > 0.005f) {
            // Tier 1: narrower red edge (larger ellipse), Tier 2: same width as hit effect
            float ellipseScale = (lhTier >= 2) ? 0.32f : 0.40f;
            renderLowHealthVignette(graphics, sw, sh, lhAlpha, ellipseScale);
        }

        // ---- Fire ash dust: particles rising from bottom when on fire ----
        if (Mano_effectClient.fireDustAlpha > 0.005f) {
            renderFireDust(graphics, sw, sh);
        }

        // ---- Rain droplets: water traces on screen during rain ----
        if (Mano_effectClient.isRaining || Mano_effectClient.rainFadeAlpha > 0.005f) {
            renderRainBlur(graphics, sw, sh);
            renderWetRipples(graphics, sw, sh);
            renderRainDroplets(graphics, sw, sh);
        }

        // Blood splats: driven by alpha, so they fade out smoothly on heal
        if (bsAlpha > 0.02f) {
            if ((lhTier >= 2 || Mano_effectClient.bloodDamageBurstActive) && mc.player.isAlive()) {
                updateBloodSplatCycle(sw, sh);
            }
            renderBloodSplats(graphics, sw, sh, bsAlpha);
        } else if (lhTier == 0) {
            clearBloodPool();
        }
    }

    // ===== Radiation Lines: white 110° diagonal lines flashing in/out =====

    /** cos(110 degrees) ~= -0.3420, sin(110 degrees) ~= 0.9397. */
    @Unique
    private static final float RAD_LINE_DX = -0.3420f;
    @Unique
    private static final float RAD_LINE_DY = 0.9397f;

    @Unique
    private static void renderRadiationLines(GuiGraphics graphics, int sw, int sh, float radAlpha) {
        for (int i = 0; i < Mano_effectClient.MAX_RAD_LINES; i++) {
            float lineAlpha = Mano_effectClient.radLineAlpha[i] * radAlpha;
            if (lineAlpha < 0.02f) continue;

            float xn = Mano_effectClient.radLineX[i];
            float yn = Mano_effectClient.radLineY[i];
            float lenNorm = Mano_effectClient.radLineLength[i];

            int startX = (int) (xn * sw);
            int startY = (int) (yn * sh);
            int totalLen = (int) (lenNorm * sw);

            // 110° down-left diagonal (negative dx = left, positive dy = down)
            int endX = startX + (int) (totalLen * RAD_LINE_DX);
            int endY = startY + (int) (totalLen * RAD_LINE_DY);

            if (startX >= sw || startY >= sh) continue;

            int a = (int) (lineAlpha * 200);
            if (a < 4) continue;

            // A short segmented line keeps the taper while avoiding one draw per two pixels.
            int steps = Math.min(10, Math.max(4, totalLen / 16));
            float stepX = (float) (endX - startX) / steps;
            float stepY = (float) (endY - startY) / steps;

            // Random thin thickness from shared state
            float thickness = Mano_effectClient.radLineThickness[i];
            if (thickness <= 0f) thickness = 1.2f;
            int halfW = Math.round(thickness / 2f);
            if (halfW < 1) halfW = 1;

            for (int s = 0; s < steps; s++) {
                int sx = (int) (startX + stepX * s);
                int sy = (int) (startY + stepY * s);
                if (sx < 0 || sy < 0 || sx >= sw || sy >= sh) continue;

                // Brightness peaks in middle, tapers at ends
                float t = (float) s / steps;
                float endFade = 1.0f - 2.0f * Math.abs(t - 0.5f);
                endFade = Math.max(0.12f, endFade * endFade);
                int sa = (int) (a * endFade);
                if (sa < 3) continue;
                int sc = (Math.min(255, sa) << 24) | 0xFFFFFF;
                graphics.fill(sx - halfW, sy - halfW,
                    sx + halfW, sy + halfW, sc);
            }
        }
    }

    // ===== Fire Dust: ash particles rising from screen bottom =====

    @Unique
    private static void renderFireDust(GuiGraphics graphics, int sw, int sh) {
        float overallAlpha = Mano_effectClient.fireDustAlpha;
        if (overallAlpha < 0.01f) return;

        for (int i = 0; i < Mano_effectClient.MAX_FIRE_PARTICLES; i++) {
            float pa = Mano_effectClient.fireParticleAlpha[i];
            if (pa < 0.015f) continue;

            int px = (int) (Mano_effectClient.fireParticleX[i] * sw);
            int py = (int) (Mano_effectClient.fireParticleY[i] * sh);
            int psize = Math.max(5, (int) Mano_effectClient.fireParticleSize[i]);

            // Clamp to screen
            if (px < 0 || px >= sw || py < 0 || py >= sh) continue;

            int baseAlpha = (int) (pa * 190);
            if (baseAlpha < 3) continue;
            int emberAlpha = Math.min(255, (int) (pa * 205));
            graphics.fill(px - 1, py - 2, px + 2, py + 1,
                (emberAlpha << 24) | 0x8A8A8A);
            drawDustSplat(graphics, Mano_effectClient.fireParticleTextureIndex[i],
                px - psize / 2, py - psize / 2, psize, psize,
                (Math.min(255, baseAlpha) << 24) | 0xFFFFFF);
        }
    }

    // ===== Rain Droplets: dynamic sliding water traces on screen during rain =====

    /**
     * Rain haze — uniform atmospheric tint + scattered fine mist particles.
     * Simple, clean, no visible artifacts.
     */
    @Unique
    private static void renderRainBlur(GuiGraphics graphics, int sw, int sh) {
        float fadeAlpha = Mano_effectClient.rainFadeAlpha;
        if (fadeAlpha < 0.01f) return;

        float intensity = Mano_effectClient.rainIntensity * fadeAlpha;
        long now = System.currentTimeMillis();

        int r = 115, g = 135, b = 160;

        // Layer 1: uniform full-screen atmospheric tint (subtle gray-blue wash)
        int tintAlpha = (int) (intensity * 16);
        if (tintAlpha > 1) {
            int tintColor = (Math.min(255, tintAlpha) << 24) | (r << 16) | (g << 8) | b;
            graphics.fill(0, 0, sw, sh, tintColor);
        }

        // Layer 2: fine scattered mist specks — tiny dots slowly drifting
        int speckAlpha = (int) (intensity * 40);
        if (speckAlpha > 1) {
            long seed = now / 2000; // change seed slowly
            RANDOM.setSeed(seed);
            int speckCount = 8 + RANDOM.nextInt(7);
            for (int i = 0; i < speckCount; i++) {
                int sx = RANDOM.nextInt(sw);
                int sy = RANDOM.nextInt(sh);
                int sa = speckAlpha / 3 + RANDOM.nextInt(speckAlpha * 2 / 3 + 1);
                if (sa < 2) continue;
                int size = 1 + RANDOM.nextInt(2);
                int color = (Math.min(255, sa) << 24) | (r << 16) | (g << 8) | b;
                graphics.fill(sx, sy, sx + size, sy + size, color);
            }
        }
    }

    @Unique
    private static void renderWetRipples(GuiGraphics graphics, int sw, int sh) {
        float wet = Mano_effectClient.wetScreenAlpha;
        if (wet < 0.01f) return;
        if (Mano_effectClient.underwater) {
            graphics.fill(0, 0, sw, sh, ((int) (wet * 22) << 24) | 0x4B91B5);
        }
    }

    @Unique
    private static void renderRainDroplets(GuiGraphics graphics, int sw, int sh) {
        float fadeAlpha = Mano_effectClient.rainFadeAlpha;
        // Water colors: deep blue-gray body, darker trail
        int bodyR = 60, bodyG = 90, bodyB = 125;
        int trailR = 45, trailG = 72, trailB = 105;
        int hlR = 140, hlG = 170, hlB = 200; // highlight (bright spot)

        // Slots continue rendering while their global wet alpha eases out.
        for (int i = 0; i < Mano_effectClient.MAX_RAIN_DROPLETS; i++) {
            float alpha = Mano_effectClient.rainDropAlpha[i] * fadeAlpha;
            if (alpha < 0.015f) continue;

            float xn = Mano_effectClient.rainDropX[i];
            float yn = Mano_effectClient.rainDropY[i];
            float size = Mano_effectClient.rainDropSize[i] * sw;

            if (yn < -0.08f || yn > 1.04f) continue;

            int px = (int) (xn * sw);
            int py = (int) (yn * sh);
            int baseAlpha = (int) (alpha * 180);
            // Skip if too faint or too small to see
            if (baseAlpha < 3 || size < 1.0f) continue;

            // Teardrop shape based on current size (grows during phase 1, full during phase 2)
            int phase = Mano_effectClient.rainDropLifePhase[i];
            int dropRX = Math.max(1, (int) (size * 0.38f));
            int dropRY = Math.max(2, (int) (size * 0.7f));
            // Longer trail when running
            int trailLen = (phase == 2) ? Math.max(15, dropRY * 6) : Math.max(4, dropRY * 3);

            // Render a handful of gradient segments instead of allocating and drawing each pixel.
            long trailSeed = (long)(xn * 7919 + yn * 6271) * 31 + i * 773;
            RANDOM.setSeed(trailSeed);
            int segments = phase == 2 ? 5 : 3;
            for (int s = 0; s < segments; s++) {
                float t0 = (float) s / segments;
                float t1 = (float) (s + 1) / segments;
                int y0 = py - dropRY - Math.round(trailLen * t1);
                int y1 = py - dropRY - Math.round(trailLen * t0);
                int tw = Math.max(1, Math.round(dropRX * (1.0f - t0 * 0.65f)));
                int wobX = Math.round((RANDOM.nextFloat() - 0.5f) * 2.5f);
                int nearAlpha = (int) (baseAlpha * 0.50f * (1.0f - t0));
                int farAlpha = (int) (baseAlpha * 0.50f * (1.0f - t1));
                int nearColor = (Math.max(0, nearAlpha) << 24) | (trailR << 16) | (trailG << 8) | trailB;
                int farColor = (Math.max(0, farAlpha) << 24) | (trailR << 16) | (trailG << 8) | trailB;
                int clippedY0 = Math.max(0, y0);
                int clippedY1 = Math.min(sh, y1);
                if (clippedY1 > clippedY0) {
                    graphics.fillGradient(px - tw + wobX, clippedY0,
                        px + tw + 1 + wobX, clippedY1, farColor, nearColor);
                }
            }

            int bodyColor = (Math.min(255, baseAlpha) << 24) | (bodyR << 16) | (bodyG << 8) | bodyB;
            graphics.fill(px - dropRX, py - dropRY, px + dropRX + 1, py + dropRY / 3, bodyColor);
            graphics.fill(px - Math.max(1, dropRX / 2), py + dropRY / 3,
                px + Math.max(1, dropRX / 2) + 1, py + dropRY, bodyColor);
            int highlight = ((int) (baseAlpha * 0.42f) << 24) | (hlR << 16) | (hlG << 8) | hlB;
            graphics.fill(px - Math.max(1, dropRX / 2), py - dropRY / 2,
                px, py - dropRY / 2 + Math.max(1, dropRY / 3), highlight);
        }
    }

    // ===== Radiation Noise (TV static dots) — more visible =====

    @Unique
    private static void renderNoise(GuiGraphics graphics, int sw, int sh, float alpha) {
        long seed = System.currentTimeMillis() / 16;
        RANDOM.setSeed(seed);

        int dotCount = 24 + RANDOM.nextInt(13);
        int baseAlpha = (int) (alpha * 150); // max ~150/255 opacity

        for (int i = 0; i < dotCount; i++) {
            int x = RANDOM.nextInt(sw);
            int y = RANDOM.nextInt(sh);
            int dotSize = 1 + RANDOM.nextInt(4); // 1-4 pixels
            int dotAlpha = baseAlpha / 2 + RANDOM.nextInt(baseAlpha / 2 + 1);

            int gray = 140 + RANDOM.nextInt(70); // brighter gray 140-210
            int color = (dotAlpha << 24) | (gray << 16) | (gray << 8) | gray;

            graphics.fill(x, y, x + dotSize, y + dotSize, color);
        }
    }

    // ===== Radiation Dust (irregular blood-splat shape) — fixed visibility =====

    @Unique
    private static void renderDustPool(GuiGraphics graphics, int sw, int sh, int pool,
            float overallAlpha, boolean allowSpawning, long spawnTime, float x, float y, float size,
            int texture, int rgb, int maxAlpha) {
        long now = System.currentTimeMillis();
        if (spawnTime > 0 && spawnTime != lastCapturedDustSpawn[pool]) {
            lastCapturedDustSpawn[pool] = spawnTime;
        }

        if (allowSpawning && overallAlpha > 0.02f && (lastDustPoolSpawnTime[pool] == 0
                || now - lastDustPoolSpawnTime[pool] >= DUST_NEXT_SPAWN_MS)) {
            for (int i = 0; i < 2; i++) {
                float randomSize = fitSplatSize(sw, sh,
                    size * (0.82f + RANDOM.nextFloat() * 0.36f));
                int customTexture = FIRST_CUSTOM_TEXTURE + RANDOM.nextInt(CUSTOM_TEXTURE_COUNT);
                addDustSplat(sw, sh, pool, randomSize, customTexture, now);
            }
            lastDustPoolSpawnTime[pool] = now;
        }

        for (int i = 0; i < DUST_POOL_SIZE; i++) {
            long age = now - dustPoolSpawnTime[pool][i];
            if (dustPoolSpawnTime[pool][i] == 0 || age < 0 || age >= POOLED_SPLAT_LIFE_MS) continue;
            float lifeAlpha = age < 350 ? age / 350f
                : (float) Math.pow(1f - (age - 350f) / (POOLED_SPLAT_LIFE_MS - 350f), 1.45);
            float alpha = overallAlpha * lifeAlpha;
            if (alpha < 0.01f) continue;
            int pixelSize = Math.max(24, (int) (sw * dustPoolSize[pool][i]));
            int px = (int) (dustPoolX[pool][i] * sw) - pixelSize / 2;
            int py = (int) (dustPoolY[pool][i] * sh) - pixelSize / 2;
            drawDustSplat(graphics, dustPoolTexture[pool][i], px, py, pixelSize, pixelSize,
                ((int) (alpha * maxAlpha) << 24) | rgb);
        }
    }

    @Unique
    private static void addDustSplat(int sw, int sh, int pool, float size,
            int texture, long now) {
        int slot = nextDustPoolSlot[pool];
        float[] position = findUniqueSplatPosition(sw, sh, size, pool, slot, pool != 0);
        dustPoolX[pool][slot] = position[0];
        dustPoolY[pool][slot] = position[1];
        dustPoolSize[pool][slot] = size;
        dustPoolTexture[pool][slot] = texture;
        dustPoolSpawnTime[pool][slot] = now;
        nextDustPoolSlot[pool] = (slot + 1) % DUST_POOL_SIZE;
    }

    @Unique
    private static void clearDustPool(int pool) {
        java.util.Arrays.fill(dustPoolSpawnTime[pool], 0L);
        nextDustPoolSlot[pool] = 0;
        lastDustPoolSpawnTime[pool] = 0L;
        lastCapturedDustSpawn[pool] = 0L;
    }

    // ===== Radiation Boost: White line FULL edge, close to edge =====

    @Unique
    private static void renderEdgeLines(GuiGraphics graphics, int sw, int sh, float alpha) {
        int lineAlpha = (int) (alpha * 220);
        // Full edge coverage
        drawEdgeLineH(graphics, 0, 0, sw, 4, lineAlpha);           // Top: full width
        drawEdgeLineH(graphics, 0, sh - 4, sw, sh, lineAlpha);     // Bottom: full width
        drawEdgeLineV(graphics, 0, 0, 4, sh, lineAlpha);           // Left: full height
        drawEdgeLineV(graphics, sw - 4, 0, sw, sh, lineAlpha);     // Right: full height
    }

    /**
     * Horizontal edge line: fades inward (y1=edge, y2=inner).
     */
    @Unique
    private static void drawEdgeLineH(GuiGraphics graphics, int x1, int y1, int x2, int y2, int alpha) {
        int thick = y2 - y1;
        int a1 = alpha;           // outermost layer
        int a2 = alpha * 2 / 5;   // middle
        int a3 = alpha / 6;       // innermost (nearly transparent)

        int l1 = thick / 3;
        int l2 = thick * 2 / 3;

        graphics.fill(x1, y1,      x2, y1 + l1, (a1 << 24) | 0xFFFFFF);
        graphics.fill(x1, y1 + l1, x2, y1 + l2, (a2 << 24) | 0xFFFFFF);
        graphics.fill(x1, y1 + l2, x2, y2,       (a3 << 24) | 0xFFFFFF);
    }

    /**
     * Vertical edge line: fades inward (x1=edge, x2=inner).
     */
    @Unique
    private static void drawEdgeLineV(GuiGraphics graphics, int x1, int y1, int x2, int y2, int alpha) {
        int thick = x2 - x1;
        int a1 = alpha;           // outermost layer
        int a2 = alpha * 2 / 5;   // middle
        int a3 = alpha / 6;       // innermost (nearly transparent)

        int l1 = thick / 3;
        int l2 = thick * 2 / 3;

        graphics.fill(x1,      y1, x1 + l1, y2, (a1 << 24) | 0xFFFFFF);
        graphics.fill(x1 + l1, y1, x1 + l2, y2, (a2 << 24) | 0xFFFFFF);
        graphics.fill(x1 + l2, y1, x2,      y2, (a3 << 24) | 0xFFFFFF);
    }

    // ===== Player Hit: Gray edge vignette =====

    /**
     * Keeps damage feedback visually separate from the persistent low-health warning.
     */
    @Unique
    private static void renderHitVignette(GuiGraphics graphics, int sw, int sh, float alpha) {
        renderVanillaVignette(graphics, sw, sh, alpha, 0x505050, 140);
    }

    // ===== Low Health: Red edge vignette (same ellipse shape as hit vignette) =====

    @Unique
    private static void renderLowHealthVignette(GuiGraphics graphics, int sw, int sh,
            float alpha, float ellipseScale) {
        float strength = ellipseScale > 0.32f ? alpha * 0.82f : alpha;
        // VIGNETTE uses multiplicative inverse-color blending: cyan produces a red result.
        renderVanillaVignette(graphics, sw, sh, strength, 0x00FFFF, 125);
    }

    // ===== Low Health: textured blood splatter =====

    @Unique
    private static long lastBloodSpawnTime = 0;
    @Unique
    private static final long BLOOD_NEXT_SPAWN_MS = 8500;
    @Unique
    private static long bloodFadeStartTime = 0;
    @Unique
    private static final long BLOOD_FADE_IN_MS = 100;
    @Unique
    private static final long BLOOD_FADE_OUT_MS = 500;

    @Unique
    private static void updateBloodSplatCycle(int sw, int sh) {
        long now = System.currentTimeMillis();
        if (lastBloodSpawnTime == 0 || now - lastBloodSpawnTime >= BLOOD_NEXT_SPAWN_MS) {
            lastBloodSpawnTime = now;
            bloodFadeStartTime = now;
            int count = 2;
            for (int i = 0; i < count; i++) {
                Mano_effectClient.bloodSplatIndex = FIRST_CUSTOM_TEXTURE
                    + RANDOM.nextInt(CUSTOM_TEXTURE_COUNT);
                Mano_effectClient.bloodSplatSize = fitSplatSize(sw, sh,
                    0.10f + RANDOM.nextFloat() * 0.22f);
                int slot = nextBloodPoolSlot;
                float[] position = findUniqueBloodPosition(sw, sh, Mano_effectClient.bloodSplatSize, slot);
                Mano_effectClient.bloodSplatX = position[0];
                Mano_effectClient.bloodSplatY = position[1];
                bloodPoolX[slot] = Mano_effectClient.bloodSplatX;
                bloodPoolY[slot] = Mano_effectClient.bloodSplatY;
                bloodPoolSize[slot] = Mano_effectClient.bloodSplatSize;
                bloodPoolTexture[slot] = Mano_effectClient.bloodSplatIndex;
                bloodPoolSpawnTime[slot] = now;
                nextBloodPoolSlot = (slot + 1) % BLOOD_POOL_SIZE;
            }
        }
    }

    @Unique
    private static float[] findUniqueSplatPosition(int sw, int sh, float size,
            int pool, int replacingSlot, boolean nearEdge) {
        int pixelSize = Math.max(24, (int) (sw * size));
        float marginX = (pixelSize / 2f + 6f) / sw;
        float marginY = (pixelSize / 2f + 6f) / sh;
        float bestX = 0.5f;
        float bestY = 0.5f;
        float bestDistance = -1f;

        for (int attempt = 0; attempt < 16; attempt++) {
            float x;
            float y;
            if (nearEdge) {
                float alongX = randomBetween(marginX, 1f - marginX);
                float alongY = randomBetween(marginY, 1f - marginY);
                switch (RANDOM.nextInt(4)) {
                    case 0 -> { x = alongX; y = marginY; }
                    case 1 -> { x = alongX; y = 1f - marginY; }
                    case 2 -> { x = marginX; y = alongY; }
                    default -> { x = 1f - marginX; y = alongY; }
                }
            } else {
                x = randomBetween(marginX, 1f - marginX);
                y = randomBetween(marginY, 1f - marginY);
            }

            float distance = minimumDustDistance(sw, sh, pool, replacingSlot, x, y, pixelSize);
            if (distance >= 1f) return new float[] {x, y};
            if (distance > bestDistance) {
                bestDistance = distance;
                bestX = x;
                bestY = y;
            }
        }
        return new float[] {bestX, bestY};
    }

    @Unique
    private static float minimumDustDistance(int sw, int sh, int pool, int replacingSlot,
            float x, float y, int pixelSize) {
        long now = System.currentTimeMillis();
        float minimum = Float.MAX_VALUE;
        for (int i = 0; i < DUST_POOL_SIZE; i++) {
            if (i == replacingSlot || dustPoolSpawnTime[pool][i] == 0
                    || now - dustPoolSpawnTime[pool][i] >= POOLED_SPLAT_LIFE_MS) continue;
            float dx = (x - dustPoolX[pool][i]) * sw;
            float dy = (y - dustPoolY[pool][i]) * sh;
            float otherSize = dustPoolSize[pool][i] * sw;
            float required = (pixelSize + otherSize) * 0.38f;
            minimum = Math.min(minimum, (float) Math.sqrt(dx * dx + dy * dy) / required);
        }
        return minimum;
    }

    @Unique
    private static float[] findUniqueBloodPosition(int sw, int sh, float size, int replacingSlot) {
        int pixelSize = Math.max(24, (int) (sw * size));
        float marginX = (pixelSize / 2f + 6f) / sw;
        float marginY = (pixelSize / 2f + 6f) / sh;
        float bestX = 0.5f;
        float bestY = 0.5f;
        float bestDistance = -1f;
        long now = System.currentTimeMillis();

        for (int attempt = 0; attempt < 16; attempt++) {
            float x = randomBetween(marginX, 1f - marginX);
            float y = randomBetween(marginY, 1f - marginY);
            float minimum = Float.MAX_VALUE;
            for (int i = 0; i < BLOOD_POOL_SIZE; i++) {
                if (i == replacingSlot || bloodPoolSpawnTime[i] == 0
                        || now - bloodPoolSpawnTime[i] >= 12000) continue;
                float dx = (x - bloodPoolX[i]) * sw;
                float dy = (y - bloodPoolY[i]) * sh;
                float required = (pixelSize + bloodPoolSize[i] * sw) * 0.38f;
                minimum = Math.min(minimum, (float) Math.sqrt(dx * dx + dy * dy) / required);
            }
            if (minimum >= 1f) return new float[] {x, y};
            if (minimum > bestDistance) {
                bestDistance = minimum;
                bestX = x;
                bestY = y;
            }
        }
        return new float[] {bestX, bestY};
    }

    @Unique
    private static float randomBetween(float min, float max) {
        return max <= min ? 0.5f : min + RANDOM.nextFloat() * (max - min);
    }

    @Unique
    private static float fitSplatSize(int sw, int sh, float requestedSize) {
        int maxPixelSize = Math.max(24, Math.min(sw - 12, sh - 12));
        return Math.min(requestedSize, maxPixelSize / (float) sw);
    }

    @Unique
    private static void renderBloodSplats(GuiGraphics graphics, int sw, int sh, float alpha) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < BLOOD_POOL_SIZE; i++) {
            long age = now - bloodPoolSpawnTime[i];
            if (bloodPoolSpawnTime[i] == 0 || age < 0 || age >= 12000) continue;
            float lifeAlpha = age < BLOOD_FADE_IN_MS ? age / (float) BLOOD_FADE_IN_MS
                : (float) Math.pow(1f - (age - BLOOD_FADE_IN_MS) / 11900f, 1.25);
            float finalAlpha = alpha * lifeAlpha;
            if (finalAlpha < 0.02f) continue;
            int size = Math.max(24, (int) (sw * bloodPoolSize[i]));
            int x = (int) (bloodPoolX[i] * sw) - size / 2;
            int y = (int) (bloodPoolY[i] * sh) - size / 2;
            drawSplat(graphics, bloodPoolTexture[i], x, y, size, size,
                ((int) (finalAlpha * 220) << 24) | 0xFFFFFF);
        }
    }

    @Unique
    private static void clearBloodPool() {
        java.util.Arrays.fill(bloodPoolSpawnTime, 0L);
        nextBloodPoolSlot = 0;
        lastBloodSpawnTime = 0L;
    }

    @Unique
    private static void drawSplat(GuiGraphics graphics, int textureIndex,
            int x, int y, int width, int height, int color) {
        int index = Math.floorMod(textureIndex, SPLAT_TEXTURES.length);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SPLAT_TEXTURES[index],
            x, y, width, height, color);
    }

    @Unique
    private static void drawDustSplat(GuiGraphics graphics, int textureIndex,
            int x, int y, int width, int height, int color) {
        int index = Math.floorMod(textureIndex, DUST_TEXTURES.length);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DUST_TEXTURES[index],
            x, y, width, height, color);
    }

    @Unique
    private static void renderVanillaVignette(GuiGraphics graphics, int sw, int sh,
            float alpha, int rgb, int maxAlpha) {
        int a = Math.min(255, Math.max(0, (int) (alpha * maxAlpha)));
        if (a < 2) return;
        graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE_TEXTURE, 0, 0,
            0f, 0f, sw, sh, sw, sh, (a << 24) | rgb);
    }

    // ===== Twisted Ankle: Black border vignette (same screen range as hit damage effect) =====

    /**
     * Renders a black border vignette for twisted ankle.
     * Same elliptical clear-center shape as the hit damage effect, but in black.
     */
    @Unique
    private static void renderBlackBorderVignette(GuiGraphics graphics, int sw, int sh, float alpha) {
        renderVanillaVignette(graphics, sw, sh, alpha, 0x000000, 170);
    }

}
