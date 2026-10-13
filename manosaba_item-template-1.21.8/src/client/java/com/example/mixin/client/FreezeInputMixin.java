// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.mixin.client;

import com.example.event.BottleStruggleClientHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Freezes all input during sleeping pills effect AND bottle struggle.
 * HEAD: locks hotbar slot via reflection.
 * TAIL: zeroes xxa/zza after aiStep runs.
 */
@Mixin(LocalPlayer.class)
public class FreezeInputMixin {

    @Unique
    private int lockedSlot = -1;
    @Unique
    private boolean wasLocked = false;
    @Unique
    private static Field selectedField = null;

    @Unique
    private static Field getSelectedField() {
        if (selectedField == null) {
            try {
                selectedField = Inventory.class.getDeclaredField("selected");
                selectedField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                try {
                    selectedField = Inventory.class.getDeclaredField("selectedSlot");
                    selectedField.setAccessible(true);
                } catch (NoSuchFieldException ignored) {}
            }
        }
        return selectedField;
    }

    @Unique
    private static boolean shouldFreeze(LocalPlayer self) {
        // Sleeping effect
        var sleepingHolder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("manosaba_item", "sleeping")
        );
        if (sleepingHolder.isPresent() && self.hasEffect(sleepingHolder.get())) return true;

        // Bottle struggle (active or ended display)
        if (BottleStruggleClientHandler.INSTANCE.getAttackerId() != null
            || BottleStruggleClientHandler.INSTANCE.getVictimId() != null) return true;

        return false;
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void onAiStepHead(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        boolean frozen = shouldFreeze(self);

        if (frozen) {
            // Lock hotbar slot
            try {
                Field f = getSelectedField();
                if (f != null) {
                    if (!wasLocked) {
                        lockedSlot = f.getInt(self.getInventory());
                        wasLocked = true;
                    }
                    f.setInt(self.getInventory(), lockedSlot);
                }
            } catch (IllegalAccessException ignored) {}
        } else {
            wasLocked = false;
        }
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void onAiStepTail(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (shouldFreeze(self)) {
            // Zero movement after aiStep processes input
            self.xxa = 0f;
            self.zza = 0f;
            self.setSprinting(false);
            self.setJumping(false);
        }
    }
}
