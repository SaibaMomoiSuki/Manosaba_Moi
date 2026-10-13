package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * S2C: Sync OP status (permission level >= 2) to the client.
 * Used to restrict the settings GUI to operators only.
 */
class OpStatusSyncS2CPacket(val isOp: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {
        buf.writeBoolean(isOp)
    }

    companion object {
        val ID: CustomPacketPayload.Type<OpStatusSyncS2CPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.OP_STATUS_SYNC)

        fun read(buf: FriendlyByteBuf): OpStatusSyncS2CPacket =
            OpStatusSyncS2CPacket(buf.readBoolean())

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, OpStatusSyncS2CPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
