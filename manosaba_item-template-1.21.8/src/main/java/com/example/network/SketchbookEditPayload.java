// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SketchbookEditPayload(int pageIndex, String text) implements CustomPacketPayload {
    public static final Type<SketchbookEditPayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath("manosaba_item", "sketchbook_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SketchbookEditPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, SketchbookEditPayload::pageIndex,
        ByteBufCodecs.stringUtf8(256), SketchbookEditPayload::text,
        SketchbookEditPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
