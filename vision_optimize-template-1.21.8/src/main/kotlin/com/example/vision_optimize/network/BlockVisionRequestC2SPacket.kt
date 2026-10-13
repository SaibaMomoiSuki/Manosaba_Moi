package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * C2S: Player requests a block vision search.
 */
class BlockVisionRequestC2SPacket(
    val pos: BlockPos,
    val blockPosX: Int,
    val blockPosY: Int,
    val blockPosZ: Int
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {
        buf.writeBlockPos(pos)
        buf.writeVarInt(blockPosX)
        buf.writeVarInt(blockPosY)
        buf.writeVarInt(blockPosZ)
    }

    companion object {
        val ID: CustomPacketPayload.Type<BlockVisionRequestC2SPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.BLOCK_VISION_REQUEST)

        fun read(buf: FriendlyByteBuf): BlockVisionRequestC2SPacket =
            BlockVisionRequestC2SPacket(
                pos = buf.readBlockPos(),
                blockPosX = buf.readVarInt(),
                blockPosY = buf.readVarInt(),
                blockPosZ = buf.readVarInt()
            )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, BlockVisionRequestC2SPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
