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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;

public class BurnRodItem extends Item {
    private static final String TIMER_KEY = "manosaba_ticks_out_of_fire";

    public BurnRodItem(Properties properties) {
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
        int ticks = isNearFire(entity) ? 0
            : stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr(TIMER_KEY, 0) + 1;
        if (ticks >= 1200) {
            ItemStack cooled = stack.transmuteCopy(ModItems.IRON_HOE);
            CustomData.update(DataComponents.CUSTOM_DATA, cooled, tag -> tag.remove(TIMER_KEY));
            replaceStack(player, slot, stack, cooled);
        } else {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TIMER_KEY, ticks));
        }
    }

    static void replaceStack(Player player, EquipmentSlot slot, ItemStack oldStack, ItemStack newStack) {
        if (slot != null) {
            player.setItemSlot(slot, newStack);
            return;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i) == oldStack) {
                player.getInventory().setItem(i, newStack);
                return;
            }
        }
    }

    static boolean isNearFire(Entity entity) {
        if (entity.isOnFire()) return true;
        var level = entity.level();
        var pos = entity.blockPosition();
        for (int dy = 0; dy <= 2; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            var state = level.getBlockState(pos.offset(dx, dy, dz));
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT)
                || state.is(Blocks.SOUL_CAMPFIRE) && state.getValue(CampfireBlock.LIT)) return true;
        }
        return false;
    }
}
