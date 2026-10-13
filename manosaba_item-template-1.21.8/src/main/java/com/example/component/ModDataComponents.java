// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.component;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;

public final class ModDataComponents {
    public static final ModDataComponents INSTANCE = new ModDataComponents();
    public static final DataComponentType<Unit> POISONED = register("poisoned",
        DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC).build());
    public static final DataComponentType<Integer> POISONED_COUNT = register("poisoned_count",
        DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final DataComponentType<ResourceLocation> ORIGINAL_FOOD = register("original_food",
        DataComponentType.<ResourceLocation>builder().persistent(ResourceLocation.CODEC)
            .networkSynchronized(ResourceLocation.STREAM_CODEC).build());
    public static final DataComponentType<Boolean> SKETCHBOOK_OPEN = register("sketchbook_open",
        DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
    public static final DataComponentType<List<SketchbookPage>> SKETCHBOOK_PAGES = register("sketchbook_pages",
        DataComponentType.<List<SketchbookPage>>builder().persistent(SketchbookPage.CODEC.listOf())
            .networkSynchronized(SketchbookPage.STREAM_CODEC.apply(ByteBufCodecs.list(100))).build());
    public static final DataComponentType<List<Integer>> SKETCHBOOK_COLORS = register("sketchbook_colors",
        DataComponentType.<List<Integer>>builder().persistent(Codec.INT.listOf())
            .networkSynchronized(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(20))).build());
    public static final DataComponentType<Integer> SKETCHBOOK_PREV_COLOR = register("sketchbook_prev_color",
        DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final DataComponentType<Integer> SKETCHBOOK_DISPLAY_PAGE = register("sketchbook_display_page",
        DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final DataComponentType<Integer> REVOLVER_AMMO = register("revolver_ammo",
        DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final DataComponentType<Integer> REVOLVER_SHELLS = register("revolver_shells",
        DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final DataComponentType<Boolean> REVOLVER_CYLINDER_EJECTED = register("revolver_cylinder_ejected",
        DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
    public static final DataComponentType<Integer> MAGAZINE_AMMO = register("magazine_ammo",
        DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    private ModDataComponents() {}
    public static void init() {}
    public DataComponentType<Unit> getPOISONED() { return POISONED; }
    public DataComponentType<Integer> getPOISONED_COUNT() { return POISONED_COUNT; }
    public DataComponentType<ResourceLocation> getORIGINAL_FOOD() { return ORIGINAL_FOOD; }

    private static <T> DataComponentType<T> register(String path, DataComponentType<T> type) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath("manosaba_item", path), type);
    }
}
