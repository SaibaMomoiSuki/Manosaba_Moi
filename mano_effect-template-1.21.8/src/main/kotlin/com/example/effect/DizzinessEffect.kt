package com.example.effect

import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory

/**
 * 头部眩晕效果
 * 客户端视觉效果和输入反转由客户端mixin处理
 */
class DizzinessEffect : MobEffect(
    MobEffectCategory.NEUTRAL,  // 无粒子效果
    0x6A0DAD  // 紫色粒子颜色
)
