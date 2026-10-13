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
 * 重创效果 - 移动速度每级降低10% (I级90%, II级80%, III级70%...)
 * 视野四周出现灰尘，固定器效果结束时此效果也随之结束
 * 可通过 /effect 指令获得，标签为 manosaba_seriously_injured
 * 与骨折、脱骨折、崴脚互斥
 *
 * 升级机制：玩家在有重创效果时从6-12格高度再次跳下，
 * 则重创持续时间重置且等级提升I→II→III...
 */
class SeriouslyInjuredEffect : MobEffect(
    MobEffectCategory.NEUTRAL,
    0x8B1A1A  // 暗红色
) {
    init {
        // 每级速度降低10% (I级90%, II级80%, III级70%...)
        // Minecraft默认对amplifier进行倍乘: modifier * (amplifier + 1)
        // -0.1 * 1 = -0.1 (I级), -0.1 * 2 = -0.2 (II级), -0.1 * 3 = -0.3 (III级)...
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "seriously_injured_slow"),
            -0.1,
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

        // 检查固定器效果是否还存在，不存在则移除此效果
        val splintedHolder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("mano_effect", "splinted")
        )
        if (splintedHolder.isEmpty || !entity.hasEffect(splintedHolder.get())) {
            removeSelfFrom(entity)
            return false
        }

        // 坐着时加速消耗
        if (entity.isPassenger) {
            DislocatedBoneEffect.accelerateEffect(
                entity,
                ResourceLocation.fromNamespaceAndPath("mano_effect", "manosaba_seriously_injured")
            )
        }

        return true
    }

    private fun removeSelfFrom(entity: LivingEntity) {
        val holder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("mano_effect", "manosaba_seriously_injured")
        )
        if (holder.isPresent) {
            entity.removeEffect(holder.get())
        }
    }

    override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean {
        return true // 每tick检查固定器是否还存在
    }
}
