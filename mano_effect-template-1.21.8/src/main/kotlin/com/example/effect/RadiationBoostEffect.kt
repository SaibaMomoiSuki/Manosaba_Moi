package com.example.effect

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes

/**
 * 辐射亢奋效果 - 屏幕边缘白色线条 + 速度提升至6/5倍
 * 服务端效果类 - 客户端视觉效果由mixin处理
 */
class RadiationBoostEffect : MobEffect(
    MobEffectCategory.NEUTRAL,  // 无粒子效果
    0xCCDDEE  // 淡蓝白色粒子
) {
    init {
        // 速度变为原来的 6/5 = 1.2倍 (增加 1/5)
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "radiation_boost_speed"),
            1.0 / 5.0,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        )
    }

    override fun applyEffectTick(level: ServerLevel, entity: LivingEntity, amplifier: Int): Boolean {
        return true
    }

    override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean {
        return true
    }
}
