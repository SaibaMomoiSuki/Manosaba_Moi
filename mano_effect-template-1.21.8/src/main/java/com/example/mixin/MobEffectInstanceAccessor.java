// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for MobEffectInstance.duration field (setter only).
 * The getter already exists as getDuration() on MobEffectInstance.
 * Used by injury effects to accelerate duration when sitting.
 */
@Mixin(MobEffectInstance.class)
public interface MobEffectInstanceAccessor {
    @Accessor("duration")
    void setDurationField(int duration);
}
