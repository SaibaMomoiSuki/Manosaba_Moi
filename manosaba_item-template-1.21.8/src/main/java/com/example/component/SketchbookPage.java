// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.ByteBuffer;
import java.util.Arrays;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public final class SketchbookPage {
    public static final int DRAWING_WIDTH = SketchbookDrawingData.WIDTH;
    public static final int DRAWING_HEIGHT = SketchbookDrawingData.HEIGHT;
    public static final int DRAWING_DATA_SIZE = SketchbookDrawingData.MAX_ENCODED_SIZE;

    public static final Codec<SketchbookPage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.string(0, 32).fieldOf("name").forGetter(SketchbookPage::getName),
        Codec.string(0, 7).fieldOf("mode").forGetter(SketchbookPage::getMode),
        Codec.string(0, 500).optionalFieldOf("textContent", "").forGetter(SketchbookPage::getTextContent),
        Codec.BYTE_BUFFER.xmap(SketchbookPage::readBytes, ByteBuffer::wrap)
            .optionalFieldOf("drawingData", new byte[0]).forGetter(SketchbookPage::getDrawingData)
    ).apply(instance, SketchbookPage::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SketchbookPage> STREAM_CODEC = StreamCodec.of(
        (buffer, page) -> {
            ByteBufCodecs.stringUtf8(32).encode(buffer, page.name);
            ByteBufCodecs.stringUtf8(7).encode(buffer, page.mode);
            ByteBufCodecs.stringUtf8(500).encode(buffer, page.textContent);
            ByteBufCodecs.byteArray(DRAWING_DATA_SIZE).encode(buffer, page.drawingData);
        },
        buffer -> new SketchbookPage(
            ByteBufCodecs.stringUtf8(32).decode(buffer),
            ByteBufCodecs.stringUtf8(7).decode(buffer),
            ByteBufCodecs.stringUtf8(500).decode(buffer),
            ByteBufCodecs.byteArray(DRAWING_DATA_SIZE).decode(buffer)
        )
    );

    private final String name;
    private final String mode;
    private final String textContent;
    private final byte[] drawingData;

    public SketchbookPage(String name, String mode, String textContent, byte[] drawingData) {
        this.name = name;
        this.mode = mode;
        this.textContent = textContent;
        this.drawingData = drawingData;
    }

    public String getName() { return name; }
    public String getMode() { return mode; }
    public String getTextContent() { return textContent; }
    public byte[] getDrawingData() { return drawingData; }

    public SketchbookPage copy(String name, String mode, String textContent, byte[] drawingData) {
        return new SketchbookPage(name, mode, textContent, drawingData);
    }

    private static byte[] readBytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof SketchbookPage other)) return false;
        return name.equals(other.name) && mode.equals(other.mode) && textContent.equals(other.textContent)
            && Arrays.equals(drawingData, other.drawingData);
    }

    @Override
    public int hashCode() {
        int result = 31 * (31 * name.hashCode() + mode.hashCode()) + textContent.hashCode();
        return 31 * result + Arrays.hashCode(drawingData);
    }
}
