// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.example.component.ModDataComponents;
import com.example.component.SketchbookDrawingData;
import com.example.component.SketchbookPage;
import com.example.network.SketchbookDisplayPayload;
import com.example.Manosaba_ItemClient;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class SketchbookDrawingScreen extends Screen {
    private static final int CANVAS_W = SketchbookDrawingData.WIDTH;
    private static final int CANVAS_H = SketchbookDrawingData.HEIGHT;
    private static final int CLEAR = 0x00FFFFFF;
    private static final int MAX_UNDO = 12;
    private static final ResourceLocation PENCIL = texture("pencil.png");
    private static final ResourceLocation BRUSH = texture("paintbrush.png");
    private static final ResourceLocation ERASER = texture("eraser.png");
    private static final ResourceLocation BUCKET = texture("paint_bucket.png");
    private static final ResourceLocation TEXT = texture("text.png");

    private enum Tool { PENCIL, BRUSH, ERASER, BUCKET, TEXT, SELECT, IMAGE, LAYERS }
    private enum PanelMode { DRAW, TEXT, IMAGE, LAYERS }
    private enum SelectionType { NONE, TEXT, IMAGE }

    private final SketchbookScreen parent;
    private final int pageIndex;
    private final ArrayDeque<Snapshot> undo = new ArrayDeque<>();
    private int[] pixels = new int[CANVAS_W * CANVAS_H];
    private List<EditablePixelLayer> pixelLayers = new ArrayList<>();
    private List<EditableText> texts = new ArrayList<>();
    private List<EditableImage> images = new ArrayList<>();
    private List<SketchbookDrawingData.LayerRef> layerOrder = new ArrayList<>();
    private SketchbookDrawingData.LayerRef selectedLayer;
    private long currentPixelLayerId;
    private final List<LibraryImage> imageLibrary = new ArrayList<>();
    private int selectedText = -1;
    private int selectedImage = -1;
    private SelectionType selectionType = SelectionType.NONE;
    private int textSize = 24;
    private final int[] strokeMarks = new int[CANVAS_W * CANVAS_H];
    private int strokeId;
    private List<Integer> colors = new ArrayList<>();
    private int currentColor = 0xFF000000;
    private int previousColor = 0xFFFFFFFF;
    private int brushSize = 3;
    private int tolerance;
    private boolean contiguous = true;
    private Tool tool = Tool.TEXT;
    private PanelMode panelMode = PanelMode.TEXT;
    private Canvas canvas;
    private EditorButton undoButton;
    private ToolButton layerToolButton;
    private boolean clearConfirm;
    private boolean libraryDeleteMode;
    private boolean libraryDeleteConfirm;
    private final Set<Integer> libraryDeleteSelection = new HashSet<>();
    private boolean loaded;
    private boolean dirty;
    private boolean textureDirty = true;
    private boolean fullTextureRefresh = true;
    private long lastTextureUploadNanos;
    private boolean syncing;
    private boolean cursorHidden;
    private boolean strokeColorRecorded;
    private DynamicTexture colorWheelTexture;
    private ResourceLocation colorWheelTextureLocation;
    private byte[] lastEncodedData = new byte[0];
    private EditBox hex, red, green, blue, hue, saturation, value, alpha, size, toleranceField, textField, textSizeField;
    private EditBox nameField;
    private String pageName = "";
    private Slider sizeSlider, textSizeSlider, redSlider, greenSlider, blueSlider, hueSlider, saturationSlider, valueSlider, alphaSlider;
    private final List<Slider> sliders = new ArrayList<>();
    private final List<ControlLabel> controlLabels = new ArrayList<>();
    private int colorRevision;
    private final String initialText;
    private boolean initialTextApplied;
    private volatile boolean floatingLayerWindowOpen;
    private volatile JFrame layerFrame;
    private volatile DefaultListModel<String> layerListModel;
    private volatile JList<String> layerList;
    private volatile boolean syncingFloatingLayerList;

    public SketchbookDrawingScreen(SketchbookScreen parent, int pageIndex) {
        this(parent, pageIndex, null);
    }

    public SketchbookDrawingScreen(SketchbookScreen parent, int pageIndex, String initialText) {
        super(Component.literal("绘画模式"));
        this.parent = parent;
        this.pageIndex = pageIndex;
        this.initialText = initialText;
        Arrays.fill(pixels, CLEAR);
        EditablePixelLayer initialLayer = new EditablePixelLayer(SketchbookDrawingData.newId(), pixels);
        pixelLayers.add(initialLayer);
        currentPixelLayerId = initialLayer.id;
        selectedLayer = new SketchbookDrawingData.LayerRef(SketchbookDrawingData.LayerType.PIXEL, initialLayer.id);
        layerOrder.add(selectedLayer);
    }

    @Override protected void init() {
        releaseSliderTextures();
        sliders.clear();
        controlLabels.clear();
        List<SketchbookPage> pages = parent.pages();
        if (pageIndex < 0 || pageIndex >= pages.size()) { minecraft.setScreen(parent); return; }
        if (!loaded) {
            lastEncodedData = pages.get(pageIndex).getDrawingData();
            pageName = pages.get(pageIndex).getName();
            load(lastEncodedData);
            colors = new ArrayList<>(parent.getSketchbookStack().getOrDefault(ModDataComponents.SKETCHBOOK_COLORS, List.of()));
            previousColor = parent.getSketchbookStack().getOrDefault(ModDataComponents.SKETCHBOOK_PREV_COLOR, 0xFF000000);
            loaded = true;
        }
        if (!initialTextApplied && initialText != null && !initialText.isBlank()) {
            applyInitialText(initialText);
            initialTextApplied = true;
        }
        if (colorWheelTexture == null) createColorWheelTexture();

        int margin = 8;
        int nameBarH = 18;
        int nameGap = 4;
        int bottom = 32;
        int panelW = Math.clamp((int) (width * 0.43), 240, 380);
        panelW = Math.min(panelW, Math.max(180, width - 120));
        int canvasAreaW = Math.max(90, width - panelW - margin * 3);
        int contentTop = margin + nameBarH + nameGap;
        int canvasAreaH = Math.max(70, height - bottom - contentTop);
        double scale = Math.min((double) canvasAreaW / CANVAS_W, (double) canvasAreaH / CANVAS_H);
        int drawW = Math.max(1, (int) Math.floor(CANVAS_W * scale));
        int drawH = Math.max(1, Math.round(drawW * CANVAS_H / (float) CANVAS_W));
        int canvasY = contentTop + Math.max(0, (canvasAreaH - drawH) / 2);
        canvas = addRenderableWidget(new Canvas(margin, canvasY, drawW, drawH));
        int panelX = margin * 2 + drawW;
        int panelInnerW = Math.max(150, width - panelX - margin);
        buildTools(panelX, contentTop, panelInnerW);

        nameField = new EditBox(font, margin, margin, Math.max(60, drawW), nameBarH, Component.literal("作品名称"));
        nameField.setMaxLength(32);
        nameField.setHint(Component.literal("作品名称"));
        nameField.setResponder(value -> pageName = value);
        nameField.setValue(pageName);
        addRenderableWidget(nameField);

        int buttonW = Math.max(55, Math.min(95, (width - 40) / 4));
        int total = buttonW * 4 + 12;
        int bx = Math.max(4, (width - total) / 2);
        int by = height - 25;
        addRenderableWidget(new EditorButton(bx, by, buttonW, 20, "保存", 0xFF315D8C, () -> { save(); minecraft.setScreen(parent); }));
        addRenderableWidget(new EditorButton(bx + buttonW + 4, by, buttonW, 20, "展示", 0xFF347A55, this::display));
        addRenderableWidget(new EditorButton(bx + (buttonW + 4) * 2, by, buttonW, 20, "清除", 0xFF8A3E45, () -> clearConfirm = true));
        addRenderableWidget(new EditorButton(bx + (buttonW + 4) * 3, by, buttonW, 20, "返回", 0xFF454B52, this::onClose));
        syncFields();
        if (tool == Tool.TEXT) syncTextControls(false);
    }

    private void buildTools(int x, int y, int w) {
        int icon = Math.min(24, Math.max(16, (w - 14) / 8));
        int gap = Math.max(2, (w - icon * 8) / 7);
        addRenderableWidget(new ToolButton(x, y, icon, PENCIL, Tool.PENCIL));
        addRenderableWidget(new ToolButton(x + icon + gap, y, icon, BRUSH, Tool.BRUSH));
        addRenderableWidget(new ToolButton(x + (icon + gap) * 2, y, icon, ERASER, Tool.ERASER));
        addRenderableWidget(new ToolButton(x + (icon + gap) * 3, y, icon, BUCKET, Tool.BUCKET));
        addRenderableWidget(new ToolButton(x + (icon + gap) * 4, y, icon, TEXT, Tool.TEXT));
        addRenderableWidget(new ToolButton(x + (icon + gap) * 5, y, icon, null, Tool.SELECT));
        addRenderableWidget(new ToolButton(x + (icon + gap) * 6, y, icon, null, Tool.IMAGE));
        layerToolButton = addRenderableWidget(new ToolButton(x + (icon + gap) * 7, y, icon, null, Tool.LAYERS));
        layerToolButton.active = !floatingLayerWindowOpen;
        undoButton = addRenderableWidget(new EditorButton(x, y + 28, (w - 4) / 2, 18,
            "撤回", 0xFF454B52, this::performUndo));
        undoButton.active = !undo.isEmpty();
        addRenderableWidget(new EditorButton(x + (w + 4) / 2, y + 28, (w - 4) / 2, 18,
            contiguous ? "连续填充" : "全局填充", 0xFF454B52, () -> { contiguous = !contiguous; rebuildWidgets(); }));

        if (panelMode == PanelMode.IMAGE) {
            buildImageLibrary(x, y + 49, w);
            return;
        }
        if (panelMode == PanelMode.LAYERS) {
            buildLayerPanel(x, y + 49, w);
            return;
        }

        int inputW = 34;
        int stepperW = 12;
        int sliderX = x + 25;
        int sliderW = Math.max(42, w - 25 - inputW - stepperW - 6);
        int inputX = sliderX + sliderW + 4;

        int controlsOffset = 0;
        if (panelMode == PanelMode.TEXT) {
            int textOffset = tool == Tool.TEXT ? 22 : 0;
            if (tool == Tool.TEXT) {
                textField = new EditBox(font, x, y + 49, w, 16, Component.literal("文字内容"));
                textField.setMaxLength(SketchbookDrawingData.MAX_TEXT_LENGTH);
                textField.setHint(Component.literal("在画布点击新建，或选择文字修改"));
                textField.setResponder(this::updateSelectedText);
                addRenderableWidget(textField);
            }
            controlLabels.add(new ControlLabel("字号", x, y + 50 + textOffset));
            textSizeSlider = addRenderableWidget(new Slider(sliderX, y + 49 + textOffset, sliderW, 8, 1, 200, textSize,
                v -> { setSelectedTextSize(v); setText(textSizeField, v); }, value -> gray(value, 1, 200)));
            textSizeField = numericField(inputX, y + 45 + textOffset, inputW, "字号", textSize, 1, 200,
                v -> { setSelectedTextSize(v); if (textSizeSlider != null) textSizeSlider.setValue(v); });
            int arrowX = inputX + inputW + 1;
            addRenderableWidget(Button.builder(Component.literal("↑"), b -> adjustTextSize(1)).pos(arrowX, y + 45 + textOffset).size(12, 7).build());
            addRenderableWidget(Button.builder(Component.literal("↓"), b -> adjustTextSize(-1)).pos(arrowX, y + 52 + textOffset).size(12, 7).build());
            controlsOffset = textOffset;
        } else {
            controlLabels.add(new ControlLabel("大小", x, y + 54));
            sizeSlider = addRenderableWidget(new Slider(sliderX, y + 53, sliderW, 8, 1, 100, brushSize,
                v -> { brushSize = v; setText(size, v); }, value -> gray(value, 1, 100)));
            size = numericField(inputX, y + 49, inputW, "大小", brushSize, 1, 100,
                v -> { brushSize = v; if (sizeSlider != null) sizeSlider.setValue(v); });
        }

        addRenderableWidget(new ColorWheel(x, y + 68 + controlsOffset, 48));
        addRenderableWidget(new ColorSwatch(x + 54, y + 68 + controlsOffset, 28, 20, false));
        addRenderableWidget(new ColorSwatch(x + 86, y + 68 + controlsOffset, 28, 20, true));
        addRenderableWidget(Button.builder(Component.literal("使用上一色"), b -> usePreviousColor())
            .pos(x + 54, y + 92 + controlsOffset).size(60, 18).build());

        int savedY = y + 120 + controlsOffset;
        addRenderableWidget(Button.builder(Component.literal("存储颜色"), b -> {
            if (colors.size() >= 20) colors.removeFirst();
            colors.add(currentColor); saveColorState(); rebuildWidgets();
        }).pos(x, savedY).size(60, 17).build());
        int swatch = Math.max(7, Math.min(12, (w - 64) / 10));
        for (int i = 0; i < colors.size(); i++) {
            int index = i;
            addRenderableWidget(new SavedColor(x + 64 + i % 10 * swatch, savedY + i / 10 * swatch, swatch - 1, index));
        }

        int colorLabelX = x;
        int colorSliderX = x + 20;
        int colorSliderW = Math.max(36, w - 20 - inputW - stepperW - 6);
        int colorInputX = colorSliderX + colorSliderW + 3;
        int rowY = y + 162 + controlsOffset;
        int[] hsv = rgbToHsv();
        controlLabels.add(new ControlLabel("RGB", colorLabelX, rowY - 11));
        redSlider = colorControl("红", colorLabelX, colorSliderX, colorInputX, rowY, colorSliderW, inputW, currentColor >> 16 & 255, 255,
            v -> setRgb(v, currentColor >> 8 & 255, currentColor & 255), v -> 0xFF000000 | v << 16);
        red = lastColorField;
        rowY += 12;
        greenSlider = colorControl("绿", colorLabelX, colorSliderX, colorInputX, rowY, colorSliderW, inputW, currentColor >> 8 & 255, 255,
            v -> setRgb(currentColor >> 16 & 255, v, currentColor & 255), v -> 0xFF000000 | v << 8);
        green = lastColorField;
        rowY += 12;
        blueSlider = colorControl("蓝", colorLabelX, colorSliderX, colorInputX, rowY, colorSliderW, inputW, currentColor & 255, 255,
            v -> setRgb(currentColor >> 16 & 255, currentColor >> 8 & 255, v), v -> 0xFF000000 | v);
        blue = lastColorField;

        rowY += 12;
        controlLabels.add(new ControlLabel("十六进制", colorLabelX, rowY + 3));
        hex = new EditBox(font, colorInputX - 26, rowY - 2, inputW + 26 + stepperW + 2, 14, Component.literal("Hex"));
        hex.setMaxLength(7);
        hex.setResponder(text -> {
            if (syncing) return;
            try {
                String clean = text.startsWith("#") ? text.substring(1) : text;
                if (clean.length() == 6) setColor((currentColor & 0xFF000000) | Integer.parseInt(clean, 16));
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(hex);

        rowY += 15;
        controlLabels.add(new ControlLabel("HSV", colorLabelX, rowY));
        rowY += 9;
        hueSlider = colorControl("H", colorLabelX, colorSliderX, colorInputX, rowY, colorSliderW, inputW, hsv[0], 360,
            this::setHue, v -> 0xFF000000 | hsvToRgb(v, 100, 100));
        hue = lastColorField;
        rowY += 12;
        saturationSlider = colorControl("S", colorLabelX, colorSliderX, colorInputX, rowY, colorSliderW, inputW, hsv[1], 100,
            this::setSaturation, v -> 0xFF000000 | hsvToRgb(rgbToHsv()[0], v, 100));
        saturation = lastColorField;
        rowY += 12;
        valueSlider = colorControl("V", colorLabelX, colorSliderX, colorInputX, rowY, colorSliderW, inputW, hsv[2], 100,
            this::setValue, v -> 0xFF000000 | hsvToRgb(rgbToHsv()[0], rgbToHsv()[1], v));
        value = lastColorField;

        rowY += 14;
        controlLabels.add(new ControlLabel("不透明度 - Alpha", colorLabelX, rowY));
        rowY += 9;
        alphaSlider = colorControl("A", colorLabelX, colorSliderX, colorInputX, rowY, colorSliderW, inputW, currentColor >>> 24, 255,
            v -> setColor((currentColor & 0xFFFFFF) | v << 24), v -> composite((currentColor & 0xFFFFFF) | v << 24));
        alpha = lastColorField;

    }

    private void buildLayerPanel(int x, int y, int w) {
        int half = (w - 4) / 2;
        addRenderableWidget(new EditorButton(x, y, half, 18, "新建画面层", 0xFF315D8C, this::addPixelLayer));
        addRenderableWidget(new EditorButton(x + half + 4, y, half, 18, "悬空", 0xFF454B52, this::openFloatingLayerWindow));
        int rowY = y + 23;
        for (int displayIndex = layerOrder.size() - 1; displayIndex >= 0; displayIndex--) {
            SketchbookDrawingData.LayerRef ref = layerOrder.get(displayIndex);
            int orderIndex = displayIndex;
            addRenderableWidget(new LayerRowButton(x, rowY, w, 18, ref, orderIndex));
            rowY += 20;
            if (rowY > height - 78) break;
        }
        int third = (w - 8) / 3;
        int controlsY = Math.min(height - 72, rowY + 2);
        addRenderableWidget(new EditorButton(x, controlsY, third, 18, "上移", 0xFF454B52, () -> moveSelectedLayer(1)));
        addRenderableWidget(new EditorButton(x + third + 4, controlsY, third, 18, "下移", 0xFF454B52, () -> moveSelectedLayer(-1)));
        addRenderableWidget(new EditorButton(x + (third + 4) * 2, controlsY, w - (third + 4) * 2, 18,
            "快速删除", 0xFF8A3E45, this::deleteSelectedLayer));
    }

    private void buildImageLibrary(int x, int y, int w) {
        int half = (w - 4) / 2;
        addRenderableWidget(new EditorButton(x, y, half, 18, "导入图片", 0xFF315D8C, this::openImageChooser));
        addRenderableWidget(new EditorButton(x + half + 4, y, half, 18,
            libraryDeleteMode ? "退出删除" : "删除图片", libraryDeleteMode ? 0xFF454B52 : 0xFF8A3E45, () -> {
            libraryDeleteMode = !libraryDeleteMode;
            libraryDeleteSelection.clear();
            rebuildWidgets();
        }));
        int itemSize = Math.clamp((w - 12) / 3, 48, 88);
        int columns = Math.max(1, w / (itemSize + 4));
        for (int i = 0; i < imageLibrary.size(); i++) {
            int index = i;
            addRenderableWidget(new LibraryImageButton(x + i % columns * (itemSize + 4), y + 24 + i / columns * (itemSize + 18),
                itemSize, index));
        }
        if (libraryDeleteMode) {
            addRenderableWidget(new EditorButton(x,
                Math.min(height - 50, y + 26 + ((imageLibrary.size() + columns - 1) / columns) * (itemSize + 18)),
                w, 18, "确认删除 (" + libraryDeleteSelection.size() + ")", 0xFF8A3E45, () -> {
                if (!libraryDeleteSelection.isEmpty()) libraryDeleteConfirm = true;
            }));
        }
    }

    private EditBox lastColorField;

    private Slider colorControl(String label, int labelX, int sliderX, int inputX, int y, int sliderW, int inputW,
                                int initial, int max, IntConsumer consumer, IntFunction<Integer> colorAt) {
        controlLabels.add(new ControlLabel(label + ":", labelX, y + 1));
        Slider slider = addRenderableWidget(new Slider(sliderX, y, sliderW, 8, 0, max, initial, consumer, colorAt));
        EditBox field = colorField(inputX, y - 3, inputW, label, value -> {
            consumer.accept(value);
            slider.setValue(value);
        }, max);
        lastColorField = field;
        int arrowX = inputX + inputW + 1;
        addRenderableWidget(Button.builder(Component.literal("↑"), button -> adjustColorField(field, 1, max))
            .pos(arrowX, y - 3).size(12, 7).build());
        addRenderableWidget(Button.builder(Component.literal("↓"), button -> adjustColorField(field, -1, max))
            .pos(arrowX, y + 4).size(12, 7).build());
        return slider;
    }

    private void adjustColorField(EditBox field, int delta, int max) {
        int current;
        try { current = Integer.parseInt(field.getValue()); }
        catch (NumberFormatException ignored) { current = 0; }
        field.setValue(Integer.toString(Math.clamp(current + delta, 0, max)));
    }

    private void adjustTextSize(int delta) {
        int value = Math.clamp(textSize + delta, 1, 200);
        setSelectedTextSize(value);
        setText(textSizeField, value);
        if (textSizeSlider != null) textSizeSlider.setValue(value);
    }

    private void setSelectedTextSize(int value) {
        textSize = Math.clamp(value, 1, 200);
        if (selectedText < 0 || selectedText >= texts.size()) return;
        EditableText selected = texts.get(selectedText);
        int oldNaturalWidth = naturalTextWidth(selected.text, selected.fontSize);
        int oldNaturalHeight = selected.fontSize;
        int newWidth = Math.max(1, Math.round(selected.width * naturalTextWidth(selected.text, textSize) / (float) Math.max(1, oldNaturalWidth)));
        int newHeight = Math.max(1, Math.round(selected.height * textSize / (float) Math.max(1, oldNaturalHeight)));
        selected.fontSize = textSize;
        selected.width = Math.min(newWidth, CANVAS_W - selected.x);
        selected.height = Math.min(newHeight, CANVAS_H - selected.y);
        dirty = true;
    }

    private void updateSelectedText(String value) {
        if (syncing || selectedText < 0 || selectedText >= texts.size()) return;
        EditableText selected = texts.get(selectedText);
        int oldNaturalWidth = naturalTextWidth(selected.text, selected.fontSize);
        selected.text = value;
        selected.width = Math.min(Math.max(1, Math.round(selected.width * naturalTextWidth(value, selected.fontSize)
            / (float) Math.max(1, oldNaturalWidth))), CANVAS_W - selected.x);
        dirty = true;
        refreshFloatingLayerWindow();
    }

    private static int gray(int value, int min, int max) {
        int shade = 45 + (value - min) * 180 / Math.max(1, max - min);
        return 0xFF000000 | shade << 16 | shade << 8 | shade;
    }

    private EditBox numericField(int x, int y, int w, String label, int initial, int min, int max, IntConsumer consumer) {
        EditBox field = new EditBox(font, x, y, w, 16, Component.literal(label));
        field.setValue(Integer.toString(initial));
        field.setResponder(text -> { try { consumer.accept(Math.clamp(Integer.parseInt(text), min, max)); } catch (NumberFormatException ignored) {} });
        addRenderableWidget(field);
        return field;
    }

    private EditBox colorField(int x, int y, int w, String label, IntConsumer consumer, int max) {
        EditBox field = new EditBox(font, x, y, w, 16, Component.literal(label));
        field.setResponder(text -> {
            if (syncing) return;
            try { consumer.accept(Math.clamp(Integer.parseInt(text), 0, max)); } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(field);
        return field;
    }

    private void pushUndo() {
        if (undo.size() >= MAX_UNDO) undo.removeFirst();
        undo.addLast(new Snapshot(copyPixelLayers(pixelLayers), copyTexts(texts), copyImages(images),
            new ArrayList<>(layerOrder), currentPixelLayerId, selectedLayer));
        if (undoButton != null) undoButton.active = true;
    }

    private void performUndo() {
        if (undo.isEmpty()) return;
        Snapshot snapshot = undo.removeLast();
        releasePixelLayerTextures(pixelLayers);
        releaseImageTextures(images);
        pixelLayers = copyPixelLayers(snapshot.pixelLayers());
        texts = copyTexts(snapshot.texts()); images = copyImages(snapshot.images());
        layerOrder = new ArrayList<>(snapshot.layerOrder());
        currentPixelLayerId = snapshot.currentPixelLayerId(); selectedLayer = snapshot.selectedLayer();
        selectCurrentPixelArray();
        clearSelection();
        dirty = true; textureDirty = true; fullTextureRefresh = true;
        undoButton.active = !undo.isEmpty();
        refreshFloatingLayerWindow();
    }

    private void save() {
        int display = parent.getSketchbookStack().getOrDefault(ModDataComponents.SKETCHBOOK_DISPLAY_PAGE, -1);
        save(display);
    }

    private void save(int displayPage) {
        List<SketchbookPage> pages = parent.pages();
        if (pageIndex >= pages.size()) return;
        SketchbookPage old = pages.get(pageIndex);
        if (dirty) lastEncodedData = serialize();
        pages.set(pageIndex, old.copy(resolveName(), old.getMode(), old.getTextContent(), lastEncodedData));
        parent.update(pages, colors, previousColor, displayPage);
        dirty = false;
    }

    private String resolveName() {
        String name = pageName == null ? "" : pageName.trim();
        if (!name.isEmpty()) return name;
        List<SketchbookPage> pages = parent.pages();
        if (pageIndex < pages.size() && !pages.get(pageIndex).getName().isEmpty()) {
            return pages.get(pageIndex).getName();
        }
        Set<String> names = new HashSet<>();
        for (SketchbookPage page : pages) names.add(page.getName());
        int number = 1;
        while (names.contains("未命名的页面" + number)) number++;
        return "未命名的页面" + number;
    }

    private void saveColorState() {
        int display = parent.getSketchbookStack().getOrDefault(ModDataComponents.SKETCHBOOK_DISPLAY_PAGE, -1);
        parent.update(parent.pages(), colors, previousColor, display);
    }

    private void display() {
        save(pageIndex);
        minecraft.setScreen(null);
    }

    private byte[] serialize() {
        return SketchbookDrawingData.encode(CANVAS_W, CANVAS_H,
            pixelLayers.stream().map(EditablePixelLayer::toData).toList(),
            texts.stream().filter(text -> !text.text.isEmpty()).map(EditableText::toData).toList(),
            images.stream().map(EditableImage::toData).toList(),
            layerOrder.stream().filter(ref -> ref.type() != SketchbookDrawingData.LayerType.TEXT
                || texts.stream().anyMatch(text -> text.id == ref.id() && !text.text.isEmpty())).toList());
    }

    private void load(byte[] data) {
        SketchbookDrawingData.Drawing drawing = SketchbookDrawingData.decode(data);
        if (drawing == null) return;
        int w = drawing.width(), h = drawing.height();
        pixelLayers = drawing.pixelLayers().stream().map(layer -> new EditablePixelLayer(layer.id(),
            scalePixels(layer.pixels(), w, h))).toList();
        pixelLayers = new ArrayList<>(pixelLayers);
        currentPixelLayerId = pixelLayers.getFirst().id;
        selectCurrentPixelArray();
        texts = drawing.texts().stream().map(text -> EditableText.fromData(text, w, h)).toList();
        texts = new ArrayList<>(texts);
        images = drawing.images().stream().map(image -> new EditableImage(image.id(), image.x() * CANVAS_W / w,
            image.y() * CANVAS_H / h, Math.max(1, image.width() * CANVAS_W / w),
            Math.max(1, image.height() * CANVAS_H / h), image.sourceWidth(), image.sourceHeight(), image.pixels().clone())).toList();
        images = new ArrayList<>(images);
        layerOrder = new ArrayList<>(drawing.layerOrder());
        selectedLayer = new SketchbookDrawingData.LayerRef(SketchbookDrawingData.LayerType.PIXEL, currentPixelLayerId);
    }

    private static int[] scalePixels(int[] source, int width, int height) {
        int[] scaled = new int[CANVAS_W * CANVAS_H];
        for (int y = 0; y < CANVAS_H; y++) for (int x = 0; x < CANVAS_W; x++)
            scaled[y * CANVAS_W + x] = source[Math.min(height - 1, y * height / CANVAS_H) * width
                + Math.min(width - 1, x * width / CANVAS_W)];
        return scaled;
    }

    private void setColor(int color) {
        if (color == currentColor) return;
        currentColor = color;
        if (tool == Tool.TEXT && selectedText >= 0 && selectedText < texts.size()) {
            texts.get(selectedText).color = color;
            dirty = true;
        }
        colorRevision++;
        syncFields();
    }

    private void usePreviousColor() {
        if (currentColor == previousColor) return;
        currentColor = previousColor;
        colorRevision++;
        syncFields();
    }

    private void setRgb(int r, int g, int b) { setColor((currentColor & 0xFF000000) | r << 16 | g << 8 | b); }
    private void setHue(int h) { int[] hsv = rgbToHsv(); setHsv(h, hsv[1], hsv[2]); }
    private void setSaturation(int s) { int[] hsv = rgbToHsv(); setHsv(hsv[0], s, hsv[2]); }
    private void setValue(int v) { int[] hsv = rgbToHsv(); setHsv(hsv[0], hsv[1], v); }

    private void setHsv(int h, int s, int v) {
        int rgb = hsvToRgb(h, s, v);
        setColor((currentColor & 0xFF000000) | rgb);
    }

    private int[] rgbToHsv() {
        float[] hsv = java.awt.Color.RGBtoHSB(currentColor >> 16 & 255, currentColor >> 8 & 255, currentColor & 255, null);
        return new int[]{Math.round(hsv[0] * 360), Math.round(hsv[1] * 100), Math.round(hsv[2] * 100)};
    }

    private static int hsvToRgb(int h, int s, int v) {
        return java.awt.Color.HSBtoRGB((h % 360) / 360.0F, s / 100.0F, v / 100.0F) & 0xFFFFFF;
    }

    private void syncFields() {
        if (syncing) return;
        syncing = true;
        try {
            int[] hsv = rgbToHsv();
            setText(red, currentColor >> 16 & 255); setText(green, currentColor >> 8 & 255); setText(blue, currentColor & 255);
            setText(alpha, currentColor >>> 24); setText(hue, hsv[0]); setText(saturation, hsv[1]); setText(value, hsv[2]);
            if (hex != null) hex.setValue(String.format(Locale.ROOT, "%06X", currentColor & 0xFFFFFF));
            if (redSlider != null) redSlider.setValue(currentColor >> 16 & 255);
            if (greenSlider != null) greenSlider.setValue(currentColor >> 8 & 255);
            if (blueSlider != null) blueSlider.setValue(currentColor & 255);
            if (hueSlider != null) hueSlider.setValue(hsv[0]);
            if (saturationSlider != null) saturationSlider.setValue(hsv[1]);
            if (valueSlider != null) valueSlider.setValue(hsv[2]);
            if (alphaSlider != null) alphaSlider.setValue(currentColor >>> 24);
            if (textSizeSlider != null) textSizeSlider.setValue(textSize);
        } finally { syncing = false; }
    }

    private static void setText(EditBox field, int value) {
        String text = Integer.toString(value);
        if (field != null && !field.getValue().equals(text)) field.setValue(text);
    }

    private void syncTextControls(boolean focus) {
        if (selectedText < 0 || selectedText >= texts.size()) return;
        EditableText selected = texts.get(selectedText);
        textSize = selected.fontSize;
        currentColor = selected.color;
        colorRevision++;
        syncing = true;
        try {
            if (textField != null) textField.setValue(selected.text);
            setText(textSizeField, textSize);
            if (textSizeSlider != null) textSizeSlider.setValue(textSize);
        } finally { syncing = false; }
        syncFields();
        if (focus && textField != null) setFocused(textField);
    }

    private int naturalTextWidth(String text, int fontSize) {
        return Math.max(1, Math.round(font.width(text) * fontSize / (float) font.lineHeight));
    }

    private void applyInitialText(String rawText) {
        String text = limitCodePoints(rawText.strip(), SketchbookDrawingData.MAX_TEXT_LENGTH);
        if (text.isEmpty()) return;
        List<String> lines = List.of();
        int chosenSize = 20;
        for (int candidate = 96; candidate >= 20; candidate -= 2) {
            List<String> candidateLines = wrapText(text, candidate, CANVAS_W - 48);
            int lineHeight = candidate + Math.max(4, candidate / 5);
            if (candidateLines.size() <= 10 && candidateLines.size() * lineHeight <= CANVAS_H - 40) {
                lines = candidateLines;
                chosenSize = candidate;
                break;
            }
        }
        if (lines.isEmpty()) lines = wrapText(text, chosenSize, CANVAS_W - 32);
        int lineHeight = chosenSize + Math.max(4, chosenSize / 5);
        int totalHeight = lines.size() * lineHeight - Math.max(4, chosenSize / 5);
        int y = Math.max(8, (CANVAS_H - totalHeight) / 2);
        for (String line : lines) {
            if (line.isEmpty() || texts.size() >= SketchbookDrawingData.MAX_TEXT_ELEMENTS) continue;
            int textWidth = Math.min(CANVAS_W - 16, naturalTextWidth(line, chosenSize));
            int x = Math.max(8, (CANVAS_W - textWidth) / 2);
            EditableText editableText = new EditableText(line, x, y, textWidth, chosenSize, chosenSize, currentColor);
            texts.add(editableText);
            layerOrder.add(new SketchbookDrawingData.LayerRef(SketchbookDrawingData.LayerType.TEXT, editableText.id));
            y += lineHeight;
        }
        if (!texts.isEmpty()) {
            selectedText = 0;
            selectionType = SelectionType.TEXT;
            selectedLayer = new SketchbookDrawingData.LayerRef(SketchbookDrawingData.LayerType.TEXT, texts.getFirst().id);
            textSize = chosenSize;
            dirty = true;
        }
    }

    private List<String> wrapText(String text, int fontSize, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n') {
                lines.add(line.toString());
                line.setLength(0);
                continue;
            }
            String next = new String(Character.toChars(codePoint));
            if (!line.isEmpty() && naturalTextWidth(line + next, fontSize) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(next);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private static String limitCodePoints(String text, int max) {
        int count = text.codePointCount(0, text.length());
        return count <= max ? text : text.substring(0, text.offsetByCodePoints(0, max));
    }

    private static List<EditableText> copyTexts(List<EditableText> source) {
        return new ArrayList<>(source.stream().map(EditableText::copy).toList());
    }

    private List<EditablePixelLayer> copyPixelLayers(List<EditablePixelLayer> source) {
        return new ArrayList<>(source.stream().map(EditablePixelLayer::copy).toList());
    }

    private static List<EditableImage> copyImages(List<EditableImage> source) {
        return new ArrayList<>(source.stream().map(EditableImage::copy).toList());
    }

    private void clearSelection() {
        selectedText = -1;
        selectedImage = -1;
        selectionType = SelectionType.NONE;
    }

    private void selectCurrentPixelArray() {
        EditablePixelLayer layer = pixelLayers.stream().filter(candidate -> candidate.id == currentPixelLayerId)
            .findFirst().orElse(pixelLayers.getFirst());
        currentPixelLayerId = layer.id;
        pixels = layer.pixels;
    }

    private EditablePixelLayer currentPixelLayer() {
        return pixelLayers.stream().filter(layer -> layer.id == currentPixelLayerId).findFirst().orElse(null);
    }

    private void selectLayer(SketchbookDrawingData.LayerRef ref) {
        selectedLayer = ref;
        if (ref.type() == SketchbookDrawingData.LayerType.PIXEL) {
            currentPixelLayerId = ref.id();
            selectCurrentPixelArray();
            clearSelection();
        } else if (ref.type() == SketchbookDrawingData.LayerType.TEXT) {
            selectedText = indexOfText(ref.id()); selectedImage = -1; selectionType = SelectionType.TEXT;
            if (panelMode == PanelMode.TEXT) syncTextControls(false);
        } else {
            selectedImage = indexOfImage(ref.id()); selectedText = -1; selectionType = SelectionType.IMAGE;
        }
        textureDirty = true; fullTextureRefresh = true;
        refreshFloatingLayerWindow();
    }

    private int indexOfText(long id) {
        for (int i = 0; i < texts.size(); i++) if (texts.get(i).id == id) return i;
        return -1;
    }

    private int indexOfImage(long id) {
        for (int i = 0; i < images.size(); i++) if (images.get(i).id == id) return i;
        return -1;
    }

    private void addPixelLayer() {
        if (pixelLayers.size() >= SketchbookDrawingData.MAX_PIXEL_LAYERS) return;
        pushUndo();
        int[] layerPixels = new int[CANVAS_W * CANVAS_H];
        Arrays.fill(layerPixels, CLEAR);
        EditablePixelLayer layer = new EditablePixelLayer(SketchbookDrawingData.newId(), layerPixels);
        pixelLayers.add(layer);
        SketchbookDrawingData.LayerRef ref = new SketchbookDrawingData.LayerRef(SketchbookDrawingData.LayerType.PIXEL, layer.id);
        layerOrder.add(ref);
        currentPixelLayerId = layer.id;
        pixels = layer.pixels;
        selectedLayer = ref;
        dirty = true; textureDirty = true; fullTextureRefresh = true;
        refreshLayerUi();
    }

    private void moveSelectedLayer(int direction) {
        if (selectedLayer == null) return;
        int index = layerOrder.indexOf(selectedLayer);
        int target = Math.clamp(index + direction, 0, layerOrder.size() - 1);
        if (index < 0 || index == target) return;
        pushUndo();
        layerOrder.remove(index);
        layerOrder.add(target, selectedLayer);
        dirty = true; textureDirty = true; fullTextureRefresh = true;
        refreshLayerUi();
    }

    private void deleteSelectedLayer() {
        if (selectedLayer == null) return;
        if (selectedLayer.type() == SketchbookDrawingData.LayerType.PIXEL && pixelLayers.size() <= 1) return;
        int oldOrderIndex = layerOrder.indexOf(selectedLayer);
        pushUndo();
        if (selectedLayer.type() == SketchbookDrawingData.LayerType.PIXEL) {
            EditablePixelLayer removed = pixelLayers.stream().filter(layer -> layer.id == selectedLayer.id()).findFirst().orElse(null);
            if (removed != null) { removed.releaseTexture(); pixelLayers.remove(removed); }
        } else if (selectedLayer.type() == SketchbookDrawingData.LayerType.TEXT) {
            int index = indexOfText(selectedLayer.id());
            if (index >= 0) texts.remove(index);
        } else {
            int index = indexOfImage(selectedLayer.id());
            if (index >= 0) images.remove(index).releaseTexture();
        }
        layerOrder.remove(selectedLayer);
        clearSelection();
        selectedLayer = layerOrder.get(Math.clamp(oldOrderIndex, 0, layerOrder.size() - 1));
        selectLayer(selectedLayer);
        dirty = true; textureDirty = true; fullTextureRefresh = true;
        refreshLayerUi();
    }

    private void refreshLayerUi() {
        refreshFloatingLayerWindow();
        if (panelMode == PanelMode.LAYERS && minecraft != null && minecraft.screen == this) rebuildWidgets();
    }

    private void openFloatingLayerWindow() {
        if (floatingLayerWindowOpen) return;
        floatingLayerWindowOpen = true;
        tool = Tool.PENCIL;
        panelMode = PanelMode.DRAW;
        if (layerToolButton != null) layerToolButton.active = false;
        SwingUtilities.invokeLater(() -> {
            try {
                if (!floatingLayerWindowOpen) return;
                JFrame frame = new JFrame();
                DefaultListModel<String> model = new DefaultListModel<>();
                JList<String> list = new JList<>(model);
                layerFrame = frame; layerListModel = model; layerList = list;
                Color background = new Color(24, 26, 30);
                Color surface = new Color(34, 37, 43);
                Color accent = new Color(67, 126, 196);
                frame.setUndecorated(true);
                frame.setBackground(background);
                frame.setIconImage(createLayerWindowIcon());
                JPanel root = new JPanel(new BorderLayout(0, 0));
                root.setBackground(background);
                root.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(76, 82, 92), 1),
                    BorderFactory.createEmptyBorder(0, 0, 6, 0)));

                JPanel titleBar = new JPanel(new BorderLayout());
                titleBar.setBackground(new Color(29, 32, 37));
                titleBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 6));
                JLabel title = new JLabel("图层");
                title.setForeground(new Color(238, 240, 244));
                title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
                ModernSwingButton close = new ModernSwingButton("X", new Color(29, 32, 37), new Color(184, 61, 67));
                close.setPreferredSize(new Dimension(34, 26));
                close.addActionListener(event -> frame.dispose());
                titleBar.add(title, BorderLayout.WEST);
                titleBar.add(close, BorderLayout.EAST);
                installWindowDrag(titleBar, frame);

                list.setBackground(surface);
                list.setForeground(new Color(225, 228, 233));
                list.setSelectionBackground(accent);
                list.setSelectionForeground(Color.WHITE);
                list.setFixedCellHeight(34);
                list.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
                list.setCellRenderer(new ModernLayerCellRenderer());
                list.addListSelectionListener(event -> {
                    if (event.getValueIsAdjusting() || syncingFloatingLayerList) return;
                    int displayIndex = list.getSelectedIndex();
                    if (displayIndex < 0 || minecraft == null) return;
                    minecraft.execute(() -> {
                        int index = layerOrder.size() - 1 - displayIndex;
                        if (index >= 0 && index < layerOrder.size()) selectLayer(layerOrder.get(index));
                    });
                });
                JPanel buttons = new JPanel(new GridLayout(1, 4, 6, 0));
                buttons.setBackground(background);
                buttons.setBorder(BorderFactory.createEmptyBorder(8, 8, 2, 8));
                JButton add = new ModernSwingButton(SwingButtonIcon.ADD, "新建画面图层",
                    new Color(49, 93, 140), new Color(62, 112, 166));
                JButton up = new ModernSwingButton(SwingButtonIcon.UP, "上移图层",
                    new Color(61, 66, 75), new Color(76, 82, 92));
                JButton down = new ModernSwingButton(SwingButtonIcon.DOWN, "下移图层",
                    new Color(61, 66, 75), new Color(76, 82, 92));
                JButton delete = new ModernSwingButton(SwingButtonIcon.DELETE, "删除所选图层",
                    new Color(132, 57, 64), new Color(162, 67, 75));
                add.addActionListener(event -> executeClient(this::addPixelLayer));
                up.addActionListener(event -> executeClient(() -> moveSelectedLayer(1)));
                down.addActionListener(event -> executeClient(() -> moveSelectedLayer(-1)));
                delete.addActionListener(event -> executeClient(this::deleteSelectedLayer));
                buttons.add(add); buttons.add(up); buttons.add(down); buttons.add(delete);
                JScrollPane scrollPane = new JScrollPane(list);
                scrollPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
                scrollPane.getViewport().setBackground(surface);
                scrollPane.getVerticalScrollBar().setUnitIncrement(16);
                JPanel footer = new JPanel(new BorderLayout());
                footer.setBackground(background);
                footer.add(buttons, BorderLayout.CENTER);
                JLabel resizeGrip = new JLabel("///");
                resizeGrip.setForeground(new Color(108, 114, 124));
                resizeGrip.setBorder(BorderFactory.createEmptyBorder(12, 4, 0, 8));
                resizeGrip.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
                installWindowResize(resizeGrip, frame);
                footer.add(resizeGrip, BorderLayout.EAST);
                root.add(titleBar, BorderLayout.NORTH);
                root.add(scrollPane, BorderLayout.CENTER);
                root.add(footer, BorderLayout.SOUTH);
                frame.setContentPane(root);
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setMinimumSize(new Dimension(360, 240));
                frame.setSize(420, 420);
                frame.setLocationRelativeTo(null);
                frame.setAutoRequestFocus(false);
                frame.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosed(WindowEvent event) {
                        floatingLayerWindowOpen = false;
                        layerFrame = null; layerListModel = null; layerList = null;
                        executeClient(() -> { if (layerToolButton != null) layerToolButton.active = true; });
                    }
                });
                executeClient(SketchbookDrawingScreen.this::refreshFloatingLayerWindow);
                if (!floatingLayerWindowOpen) { frame.dispose(); return; }
                frame.setAlwaysOnTop(true);
                frame.setVisible(true);
            } catch (Throwable error) {
                floatingLayerWindowOpen = false;
                layerFrame = null; layerListModel = null; layerList = null;
                executeClient(() -> {
                    if (layerToolButton != null) layerToolButton.active = true;
                    if (minecraft != null && minecraft.player != null) {
                        minecraft.player.displayClientMessage(Component.literal("无法打开图层窗口: " + error.getClass().getSimpleName()), false);
                    }
                    rebuildWidgets();
                });
            }
        });
        rebuildWidgets();
    }

    private void executeClient(Runnable action) {
        if (minecraft != null) minecraft.execute(action);
    }

    private static void installWindowDrag(JPanel titleBar, JFrame frame) {
        MouseAdapter drag = new MouseAdapter() {
            private Point start;

            @Override public void mousePressed(MouseEvent event) {
                start = event.getPoint();
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (start == null) return;
                Point screen = event.getLocationOnScreen();
                frame.setLocation(screen.x - start.x, screen.y - start.y);
            }
        };
        titleBar.addMouseListener(drag);
        titleBar.addMouseMotionListener(drag);
    }

    private static void installWindowResize(JLabel grip, JFrame frame) {
        MouseAdapter resize = new MouseAdapter() {
            private Point start;
            private Dimension initial;

            @Override public void mousePressed(MouseEvent event) {
                start = event.getLocationOnScreen();
                initial = frame.getSize();
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (start == null || initial == null) return;
                Point current = event.getLocationOnScreen();
                int width = Math.max(frame.getMinimumSize().width, initial.width + current.x - start.x);
                int height = Math.max(frame.getMinimumSize().height, initial.height + current.y - start.y);
                frame.setSize(width, height);
            }
        };
        grip.addMouseListener(resize);
        grip.addMouseMotionListener(resize);
    }

    private static final class ModernLayerCellRenderer extends DefaultListCellRenderer {
        @Override public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                          boolean selected, boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 10));
            label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            label.setBackground(selected ? new Color(55, 105, 164)
                : index % 2 == 0 ? new Color(38, 41, 47) : new Color(34, 37, 43));
            label.setForeground(selected ? Color.WHITE : new Color(218, 222, 228));
            return label;
        }
    }

    private enum SwingButtonIcon { NONE, ADD, UP, DOWN, DELETE }

    private static final class ModernSwingButton extends JButton {
        private final Color base;
        private final Color hover;
        private final SwingButtonIcon icon;

        private ModernSwingButton(String text, Color base, Color hover) {
            this(SwingButtonIcon.NONE, text, base, hover);
        }

        private ModernSwingButton(SwingButtonIcon icon, String tooltip, Color base, Color hover) {
            super(icon == SwingButtonIcon.NONE ? tooltip : "");
            this.base = base;
            this.hover = hover;
            this.icon = icon;
            setForeground(Color.WHITE);
            setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setOpaque(false);
            if (icon != SwingButtonIcon.NONE) setToolTipText(tooltip);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getModel().isRollover() ? hover : base);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            if (icon != SwingButtonIcon.NONE) paintIcon(g2, icon, getWidth() / 2, getHeight() / 2);
            g2.dispose();
            super.paintComponent(graphics);
        }

        private static void paintIcon(Graphics2D graphics, SwingButtonIcon icon, int cx, int cy) {
            graphics.setColor(Color.WHITE);
            graphics.setStroke(new java.awt.BasicStroke(2.0F, java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
            switch (icon) {
                case ADD -> {
                    graphics.drawLine(cx - 5, cy, cx + 5, cy);
                    graphics.drawLine(cx, cy - 5, cx, cy + 5);
                }
                case UP -> {
                    graphics.drawLine(cx, cy - 6, cx, cy + 6);
                    graphics.drawLine(cx, cy - 6, cx - 5, cy - 1);
                    graphics.drawLine(cx, cy - 6, cx + 5, cy - 1);
                }
                case DOWN -> {
                    graphics.drawLine(cx, cy - 6, cx, cy + 6);
                    graphics.drawLine(cx, cy + 6, cx - 5, cy + 1);
                    graphics.drawLine(cx, cy + 6, cx + 5, cy + 1);
                }
                case DELETE -> {
                    graphics.drawRoundRect(cx - 5, cy - 4, 10, 10, 2, 2);
                    graphics.drawLine(cx - 7, cy - 7, cx + 7, cy - 7);
                    graphics.drawLine(cx - 3, cy - 9, cx + 3, cy - 9);
                }
                default -> {}
            }
        }
    }

    private static BufferedImage createLayerWindowIcon() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(28, 31, 36));
        graphics.fillRoundRect(1, 1, 30, 30, 8, 8);
        graphics.setStroke(new java.awt.BasicStroke(2.2F, java.awt.BasicStroke.CAP_ROUND,
            java.awt.BasicStroke.JOIN_ROUND));
        graphics.setColor(new Color(111, 169, 232));
        int[] topX = {7, 16, 25, 16};
        int[] topY = {11, 6, 11, 16};
        graphics.drawPolygon(topX, topY, 4);
        graphics.setColor(new Color(83, 130, 184));
        graphics.drawLine(7, 16, 16, 21);
        graphics.drawLine(16, 21, 25, 16);
        graphics.setColor(new Color(65, 98, 137));
        graphics.drawLine(7, 21, 16, 26);
        graphics.drawLine(16, 26, 25, 21);
        graphics.dispose();
        return image;
    }

    private void refreshFloatingLayerWindow() {
        if (!floatingLayerWindowOpen) return;
        List<SketchbookDrawingData.LayerRef> order = List.copyOf(layerOrder);
        SketchbookDrawingData.LayerRef selected = selectedLayer;
        List<String> names = order.reversed().stream().map(this::layerName).toList();
        int selectedIndex = -1;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(order.size() - 1 - i).equals(selected)) { selectedIndex = i; break; }
        }
        int selection = selectedIndex;
        SwingUtilities.invokeLater(() -> {
            DefaultListModel<String> model = layerListModel;
            JList<String> list = layerList;
            if (model == null || list == null) return;
            syncingFloatingLayerList = true;
            try {
                model.clear();
                names.forEach(model::addElement);
                if (selection >= 0) list.setSelectedIndex(selection);
            } finally {
                syncingFloatingLayerList = false;
            }
        });
    }

    private void disposeFloatingLayerWindow() {
        floatingLayerWindowOpen = false;
        JFrame frame = layerFrame;
        layerFrame = null; layerListModel = null; layerList = null;
        if (frame != null) SwingUtilities.invokeLater(frame::dispose);
    }

    private String layerName(SketchbookDrawingData.LayerRef ref) {
        int number = layerOrder.indexOf(ref) + 1;
        return switch (ref.type()) {
            case PIXEL -> "画面图层 " + number;
            case IMAGE -> "图片图层 " + number;
            case TEXT -> {
                int index = indexOfText(ref.id());
                String value = index >= 0 ? texts.get(index).text : "";
                yield "文字图层 " + number + (value.isEmpty() ? "" : " - " + limitCodePoints(value, 16));
            }
        };
    }

    private void openImageChooser() {
        Thread.startVirtualThread(() -> {
            String selected;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(3);
                filters.put(stack.UTF8("*.png"));
                filters.put(stack.UTF8("*.jpg"));
                filters.put(stack.UTF8("*.jpeg"));
                filters.flip();
                selected = TinyFileDialogs.tinyfd_openFileDialog("导入素描本图片", "", filters,
                    "PNG / JPG 图片", true);
            }
            if (selected == null || selected.isBlank()) return;
            List<BufferedImage> imported = Arrays.stream(selected.split("\\|"))
                .map(Path::of).filter(SketchbookDrawingScreen::isSupportedImage).map(path -> {
                    try { return ImageIO.read(path.toFile()); }
                    catch (IOException ignored) { return null; }
                }).filter(image -> image != null).toList();
            if (minecraft != null) minecraft.execute(() -> addImportedImages(imported));
        });
    }

    @Override public void onFilesDrop(List<Path> paths) {
        Thread.startVirtualThread(() -> {
            List<BufferedImage> imported = paths.stream().filter(SketchbookDrawingScreen::isSupportedImage).map(path -> {
                try { return ImageIO.read(path.toFile()); }
                catch (IOException ignored) { return null; }
            }).filter(image -> image != null).toList();
            if (minecraft != null) minecraft.execute(() -> {
                panelMode = PanelMode.IMAGE;
                tool = Tool.IMAGE;
                addImportedImages(imported);
            });
        });
    }

    private static boolean isSupportedImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private void addImportedImages(List<BufferedImage> imported) {
        for (BufferedImage source : imported) {
            int sourceW = source.getWidth(), sourceH = source.getHeight();
            if (sourceW <= 0 || sourceH <= 0) continue;
            double scale = Math.min(1.0, Math.min(512.0 / sourceW, 512.0 / sourceH));
            int targetW = Math.max(1, (int) Math.round(sourceW * scale));
            int targetH = Math.max(1, (int) Math.round(sourceH * scale));
            BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaled.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, targetW, targetH, null);
            graphics.dispose();
            imageLibrary.add(new LibraryImage(targetW, targetH, scaled.getRGB(0, 0, targetW, targetH, null, 0, targetW)));
        }
        if (!imported.isEmpty()) rebuildWidgets();
    }

    private void placeLibraryImage(int index) {
        if (index < 0 || index >= imageLibrary.size() || images.size() >= SketchbookDrawingData.MAX_IMAGE_ELEMENTS) return;
        LibraryImage source = imageLibrary.get(index);
        int usedPixels = images.stream().mapToInt(image -> image.sourceWidth * image.sourceHeight).sum();
        if (usedPixels + source.width * source.height > SketchbookDrawingData.MAX_IMAGE_SOURCE_PIXELS) return;
        double scale = Math.min((CANVAS_W * .8) / source.width, (CANVAS_H * .8) / source.height);
        int drawW = Math.max(1, (int) Math.round(source.width * scale));
        int drawH = Math.max(1, (int) Math.round(source.height * scale));
        pushUndo();
        EditableImage image = new EditableImage((CANVAS_W - drawW) / 2, (CANVAS_H - drawH) / 2, drawW, drawH,
            source.width, source.height, source.pixels.clone());
        images.add(image);
        layerOrder.add(new SketchbookDrawingData.LayerRef(SketchbookDrawingData.LayerType.IMAGE, image.id));
        selectedImage = images.size() - 1;
        selectedText = -1;
        selectionType = SelectionType.IMAGE;
        selectedLayer = new SketchbookDrawingData.LayerRef(SketchbookDrawingData.LayerType.IMAGE, image.id);
        tool = Tool.SELECT;
        dirty = true;
        rebuildWidgets();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        updateCursorVisibility(mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, delta);
        for (ControlLabel label : controlLabels) {
            graphics.drawString(font, label.text(), label.x(), label.y(), 0xFFDDDDDD, false);
        }
        if (clearConfirm || libraryDeleteConfirm) {
            graphics.fill(0, 0, width, height, 0xAA000000);
            int cx = width / 2, cy = height / 2;
            graphics.fill(cx - 110, cy - 32, cx + 110, cy + 32, 0xFF333333);
            graphics.drawCenteredString(font, libraryDeleteConfirm ? "确定删除所选图片库项目？" : "确定清除？", cx, cy - 20, 0xFFFFFFFF);
            graphics.fill(cx - 100, cy, cx - 5, cy + 20, 0xFF448844);
            graphics.fill(cx + 5, cy, cx + 100, cy + 20, 0xFF884444);
            graphics.drawCenteredString(font, "确认", cx - 52, cy + 6, 0xFFFFFFFF);
            graphics.drawCenteredString(font, "取消", cx + 52, cy + 6, 0xFFFFFFFF);
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (clearConfirm || libraryDeleteConfirm) {
            int cx = width / 2, cy = height / 2;
            if (mouseY >= cy && mouseY <= cy + 20 && mouseX >= cx - 100 && mouseX <= cx - 5) {
                if (libraryDeleteConfirm) {
                    List<Integer> indexes = libraryDeleteSelection.stream().sorted((a, b) -> Integer.compare(b, a)).toList();
                    for (int index : indexes) if (index >= 0 && index < imageLibrary.size()) imageLibrary.remove(index).releaseTexture();
                    libraryDeleteSelection.clear();
                    libraryDeleteMode = false;
                    rebuildWidgets();
                } else {
                    pushUndo();
                    releasePixelLayerTextures(pixelLayers); releaseImageTextures(images);
                    pixelLayers.clear(); texts.clear(); images.clear(); layerOrder.clear(); clearSelection();
                    int[] layerPixels = new int[CANVAS_W * CANVAS_H]; Arrays.fill(layerPixels, CLEAR);
                    EditablePixelLayer layer = new EditablePixelLayer(SketchbookDrawingData.newId(), layerPixels);
                    pixelLayers.add(layer); currentPixelLayerId = layer.id; pixels = layer.pixels;
                    selectedLayer = new SketchbookDrawingData.LayerRef(SketchbookDrawingData.LayerType.PIXEL, layer.id);
                    layerOrder.add(selectedLayer);
                    dirty = true; textureDirty = true; fullTextureRefresh = true;
                    refreshLayerUi();
                }
            }
            clearConfirm = false;
            libraryDeleteConfirm = false;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Manosaba_ItemClient.isSketchbookDisplayKey(keyCode, scanCode)) {
            display();
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE)
            && !(getFocused() instanceof EditBox) && selectionType != SelectionType.NONE) {
            pushUndo();
            if (selectionType == SelectionType.TEXT && selectedText >= 0 && selectedText < texts.size()) {
                EditableText removed = texts.remove(selectedText);
                layerOrder.remove(new SketchbookDrawingData.LayerRef(SketchbookDrawingData.LayerType.TEXT, removed.id));
            }
            if (selectionType == SelectionType.IMAGE && selectedImage >= 0 && selectedImage < images.size()) {
                EditableImage removed = images.remove(selectedImage); removed.releaseTexture();
                layerOrder.remove(new SketchbookDrawingData.LayerRef(SketchbookDrawingData.LayerType.IMAGE, removed.id));
            }
            clearSelection();
            selectedLayer = new SketchbookDrawingData.LayerRef(SketchbookDrawingData.LayerType.PIXEL, currentPixelLayerId);
            dirty = true;
            refreshLayerUi();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void onClose() { save(); minecraft.setScreen(parent); }
    @Override public void removed() {
        disposeFloatingLayerWindow();
        setCursorHidden(false);
        releaseSliderTextures();
        if (colorWheelTextureLocation != null && minecraft != null) {
            minecraft.getTextureManager().release(colorWheelTextureLocation);
            colorWheelTextureLocation = null;
            colorWheelTexture = null;
        }
        releaseImageTextures(images);
        releasePixelLayerTextures(pixelLayers);
        for (LibraryImage image : imageLibrary) image.releaseTexture();
    }
    @Override public boolean isPauseScreen() { return false; }

    private void updateCursorVisibility(double mouseX, double mouseY) {
        boolean hide = canvas != null && canvas.isMouseOver(mouseX, mouseY)
            && (tool == Tool.BRUSH || tool == Tool.ERASER || tool == Tool.TEXT
                || tool == Tool.SELECT && selectionType != SelectionType.NONE);
        setCursorHidden(hide);
    }

    private void setCursorHidden(boolean hidden) {
        if (cursorHidden == hidden || minecraft == null) return;
        cursorHidden = hidden;
        GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR,
            hidden ? GLFW.GLFW_CURSOR_HIDDEN : GLFW.GLFW_CURSOR_NORMAL);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("manosaba_item", "textures/item/" + name);
    }

    private void releaseSliderTextures() {
        for (Slider slider : sliders) slider.releaseTexture();
    }

    private void createColorWheelTexture() {
        int size = 48;
        NativeImage image = new NativeImage(size, size, false);
        int radius = size / 2 - 1;
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int dx = px - size / 2, dy = py - size / 2;
                double distance = Math.sqrt(dx * dx + dy * dy);
                int color = 0;
                if (distance <= radius) {
                    int h = (int) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360);
                    int s = Math.clamp((int) Math.round(distance * 100 / radius), 0, 100);
                    color = 0xFF000000 | hsvToRgb(h, s, 100);
                }
                image.setPixel(px, py, color);
            }
        }
        colorWheelTexture = new DynamicTexture(() -> "manosaba sketchbook color wheel", image);
        colorWheelTextureLocation = ResourceLocation.fromNamespaceAndPath("manosaba_item",
            "sketchbook_color_wheel/" + Integer.toUnsignedString(System.identityHashCode(this)));
        minecraft.getTextureManager().register(colorWheelTextureLocation, colorWheelTexture);
    }

    private final class Canvas extends AbstractWidget {
        private boolean painting;
        private int lastX = -1, lastY = -1;
        private TextAction textAction = TextAction.NONE;
        private int actionStartX, actionStartY, originalX, originalY, originalWidth, originalHeight;
        Canvas(int x, int y, int w, int h) { super(x, y, w, h, Component.literal("画布")); }

        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.fill(getX(), getY(), getRight(), getBottom(), 0xFFFFFFFF);
            EditablePixelLayer currentLayer = currentPixelLayer();
            if (currentLayer != null) currentLayer.ensureTexture();
            if (fullTextureRefresh && currentLayer != null) {
                currentLayer.updateTexture();
                fullTextureRefresh = false;
            }
            long now = System.nanoTime();
            if (textureDirty && currentLayer != null && currentLayer.texture != null
                && (!painting || now - lastTextureUploadNanos >= 33_000_000L)) {
                currentLayer.texture.upload();
                textureDirty = false;
                lastTextureUploadNanos = now;
            }
            renderLayers(g);
            if (isMouseOver(mouseX, mouseY) && (tool == Tool.BRUSH || tool == Tool.ERASER)) {
                int radius = Math.max(1, Math.round(brushSize * getWidth() / (2.0F * CANVAS_W)));
                drawCursorCircle(g, mouseX, mouseY, radius);
            }
            if (tool == Tool.TEXT || tool == Tool.SELECT) {
                if (selectionType == SelectionType.TEXT && selectedText >= 0 && selectedText < texts.size()) drawSelection(g, texts.get(selectedText));
                if (selectionType == SelectionType.IMAGE && selectedImage >= 0 && selectedImage < images.size()) drawSelection(g, images.get(selectedImage));
                if (isMouseOver(mouseX, mouseY)) drawTextModeCursor(g, mouseX, mouseY);
            }
            g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF111111);
        }

        private void renderLayers(GuiGraphics graphics) {
            for (SketchbookDrawingData.LayerRef ref : layerOrder) {
                if (ref.type() == SketchbookDrawingData.LayerType.PIXEL) {
                    EditablePixelLayer layer = pixelLayers.stream().filter(candidate -> candidate.id == ref.id()).findFirst().orElse(null);
                    if (layer == null) continue;
                    layer.ensureTexture();
                    if (layer.textureLocation != null) graphics.blit(RenderPipelines.GUI_TEXTURED, layer.textureLocation,
                        getX(), getY(), 0, 0, getWidth(), getHeight(), CANVAS_W, CANVAS_H, CANVAS_W, CANVAS_H);
                } else if (ref.type() == SketchbookDrawingData.LayerType.IMAGE) {
                    int index = indexOfImage(ref.id());
                    if (index >= 0) renderImage(graphics, images.get(index));
                } else {
                    int index = indexOfText(ref.id());
                    if (index >= 0) renderText(graphics, texts.get(index));
                }
            }
        }

        private void renderImage(GuiGraphics graphics, EditableImage image) {
            image.ensureTexture();
            if (image.textureLocation == null) return;
            int left = screenX(image.x), top = screenY(image.y);
            int right = screenX(image.x + image.width), bottom = screenY(image.y + image.height);
            graphics.blit(RenderPipelines.GUI_TEXTURED, image.textureLocation, left, top, 0, 0,
                Math.max(1, right - left), Math.max(1, bottom - top), image.sourceWidth, image.sourceHeight,
                image.sourceWidth, image.sourceHeight);
        }

        private void renderText(GuiGraphics graphics, EditableText text) {
                float screenX = getX() + text.x * getWidth() / (float) CANVAS_W;
                float screenY = getY() + text.y * getHeight() / (float) CANVAS_H;
                float screenW = text.width * getWidth() / (float) CANVAS_W;
                float screenH = text.height * getHeight() / (float) CANVAS_H;
                float scaleX = screenW / Math.max(1, font.width(text.text));
                float scaleY = screenH / Math.max(1, font.lineHeight);
                graphics.pose().pushMatrix();
                graphics.pose().translate(screenX, screenY);
                graphics.pose().scale(scaleX, scaleY);
                graphics.drawString(font, text.text, 0, 0, text.color, false);
                graphics.pose().popMatrix();
        }

        private void drawSelection(GuiGraphics graphics, Bounds object) {
            int left = screenX(object.x()), top = screenY(object.y());
            int right = screenX(object.x() + object.width()), bottom = screenY(object.y() + object.height());
            graphics.renderOutline(left, top, Math.max(1, right - left), Math.max(1, bottom - top), 0xFF2288FF);
            int middleX = (left + right) / 2, middleY = (top + bottom) / 2;
            drawHandle(graphics, left, top); drawHandle(graphics, middleX, top); drawHandle(graphics, right, top);
            drawHandle(graphics, left, middleY); drawHandle(graphics, right, middleY);
            drawHandle(graphics, left, bottom); drawHandle(graphics, middleX, bottom); drawHandle(graphics, right, bottom);
        }

        private void drawHandle(GuiGraphics graphics, int x, int y) {
            graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFFFFFFFF);
            graphics.renderOutline(x - 2, y - 2, 5, 5, 0xFF2288FF);
        }

        private void drawTextCursor(GuiGraphics graphics, int x, int y) {
            graphics.fill(x, y - 6, x + 1, y + 7, 0xFF111111);
            graphics.fill(x - 3, y - 6, x + 4, y - 5, 0xFF111111);
            graphics.fill(x - 3, y + 6, x + 4, y + 7, 0xFF111111);
        }

        private void drawTextModeCursor(GuiGraphics graphics, int mouseX, int mouseY) {
            TextAction action = textAction;
            if (action == TextAction.NONE) {
                int canvasX = toCanvasX(mouseX), canvasY = toCanvasY(mouseY);
                Bounds selected = selectedBounds();
                if (selected != null && containsWithMargin(selected, canvasX, canvasY)) action = hitAction(selected, canvasX, canvasY);
                else action = tool == Tool.TEXT ? TextAction.EDIT : TextAction.NONE;
            }
            switch (action) {
                case LEFT, RIGHT -> drawDoubleArrow(graphics, mouseX, mouseY, 1, 0);
                case TOP, BOTTOM -> drawDoubleArrow(graphics, mouseX, mouseY, 0, 1);
                case TOP_LEFT, BOTTOM_RIGHT -> drawDoubleArrow(graphics, mouseX, mouseY, 1, 1);
                case TOP_RIGHT, BOTTOM_LEFT -> drawDoubleArrow(graphics, mouseX, mouseY, 1, -1);
                case MOVE -> drawMoveCursor(graphics, mouseX, mouseY);
                default -> { if (tool == Tool.TEXT) drawTextCursor(graphics, mouseX, mouseY); }
            }
        }

        private void drawDoubleArrow(GuiGraphics graphics, int centerX, int centerY, int directionX, int directionY) {
            int perpendicularX = -directionY, perpendicularY = directionX;
            int length = 10;
            for (int step = -length; step <= length; step++) cursorPoint(graphics,
                centerX + directionX * step, centerY + directionY * step);
            for (int side : new int[]{-1, 1}) {
                int endX = centerX + directionX * length * side;
                int endY = centerY + directionY * length * side;
                for (int step = 1; step <= 4; step++) {
                    cursorPoint(graphics, endX - directionX * step * side + perpendicularX * step,
                        endY - directionY * step * side + perpendicularY * step);
                    cursorPoint(graphics, endX - directionX * step * side - perpendicularX * step,
                        endY - directionY * step * side - perpendicularY * step);
                }
            }
        }

        private void drawMoveCursor(GuiGraphics graphics, int centerX, int centerY) {
            drawDoubleArrow(graphics, centerX, centerY, 1, 0);
            drawDoubleArrow(graphics, centerX, centerY, 0, 1);
            graphics.fill(centerX, centerY, centerX + 1, centerY + 1, 0xFF111111);
        }

        private void cursorPoint(GuiGraphics graphics, int x, int y) {
            if (x >= getX() && x < getRight() && y >= getY() && y < getBottom())
                graphics.fill(x, y, x + 1, y + 1, 0xFF111111);
        }

        private void drawCursorCircle(GuiGraphics graphics, int cx, int cy, int radius) {
            int color = tool == Tool.ERASER ? 0x88FF6666 : 0x40000000;
            int x = radius, y = 0, error = 1 - radius;
            while (x >= y) {
                plotCirclePoints(graphics, cx, cy, x, y, color);
                y++;
                if (error < 0) error += 2 * y + 1;
                else { x--; error += 2 * (y - x) + 1; }
            }
        }

        private void plotCirclePoints(GuiGraphics graphics, int cx, int cy, int x, int y, int color) {
            plotPoint(graphics, cx + x, cy + y, color); plotPoint(graphics, cx + y, cy + x, color);
            plotPoint(graphics, cx - y, cy + x, color); plotPoint(graphics, cx - x, cy + y, color);
            plotPoint(graphics, cx - x, cy - y, color); plotPoint(graphics, cx - y, cy - x, color);
            plotPoint(graphics, cx + y, cy - x, color); plotPoint(graphics, cx + x, cy - y, color);
        }

        private void plotPoint(GuiGraphics graphics, int x, int y, int color) {
            if (x >= getX() && x < getRight() && y >= getY() && y < getBottom())
                graphics.fill(x, y, x + 1, y + 1, color);
        }

        @Override public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !isMouseOver(mx, my)) return false;
            int cx = toCanvasX(mx), cy = toCanvasY(my);
            if (tool == Tool.TEXT) return beginTextAction(cx, cy);
            if (tool == Tool.SELECT) return beginSelectAction(cx, cy);
            if (tool == Tool.IMAGE || tool == Tool.LAYERS) return true;
            pushUndo();
            strokeColorRecorded = false;
            if (tool == Tool.BUCKET) fill(cx, cy);
            else {
                beginStroke();
                painting = true; paint(cx, cy); lastX = cx; lastY = cy;
            }
            dirty = true; textureDirty = true;
            return true;
        }

        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if ((tool == Tool.TEXT || tool == Tool.SELECT) && textAction != TextAction.NONE && button == 0) {
                resizeOrMoveSelection(toCanvasX(mx), toCanvasY(my));
                return true;
            }
            if (!painting || button != 0) return false;
            int cx = toCanvasX(mx), cy = toCanvasY(my);
            line(lastX, lastY, cx, cy); lastX = cx; lastY = cy; dirty = true; textureDirty = true;
            return true;
        }

        @Override public boolean mouseReleased(double mx, double my, int button) {
            if ((tool == Tool.TEXT || tool == Tool.SELECT) && button == 0) { textAction = TextAction.NONE; return true; }
            painting = false; lastX = lastY = -1; textureDirty = true; return button == 0;
        }

        private boolean beginTextAction(int x, int y) {
            int hit = findText(x, y);
            if (hit < 0) {
                if (texts.size() >= SketchbookDrawingData.MAX_TEXT_ELEMENTS) return true;
                pushUndo();
                String content = "文字";
                int textWidth = Math.min(naturalTextWidth(content, textSize), CANVAS_W - x);
                int textHeight = Math.min(textSize, CANVAS_H - y);
                EditableText text = new EditableText(content, x, y, Math.max(1, textWidth), Math.max(1, textHeight), textSize, currentColor);
                texts.add(text);
                layerOrder.add(new SketchbookDrawingData.LayerRef(SketchbookDrawingData.LayerType.TEXT, text.id));
                selectedText = texts.size() - 1;
                selectedImage = -1;
                selectionType = SelectionType.TEXT;
                selectedLayer = new SketchbookDrawingData.LayerRef(SketchbookDrawingData.LayerType.TEXT, text.id);
                dirty = true;
                syncTextControls(true);
                return true;
            }
            selectedText = hit;
            selectedImage = -1;
            selectionType = SelectionType.TEXT;
            EditableText selected = texts.get(hit);
            selectedLayer = new SketchbookDrawingData.LayerRef(SketchbookDrawingData.LayerType.TEXT, selected.id);
            TextAction action = hitAction(selected, x, y);
            if (action == TextAction.EDIT) {
                pushUndo();
                syncTextControls(true);
                return true;
            }
            pushUndo();
            textAction = action;
            actionStartX = x; actionStartY = y;
            originalX = selected.x; originalY = selected.y;
            originalWidth = selected.width; originalHeight = selected.height;
            syncTextControls(false);
            return true;
        }

        private boolean beginSelectAction(int x, int y) {
            Bounds selected = selectedBounds();
            if (selected != null && containsWithMargin(selected, x, y)) return beginTransform(selected, hitAction(selected, x, y), x, y);
            for (int i = layerOrder.size() - 1; i >= 0; i--) {
                SketchbookDrawingData.LayerRef ref = layerOrder.get(i);
                if (ref.type() == SketchbookDrawingData.LayerType.TEXT) {
                    int textHit = indexOfText(ref.id());
                    if (textHit >= 0 && containsWithMargin(texts.get(textHit), x, y)) {
                        selectedText = textHit; selectedImage = -1; selectionType = SelectionType.TEXT; selectedLayer = ref;
                        if (panelMode == PanelMode.TEXT) syncTextControls(false);
                        return beginTransform(texts.get(textHit), TextAction.MOVE, x, y);
                    }
                } else if (ref.type() == SketchbookDrawingData.LayerType.IMAGE) {
                    int imageHit = indexOfImage(ref.id());
                    if (imageHit >= 0 && containsWithMargin(images.get(imageHit), x, y)) {
                        selectedImage = imageHit; selectedText = -1; selectionType = SelectionType.IMAGE; selectedLayer = ref;
                        return beginTransform(images.get(imageHit), TextAction.MOVE, x, y);
                    }
                }
            }
            clearSelection();
            return true;
        }

        private boolean beginTransform(Bounds selected, TextAction action, int x, int y) {
            pushUndo();
            textAction = action == TextAction.EDIT ? TextAction.MOVE : action;
            actionStartX = x; actionStartY = y;
            originalX = selected.x(); originalY = selected.y();
            originalWidth = selected.width(); originalHeight = selected.height();
            return true;
        }

        private int findText(int x, int y) {
            for (int orderIndex = layerOrder.size() - 1; orderIndex >= 0; orderIndex--) {
                SketchbookDrawingData.LayerRef ref = layerOrder.get(orderIndex);
                if (ref.type() != SketchbookDrawingData.LayerType.TEXT) continue;
                int i = indexOfText(ref.id());
                if (i < 0) continue;
                EditableText text = texts.get(i);
                int margin = handleMargin();
                if (x >= text.x - margin && x <= text.x + text.width + margin
                    && y >= text.y - margin && y <= text.y + text.height + margin) return i;
            }
            return -1;
        }

        private int findImage(int x, int y) {
            for (int i = images.size() - 1; i >= 0; i--) if (containsWithMargin(images.get(i), x, y)) return i;
            return -1;
        }

        private Bounds selectedBounds() {
            if (selectionType == SelectionType.TEXT && selectedText >= 0 && selectedText < texts.size()) return texts.get(selectedText);
            if (selectionType == SelectionType.IMAGE && selectedImage >= 0 && selectedImage < images.size()) return images.get(selectedImage);
            return null;
        }

        private boolean containsWithMargin(Bounds object, int x, int y) {
            int margin = handleMargin();
            return x >= object.x() - margin && x <= object.x() + object.width() + margin
                && y >= object.y() - margin && y <= object.y() + object.height() + margin;
        }

        private TextAction hitAction(Bounds text, int x, int y) {
            int margin = handleMargin();
            boolean left = Math.abs(x - text.x()) <= margin, right = Math.abs(x - text.x() - text.width()) <= margin;
            boolean top = Math.abs(y - text.y()) <= margin, bottom = Math.abs(y - text.y() - text.height()) <= margin;
            if (left && top) return TextAction.TOP_LEFT;
            if (right && top) return TextAction.TOP_RIGHT;
            if (left && bottom) return TextAction.BOTTOM_LEFT;
            if (right && bottom) return TextAction.BOTTOM_RIGHT;
            if (top) return TextAction.TOP;
            if (bottom) return TextAction.BOTTOM;
            if (left) return TextAction.LEFT;
            if (right) return TextAction.RIGHT;
            double rx = (x - text.x()) / (double) Math.max(1, text.width());
            double ry = (y - text.y()) / (double) Math.max(1, text.height());
            return rx >= .3 && rx <= .7 && ry >= .3 && ry <= .7 ? TextAction.MOVE : TextAction.EDIT;
        }

        private void resizeOrMoveSelection(int x, int y) {
            Bounds text = selectedBounds();
            if (text == null) return;
            int dx = x - actionStartX, dy = y - actionStartY;
            int left = originalX, top = originalY, right = originalX + originalWidth, bottom = originalY + originalHeight;
            if (textAction.movesLeft()) left = Math.clamp(originalX + dx, 0, right - 1);
            if (textAction.movesRight()) right = Math.clamp(originalX + originalWidth + dx, left + 1, CANVAS_W);
            if (textAction.movesTop()) top = Math.clamp(originalY + dy, 0, bottom - 1);
            if (textAction.movesBottom()) bottom = Math.clamp(originalY + originalHeight + dy, top + 1, CANVAS_H);
            if (textAction == TextAction.MOVE) {
                left = Math.clamp(originalX + dx, 0, CANVAS_W - originalWidth);
                top = Math.clamp(originalY + dy, 0, CANVAS_H - originalHeight);
                right = left + originalWidth; bottom = top + originalHeight;
            } else if (Screen.hasShiftDown()) {
                double ratio = originalWidth / (double) Math.max(1, originalHeight);
                if (textAction.movesLeft() || textAction.movesRight()) {
                    int constrainedHeight = Math.max(1, (int) Math.round((right - left) / ratio));
                    if (textAction.movesTop()) top = Math.max(0, bottom - constrainedHeight);
                    else bottom = Math.min(CANVAS_H, top + constrainedHeight);
                } else {
                    int constrainedWidth = Math.max(1, (int) Math.round((bottom - top) * ratio));
                    if (textAction.movesLeft()) left = Math.max(0, right - constrainedWidth);
                    else right = Math.min(CANVAS_W, left + constrainedWidth);
                }
            }
            text.setBounds(left, top, right - left, bottom - top);
            dirty = true;
        }

        private int handleMargin() { return Math.max(2, Math.round(4 * CANVAS_W / (float) getWidth())); }
        private int screenX(int canvasX) { return getX() + Math.round(canvasX * getWidth() / (float) CANVAS_W); }
        private int screenY(int canvasY) { return getY() + Math.round(canvasY * getHeight() / (float) CANVAS_H); }

        private int toCanvasX(double mx) { return Math.clamp((int) ((mx - getX()) * CANVAS_W / getWidth()), 0, CANVAS_W - 1); }
        private int toCanvasY(double my) { return Math.clamp((int) ((my - getY()) * CANVAS_H / getHeight()), 0, CANVAS_H - 1); }

        private void beginStroke() {
            if (++strokeId == 0) {
                Arrays.fill(strokeMarks, 0);
                strokeId = 1;
            }
        }

        private void line(int x0, int y0, int x1, int y1) {
            if (tool != Tool.PENCIL) {
                int dx = x1 - x0, dy = y1 - y0;
                double distance = Math.sqrt(dx * dx + dy * dy);
                int spacing = Math.max(1, brushSize / 6);
                int steps = Math.max(1, (int) Math.ceil(distance / spacing));
                for (int step = 1; step <= steps; step++) {
                    paint(x0 + dx * step / steps, y0 + dy * step / steps);
                }
                return;
            }
            int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
            int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1, error = dx + dy;
            while (true) {
                paint(x0, y0); if (x0 == x1 && y0 == y1) break;
                int e2 = error * 2;
                if (e2 >= dy) { error += dy; x0 += sx; }
                if (e2 <= dx) { error += dx; y0 += sy; }
            }
        }

        private void paint(int cx, int cy) {
            int radius = tool == Tool.PENCIL ? 0 : Math.max(1, brushSize / 2);
            int color = tool == Tool.ERASER ? CLEAR : currentColor;
            for (int y = -radius; y <= radius; y++) for (int x = -radius; x <= radius; x++) {
                int px = cx + x, py = cy + y;
                if (x * x + y * y <= radius * radius && px >= 0 && px < CANVAS_W && py >= 0 && py < CANVAS_H) {
                    int index = py * CANVAS_W + px;
                    if (strokeMarks[index] == strokeId) continue;
                    strokeMarks[index] = strokeId;
                    setPixel(index, tool == Tool.ERASER ? color : alphaBlend(pixels[index], color));
                }
            }
        }

        private void setPixel(int index, int color) {
            if (pixels[index] == color) return;
            if (!strokeColorRecorded && tool != Tool.ERASER) {
                previousColor = currentColor;
                strokeColorRecorded = true;
            }
            pixels[index] = color;
            EditablePixelLayer layer = currentPixelLayer();
            if (layer != null && layer.texture != null && layer.texture.getPixels() != null) {
                layer.texture.getPixels().setPixel(index % CANVAS_W, index / CANVAS_W, color);
            }
            textureDirty = true;
        }

        private void fill(int cx, int cy) {
            int target = pixels[cy * CANVAS_W + cx];
            if (withinTolerance(target, currentColor, tolerance)) return;
            if (!contiguous) {
                for (int i = 0; i < pixels.length; i++) if (withinTolerance(pixels[i], target, tolerance)) setPixel(i, currentColor);
                return;
            }
            boolean[] visited = new boolean[pixels.length];
            int[] queue = new int[pixels.length];
            int head = 0, tail = 0;
            int first = cy * CANVAS_W + cx;
            queue[tail++] = first;
            visited[first] = true;
            while (head < tail) {
                int index = queue[head++];
                if (!withinTolerance(pixels[index], target, tolerance)) continue;
                setPixel(index, currentColor);
                int x = index % CANVAS_W;
                if (x > 0) tail = enqueue(queue, visited, tail, index - 1);
                if (x < CANVAS_W - 1) tail = enqueue(queue, visited, tail, index + 1);
                if (index >= CANVAS_W) tail = enqueue(queue, visited, tail, index - CANVAS_W);
                if (index < pixels.length - CANVAS_W) tail = enqueue(queue, visited, tail, index + CANVAS_W);
            }
        }

        private int enqueue(int[] queue, boolean[] visited, int tail, int index) {
            if (!visited[index]) {
                visited[index] = true;
                queue[tail++] = index;
            }
            return tail;
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private final class ToolButton extends AbstractWidget {
        private final ResourceLocation image;
        private final Tool selectedTool;
        ToolButton(int x, int y, int size, ResourceLocation image, Tool tool) {
            super(x, y, size, size, Component.literal(tool.name())); this.image = image; this.selectedTool = tool;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            g.fill(getX(), getY(), getRight(), getBottom(), !active ? 0xFF303236
                : tool == selectedTool ? 0xFF66AA66 : isHovered ? 0xFF777777 : 0xFF444444);
            if (image != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, image, getX() + 3, getY() + 3, 0, 0, getWidth() - 6, getHeight() - 6, 16, 16);
            } else {
                int color = active ? 0xFFFFFFFF : 0xFF777777;
                if (selectedTool == Tool.SELECT) drawSelectIcon(g, getX(), getY(), getWidth(), color);
                else if (selectedTool == Tool.IMAGE) drawImageIcon(g, getX(), getY(), getWidth(), color);
                else drawLayersIcon(g, getX(), getY(), getWidth(), color);
            }
            g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFAAAAAA);
        }
        @Override public void onClick(double x, double y) {
            if (!active || tool == selectedTool) return;
            tool = selectedTool;
            if (tool == Tool.TEXT) panelMode = PanelMode.TEXT;
            else if (tool == Tool.IMAGE) panelMode = PanelMode.IMAGE;
            else if (tool == Tool.LAYERS) panelMode = PanelMode.LAYERS;
            else if (tool != Tool.SELECT) panelMode = PanelMode.DRAW;
            rebuildWidgets();
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private static void drawSelectIcon(GuiGraphics graphics, int x, int y, int size, int color) {
        int left = x + 4, top = y + 4, right = x + size - 5, bottom = y + size - 6;
        for (int px = left; px <= right; px += 3) {
            graphics.fill(px, top, Math.min(px + 2, right + 1), top + 1, color);
            graphics.fill(px, bottom, Math.min(px + 2, right + 1), bottom + 1, color);
        }
        for (int py = top; py <= bottom; py += 3) {
            graphics.fill(left, py, left + 1, Math.min(py + 2, bottom + 1), color);
            graphics.fill(right, py, right + 1, Math.min(py + 2, bottom + 1), color);
        }
    }

    private static void drawLayersIcon(GuiGraphics graphics, int x, int y, int size, int color) {
        int left = x + 5, right = x + size - 5, top = y + 5;
        for (int layer = 0; layer < 3; layer++) {
            int offset = layer * 3;
            graphics.fill(left, top + offset + 3, (left + right) / 2, top + offset + 5, color);
            graphics.fill((left + right) / 2, top + offset + 3, right, top + offset + 5, color);
            graphics.fill(left + 2, top + offset + 5, right - 2, top + offset + 6, color);
        }
    }

    private static void drawImageIcon(GuiGraphics graphics, int x, int y, int size, int color) {
        int left = x + 4, top = y + 4, right = x + size - 4, bottom = y + size - 4;
        graphics.renderOutline(left, top, Math.max(1, right - left), Math.max(1, bottom - top), color);
        graphics.fill(right - 5, top + 3, right - 3, top + 5, color);
        int base = bottom - 3;
        for (int i = 0; i < Math.max(3, right - left - 4); i++) {
            int mountain = Math.abs(i - (right - left - 5) / 2);
            int py = base - Math.max(1, 4 - mountain / 2);
            graphics.fill(left + 2 + i, py, left + 3 + i, base, color);
        }
    }

    private final class EditorButton extends AbstractWidget {
        private final int baseColor;
        private final Runnable action;

        private EditorButton(int x, int y, int width, int height, String text, int baseColor, Runnable action) {
            super(x, y, width, height, Component.literal(text));
            this.baseColor = baseColor;
            this.action = action;
        }

        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            int color = !active ? 0xFF303236 : isHovered ? lightenColor(baseColor, 20) : baseColor;
            graphics.fill(getX() + 1, getY(), getRight() - 1, getBottom(), color);
            graphics.fill(getX(), getY() + 1, getRight(), getBottom() - 1, color);
            graphics.fill(getX() + 1, getY(), getRight() - 1, getY() + 1, lightenColor(color, 18));
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), active ? 0xFF8A9098 : 0xFF4A4D52);
            graphics.drawCenteredString(font, getMessage(), getX() + getWidth() / 2,
                getY() + (getHeight() - font.lineHeight) / 2 + 1, active ? 0xFFFFFFFF : 0xFF777777);
        }

        @Override public void onClick(double mouseX, double mouseY) {
            if (active) action.run();
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private final class LayerRowButton extends AbstractWidget {
        private final SketchbookDrawingData.LayerRef ref;
        private final int orderIndex;

        private LayerRowButton(int x, int y, int width, int height, SketchbookDrawingData.LayerRef ref, int orderIndex) {
            super(x, y, width, height, Component.literal(layerName(ref)));
            this.ref = ref; this.orderIndex = orderIndex;
        }

        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            int color = ref.equals(selectedLayer) ? 0xFF315D8C : isHovered ? 0xFF555A60 : 0xFF373B40;
            graphics.fill(getX(), getY(), getRight(), getBottom(), color);
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF777D85);
            String label = (layerOrder.size() - orderIndex) + ". " + layerName(ref);
            graphics.drawString(font, font.plainSubstrByWidth(label, getWidth() - 8), getX() + 4,
                getY() + (getHeight() - font.lineHeight) / 2, 0xFFFFFFFF, false);
        }

        @Override public void onClick(double mouseX, double mouseY) {
            selectLayer(ref);
            rebuildWidgets();
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private static int lightenColor(int color, int amount) {
        int red = Math.min(255, (color >> 16 & 255) + amount);
        int green = Math.min(255, (color >> 8 & 255) + amount);
        int blue = Math.min(255, (color & 255) + amount);
        return color & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private final class ColorWheel extends AbstractWidget {
        ColorWheel(int x, int y, int size) { super(x, y, size, size, Component.literal("色轮")); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            if (colorWheelTextureLocation != null) g.blit(RenderPipelines.GUI_TEXTURED, colorWheelTextureLocation,
                getX(), getY(), 0, 0, getWidth(), getHeight(), 48, 48, 48, 48);
        }
        @Override public boolean mouseClicked(double x, double y, int button) { if (button == 0 && pick(x, y)) return true; return false; }
        @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return button == 0 && pick(x, y); }
        private boolean pick(double mx, double my) {
            double dx = mx - (getX() + getWidth() / 2.0), dy = my - (getY() + getHeight() / 2.0);
            double radius = getWidth() / 2.0 - 1, distance = Math.sqrt(dx * dx + dy * dy);
            if (distance > radius) return false;
            int h = (int) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360);
            setHsv(h, Math.clamp((int) Math.round(distance * 100 / radius), 0, 100), 100);
            return true;
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private final class Slider extends AbstractWidget {
        private final int min, max;
        private final IntConsumer change;
        private final IntFunction<Integer> colorAt;
        private int sliderValue;
        Slider(int x, int y, int w, int h, int min, int max, int value, IntConsumer change,
               IntFunction<Integer> colorAt) {
            super(x, y, w, h, Component.empty()); this.min = min; this.max = max;
            this.sliderValue = value; this.change = change; this.colorAt = colorAt;
            sliders.add(this);
        }
        private DynamicTexture gradientTexture;
        private ResourceLocation gradientLocation;
        private int gradientRevision = Integer.MIN_VALUE;
        void setValue(int value) { sliderValue = Math.clamp(value, min, max); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            ensureGradientTexture();
            if (gradientLocation != null) g.blit(RenderPipelines.GUI_TEXTURED, gradientLocation,
                getX(), getY(), 0, 0, getWidth(), getHeight(), getWidth(), 1, getWidth(), 1);
            int position = (sliderValue - min) * (getWidth() - 1) / Math.max(1, max - min);
            g.fill(getX() + position - 1, getY() - 1, getX() + position + 2, getBottom() + 1, 0xFFFFFFFF);
        }
        private void ensureGradientTexture() {
            if (gradientTexture != null && gradientRevision == colorRevision) return;
            releaseTexture();
            NativeImage image = new NativeImage(getWidth(), 1, false);
            for (int px = 0; px < getWidth(); px++) {
                int sampled = min + px * (max - min) / Math.max(1, getWidth() - 1);
                image.setPixel(px, 0, colorAt.apply(sampled));
            }
            gradientTexture = new DynamicTexture(() -> "manosaba sketchbook slider", image);
            gradientLocation = ResourceLocation.fromNamespaceAndPath("manosaba_item",
                "sketchbook_slider/" + Integer.toUnsignedString(System.identityHashCode(this)));
            minecraft.getTextureManager().register(gradientLocation, gradientTexture);
            gradientRevision = colorRevision;
        }
        private void releaseTexture() {
            if (gradientLocation != null && minecraft != null) minecraft.getTextureManager().release(gradientLocation);
            gradientTexture = null;
            gradientLocation = null;
        }
        @Override public boolean mouseClicked(double x, double y, int button) { if (button == 0 && isMouseOver(x, y)) { update(x); return true; } return false; }
        @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { if (button == 0) { update(x); return true; } return false; }
        private void update(double mouseX) { sliderValue = Math.clamp(min + (int) ((mouseX - getX()) * (max - min) / getWidth()), min, max); change.accept(sliderValue); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private record ControlLabel(String text, int x, int y) {}

    private record Snapshot(List<EditablePixelLayer> pixelLayers, List<EditableText> texts, List<EditableImage> images,
                            List<SketchbookDrawingData.LayerRef> layerOrder, long currentPixelLayerId,
                            SketchbookDrawingData.LayerRef selectedLayer) {}

    private interface Bounds {
        int x();
        int y();
        int width();
        int height();
        void setBounds(int x, int y, int width, int height);
    }

    private enum TextAction {
        NONE, EDIT, MOVE, TOP_LEFT, TOP, TOP_RIGHT, LEFT, RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT;

        boolean movesLeft() { return this == LEFT || this == TOP_LEFT || this == BOTTOM_LEFT; }
        boolean movesRight() { return this == RIGHT || this == TOP_RIGHT || this == BOTTOM_RIGHT; }
        boolean movesTop() { return this == TOP || this == TOP_LEFT || this == TOP_RIGHT; }
        boolean movesBottom() { return this == BOTTOM || this == BOTTOM_LEFT || this == BOTTOM_RIGHT; }
    }

    private final class EditablePixelLayer {
        private final long id;
        private final int[] pixels;
        private DynamicTexture texture;
        private ResourceLocation textureLocation;

        private EditablePixelLayer(long id, int[] pixels) { this.id = id; this.pixels = pixels; }
        private EditablePixelLayer copy() { return new EditablePixelLayer(id, pixels.clone()); }
        private SketchbookDrawingData.PixelLayer toData() { return new SketchbookDrawingData.PixelLayer(id, pixels.clone()); }
        private void ensureTexture() {
            if (textureLocation != null || minecraft == null) return;
            NativeImage image = new NativeImage(CANVAS_W, CANVAS_H, false);
            for (int py = 0; py < CANVAS_H; py++) for (int px = 0; px < CANVAS_W; px++)
                image.setPixel(px, py, pixels[py * CANVAS_W + px]);
            texture = new DynamicTexture(() -> "manosaba sketchbook pixel layer", image);
            textureLocation = ResourceLocation.fromNamespaceAndPath("manosaba_item",
                "sketchbook_pixel_layer/" + Long.toUnsignedString(id) + "/" + Integer.toUnsignedString(System.identityHashCode(this)));
            minecraft.getTextureManager().register(textureLocation, texture);
        }
        private void updateTexture() {
            ensureTexture();
            if (texture == null || texture.getPixels() == null) return;
            NativeImage image = texture.getPixels();
            for (int py = 0; py < CANVAS_H; py++) for (int px = 0; px < CANVAS_W; px++)
                image.setPixel(px, py, pixels[py * CANVAS_W + px]);
            texture.upload();
        }
        private void releaseTexture() {
            if (textureLocation != null && minecraft != null) minecraft.getTextureManager().release(textureLocation);
            texture = null; textureLocation = null;
        }
    }

    private static final class EditableText implements Bounds {
        private final long id;
        private String text;
        private int x, y, width, height, fontSize, color;

        private EditableText(String text, int x, int y, int width, int height, int fontSize, int color) {
            this(SketchbookDrawingData.newId(), text, x, y, width, height, fontSize, color);
        }

        private EditableText(long id, String text, int x, int y, int width, int height, int fontSize, int color) {
            this.id = id;
            this.text = text; this.x = x; this.y = y; this.width = width; this.height = height;
            this.fontSize = fontSize; this.color = color;
        }

        private EditableText copy() { return new EditableText(id, text, x, y, width, height, fontSize, color); }
        @Override public int x() { return x; }
        @Override public int y() { return y; }
        @Override public int width() { return width; }
        @Override public int height() { return height; }
        @Override public void setBounds(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
        private SketchbookDrawingData.TextElement toData() {
            return new SketchbookDrawingData.TextElement(id, text, x, y, width, height, fontSize, color);
        }
        private static EditableText fromData(SketchbookDrawingData.TextElement text, int sourceWidth, int sourceHeight) {
            return new EditableText(text.id(), text.text(), text.x() * CANVAS_W / sourceWidth, text.y() * CANVAS_H / sourceHeight,
                Math.max(1, text.width() * CANVAS_W / sourceWidth), Math.max(1, text.height() * CANVAS_H / sourceHeight),
                text.fontSize(), text.color());
        }
    }

    private final class EditableImage implements Bounds {
        private final long id;
        private int x, y, width, height;
        private final int sourceWidth, sourceHeight;
        private final int[] pixels;
        private DynamicTexture texture;
        private ResourceLocation textureLocation;

        private EditableImage(int x, int y, int width, int height, int sourceWidth, int sourceHeight, int[] pixels) {
            this(SketchbookDrawingData.newId(), x, y, width, height, sourceWidth, sourceHeight, pixels);
        }

        private EditableImage(long id, int x, int y, int width, int height, int sourceWidth, int sourceHeight, int[] pixels) {
            this.id = id;
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.sourceWidth = sourceWidth; this.sourceHeight = sourceHeight; this.pixels = pixels;
        }

        @Override public int x() { return x; }
        @Override public int y() { return y; }
        @Override public int width() { return width; }
        @Override public int height() { return height; }
        @Override public void setBounds(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
        private EditableImage copy() { return new EditableImage(id, x, y, width, height, sourceWidth, sourceHeight, pixels.clone()); }
        private SketchbookDrawingData.ImageElement toData() {
            return new SketchbookDrawingData.ImageElement(id, x, y, width, height, sourceWidth, sourceHeight, pixels.clone());
        }
        private void ensureTexture() {
            if (textureLocation != null || minecraft == null) return;
            NativeImage nativeImage = new NativeImage(sourceWidth, sourceHeight, false);
            for (int py = 0; py < sourceHeight; py++) for (int px = 0; px < sourceWidth; px++)
                nativeImage.setPixel(px, py, pixels[py * sourceWidth + px]);
            texture = new DynamicTexture(() -> "manosaba sketchbook image", nativeImage);
            textureLocation = ResourceLocation.fromNamespaceAndPath("manosaba_item",
                "sketchbook_image/" + Integer.toUnsignedString(System.identityHashCode(this)));
            minecraft.getTextureManager().register(textureLocation, texture);
        }
        private void releaseTexture() {
            if (textureLocation != null && minecraft != null) minecraft.getTextureManager().release(textureLocation);
            textureLocation = null; texture = null;
        }
    }

    private void releaseImageTextures(List<EditableImage> source) {
        for (EditableImage image : source) image.releaseTexture();
    }

    private void releasePixelLayerTextures(List<EditablePixelLayer> source) {
        for (EditablePixelLayer layer : source) layer.releaseTexture();
    }

    private final class LibraryImage {
        private final int width, height;
        private final int[] pixels;
        private DynamicTexture texture;
        private ResourceLocation textureLocation;

        private LibraryImage(int width, int height, int[] pixels) {
            this.width = width; this.height = height; this.pixels = pixels;
        }
        private void ensureTexture() {
            if (textureLocation != null || minecraft == null) return;
            NativeImage image = new NativeImage(width, height, false);
            for (int py = 0; py < height; py++) for (int px = 0; px < width; px++) image.setPixel(px, py, pixels[py * width + px]);
            texture = new DynamicTexture(() -> "manosaba sketchbook library image", image);
            textureLocation = ResourceLocation.fromNamespaceAndPath("manosaba_item",
                "sketchbook_library/" + Integer.toUnsignedString(System.identityHashCode(this)));
            minecraft.getTextureManager().register(textureLocation, texture);
        }
        private void releaseTexture() {
            if (textureLocation != null && minecraft != null) minecraft.getTextureManager().release(textureLocation);
            textureLocation = null; texture = null;
        }
    }

    private final class LibraryImageButton extends AbstractWidget {
        private final int index;
        private LibraryImageButton(int x, int y, int size, int index) {
            super(x, y, size, size + 14, Component.literal("图片 " + (index + 1))); this.index = index;
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            if (index >= imageLibrary.size()) return;
            LibraryImage image = imageLibrary.get(index);
            image.ensureTexture();
            int box = getWidth(), drawW = box, drawH = box;
            if (image.width > image.height) drawH = Math.max(1, box * image.height / image.width);
            else drawW = Math.max(1, box * image.width / image.height);
            int left = getX() + (box - drawW) / 2, top = getY() + (box - drawH) / 2;
            graphics.fill(getX(), getY(), getRight(), getY() + box, 0xFF202225);
            if (image.textureLocation != null) graphics.blit(RenderPipelines.GUI_TEXTURED, image.textureLocation, left, top,
                0, 0, drawW, drawH, image.width, image.height, image.width, image.height);
            int outline = libraryDeleteSelection.contains(index) ? 0xFFFF4444 : isHovered ? 0xFFFFFFFF : 0xFF888888;
            graphics.renderOutline(getX(), getY(), box, box, outline);
            graphics.drawCenteredString(font, libraryDeleteMode && libraryDeleteSelection.contains(index) ? "已选择" : "点击放入",
                getX() + box / 2, getY() + box + 3, 0xFFDDDDDD);
        }
        @Override public void onClick(double mouseX, double mouseY) {
            if (libraryDeleteMode) {
                if (!libraryDeleteSelection.add(index)) libraryDeleteSelection.remove(index);
                rebuildWidgets();
            } else placeLibraryImage(index);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private final class ColorSwatch extends AbstractWidget {
        private final boolean current;
        ColorSwatch(int x, int y, int w, int h, boolean current) { super(x, y, w, h, Component.empty()); this.current = current; }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            g.fill(getX(), getY(), getRight(), getBottom(), composite(current ? currentColor : previousColor));
            g.renderOutline(getX(), getY(), getWidth(), getHeight(), current ? 0xFFFFFFFF : 0xFFAAAAAA);
        }
        @Override public void onClick(double x, double y) { if (!current) usePreviousColor(); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private final class SavedColor extends AbstractWidget {
        private final int index;
        SavedColor(int x, int y, int size, int index) { super(x, y, size, size, Component.empty()); this.index = index; }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) { if (index < colors.size()) g.fill(getX(), getY(), getRight(), getBottom(), composite(colors.get(index))); }
        @Override public void onClick(double x, double y) { if (index < colors.size()) setColor(colors.get(index)); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private static boolean withinTolerance(int first, int second, int tolerance) {
        int a1 = first >>> 24, a2 = second >>> 24;
        if (a1 == 0 && a2 == 0) return true;
        if (a1 == 0 || a2 == 0) return false;
        int dr = (first >> 16 & 255) - (second >> 16 & 255);
        int dg = (first >> 8 & 255) - (second >> 8 & 255);
        int db = (first & 255) - (second & 255);
        return dr * dr + dg * dg + db * db <= tolerance * tolerance;
    }

    private static int alphaBlend(int destination, int source) {
        int sourceAlpha = source >>> 24;
        if (sourceAlpha == 0) return destination;
        if (sourceAlpha == 255) return source;
        int destinationAlpha = destination >>> 24;
        int inverse = 255 - sourceAlpha;
        int outputAlpha = sourceAlpha + (destinationAlpha * inverse + 127) / 255;
        if (outputAlpha <= 0) return CLEAR;

        int sourceRed = source >> 16 & 255, sourceGreen = source >> 8 & 255, sourceBlue = source & 255;
        int destinationRed = destination >> 16 & 255, destinationGreen = destination >> 8 & 255, destinationBlue = destination & 255;
        int destinationWeight = destinationAlpha * inverse;
        int denominator = outputAlpha * 255;
        int red = (sourceRed * sourceAlpha * 255 + destinationRed * destinationWeight + denominator / 2) / denominator;
        int green = (sourceGreen * sourceAlpha * 255 + destinationGreen * destinationWeight + denominator / 2) / denominator;
        int blue = (sourceBlue * sourceAlpha * 255 + destinationBlue * destinationWeight + denominator / 2) / denominator;
        return outputAlpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int composite(int color) {
        int a = color >>> 24;
        int r = ((color >> 16 & 255) * a + 255 * (255 - a)) / 255;
        int g = ((color >> 8 & 255) * a + 255 * (255 - a)) / 255;
        int b = ((color & 255) * a + 255 * (255 - a)) / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
