package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * C2S: Config update from GUI settings screen.
 */
class ConfigUpdateC2SPacket(
    val settingKey: String,
    val value: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {
        buf.writeUtf(settingKey)
        buf.writeUtf(value)
    }

    companion object {
        val ID: CustomPacketPayload.Type<ConfigUpdateC2SPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.CONFIG_UPDATE)

        fun read(buf: FriendlyByteBuf): ConfigUpdateC2SPacket =
            ConfigUpdateC2SPacket(buf.readUtf(), buf.readUtf())

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ConfigUpdateC2SPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
