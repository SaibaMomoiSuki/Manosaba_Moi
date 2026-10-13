// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.client;

import com.example.component.ModDataComponents;
import com.example.component.SketchbookDrawingData;
import com.example.component.SketchbookPage;
import com.example.item.custom.AnanBookItem;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class SketchbookArtworkRenderer {
    private static final Map<byte[], CachedTexture> CACHE = new LinkedHashMap<>();
    private static final ThreadLocal<ItemStack> CURRENT_STACK = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> RENDERED = ThreadLocal.withInitial(() -> false);

    private SketchbookArtworkRenderer() {}

    public static void begin(ItemStack stack) {
        CURRENT_STACK.set(stack);
        RENDERED.set(false);
    }

    public static void renderCurrent(PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (Boolean.TRUE.equals(RENDERED.get())) return;
        ItemStack stack = CURRENT_STACK.get();
        if (stack == null) return;
        RENDERED.set(true);
        render(stack, poseStack, buffers, light, overlay);
    }

    public static void end() {
        CURRENT_STACK.remove();
        RENDERED.remove();
    }

    public static void render(ItemStack stack, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (!AnanBookItem.isOpenSketchbook(stack)) return;
        int pageIndex = stack.getOrDefault(ModDataComponents.SKETCHBOOK_DISPLAY_PAGE, -1);
        List<SketchbookPage> pages = stack.getOrDefault(ModDataComponents.SKETCHBOOK_PAGES, List.of());
        if (pageIndex < 0 || pageIndex >= pages.size()) return;
        SketchbookPage page = pages.get(pageIndex);
        if (!"drawing".equals(page.getMode())) return;
        byte[] data = page.getDrawingData();
        CachedTexture cached = CACHE.get(data);
        if (cached == null) {
            SketchbookDrawingData.Drawing drawing = SketchbookDrawingData.decode(data);
            if (drawing == null) return;
            cached = createTexture(drawing);
            CACHE.put(data, cached);
            if (CACHE.size() > 32) {
                byte[] oldest = CACHE.keySet().iterator().next();
                CachedTexture removed = CACHE.remove(oldest);
                Minecraft.getInstance().getTextureManager().release(removed.location);
            }
        }

        // Map the exact 112x84 (4:3) white model plane to the complete artwork texture.
        float x0 = -1.1F / 16.0F, x1 = 12.9F / 16.0F;
        float z0 = 1.0F / 16.0F, z1 = 11.5F / 16.0F;
        float y = 0.805F / 16.0F;
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(cached.location));
        vertex(consumer, pose, x0, y, z1, 0, 1, light, overlay);
        vertex(consumer, pose, x1, y, z1, 1, 1, light, overlay);
        vertex(consumer, pose, x1, y, z0, 1, 0, light, overlay);
        vertex(consumer, pose, x0, y, z0, 0, 0, light, overlay);
    }

    private static CachedTexture createTexture(SketchbookDrawingData.Drawing drawing) {
        NativeImage image = new NativeImage(drawing.width(), drawing.height(), false);
        int[] pixels = SketchbookTextRasterizer.render(drawing);
        for (int y = 0; y < drawing.height(); y++) {
            for (int x = 0; x < drawing.width(); x++) image.setPixel(x, y, pixels[y * drawing.width() + x]);
        }
        DynamicTexture texture = new DynamicTexture(() -> "manosaba sketchbook artwork", image);
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath("manosaba_item",
            "sketchbook_artwork/" + Integer.toUnsignedString(System.identityHashCode(texture)));
        Minecraft.getInstance().getTextureManager().register(location, texture);
        return new CachedTexture(location);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int light, int overlay) {
        consumer.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
            .setOverlay(overlay).setLight(light).setNormal(pose, 0, 1, 0);
    }

    private record CachedTexture(ResourceLocation location) {}
}
