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
import org.slf4j.LoggerFactory
import java.util.*

/**
 * 辐射效果 — 25%减速 + 每秒随机受伤 + 结束后40s辐射亢奋
 *
 * 每秒判定 (shouldApplyEffectTickThisTick → every 20 ticks):
 *   15% 脱骨折 4~7秒
 *   10% 骨折   3~6秒
 *   10% 重创   3~10秒
 *   65% 无事
 *
 * 同类型冷却 = 效果持续时长 + 5秒 (冷却从效果结束时起算)
 */
class RadiationEffect : MobEffect(
    MobEffectCategory.NEUTRAL,
    0x8B7D3C
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger("mano_effect:radiation")
        private val SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("mano_effect", "radiation_slow")
        private const val COOLDOWN_AFTER_END_TICKS = 100L // 5s cooldown after injury expires

        // Track when each injury type can next be applied (gameTime value)
        private val nextAllowFracture = WeakHashMap<UUID, Long>()
        private val nextAllowDislocated = WeakHashMap<UUID, Long>()
        private val nextAllowSeriouslyInjured = WeakHashMap<UUID, Long>()
        // Track who already got post-radiation boost
        private val boostedEntities = mutableSetOf<UUID>()
    }

    init {
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            SPEED_MODIFIER_ID,
            -0.25,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        )
    }

    override fun applyEffectTick(level: ServerLevel, entity: LivingEntity, amplifier: Int): Boolean {
        if (entity !is Player) return true

        val now = level.gameTime
        val uuid = entity.uuid

        // Check & apply post-radiation boost
        checkAndApplyBoost(entity)

        // Roll per-second random injury
        val roll = entity.random.nextFloat()
        when {
            roll < 0.15f -> tryApply(entity, "dislocated_bone", 80..140, nextAllowDislocated, uuid, now)
            roll < 0.25f -> tryApply(entity, "fracture", 60..120, nextAllowFracture, uuid, now)
            roll < 0.35f -> tryApply(entity, "manosaba_seriously_injured", 60..200, nextAllowSeriouslyInjured, uuid, now)
            // else: 65% nothing
        }

        return true
    }

    override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean {
        // Tick once per second (every 20 ticks)
        return duration % 20 == 0
    }

    /** Try to apply an injury if cooldown has elapsed. Cooldown = injury duration + 5s. */
    private fun tryApply(
        entity: Player,
        effectId: String,
        durationRange: IntRange,
        cooldownMap: WeakHashMap<UUID, Long>,
        uuid: UUID,
        now: Long
    ) {
        val nextAllowed = cooldownMap[uuid] ?: 0L
        if (now < nextAllowed) return

        val holder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("mano_effect", effectId)
        )
        if (!holder.isPresent) return

        val injuryTicks = durationRange.random()
        entity.addEffect(
            MobEffectInstance(holder.get(), injuryTicks, 0, false, false, true)
        )
        // Cooldown = injury duration + 5s after effect ends
        cooldownMap[uuid] = now + injuryTicks + COOLDOWN_AFTER_END_TICKS

        LOGGER.debug("Radiation applied {} for {} ticks to {} (next allowed at {})",
            effectId, injuryTicks, entity.name.string, cooldownMap[uuid])
    }

    private fun checkAndApplyBoost(entity: Player) {
        val radHolder = BuiltInRegistries.MOB_EFFECT.get(
            ResourceLocation.fromNamespaceAndPath("mano_effect", "radiation")
        )
        if (!radHolder.isPresent) return

        val instance = entity.getEffect(radHolder.get())
        val uuid = entity.uuid

        if (instance != null && instance.duration in 1..20) {
            if (boostedEntities.add(uuid)) {
                val boostHolder = BuiltInRegistries.MOB_EFFECT.get(
                    ResourceLocation.fromNamespaceAndPath("mano_effect", "radiation_boost")
                )
                if (boostHolder.isPresent) {
                    entity.addEffect(
                        MobEffectInstance(boostHolder.get(), 800, 0, false, false, true)
                    )
                    LOGGER.info("Radiation ended for {} — applied 40s radiation_boost", entity.name.string)
                }
            }
        } else if (instance == null) {
            boostedEntities.remove(uuid)
        }
    }
}
