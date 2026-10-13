package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * C2S: Player requests item vision for the item in the hovered slot.
 */
class ItemVisionRequestC2SPacket(val slotIndex: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<*> = ID

    fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(slotIndex)
    }

    companion object {
        val ID: CustomPacketPayload.Type<ItemVisionRequestC2SPacket> =
            CustomPacketPayload.Type(VisionOptimizeIds.ITEM_VISION_REQUEST)

        fun read(buf: FriendlyByteBuf): ItemVisionRequestC2SPacket =
            ItemVisionRequestC2SPacket(buf.readVarInt())

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ItemVisionRequestC2SPacket> =
            StreamCodec.ofMember({ packet, buf -> packet.write(buf) }, { buf -> read(buf) })
    }
}
