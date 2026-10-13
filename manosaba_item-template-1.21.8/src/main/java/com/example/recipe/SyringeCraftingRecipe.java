// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.recipe;

import com.example.item.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class SyringeCraftingRecipe extends CustomRecipe {
    public SyringeCraftingRecipe(CraftingBookCategory category) { super(category); }
    @Override public boolean matches(CraftingInput input, Level level) {
        boolean syringe = false, potion = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i); if (stack.isEmpty()) continue;
            if (stack.is(ModItems.SYRINGE)) { if (syringe) return false; syringe = true; }
            else if (stack.is(ModItems.YUKIS_SEXUAL_FLUIDS) || stack.is(ModItems.SLEEPING_PILLS) || stack.is(ModItems.POISON)) {
                if (potion) return false; potion = true;
            } else return false;
        }
        return syringe && potion;
    }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Item potion = null;
        for (int i = 0; i < input.size(); i++) if (!input.getItem(i).isEmpty() && !input.getItem(i).is(ModItems.SYRINGE)) potion = input.getItem(i).getItem();
        if (potion == ModItems.YUKIS_SEXUAL_FLUIDS) return new ItemStack(ModItems.SYRINGE_UNKNOWN_ONE);
        if (potion == ModItems.SLEEPING_PILLS) return new ItemStack(ModItems.SYRINGE_UNKNOWN_TWO);
        if (potion == ModItems.POISON) return new ItemStack(ModItems.SYRINGE_UNKNOWN_THREE);
        return ItemStack.EMPTY;
    }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<? extends CustomRecipe> getSerializer() { return ModRecipes.SYRINGE_CRAFTING_SERIALIZER; }
}
