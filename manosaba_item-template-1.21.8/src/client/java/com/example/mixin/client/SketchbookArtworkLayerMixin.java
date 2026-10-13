// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin.client;

import com.example.client.SketchbookArtworkRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public class SketchbookArtworkLayerMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void renderSketchbookArtwork(PoseStack poseStack, MultiBufferSource buffers,
                                         int light, int overlay, CallbackInfo ci) {
        SketchbookArtworkRenderer.renderCurrent(poseStack, buffers, light, overlay);
    }
}
