package com.example

import com.example.config.ChatLimitConfig
import com.example.network.ChatLimitNetwork
import com.example.util.HeartTagTracker
import com.example.util.SpeakVolumeTracker
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory

object ChatLimit_Moi : ModInitializer {
    val LOGGER = LoggerFactory.getLogger("chatlimit_moi")
    var config: ChatLimitConfig = ChatLimitConfig.load()

    override fun onInitialize() {
        LOGGER.info("ChatLimit_Moi initializing...")
        config = ChatLimitConfig.load()
        HeartTagTracker.load()

        // Register networking (payload types + server-side receivers)
        ChatLimitNetwork.registerCommon()
        ChatLimitNetwork.registerServerReceiver { player ->
            ChatLimitNetwork.sendConfigToClient(player, config)
            ChatLimitNetwork.sendAdminAccessToClient(player, SpeakVolumeTracker.hasAdminTag(player))
        }
        ChatLimitNetwork.registerAdminAccessReceiver { player ->
            ChatLimitNetwork.sendAdminAccessToClient(player, SpeakVolumeTracker.hasAdminTag(player))
        }
        ChatLimitNetwork.registerVolumeReceiver { player, mode, multiplier, attitude, transform ->
            SpeakVolumeTracker.setState(player, mode, multiplier, attitude, transform)
            ChatLimitNetwork.sendRestrictionToClient(
                player,
                SpeakVolumeTracker.maxAllowedSliderPosition(player),
                SpeakVolumeTracker.restrictionRemainingMillis(player)
            )
            LOGGER.info("Player {} set speak mode={} multiplier={}", player.name.string, mode, multiplier)
        }
        ChatLimitNetwork.registerHeartTagsReceiver { tags ->
            HeartTagTracker.setTags(tags)
            LOGGER.info("Heart tags updated: {}", tags)
        }

        // Clean up session trackers on disconnect (heart tags persist to disk)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            SpeakVolumeTracker.removePlayer(handler.player)
        }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            ChatLimitNetwork.sendAdminAccessToClient(
                handler.player,
                SpeakVolumeTracker.hasAdminTag(handler.player)
            )
        }

        // Chat message filtering is handled by ChatBroadcastMixin (see mixins JSON)
        // The mixin intercepts ClientboundPlayerChatPacket in PlayerList.broadcastAll()

        registerCommands()
        LOGGER.info("ChatLimit_Moi initialized successfully")
    }

    // ==================== COMMAND REGISTRATION ====================

    private fun registerCommands() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->

            val root = Commands.literal("chatlimit")

            root.then(
                Commands.literal("start")
                    .requires { it.hasPermission(2) }
                    .executes {
                        config.enabled = true
                        config.playerSilenceEnabled = false
                        config.save()
                        SpeakVolumeTracker.disablePlayerSilence(it.source.server)
                        ChatLimitNetwork.sendConfigToClient(it.source.playerOrException, config)
                        it.source.sendSuccess(
                            { Component.literal("§a聊天距离限制已启用") }, true
                        )
                        1
                    }
            )

            root.then(
                Commands.literal("stop")
                    .requires { it.hasPermission(2) }
                    .executes {
                        config.enabled = false
                        config.playerSilenceEnabled = false
                        config.save()
                        SpeakVolumeTracker.disablePlayerSilence(it.source.server)
                        ChatLimitNetwork.sendConfigToClient(it.source.playerOrException, config)
                        it.source.sendSuccess(
                            { Component.literal("§c聊天距离限制已禁用") }, true
                        )
                        1
                    }
            )

            root.then(
                Commands.literal("player_silence")
                    .requires { it.hasPermission(2) }
                    .executes {
                        config.enabled = false
                        config.playerSilenceEnabled = true
                        config.save()
                        SpeakVolumeTracker.enablePlayerSilence(it.source.server)
                        ChatLimitNetwork.sendConfigToClient(it.source.playerOrException, config)
                        it.source.sendSuccess({ Component.literal("§e玩家沉默已启用，距离限制已关闭") }, true)
                        1
                    }
            )

            root.then(buildChatSubcommand())
            root.then(buildPenetrateSubcommand())

            // Axis multiplier commands
            root.then(
                Commands.literal("y_limit")
                    .requires { it.hasPermission(2) }
                    .then(
                        Commands.argument("value", StringArgumentType.word())
                            .executes { ctx -> setAxisMultiplier(ctx, "y") }
                    )
            )
            root.then(
                Commands.literal("x_limit")
                    .requires { it.hasPermission(2) }
                    .then(
                        Commands.argument("value", StringArgumentType.word())
                            .executes { ctx -> setAxisMultiplier(ctx, "x") }
                    )
            )
            root.then(
                Commands.literal("z_limit")
                    .requires { it.hasPermission(2) }
                    .then(
                        Commands.argument("value", StringArgumentType.word())
                            .executes { ctx -> setAxisMultiplier(ctx, "z") }
                    )
            )

            // Quiet range command
            root.then(
                Commands.literal("quiet")
                    .requires { it.hasPermission(2) }
                    .then(
                        Commands.argument("x", StringArgumentType.word())
                            .then(
                                Commands.argument("y", StringArgumentType.word())
                                    .then(
                                        Commands.argument("z", StringArgumentType.word())
                                            .executes { ctx -> setQuietRange(ctx) }
                                    )
                            )
                    )
            )

            // Shout spread (range multiplier) command
            root.then(
                Commands.literal("shout_spread")
                    .requires { it.hasPermission(2) }
                    .then(
                        Commands.argument("multiplier", StringArgumentType.word())
                            .executes { ctx -> setShoutSpread(ctx) }
                    )
            )

            // Shout penetrate (penetration multiplier) command
            root.then(
                Commands.literal("shout_penetrate")
                    .requires { it.hasPermission(2) }
                    .then(
                        Commands.argument("multiplier", StringArgumentType.word())
                            .executes { ctx -> setShoutPenetrate(ctx) }
                    )
            )

            root.then(
                Commands.literal("setting")
                    .executes {
                        val player = it.source.playerOrException
                        ChatLimitNetwork.sendConfigToClient(player, config)
                        it.source.sendSuccess(
                            { Component.literal("§a设置面板已打开") }, false
                        )
                        1
                    }
            )

            // help — any player
            root.then(
                Commands.literal("help")
                    .executes { ctx ->
                        val src = ctx.source
                        src.sendSuccess({ Component.literal("§7This mod By.Momoi") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit chat level one §6<最小值 | infinity> <最大值 | infinity> §e设置清楚声音传播距离") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit chat level two §6<最小值 | infinity> <最大值 | infinity> §e设置模糊声音传播距离") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit chat level three §6<最小值 | infinity> <最大值 | infinity> §e设置略有声音传播距离") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit chat level four §6<数值 | infinity> §e在此距离后玩家听不到你的声音") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit penetrate level one §6<数值 | infinity> §e设置不完整方块穿透格数") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit penetrate level two §6<数值 | infinity> §e设置小密度方块穿透格数") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit penetrate level three §6<数值 | infinity> §e设置中密度方块穿透格数") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit penetrate level four §6<数值 | infinity> §e设置大密度方块穿透格数") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit setting §e打开设置界面 §6快捷键为KP_6 [小数字键盘6] §7默认") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit start §e启用mod") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit stop §e禁用mod") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit x_limit §6<数值> §e设定x轴限制格数倍率 §6[数值最高为100]") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit z_limit §6<数值> §e设定z轴限制格数倍率 §6[数值最高为100]") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit y_limit §6<数值> §e设定y轴限制格数倍率 §6[数值最高为100]") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit quiet §6<X> <Y> <Z> §e设置小声说话X/Y/Z轴听觉范围") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit shout_spread §6<倍数> §e设置大喊声音传播范围倍率") }, false)
                        src.sendSuccess({ Component.literal("§a/chatlimit shout_penetrate §6<倍数> §e设置大喊可穿透方块倍率") }, false)
                        src.sendSuccess({ Component.literal("") }, false)
                        src.sendSuccess({ Component.literal("§9在清楚范围外的听玩家说话越远文字会越黑") }, false)
                        src.sendSuccess({ Component.literal("§3在设置面板中输入空格相当于指令中的infinity") }, false)
                        src.sendSuccess({ Component.literal("§a~~感谢游玩魔溃喵~~") }, false)
                        1
                    }
            )

            dispatcher.root.addChild(root.build())
        }
    }

    private fun buildChatSubcommand(): com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> {
        val chatNode = Commands.literal("chat")
            .requires { it.hasPermission(2) }
        val levelNode = Commands.literal("level")

        levelNode.then(Commands.literal("one").then(
            Commands.argument("min", StringArgumentType.word()).then(
                Commands.argument("max", StringArgumentType.word())
                    .executes { ctx -> setChatLevel(ctx, 1) }
            )
        ))
        levelNode.then(Commands.literal("two").then(
            Commands.argument("min", StringArgumentType.word()).then(
                Commands.argument("max", StringArgumentType.word())
                    .executes { ctx -> setChatLevel(ctx, 2) }
            )
        ))
        levelNode.then(Commands.literal("three").then(
            Commands.argument("min", StringArgumentType.word()).then(
                Commands.argument("max", StringArgumentType.word())
                    .executes { ctx -> setChatLevel(ctx, 3) }
            )
        ))
        levelNode.then(Commands.literal("four").then(
            Commands.argument("value", StringArgumentType.word())
                .executes { ctx -> setChatLevelFour(ctx) }
        ))

        chatNode.then(levelNode)
        return chatNode
    }

    private fun buildPenetrateSubcommand(): com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> {
        val penNode = Commands.literal("penetrate")
            .requires { it.hasPermission(2) }
        val levelNode = Commands.literal("level")

        levelNode.then(Commands.literal("one").then(
            Commands.argument("value", StringArgumentType.word())
                .executes { ctx -> setPenetrateLevel(ctx, 1) }
        ))
        levelNode.then(Commands.literal("two").then(
            Commands.argument("value", StringArgumentType.word())
                .executes { ctx -> setPenetrateLevel(ctx, 2) }
        ))
        levelNode.then(Commands.literal("three").then(
            Commands.argument("value", StringArgumentType.word())
                .executes { ctx -> setPenetrateLevel(ctx, 3) }
        ))
        levelNode.then(Commands.literal("four").then(
            Commands.argument("value", StringArgumentType.word())
                .executes { ctx -> setPenetrateLevel(ctx, 4) }
        ))

        penNode.then(levelNode)
        return penNode
    }

    // ==================== COMMAND HANDLERS ====================

    private fun parseArg(raw: String): Double? =
        if (raw.isBlank() || raw.equals("infinity", ignoreCase = true)) Double.POSITIVE_INFINITY
        else raw.toDoubleOrNull()

    private fun fmt(v: Double): String =
        if (v.isInfinite()) "" else v.toBigDecimal().stripTrailingZeros().toPlainString()

    private fun setChatLevel(
        ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
        level: Int,
    ): Int {
        val src = ctx.source
        val minS = StringArgumentType.getString(ctx, "min")
        val maxS = StringArgumentType.getString(ctx, "max")
        val min = parseArg(minS) ?: return fail(src, "§c无效的数值: $minS")
        val max = parseArg(maxS) ?: return fail(src, "§c无效的数值: $maxS")
        if (min >= max) return fail(src, "§c最小值必须小于最大值")

        val oMin: Double; val oMax: Double
        when (level) {
            1 -> { oMin = config.chatLevelOneMin; oMax = config.chatLevelOneMax; config.chatLevelOneMin = min; config.chatLevelOneMax = max }
            2 -> { oMin = config.chatLevelTwoMin; oMax = config.chatLevelTwoMax; config.chatLevelTwoMin = min; config.chatLevelTwoMax = max }
            3 -> { oMin = config.chatLevelThreeMin; oMax = config.chatLevelThreeMax; config.chatLevelThreeMin = min; config.chatLevelThreeMax = max }
            else -> return 0
        }
        val err = config.validateChatLevels()
        if (err != null) {
            when (level) {
                1 -> { config.chatLevelOneMin = oMin; config.chatLevelOneMax = oMax }
                2 -> { config.chatLevelTwoMin = oMin; config.chatLevelTwoMax = oMax }
                3 -> { config.chatLevelThreeMin = oMin; config.chatLevelThreeMax = oMax }
            }
            return fail(src, err)
        }
        config.save()
        LOGGER.info("ChatLimit config updated: chat level {} = {} ~ {}", level, fmt(min), fmt(max))
        src.sendSuccess({ Component.literal("§a聊天距离等级 $level: ${fmt(min)} ~ ${fmt(max)}") }, true)
        return 1
    }

    private fun setChatLevelFour(
        ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
    ): Int {
        val src = ctx.source
        val vs = StringArgumentType.getString(ctx, "value")
        val v = parseArg(vs) ?: return fail(src, "§c无效的数值: $vs")
        if (v < 0 && !v.isInfinite()) return fail(src, "§c数值不能为负数")

        val old = config.chatLevelFour
        config.chatLevelFour = v
        val err = config.validateChatLevels()
        if (err != null) { config.chatLevelFour = old; return fail(src, err) }
        config.save()
        src.sendSuccess({ Component.literal("§a听不见范围已设置为: ${fmt(v)}") }, true)
        return 1
    }

    private fun setPenetrateLevel(
        ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
        level: Int,
    ): Int {
        val src = ctx.source
        val vs = StringArgumentType.getString(ctx, "value")
        val v = parseArg(vs) ?: return fail(src, "§c无效的数值: $vs")
        if (v < 0 && !v.isInfinite()) return fail(src, "§c数值不能为负数")

        val old: Double
        when (level) {
            1 -> { old = config.penetrateLevelOne; config.penetrateLevelOne = v }
            2 -> { old = config.penetrateLevelTwo; config.penetrateLevelTwo = v }
            3 -> { old = config.penetrateLevelThree; config.penetrateLevelThree = v }
            4 -> { old = config.penetrateLevelFour; config.penetrateLevelFour = v }
            else -> return 0
        }
        val err = config.validatePenetrateLevels()
        if (err != null) {
            when (level) {
                1 -> config.penetrateLevelOne = old
                2 -> config.penetrateLevelTwo = old
                3 -> config.penetrateLevelThree = old
                4 -> config.penetrateLevelFour = old
            }
            return fail(src, err)
        }
        config.save()
        val name = when (level) {
            1 -> "不完整方块穿透"; 2 -> "密度小方块穿透"
            3 -> "密度中等方块穿透"; 4 -> "密度高方块穿透"
            else -> "穿透"
        }
        src.sendSuccess({ Component.literal("§a${name}格数设置为: ${fmt(v)}") }, true)
        return 1
    }

    private fun setAxisMultiplier(
        ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
        axis: String,
    ): Int {
        val src = ctx.source
        val vs = StringArgumentType.getString(ctx, "value")
        val v = vs.toDoubleOrNull()
            ?: return fail(src, "§c无效的数值: $vs，请输入数字")
        if (v < 0) return fail(src, "§c数值不能为负数")
        if (v > 100) return fail(src, "§c数值最高为 100")

        val old: Double
        val name: String
        when (axis) {
            "y" -> { old = config.yMultiplier; config.yMultiplier = v; name = "Y轴" }
            "x" -> { old = config.xMultiplier; config.xMultiplier = v; name = "X轴" }
            "z" -> { old = config.zMultiplier; config.zMultiplier = v; name = "Z轴" }
            else -> return 0
        }
        val err = config.validateAxisMultipliers()
        if (err != null) {
            when (axis) {
                "y" -> config.yMultiplier = old
                "x" -> config.xMultiplier = old
                "z" -> config.zMultiplier = old
            }
            return fail(src, err)
        }
        config.save()
        src.sendSuccess(
            { Component.literal("§a${name}距离限制倍数已设置为: ${fmt(v)}") }, true
        )
        return 1
    }

    private fun setQuietRange(
        ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
    ): Int {
        val src = ctx.source
        val xs = StringArgumentType.getString(ctx, "x")
        val ys = StringArgumentType.getString(ctx, "y")
        val zs = StringArgumentType.getString(ctx, "z")
        val x = xs.toDoubleOrNull() ?: return fail(src, "§c无效的X值: $xs")
        val y = ys.toDoubleOrNull() ?: return fail(src, "§c无效的Y值: $ys")
        val z = zs.toDoubleOrNull() ?: return fail(src, "§c无效的Z值: $zs")
        if (x < 0 || y < 0 || z < 0) return fail(src, "§c范围值不能为负数")

        config.quietRangeX = x
        config.quietRangeY = y
        config.quietRangeZ = z
        config.save()
        src.sendSuccess(
            { Component.literal("§a小声说话范围已设置为: X=${fmt(x)} Y=${fmt(y)} Z=${fmt(z)}") }, true
        )
        return 1
    }

    private fun setShoutSpread(
        ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
    ): Int {
        val src = ctx.source
        val ms = StringArgumentType.getString(ctx, "multiplier")
        val m = ms.toDoubleOrNull() ?: return fail(src, "§c无效的声音倍率: $ms")
        if (m < 0) return fail(src, "§c声音倍率值不能为负数")

        config.shoutRangeMultiplier = m
        config.save()
        src.sendSuccess(
            { Component.literal("§a大喊声音传播范围倍率已设置为: ×${fmt(m)}") }, true
        )
        return 1
    }

    private fun setShoutPenetrate(
        ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
    ): Int {
        val src = ctx.source
        val ms = StringArgumentType.getString(ctx, "multiplier")
        val m = ms.toDoubleOrNull() ?: return fail(src, "§c无效的穿透倍率: $ms")
        if (m < 0) return fail(src, "§c穿透倍率值不能为负数")

        config.shoutPenetrationMultiplier = m
        config.save()
        src.sendSuccess(
            { Component.literal("§a大喊方块穿透倍率已设置为: ×${fmt(m)}") }, true
        )
        return 1
    }

    private fun fail(src: CommandSourceStack, msg: String): Int {
        src.sendFailure(Component.literal(msg))
        return 0
    }
}
