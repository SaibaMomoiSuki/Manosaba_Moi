package com.example.audio

import com.google.gson.Gson
import com.google.gson.JsonObject
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Persists the player's voice-input choices across sessions: which microphone
 * (mixer) to record from, and which speech model to recognize with.
 *
 * Both are stored in a tiny JSON file under `config/chatlimit_moi/microphone.json`.
 * A null value means "decide automatically" — the system default microphone, and
 * the first model found in the model directory.
 */
object MicrophoneSettings {

    private const val KEY_MICROPHONE = "microphone"
    private const val KEY_MODEL = "model"

    private val file: Path =
        FabricLoader.getInstance().configDir.resolve("chatlimit_moi/microphone.json")

    private val gson = Gson()

    @Volatile
    private var microphone: String? = null

    @Volatile
    private var model: String? = null

    @Volatile
    private var loaded = false

    /** The saved microphone name, or null when the system default is selected. */
    fun getSelected(): String? {
        ensureLoaded()
        return microphone
    }

    /** Saves the microphone name (null = system default). */
    fun setSelected(name: String?) {
        ensureLoaded()
        microphone = name
        save()
    }

    /** The saved speech model id, or null when the model is picked automatically. */
    fun getSelectedModel(): String? {
        ensureLoaded()
        return model
    }

    /** Saves the speech model id (null = pick automatically). */
    fun setSelectedModel(id: String?) {
        ensureLoaded()
        model = id
        save()
    }

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            if (!Files.exists(file)) return
            val obj = gson.fromJson(Files.readString(file), JsonObject::class.java) ?: return
            microphone = obj.stringOrNull(KEY_MICROPHONE)
            model = obj.stringOrNull(KEY_MODEL)
        } catch (_: Throwable) {
            // Corrupt or unreadable config — fall back to automatic choices.
        }
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

    private fun save() {
        try {
            Files.createDirectories(file.parent)
            val obj = JsonObject()
            obj.addProperty(KEY_MICROPHONE, microphone ?: "")
            obj.addProperty(KEY_MODEL, model ?: "")
            Files.writeString(file, gson.toJson(obj))
        } catch (_: Throwable) {
            // Non-fatal — persistence is best-effort.
        }
    }
}
