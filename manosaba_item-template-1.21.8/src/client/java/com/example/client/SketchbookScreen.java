// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.client;

import com.example.component.ModDataComponents;
import com.example.component.SketchbookPage;
import com.example.component.SketchbookDrawingData;
import com.example.item.custom.AnanBookItem;
import com.example.network.SketchbookUpdatePayload;
import com.example.network.SketchbookPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class SketchbookScreen extends Screen {
    private ItemStack sketchbookStack = ItemStack.EMPTY;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;

    public SketchbookScreen() {
        super(Component.literal("夏目安安的素描本"));
    }

    @Override
    protected void init() {
        ItemStack stack = getSketchbookStack();
        boolean open = Boolean.TRUE.equals(stack.get(ModDataComponents.SKETCHBOOK_OPEN));
        panelWidth = Math.min(292, Math.max(210, width - 32));
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(58, (height - 126) / 2);
        int gap = 6;
        int half = (panelWidth - gap) / 2;
        addRenderableWidget(button("清空展示", 8, 8, 74, b -> clearDisplayedPage(),
            stack.getOrDefault(ModDataComponents.SKETCHBOOK_DISPLAY_PAGE, -1) >= 0));
        addRenderableWidget(button("新的一页", panelLeft, panelTop, panelWidth, b -> newPage(), true));
        addRenderableWidget(button("编辑页面", panelLeft, panelTop + 27, half,
            b -> minecraft.setScreen(new SketchbookEditPagesScreen(this)), true));
        addRenderableWidget(button("展示页面", panelLeft + half + gap, panelTop + 27, half,
            b -> minecraft.setScreen(new SketchbookDisplayPagesScreen(this)), open));
        addRenderableWidget(button("关闭菜单", panelLeft, panelTop + 62, panelWidth, b -> onClose(), true));
    }

    private Button button(String text, int x, int y, int w, Button.OnPress press, boolean active) {
        Button result = Button.builder(Component.literal(text), press).pos(x, y).size(w, 20).build();
        result.active = active;
        return result;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.renderOutline(panelLeft - 8, panelTop - 28, panelWidth + 16, 120, 0xFF777777);
        graphics.drawCenteredString(font, title, width / 2, panelTop - 20, 0xFFFFFFFF);
    }

    public ItemStack getSketchbookStack() {
        if (minecraft == null || minecraft.player == null) return sketchbookStack;
        ItemStack main = minecraft.player.getMainHandItem();
        ItemStack off = minecraft.player.getOffhandItem();
        if (main.getItem() instanceof AnanBookItem) sketchbookStack = main;
        else if (off.getItem() instanceof AnanBookItem) sketchbookStack = off;
        else for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (stack.getItem() instanceof AnanBookItem) { sketchbookStack = stack; break; }
        }
        return sketchbookStack;
    }

    public List<SketchbookPage> pages() {
        List<SketchbookPage> pages = getSketchbookStack().get(ModDataComponents.SKETCHBOOK_PAGES);
        return pages == null ? new ArrayList<>() : new ArrayList<>(pages);
    }

    public void update(List<SketchbookPage> pages, List<Integer> colors, int previousColor, int displayPage) {
        ItemStack stack = getSketchbookStack();
        List<SketchbookPage> safePages = List.copyOf(pages);
        List<Integer> safeColors = List.copyOf(colors);
        stack.set(ModDataComponents.SKETCHBOOK_PAGES, safePages);
        stack.set(ModDataComponents.SKETCHBOOK_COLORS, safeColors);
        stack.set(ModDataComponents.SKETCHBOOK_PREV_COLOR, previousColor);
        stack.set(ModDataComponents.SKETCHBOOK_DISPLAY_PAGE, displayPage);
        ClientPlayNetworking.send(new SketchbookUpdatePayload(safePages, safeColors, previousColor, displayPage));
    }

    public void updatePages(List<SketchbookPage> pages) {
        ItemStack stack = getSketchbookStack();
        List<Integer> colors = stack.get(ModDataComponents.SKETCHBOOK_COLORS);
        Integer previous = stack.get(ModDataComponents.SKETCHBOOK_PREV_COLOR);
        Integer display = stack.get(ModDataComponents.SKETCHBOOK_DISPLAY_PAGE);
        update(pages, colors == null ? List.of() : colors, previous == null ? 0xFF000000 : previous,
            display == null ? -1 : display);
    }

    private void newPage() {
        List<SketchbookPage> pages = pages();
        pages.add(new SketchbookPage("", "drawing", "", new byte[0]));
        updatePages(pages);
        minecraft.setScreen(new SketchbookDrawingScreen(this, pages.size() - 1));
    }

    private void clearDisplayedPage() {
        ItemStack stack = getSketchbookStack();
        update(pages(), stack.getOrDefault(ModDataComponents.SKETCHBOOK_COLORS, List.of()),
            stack.getOrDefault(ModDataComponents.SKETCHBOOK_PREV_COLOR, 0xFF000000), -1);
        rebuildWidgets();
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new SketchbookScreen());
    }

    public static boolean openWithText(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || text == null || text.isBlank()) return false;
        ItemStack book = findSketchbook(minecraft);
        if (book.isEmpty()) return false;
        ClientPlayNetworking.send(new SketchbookPayload(text));
        return true;
    }

    public static void saveAndDisplayText(int pageIndex, String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        ItemStack book = findSketchbook(minecraft);
        if (book.isEmpty()) return;
        SketchbookScreen screen = new SketchbookScreen();
        screen.sketchbookStack = book;
        List<SketchbookPage> pages = screen.pages();
        if (pageIndex < 0 || pageIndex >= pages.size()) return;
        SketchbookPage old = pages.get(pageIndex);
        byte[] drawingData = createCenteredTextDrawing(minecraft, text);
        pages.set(pageIndex, old.copy(old.getName(), "drawing", "", drawingData));
        screen.update(pages, book.getOrDefault(ModDataComponents.SKETCHBOOK_COLORS, List.of()),
            book.getOrDefault(ModDataComponents.SKETCHBOOK_PREV_COLOR, 0xFF000000), pageIndex);
        minecraft.setScreen(null);
    }

    private static byte[] createCenteredTextDrawing(Minecraft minecraft, String rawText) {
        String text = limitCodePoints(rawText.strip(), SketchbookDrawingData.MAX_TEXT_LENGTH);
        int width = SketchbookDrawingData.WIDTH;
        int height = SketchbookDrawingData.HEIGHT;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, 0x00FFFFFF);
        List<String> lines = List.of();
        int chosenSize = 20;
        for (int candidate = 96; candidate >= 20; candidate -= 2) {
            List<String> candidateLines = wrapText(minecraft, text, candidate, width - 48);
            int lineHeight = candidate + Math.max(4, candidate / 5);
            if (candidateLines.size() <= 10 && candidateLines.size() * lineHeight <= height - 40) {
                lines = candidateLines;
                chosenSize = candidate;
                break;
            }
        }
        if (lines.isEmpty()) lines = wrapText(minecraft, text, chosenSize, width - 32);
        int gap = Math.max(4, chosenSize / 5);
        int lineHeight = chosenSize + gap;
        int y = Math.max(8, (height - (lines.size() * lineHeight - gap)) / 2);
        List<SketchbookDrawingData.TextElement> elements = new ArrayList<>();
        for (String line : lines) {
            if (line.isEmpty()) continue;
            int textWidth = Math.min(width - 16, naturalTextWidth(minecraft, line, chosenSize));
            elements.add(new SketchbookDrawingData.TextElement(line, Math.max(8, (width - textWidth) / 2),
                y, textWidth, chosenSize, chosenSize, 0xFF000000));
            y += lineHeight;
        }
        SketchbookDrawingData.PixelLayer pixelLayer = new SketchbookDrawingData.PixelLayer(
            SketchbookDrawingData.newId(), pixels);
        List<SketchbookDrawingData.LayerRef> order = new ArrayList<>();
        order.add(new SketchbookDrawingData.LayerRef(SketchbookDrawingData.LayerType.PIXEL, pixelLayer.id()));
        elements.forEach(element -> order.add(new SketchbookDrawingData.LayerRef(
            SketchbookDrawingData.LayerType.TEXT, element.id())));
        return SketchbookDrawingData.encode(width, height, List.of(pixelLayer), elements, List.of(), order);
    }

    private static List<String> wrapText(Minecraft minecraft, String text, int fontSize, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n') {
                if (!line.isEmpty()) lines.add(line.toString());
                line.setLength(0);
                continue;
            }
            String next = new String(Character.toChars(codePoint));
            if (!line.isEmpty() && naturalTextWidth(minecraft, line + next, fontSize) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(next);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private static int naturalTextWidth(Minecraft minecraft, String text, int fontSize) {
        return Math.max(1, Math.round(minecraft.font.width(text) * fontSize / (float) minecraft.font.lineHeight));
    }

    private static String limitCodePoints(String text, int max) {
        int count = text.codePointCount(0, text.length());
        return count <= max ? text : text.substring(0, text.offsetByCodePoints(0, max));
    }

    private static ItemStack findSketchbook(Minecraft minecraft) {
        ItemStack main = minecraft.player.getMainHandItem();
        if (main.getItem() instanceof AnanBookItem) return main;
        ItemStack off = minecraft.player.getOffhandItem();
        if (off.getItem() instanceof AnanBookItem) return off;
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (stack.getItem() instanceof AnanBookItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override public boolean isPauseScreen() { return false; }
}
