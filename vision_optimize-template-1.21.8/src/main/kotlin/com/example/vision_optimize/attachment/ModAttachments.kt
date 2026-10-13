package com.example.vision_optimize.attachment

import com.example.vision_optimize.VisionOptimizeIds
import com.example.vision_optimize.config.VisionLevel
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import java.util.function.Supplier

/**
 * Registers all custom AttachmentTypes for the mod.
 */
object ModAttachments {
    /**
     * Defaulted (non-persistent) attachment storing the player's path history.
     * NOT serialized to NBT — eliminates the serialization overhead on every player save.
     * Since path data has a max 5-minute lookback, losing data on server restart is acceptable.
     * Data survives dimension changes (entity-scoped, not world-scoped).
     * NOT copied on death — we handle death clearing manually based on gamerule.
     */
    lateinit var PLAYER_PATH: AttachmentType<PlayerPathData>
        private set

    /**
     * Defaulted attachment storing the player's block vision level.
     * Non-persistent — resets on login.
     */
    lateinit var BLOCK_LEVEL: AttachmentType<VisionLevel>
        private set

    /**
     * Defaulted attachment storing the player's item vision level.
     * Non-persistent — resets on login.
     */
    lateinit var ITEM_LEVEL: AttachmentType<VisionLevel>
        private set

    fun register() {
        PLAYER_PATH = AttachmentRegistry.createDefaulted(
            VisionOptimizeIds.PLAYER_PATH,
            Supplier { PlayerPathData() }
        )

        BLOCK_LEVEL = AttachmentRegistry.createDefaulted(
            VisionOptimizeIds.BLOCK_LEVEL,
            Supplier { VisionLevel.NONE }
        )

        ITEM_LEVEL = AttachmentRegistry.createDefaulted(
            VisionOptimizeIds.ITEM_LEVEL,
            Supplier { VisionLevel.NONE }
        )
    }
}
