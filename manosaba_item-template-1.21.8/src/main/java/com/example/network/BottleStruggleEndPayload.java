// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BottleStruggleEndPayload(boolean attackerWon) implements CustomPacketPayload {
    public static final Type<BottleStruggleEndPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("manosaba_item", "bottle_struggle_end"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BottleStruggleEndPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, BottleStruggleEndPayload::attackerWon, BottleStruggleEndPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    public boolean getAttackerWon() { return attackerWon; }
}
