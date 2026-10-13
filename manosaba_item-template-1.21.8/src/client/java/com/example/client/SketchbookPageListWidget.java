// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.client;

import com.example.component.SketchbookPage;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

final class SketchbookPageListWidget extends AbstractScrollArea {
    private final List<SketchbookPage> pages;
    private final IntConsumer onSelect;
    private int selected = -1;

    SketchbookPageListWidget(int x, int y, int width, int height, List<SketchbookPage> pages, IntConsumer onSelect) {
        super(x, y, width, height, Component.literal("页面列表"));
        this.pages = pages;
        this.onSelect = onSelect;
    }

    void setSelected(int selected) { this.selected = selected; }

    @Override protected int contentHeight() { return Math.max(getHeight(), pages.size() * 24 + 4); }
    @Override protected double scrollRate() { return 24.0; }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(getX(), getY(), getRight(), getBottom(), 0x66000000);
        graphics.enableScissor(getX(), getY(), getRight(), getBottom());
        int y = getY() + 2 - (int) scrollAmount();
        for (int i = 0; i < pages.size(); i++) {
            int rowY = y + i * 24;
            if (rowY + 21 >= getY() && rowY <= getBottom()) {
                boolean hover = mouseX >= getX() + 2 && mouseX < getRight() - 8 && mouseY >= rowY && mouseY < rowY + 21;
                graphics.fill(getX() + 2, rowY, getRight() - 8, rowY + 21,
                    i == selected ? 0xAA993333 : hover ? 0xAA555555 : 0xAA333333);
                SketchbookPage page = pages.get(i);
                graphics.drawString(Minecraft.getInstance().font, page.getName(), getX() + 8, rowY + 6, 0xFFFFFFFF, false);
            }
        }
        graphics.disableScissor();
        renderScrollbar(graphics);
        graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF777777);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;
        if (updateScrolling(mouseX, mouseY, button)) return true;
        int index = (int) ((mouseY - getY() + scrollAmount() - 2) / 24);
        if (index >= 0 && index < pages.size()) {
            selected = index;
            onSelect.accept(index);
            return true;
        }
        return false;
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
