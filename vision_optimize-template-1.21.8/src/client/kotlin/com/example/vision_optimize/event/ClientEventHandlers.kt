package com.example.vision_optimize.event

import com.example.vision_optimize.client.ClientVisionState
import com.example.vision_optimize.config.ModConfig
import com.example.vision_optimize.network.*
import com.example.vision_optimize.network.PlayerVisionWarningS2CPacket
import com.example.vision_optimize.network.PlayerVisionTellrawSuppressS2CPacket
import com.example.vision_optimize.util.TextBuilder
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component

/**
 * Registers all client-side packet receivers.
 */
object ClientEventHandlers {

    fun register() {
        // Receive OP status sync from server
        ClientPlayNetworking.registerGlobalReceiver(OpStatusSyncS2CPacket.ID) { packet, _ ->
            ClientVisionState.isOp = packet.isOp
        }

        // Receive config sync
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncS2CPacket.ID) { packet, _ ->
            try {
                ClientVisionState.cachedConfig = ModConfig.fromJson(packet.configJson)
            } catch (e: Exception) {
                com.example.vision_optimize.Vision_optimizeClient.logger.error("Failed to parse config sync", e)
            }
        }

        // Receive player vision level sync from server (keeps client in sync with scoreboard)
        ClientPlayNetworking.registerGlobalReceiver(com.example.vision_optimize.network.PlayerVisionLevelSyncS2CPacket.ID) { packet, _ ->
            ClientVisionState.cachedPlayerVisionLevel = when (packet.visionLevel) {
                3 -> com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_3
                2 -> com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_2
                else -> com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_1
            }
        }

        // Receive block vision results - display via chat
        ClientPlayNetworking.registerGlobalReceiver(BlockVisionResultS2CPacket.ID) { packet, _ ->
            ClientVisionState.resetBlockVision()
            val client = Minecraft.getInstance()
            val player = client.player ?: return@registerGlobalReceiver

            if (packet.entries.isEmpty()) {
                // No players passed through — show white text
                player.displayClientMessage(TextBuilder.blockNoResults(), false)
                return@registerGlobalReceiver
            }

            when (packet.level) {
                1 -> displayLevel1Results(player, packet)
                2 -> displayLevel2Results(player, packet)
                3 -> displayLevel3Results(player, packet)
            }
        }

        // Block vision progress is now client-driven (hold-to-charge).
        // The server may still send progress packets from legacy search,
        // but the client no longer uses them for the HUD bar.

        // Receive player vision cooldown reset
        ClientPlayNetworking.registerGlobalReceiver(PlayerVisionCooldownResetS2CPacket.ID) { _, _ ->
            val player = Minecraft.getInstance().player ?: return@registerGlobalReceiver
            ClientVisionState.playerVisionCooldown = 0
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("人物幻视冷却已重置")
                    .withStyle { it.withColor(0x55FF55) },
                true
            )
        }

        // Receive player vision result
        ClientPlayNetworking.registerGlobalReceiver(PlayerVisionResultS2CPacket.ID) { packet, _ ->
            val player = Minecraft.getInstance().player ?: return@registerGlobalReceiver

            if (packet.success) {
                // Success: start success cooldown (all levels)
                val cooldownMin = ClientVisionState.cachedConfig?.playerVisionSuccessCooldown ?: 12
                ClientVisionState.playerVisionCooldown = cooldownMin * 60 * 20

                // Cache the server-determined player vision level
                val levelFromServer = when (packet.visionLevel) {
                    3 -> com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_3
                    2 -> com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_2
                    else -> com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_1
                }
                ClientVisionState.cachedPlayerVisionLevel = levelFromServer

                // L2/L3: display target's hotbar items privately with hover tooltip
                if (packet.visionLevel >= 2 && packet.hotbarItemNames.isNotEmpty()) {
                    val slotRange = if (packet.visionLevel >= 3) "1~3" else "1"
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§e${packet.targetName} §7的物品栏 ($slotRange):")
                            .withStyle { it.withColor(0xFFFF55) },
                        false
                    )
                    for ((index, itemStr) in packet.hotbarItemNames.withIndex()) {
                        if (itemStr == "empty") {
                            player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("  §7[${index + 1}] §8空")
                                    .withStyle { it.withColor(0x888888) },
                                false
                            )
                        } else {
                            val parts = itemStr.split("|", limit = 2)
                            val registryKey = parts.getOrElse(0) { "" }
                            val count = parts.getOrElse(1) { "1" }.toIntOrNull() ?: 1
                            val item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                net.minecraft.resources.ResourceLocation.parse(registryKey)
                            ) ?: return@registerGlobalReceiver
                            @Suppress("USELESS_CAST")
                            val stack = net.minecraft.world.item.ItemStack(item as net.minecraft.world.level.ItemLike, count)
                            val hoverComponent = net.minecraft.network.chat.Component.literal("  §7[${index + 1}] ")
                                .append(stack.hoverName.copy().withStyle { it.withColor(0xFFFFFF) })
                                .append(net.minecraft.network.chat.Component.literal(" §7x${count}"))
                            player.displayClientMessage(hoverComponent, false)
                        }
                    }
                }
            } else {
                // Fail: start fail cooldown + suppress tellraw
                val cooldownMin = ClientVisionState.cachedConfig?.playerVisionFailCooldown ?: 6
                ClientVisionState.playerVisionCooldown = cooldownMin * 60 * 20

                if (packet.resultType == 1) {
                    // Discovered fail — messages already sent by warning packet
                } else {
                    // Normal fail
                    if (!ClientVisionState.isTellrawSuppressed()) {
                        player.displayClientMessage(TextBuilder.playerVisionFail(), true)
                    }
                    ClientVisionState.suppressTellraw(3)
                }
            }
        }

        // Receive player vision warning
        ClientPlayNetworking.registerGlobalReceiver(PlayerVisionWarningS2CPacket.ID) { packet, _ ->
            val player = Minecraft.getInstance().player ?: return@registerGlobalReceiver
            val localPlayerName = player.displayName!!.string
            val config = ClientVisionState.cachedConfig

            when (packet.warningType) {
                0 -> {
                    // Target in-view warning — only show to the target
                    if (localPlayerName == packet.targetName) {
                        player.displayClientMessage(
                            TextBuilder.playerVisionInViewWarning(TextBuilder.manosabaColoredName(packet.casterName, config)), true
                        )
                    }
                }
                1 -> {
                    // Third-party raid — show yellow chat
                    if (localPlayerName != packet.casterName && localPlayerName != packet.targetName) {
                        player.displayClientMessage(
                            TextBuilder.playerVisionThirdPartyRaid(TextBuilder.manosabaColoredName(packet.casterName, config), TextBuilder.manosabaColoredName(packet.targetName, config)), false
                        )
                    }
                }
                2 -> {
                    // Third-party sneak — show blue chat
                    if (localPlayerName != packet.casterName && localPlayerName != packet.targetName) {
                        player.displayClientMessage(
                            TextBuilder.playerVisionThirdPartySneak(TextBuilder.manosabaColoredName(packet.casterName, config), TextBuilder.manosabaColoredName(packet.targetName, config)), false
                        )
                    }
                }
                3 -> {
                    // Discovered
                    if (localPlayerName == packet.targetName) {
                        player.displayClientMessage(
                            TextBuilder.playerVisionDiscovered(TextBuilder.manosabaColoredName(packet.casterName, config)), true
                        )
                    } else if (localPlayerName == packet.casterName) {
                        player.displayClientMessage(
                            TextBuilder.playerVisionDiscoveredCaster(TextBuilder.manosabaColoredName(packet.targetName, config)), true
                        )
                    }
                }
            }
        }

        // Receive tellraw suppression
        ClientPlayNetworking.registerGlobalReceiver(PlayerVisionTellrawSuppressS2CPacket.ID) { packet, _ ->
            ClientVisionState.suppressTellraw(packet.durationSeconds)
        }

        // Receive item vision results - display via chat
        ClientPlayNetworking.registerGlobalReceiver(ItemVisionResultS2CPacket.ID) { packet, _ ->
            val player = Minecraft.getInstance().player ?: return@registerGlobalReceiver

            if (packet.deathInterfered) {
                player.displayClientMessage(TextBuilder.itemDeathInterference(), false)
                return@registerGlobalReceiver
            }

            if (packet.holders.isEmpty()) {
                player.displayClientMessage(TextBuilder.itemNoPrevious(), false)
                return@registerGlobalReceiver
            }

            player.displayClientMessage(TextBuilder.itemVisionHeader(), false)
            val config = ClientVisionState.cachedConfig
            for (holder in packet.holders) {
                player.displayClientMessage(
                    TextBuilder.manosabaColoredName(holder.playerName, config), false
                )
            }
        }
    }

    private fun displayLevel1Results(player: net.minecraft.client.player.LocalPlayer, packet: BlockVisionResultS2CPacket) {
        val groups = packet.entries.groupBy { it.groupId }
        val config = ClientVisionState.cachedConfig

        for ((groupId, members) in groups) {
            val names = members.map { it.playerName }
            val message = if (groupId < 0 || members.size == 1) {
                // Single player
                TextBuilder.blockL1Single(names.first(), config)
            } else {
                // Group
                TextBuilder.blockL1Group(names, config)
            }
            player.displayClientMessage(message, false)
        }
    }

    private fun displayLevel2Results(player: net.minecraft.client.player.LocalPlayer, packet: BlockVisionResultS2CPacket) {
        val groups = packet.entries.groupBy { it.groupId }
        val config = ClientVisionState.cachedConfig

        for ((category, members) in groups.toSortedMap()) {
            val names = members.map { it.playerName }
            val message = when (category) {
                0 -> TextBuilder.blockL2NearMe(names, config)
                1 -> TextBuilder.blockL2Recently(names, config)
                2 -> TextBuilder.blockL2Lately(names, config)
                3 -> TextBuilder.blockL2Once(names, config)
                else -> null
            }
            message?.let { player.displayClientMessage(it, false) }
        }
    }

    private fun displayLevel3Results(player: LocalPlayer, packet: BlockVisionResultS2CPacket) {
        val config = ClientVisionState.cachedConfig
        for (entry in packet.entries) {
            player.displayClientMessage(
                TextBuilder.blockL3Entry(entry.playerName, entry.timeSeconds, config), false
            )
        }
    }
}
