// Copyright (c) 2026 才羽モモイ. Proprietary - Internal Use Only. 内部私有，严禁任何形式使用（包括游玩、复制、分发）。
package com.intervention.mixin;

import com.intervention.Main;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让放大镜在第三人称显示望远镜举到面前的姿势
 */
@Mixin(PlayerRenderer.class)
public class MagnifyingGlassArmPoseMixin {

    @Inject(
            method = "getArmPose(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void modifyArmPose(Player player, ItemStack itemStack, InteractionHand interactionHand,
                                       CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        if (!itemStack.isEmpty()
                && player.getUsedItemHand() == interactionHand
                && player.getUseItemRemainingTicks() > 0
                && itemStack.getItem() == Main.MAGNIFYING_GLASS) {
            cir.setReturnValue(HumanoidModel.ArmPose.SPYGLASS);
        }
    }
}
