package com.example

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.client.renderer.LevelTargetBundle
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory
import kotlin.random.Random

object Mano_effectClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("mano_effect:client")
    private var shaderPreloaded = false
    private var shaderPreloadAttempts = 0
    private var hadDizziness = false
    private val dizzinessShaderId = ResourceLocation.fromNamespaceAndPath("mano_effect", "dizziness")
    private val dizzinessEffectId = ResourceLocation.fromNamespaceAndPath("mano_effect", "dizziness")

    // Shared state for blur radius — accessed by OptionsMixin and GameRendererMixin
    @JvmField
    var smoothBlurRadius = 0f
    @JvmField
    var targetBlurRadius = 6f
    @JvmField
    var blurChangeTimer = 0
    @JvmField
    var blurActive = false
    @JvmField
    var sensitivityNoise = 0f

    // Shared state for radiation effect fade
    @JvmField
    var smoothRadiationAlpha = 0f
    @JvmField
    var radiationActive = false

    // Dust particle state (single particle, ~1/6 screen, lifecycle animation)
    @JvmField
    var dustX = 0.5f
    @JvmField
    var dustY = 0.5f
    @JvmField
    var dustSize = 0.28f
    @JvmField
    var dustAlpha = 0f
    @JvmField
    var dustSpawnTime = 0L
    @JvmField
    var dustTextureIndex = 0

    // Shared state for radiation boost effect fade
    @JvmField
    var smoothBoostAlpha = 0f
    @JvmField
    var boostActive = false

    // Shared state for player-hit screen-edge gray vignette effect
    @JvmField
    var hitEffectActive = false
    @JvmField
    var hitEffectStartTime = 0L

    // Shared state for low-health red edge effect
    // Tier 0 = none (>5HP), 1 = subtle red (3-5HP), 2 = full red (≤3HP)
    @JvmField
    var lowHealthTier = 0
    @JvmField
    var lowHealthAlpha = 0f           // smooth fade alpha for red vignette

    // Blood splat state (cycles between 血迹1/2/3.png every few seconds)
    @JvmField
    var bloodSplatIndex = 0           // 0, 1, or 2
    @JvmField
    var bloodSplatX = 0.3f            // normalized screen position
    @JvmField
    var bloodSplatY = 0.3f
    @JvmField
    var bloodSplatSize = 0.0f         // normalized size (0 = hidden)
    @JvmField
    var bloodSplatAlpha = 0f          // fade alpha
    @JvmField
    var bloodSplatTimer = 0L          // for cycling
    @JvmField
    var bloodDamageBurstActive = false
    @JvmField
    var bloodDamageBurstEndTime = 0L

    // Injury effect state (dislocated_bone, fracture, twisted_ankle)
    @JvmField
    var dislocatedBoneActive = false
    @JvmField
    var fractureActive = false
    @JvmField
    var twistedAnkleActive = false
    @JvmField
    var splintedActive = false
    @JvmField
    var seriouslyInjuredActive = false
    @JvmField
    var dizzinessActive = false

    // Edge dust state for injuries (dust only at screen edges, similar to radiation dust)
    @JvmField
    var smoothInjuryDustAlpha = 0f
    @JvmField
    var injuryDustX = 0f
    @JvmField
    var injuryDustY = 0f
    @JvmField
    var injuryDustSize = 0f
    @JvmField
    var injuryDustAlpha = 0f
    @JvmField
    var injuryDustSpawnTime = 0L
    @JvmField
    var injuryDustTextureIndex = 0

    // Fracture blur state (persistent blur like dizziness but for fracture)
    @JvmField
    var fractureBlurTargetRadius = 6f
    @JvmField
    var fractureBlurChangeTimer = 0
    @JvmField
    var fractureBlurActive = false

    // Dislocated bone jump/land hit effect state
    @JvmField
    var injuryHitEffectActive = false
    @JvmField
    var injuryHitEffectStartTime = 0L

    // Twisted ankle black border vignette state (replaces injury edge dust)
    @JvmField
    var twistedAnkleBorderAlpha = 0f

    // Seriously injured dust state (similar to injury edge dust)
    @JvmField
    var smoothSeriouslyInjuredDustAlpha = 0f
    @JvmField
    var seriouslyInjuredDustX = 0f
    @JvmField
    var seriouslyInjuredDustY = 0f
    @JvmField
    var seriouslyInjuredDustSize = 0f
    @JvmField
    var seriouslyInjuredDustAlpha = 0f
    @JvmField
    var seriouslyInjuredDustSpawnTime = 0L
    @JvmField
    var seriouslyInjuredDustTextureIndex = 0
    @JvmField
    var seriouslyInjuredAmplifier = 0  // 重创等级 (0=I, 1=II, 2=III, 3=IV+)

    // Rain droplet overlay state — dynamic sliding droplets with trails
    const val MAX_RAIN_DROPLETS = 20
    @JvmField var rainDropX = FloatArray(MAX_RAIN_DROPLETS)
    @JvmField var rainDropY = FloatArray(MAX_RAIN_DROPLETS)
    @JvmField var rainDropPrevX = FloatArray(MAX_RAIN_DROPLETS)
    @JvmField var rainDropPrevY = FloatArray(MAX_RAIN_DROPLETS)
    @JvmField var rainDropSize = FloatArray(MAX_RAIN_DROPLETS)
    @JvmField var rainDropAlpha = FloatArray(MAX_RAIN_DROPLETS)
    @JvmField var rainDropVy = FloatArray(MAX_RAIN_DROPLETS)
    @JvmField var rainDropWobblePhase = FloatArray(MAX_RAIN_DROPLETS)
    @JvmField var rainDropWobbleFreq = FloatArray(MAX_RAIN_DROPLETS)
    @JvmField var rainDropLifePhase = IntArray(MAX_RAIN_DROPLETS) { 4 }  // 0=appear, 1=grow, 2=run, 3=fade, 4=respawn
    @JvmField var rainDropSpawnTime = LongArray(MAX_RAIN_DROPLETS)
    @JvmField var rainDropFadeOutMs = LongArray(MAX_RAIN_DROPLETS)
    @JvmField var rainDropTargetSize = FloatArray(MAX_RAIN_DROPLETS) // size before running
    @JvmField var rainDropRunSpeed = FloatArray(MAX_RAIN_DROPLETS)   // screen/sec when running
    @JvmField var rainDropPhaseDurationMs = LongArray(MAX_RAIN_DROPLETS)
    @JvmField var rainActiveCount = 6
    @JvmField var rainBlurRadius = 0f
    @JvmField var rainTargetBlur = 0f
    @JvmField var isRaining = false
    @JvmField var rainIntensity = 0f
    @JvmField var rainFadeAlpha = 0f
    @JvmField var wetScreenAlpha = 0f
    @JvmField var underwater = false
    @JvmField var soundMuffleStrength = 0f
    @JvmField var waterEnterTime = 0L
    @JvmField var waterExitTime = 0L

    // Radiation diagonal line effect — white 25° lines flashing in/out
    const val MAX_RAD_LINES = 6
    @JvmField var radLineX = FloatArray(MAX_RAD_LINES)
    @JvmField var radLineY = FloatArray(MAX_RAD_LINES)
    @JvmField var radLineLength = FloatArray(MAX_RAD_LINES)  // normalized screen length
    @JvmField var radLineAlpha = FloatArray(MAX_RAD_LINES)
    @JvmField var radLinePhase = IntArray(MAX_RAD_LINES) { 2 }  // 0=fadeIn, 1=hold, 2=fadeOut→respawn
    @JvmField var radLineSpawnTime = LongArray(MAX_RAD_LINES)
    @JvmField var radLineFadeInMs = LongArray(MAX_RAD_LINES)
    @JvmField var radLineHoldMs = LongArray(MAX_RAD_LINES)
    @JvmField var radLineFadeOutMs = LongArray(MAX_RAD_LINES)
    @JvmField var radLineThickness = FloatArray(MAX_RAD_LINES) { 1.2f }

    // Fire ash dust — particles rising from bottom when player is on fire
    const val MAX_FIRE_PARTICLES = 12
    @JvmField var fireDustAlpha = 0f           // smoothed overall alpha from fire state
    @JvmField var fireDustTargetAlpha = 0f     // 1 when on fire, 0 when not
    @JvmField var fireParticleX = FloatArray(MAX_FIRE_PARTICLES)
    @JvmField var fireParticleY = FloatArray(MAX_FIRE_PARTICLES)
    @JvmField var fireParticleBaseX = FloatArray(MAX_FIRE_PARTICLES) // stable base X, no random jitter
    @JvmField var fireParticleAlpha = FloatArray(MAX_FIRE_PARTICLES)
    @JvmField var fireParticleSize = FloatArray(MAX_FIRE_PARTICLES)
    @JvmField var fireParticleTextureIndex = IntArray(MAX_FIRE_PARTICLES)
    @JvmField var fireParticleSpawnTime = LongArray(MAX_FIRE_PARTICLES)
    @JvmField var fireParticleLifeMs = LongArray(MAX_FIRE_PARTICLES)
    @JvmField var fireParticlePhase = IntArray(MAX_FIRE_PARTICLES) { 2 }

    override fun onInitializeClient() {
        logger.info("Mano Effect client init")

        // Preload the dizziness post-effect shader
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            val player = client.player
            val dizziness = BuiltInRegistries.MOB_EFFECT.get(dizzinessEffectId)
            val hasDizziness = player != null && dizziness.isPresent && player.hasEffect(dizziness.get())

            if (hasDizziness && !hadDizziness) {
                if (Random.nextFloat() < 0.95f) {
                    val sound = if (Random.nextBoolean()) Mano_effect.TINNITUS_1 else Mano_effect.TINNITUS_2
                    client.soundManager.play(SimpleSoundInstance.forUI(sound, 1.0f, 1.0f))
                }
            }
            hadDizziness = hasDizziness

            if (!shaderPreloaded && shaderPreloadAttempts < 3 && client.player != null) {
                shaderPreloadAttempts++
                try {
                    val chain = client.shaderManager.getPostChain(
                        dizzinessShaderId,
                        LevelTargetBundle.MAIN_TARGETS
                    )
                    if (chain != null) {
                        shaderPreloaded = true
                        logger.info("Dizziness shader preloaded OK")
                    }
                } catch (exception: Exception) {
                    if (shaderPreloadAttempts == 3) {
                        logger.warn("Dizziness shader preload failed after 3 attempts", exception)
                    }
                }
            }
        }
    }
}
