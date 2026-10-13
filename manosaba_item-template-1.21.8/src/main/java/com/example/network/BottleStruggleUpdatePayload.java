// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BottleStruggleUpdatePayload(float progress, int attackerPresses, int victimPresses, boolean reachedThreshold)
    implements CustomPacketPayload {
    public static final Type<BottleStruggleUpdatePayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("manosaba_item", "bottle_struggle_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BottleStruggleUpdatePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, BottleStruggleUpdatePayload::progress,
        ByteBufCodecs.VAR_INT, BottleStruggleUpdatePayload::attackerPresses,
        ByteBufCodecs.VAR_INT, BottleStruggleUpdatePayload::victimPresses,
        ByteBufCodecs.BOOL, BottleStruggleUpdatePayload::reachedThreshold,
        BottleStruggleUpdatePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    public float getProgress() { return progress; }
    public int getAttackerPresses() { return attackerPresses; }
    public int getVictimPresses() { return victimPresses; }
    public boolean getReachedThreshold() { return reachedThreshold; }
}
