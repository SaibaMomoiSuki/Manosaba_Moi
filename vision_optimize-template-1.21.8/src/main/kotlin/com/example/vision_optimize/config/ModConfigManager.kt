package com.example.vision_optimize.config

import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe manager for loading, saving, and accessing the mod configuration.
 */
object ModConfigManager {
    private val logger = LoggerFactory.getLogger("vision_optimize:config")
    private val lock = ReentrantReadWriteLock()
    private var config: ModConfig = ModConfig()
    private var configPath: Path? = null

    val configDir: Path
        get() {
            val dir = FabricLoader.getInstance().configDir.resolve("vision_optimize")
            if (!Files.exists(dir)) Files.createDirectories(dir)
            return dir
        }

    val configFile: Path
        get() = configDir.resolve("config.json")

    /**
     * Load configuration from disk, or create default if not found.
     */
    fun load() {
        lock.write {
            configPath = configFile
            val file = configPath!!.toFile()
            if (file.exists()) {
                try {
                    val json = file.readText(Charsets.UTF_8)
                    config = ModConfig.fromJson(json)
                    // Migration: ensure all existing accessTags are vision-enabled by default
                    if (config.tagVisionEnabled.isEmpty() && config.accessTags.isNotEmpty()) {
                        config.tagVisionEnabled.addAll(config.accessTags)
                        logger.info("Migrated {} existing access tags to vision-enabled", config.accessTags.size)
                        saveInternal()
                    }
                    logger.info("Configuration loaded from {}", configPath)
                } catch (e: Exception) {
                    logger.error("Failed to load config, using defaults", e)
                    config = ModConfig()
                    saveInternal()
                }
            } else {
                logger.info("No config file found, creating default")
                config = ModConfig()
                saveInternal()
            }
        }
    }

    /**
     * Save configuration to disk.
     */
    fun save() {
        lock.write {
            saveInternal()
        }
    }

    private fun saveInternal() {
        val path = configPath ?: configFile
        try {
            val json = ModConfig.toJson(config)
            val tmpFile = path.resolveSibling("${path.fileName}.tmp")
            tmpFile.toFile().writeText(json, Charsets.UTF_8)
            Files.move(tmpFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            logger.debug("Configuration saved to {}", path)
        } catch (e: Exception) {
            logger.error("Failed to save config", e)
        }
    }

    /**
     * Get a thread-safe snapshot of the current configuration.
     */
    fun get(): ModConfig = lock.read { config }

    /**
     * Update the configuration with a mutation function, then save.
     */
    fun update(block: ModConfig.() -> Unit) {
        lock.write {
            block(config)
            saveInternal()
        }
    }

    /**
     * Update configuration without immediately saving (for batch operations).
     * Call save() after all updates.
     */
    fun updateNoSave(block: ModConfig.() -> Unit) {
        lock.write {
            block(config)
        }
    }

    /**
     * Check if the mod is currently enabled.
     */
    fun isEnabled(): Boolean = lock.read { config.enabled }

    /**
     * Check if a player is excluded from tracking.
     */
    fun isPlayerExcluded(playerName: String): Boolean = lock.read {
        config.excludedPlayers.contains(playerName)
    }

    /**
     * Validate the current SideEffect configuration.
     * Returns null if valid, or an error message string.
     */
    fun validateConfig(): String? = lock.read { config.validateSideEffects() }
}
