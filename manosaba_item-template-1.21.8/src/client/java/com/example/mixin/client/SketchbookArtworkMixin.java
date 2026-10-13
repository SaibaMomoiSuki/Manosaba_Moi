// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin.client;

import com.example.client.SketchbookArtworkRenderer;
import com.example.client.SketchbookArtworkState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public class SketchbookArtworkMixin implements SketchbookArtworkState {
    @Unique private ItemStack manosabaItem$sketchbookStack = ItemStack.EMPTY;

    @Override
    public void manosabaItem$setSketchbookStack(ItemStack stack) {
        manosabaItem$sketchbookStack = stack;
    }

    @Override
    public ItemStack manosabaItem$getSketchbookStack() {
        return manosabaItem$sketchbookStack;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void beginSketchbookArtwork(PoseStack poseStack, MultiBufferSource buffers,
                                        int light, int overlay, CallbackInfo ci) {
        SketchbookArtworkRenderer.begin(manosabaItem$sketchbookStack);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void endSketchbookArtwork(PoseStack poseStack, MultiBufferSource buffers,
                                      int light, int overlay, CallbackInfo ci) {
        SketchbookArtworkRenderer.end();
    }
}
