// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin;

import com.example.item.custom.AnanBookItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents server-side hotbar slot changes when holding an open sketchbook.
 * Catches the SetCarriedItem packet before it processes.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class SketchbookServerLockMixin {

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void onSetCarriedItem(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl handler = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = handler.player;

        if (AnanBookItem.isHoldingOpenSketchbook(player)) {
            ci.cancel();
        }
    }
}
