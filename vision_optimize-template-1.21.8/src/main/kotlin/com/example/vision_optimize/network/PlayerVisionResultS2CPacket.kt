package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * S2C: Player vision result sent to the caster.
 * @param success Whether the vision attempt succeeded.
 * @param resultType 0 = normal fail, 1 = discovered fail, 2 = success
 * @param targetName The target player's name.
 * @param tagText The magic tag text displayed on success (empty if fail).
 * @param visionLevel The player vision level used (for Level3 inventory display).
 * @param hotbarItemNames The target's hotbar item names and counts (slots 0-4, only populated for Level3 success).
 *   Format per slot: "empty" or "itemName|count"
 */
class PlayerVisionResultS2CPacket(
    val success: Boolean,
    val resultType: Int,
    val targetName: String,
    val tagText: String,
    val visionLevel: Int = 0,
    val hotbarItemNames: List<String> = emptyList()
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {
        buf.writeBoolean(success)
        buf.writeVarInt(resultType)
        buf.writeUtf(targetName)
        buf.writeUtf(tagText)
        buf.writeVarInt(visionLevel)
        buf.writeVarInt(hotbarItemNames.size)
        for (itemStr in hotbarItemNames) {
            buf.writeUtf(itemStr)
        }
    }

    companion object {
        val ID: CustomPacketPayload.Type<PlayerVisionResultS2CPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.PLAYER_VISION_RESULT)

        fun read(buf: FriendlyByteBuf): PlayerVisionResultS2CPacket {
            val success = buf.readBoolean()
            val resultType = buf.readVarInt()
            val targetName = buf.readUtf()
            val tagText = buf.readUtf()
            val visionLevel = buf.readVarInt()
            val count = buf.readVarInt()
            val items = (0 until count).map {
                buf.readUtf()
            }
            return PlayerVisionResultS2CPacket(success, resultType, targetName, tagText, visionLevel, items)
        }

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PlayerVisionResultS2CPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
