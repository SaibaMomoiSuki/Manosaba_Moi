package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * S2C: Full config sync (sent as JSON string).
 */
class ConfigSyncS2CPacket(val configJson: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {
        buf.writeUtf(configJson)
    }

    companion object {
        val ID: CustomPacketPayload.Type<ConfigSyncS2CPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.CONFIG_SYNC)

        fun read(buf: FriendlyByteBuf): ConfigSyncS2CPacket =
            ConfigSyncS2CPacket(buf.readUtf())

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ConfigSyncS2CPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
