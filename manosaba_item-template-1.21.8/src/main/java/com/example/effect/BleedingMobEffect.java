// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class BleedingMobEffect extends MobEffect {
    public BleedingMobEffect() { super(MobEffectCategory.HARMFUL, 0x8B0000); }
    @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return duration % 120 == 0; }
    @Override public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity.getHealth() > 1.0F) entity.hurt(level.damageSources().generic(), 1.0F);
        return true;
    }
}
