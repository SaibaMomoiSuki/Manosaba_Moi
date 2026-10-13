package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * C2S: Player requests player vision on a target player.
 * @param targetName The game profile name of the target player.
 * @param wasInView Whether the caster was estimated to be in the target's FOV at charge start.
 * @param fromPKey Whether this request originated from the P-key selection screen (vs V-key charge).
 */
class PlayerVisionRequestC2SPacket(
    val targetName: String,
    val wasInView: Boolean,
    val fromPKey: Boolean = false
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {
        buf.writeUtf(targetName)
        buf.writeBoolean(wasInView)
        buf.writeBoolean(fromPKey)
    }

    companion object {
        val ID: CustomPacketPayload.Type<PlayerVisionRequestC2SPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.PLAYER_VISION_REQUEST)

        fun read(buf: FriendlyByteBuf): PlayerVisionRequestC2SPacket =
            PlayerVisionRequestC2SPacket(buf.readUtf(), buf.readBoolean(), buf.readBoolean())

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PlayerVisionRequestC2SPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
