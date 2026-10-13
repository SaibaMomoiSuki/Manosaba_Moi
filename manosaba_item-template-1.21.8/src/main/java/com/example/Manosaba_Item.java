// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example;

import com.example.component.ModDataComponents;
import com.example.effect.ModEffects;
import com.example.event.ItemEventHandlers;
import com.example.event.ModEvents;
import com.example.item.ModCreativeTab;
import com.example.item.ModItems;
import com.example.recipe.ModRecipes;
import com.example.sound.ModSounds;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Manosaba_Item implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("manosaba_item");
    @Override public void onInitialize() {
        LOGGER.info("Manosaba Item Mod Initializing...");
        ModItems.init();
        ModSounds.init();
        ModCreativeTab.register();
        ModDataComponents.init();
        ModEffects.init();
        ModRecipes.init();
        ModEvents.register();
        ItemEventHandlers.register();
        LOGGER.info("Manosaba Item Mod Initialized! 37 items registered.");
    }
}
