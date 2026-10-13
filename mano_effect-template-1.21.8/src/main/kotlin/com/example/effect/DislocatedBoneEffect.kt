package com.example.effect

import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player

/**
 * 脱骨折效果 - 移动速度降至3/4，屏幕边缘灰尘，跳跃落地受击模糊
 * 当玩家获得生命恢复/瞬间治疗时效果消失
 * 坐着时加速消耗（每秒减3秒时长）
 */
class DislocatedBoneEffect : MobEffect(
    MobEffectCategory.NEUTRAL,
    0xCC9966  // 浅棕色
) {
    init {
        // 速度降至 3/4 (减少 1/4)
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "dislocated_bone_slow"),
            -1.0 / 4.0,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        )
    }

    override fun onEffectAdded(entity: LivingEntity, amplifier: Int) {
        super.onEffectAdded(entity, amplifier)
        // 脱骨折造成 1.5 颗心（3 点）伤害
        val level = entity.level()
        if (entity is Player && level is ServerLevel) {
            entity.hurtServer(level, entity.damageSources().generic(), 3.0f)
        }
    }

    override fun applyEffectTick(level: ServerLevel, entity: LivingEntity, amplifier: Int): Boolean {
        if (entity !is Player) return true

        // 检查是否有生命恢复或瞬间治疗，有则移除此效果
        if (hasHealingEffect(entity)) {
            removeSelfFrom(entity)
            return false
        }

        // 坐着时加速消耗：每秒减3秒时长（每tick额外减2tick）
        if (entity.isPassenger) {
            accelerateEffect(entity, ResourceLocation.fromNamespaceAndPath("mano_effect", "dislocated_bone"))
        }

        return true
    }

    private fun removeSelfFrom(entity: LivingEntity) {
        val holder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("mano_effect", "dislocated_bone")
        )
        if (holder.isPresent) {
            entity.removeEffect(holder.get())
        }
    }

    override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean {
        return true // 每tick检查
    }

    companion object {
        /**
         * 检查实体是否有再生或瞬间治疗效果
         */
        fun hasHealingEffect(entity: LivingEntity): Boolean {
            val regeneration = BuiltInRegistries.MOB_EFFECT.get(
                ResourceLocation.withDefaultNamespace("regeneration")
            )
            val instantHealth = BuiltInRegistries.MOB_EFFECT.get(
                ResourceLocation.withDefaultNamespace("instant_health")
            )
            return (regeneration.isPresent && entity.hasEffect(regeneration.get())) ||
                   (instantHealth.isPresent && entity.hasEffect(instantHealth.get()))
        }

        /**
         * 加速效果消耗 - 每秒减3秒（每tick额外减2tick）
         * @param effectKey 要加速的效果的ResourceLocation key
         */
        fun accelerateEffect(entity: LivingEntity, effectKey: ResourceLocation) {
            val holder = BuiltInRegistries.MOB_EFFECT.get(effectKey)
            if (holder.isEmpty) return
            val effect = entity.getEffect(holder.get()) ?: return
            // 使用accessor减少额外2tick时长 (1 tick vanilla + 2 extra = 3 per tick)
            try {
                val accessor = effect as com.example.mixin.MobEffectInstanceAccessor
                accessor.setDurationField(effect.getDuration() - 2)
            } catch (_: Exception) {}
        }
    }
}
