package com.example.network

import com.example.config.ChatLimitConfig
import com.example.util.HeartTagTracker
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

// ==================== ConfigSyncPayload (S2C) ====================

class ConfigSyncPayload(val configJson: String) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("chatlimit_moi", "config_sync")

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ConfigSyncPayload> =
            object : StreamCodec<FriendlyByteBuf, ConfigSyncPayload> {
                override fun decode(buf: FriendlyByteBuf): ConfigSyncPayload =
                    ConfigSyncPayload(buf.readUtf())

                override fun encode(buf: FriendlyByteBuf, value: ConfigSyncPayload) {
                    buf.writeUtf(value.configJson)
                }
            }

        val TYPE: CustomPacketPayload.Type<ConfigSyncPayload> =
            CustomPacketPayload.Type(ID)

        fun registerType() {
            PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC)
        }
    }
}

// ==================== RequestConfigPayload (C2S) ====================

class RequestConfigPayload : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("chatlimit_moi", "request_config")

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RequestConfigPayload> =
            object : StreamCodec<FriendlyByteBuf, RequestConfigPayload> {
                override fun decode(buf: FriendlyByteBuf): RequestConfigPayload =
                    RequestConfigPayload()

                override fun encode(buf: FriendlyByteBuf, value: RequestConfigPayload) {}
            }

        val TYPE: CustomPacketPayload.Type<RequestConfigPayload> =
            CustomPacketPayload.Type(ID)

        fun registerType() {
            PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC)
        }
    }
}

// ==================== SpeakVolumePayload (C2S) ====================

/**
 * The player's speak mode (正常 / 心声) plus, for normal mode, the continuous
 * range multiplier selected by the volume slider (×1 = normal, <1 = quieter,
 * >1 = louder/shout).
 */
class SpeakVolumePayload(val mode: Int, val multiplier: Double, val attitude: Int = 0, val transform: Boolean = false) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("chatlimit_moi", "speak_volume")

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, SpeakVolumePayload> =
            object : StreamCodec<FriendlyByteBuf, SpeakVolumePayload> {
                override fun decode(buf: FriendlyByteBuf): SpeakVolumePayload =
                    SpeakVolumePayload(buf.readVarInt(), buf.readDouble(), buf.readVarInt(), buf.readBoolean())

                override fun encode(buf: FriendlyByteBuf, value: SpeakVolumePayload) {
                    buf.writeVarInt(value.mode)
                    buf.writeDouble(value.multiplier)
                    buf.writeVarInt(value.attitude)
                    buf.writeBoolean(value.transform)
                }
            }

        val TYPE: CustomPacketPayload.Type<SpeakVolumePayload> =
            CustomPacketPayload.Type(ID)

        fun registerType() {
            PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC)
        }
    }
}

class SpeechRestrictionPayload(val maxSliderPosition: Double, val remainingMillis: Long) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("chatlimit_moi", "speech_restriction")
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, SpeechRestrictionPayload> =
            object : StreamCodec<FriendlyByteBuf, SpeechRestrictionPayload> {
                override fun decode(buf: FriendlyByteBuf) = SpeechRestrictionPayload(buf.readDouble(), buf.readVarLong())
                override fun encode(buf: FriendlyByteBuf, value: SpeechRestrictionPayload) {
                    buf.writeDouble(value.maxSliderPosition)
                    buf.writeVarLong(value.remainingMillis)
                }
            }
        val TYPE = CustomPacketPayload.Type<SpeechRestrictionPayload>(ID)
        fun registerType() = PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC)
    }
}

// ==================== HeartTagsUpdatePayload (C2S) ====================

class HeartTagsUpdatePayload(val tags: List<String>) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("chatlimit_moi", "heart_tags_update")

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, HeartTagsUpdatePayload> =
            object : StreamCodec<FriendlyByteBuf, HeartTagsUpdatePayload> {
                override fun decode(buf: FriendlyByteBuf): HeartTagsUpdatePayload {
                    val size = buf.readVarInt()
                    val tags = ArrayList<String>(size)
                    repeat(size) { tags.add(buf.readUtf()) }
                    return HeartTagsUpdatePayload(tags)
                }

                override fun encode(buf: FriendlyByteBuf, value: HeartTagsUpdatePayload) {
                    buf.writeVarInt(value.tags.size)
                    value.tags.forEach { buf.writeUtf(it) }
                }
            }

        val TYPE: CustomPacketPayload.Type<HeartTagsUpdatePayload> =
            CustomPacketPayload.Type(ID)

        fun registerType() {
            PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC)
        }
    }
}

// ==================== RequestHeartTagsPayload (C2S) ====================

class RequestHeartTagsPayload : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("chatlimit_moi", "request_heart_tags")

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RequestHeartTagsPayload> =
            object : StreamCodec<FriendlyByteBuf, RequestHeartTagsPayload> {
                override fun decode(buf: FriendlyByteBuf): RequestHeartTagsPayload =
                    RequestHeartTagsPayload()

                override fun encode(buf: FriendlyByteBuf, value: RequestHeartTagsPayload) {}
            }

        val TYPE: CustomPacketPayload.Type<RequestHeartTagsPayload> =
            CustomPacketPayload.Type(ID)

        fun registerType() {
            PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC)
        }
    }
}

// ==================== HeartTagsSyncPayload (S2C) ====================

class HeartTagsSyncPayload(val tags: List<String>) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("chatlimit_moi", "heart_tags_sync")

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, HeartTagsSyncPayload> =
            object : StreamCodec<FriendlyByteBuf, HeartTagsSyncPayload> {
                override fun decode(buf: FriendlyByteBuf): HeartTagsSyncPayload {
                    val size = buf.readVarInt()
                    val tags = ArrayList<String>(size)
                    repeat(size) { tags.add(buf.readUtf()) }
                    return HeartTagsSyncPayload(tags)
                }

                override fun encode(buf: FriendlyByteBuf, value: HeartTagsSyncPayload) {
                    buf.writeVarInt(value.tags.size)
                    value.tags.forEach { buf.writeUtf(it) }
                }
            }

        val TYPE: CustomPacketPayload.Type<HeartTagsSyncPayload> =
            CustomPacketPayload.Type(ID)

        fun registerType() {
            PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC)
        }
    }
}

class AdminAccessPayload(val allowed: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("chatlimit_moi", "admin_access")
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AdminAccessPayload> =
            object : StreamCodec<FriendlyByteBuf, AdminAccessPayload> {
                override fun decode(buf: FriendlyByteBuf) = AdminAccessPayload(buf.readBoolean())
                override fun encode(buf: FriendlyByteBuf, value: AdminAccessPayload) {
                    buf.writeBoolean(value.allowed)
                }
            }
        val TYPE = CustomPacketPayload.Type<AdminAccessPayload>(ID)
        fun registerType() = PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC)
    }
}

class RequestAdminAccessPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath("chatlimit_moi", "request_admin_access")
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RequestAdminAccessPayload> =
            object : StreamCodec<FriendlyByteBuf, RequestAdminAccessPayload> {
                override fun decode(buf: FriendlyByteBuf) = RequestAdminAccessPayload()
                override fun encode(buf: FriendlyByteBuf, value: RequestAdminAccessPayload) {}
            }
        val TYPE = CustomPacketPayload.Type<RequestAdminAccessPayload>(ID)
        fun registerType() = PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC)
    }
}

// ==================== Common registration ====================

object ChatLimitNetwork {

    private var registered = false

    fun registerCommon() {
        if (registered) return
        registered = true
        ConfigSyncPayload.registerType()
        RequestConfigPayload.registerType()
        SpeakVolumePayload.registerType()
        SpeechRestrictionPayload.registerType()
        HeartTagsUpdatePayload.registerType()
        RequestHeartTagsPayload.registerType()
        HeartTagsSyncPayload.registerType()
        AdminAccessPayload.registerType()
        RequestAdminAccessPayload.registerType()
    }

    fun registerServerReceiver(onRequest: (ServerPlayer) -> Unit) {
        ServerPlayNetworking.registerGlobalReceiver(RequestConfigPayload.TYPE) { _payload, context ->
            context.server().execute {
                onRequest(context.player())
            }
        }
    }

    fun registerAdminAccessReceiver(onRequest: (ServerPlayer) -> Unit) {
        ServerPlayNetworking.registerGlobalReceiver(RequestAdminAccessPayload.TYPE) { _, context ->
            context.server().execute { onRequest(context.player()) }
        }
    }

    fun registerVolumeReceiver(onVolume: (ServerPlayer, Int, Double, Int, Boolean) -> Unit) {
        ServerPlayNetworking.registerGlobalReceiver(SpeakVolumePayload.TYPE) { payload, context ->
            context.server().execute {
                onVolume(context.player(), payload.mode, payload.multiplier, payload.attitude, payload.transform)
            }
        }
    }

    fun registerHeartTagsReceiver(onUpdate: (List<String>) -> Unit) {
        ServerPlayNetworking.registerGlobalReceiver(HeartTagsUpdatePayload.TYPE) { payload, context ->
            context.server().execute {
                onUpdate(payload.tags)
                // Broadcast the updated shared tag list to every player.
                val tags = HeartTagTracker.getTags().toList()
                context.server().playerList.players.forEach { player ->
                    sendHeartTagsToClient(player, tags)
                }
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(RequestHeartTagsPayload.TYPE) { _payload, context ->
            context.server().execute {
                sendHeartTagsToClient(context.player(), HeartTagTracker.getTags().toList())
            }
        }
    }

    fun sendConfigToClient(player: ServerPlayer, config: ChatLimitConfig) {
        ServerPlayNetworking.send(player, ConfigSyncPayload(config.toJson()))
    }

    fun sendHeartTagsToClient(player: ServerPlayer, tags: List<String>) {
        ServerPlayNetworking.send(player, HeartTagsSyncPayload(tags))
    }

    fun sendAdminAccessToClient(player: ServerPlayer, allowed: Boolean) {
        ServerPlayNetworking.send(player, AdminAccessPayload(allowed))
    }

    fun sendRestrictionToClient(player: ServerPlayer, maxSliderPosition: Double, remainingMillis: Long) {
        ServerPlayNetworking.send(player, SpeechRestrictionPayload(maxSliderPosition, remainingMillis))
    }
}
