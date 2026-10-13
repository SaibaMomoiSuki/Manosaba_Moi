// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item.custom;

import com.example.item.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class EmptyJarItem extends Item {
    public EmptyJarItem(Properties properties) { super(properties); }

    public static boolean canCombineWithJar(Item item) {
        boolean blacklisted = item == ModItems.GOLDUCK || item == ModItems.EMPTY_JAR || item == ModItems.JAR
            || item == ModItems.POISON || item == ModItems.SLEEPING_PILLS || item == ModItems.YUKIS_SEXUAL_FLUIDS
            || item == ModItems.ZHANG_XUEFENG;
        return !blacklisted && new ItemStack(item).has(DataComponents.FOOD);
    }
}
