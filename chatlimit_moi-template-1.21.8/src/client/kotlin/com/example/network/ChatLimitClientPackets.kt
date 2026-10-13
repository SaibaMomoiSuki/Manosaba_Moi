package com.example.network

import com.example.config.ChatLimitConfig
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

object ChatLimitClientNetwork {

    fun registerAdminAccessReceiver(onReceive: (Boolean) -> Unit) {
        ClientPlayNetworking.registerGlobalReceiver(AdminAccessPayload.TYPE) { payload, context ->
            context.client().execute { onReceive(payload.allowed) }
        }
    }

    fun requestAdminAccess() {
        ClientPlayNetworking.send(RequestAdminAccessPayload())
    }

    fun registerClientReceiver(onReceive: (ChatLimitConfig) -> Unit) {
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE) { payload, context ->
            val config = ChatLimitConfig.fromJson(payload.configJson)
            context.client().execute {
                onReceive(config)
            }
        }
    }

    fun registerHeartTagsReceiver(onReceive: (List<String>) -> Unit) {
        ClientPlayNetworking.registerGlobalReceiver(HeartTagsSyncPayload.TYPE) { payload, context ->
            context.client().execute {
                onReceive(payload.tags)
            }
        }
    }

    fun registerRestrictionReceiver(onReceive: (Double, Long) -> Unit) {
        ClientPlayNetworking.registerGlobalReceiver(SpeechRestrictionPayload.TYPE) { payload, context ->
            context.client().execute { onReceive(payload.maxSliderPosition, payload.remainingMillis) }
        }
    }

    fun requestConfigFromServer() {
        ClientPlayNetworking.send(RequestConfigPayload())
    }

    fun requestHeartTags() {
        ClientPlayNetworking.send(RequestHeartTagsPayload())
    }

    fun sendVolumeToServer(mode: Int, multiplier: Double, attitude: Int = 0, transform: Boolean = false) {
        ClientPlayNetworking.send(SpeakVolumePayload(mode, multiplier, attitude, transform))
    }

    fun sendHeartTags(tags: List<String>) {
        ClientPlayNetworking.send(HeartTagsUpdatePayload(tags))
    }
}
