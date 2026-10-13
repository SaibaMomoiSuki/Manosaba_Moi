package com.example.item.custom;

import com.example.component.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;

public final class RotaryMagazineItem extends Item {
    public RotaryMagazineItem(Properties properties) { super(properties); }

    @Override public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) { return 72000; }
    @Override public net.minecraft.world.item.ItemUseAnimation getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.ItemUseAnimation.CROSSBOW;
    }

    public static void updateModel(ItemStack stack) {
        int ammo = Math.clamp(stack.getOrDefault(ModDataComponents.MAGAZINE_AMMO, 0), 0, 6);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
            new net.minecraft.world.item.component.CustomModelData(List.of(), List.of(),
                List.of(Integer.toString(ammo)), List.of()));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.manosaba_item.magazine_ammo",
            stack.getOrDefault(ModDataComponents.MAGAZINE_AMMO, 0), 6));
    }
}
