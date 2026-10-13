// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BottleKeyPressPayload(boolean isAttacker) implements CustomPacketPayload {
    public static final Type<BottleKeyPressPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("manosaba_item", "bottle_key_press"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BottleKeyPressPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, BottleKeyPressPayload::isAttacker, BottleKeyPressPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    public boolean getIsAttacker() { return isAttacker; }
}
