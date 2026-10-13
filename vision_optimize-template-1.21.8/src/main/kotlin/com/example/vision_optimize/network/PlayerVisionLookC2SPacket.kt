package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * C2S: Client notifies server that the player opened the L2/L3 selection screen.
 * Server sends probabilistic warning messages to each target in the list.
 * @param targetNames List of player names currently in the caster's FOV.
 */
class PlayerVisionLookC2SPacket(
    val targetNames: List<String>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {
        buf.writeInt(targetNames.size)
        for (name in targetNames) {
            buf.writeUtf(name)
        }
    }

    companion object {
        val ID: CustomPacketPayload.Type<PlayerVisionLookC2SPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.PLAYER_VISION_LOOK)

        fun read(buf: FriendlyByteBuf): PlayerVisionLookC2SPacket {
            val count = buf.readInt()
            val names = (0 until count).map { buf.readUtf() }
            return PlayerVisionLookC2SPacket(names)
        }

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PlayerVisionLookC2SPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
