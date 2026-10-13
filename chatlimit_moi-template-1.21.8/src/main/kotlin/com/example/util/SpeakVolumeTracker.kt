package com.example.util

import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.ChatFormatting
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import java.util.UUID
import kotlin.random.Random

/**
 * Tracks each player's selected speaking mode on the server side.
 *
 * Two modes exist:
 * - NORMAL (1): distance/penetration filtering, scaled by a continuous range
 *   [multiplier] (×1 = normal, <1 = quieter, >1 = louder/shout).
 * - HEART (2→3): tag-based private channel (see HeartTagTracker).
 */
object SpeakVolumeTracker {

    const val MODE_NORMAL = 1
    const val MODE_HEART = 3
    const val MODE_ADMIN = 5

    /** A player's speak selection: [mode] plus the normal-mode [multiplier]. */
    data class SpeakState(
        val mode: Int = MODE_NORMAL,
        val multiplier: Double = 1.0,
        val attitude: Int = 0,
        val transform: Boolean = false,
    )

    data class ProcessedSpeech(
        val text: String,
        val multiplier: Double,
        val shout: Boolean,
        val shoutCharacterCount: Int = 0,
        val originalText: String = text,
        val attitude: Int = 0,
    )

    data class RestrictionState(
        var shoutLockedUntil: Long = 0,
        var raisedLockedUntil: Long = 0,
        var raisedCount: Int = 0,
        var lastRaisedAt: Long = 0,
    )

    private const val RAISED_COOLDOWN_MS = 120_000L
    private const val RAISED_CHAIN_MS = 20_000L
    private val coughMessages = listOf(
        "咳咳...咳咳咳...", "啊咳..咳咳...咳..", "呃咳咳...呃咳咳...",
        "啊咳...呃咳咳...嗯...", "咳...咳...！", "咳咳!咳咳....",
        "呃...呃嗯!咳...", "咳咳...嗯...", "咳咳...!"
    )
    private val dizzinessMessages = listOf(
        "你的耳朵震耳欲聋", "你感到耳蜗在悲鸣...", "这声音让你头晕目眩...",
        "这声音比电摇滚现场还刺耳...", "快停下...快停下...!"
    )

    private val playerStates = mutableMapOf<UUID, SpeakState>()
    private val restrictions = mutableMapOf<UUID, RestrictionState>()
    private val playerMutedUntil = mutableMapOf<UUID, Long>()

    fun hasPlayerTag(player: ServerPlayer): Boolean =
        player.tags.any { it.equals("player", true) }

    fun isPlayerMuted(player: ServerPlayer): Boolean =
        hasPlayerTag(player) && (playerMutedUntil[player.uuid] ?: 0L) > System.currentTimeMillis()

    fun enablePlayerSilence(server: net.minecraft.server.MinecraftServer) {
        val until = System.currentTimeMillis() + 15_000L
        server.playerList.players.filter { hasPlayerTag(it) }.forEach { player ->
            playerMutedUntil[player.uuid] = until
            player.connection.send(ClientboundSetTitleTextPacket(
                net.minecraft.network.chat.Component.literal("语言限制更换")
                    .withStyle(ChatFormatting.YELLOW)
            ))
            player.connection.send(ClientboundSetSubtitleTextPacket(
                net.minecraft.network.chat.Component.literal("15秒后解除禁言")
                    .withStyle(ChatFormatting.YELLOW)
            ))
            player.playNotifySound(SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0f, 1.0f)
        }
    }

    fun disablePlayerSilence(server: net.minecraft.server.MinecraftServer) {
        playerMutedUntil.clear()
    }

    fun setState(player: ServerPlayer, mode: Int, multiplier: Double, attitude: Int = 0, transform: Boolean = false) {
        val safeMode = when {
            mode == MODE_HEART -> MODE_HEART
            mode == MODE_ADMIN && hasAdminTag(player) -> MODE_ADMIN
            else -> MODE_NORMAL
        }
        playerStates[player.uuid] = SpeakState(safeMode, multiplier.coerceIn(0.1, 10.0), attitude.coerceIn(0, 10), transform)
    }

    fun getState(player: ServerPlayer): SpeakState =
        playerStates[player.uuid] ?: SpeakState()

    fun attitudeName(index: Int): String =
        listOf("平缓", "生气", "伤心", "开心", "烦躁", "惊恐", "乏累", "欣慰", "严肃", "微笑", "开玩笑")
            .getOrElse(index.coerceIn(0, 10)) { "平缓" }

    fun removePlayer(player: ServerPlayer) {
        playerStates.remove(player.uuid)
        restrictions.remove(player.uuid)
        playerMutedUntil.remove(player.uuid)
    }

    fun hasAdminTag(player: ServerPlayer): Boolean =
        player.tags.any { it.equals("master", true) || it.equals("changwu", true) }

    fun processSpeech(player: ServerPlayer, rawText: String, shoutMax: Double): ProcessedSpeech {
        val state = getState(player)
        if (state.mode == MODE_HEART || state.mode == MODE_ADMIN) {
            return ProcessedSpeech(rawText, state.multiplier, false, originalText = rawText, attitude = state.attitude)
        }

        val now = System.currentTimeMillis()
        val restriction = restrictions.getOrPut(player.uuid) { RestrictionState() }
        val normalMax = 1.0
        val quietMax = 0.8
        val raisedThreshold = 1.0 + (shoutMax.coerceAtLeast(2.0) - 1.0) * 0.2
        val shoutThreshold = 1.0 + (shoutMax.coerceAtLeast(2.0) - 1.0) * 0.6
        var multiplier = state.multiplier
        if (restriction.shoutLockedUntil > now) multiplier = minOf(multiplier, quietMax)
        if (restriction.raisedLockedUntil > now) multiplier = minOf(multiplier, normalMax)

        var text = rawText
        var speechMultiplier = multiplier
        val effectiveAttitude = if (state.transform) state.attitude else 0
        when (effectiveAttitude) {
            4 -> if (Random.nextInt(100) < 40) speechMultiplier += 5.0
            5, 6 -> text = addPauses(text)
        }
        val isShout = multiplier >= shoutThreshold
        if (isShout) {
            val characterCount = rawText.codePointCount(0, rawText.length).coerceAtLeast(1)
            val cooldownSeconds = (60 + (characterCount - 1) * 10).coerceAtMost(120)
            text = takeCodePoints(text, 10) + "\n(嘶哑的声音从喉咙中发出...)"
            restriction.shoutLockedUntil = maxOf(restriction.shoutLockedUntil, now + cooldownSeconds * 1000L)
            text = applyAttitude(text, effectiveAttitude)
            return ProcessedSpeech(text, speechMultiplier, true, characterCount, rawText, state.attitude)
        } else if (multiplier >= raisedThreshold) {
            if (now - restriction.lastRaisedAt > RAISED_CHAIN_MS) restriction.raisedCount = 0
            restriction.lastRaisedAt = now
            restriction.raisedCount++
            val tooLong = text.codePointCount(0, text.length) > 40
            if (tooLong) text = takeCodePoints(text, 40)
            if (tooLong || restriction.raisedCount >= 3) {
                text += "\n" + coughMessages.random()
                restriction.raisedLockedUntil = maxOf(restriction.raisedLockedUntil, now + RAISED_COOLDOWN_MS)
                restriction.raisedCount = 0
            }
        }
        text = applyAttitude(text, effectiveAttitude)
        return ProcessedSpeech(text, speechMultiplier, false, originalText = rawText, attitude = state.attitude)
    }

    fun maxAllowedSliderPosition(player: ServerPlayer): Double {
        val restriction = restrictions[player.uuid] ?: return 1.0
        val now = System.currentTimeMillis()
        return when {
            restriction.shoutLockedUntil > now -> 0.399
            restriction.raisedLockedUntil > now -> 0.5
            else -> 1.0
        }
    }

    fun restrictionRemainingMillis(player: ServerPlayer): Long {
        val restriction = restrictions[player.uuid] ?: return 0
        val now = System.currentTimeMillis()
        return maxOf(restriction.shoutLockedUntil, restriction.raisedLockedUntil, now) - now
    }

    fun applyShoutDizziness(sender: ServerPlayer, characterCount: Int) {
        val effectId = ResourceLocation.fromNamespaceAndPath("mano_effect", "dizziness")
        val effect = BuiltInRegistries.MOB_EFFECT.get(effectId).orElse(null) ?: return
        val server = sender.server ?: return
        val durationSeconds = (20 + (characterCount.coerceAtLeast(1) - 1) * 3).coerceAtMost(50)
        for (receiver in server.playerList.players) {
            if (receiver == sender || receiver.level() != sender.level() || receiver.distanceToSqr(sender) > 9.0) continue
            receiver.addEffect(MobEffectInstance(effect, durationSeconds * 20, 0, false, false, true))
            val roll = Random.nextInt(100)
            val message = when {
                roll < 4 -> dizzinessMessages[3]
                roll < 8 -> dizzinessMessages[4]
                else -> dizzinessMessages[Random.nextInt(3)]
            }
            receiver.connection.send(ClientboundSystemChatPacket(Component.literal(message), false))
        }
    }

    private fun takeCodePoints(text: String, count: Int): String {
        val end = text.offsetByCodePoints(0, minOf(count, text.codePointCount(0, text.length)))
        return text.substring(0, end)
    }

    private fun applyAttitude(text: String, attitude: Int): String {
        if (text.isEmpty()) return text
        return when (attitude) {
            1 -> text.replace(Regex("([。！？?!])+$")) {
                when {
                    Random.nextInt(100) < 15 -> "！！！"
                    Random.nextBoolean() -> "！"
                    else -> it.value
                }
            }
            3 -> text.replace(Regex("[。！？?!]+$")) { if (Random.nextInt(100) < 60) "☆~" else it.value }
            5 -> text.replace(Regex("[。！？?!]+$")) {
                when (Random.nextInt(100)) { in 0..19 -> "..."; in 20..39 -> "啊..."; else -> it.value }
            }
            7 -> text
            10 -> if (Random.nextInt(100) < 40) text + listOf("嗯哼哼...", "哈哈", "啊哈哈...").random() else text
            else -> text
        }
    }

    private fun addPauses(text: String): String {
        val result = StringBuilder()
        var plainCharacters = 0
        val nextPause = Random.nextInt(5, 9)
        for (ch in text) {
            result.append(ch)
            if (ch in "，。！？?!、；：,.!?;:") continue
            plainCharacters++
            if (plainCharacters >= nextPause && Random.nextInt(100) < 40) {
                result.append("...")
                plainCharacters = 0
            }
        }
        return result.toString()
    }
}
