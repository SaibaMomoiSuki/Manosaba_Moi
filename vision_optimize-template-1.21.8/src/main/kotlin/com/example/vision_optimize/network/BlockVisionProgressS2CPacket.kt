package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * S2C: Progress update during block vision search (0.0 to 1.0).
 */
class BlockVisionProgressS2CPacket(val progress: Float) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {
        buf.writeFloat(progress)
    }

    companion object {
        val ID: CustomPacketPayload.Type<BlockVisionProgressS2CPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.BLOCK_VISION_PROGRESS)

        fun read(buf: FriendlyByteBuf): BlockVisionProgressS2CPacket =
            BlockVisionProgressS2CPacket(buf.readFloat())

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, BlockVisionProgressS2CPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
