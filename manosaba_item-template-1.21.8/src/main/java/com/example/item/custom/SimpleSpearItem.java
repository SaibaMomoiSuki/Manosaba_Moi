// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item.custom;

import com.example.item.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.Level;

import java.util.List;

public class SimpleSpearItem extends Item {
    public SimpleSpearItem(Properties properties) {
        super(properties.component(DataComponents.WEAPON, new Weapon(13, -3.0F)).attributes(createAttributes()));
    }
    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 13.0,
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -3.0,
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(
                ResourceLocation.fromNamespaceAndPath("manosaba_item", "spear_reach"), 2.0,
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build();
    }
    @Override public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean willBreak = stack.getDamageValue() >= stack.getMaxDamage() - 1;
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        if (willBreak && stack.isEmpty() && !attacker.level().isClientSide()) spawnBreakDrops(attacker.level(), attacker);
        super.postHurtEnemy(stack, target, attacker);
    }
    private void spawnBreakDrops(Level level, LivingEntity attacker) {
        var look = attacker.getLookAngle();
        var origin = attacker.getEyePosition().subtract(0.0, 0.3, 0.0);
        var drops = List.of(new ItemStack(ModItems.BLOOD_GOLDEN_SWORD), new ItemStack(Items.STICK),
            new ItemStack(ModItems.JIAN), new ItemStack(ModItems.BLOOD_NANOKA_HAIRBAND));
        for (int i = 0; i < drops.size(); i++) {
            var pos = origin.add(look.scale(i + 1.0));
            level.addFreshEntity(new ItemEntity(level, pos.x, pos.y, pos.z, drops.get(i)));
        }
    }
}
