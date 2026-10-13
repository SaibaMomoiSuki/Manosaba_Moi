// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item.custom;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;

public class CustomSwordItem extends Item {
    public CustomSwordItem(int attackDamage, float attackSpeed, Properties properties) {
        super(properties.component(DataComponents.WEAPON, new Weapon(attackDamage, attackSpeed))
            .attributes(createModifiers(attackDamage, attackSpeed)));
    }

    public static ItemAttributeModifiers createModifiers(double damage, double speed) {
        return ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED,
                new AttributeModifier(BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .build();
    }
}
