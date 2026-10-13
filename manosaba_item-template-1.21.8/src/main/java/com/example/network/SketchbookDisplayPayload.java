// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SketchbookDisplayPayload(int pageIndex) implements CustomPacketPayload {
    public static final Type<SketchbookDisplayPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("manosaba_item", "sketchbook_display"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SketchbookDisplayPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, SketchbookDisplayPayload::pageIndex, SketchbookDisplayPayload::new);
    public int getPageIndex() { return pageIndex; }
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
