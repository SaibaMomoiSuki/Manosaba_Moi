package com.example.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
    public static final SoundEvent GET_FIRE = register("get_fire");
    public static final SoundEvent RELOAD_OVER1 = register("reload_over1");
    public static final SoundEvent RELOAD_OVER2 = register("reload_over2");
    public static final SoundEvent RELOAD_QUICK = register("reload_quick");
    public static final SoundEvent RELOAD_LOWER = register("reload_lower");
    public static final SoundEvent NEED_RELOAD1 = register("needreload1");
    public static final SoundEvent NEED_RELOAD2 = register("needreload2");
    public static final SoundEvent NEED_RELOAD3 = register("needreload3");

    private ModSounds() {}

    private static SoundEvent register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("manosaba_item", name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {}
}
