package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * S2C: Block vision search results.
 */
class BlockVisionResultEntry(
    val playerName: String,
    val timeSeconds: Long,
    val groupId: Int = -1
)

class BlockVisionResultS2CPacket(
    val level: Int,
    val entries: List<BlockVisionResultEntry>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(level)
        buf.writeVarInt(entries.size)
        entries.forEach {
            buf.writeUtf(it.playerName)
            buf.writeVarLong(it.timeSeconds)
            buf.writeVarInt(it.groupId)
        }
    }

    companion object {
        val ID: CustomPacketPayload.Type<BlockVisionResultS2CPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.BLOCK_VISION_RESULT)

        fun read(buf: FriendlyByteBuf): BlockVisionResultS2CPacket {
            val level = buf.readVarInt()
            val count = buf.readVarInt()
            val entries = (0 until count).map {
                BlockVisionResultEntry(
                    playerName = buf.readUtf(),
                    timeSeconds = buf.readVarLong(),
                    groupId = buf.readVarInt()
                )
            }
            return BlockVisionResultS2CPacket(level, entries)
        }

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, BlockVisionResultS2CPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
