package com.example.vision_optimize

import com.example.vision_optimize.attachment.ModAttachments
import com.example.vision_optimize.command.VisionCommand
import com.example.vision_optimize.config.ModConfigManager
import com.example.vision_optimize.event.ServerEventHandlers
import com.example.vision_optimize.network.ModPackets
import com.example.vision_optimize.tracking.ItemTrackingComponent
import net.fabricmc.api.ModInitializer

object Vision_optimize : ModInitializer {
    val logger = org.slf4j.LoggerFactory.getLogger("vision_optimize")

    override fun onInitialize() {
        logger.info("Vision Optimize mod initializing...")

        // Load configuration
        ModConfigManager.load()
        logger.info("Configuration loaded")

        // Register attachment types
        ModAttachments.register()
        logger.info("Attachments registered")

        // Register custom data component type for item tracking
        ItemTrackingComponent.register()
        logger.info("Item tracking component registered")

        // Register network packets
        ModPackets.registerServer()
        logger.info("Network packets registered")

        // Register server event handlers
        ServerEventHandlers.register()
        logger.info("Server event handlers registered")

        // Register commands
        VisionCommand.register()
        logger.info("Commands registered")

        logger.info("Vision Optimize mod initialized successfully!")
    }
}
