// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.network;

import com.example.component.SketchbookPage;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SketchbookUpdatePayload(List<SketchbookPage> pages, List<Integer> colors, int prevColor, int displayPage)
    implements CustomPacketPayload {
    public static final Type<SketchbookUpdatePayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("manosaba_item", "sketchbook_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SketchbookUpdatePayload> STREAM_CODEC = StreamCodec.composite(
        SketchbookPage.STREAM_CODEC.apply(ByteBufCodecs.list(100)), SketchbookUpdatePayload::pages,
        ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(20)), SketchbookUpdatePayload::colors,
        ByteBufCodecs.VAR_INT, SketchbookUpdatePayload::prevColor,
        ByteBufCodecs.VAR_INT, SketchbookUpdatePayload::displayPage,
        SketchbookUpdatePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    public List<SketchbookPage> getPages() { return pages; }
    public List<Integer> getColors() { return colors; }
    public int getPrevColor() { return prevColor; }
    public int getDisplayPage() { return displayPage; }
}
