// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example;

import com.example.client.SketchbookScreen;
import com.example.client.RevolverClientState;
import com.example.component.ModDataComponents;
import com.example.event.BottleStruggleClientHandler;
import com.example.event.SleepingPillsClientFreeze;
import com.example.event.SleepingPillsVolumeHandler;
import com.example.item.custom.AnanBookItem;
import com.example.network.SketchbookEditPayload;
import com.example.network.RevolverActionPayload;
import com.example.network.RevolverStatusPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class Manosaba_ItemClient implements ClientModInitializer {
    private static final KeyMapping SKETCHBOOK_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.manosaba_item.sketchbook", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L,
        "category.manosaba_item.general"));
    private static final KeyMapping SKETCHBOOK_MOUSE = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.manosaba_item.sketchbook_mouse", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
        "category.manosaba_item.general"));
    private static final KeyMapping SKETCHBOOK_DISPLAY_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.manosaba_item.sketchbook_display", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_ENTER,
        "category.manosaba_item.general"));
    private static final KeyMapping REVOLVER_RELOAD_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.manosaba_item.revolver_reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
        "category.manosaba_item.general"));
    private static final KeyMapping REVOLVER_INSPECT_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.manosaba_item.revolver_inspect", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F,
        "category.manosaba_item.general"));

    public static boolean isSketchbookDisplayKey(int keyCode, int scanCode) {
        return SKETCHBOOK_DISPLAY_KEY.matches(keyCode, scanCode);
    }

    @Override
    public void onInitializeClient() {
        System.setProperty("java.awt.headless", "false");
        SleepingPillsClientFreeze.register();
        SleepingPillsVolumeHandler.register();
        BottleStruggleClientHandler.register();
        ClientPlayNetworking.registerGlobalReceiver(SketchbookEditPayload.ID, (payload, context) ->
            context.client().execute(() -> SketchbookScreen.saveAndDisplayText(payload.pageIndex(), payload.text())));
        ClientPlayNetworking.registerGlobalReceiver(RevolverStatusPayload.ID, (payload, context) ->
            context.client().execute(() -> {
                RevolverClientState.update(payload.active(), payload.ticks(), payload.magazine());
                if (context.client().player == null) return;
                if (payload.active() && (context.client().player.getMainHandItem().is(com.example.item.ModItems.ZUOLUN)
                    || context.client().player.getMainHandItem().is(com.example.item.ModItems.ROTARY_MAGAZINE))) {
                    context.client().player.startUsingItem(InteractionHand.MAIN_HAND);
                } else if (!payload.active()) {
                    context.client().player.stopUsingItem();
                }
            }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && RevolverClientState.isReloading()) {
                client.player.zza *= 0.2F;
                client.player.xxa *= 0.2F;
                client.player.setDeltaMovement(client.player.getDeltaMovement().multiply(0.2, 1.0, 0.2));
            }
            while (REVOLVER_RELOAD_KEY.consumeClick()) {
                if (client.player != null && client.screen == null && (client.player.getMainHandItem().is(com.example.item.ModItems.ZUOLUN)
                    || client.player.getMainHandItem().is(com.example.item.ModItems.ROTARY_MAGAZINE)))
                    ClientPlayNetworking.send(new RevolverActionPayload(0));
            }
            while (REVOLVER_INSPECT_KEY.consumeClick()) {
                if (client.player != null && client.screen == null && client.player.getMainHandItem().is(com.example.item.ModItems.ZUOLUN))
                    ClientPlayNetworking.send(new RevolverActionPayload(1));
            }
            while (SKETCHBOOK_KEY.consumeClick() || SKETCHBOOK_MOUSE.consumeClick()) {
                if (client.player == null || client.screen != null) continue;
                ItemStack main = client.player.getMainHandItem();
                ItemStack off = client.player.getOffhandItem();
                ItemStack book = main.getItem() instanceof AnanBookItem ? main
                    : off.getItem() instanceof AnanBookItem ? off : ItemStack.EMPTY;
                if (!book.isEmpty() && Boolean.TRUE.equals(book.get(ModDataComponents.SKETCHBOOK_OPEN))) {
                    client.setScreen(new SketchbookScreen());
                }
            }
        });
    }
}
