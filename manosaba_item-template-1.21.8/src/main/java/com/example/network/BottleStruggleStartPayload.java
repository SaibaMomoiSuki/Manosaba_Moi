// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.network;

import java.util.UUID;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BottleStruggleStartPayload(UUID attackerId, UUID victimId) implements CustomPacketPayload {
    public static final Type<BottleStruggleStartPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("manosaba_item", "bottle_struggle_start"));
    private static final StreamCodec<ByteBuf, UUID> UUID_CODEC = StreamCodec.of(
        (buffer, uuid) -> { buffer.writeLong(uuid.getMostSignificantBits()); buffer.writeLong(uuid.getLeastSignificantBits()); },
        buffer -> new UUID(buffer.readLong(), buffer.readLong()));
    public static final StreamCodec<RegistryFriendlyByteBuf, BottleStruggleStartPayload> STREAM_CODEC = StreamCodec.composite(
        UUID_CODEC, BottleStruggleStartPayload::attackerId, UUID_CODEC, BottleStruggleStartPayload::victimId,
        BottleStruggleStartPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    public UUID getAttackerId() { return attackerId; }
    public UUID getVictimId() { return victimId; }
}
