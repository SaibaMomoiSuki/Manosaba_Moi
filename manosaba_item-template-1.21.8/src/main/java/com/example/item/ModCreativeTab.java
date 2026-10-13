// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTab {
    public static final ModCreativeTab INSTANCE = new ModCreativeTab();

    private ModCreativeTab() {
    }

    public static final CreativeModeTab TAB = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
        .icon(() -> new ItemStack(ModItems.JIAN_YI_CHANG_MAO))
        .title(Component.translatable("itemGroup.manosaba_item.tab"))
        .displayItems((parameters, output) -> {
            output.accept(ModItems.ANAN_BOOK);
            output.accept(ModItems.BANDAGE);
            output.accept(ModItems.BLOOD_NANOKA_HAIRBAND);
            output.accept(ModItems.BLOOD_GOLDEN_SWORD);
            output.accept(ModItems.BLOODIED_BROKEN_BOTTLE);
            output.accept(ModItems.BROKEN_BOTTLE);
            output.accept(ModItems.DIAMOND_SWORD);
            output.accept(ModItems.EMPTY_GLASS_BOTTLE);
            output.accept(ModItems.EMPTY_JAR);
            output.accept(ModItems.FEATHER_FAN);
            output.accept(ModItems.GOLDEN_HOE);
            output.accept(ModItems.GOLDEN_SWORD);
            output.accept(ModItems.GOLDUCK);
            output.accept(ModItems.HIRO_PEN);
            output.accept(ModItems.IRON_HOE);
            output.accept(ModItems.JAR);
            output.accept(ModItems.JIAN);
            output.accept(ModItems.JIAN_YI_CHANG_MAO);
            output.accept(ModItems.KNIFE);
            output.accept(ModItems.NETHERITE_SWORD);
            output.accept(ModItems.POISON);
            output.accept(ModItems.SLEEPING_PILLS);
            output.accept(ModItems.SPLINT);
            output.accept(ModItems.SYRINGE);
            output.accept(ModItems.SYRINGE_SLEEPING);
            output.accept(ModItems.SYRINGE_TLDJM);
            output.accept(ModItems.SYRINGE_UNKNOWN_ONE);
            output.accept(ModItems.SYRINGE_UNKNOWN_THREE);
            output.accept(ModItems.SYRINGE_UNKNOWN_TWO);
            output.accept(ModItems.SYRINGE_POISON);
            output.accept(ModItems.YUKIS_SEXUAL_FLUIDS);
            output.accept(ModItems.WOODEN_SWORD);
            output.accept(ModItems.NANOKA_HAIRBAND);
            output.accept(ModItems.ZHANG_XUEFENG);
            output.accept(ModItems.ZONGZI);
            output.accept(ModItems.ZUOLUN);
            output.accept(ModItems.REVOLVER_BULLET);
            output.accept(ModItems.ROTARY_MAGAZINE);
            output.accept(ModItems.SHELL_CASING);
        }).build();

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath("manosaba_item", "tab"), TAB);
    }
}
