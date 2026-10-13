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
 * 崴脚效果 - 移动速度降至4/5，屏幕边缘灰尘
 * 当玩家获得生命恢复/瞬间治疗时效果消失
 * 坐着时加速消耗（每秒减3秒时长）
 * 与骨折、脱骨折互斥
 */
class TwistedAnkleEffect : MobEffect(
    MobEffectCategory.NEUTRAL,
    0xCCAA44  // 暗黄色
) {
    init {
        // 速度降至 4/5 (减少 1/5)
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "twisted_ankle_slow"),
            -1.0 / 5.0,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        )
    }

    override fun applyEffectTick(level: ServerLevel, entity: LivingEntity, amplifier: Int): Boolean {
        if (entity !is Player) return true

        // 检查是否有生命恢复或瞬间治疗，有则移除此效果
        if (DislocatedBoneEffect.hasHealingEffect(entity)) {
            removeSelfFrom(entity)
            return false
        }

        // 坐着时加速消耗
        if (entity.isPassenger) {
            DislocatedBoneEffect.accelerateEffect(entity, ResourceLocation.fromNamespaceAndPath("mano_effect", "twisted_ankle"))
        }

        return true
    }

    private fun removeSelfFrom(entity: LivingEntity) {
        val holder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("mano_effect", "twisted_ankle")
        )
        if (holder.isPresent) {
            entity.removeEffect(holder.get())
        }
    }

    override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean {
        return true
    }
}
