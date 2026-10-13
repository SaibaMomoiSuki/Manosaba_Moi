package com.example.effect

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player

/**
 * 固定器效果 - 移动速度降至5/6
 * 使用建议固定器后获得，替代骨折/脱骨折/崴脚效果
 * 持续时间与原受伤效果相同
 * 当固定器效果结束时，重创效果也随之结束
 */
class SplintedEffect : MobEffect(
    MobEffectCategory.NEUTRAL,
    0x88AACC  // 淡蓝灰色
) {
    init {
        // 速度降至 5/6 (减少 1/6)
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "splinted_slow"),
            -1.0 / 6.0,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        )
    }

    override fun applyEffectTick(level: ServerLevel, entity: LivingEntity, amplifier: Int): Boolean {
        if (entity !is Player) return true

        // 如果固定器即将结束，移除重创效果
        val seriouslyInjuredHolder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("mano_effect", "manosaba_seriously_injured")
        )
        if (seriouslyInjuredHolder.isPresent && entity.hasEffect(seriouslyInjuredHolder.get())) {
            // 检查固定器是否还存在（duration <= 1 意味着即将结束）
            val splintedHolder = BuiltInRegistries.MOB_EFFECT.get(
                ResourceLocation.fromNamespaceAndPath("mano_effect", "splinted")
            )
            if (splintedHolder.isEmpty || !entity.hasEffect(splintedHolder.get())) {
                entity.removeEffect(seriouslyInjuredHolder.get())
            }
        }

        return true
    }

    override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean {
        return duration <= 2 // 只在最后2tick检查，确保及时移除重创
    }
}
