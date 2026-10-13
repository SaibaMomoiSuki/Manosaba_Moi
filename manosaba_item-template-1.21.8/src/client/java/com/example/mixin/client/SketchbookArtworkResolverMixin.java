// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin.client;

import com.example.client.SketchbookArtworkState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public class SketchbookArtworkResolverMixin {
    @Inject(method = "appendItemLayers", at = @At("HEAD"))
    private void captureSketchbookStack(ItemStackRenderState state, ItemStack stack,
                                        ItemDisplayContext context, Level level,
                                        LivingEntity entity, int seed, CallbackInfo ci) {
        ((SketchbookArtworkState) state).manosabaItem$setSketchbookStack(stack);
    }
}
