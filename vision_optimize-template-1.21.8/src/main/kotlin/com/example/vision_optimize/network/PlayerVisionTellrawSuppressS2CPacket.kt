package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * S2C: Server instructs the client to suppress action-bar tellraw messages.
 * @param durationSeconds How long to suppress (seconds).
 */
class PlayerVisionTellrawSuppressS2CPacket(
    val durationSeconds: Int
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(durationSeconds)
    }

    companion object {
        val ID: CustomPacketPayload.Type<PlayerVisionTellrawSuppressS2CPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.PLAYER_VISION_TELLRAW_SUPPRESS)

        fun read(buf: FriendlyByteBuf): PlayerVisionTellrawSuppressS2CPacket =
            PlayerVisionTellrawSuppressS2CPacket(buf.readVarInt())

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PlayerVisionTellrawSuppressS2CPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
