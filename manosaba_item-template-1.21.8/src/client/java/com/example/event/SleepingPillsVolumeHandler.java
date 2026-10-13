// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.event;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

public final class SleepingPillsVolumeHandler {
    private static float originalVolume = -1.0F;
    private static int initialDuration = -1;
    private static boolean wasDarkened;

    private SleepingPillsVolumeHandler() {}

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(SleepingPillsVolumeHandler::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> restore(client));
    }

    private static void tick(Minecraft client) {
        if (client.player == null) return;
        var holder = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.fromNamespaceAndPath("manosaba_item", "sleeping"));
        if (holder.isEmpty()) return;
        var effect = client.player.getEffect(holder.get());
        boolean darkened = effect != null;
        if (darkened && !wasDarkened) {
            originalVolume = client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get().floatValue();
            initialDuration = effect.getDuration();
        }
        if (darkened) {
            int elapsed = initialDuration - effect.getDuration();
            float target = elapsed <= 600 ? lerp(originalVolume, 0.05F, elapsed / 600.0F)
                : elapsed <= 2200 ? 0.05F
                : lerp(0.05F, originalVolume, Math.clamp((elapsed - 2200) / 200.0F, 0.0F, 1.0F));
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set((double) target);
        } else if (wasDarkened) {
            restore(client);
        }
        wasDarkened = darkened;
    }

    private static void restore(Minecraft client) {
        if (originalVolume >= 0.0F) {
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set((double) originalVolume);
            originalVolume = -1.0F;
        }
        initialDuration = -1;
        wasDarkened = false;
    }

    private static float lerp(float from, float to, float value) {
        return from + (to - from) * value;
    }
}
