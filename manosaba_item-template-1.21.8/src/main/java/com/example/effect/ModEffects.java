// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.effect;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

public final class ModEffects {
    public static final MobEffect SLEEPING = register("sleeping", new SleepingMobEffect());
    public static final MobEffect DIZZINESS = register("dizziness", new DizzinessMobEffect());
    public static final MobEffect BLEEDING = register("bleeding", new BleedingMobEffect());
    private ModEffects() {}
    public static void init() {}
    private static MobEffect register(String path, MobEffect effect) {
        return Registry.register(BuiltInRegistries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("manosaba_item", path), effect);
    }
    public static boolean hasSleepingEffect(LivingEntity entity) {
        return entity.hasEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(SLEEPING));
    }
}
