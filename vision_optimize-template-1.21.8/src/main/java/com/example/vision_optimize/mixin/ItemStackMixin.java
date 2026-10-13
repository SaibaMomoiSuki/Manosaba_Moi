/*
 * Copyright (c) 2026 Momoi. Proprietary - Internal Use Only.
 * 内部私有，严禁任何形式使用（包括游玩、复制、分发）。
 */
package com.example.vision_optimize.mixin;

import com.example.vision_optimize.tracking.ItemTrackingComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    /**
     * Thread-local storage for tracking UUIDs temporarily removed
     * during the isSameItemSameComponents comparison.
     * Two slots for the two stacks being compared.
     */
    private static final ThreadLocal<UUID> STRIPPED_UUID_1 = new ThreadLocal<>();
    private static final ThreadLocal<UUID> STRIPPED_UUID_2 = new ThreadLocal<>();

    /**
     * Preserve the tracking UUID when an ItemStack is copied.
     * Mojang name: copy() -> the method that creates a copy of the stack.
     */
    @Inject(at = @At("RETURN"), method = "copy")
    private void visionOptimize$onCopy(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack original = (ItemStack) (Object) this;
        ItemStack copy = cir.getReturnValue();
        // Safe: no-op if component not yet registered (e.g., during other mods' init)
        ItemTrackingComponent.INSTANCE.copyTrackingId(original, copy);
    }

    /**
     * Before the stacking compatibility check, temporarily remove tracking UUIDs
     * from both stacks so that the vanilla comparison ignores them.
     * Without this, two items with different tracking UUIDs would be seen as
     * having different components and thus unstackable.
     *
     * Mojang name: isSameItemSameComponents(ItemStack, ItemStack) -> boolean (static)
     */
    @Inject(at = @At("HEAD"), method = "isSameItemSameComponents")
    private static void visionOptimize$beforeSameCheck(ItemStack stack, ItemStack other, CallbackInfoReturnable<Boolean> cir) {
        STRIPPED_UUID_1.set(ItemTrackingComponent.INSTANCE.getTrackingId(stack));
        STRIPPED_UUID_2.set(ItemTrackingComponent.INSTANCE.getTrackingId(other));
        ItemTrackingComponent.INSTANCE.removeTrackingId(stack);
        ItemTrackingComponent.INSTANCE.removeTrackingId(other);
    }

    /**
     * After the stacking compatibility check, restore the tracking UUIDs
     * that were stripped in the HEAD hook.
     */
    @Inject(at = @At("RETURN"), method = "isSameItemSameComponents")
    private static void visionOptimize$afterSameCheck(ItemStack stack, ItemStack other, CallbackInfoReturnable<Boolean> cir) {
        UUID id1 = STRIPPED_UUID_1.get();
        UUID id2 = STRIPPED_UUID_2.get();
        if (id1 != null) ItemTrackingComponent.INSTANCE.setTrackingId(stack, id1);
        if (id2 != null) ItemTrackingComponent.INSTANCE.setTrackingId(other, id2);
        STRIPPED_UUID_1.remove();
        STRIPPED_UUID_2.remove();
    }
}
