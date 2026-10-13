// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.example.component.SketchbookDrawingData;
import com.example.component.ModDataComponents;
import com.example.component.SketchbookPage;
import com.example.network.SketchbookDisplayPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SketchbookDisplayPagesScreen extends Screen {
    private final SketchbookScreen parent;
    private List<SketchbookPage> pages;
    private int preview = -1;
    private double textScroll;
    private DynamicTexture previewTexture;
    private ResourceLocation previewTextureLocation;
    private int previewTextureHash;

    public SketchbookDisplayPagesScreen(SketchbookScreen parent) {
        super(Component.literal("展示页面"));
        this.parent = parent;
    }

    @Override protected void init() {
        pages = parent.pages();
        if (preview < 0) buildList(); else buildPreview();
    }

    private void buildList() {
        int w = Math.min(440, Math.max(190, width - 24));
        addRenderableWidget(new SketchbookPageListWidget((width - w) / 2, 36, w, Math.max(45, height - 70), pages, index -> {
            preview = index; textScroll = 0; rebuildWidgets();
        }));
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose()).pos(width / 2 - 50, height - 26).size(100, 20).build());
    }

    private void buildPreview() {
        int buttonWidth = Math.min(150, (width - 30) / 2);
        addRenderableWidget(Button.builder(Component.literal("确认展示"), b -> confirm()).pos(width / 2 - buttonWidth - 4, height - 26).size(buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> { releasePreviewTexture(); preview = -1; rebuildWidgets(); }).pos(width / 2 + 4, height - 26).size(buttonWidth, 20).build());
    }

    private void confirm() {
        if (preview < 0 || preview >= pages.size()) return;
        SketchbookPage page = pages.get(preview);
        if ("drawing".equals(page.getMode())) {
            parent.getSketchbookStack().set(ModDataComponents.SKETCHBOOK_DISPLAY_PAGE, preview);
        }
        ClientPlayNetworking.send(new SketchbookDisplayPayload(preview));
        minecraft.setScreen(parent);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, preview < 0 ? "选择要展示的页面" : "预览 - " + pages.get(preview).getName(), width / 2, 14, 0xFFFFFFFF);
        if (preview < 0) {
            if (pages.isEmpty()) graphics.drawCenteredString(font, "还没有页面", width / 2, height / 2, 0xFFAAAAAA);
            return;
        }
        SketchbookPage page = pages.get(preview);
        int x = 20, y = 34, w = width - 40, h = Math.max(20, height - 68);
        if ("drawing".equals(page.getMode())) {
            renderDrawing(graphics, page.getDrawingData(), x, y, w, h);
        } else {
            graphics.fill(x, y, x + w, y + h, 0xCCF2E8D5);
            graphics.enableScissor(x, y, x + w, y + h);
            graphics.drawWordWrap(font, Component.literal(page.getTextContent()), x + 8, y + 8 - (int) textScroll, w - 16, 0xFF222222, false);
            graphics.disableScissor();
            graphics.renderOutline(x, y, w, h, 0xFF665544);
        }
    }

    private void renderDrawing(GuiGraphics graphics, byte[] data, int x, int y, int areaW, int areaH) {
        SketchbookDrawingData.Drawing drawing = SketchbookDrawingData.decode(data);
        if (drawing == null) return;
        int sourceW = drawing.width();
        int sourceH = drawing.height();
        double scale = Math.min((double) areaW / sourceW, (double) areaH / sourceH);
        int drawW = Math.max(1, (int) (sourceW * scale));
        int drawH = Math.max(1, (int) (sourceH * scale));
        int left = x + (areaW - drawW) / 2;
        int top = y + (areaH - drawH) / 2;
        graphics.fill(left, top, left + drawW, top + drawH, 0xFFFFFFFF);
        graphics.enableScissor(left, top, left + drawW, top + drawH);
        int hash = java.util.Arrays.hashCode(data);
        if (previewTexture == null || previewTextureHash != hash) {
            releasePreviewTexture();
            NativeImage image = new NativeImage(sourceW, sourceH, false);
            int[] pixels = SketchbookTextRasterizer.render(drawing);
            for (int py = 0; py < sourceH; py++) {
                for (int px = 0; px < sourceW; px++) image.setPixel(px, py, compositeWhite(pixels[py * sourceW + px]));
            }
            previewTexture = new DynamicTexture(() -> "manosaba sketchbook preview", image);
            previewTextureLocation = ResourceLocation.fromNamespaceAndPath("manosaba_item",
                "sketchbook_preview/" + Integer.toUnsignedString(System.identityHashCode(this)));
            minecraft.getTextureManager().register(previewTextureLocation, previewTexture);
            previewTextureHash = hash;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, previewTextureLocation, left, top, 0, 0,
            drawW, drawH, sourceW, sourceH, sourceW, sourceH);
        graphics.disableScissor();
        graphics.renderOutline(left, top, drawW, drawH, 0xFF665544);
    }

    private static int compositeWhite(int color) {
        int a = color >>> 24;
        int r = ((color >> 16 & 255) * a + 255 * (255 - a)) / 255;
        int g = ((color >> 8 & 255) * a + 255 * (255 - a)) / 255;
        int b = ((color & 255) * a + 255 * (255 - a)) / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (preview >= 0 && "writing".equals(pages.get(preview).getMode())) {
            int contentHeight = font.split(Component.literal(pages.get(preview).getTextContent()), Math.max(20, width - 56)).size() * 9 + 16;
            textScroll = Math.clamp(textScroll - vertical * 12, 0, Math.max(0, contentHeight - (height - 68)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private void releasePreviewTexture() {
        if (previewTextureLocation != null && minecraft != null) minecraft.getTextureManager().release(previewTextureLocation);
        previewTexture = null;
        previewTextureLocation = null;
        previewTextureHash = 0;
    }

    @Override public void onClose() { releasePreviewTexture(); minecraft.setScreen(parent); }
    @Override public void removed() { releasePreviewTexture(); }
    @Override public boolean isPauseScreen() { return false; }
}
