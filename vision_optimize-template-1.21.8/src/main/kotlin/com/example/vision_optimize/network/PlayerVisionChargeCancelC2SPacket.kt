package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * C2S: Client notifies server that a player vision charge was cancelled.
 * @param targetName The game profile name of the target player.
 */
class PlayerVisionChargeCancelC2SPacket(
    val targetName: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {
        buf.writeUtf(targetName)
    }

    companion object {
        val ID: CustomPacketPayload.Type<PlayerVisionChargeCancelC2SPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.PLAYER_VISION_CHARGE_CANCEL)

        fun read(buf: FriendlyByteBuf): PlayerVisionChargeCancelC2SPacket =
            PlayerVisionChargeCancelC2SPacket(buf.readUtf())

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PlayerVisionChargeCancelC2SPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
