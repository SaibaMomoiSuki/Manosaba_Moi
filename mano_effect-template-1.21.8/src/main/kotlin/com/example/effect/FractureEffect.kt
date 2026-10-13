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
 * 骨折效果 - 移动速度降至3/5，屏幕边缘灰尘+持续模糊
 * 当玩家获得生命恢复/瞬间治疗时效果消失
 * 坐着时加速消耗（每秒减3秒时长）
 * 与脱骨折、崴脚互斥
 */
class FractureEffect : MobEffect(
    MobEffectCategory.NEUTRAL,
    0xCC4444  // 暗红色
) {
    init {
        // 速度降至 3/5 (减少 2/5)
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "fracture_slow"),
            -2.0 / 5.0,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        )
    }

    override fun onEffectAdded(entity: LivingEntity, amplifier: Int) {
        super.onEffectAdded(entity, amplifier)
        // 骨折造成 3 颗心（6 点）伤害
        val level = entity.level()
        if (entity is Player && level is ServerLevel) {
            entity.hurtServer(level, entity.damageSources().generic(), 6.0f)
        }
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
            DislocatedBoneEffect.accelerateEffect(entity, ResourceLocation.fromNamespaceAndPath("mano_effect", "fracture"))
        }

        return true
    }

    private fun removeSelfFrom(entity: LivingEntity) {
        val holder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("mano_effect", "fracture")
        )
        if (holder.isPresent) {
            entity.removeEffect(holder.get())
        }
    }

    override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean {
        return true
    }
}
