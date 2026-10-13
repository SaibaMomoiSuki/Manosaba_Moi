/*
 * Copyright (c) 2026 Momoi. Proprietary - Internal Use Only.
 * 内部私有，严禁任何形式使用（包括游玩、复制、分发）。
 */
package com.example.vision_optimize.mixin.client;

import com.example.vision_optimize.client.ClientVisionState;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side Mixin to suppress action-bar tellraw messages
 * during player vision fail/discovery windows.
 */
@Mixin(Gui.class)
public class GuiMixin {

    /**
     * Intercept setOverlayMessage and cancel if tellraw is currently suppressed.
     */
    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void visionOptimize$onSetOverlayMessage(Component component, boolean tinted, CallbackInfo ci) {
        if (ClientVisionState.INSTANCE.isTellrawSuppressed()) {
            ci.cancel();
        }
    }
}
