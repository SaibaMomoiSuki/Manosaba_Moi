package com.example.command

import com.example.Mano_effect
import com.example.config.ModConfig
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * /mano_effect falldamage true/false 命令
 * 控制摔落伤害是否启用。当为false时，摔落将触发自定义受伤效果。
 */
object ManoEffectCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = Commands.literal("mano_effect")
            .then(
                Commands.literal("falldamage")
                    .then(
                        Commands.literal("true")
                            .executes { ctx -> setFallDamage(ctx, true) }
                    )
                    .then(
                        Commands.literal("false")
                            .executes { ctx -> setFallDamage(ctx, false) }
                    )
            )

        dispatcher.register(root)
    }

    private fun setFallDamage(ctx: CommandContext<CommandSourceStack>, enabled: Boolean): Int {
        Mano_effect.fallDamageEnabled = enabled
        ModConfig.fallDamageEnabled = enabled
        ModConfig.save()
        val status = if (enabled) "enabled" else "disabled"
        val chineseStatus = if (enabled) "§a启用" else "§c关闭"
        ctx.source.sendSuccess(
            { Component.literal("§e[mano_effect] §r摔落伤害: $chineseStatus (§7fall damage $status§r)") },
            true
        )
        return 1
    }
}
