// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessors for GameRenderer private fields and methods.
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("postEffectId")
    ResourceLocation getPostEffectId();

    @Accessor("postEffectId")
    void setPostEffectId(ResourceLocation id);

    @Accessor("effectActive")
    boolean isEffectActive();

    @Accessor("effectActive")
    void setEffectActive(boolean active);

    @Invoker("setPostEffect")
    void invokeSetPostEffect(ResourceLocation id);
}
