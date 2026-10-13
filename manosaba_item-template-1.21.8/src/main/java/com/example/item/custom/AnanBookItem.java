// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item.custom;

import com.example.component.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

import java.util.List;

public class AnanBookItem extends Item {
    public AnanBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
            boolean open = Boolean.TRUE.equals(stack.get(ModDataComponents.SKETCHBOOK_OPEN));
            stack.set(ModDataComponents.SKETCHBOOK_OPEN, !open);
            player.getCooldowns().addCooldown(stack, 40);
            stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(open ? "0" : "1"), List.of()));
            if (stack.get(ModDataComponents.SKETCHBOOK_PAGES) == null)
                stack.set(ModDataComponents.SKETCHBOOK_PAGES, List.of());
            if (stack.get(ModDataComponents.SKETCHBOOK_COLORS) == null)
                stack.set(ModDataComponents.SKETCHBOOK_COLORS, List.of());
            if (stack.get(ModDataComponents.SKETCHBOOK_DISPLAY_PAGE) == null)
                stack.set(ModDataComponents.SKETCHBOOK_DISPLAY_PAGE, -1);
            if (stack.get(ModDataComponents.SKETCHBOOK_PREV_COLOR) == null)
                stack.set(ModDataComponents.SKETCHBOOK_PREV_COLOR, 0xFF000000);
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean isOpen(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModDataComponents.SKETCHBOOK_OPEN));
    }

    public static boolean isOpenSketchbook(ItemStack stack) {
        return stack.getItem() instanceof AnanBookItem && isOpen(stack);
    }

    public static boolean isHoldingOpenSketchbook(Player player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return main.getItem() instanceof AnanBookItem && isOpen(main)
            || off.getItem() instanceof AnanBookItem && isOpen(off);
    }

    public static void openScreen() {
        try {
            Class<?> screenClass = Class.forName("com.example.client.SketchbookScreen");
            Object screen = screenClass.getDeclaredConstructor().newInstance();
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            minecraftClass.getMethod("setScreen", Class.forName("net.minecraft.client.gui.screens.Screen"))
                .invoke(minecraft, screen);
        } catch (ReflectiveOperationException ignored) {
            // Client screen classes are unavailable on a dedicated server.
        }
    }
}
