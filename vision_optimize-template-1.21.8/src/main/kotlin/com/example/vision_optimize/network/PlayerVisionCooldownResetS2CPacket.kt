package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * S2C: Sent to reset player vision cooldown on the client.
 * Empty payload — presence of the packet triggers the reset.
 */
class PlayerVisionCooldownResetS2CPacket : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {}

    companion object {
        val ID: CustomPacketPayload.Type<PlayerVisionCooldownResetS2CPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.PLAYER_VISION_COOLDOWN_RESET)

        fun read(buf: FriendlyByteBuf): PlayerVisionCooldownResetS2CPacket =
            PlayerVisionCooldownResetS2CPacket()

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PlayerVisionCooldownResetS2CPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
