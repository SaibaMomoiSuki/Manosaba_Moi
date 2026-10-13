package com.example.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * Tracks the shared "心声" (heart voice) tags on the server side.
 *
 * Tags are a single global list that every player sees and edits together.
 * A player hears a 心声 message only when they themselves hold at least one
 * of these tags (as a scoreboard/entity tag); the sender needs no tag.
 *
 * The list is persisted to disk so it survives server restarts.
 */
object HeartTagTracker {

    private val tags = LinkedHashSet<String>()

    private val DATA_FILE: File = File(
        FabricLoader.getInstance().configDir.toFile(), "chatlimit_moi_heart_tags.json"
    )
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    /** Load persisted tags from disk (called once on server startup). */
    fun load() {
        if (!DATA_FILE.exists()) return
        try {
            val type = object : TypeToken<List<String>>() {}.type
            val raw: List<String> = GSON.fromJson(FileReader(DATA_FILE), type) ?: emptyList()
            tags.clear()
            tags.addAll(raw.filter { it.isNotBlank() })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Persist the current tags to disk. */
    fun save() {
        try {
            FileWriter(DATA_FILE).use { writer -> GSON.toJson(tags.toList(), writer) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setTags(newTags: List<String>) {
        tags.clear()
        tags.addAll(newTags.filter { it.isNotBlank() })
        save()
    }

    fun getTags(): Set<String> = tags
}
