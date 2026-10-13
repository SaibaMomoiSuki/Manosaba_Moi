package com.example.state

import com.example.ChatLimit_MoiClient
import com.example.network.ChatLimitClientNetwork
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.fabricmc.loader.api.FabricLoader

/**
 * Client-side, single source of truth for the player's *speak mode* selection.
 *
 * The old UI exposed four discrete volumes (小声 / 正常 / 大喊 / 心声). That is
 * now collapsed into:
 *
 *  - a **mode** (正常 = normal, 心声 = heart), toggled by the dedicated key or
 *    by clicking the toggle button, and
 *  - a continuous **slider position** (0..1) that only applies in normal mode.
 *    It spans 小声 (far left, quiet) → 正常 (centre, ×1) → 大喊 (far right,
 *    shout), and maps to a range multiplier that scales how far normal speech
 *    carries.
 *
 * Both values are persisted statically so they survive closing/reopening chat,
 * and every change is pushed straight to the server.
 */
object SpeakModeState {

    const val MODE_NORMAL = 1
    const val MODE_HEART = 3
    const val MODE_BOOK = 4
    const val MODE_ADMIN = 5
    const val ATTITUDE_CALM = 0
    val ATTITUDES = listOf("平缓", "生气", "伤心", "开心", "烦躁", "惊恐", "乏累", "欣慰", "严肃", "微笑", "开玩笑")
    private val MODES = listOf("正常", "心声", "管理组")

    /** Floor for the range multiplier, so the far-left "quiet" end stays audible
     *  at point-blank instead of dividing by zero on the server. */
    private const val MIN_MULTIPLIER = 0.1

    @Volatile
    var mode: Int = MODE_NORMAL
        private set

    /** 0 = 小声 (far left), 0.5 = 正常 (centre), 1 = 大喊 (far right). */
    @Volatile
    var sliderPosition: Double = 0.5
        private set

    @Volatile
    private var restrictionMaxPosition: Double = 1.0
    @Volatile
    private var restrictionEndsAt: Long = 0

    @Volatile
    var attitude: Int = ATTITUDE_CALM
        private set
    @Volatile
    var transformAttitude: Boolean = false
        private set

    fun isNormalMode(): Boolean = mode == MODE_NORMAL
    fun isBookMode(): Boolean = mode == MODE_BOOK
    fun attitudeName(): String = ATTITUDES.getOrElse(attitude) { ATTITUDES[0] }
    fun getAttitudeName(): String = attitudeName()
    fun getAttitudes(): List<String> = ATTITUDES
    fun modeName(): String = when (mode) {
        MODE_HEART -> "心声"
        MODE_BOOK -> "素描本"
        MODE_ADMIN -> "管理组"
        else -> "正常"
    }
    fun getModeName(): String = modeName()
    fun getModes(): List<String> {
        ensureAvailableMode()
        return buildList {
        addAll(MODES.take(2))
        if (ChatLimit_MoiClient.adminAccess) add("管理组")
        if (canUseBookMode()) add("素描本")
        }
    }

    fun ensureAvailableMode() {
        if (mode == MODE_ADMIN && !canUseAdminMode()) {
            mode = MODE_NORMAL
            pushToServer()
        } else if (mode == MODE_BOOK && !canUseBookMode()) {
            mode = MODE_NORMAL
            pushToServer()
        }
    }

    fun setAttitude(value: Int) {
        attitude = value.coerceIn(0, ATTITUDES.lastIndex)
        pushToServer()
    }

    fun setTransformAttitude(value: Boolean) {
        transformAttitude = value
        pushToServer()
    }

    fun isTransformAttitude(): Boolean = transformAttitude

    fun setMode(value: Int) {
        val selected = getModes().getOrNull(value)
        mode = when (selected) {
            "心声" -> MODE_HEART
            "管理组" -> if (canUseAdminMode()) MODE_ADMIN else MODE_NORMAL
            "素描本" -> if (canUseBookMode()) MODE_BOOK else MODE_NORMAL
            else -> MODE_NORMAL
        }
        pushToServer()
    }

    fun canUseBookMode(): Boolean {
        if (!FabricLoader.getInstance().isModLoaded("manosaba_item")) return false
        val player = Minecraft.getInstance().player ?: return false
        val item = BuiltInRegistries.ITEM.getValue(ResourceLocation.fromNamespaceAndPath("manosaba_item", "anan_book"))
        for (slot in 0 until player.inventory.containerSize) {
            if (player.inventory.getItem(slot).item === item) return true
        }
        return player.offhandItem.item === item
    }

    fun canUseAdminMode(): Boolean {
        return ChatLimit_MoiClient.adminAccess
    }

    /** Flips between 正常 and 心声. Only meaningful while the chat screen is open. */
    fun toggle() {
        mode = when (mode) {
            MODE_NORMAL -> MODE_HEART
            MODE_HEART -> if (canUseAdminMode()) MODE_ADMIN else if (canUseBookMode()) MODE_BOOK else MODE_NORMAL
            MODE_ADMIN -> if (canUseBookMode()) MODE_BOOK else MODE_NORMAL
            else -> MODE_NORMAL
        }
        pushToServer()
    }

    fun setSliderPosition(p: Double) {
        sliderPosition = p.coerceIn(0.0, maxAllowedPosition())
        pushToServer()
    }

    fun updateRestriction(maxPosition: Double, remainingMillis: Long) {
        restrictionMaxPosition = maxPosition.coerceIn(0.0, 1.0)
        restrictionEndsAt = System.currentTimeMillis() + remainingMillis.coerceAtLeast(0)
        if (sliderPosition > maxAllowedPosition()) setSliderPosition(maxAllowedPosition())
    }

    fun maxAllowedPosition(): Double =
        if (System.currentTimeMillis() < restrictionEndsAt) restrictionMaxPosition else 1.0

    fun isRestricted(): Boolean = maxAllowedPosition() < 1.0

    /** Fine-tune the slider by [delta] (fraction of the whole track). */
    fun nudge(delta: Double) {
        setSliderPosition(sliderPosition + delta)
    }

    /** Fine-tunes the effective audible range by an exact number of blocks. */
    fun nudgeRangeBlocks(delta: Double) {
        val baseRange = ChatLimit_MoiClient.clientConfig.chatLevelOneMax
        if (baseRange <= 0.0) return

        val targetMultiplier = ((clearRangeBlocks() + delta) / baseRange)
            .coerceIn(MIN_MULTIPLIER, maxMultiplier())
        val position = if (targetMultiplier <= 1.0) {
            targetMultiplier / 2.0
        } else {
            0.5 + (targetMultiplier - 1.0) / (maxMultiplier() - 1.0) * 0.5
        }
        setSliderPosition(position)
    }

    /**
     * Maps a slider position (0..1) to a range multiplier.
     *
     * Piecewise-linear so that 正常 (×1) is always exactly at the centre
     * regardless of the configured shout multiplier:
     *   - left half  [0, 0.5] → ×0 … ×1   (quiet → normal)
     *   - right half [0.5, 1] → ×1 … ×max (normal → shout)
     *
     * Example (with max = 2): the point ⅔ of the way from the left edge to the
     * centre is position ⅓, giving ×⅔ — matching "正常语音限制 9 格 → 6 格".
     */
    fun multiplierForPosition(p: Double): Double {
        if (p <= 0.5) return p * 2.0
        val max = maxMultiplier()
        return 1.0 + (max - 1.0) * ((p - 0.5) / 0.5)
    }

    /** The multiplier currently selected by the slider (floored for quiet). */
    fun currentMultiplier(): Double =
        multiplierForPosition(sliderPosition).coerceAtLeast(MIN_MULTIPLIER)

    /** Right-end (shout) multiplier, from the synced server config. */
    fun maxMultiplier(): Double {
        val m = ChatLimit_MoiClient.clientConfig.shoutRangeMultiplier
        return if (m > 1.0) m else 2.0
    }

    /** Effective "clear" audible range in blocks for the current slider position. */
    fun clearRangeBlocks(): Double =
        ChatLimit_MoiClient.clientConfig.chatLevelOneMax * currentMultiplier()

    fun pushToServer() {
        val player = Minecraft.getInstance().player ?: return
        ChatLimitClientNetwork.sendVolumeToServer(if (mode == MODE_BOOK) MODE_NORMAL else mode,
            currentMultiplier(), attitude, transformAttitude)
    }
}
