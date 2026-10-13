package com.example.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

object ModConfig {
    private val logger = LoggerFactory.getLogger("mano_effect:config")
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path = FabricLoader.getInstance().configDir.resolve("mano_effect.json")

    @JvmField
    var fallDamageEnabled: Boolean = true

    fun load() {
        if (Files.exists(configPath)) {
            try {
                val json = Files.readString(configPath)
                val data = gson.fromJson(json, ConfigData::class.java)
                fallDamageEnabled = data.fallDamageEnabled
                logger.info("Config loaded: fallDamageEnabled={}", fallDamageEnabled)
            } catch (e: Exception) {
                logger.warn("Failed to load config, using defaults: {}", e.message)
            }
        } else {
            logger.info("No config file found, using defaults")
        }
    }

    fun save() {
        try {
            Files.createDirectories(configPath.parent)
            val data = ConfigData(fallDamageEnabled)
            Files.writeString(configPath, gson.toJson(data))
            logger.info("Config saved: fallDamageEnabled={}", fallDamageEnabled)
        } catch (e: Exception) {
            logger.warn("Failed to save config: {}", e.message)
        }
    }

    private data class ConfigData(
        val fallDamageEnabled: Boolean = true
    )
}