// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item.custom;

import com.example.event.ModPlayerState;
import com.example.item.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BloodyKnightSwordItem extends Item {
    public static final CompanionAdapter Companion = new CompanionAdapter();
    public static final Map<UUID, Map<Long, BloodScentData>> bloodScents = new ConcurrentHashMap<>();

    public BloodyKnightSwordItem(int attackDamage, float attackSpeed, Properties properties) {
        super(properties.component(DataComponents.WEAPON, new Weapon(attackDamage, attackSpeed))
            .attributes(createModifiers(attackDamage, attackSpeed)));
    }

    public static ItemAttributeModifiers createModifiers(double damage, double speed) {
        return ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage,
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, speed,
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build();
    }

    public static void addBloodScent(ServerLevel level, int x, int y, int z, UUID ownerUuid) {
        UUID world = worldId(level.dimension().location());
        Map<Long, BloodScentData> scents = bloodScents.computeIfAbsent(world, key -> new ConcurrentHashMap<>());
        long position = ((long) x << 32) | (z & 0xFFFFFFFFL);
        scents.put(position, new BloodScentData(x, y, z, level.getGameTime() + 3600, ownerUuid));
    }

    public static Map<Long, BloodScentData> getBloodScentsInWorld(ResourceLocation worldKey) {
        return bloodScents.getOrDefault(worldId(worldKey), Collections.emptyMap());
    }

    private static UUID worldId(ResourceLocation location) {
        return UUID.nameUUIDFromBytes(location.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static final class CompanionAdapter {
        public Map<Long, BloodScentData> getBloodScentsInWorld(ResourceLocation worldKey) {
            return BloodyKnightSwordItem.getBloodScentsInWorld(worldKey);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof Player player)) return;
        UUID uuid = player.getUUID();
        if (player.isInWater()) {
            ModPlayerState.BloodySwordWashState state = ModPlayerState.bloodySwordWash.computeIfAbsent(uuid,
                key -> new ModPlayerState.BloodySwordWashState(level.getGameTime()));
            if (level.getGameTime() - state.enterWaterTime >= 40) {
                var pos = player.blockPosition();
                addBloodScent(level, pos.getX(), pos.getY(), pos.getZ(), uuid);
                ItemStack cleaned = stack.transmuteCopy(ModItems.GOLDEN_SWORD);
                if (slot != null) {
                    player.setItemSlot(slot, cleaned);
                } else {
                    int inventorySlot = player.getInventory().findSlotMatchingItem(stack);
                    if (inventorySlot >= 0) player.getInventory().setItem(inventorySlot, cleaned);
                }
                ModPlayerState.bloodySwordWash.remove(uuid);
            }
        } else {
            ModPlayerState.bloodySwordWash.remove(uuid);
        }
    }

    public static final class BloodScentData {
        public final int x;
        public final int y;
        public final int z;
        public final long expiryTime;
        public final UUID ownerUuid;

        public BloodScentData(int x, int y, int z, long expiryTime, UUID ownerUuid) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.expiryTime = expiryTime;
            this.ownerUuid = ownerUuid;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
        public long getExpiryTime() { return expiryTime; }
        public UUID getOwnerUuid() { return ownerUuid; }
    }
}
