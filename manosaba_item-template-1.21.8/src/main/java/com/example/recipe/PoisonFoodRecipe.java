// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.recipe;

import com.example.component.ModDataComponents;
import com.example.item.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class PoisonFoodRecipe extends CustomRecipe {
    public static final TagKey<Item> CAN_BE_POISONED = TagKey.create(Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath("manosaba_item", "can_be_poisoned"));
    public PoisonFoodRecipe(CraftingBookCategory category) { super(category); }
    @Override public boolean matches(CraftingInput input, Level level) {
        boolean poison = false; int food = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i); if (stack.isEmpty()) continue;
            if (stack.is(ModItems.POISON)) { if (poison) return false; poison = true; }
            else if (stack.is(CAN_BE_POISONED)) { if (++food > 1) return false; }
            else return false;
        }
        return poison && food == 1;
    }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty() && !stack.is(ModItems.POISON)) {
                ItemStack result = stack.copyWithCount(1);
                result.set(ModDataComponents.POISONED_COUNT, 1); result.set(ModDataComponents.POISONED, Unit.INSTANCE); return result;
            }
        }
        return ItemStack.EMPTY;
    }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<? extends CustomRecipe> getSerializer() { return ModRecipes.POISON_FOOD_SERIALIZER; }
}
