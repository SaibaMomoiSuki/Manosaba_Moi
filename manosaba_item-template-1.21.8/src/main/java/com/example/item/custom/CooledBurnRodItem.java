// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item.custom;

import com.example.item.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;

public class CooledBurnRodItem extends Item {
    private static final String TIMER_KEY = "manosaba_ticks_in_fire";

    public CooledBurnRodItem(Properties properties) {
        super(properties.component(DataComponents.WEAPON, new Weapon(5, -2.4F)).attributes(createAttributes()));
    }

    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 5.0,
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.4,
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build();
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof Player player)) return;
        int ticks = BurnRodItem.isNearFire(entity)
            ? stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr(TIMER_KEY, 0) + 1 : 0;
        if (ticks >= 200) {
            ItemStack heated = stack.transmuteCopy(ModItems.GOLDEN_HOE);
            CustomData.update(DataComponents.CUSTOM_DATA, heated, tag -> tag.remove(TIMER_KEY));
            BurnRodItem.replaceStack(player, slot, stack, heated);
        } else {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TIMER_KEY, ticks));
        }
    }
}
