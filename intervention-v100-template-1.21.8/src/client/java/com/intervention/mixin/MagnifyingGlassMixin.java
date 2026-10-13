// Copyright (c) 2026 才羽モモイ. Proprietary - Internal Use Only. 内部私有，严禁任何形式使用（包括游玩、复制、分发）。
package com.intervention.mixin;

import com.intervention.Main;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 放大镜 FOV 动画 Mixin。
 * <p>
 * 长按右键时从当前 FOV 平滑减去 20，松开右键平滑恢复，
 * 使用指数衰减插值实现丝滑过渡。
 * </p>
 */
@Mixin(GameRenderer.class)
public abstract class MagnifyingGlassMixin {

    /** 放大镜缩放动画进度：0.0 = 无缩放, 1.0 = 完全缩放（-20 FOV） */
    @Unique
    private float magnifyingGlassZoomProgress = 0.0F;

    /** 动画插值速度（每帧趋近目标的比率，0~1，越大越快） */
    @Unique
    private static final float ANIMATION_SPEED = 0.12F;

    /** 缩放 FOV 幅度 */
    @Unique
    private static final float ZOOM_AMOUNT = 30.0F;

    /**
     * 在 getFov 返回前检查：如果玩家正在使用放大镜，在原 FOV 上平滑减去 20。
     * 仅在 useFovSetting=true 时处理（主画面渲染），避免影响其他渲染通道。
     */
    @Inject(
            method = "getFov",
            at = @At("RETURN"),
            cancellable = true
    )
    private void modifyFovForMagnifyingGlass(Camera camera, float partialTicks, boolean useFovSetting,
                                             CallbackInfoReturnable<Float> cir) {
        // 仅处理主画面，跳过粒子/UI等渲染通道
        if (!useFovSetting) return;
        if (!(camera.getEntity() instanceof Player player)) return;

        boolean active = player.isUsingItem()
                && player.getUseItem().getItem() == Main.MAGNIFYING_GLASS;
        float target = active ? 1.0F : 0.0F;

        // 指数衰减平滑：每帧向目标值靠近 ANIMATION_SPEED 比例
        magnifyingGlassZoomProgress += (target - magnifyingGlassZoomProgress) * ANIMATION_SPEED;

        // 趋近目标时直接吸附，避免浮点抖动
        if (Math.abs(magnifyingGlassZoomProgress - target) < 0.0005F) {
            magnifyingGlassZoomProgress = target;
        }

        // 仅在进度 > 0 时修改 FOV（避免静止时无效计算）
        if (magnifyingGlassZoomProgress > 0.0005F) {
            float currentFov = cir.getReturnValue();
            cir.setReturnValue(currentFov - magnifyingGlassZoomProgress * ZOOM_AMOUNT);
        }
    }
}