// Copyright (c) 2026 Moi. Proprietary - Internal Use Only.
package com.example.mixin.client;

import com.example.audio.VoiceInputManager;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URI;

/**
 * Opens the Vosk model download links directly instead of showing Minecraft's
 * "open link?" confirmation screen. Only the two known download URLs are
 * bypassed; every other link keeps the default confirm-first behaviour.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void onHandleComponentClicked(Style style, CallbackInfoReturnable<Boolean> cir) {
        if (style == null) return;
        ClickEvent event = style.getClickEvent();
        if (event == null || event.action() != ClickEvent.Action.OPEN_URL) return;
        URI uri = ((ClickEvent.OpenUrl) event).uri();
        if (!VoiceInputManager.INSTANCE.isModelDownloadUrl(uri.toString())) return;
        Util.getPlatform().openUri(uri);
        cir.setReturnValue(true);
    }
}
