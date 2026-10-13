package com.example.vision_optimize.command

import com.example.vision_optimize.config.AccessMode
import com.example.vision_optimize.config.ModConfigManager
import com.example.vision_optimize.config.VisionLevel
import com.example.vision_optimize.tracking.ItemTracker
import com.example.vision_optimize.tracking.ItemTrackingComponent
import com.example.vision_optimize.tracking.PlayerPathTracker
import com.example.vision_optimize.vision.player.PlayerVisionHandler
import com.example.vision_optimize.util.TextBuilder
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.*
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import com.example.vision_optimize.network.ModPackets
import net.minecraft.ChatFormatting

/**
 * Root command registration for /vision_optimize
 */
object VisionCommand {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, registry, environment ->
            dispatcher.register(
                Commands.literal("vision_optimize")
                    .then(blockLevelOneScope())
                    .then(blockSearch())
                    .then(blockLevelOneAfterTime())
                    .then(blockLevelOnePlayerInterval())
                    .then(blockLevelTwoScope())
                    .then(blockLevelTwoSideEffectOne())
                    .then(blockLevelTwoSideEffectTwo())
                    .then(blockLevelTwoSideEffectThree())
                    .then(blockLevelTwoSideEffectFour())
                    .then(blockLevelThreeScope())
                    .then(blockLevelThreeAfterTime())
                    .then(blockScoreboard("block_level_one"))
                    .then(blockScoreboard("block_level_two"))
                    .then(blockScoreboard("block_level_three"))
                    .then(blockScoreboardReset("block_level_one"))
                    .then(blockScoreboardReset("block_level_two"))
                    .then(blockScoreboardReset("block_level_three"))
                    .then(gamerulePathPlayerDeath())
                    .then(gameruleVisionPlayerDeath())
                    .then(pathClear())
                    .then(visionClear())
                    .then(excludePlayerAdd())
                    .then(excludePlayerRemove())
                    .then(nbtLevelOne())
                    .then(nbtLevelTwoVisionTime())
                    .then(nbtLevelPlayerDeathFalse())
                    .then(nbtScoreboard("nbt_level_one"))
                    .then(nbtScoreboard("nbt_level_two"))
                    .then(nbtScoreboardReset("nbt_level_one"))
                    .then(nbtScoreboardReset("nbt_level_two"))
                    .then(nbtCooldown())
                    .then(blockCooldown())
                    .then(nbtCoolDownReset())
                    .then(blockCoolDownReset())
                    .then(playerVisionSubCommand())
                    .then(playerVisionScoreboard("player_vision_level_one"))
                    .then(playerVisionScoreboard("player_vision_level_two"))
                    .then(playerVisionScoreboardReset("player_vision_level_one"))
                    .then(playerVisionScoreboardReset("player_vision_level_two"))
                    .then(playerVisionScoreboard("player_vision_level_three"))
                    .then(playerVisionScoreboardReset("player_vision_level_three"))
                    .then(blockVisionPercentSet())
                    .then(blockVisionFailAdd())
                    .then(nbtVisionPercentSet())
                    .then(nbtVisionFailAdd())
                    .then(tagAdd())
                    .then(tagRemove())
                    .then(tagList())
                    .then(openTagAdd())
                    .then(openTagRemove())
                    .then(openTagList())
                    .then(openTagVisionEnable())
                    .then(openTagVisionDisable())
                    .then(setBlockLevelOne())
                    .then(setBlockLevelTwo())
                    .then(setBlockLevelThree())
                    .then(setNbtLevelOne())
                    .then(setNbtLevelTwo())
                    .then(setPlayerVisionLevelOne())
                    .then(setPlayerVisionLevelTwo())
                    .then(setPlayerVisionLevelThree())
                    .then(disableCmd())
                    .then(enableCmd())
                    .then(allMemberCmd())
                    .then(tagMemberCmd())
                    .then(manosabaCmd())
                    .then(helpCmd())
            )
        }
    }

    // ===== Helper Functions =====

    private fun feedback(context: CommandContext<CommandSourceStack>, message: String, color: ChatFormatting = ChatFormatting.GREEN) {
        context.source.sendSuccess({ Component.literal(message).withStyle(color) }, false)
    }

    private fun errorFeedback(context: CommandContext<CommandSourceStack>, message: String) {
        context.source.sendFailure(Component.literal(message).withStyle(ChatFormatting.RED))
    }

    private fun requireOpLevel(): (CommandSourceStack) -> Boolean = { it.hasPermission(2) }

    /**
     * Check if a player has general vision access (any of the three vision types).
     * Returns false if: mod disabled, player excluded, TAG_MEMBER without vision access tag.
     */
    private fun hasGeneralVisionAccess(player: net.minecraft.server.level.ServerPlayer): Boolean {
        val config = ModConfigManager.get()
        if (!config.enabled) return false
        val name = player.displayName!!.string
        if (config.excludedPlayers.contains(name)) return false
        if (config.mode == com.example.vision_optimize.config.AccessMode.ALL_MEMBERS) return true
        return config.hasVisionAccess(name)
    }

    /** Parse a hex color string (#RRGGBB) to an ARGB Int. Returns null on invalid input. */
    private fun parseHexColorArg(hex: String): Int? {
        val cleaned = hex.removePrefix("#").trim()
        if (cleaned.length != 6) return null
        return try {
            (0xFF000000.toInt() or Integer.parseInt(cleaned, 16))
        } catch (_: Exception) {
            null
        }
    }

    /** Update config AND sync to all clients. */
    private fun updateAndSync(ctx: CommandContext<CommandSourceStack>, block: com.example.vision_optimize.config.ModConfig.() -> Unit) {
        com.example.vision_optimize.config.ModConfigManager.update(block)
        com.example.vision_optimize.network.ModPackets.syncConfigToAll(ctx.source.server)
    }

    // ===== Block Vision Scope Commands =====

    private fun blockLevelOneScope() = Commands.literal("block_level_one").then(
        Commands.literal("scope").requires(requireOpLevel()).then(
            Commands.argument("x", IntegerArgumentType.integer(1, 100)).then(
                Commands.argument("y", IntegerArgumentType.integer(1, 100)).then(
                    Commands.argument("z", IntegerArgumentType.integer(1, 100))
                        .executes { ctx ->
                            val x = IntegerArgumentType.getInteger(ctx, "x")
                            val y = IntegerArgumentType.getInteger(ctx, "y")
                            val z = IntegerArgumentType.getInteger(ctx, "z")
                            ModConfigManager.update {
                                blockL1ScopeX = x; blockL1ScopeY = y; blockL1ScopeZ = z
                            }
                            feedback(ctx, "方块幻视一阶段范围已设置为 $x x $y x $z")
                            1
                        }
                )
            )
        )
    )

    private fun blockSearch() = Commands.literal("block").then(
        Commands.literal("search").requires(requireOpLevel()).then(
            Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                .executes { ctx ->
                    val sec = IntegerArgumentType.getInteger(ctx, "seconds")
                    updateAndSync(ctx) { blockSearchTime = sec }
                    feedback(ctx, "方块幻视搜索时间已设置为 $sec 秒")
                    1
                }
        )
    )

    private fun blockLevelOneAfterTime() = Commands.literal("block_level_one").then(
        Commands.literal("after_time").requires(requireOpLevel()).then(
            Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                .executes { ctx ->
                    val sec = IntegerArgumentType.getInteger(ctx, "seconds")
                    ModConfigManager.update { blockL1AfterTime = sec }
                    feedback(ctx, "方块幻视一阶段判定时间已设置为 $sec 秒")
                    1
                }
        )
    )

    private fun blockLevelOnePlayerInterval() = Commands.literal("block_level_one").then(
        Commands.literal("player_interval").requires(requireOpLevel()).then(
            Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                .executes { ctx ->
                    val sec = IntegerArgumentType.getInteger(ctx, "seconds")
                    ModConfigManager.update { blockL1PlayerInterval = sec }
                    feedback(ctx, "方块幻视一阶段在一起判定时间已设置为 $sec 秒")
                    1
                }
        )
    )

    private fun blockLevelTwoScope() = Commands.literal("block_level_two").then(
        Commands.literal("scope").requires(requireOpLevel()).then(
            Commands.argument("x", IntegerArgumentType.integer(1, 100)).then(
                Commands.argument("y", IntegerArgumentType.integer(1, 100)).then(
                    Commands.argument("z", IntegerArgumentType.integer(1, 100))
                        .executes { ctx ->
                            val x = IntegerArgumentType.getInteger(ctx, "x")
                            val y = IntegerArgumentType.getInteger(ctx, "y")
                            val z = IntegerArgumentType.getInteger(ctx, "z")
                            ModConfigManager.update { blockL2ScopeX = x; blockL2ScopeY = y; blockL2ScopeZ = z }
                            feedback(ctx, "方块幻视二阶段范围已设置为 $x x $y x $z")
                            1
                        }
                )
            )
        )
    )

    private fun blockLevelTwoSideEffectOne() = Commands.literal("block_level_two").then(
        Commands.literal("SideEffect_one").requires(requireOpLevel()).then(
            Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                .executes { ctx ->
                    val sec = IntegerArgumentType.getInteger(ctx, "seconds")
                    ModConfigManager.update { blockL2SideEffect1 = sec }
                    feedback(ctx, "方块幻视二阶段「在我附近」判定时间已设置为 $sec 秒")
                    1
                }
        )
    )

    private fun blockLevelTwoSideEffectTwo() = Commands.literal("block_level_two").then(
        Commands.literal("SideEffect_two").requires(requireOpLevel()).then(
            Commands.argument("min", StringArgumentType.word()).then(
                Commands.argument("max", StringArgumentType.word())
                    .executes { ctx ->
                        setSideEffect(ctx, "two", "不久前")
                    }
            )
        )
    )

    private fun blockLevelTwoSideEffectThree() = Commands.literal("block_level_two").then(
        Commands.literal("SideEffect_three").requires(requireOpLevel()).then(
            Commands.argument("min", StringArgumentType.word()).then(
                Commands.argument("max", StringArgumentType.word())
                    .executes { ctx ->
                        setSideEffect(ctx, "three", "最近")
                    }
            )
        )
    )

    private fun blockLevelTwoSideEffectFour() = Commands.literal("block_level_two").then(
        Commands.literal("SideEffect_four").requires(requireOpLevel()).then(
            Commands.argument("min", StringArgumentType.word()).then(
                Commands.argument("max", StringArgumentType.word())
                    .executes { ctx ->
                        setSideEffect(ctx, "four", "曾近")
                    }
            )
        )
    )

    private fun setSideEffect(ctx: CommandContext<CommandSourceStack>, effect: String, label: String): Int {
        val minStr = StringArgumentType.getString(ctx, "min")
        val maxStr = StringArgumentType.getString(ctx, "max")
        val min = if (minStr == "infinity") null else minStr.toDoubleOrNull()
        val max = if (maxStr == "infinity") null else maxStr.toDoubleOrNull()
        if (min == null && minStr != "infinity") { errorFeedback(ctx, "最小值格式无效"); return 0 }
        if (max == null && maxStr != "infinity") { errorFeedback(ctx, "最大值格式无效"); return 0 }

        ModConfigManager.update {
            when (effect) {
                "two" -> { blockL2SideEffect2Min = min; blockL2SideEffect2Max = max }
                "three" -> { blockL2SideEffect3Min = min; blockL2SideEffect3Max = max }
                "four" -> { blockL2SideEffect4Min = min; blockL2SideEffect4Max = max }
            }
        }
        val err = ModConfigManager.validateConfig()
        if (err != null) {
            errorFeedback(ctx, "配置验证失败: $err")
            return 0
        }
        feedback(ctx, "方块幻视二阶段「${label}经过这里」判定区间已设置为 $minStr ~ $maxStr")
        return 1
    }

    private fun blockLevelThreeScope() = Commands.literal("block_level_three").then(
        Commands.literal("scope").requires(requireOpLevel()).then(
            Commands.argument("x", IntegerArgumentType.integer(1, 100)).then(
                Commands.argument("y", IntegerArgumentType.integer(1, 100)).then(
                    Commands.argument("z", IntegerArgumentType.integer(1, 100))
                        .executes { ctx ->
                            val x = IntegerArgumentType.getInteger(ctx, "x")
                            val y = IntegerArgumentType.getInteger(ctx, "y")
                            val z = IntegerArgumentType.getInteger(ctx, "z")
                            ModConfigManager.update { blockL3ScopeX = x; blockL3ScopeY = y; blockL3ScopeZ = z }
                            feedback(ctx, "方块幻视三阶段范围已设置为 $x x $y x $z")
                            1
                        }
                )
            )
        )
    )

    private fun blockLevelThreeAfterTime() = Commands.literal("block_level_three").then(
        Commands.literal("after_time").requires(requireOpLevel()).then(
            Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                .executes { ctx ->
                    val sec = IntegerArgumentType.getInteger(ctx, "seconds")
                    ModConfigManager.update { blockL3AfterTime = sec }
                    feedback(ctx, "方块幻视三阶段判定时间范围已设置为 $sec 秒")
                    1
                }
        )
    )

    // ===== Scoreboard Commands =====

    private fun blockScoreboard(level: String) = Commands.literal(level).requires(requireOpLevel()).then(
        Commands.argument("objective", StringArgumentType.word()).then(
            Commands.argument("min", StringArgumentType.word()).then(
                Commands.argument("max", StringArgumentType.word())
                    .executes { ctx ->
                        setBlockScoreboard(ctx, level)
                    }
            )
        )
    )

    private fun setBlockScoreboard(ctx: CommandContext<CommandSourceStack>, level: String): Int {
        val objective = StringArgumentType.getString(ctx, "objective")
        val minStr = StringArgumentType.getString(ctx, "min")
        val maxStr = StringArgumentType.getString(ctx, "max")
        val min = if (minStr == "infinity") null else minStr.toDoubleOrNull()
        val max = if (maxStr == "infinity") null else maxStr.toDoubleOrNull()
        if (min == null && minStr != "infinity") { errorFeedback(ctx, "最小值格式无效"); return 0 }
        if (max == null && maxStr != "infinity") { errorFeedback(ctx, "最大值格式无效"); return 0 }
        if (min != null && max != null && min >= max) {
            errorFeedback(ctx, "最小值必须小于最大值")
            return 0
        }

        ModConfigManager.update {
            when (level) {
                "block_level_one" -> { blockL1Scoreboard = objective; blockL1ScoreMin = min; blockL1ScoreMax = max }
                "block_level_two" -> { blockL2Scoreboard = objective; blockL2ScoreMin = min; blockL2ScoreMax = max }
                "block_level_three" -> { blockL3Scoreboard = objective; blockL3ScoreMin = min; blockL3ScoreMax = max }
            }
        }
        val overlapErr = ModConfigManager.get().validateBlockScoreboardOverlap()
        if (overlapErr != null) {
            errorFeedback(ctx, "计分板范围重叠: $overlapErr")
            return 0
        }
        feedback(ctx, "$level 计分板已设置为 $objective (${minStr} ~ ${maxStr})")
        return 1
    }

    private fun blockScoreboardReset(level: String) = Commands.literal(level).requires(requireOpLevel()).then(
        Commands.literal("reset")
            .executes { ctx ->
                ModConfigManager.update {
                    when (level) {
                        "block_level_one" -> { blockL1Scoreboard = ""; blockL1ScoreMin = null; blockL1ScoreMax = null }
                        "block_level_two" -> { blockL2Scoreboard = ""; blockL2ScoreMin = null; blockL2ScoreMax = null }
                        "block_level_three" -> { blockL3Scoreboard = ""; blockL3ScoreMin = null; blockL3ScoreMax = null }
                    }
                }
                feedback(ctx, "$level 计分板已重置")
                1
            }
    )

    // ===== Player Vision Scoreboard Commands =====

    private fun playerVisionScoreboard(level: String) = Commands.literal(level).requires(requireOpLevel()).then(
        Commands.argument("objective", StringArgumentType.word()).then(
            Commands.argument("min", StringArgumentType.word()).then(
                Commands.argument("max", StringArgumentType.word())
                    .executes { ctx ->
                        val obj = StringArgumentType.getString(ctx, "objective")
                        val minStr = StringArgumentType.getString(ctx, "min")
                        val maxStr = StringArgumentType.getString(ctx, "max")
                        val min = if (minStr == "infinity") null else minStr.toDoubleOrNull()
                        val max = if (maxStr == "infinity") null else maxStr.toDoubleOrNull()
                        if (min == null && minStr != "infinity") { errorFeedback(ctx, "最小值格式无效"); return@executes 0 }
                        if (max == null && maxStr != "infinity") { errorFeedback(ctx, "最大值格式无效"); return@executes 0 }
                        if (min != null && max != null && min >= max) {
                            errorFeedback(ctx, "最小值必须小于最大值")
                            return@executes 0
                        }
                        ModConfigManager.update {
                            when (level) {
                                "player_vision_level_one" -> {
                                    playerVisionL1Scoreboard = obj; playerVisionL1ScoreMin = min; playerVisionL1ScoreMax = max
                                }
                                "player_vision_level_two" -> {
                                    playerVisionL2Scoreboard = obj; playerVisionL2ScoreMin = min; playerVisionL2ScoreMax = max
                                }
                                "player_vision_level_three" -> {
                                    playerVisionL3Scoreboard = obj; playerVisionL3ScoreMin = min; playerVisionL3ScoreMax = max
                                }
                            }
                        }
                        // Validate that ranges do not overlap after update
                        val overlapErr = ModConfigManager.get().validatePlayerVisionScoreboardOverlap()
                        if (overlapErr != null) {
                            errorFeedback(ctx, "计分板范围重叠: $overlapErr")
                            return@executes 0
                        }
                        feedback(ctx, "$level 计分板已设置为 $obj (${minStr} ~ ${maxStr})")
                        1
                    }
            )
        )
    )

    /** Helper for player_vision level2/level3 <objective> <min> <max> commands. */
    private fun setPlayerVisionScoreboardFromLiteral(ctx: CommandContext<CommandSourceStack>, level: String): Int {
        val obj = StringArgumentType.getString(ctx, "objective")
        val minStr = StringArgumentType.getString(ctx, "min")
        val maxStr = StringArgumentType.getString(ctx, "max")
        val min = if (minStr == "infinity") null else minStr.toDoubleOrNull()
        val max = if (maxStr == "infinity") null else maxStr.toDoubleOrNull()
        if (min == null && minStr != "infinity") { errorFeedback(ctx, "最小值格式无效"); return 0 }
        if (max == null && maxStr != "infinity") { errorFeedback(ctx, "最大值格式无效"); return 0 }
        if (min != null && max != null && min >= max) {
            errorFeedback(ctx, "最小值必须小于最大值")
            return 0
        }
        ModConfigManager.update {
            when (level) {
                "level2" -> {
                    playerVisionL2Scoreboard = obj; playerVisionL2ScoreMin = min; playerVisionL2ScoreMax = max
                }
                "level3" -> {
                    playerVisionL3Scoreboard = obj; playerVisionL3ScoreMin = min; playerVisionL3ScoreMax = max
                }
            }
        }
        val overlapErr = ModConfigManager.get().validatePlayerVisionScoreboardOverlap()
        if (overlapErr != null) {
            errorFeedback(ctx, "计分板范围重叠: $overlapErr")
            return 0
        }
        feedback(ctx, "人物幻视 $level 计分板已设置为 $obj (${minStr} ~ ${maxStr})")
        return 1
    }

    private fun playerVisionScoreboardReset(level: String) = Commands.literal(level).requires(requireOpLevel()).then(
        Commands.literal("reset")
            .executes { ctx ->
                ModConfigManager.update {
                    when (level) {
                        "player_vision_level_one" -> {
                            playerVisionL1Scoreboard = ""; playerVisionL1ScoreMin = null; playerVisionL1ScoreMax = null
                        }
                        "player_vision_level_two" -> {
                            playerVisionL2Scoreboard = ""; playerVisionL2ScoreMin = null; playerVisionL2ScoreMax = null
                        }
                        "player_vision_level_three" -> {
                            playerVisionL3Scoreboard = ""; playerVisionL3ScoreMin = null; playerVisionL3ScoreMax = null
                        }
                    }
                }
                feedback(ctx, "$level 计分板已重置")
                1
            }
    )

    // ===== Gamerule Commands =====

    private fun gamerulePathPlayerDeath() = Commands.literal("vision_optimize_gamerule").then(
        Commands.literal("Path").then(
            Commands.literal("PlayerDeath").requires(requireOpLevel()).then(
                Commands.argument("value", BoolArgumentType.bool())
                    .executes { ctx ->
                        val v = BoolArgumentType.getBool(ctx, "value")
                        ModConfigManager.update { pathClearOnDeath = v }
                        feedback(ctx, if (v) "玩家死亡时将清除经过路径" else "玩家死亡时将不清除经过路径")
                        1
                    }
            )
        )
    )

    private fun gameruleVisionPlayerDeath() = Commands.literal("vision_optimize_gamerule").then(
        Commands.literal("vision").then(
            Commands.literal("PlayerDeath").requires(requireOpLevel()).then(
                Commands.argument("value", BoolArgumentType.bool())
                    .executes { ctx ->
                        val v = BoolArgumentType.getBool(ctx, "value")
                        ModConfigManager.update { visionInterferenceOnDeath = v }
                        feedback(ctx, if (v) "玩家死亡时将干扰物品幻视" else "玩家死亡时将不干扰物品幻视")
                        1
                    }
            )
        )
    )

    // ===== Management Commands =====

    private fun pathClear() = Commands.literal("PathClear").requires(requireOpLevel())
        .executes { ctx ->
            val server = ctx.source.server
            PlayerPathTracker.clearAllPaths(server)
            feedback(ctx, "已清除所有玩家经过路径")
            1
        }

    private fun visionClear() = Commands.literal("VisionClear").requires(requireOpLevel())
        .executes { ctx ->
            ItemTracker.clearAll()
            ItemTracker.clearDeathTimestamps()
            ItemTrackingComponent.clearTrackingIdsFromPlayerInventories(ctx.source.server)
            feedback(ctx, "已重置所有玩家拿过物品记录")
            1
        }

    private fun excludePlayerAdd() = Commands.literal("ExcludePlayer").then(
        Commands.literal("add").requires(requireOpLevel()).then(
            Commands.argument("player", StringArgumentType.word())
                .executes { ctx ->
                    val name = StringArgumentType.getString(ctx, "player")
                    ModConfigManager.update { excludedPlayers.add(name) }
                    feedback(ctx, "已将 $name 列入排除名单")
                    1
                }
        )
    )

    private fun excludePlayerRemove() = Commands.literal("ExcludePlayer").then(
        Commands.literal("remove").requires(requireOpLevel()).then(
            Commands.argument("player", StringArgumentType.word())
                .suggests { ctx, builder ->
                    ModConfigManager.get().excludedPlayers.forEach { builder.suggest(it) }
                    builder.buildFuture()
                }
                .executes { ctx ->
                    val name = StringArgumentType.getString(ctx, "player")
                    ModConfigManager.update { excludedPlayers.remove(name) }
                    feedback(ctx, "已将 $name 移出排除名单")
                    1
                }
        )
    )

    // ===== Item Vision Commands =====

    private fun nbtLevelOne() = Commands.literal("nbt_level_one").requires(requireOpLevel()).then(
        Commands.argument("count", IntegerArgumentType.integer(1, 100))
            .executes { ctx ->
                val count = IntegerArgumentType.getInteger(ctx, "count")
                ModConfigManager.update { itemL1MaxDisplay = count }
                feedback(ctx, "一阶段可显示玩家数量已设置为 $count")
                1
            }
    )

    private fun nbtLevelTwoVisionTime() = Commands.literal("nbt_level_two").then(
        Commands.literal("VisionTime").requires(requireOpLevel()).then(
            Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                .executes { ctx ->
                    val sec = IntegerArgumentType.getInteger(ctx, "seconds")
                    ModConfigManager.update { itemL2VisionTime = sec }
                    feedback(ctx, "二阶段物品幻视判定时间已设置为 $sec 秒")
                    1
                }
        )
    )

    private fun nbtLevelPlayerDeathFalse() = Commands.literal("nbt_level").then(
        Commands.literal("PlayerDeathFalse").requires(requireOpLevel()).then(
            Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                .executes { ctx ->
                    val min = IntegerArgumentType.getInteger(ctx, "minutes")
                    ModConfigManager.update { itemDeathFalseTime = min * 60 }
                    feedback(ctx, "玩家死亡后干扰幻视时间已设置为 $min 分钟")
                    1
                }
        )
    )

    private fun nbtScoreboard(level: String) = Commands.literal(level).requires(requireOpLevel()).then(
        Commands.argument("objective", StringArgumentType.word()).then(
            Commands.argument("min", StringArgumentType.word()).then(
                Commands.argument("max", StringArgumentType.word())
                    .executes { ctx ->
                        val obj = StringArgumentType.getString(ctx, "objective")
                        val minStr = StringArgumentType.getString(ctx, "min")
                        val maxStr = StringArgumentType.getString(ctx, "max")
                        val min = if (minStr == "infinity") null else minStr.toDoubleOrNull()
                        val max = if (maxStr == "infinity") null else maxStr.toDoubleOrNull()
                        if (min == null && minStr != "infinity") { errorFeedback(ctx, "最小值格式无效"); return@executes 0 }
                        if (max == null && maxStr != "infinity") { errorFeedback(ctx, "最大值格式无效"); return@executes 0 }
                        if (min != null && max != null && min >= max) {
                            errorFeedback(ctx, "最小值必须小于最大值")
                            return@executes 0
                        }
                        ModConfigManager.update {
                            when (level) {
                                "nbt_level_one" -> { itemL1Scoreboard = obj; itemL1ScoreMin = min; itemL1ScoreMax = max }
                                "nbt_level_two" -> { itemL2Scoreboard = obj; itemL2ScoreMin = min; itemL2ScoreMax = max }
                            }
                        }
                        val overlapErr = ModConfigManager.get().validateItemScoreboardOverlap()
                        if (overlapErr != null) {
                            errorFeedback(ctx, "计分板范围重叠: $overlapErr")
                            return@executes 0
                        }
                        feedback(ctx, "$level 计分板已设置为 $obj ($minStr ~ ${maxStr})")
                        1
                    }
            )
        )
    )

    private fun nbtScoreboardReset(level: String) = Commands.literal(level).requires(requireOpLevel()).then(
        Commands.literal("reset")
            .executes { ctx ->
                ModConfigManager.update {
                    when (level) {
                        "nbt_level_one" -> { itemL1Scoreboard = ""; itemL1ScoreMin = null; itemL1ScoreMax = null }
                        "nbt_level_two" -> { itemL2Scoreboard = ""; itemL2ScoreMin = null; itemL2ScoreMax = null }
                    }
                }
                feedback(ctx, "$level 计分板已重置")
                1
            }
    )

    private fun nbtCooldown() = Commands.literal("nbt_cooldown").requires(requireOpLevel()).then(
        Commands.argument("time", IntegerArgumentType.integer(1, 3600))
            .executes { ctx ->
                val time = IntegerArgumentType.getInteger(ctx, "time")
                ModConfigManager.update { itemCooldownTime = time }
                feedback(ctx, "物品幻视冷却时间已设置为 $time 秒")
                1
            }
    )

    private fun blockCooldown() = Commands.literal("block_cooldown").requires(requireOpLevel()).then(
        Commands.argument("time", IntegerArgumentType.integer(1, 3600))
            .executes { ctx ->
                val time = IntegerArgumentType.getInteger(ctx, "time")
                ModConfigManager.update { blockCooldownTime = time }
                feedback(ctx, "方块幻视冷却时间已设置为 $time 秒")
                1
            }
    )

    private fun nbtCoolDownReset() = Commands.literal("nbt_CoolDownReset").requires(requireOpLevel())
        .executes { ctx ->
            ModConfigManager.update { itemCooldownTime = 5 }
            feedback(ctx, "物品幻视冷却时间已重置为 5 秒")
            1
        }

    private fun blockCoolDownReset() = Commands.literal("block_CoolDownReset").requires(requireOpLevel())
        .executes { ctx ->
            ModConfigManager.update { blockCooldownTime = 3 }
            feedback(ctx, "方块幻视冷却时间已重置为 3 秒")
            1
        }

    // ===== Block Vision Probability Commands =====

    private fun blockVisionPercentSet() = Commands.literal("block_vision").requires(requireOpLevel())
        .then(Commands.literal("percent_set")
            .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                .executes { ctx ->
                    val v = IntegerArgumentType.getInteger(ctx, "value")
                    ModConfigManager.update { blockVisionPercent = v }
                    feedback(ctx, "方块幻视初始成功率已设置为 $v%")
                    1
                }
            ))

    private fun blockVisionFailAdd() = Commands.literal("block_vision").requires(requireOpLevel())
        .then(Commands.literal("fail_add")
            .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                .executes { ctx ->
                    val v = IntegerArgumentType.getInteger(ctx, "value")
                    ModConfigManager.update { blockVisionFailAdd = v }
                    feedback(ctx, "方块幻视失败后提升概率已设置为 $v%")
                    1
                }
            ))

    // ===== Item Vision Probability Commands =====

    private fun nbtVisionPercentSet() = Commands.literal("nbt_vision").requires(requireOpLevel())
        .then(Commands.literal("percent_set")
            .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                .executes { ctx ->
                    val v = IntegerArgumentType.getInteger(ctx, "value")
                    ModConfigManager.update { itemVisionPercent = v }
                    feedback(ctx, "物品幻视初始成功率已设置为 $v%")
                    1
                }
            ))

    private fun nbtVisionFailAdd() = Commands.literal("nbt_vision").requires(requireOpLevel())
        .then(Commands.literal("fail_add")
            .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                .executes { ctx ->
                    val v = IntegerArgumentType.getInteger(ctx, "value")
                    ModConfigManager.update { itemVisionFailAdd = v }
                    feedback(ctx, "物品幻视失败后提升概率已设置为 $v%")
                    1
                }
            ))

    // ===== Player Vision Commands =====

    private fun playerVisionSubCommand() = Commands.literal("player_vision")
        .then(Commands.literal("range").requires(requireOpLevel())
            .then(Commands.argument("x", IntegerArgumentType.integer(1, 100)).then(
                Commands.argument("y", IntegerArgumentType.integer(1, 100)).then(
                    Commands.argument("z", IntegerArgumentType.integer(1, 100))
                        .executes { ctx ->
                            val x = IntegerArgumentType.getInteger(ctx, "x")
                            val y = IntegerArgumentType.getInteger(ctx, "y")
                            val z = IntegerArgumentType.getInteger(ctx, "z")
                            ModConfigManager.update {
                                playerVisionRangeX = x; playerVisionRangeY = y; playerVisionRangeZ = z
                            }
                            feedback(ctx, "人物幻视范围已设置为 $x x $y x $z")
                            1
                        }
                )
            ))
        )
        .then(Commands.literal("reaction").requires(requireOpLevel())
            .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                .executes { ctx ->
                    val v = IntegerArgumentType.getInteger(ctx, "seconds")
                    ModConfigManager.update { playerVisionReactionTime = v }
                    feedback(ctx, "人物幻视被发现反应时间已设置为 $v 秒")
                    1
                }
            ))
        .then(Commands.literal("distance").requires(requireOpLevel())
            .then(Commands.argument("value", IntegerArgumentType.integer(1, 100))
                .executes { ctx ->
                    val v = IntegerArgumentType.getInteger(ctx, "value")
                    ModConfigManager.update { playerVisionDistance = v }
                    feedback(ctx, "人物幻视距离已设置为 $v 格")
                    1
                }
            ))
        .then(Commands.literal("fail_cooldown").requires(requireOpLevel())
            .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                .executes { ctx ->
                    val v = IntegerArgumentType.getInteger(ctx, "minutes")
                    ModConfigManager.update { playerVisionFailCooldown = v }
                    feedback(ctx, "人物幻视失败冷却已设置为 $v 分钟")
                    1
                }
            ))
        .then(Commands.literal("success_cooldown").requires(requireOpLevel())
            .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                .executes { ctx ->
                    val v = IntegerArgumentType.getInteger(ctx, "minutes")
                    ModConfigManager.update { playerVisionSuccessCooldown = v }
                    feedback(ctx, "人物幻视成功冷却已设置为 $v 分钟")
                    1
                }
            ))
        .then(Commands.literal("raid").requires(requireOpLevel())
            .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1200))
                .executes { ctx ->
                    val v = IntegerArgumentType.getInteger(ctx, "ticks")
                    ModConfigManager.update { playerVisionRaidTime = v }
                    feedback(ctx, "被幻视玩家视野内进行人物幻视时间已设置为 $v 刻(约${"%.1f".format(v / 20f)}秒)")
                    1
                }
            ))
        .then(Commands.literal("sneak").requires(requireOpLevel())
            .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1200))
                .executes { ctx ->
                    val v = IntegerArgumentType.getInteger(ctx, "ticks")
                    ModConfigManager.update { playerVisionSneakTime = v }
                    feedback(ctx, "被幻视玩家视野外进行人物幻视时间已设置为 $v 刻(约${"%.1f".format(v / 20f)}秒)")
                    1
                }
            ))
        .then(Commands.literal("discover").requires(requireOpLevel())
            .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1200))
                .executes { ctx ->
                    val v = IntegerArgumentType.getInteger(ctx, "ticks")
                    ModConfigManager.update { playerVisionDiscoverTime = v }
                    feedback(ctx, "第三者发现所需时间已设置为 $v 刻(约${"%.1f".format(v / 20f)}秒)")
                    1
                }
            ))
        .then(Commands.literal("third_perspective").requires(requireOpLevel())
            .then(Commands.argument("value", IntegerArgumentType.integer(1, 32))
                .executes { ctx ->
                    val v = IntegerArgumentType.getInteger(ctx, "value")
                    ModConfigManager.update { playerVisionThirdPerspectiveDistance = v }
                    feedback(ctx, "第三者视野内距离已设置为 $v 格")
                    1
                }
            ))
        .then(Commands.literal("level2").requires(requireOpLevel())
            .then(Commands.argument("objective", StringArgumentType.word()).then(
                Commands.argument("min", StringArgumentType.word()).then(
                    Commands.argument("max", StringArgumentType.word())
                        .executes { ctx ->
                            setPlayerVisionScoreboardFromLiteral(ctx, "level2")
                        }
                )
            ))
        )
        .then(Commands.literal("level3").requires(requireOpLevel())
            .then(Commands.argument("objective", StringArgumentType.word()).then(
                Commands.argument("min", StringArgumentType.word()).then(
                    Commands.argument("max", StringArgumentType.word())
                        .executes { ctx ->
                            setPlayerVisionScoreboardFromLiteral(ctx, "level3")
                        }
                )
            ))
        )
        .then(Commands.literal("cooldown_reset").requires(requireOpLevel())
            .executes { ctx ->
                val server = ctx.source.server
                // Clear cooldowns for all players
                server.playerList.players.forEach { p ->
                    PlayerVisionHandler.clearCooldown(p.displayName!!.string)
                }
                // Only notify the command sender
                val sender = ctx.source.player
                if (sender != null) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                        sender,
                        com.example.vision_optimize.network.PlayerVisionCooldownResetS2CPacket()
                    )
                }
                feedback(ctx, "已重置所有玩家的人物幻视冷却")
                1
            }
        )
        .then(Commands.literal("tag")
            .then(Commands.literal("add").requires(requireOpLevel())
                .then(Commands.argument("text", StringArgumentType.word())
                    .then(Commands.argument("color", StringArgumentType.word())
                        .executes { ctx ->
                            val text = StringArgumentType.getString(ctx, "text")
                            val colorStr = StringArgumentType.getString(ctx, "color")
                            val colorInt = parseHexColorArg(colorStr) ?: 0xFFFFFFFF.toInt()
                            ModConfigManager.update { playerVisionTags[text] = colorInt }
                            feedback(ctx, "已添加人物幻视文本标签: $text (颜色: $colorStr)")
                            1
                        }
                    )
                    .executes { ctx ->
                        val text = StringArgumentType.getString(ctx, "text")
                        ModConfigManager.update { playerVisionTags[text] = 0xFFFFFFFF.toInt() }
                        feedback(ctx, "已添加人物幻视文本标签: $text (默认白色)")
                        1
                    }
                ))
            .then(Commands.literal("remove").requires(requireOpLevel())
                .then(Commands.argument("text", StringArgumentType.word())
                    .suggests { ctx, builder ->
                        ModConfigManager.get().playerVisionTags.keys.forEach { builder.suggest(it) }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val text = StringArgumentType.getString(ctx, "text")
                        ModConfigManager.update {
                            playerVisionTags.remove(text)
                            val assigned = playerVisionPlayerTags.entries.find { it.value == text }?.key
                            if (assigned != null) playerVisionPlayerTags.remove(assigned)
                        }
                        feedback(ctx, "已删除人物幻视文本标签: $text")
                        1
                    }
                ))
            .then(Commands.literal("list").requires(requireOpLevel())
                .executes { ctx ->
                    val config = ModConfigManager.get()
                    val tags = config.playerVisionTags.keys
                    if (tags.isEmpty()) {
                        feedback(ctx, "当前没有人物幻视文本标签")
                    } else {
                        val info = tags.joinToString(", ")
                        feedback(ctx, "人物幻视文本标签: $info")
                    }
                    1
                }
            )
            .then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.literal("add").requires(requireOpLevel())
                    .then(Commands.argument("tag", StringArgumentType.word())
                        .suggests { ctx, builder ->
                            ModConfigManager.get().playerVisionTags.keys.forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val playerName = StringArgumentType.getString(ctx, "player")
                            val tag = StringArgumentType.getString(ctx, "tag")
                            val config = ModConfigManager.get()
                            if (tag !in config.playerVisionTags) {
                                errorFeedback(ctx, "标签 '$tag' 不存在")
                                return@executes 0
                            }
                            if (config.playerVisionPlayerTags.containsKey(playerName)) {
                                errorFeedback(ctx, "$playerName 已设有标签，请先清除")
                                return@executes 0
                            }
                            if (config.playerVisionPlayerTags.containsValue(tag)) {
                                errorFeedback(ctx, "标签 '$tag' 已被分配给其他玩家")
                                return@executes 0
                            }
                            ModConfigManager.update { playerVisionPlayerTags[playerName] = tag }
                            feedback(ctx, "已为 $playerName 分配人物幻视标签: $tag")
                            1
                        }
                    ))
                .then(Commands.literal("clear").requires(requireOpLevel())
                    .executes { ctx ->
                        val playerName = StringArgumentType.getString(ctx, "player")
                        ModConfigManager.update { playerVisionPlayerTags.remove(playerName) }
                        feedback(ctx, "已清除 $playerName 的人物幻视标签")
                        1
                    }
                )
            )
            .then(Commands.literal("tag_clear").requires(requireOpLevel())
                .executes { ctx ->
                    ModConfigManager.update {
                        playerVisionTags.clear()
                        playerVisionPlayerTags.clear()
                    }
                    feedback(ctx, "已清除所有人物幻视文本标签及分配")
                    1
                }
            )
            .then(Commands.literal("player_clear").requires(requireOpLevel())
                .executes { ctx ->
                    ModConfigManager.update { playerVisionPlayerTags.clear() }
                    feedback(ctx, "已清除所有人物幻视标签分配")
                    1
                }
            )
        )

    // ===== Tag Commands =====

    private fun tagAdd() = Commands.literal("tag").requires(requireOpLevel()).then(
        Commands.argument("player", StringArgumentType.word()).then(
            Commands.literal("add").then(
                Commands.argument("tag", StringArgumentType.word())
                    .executes { ctx ->
                        val playerName = StringArgumentType.getString(ctx, "player")
                        val tag = StringArgumentType.getString(ctx, "tag")
                        ModConfigManager.update {
                            playerTags.getOrPut(playerName) { mutableSetOf() }.add(tag)
                        }
                        // Also apply vanilla scoreboard tag to online player
                        val server = ctx.source.server
                        val targetPlayer = server.playerList.getPlayerByName(playerName)
                        if (targetPlayer != null) targetPlayer.addTag(tag)
                        updateAndSync(ctx) {} // sync to all clients
                        feedback(ctx, "已为 $playerName 添加标签 $tag (仅在标签模式下生效)")
                        1
                    }
            )
        )
    )

    private fun tagRemove() = Commands.literal("tag").requires(requireOpLevel()).then(
        Commands.argument("player", StringArgumentType.word()).then(
            Commands.literal("remove").then(
                Commands.argument("tag", StringArgumentType.word())
                    .executes { ctx ->
                        val playerName = StringArgumentType.getString(ctx, "player")
                        val tag = StringArgumentType.getString(ctx, "tag")
                        ModConfigManager.update {
                            playerTags[playerName]?.remove(tag)
                            if (playerTags[playerName].isNullOrEmpty()) playerTags.remove(playerName)
                        }
                        val server = ctx.source.server
                        val targetPlayer = server.playerList.getPlayerByName(playerName)
                        if (targetPlayer != null) targetPlayer.removeTag(tag)
                        updateAndSync(ctx) {}
                        feedback(ctx, "已为 $playerName 移除标签 $tag")
                        1
                    }
            )
        )
    )

    private fun tagList() = Commands.literal("tag").requires(requireOpLevel()).then(
        Commands.argument("player", StringArgumentType.word()).then(
            Commands.literal("list")
                .executes { ctx ->
                    val playerName = StringArgumentType.getString(ctx, "player")
                    val config = ModConfigManager.get()
                    val tags = config.playerTags[playerName] ?: emptySet()
                    if (tags.isEmpty()) {
                        feedback(ctx, "$playerName 没有标签", ChatFormatting.YELLOW)
                    } else {
                        feedback(ctx, "$playerName 的标签: ${tags.joinToString(", ")}", ChatFormatting.GREEN)
                    }
                    1
                }
        )
    )

    private fun openTagAdd() = Commands.literal("open").then(
        Commands.literal("tag").requires(requireOpLevel()).then(
            Commands.literal("add").then(
                Commands.argument("tag", StringArgumentType.word())
                    .executes { ctx ->
                        val tag = StringArgumentType.getString(ctx, "tag")
                        ModConfigManager.update {
                            accessTags.add(tag)
                            tagVisionEnabled.add(tag) // new tags are vision-enabled by default
                        }
                        feedback(ctx, "已添加权限标签: $tag (幻视已启用)")
                        1
                    }
            )
        )
    )

    private fun openTagRemove() = Commands.literal("open").then(
        Commands.literal("tag").requires(requireOpLevel()).then(
            Commands.literal("remove").then(
                Commands.argument("tag", StringArgumentType.word())
                    .suggests { ctx, builder ->
                        ModConfigManager.get().accessTags.forEach { builder.suggest(it) }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val tag = StringArgumentType.getString(ctx, "tag")
                        ModConfigManager.update {
                            accessTags.remove(tag)
                            tagVisionEnabled.remove(tag)
                        }
                        feedback(ctx, "已移除权限标签: $tag")
                        1
                    }
            )
        )
    )

    private fun openTagList() = Commands.literal("open").then(
        Commands.literal("tag").requires(requireOpLevel()).then(
            Commands.literal("list")
                .executes { ctx ->
                    val config = ModConfigManager.get()
                    val tags = config.accessTags
                    if (tags.isEmpty()) {
                        feedback(ctx, "当前没有已添加的权限标签")
                    } else {
                        val tagInfo = tags.joinToString(", ") { tag ->
                            val visionIcon = if (tag in config.tagVisionEnabled) "✓" else "✕"
                            "$tag(幻视$visionIcon)"
                        }
                        feedback(ctx, "已添加的权限标签: $tagInfo")
                    }
                    1
                }
        )
    )

    private fun openTagVisionEnable() = Commands.literal("open").then(
        Commands.literal("tag").requires(requireOpLevel()).then(
            Commands.literal("vision").then(
                Commands.literal("enable").then(
                    Commands.argument("tag", StringArgumentType.word())
                        .suggests { ctx, builder ->
                            ModConfigManager.get().accessTags.forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val tag = StringArgumentType.getString(ctx, "tag")
                            if (tag !in ModConfigManager.get().accessTags) {
                                errorFeedback(ctx, "标签 '$tag' 不存在")
                                return@executes 0
                            }
                            ModConfigManager.update { tagVisionEnabled.add(tag) }
                            feedback(ctx, "已为标签 '$tag' 启用幻视功能")
                            1
                        }
                )
            )
        )
    )

    private fun openTagVisionDisable() = Commands.literal("open").then(
        Commands.literal("tag").requires(requireOpLevel()).then(
            Commands.literal("vision").then(
                Commands.literal("disable").then(
                    Commands.argument("tag", StringArgumentType.word())
                        .suggests { ctx, builder ->
                            ModConfigManager.get().tagVisionEnabled.forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val tag = StringArgumentType.getString(ctx, "tag")
                            ModConfigManager.update { tagVisionEnabled.remove(tag) }
                            feedback(ctx, "已为标签 '$tag' 禁用幻视功能")
                            1
                        }
                )
            )
        )
    )

    // ===== Mode Commands (set player vision level) =====

    private fun setBlockLevelOne() = Commands.literal("block_level_one")
        .executes { ctx ->
            val player = ctx.source.playerOrException
            if (!hasGeneralVisionAccess(player)) {
                errorFeedback(ctx, "你没有幻视权限")
                return@executes 0
            }
            val config = ModConfigManager.get()
            if (config.blockL1Scoreboard.isNotEmpty()) {
                errorFeedback(ctx, "已设定计分板区间，无法手动设置等级")
                return@executes 0
            }
            ModConfigManager.update { playerBlockLevels[player.displayName!!.string] = VisionLevel.BLOCK_LEVEL_1 }
            com.example.vision_optimize.vision.block.BlockVisionHandler.getBlockVisionLevel(player)
            feedback(ctx, "你的方块幻视等级已调为一阶段")
            1
        }

    private fun setBlockLevelTwo() = Commands.literal("block_level_two")
        .executes { ctx ->
            val player = ctx.source.playerOrException
            if (!hasGeneralVisionAccess(player)) {
                errorFeedback(ctx, "你没有幻视权限")
                return@executes 0
            }
            val config = ModConfigManager.get()
            if (config.blockL2Scoreboard.isNotEmpty()) {
                errorFeedback(ctx, "已设定计分板区间，无法手动设置等级")
                return@executes 0
            }
            ModConfigManager.update { playerBlockLevels[player.displayName!!.string] = VisionLevel.BLOCK_LEVEL_2 }
            com.example.vision_optimize.vision.block.BlockVisionHandler.getBlockVisionLevel(player)
            feedback(ctx, "你的方块幻视等级已调为二阶段")
            1
        }

    private fun setBlockLevelThree() = Commands.literal("block_level_three")
        .executes { ctx ->
            val player = ctx.source.playerOrException
            if (!hasGeneralVisionAccess(player)) {
                errorFeedback(ctx, "你没有幻视权限")
                return@executes 0
            }
            val config = ModConfigManager.get()
            if (config.blockL3Scoreboard.isNotEmpty()) {
                errorFeedback(ctx, "已设定计分板区间，无法手动设置等级")
                return@executes 0
            }
            ModConfigManager.update { playerBlockLevels[player.displayName!!.string] = VisionLevel.BLOCK_LEVEL_3 }
            com.example.vision_optimize.vision.block.BlockVisionHandler.getBlockVisionLevel(player)
            feedback(ctx, "你的方块幻视等级已调为三阶段")
            1
        }

    private fun setNbtLevelOne() = Commands.literal("nbt_level_one")
        .executes { ctx ->
            val player = ctx.source.playerOrException
            if (!hasGeneralVisionAccess(player)) {
                errorFeedback(ctx, "你没有幻视权限")
                return@executes 0
            }
            val config = ModConfigManager.get()
            if (config.itemL1Scoreboard.isNotEmpty()) {
                errorFeedback(ctx, "已设定计分板区间，无法手动设置等级")
                return@executes 0
            }
            ModConfigManager.update { playerItemLevels[player.displayName!!.string] = VisionLevel.ITEM_LEVEL_1 }
            com.example.vision_optimize.vision.item.ItemVisionHandler.getItemVisionLevel(player)
            feedback(ctx, "你的物品幻视等级已调为一阶段")
            1
        }

    private fun setNbtLevelTwo() = Commands.literal("nbt_level_two")
        .executes { ctx ->
            val player = ctx.source.playerOrException
            if (!hasGeneralVisionAccess(player)) {
                errorFeedback(ctx, "你没有幻视权限")
                return@executes 0
            }
            val config = ModConfigManager.get()
            if (config.itemL2Scoreboard.isNotEmpty()) {
                errorFeedback(ctx, "已设定计分板区间，无法手动设置等级")
                return@executes 0
            }
            ModConfigManager.update { playerItemLevels[player.displayName!!.string] = VisionLevel.ITEM_LEVEL_2 }
            com.example.vision_optimize.vision.item.ItemVisionHandler.getItemVisionLevel(player)
            feedback(ctx, "你的物品幻视等级已调为二阶段")
            1
        }

    private fun setPlayerVisionLevelOne() = Commands.literal("player_vision_level_one")
        .executes { ctx ->
            val player = ctx.source.playerOrException
            if (!hasGeneralVisionAccess(player)) {
                errorFeedback(ctx, "你没有幻视权限")
                return@executes 0
            }
            val config = ModConfigManager.get()
            if (config.playerVisionL1Scoreboard.isNotEmpty()) {
                errorFeedback(ctx, "已设定计分板区间，无法手动设置等级")
                return@executes 0
            }
            updateAndSync(ctx) {
                playerPlayerLevels[player.displayName!!.string] = VisionLevel.PLAYER_VISION_LEVEL_1
            }
            com.example.vision_optimize.vision.player.PlayerVisionHandler.getPlayerVisionLevel(player)
            feedback(ctx, "你的人物幻视等级已调为一阶段")
            1
        }

    private fun setPlayerVisionLevelTwo() = Commands.literal("player_vision_level_two")
        .executes { ctx ->
            val player = ctx.source.playerOrException
            if (!hasGeneralVisionAccess(player)) {
                errorFeedback(ctx, "你没有幻视权限")
                return@executes 0
            }
            val config = ModConfigManager.get()
            if (config.playerVisionL2Scoreboard.isNotEmpty()) {
                errorFeedback(ctx, "已设定计分板区间，无法手动设置等级")
                return@executes 0
            }
            updateAndSync(ctx) {
                playerPlayerLevels[player.displayName!!.string] = VisionLevel.PLAYER_VISION_LEVEL_2
            }
            com.example.vision_optimize.vision.player.PlayerVisionHandler.getPlayerVisionLevel(player)
            feedback(ctx, "你的人物幻视等级已调为二阶段")
            1
        }

    private fun setPlayerVisionLevelThree() = Commands.literal("player_vision_level_three")
        .executes { ctx ->
            val player = ctx.source.playerOrException
            if (!hasGeneralVisionAccess(player)) {
                errorFeedback(ctx, "你没有幻视权限")
                return@executes 0
            }
            val config = ModConfigManager.get()
            if (config.playerVisionL3Scoreboard.isNotEmpty()) {
                errorFeedback(ctx, "已设定计分板区间，无法手动设置等级")
                return@executes 0
            }
            updateAndSync(ctx) {
                playerPlayerLevels[player.displayName!!.string] = VisionLevel.PLAYER_VISION_LEVEL_3
            }
            com.example.vision_optimize.vision.player.PlayerVisionHandler.getPlayerVisionLevel(player)
            feedback(ctx, "你的人物幻视等级已调为三阶段")
            1
        }

    // ===== System Commands =====

    private fun disableCmd() = Commands.literal("disable").requires(requireOpLevel())
        .executes { ctx ->
            ModConfigManager.update { enabled = false }
            feedback(ctx, "vision_optimize 已禁用", ChatFormatting.RED)
            1
        }

    private fun enableCmd() = Commands.literal("enable").requires(requireOpLevel())
        .executes { ctx ->
            ModConfigManager.update { enabled = true }
            feedback(ctx, "vision_optimize 已启用", ChatFormatting.GREEN)
            1
        }

    private fun manosabaCmd() = Commands.literal("manosaba").requires(requireOpLevel())
        .then(Commands.literal("on")
            .executes { ctx ->
                updateAndSync(ctx) { manosabaEnabled = true }
                feedback(ctx, "魔法少女的魔女审判 已开启", ChatFormatting.GREEN)
                1
            }
        )
        .then(Commands.literal("off")
            .executes { ctx ->
                updateAndSync(ctx) { manosabaEnabled = false }
                feedback(ctx, "魔法少女的魔女审判 已关闭", ChatFormatting.RED)
                1
            }
        )

    private fun allMemberCmd() = Commands.literal("AllMember").requires(requireOpLevel())
        .executes { ctx ->
            ModConfigManager.update { mode = AccessMode.ALL_MEMBERS }
            feedback(ctx, "已设置为全员可使用模式", ChatFormatting.YELLOW)
            1
        }

    private fun tagMemberCmd() = Commands.literal("TagMember").requires(requireOpLevel())
        .executes { ctx ->
            ModConfigManager.update { mode = AccessMode.TAG_MEMBER }
            feedback(ctx, "已设置为特定标签使用模式", ChatFormatting.GRAY)
            1
        }

    // ===== Help Command =====

    private fun helpCmd() = Commands.literal("help")
        .executes { ctx ->
            val source = ctx.source
            source.sendSystemMessage(TextBuilder.creditLine())
            source.sendSystemMessage(Component.literal("").withStyle(ChatFormatting.WHITE))

            val helpLines = listOf(
                "/vision_optimize block_level_one scope <x> <y> <z>" to "修改方块幻视第一阶段幻视区域",
                "/vision_optimize block search <秒>" to "修改方块幻视搜索时间",
                "/vision_optimize block_level_one after_time <秒>" to "修改第一阶段玩家经过判定时间",
                "/vision_optimize block_level_one player_interval <秒>" to "修改第一阶段玩家在一起判定间隔",
                "/vision_optimize block_level_two scope <x> <y> <z>" to "修改第二阶段幻视区域",
                "/vision_optimize block_level_two SideEffect_one <秒>" to "修改第二阶段「在我附近」判定区间",
                "/vision_optimize block_level_two SideEffect_two <min|infinity> <max|infinity>" to "修改第二阶段「不久前」判定区间",
                "/vision_optimize block_level_two SideEffect_three <min|infinity> <max|infinity>" to "修改第二阶段「最近」判定区间",
                "/vision_optimize block_level_two SideEffect_four <min|infinity> <max|infinity>" to "修改第二阶段「曾近」判定区间",
                "/vision_optimize block_level_three scope <x> <y> <z>" to "修改第三阶段幻视区域",
                "/vision_optimize block_level_three after_time <秒>" to "修改第三阶段玩家判定时间范围",
                "/vision_optimize block_level_one/block_level_two/block_level_three <计分板> <min|infinity> <max|infinity>" to "设置方块幻视计分板",
                "/vision_optimize block_level_one/block_level_two/block_level_three reset" to "重置方块幻视计分板",
                "/vision_optimize_gamerule Path PlayerDeath <true/false>" to "死亡是否清除路径",
                "/vision_optimize_gamerule vision PlayerDeath <true/false>" to "死亡是否干扰幻视",
                "/vision_optimize PathClear" to "清除所有玩家经过路径",
                "/vision_optimize VisionClear" to "重置所有物品记录",
                "/vision_optimize ExcludePlayer add/remove <玩家名称>" to "排除/取消排除玩家",
                "/vision_optimize nbt_level_one <数值>" to "设置一阶段物品幻视显示数量",
                "/vision_optimize nbt_level_two VisionTime <秒>" to "设置二阶段物品幻视时间",
                "/vision_optimize nbt_level PlayerDeathFalse <分钟>" to "设置死亡干扰时间",
                "/vision_optimize nbt_level_one/nbt_level_two <计分板> <min|infinity> <max|infinity>" to "设置物品幻视计分板",
                "/vision_optimize nbt_level_one/nbt_level_two reset" to "重置物品幻视计分板",
                "/vision_optimize tag <玩家名称> add/remove <标签>" to "管理玩家标签",
                "/vision_optimize tag <玩家名称> list" to "查看玩家标签",
                "/vision_optimize open tag add/remove/list <标签>" to "管理权限标签",
                "/vision_optimize block_level_one/two/three" to "手动设置方块幻视等级",
                "/vision_optimize nbt_level_one/two" to "手动设置物品幻视等级",
                "/vision_optimize disable" to "禁用mod",
                "/vision_optimize enable" to "启用mod",
                "/vision_optimize AllMember" to "全员可用模式",
                "/vision_optimize TagMember" to "特定标签模式",
                "/vision_optimize nbt_cooldown <时间(秒)>" to "修改物品幻视冷却时间",
                "/vision_optimize block_cooldown <时间(秒)>" to "修改方块幻视冷却时间",
                "/vision_optimize nbt_CoolDownReset" to "重置物品幻视冷却时间",
                "/vision_optimize block_CoolDownReset" to "重置方块幻视冷却时间",
                "/vision_optimize block_vision percent_set <0~100>" to "设置方块幻视初始成功率",
                "/vision_optimize block_vision fail_add <0~100>" to "设置方块幻视失败后提升概率",
                "/vision_optimize nbt_vision percent_set <0~100>" to "设置物品幻视初始成功率",
                "/vision_optimize nbt_vision fail_add <0~100>" to "设置物品幻视失败后提升概率",
                "/vision_optimize player_vision range <x> <y> <z>" to "设置人物幻视范围(单位格,以施术者为中心)",
                "/vision_optimize player_vision reaction <时间(单位秒)>" to "设置人物幻视被发现后反应时间",
                "/vision_optimize player_vision distance <数值>" to "设置人物幻视距离(单位格)",
                "/vision_optimize player_vision fail_cooldown <时间(单位分)>" to "设置人物幻视失败冷却",
                "/vision_optimize player_vision success_cooldown <时间(单位分)>" to "设置人物幻视成功冷却",
                "/vision_optimize player_vision raid <时间(单位刻)>" to "设置视野内幻视所需时间",
                "/vision_optimize player_vision sneak <时间(单位刻)>" to "设置视野外幻视所需时间",
                "/vision_optimize player_vision discover <时间(单位刻)>" to "设置第三者发现所需时间",
                "/vision_optimize player_vision third_perspective <数值(单位格)>" to "设置第三者视野内最远距离",
                "/vision_optimize player_vision tag add <文本> [<颜色(#000000)>]" to "添加人物幻视文本标签(可指定颜色)",
                "/vision_optimize player_vision tag remove <文本>" to "删除人物幻视文本标签",
                "/vision_optimize player_vision tag list" to "查看人物幻视文本标签",
                "/vision_optimize player_vision tag <玩家名称> add <标签文本>" to "为玩家分配人物幻视标签",
                "/vision_optimize player_vision tag <玩家名称> clear" to "清除玩家的人物幻视标签",
                "/vision_optimize player_vision tag tag_clear" to "清除所有人物幻视文本标签",
                "/vision_optimize player_vision tag player_clear" to "清除所有人物幻视标签分配",
                "/vision_optimize player_vision cooldown_reset" to "重置所有玩家的人物幻视冷却",
                "/vision_optimize player_vision level2 <计分板> <min|infinity> <max|infinity>" to "设置人物幻视二阶段计分板",
                "/vision_optimize player_vision level3 <计分板> <min|infinity> <max|infinity>" to "设置人物幻视三阶段计分板",
                "/vision_optimize player_vision_level_one/player_vision_level_two/player_vision_level_three <计分板> <min|infinity> <max|infinity>" to "设置人物幻视计分板",
                "/vision_optimize player_vision_level_one/player_vision_level_two/player_vision_level_three reset" to "重置人物幻视计分板",
                "/vision_optimize player_vision_level_one/two/three" to "手动设置人物幻视等级",
                "/vision_optimize manosaba on/off" to "开关魔法少女的魔女审判(角色名彩色显示)",
            )

            for ((cmd, desc) in helpLines) {
                source.sendSystemMessage(
                    Component.literal(cmd).withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(desc).withStyle(ChatFormatting.GREEN))
                )
            }
            source.sendSystemMessage(Component.literal(""))
            source.sendSystemMessage(Component.literal("感谢游玩魔溃喵").withStyle(ChatFormatting.GREEN))
            1
        }
}
