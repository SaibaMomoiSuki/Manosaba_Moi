package com.example.vision_optimize.tracking

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.core.Registry
import net.minecraft.core.UUIDUtil
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import java.util.*

/**
 * Custom DataComponentType for tracking items with a unique UUID.
 * This UUID persists with the item through copies, crafting, etc.,
 * and is used to look up holder history in ItemTracker.
 */
object ItemTrackingComponent {
    /** Nullable — may not be registered yet when other mods trigger ItemStack.copy() during their init. */
    private var TRACKING_ID: DataComponentType<UUID>? = null

    fun register() {
        TRACKING_ID = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            VisionOptimizeIds.TRACKING_ID,
            DataComponentType.builder<UUID>()
                .persistent(UUIDUtil.CODEC)
                // NOT network-synchronized: the tracking UUID is only used server-side
                // for ItemTracker holder-history lookups. The client never reads this
                // component, so syncing it over the network is pure overhead —
                // 16 bytes per item stack in every inventory/chunk/container packet.
                .build()
        )
    }

    /** Get the registered component type, or null if not yet registered. */
    private fun trackingId(): DataComponentType<UUID>? = TRACKING_ID

    /**
     * Get or create a tracking UUID for the given ItemStack.
     * If the stack already has a tracking ID, it is returned.
     * Otherwise, a new UUID is generated and set on the stack.
     * Safe to call before registration — returns a new UUID without setting the component.
     */
    fun getOrCreateTrackingId(stack: ItemStack): UUID {
        val type = trackingId() ?: return UUID.randomUUID()
        val existing = stack.get(type)
        if (existing != null) return existing
        val newId = UUID.randomUUID()
        stack.set(type, newId)
        return newId
    }

    /**
     * Check if the stack has a tracking ID.
     * Safe to call before registration — returns false.
     */
    fun hasTrackingId(stack: ItemStack): Boolean {
        val type = trackingId() ?: return false
        return stack.has(type)
    }

    /**
     * Get the tracking ID from a stack, or null if not present.
     * Safe to call before registration — returns null.
     */
    fun getTrackingId(stack: ItemStack): UUID? {
        val type = trackingId() ?: return null
        return stack.get(type)
    }

    /**
     * Copy the tracking ID from one stack to another.
     * Safe to call before registration — no-op.
     */
    fun copyTrackingId(from: ItemStack, to: ItemStack) {
        val type = trackingId() ?: return
        val id = from.get(type) ?: return
        to.set(type, id)
    }

    /**
     * Remove the tracking UUID from a stack.
     * Safe to call before registration — no-op.
     */
    fun removeTrackingId(stack: ItemStack) {
        val type = trackingId() ?: return
        stack.remove(type)
    }

    /**
     * Set a specific tracking UUID on a stack.
     * Safe to call before registration — no-op.
     */
    fun setTrackingId(stack: ItemStack, uuid: UUID) {
        val type = trackingId() ?: return
        stack.set(type, uuid)
    }

    /**
     * Merge holder history from one tracking UUID into another.
     * Records are appended without de-duplication, preserving the fact
     * that the same player may have held items from different source stacks.
     * The source history is NOT cleared — remainder items (if not all merged)
     * still need their tracking history intact.
     * Safe: if either UUID is null or source has no history, this is a no-op.
     */
    fun mergeHistory(fromUuid: UUID, toUuid: UUID) {
        val sourceHistory = ItemTracker.getHistory(fromUuid)
        if (sourceHistory.isEmpty()) return
        for (record in sourceHistory) {
            ItemTracker.addHistoryRecord(toUuid, record)
        }
    }

    /**
     * Remove tracking IDs from all items held by all online players.
     * This ensures that after clearing the ItemTracker history,
     * items won't immediately re-register with their old tracking UUIDs
     * on the next tick via ServerPlayerMixin.
     * Safe to call before registration — no-op.
     */
    fun clearTrackingIdsFromPlayerInventories(server: net.minecraft.server.MinecraftServer) {
        val type = trackingId() ?: return
        for (player in server.playerList.players) {
            val inv = player.inventory
            for (i in 0 until inv.containerSize) {
                val stack = inv.getItem(i)
                if (!stack.isEmpty) {
                    stack.remove(type)
                }
            }
        }
    }
}
