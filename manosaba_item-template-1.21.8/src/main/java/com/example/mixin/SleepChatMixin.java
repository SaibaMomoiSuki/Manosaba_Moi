// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin;

import com.example.effect.ModEffects;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents players under the custom "sleeping" effect from hearing other
 * players' chat messages.
 *
 * <p>{@code ServerPlayer.sendChatMessage(OutgoingChatMessage, boolean,
 * ChatType.Bound)} is the single per-recipient delivery point that
 * {@code PlayerList.broadcastChatMessage} invokes for every recipient, so
 * cancelling it here stops the message from reaching a sleeping player.
 */
@Mixin(ServerPlayer.class)
public class SleepChatMixin {

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void manosaba$blockSleepingRecipients(
        OutgoingChatMessage message, boolean filtered, ChatType.Bound bound, CallbackInfo ci
    ) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (ModEffects.hasSleepingEffect(self)) {
            ci.cancel();
        }
    }
}
