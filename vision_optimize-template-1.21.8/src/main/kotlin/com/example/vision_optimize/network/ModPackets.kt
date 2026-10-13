package com.example.vision_optimize.network

import com.example.vision_optimize.VisionOptimizeIds
import com.example.vision_optimize.config.AccessMode
import com.example.vision_optimize.config.ModConfig
import com.example.vision_optimize.config.ModConfigManager
import com.example.vision_optimize.config.VisionLevel
import com.example.vision_optimize.vision.block.BlockVisionHandler
import com.example.vision_optimize.vision.item.ItemVisionHandler
import com.example.vision_optimize.vision.player.PlayerVisionHandler
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

/**
 * Central registration hub for all network packets.
 */
object ModPackets {
    private val logger = LoggerFactory.getLogger("vision_optimize:network")

    fun registerServer() {
        // Register S2C packet types
        PayloadTypeRegistry.playS2C().register(ConfigSyncS2CPacket.ID, ConfigSyncS2CPacket.STREAM_CODEC)
        PayloadTypeRegistry.playS2C().register(OpStatusSyncS2CPacket.ID, OpStatusSyncS2CPacket.STREAM_CODEC)
        PayloadTypeRegistry.playS2C().register(BlockVisionResultS2CPacket.ID, BlockVisionResultS2CPacket.STREAM_CODEC)
        PayloadTypeRegistry.playS2C().register(BlockVisionProgressS2CPacket.ID, BlockVisionProgressS2CPacket.STREAM_CODEC)
        PayloadTypeRegistry.playS2C().register(ItemVisionResultS2CPacket.ID, ItemVisionResultS2CPacket.STREAM_CODEC)
        PayloadTypeRegistry.playS2C().register(PlayerVisionResultS2CPacket.ID, PlayerVisionResultS2CPacket.STREAM_CODEC)
        PayloadTypeRegistry.playS2C().register(PlayerVisionCooldownResetS2CPacket.ID, PlayerVisionCooldownResetS2CPacket.STREAM_CODEC)
        PayloadTypeRegistry.playS2C().register(PlayerVisionWarningS2CPacket.ID, PlayerVisionWarningS2CPacket.STREAM_CODEC)
        PayloadTypeRegistry.playS2C().register(PlayerVisionTellrawSuppressS2CPacket.ID, PlayerVisionTellrawSuppressS2CPacket.STREAM_CODEC)
        PayloadTypeRegistry.playS2C().register(PlayerVisionLevelSyncS2CPacket.ID, PlayerVisionLevelSyncS2CPacket.STREAM_CODEC)

        // Register C2S packet types
        PayloadTypeRegistry.playC2S().register(BlockVisionRequestC2SPacket.ID, BlockVisionRequestC2SPacket.STREAM_CODEC)
        PayloadTypeRegistry.playC2S().register(ItemVisionRequestC2SPacket.ID, ItemVisionRequestC2SPacket.STREAM_CODEC)
        PayloadTypeRegistry.playC2S().register(ConfigUpdateC2SPacket.ID, ConfigUpdateC2SPacket.STREAM_CODEC)
        PayloadTypeRegistry.playC2S().register(PlayerVisionRequestC2SPacket.ID, PlayerVisionRequestC2SPacket.STREAM_CODEC)
        PayloadTypeRegistry.playC2S().register(PlayerVisionChargeStartC2SPacket.ID, PlayerVisionChargeStartC2SPacket.STREAM_CODEC)
        PayloadTypeRegistry.playC2S().register(PlayerVisionChargeCancelC2SPacket.ID, PlayerVisionChargeCancelC2SPacket.STREAM_CODEC)
        PayloadTypeRegistry.playC2S().register(PlayerVisionLookC2SPacket.ID, PlayerVisionLookC2SPacket.STREAM_CODEC)

        // Register global receivers
        ServerPlayNetworking.registerGlobalReceiver(BlockVisionRequestC2SPacket.ID) { packet, context ->
            context.server().execute {
                BlockVisionHandler.handleRequest(context.player(), packet.pos)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ItemVisionRequestC2SPacket.ID) { packet, context ->
            context.server().execute {
                val player = context.player()
                val stack = if (packet.slotIndex < 0) {
                    player.mainHandItem
                } else if (packet.slotIndex < player.inventory.containerSize) {
                    player.inventory.getItem(packet.slotIndex)
                } else {
                    net.minecraft.world.item.ItemStack.EMPTY
                }
                ItemVisionHandler.handleRequest(player, stack)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(PlayerVisionRequestC2SPacket.ID) { packet, context ->
            context.server().execute {
                PlayerVisionHandler.handleRequest(context.player(), packet)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(PlayerVisionChargeStartC2SPacket.ID) { packet, context ->
            context.server().execute {
                PlayerVisionHandler.handleChargeStart(context.player(), packet.targetName, packet.wasInView)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(PlayerVisionChargeCancelC2SPacket.ID) { packet, context ->
            context.server().execute {
                PlayerVisionHandler.handleChargeCancel(context.player(), packet.targetName)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(PlayerVisionLookC2SPacket.ID) { packet, context ->
            context.server().execute {
                PlayerVisionHandler.handleLook(context.player(), packet.targetNames)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ConfigUpdateC2SPacket.ID) { packet, context ->
            context.server().execute {
                handleConfigUpdate(packet, context.player())
            }
        }
    }

    /**
     * Actually process the config update from the GUI.
     */
    private fun handleConfigUpdate(packet: ConfigUpdateC2SPacket, player: ServerPlayer) {
        // OP-only: reject config updates from non-operator players
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("你没有权限更改设置")
                    .withStyle { it.withColor(0xFF5555) },
                true
            )
            logger.warn("Non-OP player {} attempted config update: key={}", player.displayName!!.string, packet.settingKey)
            return
        }

        val key = packet.settingKey
        val value = packet.value
        logger.info("Config update from {}: key={}, value={}", player.displayName!!.string, key, value)

        when (key) {
            "disable" -> ModConfigManager.update { enabled = false }
            "enable" -> ModConfigManager.update { enabled = true }
            "manosaba_enable" -> ModConfigManager.update { manosabaEnabled = true }
            "manosaba_disable" -> ModConfigManager.update { manosabaEnabled = false }
            "AllMember" -> ModConfigManager.update { mode = AccessMode.ALL_MEMBERS }
            "TagMember" -> ModConfigManager.update { mode = AccessMode.TAG_MEMBER }

            // Tag management
            "open_tag_add" -> {
                if (value.isNotEmpty()) {
                    ModConfigManager.update {
                        accessTags.add(value)
                        tagVisionEnabled.add(value) // new tags are vision-enabled by default
                    }
                }
            }
            "open_tag_remove" -> {
                if (value.isNotEmpty()) {
                    ModConfigManager.update {
                        accessTags.remove(value)
                        tagVisionEnabled.remove(value)
                        // Also remove this tag from all player assignments
                        for ((_, tags) in playerTags) {
                            tags.remove(value)
                        }
                        // Clean up empty entries
                        playerTags.entries.removeAll { it.value.isEmpty() }
                    }
                }
            }
            // Tag vision toggle
            "tag_vision_enable" -> {
                if (value.isNotEmpty()) {
                    ModConfigManager.update { tagVisionEnabled.add(value) }
                    logger.info("Vision enabled for tag '{}'", value)
                }
            }
            "tag_vision_disable" -> {
                if (value.isNotEmpty()) {
                    ModConfigManager.update { tagVisionEnabled.remove(value) }
                    logger.info("Vision disabled for tag '{}'", value)
                }
            }

            "player_tag_add" -> {
                // Format: "playerName|tagName"
                val parts = value.split("|", limit = 2)
                if (parts.size == 2) {
                    val playerName = parts[0]
                    val tag = parts[1]
                    ModConfigManager.update {
                        playerTags.getOrPut(playerName) { mutableSetOf() }.add(tag)
                    }
                    // Also apply Minecraft scoreboard tag to the player if online
                    val targetPlayer = player.server?.playerList?.getPlayerByName(playerName)
                    if (targetPlayer != null) {
                        targetPlayer.addTag(tag)
                    }
                    logger.info("Added tag '{}' to player '{}'", tag, playerName)
                }
            }
            "player_tag_remove" -> {
                val parts = value.split("|", limit = 2)
                if (parts.size == 2) {
                    val playerName = parts[0]
                    val tag = parts[1]
                    ModConfigManager.update {
                        playerTags[playerName]?.remove(tag)
                        if (playerTags[playerName].isNullOrEmpty()) {
                            playerTags.remove(playerName)
                        }
                    }
                    // Also remove Minecraft scoreboard tag from the player if online
                    val targetPlayer = player.server?.playerList?.getPlayerByName(playerName)
                    if (targetPlayer != null) {
                        targetPlayer.removeTag(tag)
                    }
                    logger.info("Removed tag '{}' from player '{}'", tag, playerName)
                }
            }

            // Vision level overrides (from GUI tag settings)
            "player_set_block_level" -> {
                // Format: "playerName|VISION_LEVEL"
                val parts = value.split("|", limit = 2)
                if (parts.size == 2) {
                    val playerName = parts[0]
                    val levelStr = parts[1]
                    val level = try { VisionLevel.valueOf(levelStr) } catch (_: Exception) { null }
                    if (level != null) {
                        // Use the player name directly as the key
                        val targetPlayer = player.server?.playerList?.getPlayerByName(playerName)
                        val key = targetPlayer?.name?.string ?: playerName
                        ModConfigManager.update {
                            if (level == VisionLevel.NONE) {
                                playerBlockLevels.remove(key)
                            } else {
                                playerBlockLevels[key] = level
                            }
                        }
                        logger.info("Set block vision level of '{}' to {}", playerName, level)
                    }
                }
            }
            "player_set_item_level" -> {
                // Format: "playerName|VISION_LEVEL"
                val parts = value.split("|", limit = 2)
                if (parts.size == 2) {
                    val playerName = parts[0]
                    val levelStr = parts[1]
                    val level = try { VisionLevel.valueOf(levelStr) } catch (_: Exception) { null }
                    if (level != null) {
                        val targetPlayer = player.server?.playerList?.getPlayerByName(playerName)
                        val key = targetPlayer?.name?.string ?: playerName
                        ModConfigManager.update {
                            if (level == VisionLevel.NONE) {
                                playerItemLevels.remove(key)
                            } else {
                                playerItemLevels[key] = level
                            }
                        }
                        logger.info("Set item vision level of '{}' to {}", playerName, level)
                    }
                }
            }

            // Player vision numeric settings
            "pv_distance" -> value.toIntOrNull()?.let { ModConfigManager.update { playerVisionDistance = it } }
            "pv_range_x" -> value.toIntOrNull()?.let { ModConfigManager.update { playerVisionRangeX = it } }
            "pv_range_y" -> value.toIntOrNull()?.let { ModConfigManager.update { playerVisionRangeY = it } }
            "pv_range_z" -> value.toIntOrNull()?.let { ModConfigManager.update { playerVisionRangeZ = it } }
            "pv_reaction_time" -> value.toIntOrNull()?.let { ModConfigManager.update { playerVisionReactionTime = it } }
            "pv_fail_cooldown" -> value.toIntOrNull()?.let { ModConfigManager.update { playerVisionFailCooldown = it } }
            "pv_success_cooldown" -> value.toIntOrNull()?.let { ModConfigManager.update { playerVisionSuccessCooldown = it } }
            "pv_raid_time" -> value.toIntOrNull()?.let { ModConfigManager.update { playerVisionRaidTime = it } }
            "pv_sneak_time" -> value.toIntOrNull()?.let { ModConfigManager.update { playerVisionSneakTime = it } }
            "pv_discover_time" -> value.toIntOrNull()?.let { ModConfigManager.update { playerVisionDiscoverTime = it } }
            "pv_third_perspective" -> value.toIntOrNull()?.let { ModConfigManager.update { playerVisionThirdPerspectiveDistance = it } }

            // Player vision level overrides
            "player_set_player_vision_level" -> {
                // Format: "playerName|VISION_LEVEL"
                val parts = value.split("|", limit = 2)
                if (parts.size == 2) {
                    val playerName = parts[0]
                    val levelStr = parts[1]
                    val level = try { VisionLevel.valueOf(levelStr) } catch (_: Exception) { null }
                    if (level != null) {
                        val targetPlayer = player.server?.playerList?.getPlayerByName(playerName)
                        val key = targetPlayer?.name?.string ?: playerName
                        ModConfigManager.update {
                            if (level == VisionLevel.NONE) {
                                playerPlayerLevels.remove(key)
                            } else {
                                playerPlayerLevels[key] = level
                            }
                        }
                        logger.info("Set player vision level of '{}' to {}", playerName, level)
                    }
                }
            }

            // Player vision scoreboard settings (from GUI)
            "pv_scoreboard" -> {
                if (value.isNotEmpty()) {
                    ModConfigManager.update {
                        playerVisionL1Scoreboard = value
                        playerVisionL2Scoreboard = value
                        playerVisionL3Scoreboard = value
                    }
                }
            }
            "pv_l1_scoreboard" -> {
                if (value.isNotEmpty()) {
                    ModConfigManager.update { playerVisionL1Scoreboard = value; playerVisionL2Scoreboard = value; playerVisionL3Scoreboard = value }
                }
            }
            "pv_l1_min" -> value.toDoubleOrNull()?.let { ModConfigManager.update { playerVisionL1ScoreMin = it } }
            "pv_l1_max" -> value.toDoubleOrNull()?.let { ModConfigManager.update { playerVisionL1ScoreMax = it } }
            "pv_l2_min" -> value.toDoubleOrNull()?.let { ModConfigManager.update { playerVisionL2ScoreMin = it } }
            "pv_l2_max" -> value.toDoubleOrNull()?.let { ModConfigManager.update { playerVisionL2ScoreMax = it } }
            "pv_l3_min" -> value.toDoubleOrNull()?.let { ModConfigManager.update { playerVisionL3ScoreMin = it } }
            "pv_l3_max" -> value.toDoubleOrNull()?.let { ModConfigManager.update { playerVisionL3ScoreMax = it } }
            "pv_l1_reset" -> ModConfigManager.update {
                playerVisionL1Scoreboard = ""; playerVisionL1ScoreMin = null; playerVisionL1ScoreMax = null
            }
            "pv_l2_reset" -> ModConfigManager.update {
                playerVisionL2Scoreboard = ""; playerVisionL2ScoreMin = null; playerVisionL2ScoreMax = null
            }
            "pv_l3_reset" -> ModConfigManager.update {
                playerVisionL3Scoreboard = ""; playerVisionL3ScoreMin = null; playerVisionL3ScoreMax = null
            }

            // Player vision tag management
            "pv_tag_add" -> {
                // Format: "tagText|colorInt"
                val parts = value.split("|", limit = 2)
                if (parts.size == 2) {
                    val colorInt = parts[1].toIntOrNull() ?: 0xFFFFFFFF.toInt()
                    ModConfigManager.update { playerVisionTags[parts[0]] = colorInt }
                }
            }
            "pv_tag_remove" -> {
                if (value.isNotEmpty()) {
                    ModConfigManager.update {
                        playerVisionTags.remove(value)
                        // Also remove assignment from any player assigned to this tag
                        val assignedPlayer = playerVisionPlayerTags.entries.find { it.value == value }?.key
                        if (assignedPlayer != null) {
                            playerVisionPlayerTags.remove(assignedPlayer)
                        }
                    }
                }
            }
            "pv_player_tag_add" -> {
                // Format: "playerName|tagText"
                val parts = value.split("|", limit = 2)
                if (parts.size == 2) {
                    ModConfigManager.update { playerVisionPlayerTags[parts[0]] = parts[1] }
                }
            }
            "pv_player_tag_clear" -> {
                if (value.isNotEmpty()) {
                    ModConfigManager.update { playerVisionPlayerTags.remove(value) }
                }
            }
            "pv_tag_clear_all" -> {
                ModConfigManager.update {
                    playerVisionTags.clear()
                    playerVisionPlayerTags.clear()
                }
            }
            "pv_player_clear_all" -> {
                ModConfigManager.update { playerVisionPlayerTags.clear() }
            }

            // Scoreboard resets
            "nbt_level_one_reset" -> ModConfigManager.update {
                itemL1Scoreboard = ""; itemL1ScoreMin = null; itemL1ScoreMax = null
            }
            "nbt_level_two_reset" -> ModConfigManager.update {
                itemL2Scoreboard = ""; itemL2ScoreMin = null; itemL2ScoreMax = null
            }
            "block_l1_reset" -> ModConfigManager.update {
                blockL1Scoreboard = ""; blockL1ScoreMin = null; blockL1ScoreMax = null
            }
            "block_l2_reset" -> ModConfigManager.update {
                blockL2Scoreboard = ""; blockL2ScoreMin = null; blockL2ScoreMax = null
            }
            "block_l3_reset" -> ModConfigManager.update {
                blockL3Scoreboard = ""; blockL3ScoreMin = null; blockL3ScoreMax = null
            }

            // Block vision numeric settings (from GUI)
            "search_time" -> value.toIntOrNull()?.let { ModConfigManager.update { blockSearchTime = it } }
            "block_cooldown" -> value.toIntOrNull()?.let { ModConfigManager.update { blockCooldownTime = it } }
            "block_cooldown_reset" -> ModConfigManager.update { blockCooldownTime = 3 }
            "block_vision_percent" -> value.toIntOrNull()?.let { ModConfigManager.update { blockVisionPercent = it } }
            "block_vision_fail_add" -> value.toIntOrNull()?.let { ModConfigManager.update { blockVisionFailAdd = it } }
            "l1_after_time" -> value.toIntOrNull()?.let { ModConfigManager.update { blockL1AfterTime = it } }
            "l1_player_interval" -> value.toIntOrNull()?.let { ModConfigManager.update { blockL1PlayerInterval = it } }
            "l2_se1" -> value.toIntOrNull()?.let { ModConfigManager.update { blockL2SideEffect1 = it } }
            "l3_after_time" -> value.toIntOrNull()?.let { ModConfigManager.update { blockL3AfterTime = it } }

            // Scope X/Y/Z
            "l1_scope_x" -> value.toIntOrNull()?.let { ModConfigManager.update { blockL1ScopeX = it } }
            "l1_scope_y" -> value.toIntOrNull()?.let { ModConfigManager.update { blockL1ScopeY = it } }
            "l1_scope_z" -> value.toIntOrNull()?.let { ModConfigManager.update { blockL1ScopeZ = it } }
            "l2_scope_x" -> value.toIntOrNull()?.let { ModConfigManager.update { blockL2ScopeX = it } }
            "l2_scope_y" -> value.toIntOrNull()?.let { ModConfigManager.update { blockL2ScopeY = it } }
            "l2_scope_z" -> value.toIntOrNull()?.let { ModConfigManager.update { blockL2ScopeZ = it } }
            "l3_scope_x" -> value.toIntOrNull()?.let { ModConfigManager.update { blockL3ScopeX = it } }
            "l3_scope_y" -> value.toIntOrNull()?.let { ModConfigManager.update { blockL3ScopeY = it } }
            "l3_scope_z" -> value.toIntOrNull()?.let { ModConfigManager.update { blockL3ScopeZ = it } }

            // SideEffect min/max
            "l2_se2_min" -> value.toDoubleOrNull()?.let { ModConfigManager.update { blockL2SideEffect2Min = it } }
            "l2_se2_max" -> value.toDoubleOrNull()?.let { ModConfigManager.update { blockL2SideEffect2Max = it } }
            "l2_se3_min" -> value.toDoubleOrNull()?.let { ModConfigManager.update { blockL2SideEffect3Min = it } }
            "l2_se3_max" -> value.toDoubleOrNull()?.let { ModConfigManager.update { blockL2SideEffect3Max = it } }
            "l2_se4_min" -> value.toDoubleOrNull()?.let { ModConfigManager.update { blockL2SideEffect4Min = it } }
            "l2_se4_max" -> value.toDoubleOrNull()?.let { ModConfigManager.update { blockL2SideEffect4Max = it } }

            // Level scoreboard min/max
            "block_l1_min" -> value.toDoubleOrNull()?.let { ModConfigManager.update { blockL1ScoreMin = it } }
            "block_l1_max" -> value.toDoubleOrNull()?.let { ModConfigManager.update { blockL1ScoreMax = it } }
            "block_l2_min" -> value.toDoubleOrNull()?.let { ModConfigManager.update { blockL2ScoreMin = it } }
            "block_l2_max" -> value.toDoubleOrNull()?.let { ModConfigManager.update { blockL2ScoreMax = it } }
            "block_l3_min" -> value.toDoubleOrNull()?.let { ModConfigManager.update { blockL3ScoreMin = it } }
            "block_l3_max" -> value.toDoubleOrNull()?.let { ModConfigManager.update { blockL3ScoreMax = it } }

            // Item vision settings (from GUI)
            "item_l1_display" -> value.toIntOrNull()?.let { ModConfigManager.update { itemL1MaxDisplay = it } }
            "item_l2_time" -> value.toIntOrNull()?.let { ModConfigManager.update { itemL2VisionTime = it } }
            "item_death_false" -> value.toIntOrNull()?.let { ModConfigManager.update { itemDeathFalseTime = it * 60 } }
            "item_cooldown" -> value.toIntOrNull()?.let { ModConfigManager.update { itemCooldownTime = it } }
            "item_cooldown_reset" -> ModConfigManager.update { itemCooldownTime = 5 }
            "nbt_vision_percent" -> value.toIntOrNull()?.let { ModConfigManager.update { itemVisionPercent = it } }
            "nbt_vision_fail_add" -> value.toIntOrNull()?.let { ModConfigManager.update { itemVisionFailAdd = it } }
            "nbt_l1_min" -> value.toDoubleOrNull()?.let { ModConfigManager.update { itemL1ScoreMin = it } }
            "nbt_l1_max" -> value.toDoubleOrNull()?.let { ModConfigManager.update { itemL1ScoreMax = it } }
            "nbt_l2_min" -> value.toDoubleOrNull()?.let { ModConfigManager.update { itemL2ScoreMin = it } }
            "nbt_l2_max" -> value.toDoubleOrNull()?.let { ModConfigManager.update { itemL2ScoreMax = it } }
            "item_scoreboard" -> {
                // Scoreboard for item vision (first non-empty field sets the scoreboard name)
                if (value.isNotEmpty()) {
                    ModConfigManager.update { itemL1Scoreboard = value; itemL2Scoreboard = value }
                }
            }
            "block_scoreboard" -> {
                if (value.isNotEmpty()) {
                    ModConfigManager.update {
                        blockL1Scoreboard = value
                        blockL2Scoreboard = value
                        blockL3Scoreboard = value
                    }
                }
            }
        }

        // Validate SideEffect ordering after any config change (log only, GUI shows errors on-screen)
        val config = ModConfigManager.get()
        val seError = config.validateSideEffects()
        if (seError != null) {
            logger.warn("SideEffect validation warning: {}", seError)
        }
        val sbRangeError = config.validateScoreboardRanges()
        if (sbRangeError != null) {
            logger.warn("Scoreboard range warning: {}", sbRangeError)
        }
        val blockSbError = config.validateBlockScoreboardOverlap()
        if (blockSbError != null) {
            logger.warn("Block scoreboard overlap warning: {}", blockSbError)
        }
        val itemSbError = config.validateItemScoreboardOverlap()
        if (itemSbError != null) {
            logger.warn("Item scoreboard overlap warning: {}", itemSbError)
        }

        // Always re-sync config after any update
        val server = player.server ?: return
        syncConfigToAll(server)
    }

    /**
     * Sync the full config to a specific player.
     */
    fun syncConfigToPlayer(player: ServerPlayer) {
        val config = ModConfigManager.get()
        val json = ModConfig.toJson(config)
        val packet = ConfigSyncS2CPacket(json)
        ServerPlayNetworking.send(player, packet)
    }

    /**
     * Sync the full config to all online players.
     */
    fun syncConfigToAll(server: net.minecraft.server.MinecraftServer) {
        val config = ModConfigManager.get()
        val json = ModConfig.toJson(config)
        val packet = ConfigSyncS2CPacket(json)
        server.playerList.players.forEach { player ->
            ServerPlayNetworking.send(player, packet)
        }
    }
}
