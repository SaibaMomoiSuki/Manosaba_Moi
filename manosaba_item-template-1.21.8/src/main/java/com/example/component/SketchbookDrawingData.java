// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class SketchbookDrawingData {
    private static final int MAGIC = 0x4D534432; // MSD2
    private static final int LAYERED_MAGIC = 0x4D534433; // MSD3
    private static final int IMAGE_LAYERED_MAGIC = 0x4D534434; // MSD4
    private static final int MULTI_LAYER_MAGIC = 0x4D534435; // MSD5
    private static final AtomicLong NEXT_ID = new AtomicLong(System.nanoTime());
    public static final int MAX_PIXEL_LAYERS = 16;
    public static final int MAX_TEXT_ELEMENTS = 64;
    public static final int MAX_TEXT_LENGTH = 256;
    public static final int MAX_IMAGE_ELEMENTS = 16;
    public static final int MAX_IMAGE_SOURCE_PIXELS = 300_000;
    public static final int GRID_WIDTH = 112;
    public static final int GRID_HEIGHT = 84;
    public static final int WIDTH = GRID_WIDTH * 4;
    public static final int HEIGHT = GRID_HEIGHT * 4;
    public static final int MAX_PIXELS = 500 * 400;
    public static final int MAX_ENCODED_SIZE = 1_500_000;

    private SketchbookDrawingData() {}

    public static long newId() {
        return NEXT_ID.incrementAndGet();
    }

    public static byte[] encode(int[] pixels, int width, int height) {
        return encode(pixels, width, height, List.of(), List.of());
    }

    public static byte[] encode(int[] pixels, int width, int height, List<TextElement> texts) {
        return encode(pixels, width, height, texts, List.of());
    }

    public static byte[] encode(int[] pixels, int width, int height, List<TextElement> texts, List<ImageElement> images) {
        PixelLayer pixelLayer = new PixelLayer(newId(), pixels);
        List<LayerRef> order = new ArrayList<>();
        order.add(new LayerRef(LayerType.PIXEL, pixelLayer.id()));
        images.forEach(image -> order.add(new LayerRef(LayerType.IMAGE, image.id())));
        texts.forEach(text -> order.add(new LayerRef(LayerType.TEXT, text.id())));
        return encode(width, height, List.of(pixelLayer), texts, images, order);
    }

    public static byte[] encode(int width, int height, List<PixelLayer> pixelLayers, List<TextElement> texts,
                                List<ImageElement> images, List<LayerRef> layerOrder) {
        validate(width, height, pixelLayers, texts, images, layerOrder);
        byte[] raw;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(width * height * Integer.BYTES);
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(pixelLayers.size());
            for (PixelLayer layer : pixelLayers) {
                output.writeLong(layer.id());
                for (int pixel : layer.pixels()) output.writeInt(pixel);
            }
            output.writeInt(texts.size());
            for (TextElement text : texts) {
                byte[] content = text.text().getBytes(StandardCharsets.UTF_8);
                output.writeLong(text.id());
                output.writeInt(text.x()); output.writeInt(text.y()); output.writeInt(text.width());
                output.writeInt(text.height()); output.writeInt(text.fontSize()); output.writeInt(text.color());
                output.writeShort(content.length); output.write(content);
            }
            output.writeInt(images.size());
            for (ImageElement image : images) {
                output.writeLong(image.id());
                output.writeInt(image.x()); output.writeInt(image.y()); output.writeInt(image.width());
                output.writeInt(image.height()); output.writeInt(image.sourceWidth()); output.writeInt(image.sourceHeight());
                for (int pixel : image.pixels()) output.writeInt(pixel);
            }
            output.writeInt(layerOrder.size());
            for (LayerRef layer : layerOrder) {
                output.writeByte(layer.type().ordinal());
                output.writeLong(layer.id());
            }
            raw = bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(raw.length / 4);
        byte[] chunk = new byte[8192];
        while (!deflater.finished()) {
            int count = deflater.deflate(chunk);
            if (count <= 0) break;
            compressed.write(chunk, 0, count);
            if (compressed.size() > MAX_ENCODED_SIZE - 12) {
                deflater.end();
                throw new IllegalArgumentException("Sketchbook drawing is too complex to store");
            }
        }
        deflater.end();
        byte[] body = compressed.toByteArray();
        return ByteBuffer.allocate(12 + body.length).putInt(MULTI_LAYER_MAGIC).putShort((short) width)
            .putShort((short) height).putInt(raw.length).put(body).array();
    }

    public static Drawing decode(byte[] data) {
        try {
            return decodeUnchecked(data);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Drawing decodeUnchecked(byte[] data) {
        if (data == null || data.length < 4 || data.length > MAX_ENCODED_SIZE) return null;
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int magic = data.length >= 12 ? buffer.getInt(0) : 0;
        if (magic == MAGIC || magic == LAYERED_MAGIC || magic == IMAGE_LAYERED_MAGIC || magic == MULTI_LAYER_MAGIC) {
            buffer.getInt();
            int width = Short.toUnsignedInt(buffer.getShort());
            int height = Short.toUnsignedInt(buffer.getShort());
            int rawLength = buffer.getInt();
            long maxRawLength = MAX_PIXEL_LAYERS * ((long) width * height * Integer.BYTES + Long.BYTES)
                + MAX_TEXT_ELEMENTS * (MAX_TEXT_LENGTH * 4 + 40)
                + MAX_IMAGE_SOURCE_PIXELS * Integer.BYTES + MAX_IMAGE_ELEMENTS * 36
                + (MAX_PIXEL_LAYERS + MAX_TEXT_ELEMENTS + MAX_IMAGE_ELEMENTS) * 9 + 16;
            if (!validDimensions(width, height) || rawLength < 0 || rawLength > maxRawLength) return null;
            byte[] compressed = new byte[buffer.remaining()];
            buffer.get(compressed);
            byte[] raw = inflate(compressed, rawLength);
            if (raw == null) return null;
            return magic == MULTI_LAYER_MAGIC ? decodeMsd5(ByteBuffer.wrap(raw), width, height)
                : decodeLegacyLayered(ByteBuffer.wrap(raw), magic, width, height);
        }

        int width = Short.toUnsignedInt(buffer.getShort());
        int height = Short.toUnsignedInt(buffer.getShort());
        if (!validDimensions(width, height) || buffer.remaining() != width * height * Integer.BYTES) return null;
        return legacyDrawing(width, height, readPixels(buffer, width * height), List.of(), List.of());
    }

    private static Drawing decodeMsd5(ByteBuffer raw, int width, int height) {
        if (raw.remaining() < Integer.BYTES) return null;
        int pixelLayerCount = raw.getInt();
        if (pixelLayerCount < 1 || pixelLayerCount > MAX_PIXEL_LAYERS) return null;
        List<PixelLayer> pixelLayers = new ArrayList<>(pixelLayerCount);
        for (int i = 0; i < pixelLayerCount; i++) {
            if (raw.remaining() < Long.BYTES + width * height * Integer.BYTES) return null;
            pixelLayers.add(new PixelLayer(raw.getLong(), readPixels(raw, width * height)));
        }
        List<TextElement> texts = readTexts(raw, width, height, true);
        if (texts == null) return null;
        List<ImageElement> images = readImages(raw, width, height, true);
        if (images == null || raw.remaining() < Integer.BYTES) return null;
        int orderCount = raw.getInt();
        if (orderCount != pixelLayers.size() + texts.size() + images.size() || raw.remaining() != orderCount * 9) return null;
        List<LayerRef> order = new ArrayList<>(orderCount);
        for (int i = 0; i < orderCount; i++) {
            int type = Byte.toUnsignedInt(raw.get());
            if (type >= LayerType.values().length) return null;
            order.add(new LayerRef(LayerType.values()[type], raw.getLong()));
        }
        try {
            validate(width, height, pixelLayers, texts, images, order);
            return new Drawing(width, height, List.copyOf(pixelLayers), List.copyOf(texts), List.copyOf(images), List.copyOf(order));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Drawing decodeLegacyLayered(ByteBuffer raw, int magic, int width, int height) {
        if (raw.remaining() < width * height * Integer.BYTES) return null;
        int[] pixels = readPixels(raw, width * height);
        if (magic == MAGIC) return raw.hasRemaining() ? null : legacyDrawing(width, height, pixels, List.of(), List.of());
        List<TextElement> texts = readTexts(raw, width, height, false);
        if (texts == null) return null;
        if (magic == LAYERED_MAGIC) return raw.hasRemaining() ? null : legacyDrawing(width, height, pixels, texts, List.of());
        List<ImageElement> images = readImages(raw, width, height, false);
        return images == null || raw.hasRemaining() ? null : legacyDrawing(width, height, pixels, texts, images);
    }

    private static List<TextElement> readTexts(ByteBuffer raw, int width, int height, boolean ids) {
        if (raw.remaining() < Integer.BYTES) return null;
        int count = raw.getInt();
        if (count < 0 || count > MAX_TEXT_ELEMENTS) return null;
        List<TextElement> texts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (raw.remaining() < (ids ? 34 : 26)) return null;
            long id = ids ? raw.getLong() : newId();
            int x = raw.getInt(), y = raw.getInt(), elementWidth = raw.getInt(), elementHeight = raw.getInt();
            int fontSize = raw.getInt(), color = raw.getInt(), contentLength = Short.toUnsignedInt(raw.getShort());
            if (contentLength > raw.remaining()) return null;
            byte[] content = new byte[contentLength]; raw.get(content);
            TextElement text = new TextElement(id, new String(content, StandardCharsets.UTF_8), x, y,
                elementWidth, elementHeight, fontSize, color);
            if (!validText(text, width, height)) return null;
            texts.add(text);
        }
        return List.copyOf(texts);
    }

    private static List<ImageElement> readImages(ByteBuffer raw, int width, int height, boolean ids) {
        if (raw.remaining() < Integer.BYTES) return null;
        int count = raw.getInt();
        if (count < 0 || count > MAX_IMAGE_ELEMENTS) return null;
        int totalPixels = 0;
        List<ImageElement> images = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (raw.remaining() < (ids ? 32 : 24)) return null;
            long id = ids ? raw.getLong() : newId();
            int x = raw.getInt(), y = raw.getInt(), elementWidth = raw.getInt(), elementHeight = raw.getInt();
            int sourceWidth = raw.getInt(), sourceHeight = raw.getInt();
            if (sourceWidth <= 0 || sourceHeight <= 0 || sourceWidth > 512 || sourceHeight > 512) return null;
            int sourcePixels = sourceWidth * sourceHeight;
            totalPixels += sourcePixels;
            if (totalPixels > MAX_IMAGE_SOURCE_PIXELS || raw.remaining() < sourcePixels * Integer.BYTES) return null;
            ImageElement image = new ImageElement(id, x, y, elementWidth, elementHeight, sourceWidth, sourceHeight,
                readPixels(raw, sourcePixels));
            if (!validImage(image, width, height)) return null;
            images.add(image);
        }
        return List.copyOf(images);
    }

    private static Drawing legacyDrawing(int width, int height, int[] pixels, List<TextElement> texts, List<ImageElement> images) {
        PixelLayer pixelLayer = new PixelLayer(newId(), pixels);
        List<LayerRef> order = new ArrayList<>();
        order.add(new LayerRef(LayerType.PIXEL, pixelLayer.id()));
        images.forEach(image -> order.add(new LayerRef(LayerType.IMAGE, image.id())));
        texts.forEach(text -> order.add(new LayerRef(LayerType.TEXT, text.id())));
        return new Drawing(width, height, List.of(pixelLayer), texts, images, List.copyOf(order));
    }

    private static byte[] inflate(byte[] compressed, int rawLength) {
        byte[] raw = new byte[rawLength];
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        try {
            int written = 0;
            while (!inflater.finished() && written < raw.length) {
                int count = inflater.inflate(raw, written, raw.length - written);
                if (count == 0) break;
                written += count;
            }
            return written == rawLength && inflater.finished() ? raw : null;
        } catch (DataFormatException exception) {
            return null;
        } finally {
            inflater.end();
        }
    }

    private static void validate(int width, int height, List<PixelLayer> pixelLayers, List<TextElement> texts,
                                 List<ImageElement> images, List<LayerRef> order) {
        if (!validDimensions(width, height) || pixelLayers == null || pixelLayers.isEmpty()
            || pixelLayers.size() > MAX_PIXEL_LAYERS || pixelLayers.stream().anyMatch(layer -> layer == null
                || layer.pixels() == null || layer.pixels().length != width * height)) {
            throw new IllegalArgumentException("Invalid sketchbook pixel layers");
        }
        if (texts == null || texts.size() > MAX_TEXT_ELEMENTS || texts.stream().anyMatch(text -> !validText(text, width, height)))
            throw new IllegalArgumentException("Invalid sketchbook text elements");
        if (images == null || images.size() > MAX_IMAGE_ELEMENTS || images.stream().anyMatch(image -> !validImage(image, width, height))
            || images.stream().mapToInt(image -> image.sourceWidth() * image.sourceHeight()).sum() > MAX_IMAGE_SOURCE_PIXELS)
            throw new IllegalArgumentException("Invalid sketchbook image elements");
        Set<LayerRef> expected = new HashSet<>();
        pixelLayers.forEach(layer -> expected.add(new LayerRef(LayerType.PIXEL, layer.id())));
        texts.forEach(text -> expected.add(new LayerRef(LayerType.TEXT, text.id())));
        images.forEach(image -> expected.add(new LayerRef(LayerType.IMAGE, image.id())));
        if (expected.size() != pixelLayers.size() + texts.size() + images.size() || order == null
            || order.size() != expected.size() || !expected.equals(new HashSet<>(order))) {
            throw new IllegalArgumentException("Invalid sketchbook layer order");
        }
    }

    public static boolean isValid(byte[] data) { return data.length == 0 || decode(data) != null; }
    private static boolean validDimensions(int width, int height) {
        return width > 0 && height > 0 && (long) width * height <= MAX_PIXELS;
    }
    private static int[] readPixels(ByteBuffer buffer, int count) {
        int[] pixels = new int[count];
        for (int i = 0; i < count; i++) pixels[i] = buffer.getInt();
        return pixels;
    }
    private static boolean validText(TextElement text, int width, int height) {
        return text != null && text.id() != 0 && text.text() != null && !text.text().isEmpty()
            && text.text().codePointCount(0, text.text().length()) <= MAX_TEXT_LENGTH
            && text.x() >= 0 && text.y() >= 0 && text.x() < width && text.y() < height
            && text.width() > 0 && text.height() > 0 && text.x() + text.width() <= width
            && text.y() + text.height() <= height && text.fontSize() >= 1 && text.fontSize() <= 200;
    }
    private static boolean validImage(ImageElement image, int width, int height) {
        return image != null && image.id() != 0 && image.pixels() != null && image.sourceWidth() > 0 && image.sourceHeight() > 0
            && image.sourceWidth() <= 512 && image.sourceHeight() <= 512
            && image.pixels().length == image.sourceWidth() * image.sourceHeight()
            && image.x() >= 0 && image.y() >= 0 && image.x() < width && image.y() < height
            && image.width() > 0 && image.height() > 0 && image.x() + image.width() <= width
            && image.y() + image.height() <= height;
    }

    public enum LayerType { PIXEL, IMAGE, TEXT }
    public record LayerRef(LayerType type, long id) {}
    public record PixelLayer(long id, int[] pixels) {}
    public record TextElement(long id, String text, int x, int y, int width, int height, int fontSize, int color) {
        public TextElement(String text, int x, int y, int width, int height, int fontSize, int color) {
            this(newId(), text, x, y, width, height, fontSize, color);
        }
    }
    public record ImageElement(long id, int x, int y, int width, int height, int sourceWidth, int sourceHeight, int[] pixels) {
        public ImageElement(int x, int y, int width, int height, int sourceWidth, int sourceHeight, int[] pixels) {
            this(newId(), x, y, width, height, sourceWidth, sourceHeight, pixels);
        }
    }
    public record Drawing(int width, int height, List<PixelLayer> pixelLayers, List<TextElement> texts,
                          List<ImageElement> images, List<LayerRef> layerOrder) {
        public int[] pixels() { return pixelLayers.getFirst().pixels(); }
    }
}
