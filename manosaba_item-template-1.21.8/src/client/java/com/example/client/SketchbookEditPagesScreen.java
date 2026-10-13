// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.client;

import com.example.component.ModDataComponents;
import com.example.component.SketchbookPage;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SketchbookEditPagesScreen extends Screen {
    private final SketchbookScreen parent;
    private List<SketchbookPage> pages;
    private boolean deleteMode;
    private int selected = -1;
    private Button deleteConfirm;
    private SketchbookPageListWidget list;

    public SketchbookEditPagesScreen(SketchbookScreen parent) {
        super(Component.literal("编辑页面"));
        this.parent = parent;
    }

    @Override protected void init() {
        pages = parent.pages();
        int contentWidth = Math.min(440, Math.max(190, width - 24));
        int x = (width - contentWidth) / 2;
        int listTop = 40;
        int listBottom = Math.max(listTop + 45, height - 38);
        list = addRenderableWidget(new SketchbookPageListWidget(x, listTop, contentWidth, listBottom - listTop, pages, this::select));
        list.setSelected(selected);
        int half = (contentWidth - 8) / 2;
        addRenderableWidget(Button.builder(Component.literal(deleteMode ? "退出删除模式" : "删除页面"), b -> {
            deleteMode = !deleteMode; selected = -1; rebuildWidgets();
        }).pos(x, 14).size(half, 20).build());
        deleteConfirm = addRenderableWidget(Button.builder(Component.literal("确认删除"), b -> delete()).pos(x + half + 8, 14).size(half, 20).build());
        deleteConfirm.active = deleteMode && selected >= 0;
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose()).pos(width / 2 - 50, height - 26).size(100, 20).build());
    }

    private void select(int index) {
        if (deleteMode) {
            selected = index;
            deleteConfirm.active = true;
        } else {
            minecraft.setScreen(new SketchbookDrawingScreen(parent, index));
        }
    }

    private void delete() {
        if (!deleteMode || selected < 0 || selected >= pages.size()) return;
        int display = parent.getSketchbookStack().getOrDefault(ModDataComponents.SKETCHBOOK_DISPLAY_PAGE, -1);
        pages.remove(selected);
        if (display == selected) display = -1;
        else if (display > selected) display--;
        List<Integer> colors = parent.getSketchbookStack().getOrDefault(ModDataComponents.SKETCHBOOK_COLORS, List.of());
        int previous = parent.getSketchbookStack().getOrDefault(ModDataComponents.SKETCHBOOK_PREV_COLOR, 0xFF000000);
        parent.update(pages, colors, previous, display);
        selected = -1; deleteMode = false; rebuildWidgets();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        if (pages != null && pages.isEmpty()) graphics.drawCenteredString(font, "还没有页面，请先创建新页面", width / 2, height / 2, 0xFFAAAAAA);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
