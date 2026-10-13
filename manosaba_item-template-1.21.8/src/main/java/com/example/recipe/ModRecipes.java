// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.recipe;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class ModRecipes {
    public static final RecipeSerializer<PoisonFoodRecipe> POISON_FOOD_SERIALIZER = register("poison_food", new CustomRecipe.Serializer<>(PoisonFoodRecipe::new));
    public static final RecipeSerializer<SyringeCraftingRecipe> SYRINGE_CRAFTING_SERIALIZER = register("syringe_crafting", new CustomRecipe.Serializer<>(SyringeCraftingRecipe::new));
    public static final RecipeSerializer<JarFoodRecipe> JAR_FOOD_SERIALIZER = register("jar_food", new CustomRecipe.Serializer<>(JarFoodRecipe::new));
    private ModRecipes() {}
    public static void init() {}
    private static <T extends CustomRecipe> RecipeSerializer<T> register(String path, RecipeSerializer<T> serializer) {
        return Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, ResourceLocation.fromNamespaceAndPath("manosaba_item", path), serializer);
    }
}
