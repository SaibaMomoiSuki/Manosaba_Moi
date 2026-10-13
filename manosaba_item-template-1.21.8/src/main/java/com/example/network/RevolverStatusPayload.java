package com.example.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RevolverStatusPayload(boolean active, int ticks, boolean magazine) implements CustomPacketPayload {
    public static final Type<RevolverStatusPayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath("manosaba_item", "revolver_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RevolverStatusPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, RevolverStatusPayload::active,
        ByteBufCodecs.VAR_INT, RevolverStatusPayload::ticks,
        ByteBufCodecs.BOOL, RevolverStatusPayload::magazine,
        RevolverStatusPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
