/*
 * Copyright (c) 2026 Momoi. Proprietary - Internal Use Only.
 * 内部私有，严禁任何形式使用（包括游玩、复制、分发）。
 */
package com.example.vision_optimize.mixin;

import com.example.vision_optimize.tracking.ItemTrackingComponent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Mixin to ensure items entering a container (including player inventory) get a tracking UUID,
 * and to merge holder histories when items are stacked.
 * SimpleContainer is the base for most inventories including player inventory.
 */
@Mixin(SimpleContainer.class)
public class InventoryMixin {

    /** ThreadLocal: tracking UUID of the stack being added via addItem (for history merging). */
    private static final ThreadLocal<UUID> MERGE_SOURCE_UUID = new ThreadLocal<>();

    /** ThreadLocal: snapshot of slot counts before addItem runs (for detecting which slots absorbed items). */
    private static final ThreadLocal<int[]> MERGE_SLOT_COUNTS = new ThreadLocal<>();

    /**
     * When an item is added to a container, ensure it has a tracking UUID
     * AND capture pre-merge state for history merging.
     * Mojang name: addItem(ItemStack) -> ItemStack
     */
    @Inject(at = @At("HEAD"), method = "addItem")
    private void visionOptimize$onAddItem(ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (!stack.isEmpty()) {
            ItemTrackingComponent.INSTANCE.getOrCreateTrackingId(stack);
        }

        // Save pre-merge state for history merging
        SimpleContainer self = (SimpleContainer) (Object) this;
        int size = self.getContainerSize();
        int[] counts = new int[size];
        for (int i = 0; i < size; i++) {
            counts[i] = self.getItem(i).getCount();
        }
        MERGE_SLOT_COUNTS.set(counts);
        MERGE_SOURCE_UUID.set(ItemTrackingComponent.INSTANCE.getTrackingId(stack));
    }

    /**
     * After addItem completes, detect which slots received items from the incoming stack
     * and merge the incoming stack's tracking history into them.
     * Mojang name: addItem(ItemStack) -> ItemStack
     */
    @Inject(at = @At("RETURN"), method = "addItem")
    private void visionOptimize$afterAddItem(ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        UUID sourceUuid = MERGE_SOURCE_UUID.get();
        int[] oldCounts = MERGE_SLOT_COUNTS.get();
        MERGE_SOURCE_UUID.remove();
        MERGE_SLOT_COUNTS.remove();

        if (sourceUuid == null || oldCounts == null) return;

        SimpleContainer self = (SimpleContainer) (Object) this;
        int size = Math.min(oldCounts.length, self.getContainerSize());
        for (int i = 0; i < size; i++) {
            int newCount = self.getItem(i).getCount();
            int oldCount = oldCounts[i];
            if (newCount > oldCount) {
                // Items from the incoming stack were merged into this slot
                ItemStack slotStack = self.getItem(i);
                if (!slotStack.isEmpty()) {
                    UUID slotUuid = ItemTrackingComponent.INSTANCE.getOrCreateTrackingId(slotStack);
                    if (!slotUuid.equals(sourceUuid)) {
                        ItemTrackingComponent.INSTANCE.mergeHistory(sourceUuid, slotUuid);
                    }
                }
            }
        }
    }

    /**
     * When setting an item in a slot, ensure it has a tracking UUID.
     * Mojang name: setItem(int, ItemStack)
     */
    @Inject(at = @At("HEAD"), method = "setItem")
    private void visionOptimize$onSetItem(int slot, ItemStack stack, CallbackInfo ci) {
        if (!stack.isEmpty()) {
            ItemTrackingComponent.INSTANCE.getOrCreateTrackingId(stack);
        }
    }
}
