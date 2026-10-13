package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * C2S: Client notifies server that a player vision charge started.
 * @param targetName The game profile name of the target player.
 * @param wasInView Whether the caster was estimated to be in the target's FOV at charge start.
 */
class PlayerVisionChargeStartC2SPacket(
    val targetName: String,
    val wasInView: Boolean
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {
        buf.writeUtf(targetName)
        buf.writeBoolean(wasInView)
    }

    companion object {
        val ID: CustomPacketPayload.Type<PlayerVisionChargeStartC2SPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.PLAYER_VISION_CHARGE_START)

        fun read(buf: FriendlyByteBuf): PlayerVisionChargeStartC2SPacket =
            PlayerVisionChargeStartC2SPacket(buf.readUtf(), buf.readBoolean())

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PlayerVisionChargeStartC2SPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
