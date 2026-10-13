package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * S2C: Item vision holder history result.
 */
class ItemVisionHolderEntry(
    val playerName: String,
    val timestamp: Long
)

class ItemVisionResultS2CPacket(
    val level: Int,
    val holders: List<ItemVisionHolderEntry>,
    val deathInterfered: Boolean = false
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(level)
        buf.writeBoolean(deathInterfered)
        buf.writeVarInt(holders.size)
        holders.forEach {
            buf.writeUtf(it.playerName)
            buf.writeLong(it.timestamp)
        }
    }

    companion object {
        val ID: CustomPacketPayload.Type<ItemVisionResultS2CPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.ITEM_VISION_RESULT)

        fun read(buf: FriendlyByteBuf): ItemVisionResultS2CPacket {
            val level = buf.readVarInt()
            val deathInterfered = buf.readBoolean()
            val count = buf.readVarInt()
            val holders = (0 until count).map {
                ItemVisionHolderEntry(
                    playerName = buf.readUtf(),
                    timestamp = buf.readLong()
                )
            }
            return ItemVisionResultS2CPacket(level, holders, deathInterfered)
        }

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ItemVisionResultS2CPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
