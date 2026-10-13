// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.recipe;

import com.example.component.ModDataComponents;
import com.example.item.ModItems;
import com.example.item.custom.EmptyJarItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class JarFoodRecipe extends CustomRecipe {
    public JarFoodRecipe(CraftingBookCategory category) { super(category); }
    @Override public boolean matches(CraftingInput input, Level level) {
        boolean jar = false, food = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i); if (stack.isEmpty()) continue;
            if (stack.is(ModItems.EMPTY_JAR)) { if (jar) return false; jar = true; }
            else if (EmptyJarItem.canCombineWithJar(stack.getItem())) { if (food) return false; food = true; }
            else return false;
        }
        return jar && food;
    }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack food = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) if (!input.getItem(i).isEmpty() && !input.getItem(i).is(ModItems.EMPTY_JAR)) { food = input.getItem(i); break; }
        ItemStack result = new ItemStack(ModItems.JAR);
        if (food.get(DataComponents.FOOD) != null) result.set(DataComponents.FOOD, food.get(DataComponents.FOOD));
        if (food.get(DataComponents.CONSUMABLE) != null) result.set(DataComponents.CONSUMABLE, food.get(DataComponents.CONSUMABLE));
        if (food.get(ModDataComponents.POISONED) != null) result.set(ModDataComponents.POISONED, food.get(ModDataComponents.POISONED));
        if (food.get(ModDataComponents.POISONED_COUNT) != null) result.set(ModDataComponents.POISONED_COUNT, food.get(ModDataComponents.POISONED_COUNT));
        return result;
    }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<? extends CustomRecipe> getSerializer() { return ModRecipes.JAR_FOOD_SERIALIZER; }
}
