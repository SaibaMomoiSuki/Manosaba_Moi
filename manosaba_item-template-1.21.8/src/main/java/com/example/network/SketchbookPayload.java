// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SketchbookPayload(String text) implements CustomPacketPayload {
    public static final Type<SketchbookPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("manosaba_item", "sketchbook_text"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SketchbookPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(500), SketchbookPayload::text, SketchbookPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    public String getText() { return text; }
}
