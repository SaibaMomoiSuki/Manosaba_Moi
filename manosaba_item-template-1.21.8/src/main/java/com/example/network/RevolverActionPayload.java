package com.example.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RevolverActionPayload(int action) implements CustomPacketPayload {
    public static final Type<RevolverActionPayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath("manosaba_item", "revolver_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RevolverActionPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, RevolverActionPayload::action, RevolverActionPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
