// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.client;

import com.example.component.SketchbookDrawingData;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

final class SketchbookTextRasterizer {
    private SketchbookTextRasterizer() {}

    static int[] render(SketchbookDrawingData.Drawing drawing) {
        int width = drawing.width(), height = drawing.height();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (SketchbookDrawingData.LayerRef ref : drawing.layerOrder()) {
            if (ref.type() == SketchbookDrawingData.LayerType.PIXEL) {
                drawing.pixelLayers().stream().filter(layer -> layer.id() == ref.id()).findFirst().ifPresent(layer -> {
                    BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    source.setRGB(0, 0, width, height, layer.pixels(), 0, width);
                    graphics.drawImage(source, 0, 0, null);
                });
            } else if (ref.type() == SketchbookDrawingData.LayerType.IMAGE) {
                drawing.images().stream().filter(layer -> layer.id() == ref.id()).findFirst().ifPresent(layer -> {
                    BufferedImage source = new BufferedImage(layer.sourceWidth(), layer.sourceHeight(), BufferedImage.TYPE_INT_ARGB);
                    source.setRGB(0, 0, layer.sourceWidth(), layer.sourceHeight(), layer.pixels(), 0, layer.sourceWidth());
                    graphics.drawImage(source, layer.x(), layer.y(), layer.width(), layer.height(), null);
                });
            } else {
                drawing.texts().stream().filter(text -> text.id() == ref.id()).findFirst().ifPresent(text -> {
                    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    Font font = new Font(Font.SANS_SERIF, Font.PLAIN, text.fontSize());
                    int naturalWidth = Math.max(1, graphics.getFontMetrics(font).stringWidth(text.text()));
                    int naturalHeight = Math.max(1, graphics.getFontMetrics(font).getHeight());
                    AffineTransform oldTransform = graphics.getTransform();
                    graphics.translate(text.x(), text.y());
                    graphics.scale(text.width() / (double) naturalWidth, text.height() / (double) naturalHeight);
                    graphics.setFont(font);
                    graphics.setColor(new Color(text.color(), true));
                    graphics.drawString(text.text(), 0, graphics.getFontMetrics().getAscent());
                    graphics.setTransform(oldTransform);
                });
            }
        }
        graphics.dispose();
        return image.getRGB(0, 0, width, height, null, 0, width);
    }
}
