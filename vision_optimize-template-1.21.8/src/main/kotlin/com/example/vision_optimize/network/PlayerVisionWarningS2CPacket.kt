package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * S2C: Server sends a player vision warning message to a client.
 * @param warningType 0 = target in-view warning, 1 = third-party raid, 2 = third-party sneak, 3 = discovered
 * @param casterName The name of the caster player.
 * @param targetName The name of the target player.
 */
class PlayerVisionWarningS2CPacket(
    val warningType: Int,
    val casterName: String,
    val targetName: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(warningType)
        buf.writeUtf(casterName)
        buf.writeUtf(targetName)
    }

    companion object {
        val ID: CustomPacketPayload.Type<PlayerVisionWarningS2CPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.PLAYER_VISION_WARNING)

        fun read(buf: FriendlyByteBuf): PlayerVisionWarningS2CPacket =
            PlayerVisionWarningS2CPacket(buf.readVarInt(), buf.readUtf(), buf.readUtf())

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PlayerVisionWarningS2CPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
