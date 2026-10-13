package com.example.vision_optimize.client

import com.example.vision_optimize.config.ModConfig
import net.minecraft.client.Minecraft

/**
 * Client-side cached state for the vision_optimize mod.
 */
object ClientVisionState {
    var cachedConfig: ModConfig? = null

    // ===== OP Status =====
    /** Whether the local player has permission level >= 2 (is an operator). */
    var isOp: Boolean = false

    // ===== Silenced Debuff =====
    /** Cooldown ticks remaining when vision is blocked by the Silenced tag. 0 = not blocked. */
    var silencedCooldown: Int = 0

    // ===== Block Vision (client-driven charge) =====
    /** Bar is visible and progressing (0%→100%). */
    var blockVisionCharging: Boolean = false
    /** 0.0 → 1.0. Reset to 0 on completion or cancel. */
    var blockVisionProgress: Float = 0f
    /** Prevents re-trigger while key is still held after a completed charge. */
    var blockVisionLocked: Boolean = false
    /** Cooldown ticks remaining after a scan, counts down every tick regardless of activity. 0 = ready. */
    var blockVisionCooldown: Int = 0
    /** Target block position. */
    var blockVisionTargetPos: net.minecraft.core.BlockPos? = null

    fun resetBlockVision() {
        blockVisionCharging = false
        blockVisionProgress = 0f
        blockVisionLocked = false
        blockVisionTargetPos = null
    }

    // ===== Cooldown display tracking =====
    /** Last displayed cooldown second for block vision (to show message every second). */
    var blockVisionLastCooldownSec: Int = -1
    /** Last displayed cooldown second for item vision (to show message every second). */
    var itemVisionLastCooldownSec: Int = -1

    // ===== Player Vision (client-driven charge) =====
    /** Whether player vision is currently charging. */
    var playerVisionCharging: Boolean = false
    /** Charge progress 0.0 → 1.0. */
    var playerVisionProgress: Float = 0f
    /** Prevents re-trigger while key is held after a completed charge. */
    var playerVisionLocked: Boolean = false
    /** Name of the target player. */
    var playerVisionTargetName: String? = null
    /** Whether caster was estimated to be in target's FOV at charge start. */
    var playerVisionInView: Boolean = false
    /** Cooldown ticks remaining, 0 = ready. */
    var playerVisionCooldown: Int = 0
    /** Last displayed cooldown second for player vision. */
    var playerVisionLastCooldownSec: Int = -1

    fun resetPlayerVision() {
        playerVisionCharging = false
        playerVisionProgress = 0f
        playerVisionLocked = false
        playerVisionTargetName = null
        playerVisionInView = false
    }

    // ===== Tellraw Suppression =====
    /** Timestamp (System.currentTimeMillis()) until which action-bar tellraw is suppressed. */
    var tellrawSuppressUntil: Long = 0

    /** Whether action-bar tellraw messages should be suppressed. */
    fun isTellrawSuppressed(): Boolean {
        return System.currentTimeMillis() < tellrawSuppressUntil
    }

    /** Suppress action-bar tellraw for the given duration (seconds). */
    fun suppressTellraw(durationSeconds: Int) {
        tellrawSuppressUntil = System.currentTimeMillis() + (durationSeconds * 1000L)
    }

    // ===== Player Vision Level Cache =====
    /**
     * Cached player vision level from the last server response.
     * Used because client cannot determine scoreboard-based level locally.
     * Defaults to PLAYER_VISION_LEVEL_1 if player has permission.
     */
    var cachedPlayerVisionLevel: com.example.vision_optimize.config.VisionLevel? = null

    // ===== Item Vision =====
    /** Cooldown ticks remaining, 0 = ready. Set to 100 (5sec) after each use. */
    var itemVisionCooldown: Int = 0

    /** Returns true only if config is synced AND mod is enabled. */
    fun isModEnabled(): Boolean = cachedConfig?.enabled == true

    /**
     * Whether the local player has the "Silenced" tag.
     * Players with this tag cannot use any vision function.
     */
    fun isSilenced(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return player.tags.contains("Silenced")
    }

    /**
     * Whether the local player holds the "Vision" (幻视) magic tag while the
     * magical-girl witch trial (魔女审判 / manosaba) is enabled. While enabled,
     * the tag grants full vision permissions automatically.
     */
    fun hasVisionMagicFullAccess(): Boolean {
        val c = cachedConfig ?: return false
        if (!c.manosabaEnabled) return false
        val player = Minecraft.getInstance().player ?: return false
        return player.tags.any { it.equals("Vision", ignoreCase = true) }
    }

    /**
     * Whether the local player has block vision permission.
     * True if mod enabled AND (ALL_MEMBERS mode OR player has assigned level OR player has a vision-enabled access tag).
     * @param playerName Display name for level override lookup and tag-based access
     */
    fun hasBlockVisionPermission(playerName: String): Boolean {
        val c = cachedConfig ?: return false
        if (!c.enabled) return false
        if (hasVisionMagicFullAccess()) return true
        if (c.mode == com.example.vision_optimize.config.AccessMode.ALL_MEMBERS) return true
        // TAG_MEMBER mode: player must have an assigned level OR a vision-enabled access tag
        val level = c.playerBlockLevels[playerName]
        if (level != null && level != com.example.vision_optimize.config.VisionLevel.NONE) return true
        return c.hasVisionAccess(playerName)
    }

    /**
     * Whether the local player has block vision permission (by player name).
     */
    fun hasBlockVisionPermissionByName(playerName: String): Boolean {
        val c = cachedConfig ?: return false
        if (!c.enabled) return false
        if (c.mode == com.example.vision_optimize.config.AccessMode.ALL_MEMBERS) return true
        return c.hasVisionAccess(playerName)
    }

    /**
     * Whether the local player has player vision permission.
     * Uses hasPlayerVisionAccess for backward compatibility (checks playerItemLevels too).
     * In TAG_MEMBER mode: player must have an assigned level (item or player vision) or vision-enabled tag.
     */
    fun hasPlayerVisionPermission(): Boolean {
        val c = cachedConfig ?: return false
        if (!c.enabled) return false
        if (hasVisionMagicFullAccess()) return true
        if (c.mode == com.example.vision_optimize.config.AccessMode.ALL_MEMBERS) return true
        val player = Minecraft.getInstance().player ?: return false
        val name = player.displayName!!.string
        // Check player vision level assignment
        val pvLevel = c.playerPlayerLevels[name]
        if (pvLevel != null && pvLevel != com.example.vision_optimize.config.VisionLevel.NONE) return true
        // Check item level assignment (backward compatible)
        val itemLevel = c.playerItemLevels[name]
        if (itemLevel != null && itemLevel != com.example.vision_optimize.config.VisionLevel.NONE) return true
        // Check vision-enabled access tag
        return c.hasVisionAccess(name)
    }

    /**
     * Get the effective player vision level for the local player (client-side estimate).
     * Note: scoreboard-based level cannot be determined client-side, returns manual assignment or default.
     */
    fun getPlayerVisionLevel(): com.example.vision_optimize.config.VisionLevel? {
        // Manosaba (魔女审判) override: Vision magic tag grants full player vision.
        if (hasVisionMagicFullAccess()) return com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_3
        // Use cached level from server response first
        cachedPlayerVisionLevel?.let { return it }
        // Fall back to local estimation
        val c = cachedConfig ?: return null
        if (!c.enabled) return null
        val player = Minecraft.getInstance().player ?: return null
        val name = player.displayName!!.string
        val manualLevel = c.playerPlayerLevels[name]
        if (manualLevel != null && manualLevel != com.example.vision_optimize.config.VisionLevel.NONE) return manualLevel
        if (c.mode == com.example.vision_optimize.config.AccessMode.ALL_MEMBERS) {
            return com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_1
        }
        if (c.hasVisionAccess(name)) {
            return com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_1
        }
        return null
    }

    /**
     * Whether the local player has item vision permission.
     * In TAG_MEMBER mode: player must have an assigned item level (not NONE) or a vision-enabled access tag.
     */
    fun hasItemVisionPermission(): Boolean {
        val c = cachedConfig ?: return false
        if (!c.enabled) return false
        if (hasVisionMagicFullAccess()) return true
        if (c.mode == com.example.vision_optimize.config.AccessMode.ALL_MEMBERS) return true
        val player = Minecraft.getInstance().player ?: return false
        val name = player.displayName!!.string
        // Check if player has an assigned item level (not NONE)
        val itemLevel = c.playerItemLevels[name]
        if (itemLevel != null && itemLevel != com.example.vision_optimize.config.VisionLevel.NONE) return true
        // Check if player has a vision-enabled access tag
        return c.hasVisionAccess(name)
    }
}
