// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor to call protected methods on Camera.
 */
@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setPosition")
    void invokeSetPosition(double x, double y, double z);

    @Invoker("setRotation")
    void invokeSetRotation(float yRot, float xRot);
}
