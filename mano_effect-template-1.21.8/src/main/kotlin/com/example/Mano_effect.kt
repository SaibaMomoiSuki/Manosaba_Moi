package com.example

import com.example.command.ManoEffectCommand
import com.example.config.ModConfig
import com.example.effect.DislocatedBoneEffect
import com.example.effect.DizzinessEffect
import com.example.effect.FractureEffect
import com.example.effect.RadiationBoostEffect
import com.example.effect.RadiationEffect
import com.example.effect.SeriouslyInjuredEffect
import com.example.effect.SplintedEffect
import com.example.effect.TwistedAnkleEffect
import com.example.item.SplintItem
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import org.slf4j.LoggerFactory

object Mano_effect : ModInitializer {
    private val logger = LoggerFactory.getLogger("mano_effect")

    val DIZZINESS = DizzinessEffect()
    val RADIATION = RadiationEffect()
    val RADIATION_BOOST = RadiationBoostEffect()
    val DISLOCATED_BONE = DislocatedBoneEffect()
    val FRACTURE = FractureEffect()
    val TWISTED_ANKLE = TwistedAnkleEffect()
    val SPLINTED = SplintedEffect()
    val SERIOUSLY_INJURED = SeriouslyInjuredEffect()

    val SPLINT_ITEM = SplintItem(
        Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("mano_effect", "splint")))
    )

    @JvmField
    val HEARTBEAT: SoundEvent = SoundEvent.createVariableRangeEvent(
        ResourceLocation.fromNamespaceAndPath("mano_effect", "heartbeat")
    )

    @JvmField
    val TINNITUS_1: SoundEvent = SoundEvent.createVariableRangeEvent(
        ResourceLocation.fromNamespaceAndPath("mano_effect", "tinnitus_1")
    )

    @JvmField
    val TINNITUS_2: SoundEvent = SoundEvent.createVariableRangeEvent(
        ResourceLocation.fromNamespaceAndPath("mano_effect", "tinnitus_2")
    )

    @JvmStatic
    var fallDamageEnabled = true // default: vanilla fall damage enabled

    override fun onInitialize() {
        // Load persisted config
        ModConfig.load()
        fallDamageEnabled = ModConfig.fallDamageEnabled

        logger.info("Initializing Mano Effect Mod")

        // ---- Register effects ----

        Registry.register(
            BuiltInRegistries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "dizziness"),
            DIZZINESS
        )
        logger.info("Registered mano_effect:dizziness")

        Registry.register(
            BuiltInRegistries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "radiation"),
            RADIATION
        )
        logger.info("Registered mano_effect:radiation")

        Registry.register(
            BuiltInRegistries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "radiation_boost"),
            RADIATION_BOOST
        )
        logger.info("Registered mano_effect:radiation_boost")

        Registry.register(
            BuiltInRegistries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "dislocated_bone"),
            DISLOCATED_BONE
        )
        logger.info("Registered mano_effect:dislocated_bone")

        Registry.register(
            BuiltInRegistries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "fracture"),
            FRACTURE
        )
        logger.info("Registered mano_effect:fracture")

        Registry.register(
            BuiltInRegistries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "twisted_ankle"),
            TWISTED_ANKLE
        )
        logger.info("Registered mano_effect:twisted_ankle")

        Registry.register(
            BuiltInRegistries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "splinted"),
            SPLINTED
        )
        logger.info("Registered mano_effect:splinted")

        Registry.register(
            BuiltInRegistries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "manosaba_seriously_injured"),
            SERIOUSLY_INJURED
        )
        logger.info("Registered mano_effect:manosaba_seriously_injured")

        // ---- Register items ----

        Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "splint"),
            SPLINT_ITEM
        )
        logger.info("Registered mano_effect:splint")

        // ---- Register sounds ----

        Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "heartbeat"),
            HEARTBEAT
        )
        logger.info("Registered mano_effect:heartbeat sound")

        Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "tinnitus_1"),
            TINNITUS_1
        )
        Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            ResourceLocation.fromNamespaceAndPath("mano_effect", "tinnitus_2"),
            TINNITUS_2
        )
        logger.info("Registered tinnitus sounds")

        // Add to creative tab (Tools & Utilities)
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register { entries ->
            entries.accept(SPLINT_ITEM)
        }

        // ---- Register commands ----

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            ManoEffectCommand.register(dispatcher)
        }
        logger.info("Registered /mano_effect command")
    }
}
