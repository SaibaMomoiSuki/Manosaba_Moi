package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * S2C: Server tells the client their effective player vision level.
 * Sent when a level change is detected (command or scoreboard).
 * @param visionLevel 1, 2, or 3
 */
class PlayerVisionLevelSyncS2CPacket(
    val visionLevel: Int
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {
        buf.writeInt(visionLevel)
    }

    companion object {
        val ID: CustomPacketPayload.Type<PlayerVisionLevelSyncS2CPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.PLAYER_VISION_LEVEL_SYNC)

        fun read(buf: FriendlyByteBuf): PlayerVisionLevelSyncS2CPacket =
            PlayerVisionLevelSyncS2CPacket(buf.readInt())

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PlayerVisionLevelSyncS2CPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
