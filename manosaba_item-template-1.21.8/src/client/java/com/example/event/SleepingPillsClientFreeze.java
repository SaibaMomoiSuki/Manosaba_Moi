// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.event;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class SleepingPillsClientFreeze {
    private static boolean keysUnbound;
    private static InputConstants.Key up, down, left, right;
    private static Field keyField;

    private SleepingPillsClientFreeze() {}

    public static void register() {
        try {
            keyField = KeyMapping.class.getDeclaredField("key");
            keyField.setAccessible(true);
        } catch (ReflectiveOperationException ignored) {
        }
        ClientTickEvents.START_CLIENT_TICK.register(SleepingPillsClientFreeze::tickStart);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && sleeping(client)) {
                client.player.xxa = 0.0F;
                client.player.zza = 0.0F;
                client.player.setSprinting(false);
                client.player.setDeltaMovement(0.0, Math.min(client.player.getDeltaMovement().y, 0.0), 0.0);
            }
        });
    }

    private static void tickStart(Minecraft client) {
        if (client.player == null || keyField == null) return;
        boolean sleeping = sleeping(client);
        try {
            if (sleeping && !keysUnbound) {
                up = bound(client.options.keyUp); down = bound(client.options.keyDown);
                left = bound(client.options.keyLeft); right = bound(client.options.keyRight);
                bind(client.options.keyUp, InputConstants.UNKNOWN); bind(client.options.keyDown, InputConstants.UNKNOWN);
                bind(client.options.keyLeft, InputConstants.UNKNOWN); bind(client.options.keyRight, InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
                keysUnbound = true;
            } else if (!sleeping && keysUnbound) {
                bind(client.options.keyUp, up); bind(client.options.keyDown, down);
                bind(client.options.keyLeft, left); bind(client.options.keyRight, right);
                KeyMapping.resetMapping();
                keysUnbound = false;
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    private static boolean sleeping(Minecraft client) {
        var holder = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.fromNamespaceAndPath("manosaba_item", "sleeping"));
        var instance = holder.map(client.player::getEffect).orElse(null);
        return instance != null;
    }

    private static InputConstants.Key bound(KeyMapping mapping) throws IllegalAccessException {
        return (InputConstants.Key) keyField.get(mapping);
    }

    private static void bind(KeyMapping mapping, InputConstants.Key key) throws IllegalAccessException {
        if (key != null) keyField.set(mapping, key);
    }
}
