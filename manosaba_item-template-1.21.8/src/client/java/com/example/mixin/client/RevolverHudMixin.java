package com.example.mixin.client;

import com.example.item.ModItems;
import com.example.client.RevolverClientState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public final class RevolverHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void renderRevolverHud(GuiGraphics graphics, DeltaTracker delta, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        ItemStack stack = client.player.getMainHandItem();
        if (!stack.is(ModItems.ZUOLUN) && !stack.is(ModItems.ROTARY_MAGAZINE)) return;
        int x = client.getWindow().getGuiScaledWidth() / 2;
        if (!RevolverClientState.isReloading()) return;
        int y = client.getWindow().getGuiScaledHeight() - 60;
        graphics.drawCenteredString(client.font,
            String.format("换弹 %.2f", RevolverClientState.ticks() / 20.0), x, y, 0xFFFFFFFF);
    }
}
