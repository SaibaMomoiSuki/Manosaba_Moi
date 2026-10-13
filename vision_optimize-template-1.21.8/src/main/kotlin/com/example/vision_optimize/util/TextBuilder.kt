package com.example.vision_optimize.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import kotlin.random.Random

/**
 * Centralized text component builder for all mod display strings.
 * Colors follow the spec:
 * - Yellow: time descriptions, no-previous-owner message
 * - Green: connectors ("和", "、"), level 2 "曾近经过这里", level 3 main text
 * - Cyan: level 2 "最近经过这里"
 * - Red: level 2 "在我附近", death interference
 * - Dark red: death interference item vision
 * - Gray: item tooltip hint
 */
object TextBuilder {

    fun playerName(name: String): MutableComponent = Component.literal(name)

    /**
     * Build a colored player name based on manosaba character tags.
     * Checks config.playerTags (client or server side).
     * If manosaba is enabled and the player has a matching character tag,
     * returns the colored character display name.
     * Otherwise returns a plain white name.
     *
     * NOTE: On the server side, prefer ManosabaCharacters.resolveManosabaNameServerColored()
     * which also checks vanilla /tag scoreboard tags.
     */
    fun manosabaColoredName(playerName: String, config: com.example.vision_optimize.config.ModConfig?): MutableComponent {
        if (config == null || !config.manosabaEnabled) return playerName(playerName)
        val playerTags = config.playerTags[playerName] ?: return playerName(playerName)
        val matchingTag = ManosabaCharacters.findMatchingTag(playerTags) ?: return playerName(playerName)
        return ManosabaCharacters.buildColoredName(matchingTag) ?: playerName(playerName)
    }

    fun yellow(text: String): MutableComponent = Component.literal(text).withStyle(ChatFormatting.YELLOW)
    fun green(text: String): MutableComponent = Component.literal(text).withStyle(ChatFormatting.GREEN)
    fun cyan(text: String): MutableComponent = Component.literal(text).withStyle(ChatFormatting.AQUA)
    fun red(text: String): MutableComponent = Component.literal(text).withStyle(ChatFormatting.RED)
    fun darkRed(text: String): MutableComponent = Component.literal(text).withStyle(ChatFormatting.DARK_RED)
    fun gray(text: String): MutableComponent = Component.literal(text).withStyle(ChatFormatting.GRAY)
    fun white(text: String): MutableComponent = Component.literal(text).withStyle(ChatFormatting.WHITE)

    // ===== Block Vision Level 1 =====

    /**
     * Single player: "<name>不久前单独经过这里..."
     */
    fun blockL1Single(name: String, config: com.example.vision_optimize.config.ModConfig? = null): Component {
        return Component.empty()
            .append(manosabaColoredName(name, config))
            .append(yellow("不久前单独经过这里..."))
    }

    /**
     * Group: "<name1>和<name2>不久前一起经过这里..."
     * Additional names joined with "、"
     */
    fun blockL1Group(names: List<String>, config: com.example.vision_optimize.config.ModConfig? = null): Component {
        val result = Component.empty()
        for (i in names.indices) {
            if (i > 0 && i == names.size - 1) {
                result.append(green("和"))
            } else if (i > 0) {
                result.append(green("、"))
            }
            result.append(manosabaColoredName(names[i], config))
        }
        result.append(yellow("不久前一起经过这里..."))
        return result
    }

    // ===== Block Vision Level 2 =====

    /**
     * "<names>在我附近" — RED
     */
    fun blockL2NearMe(names: List<String>, config: com.example.vision_optimize.config.ModConfig? = null): Component {
        return Component.empty()
            .append(joinNames(names, config))
            .append(red("在我附近"))
    }

    /**
     * "<names>不久前经过这里..." — YELLOW
     */
    fun blockL2Recently(names: List<String>, config: com.example.vision_optimize.config.ModConfig? = null): Component {
        return Component.empty()
            .append(joinNames(names, config))
            .append(yellow("不久前经过这里..."))
    }

    /**
     * "<names>最近经过这里..." — CYAN
     */
    fun blockL2Lately(names: List<String>, config: com.example.vision_optimize.config.ModConfig? = null): Component {
        return Component.empty()
            .append(joinNames(names, config))
            .append(cyan("最近经过这里..."))
    }

    /**
     * "<names>曾近经过这里..." — GREEN
     */
    fun blockL2Once(names: List<String>, config: com.example.vision_optimize.config.ModConfig? = null): Component {
        return Component.empty()
            .append(joinNames(names, config))
            .append(green("曾近经过这里..."))
    }

    // ===== Block Vision Level 3 =====

    /**
     * "<name>在<time>秒之前经过这里" — name original, time yellow, rest green
     */
    fun blockL3Entry(name: String, seconds: Long, config: com.example.vision_optimize.config.ModConfig? = null): Component {
        return Component.empty()
            .append(manosabaColoredName(name, config))
            .append(green("在"))
            .append(yellow(seconds.toString()))
            .append(green("秒之前经过这里"))
    }

    // ===== Block Vision No Results =====

    /**
     * "似乎没人经过这里" — WHITE
     */
    fun blockNoResults(): MutableComponent = white("似乎没人经过这里")

    /**
     * "你感到不适,幻视失败了..." — RED (action bar)
     */
    fun blockVisionFail(): MutableComponent = red("你感到不适,幻视失败了...")

    // ===== Item Vision =====

    /**
     * "先前使用者[由最近往后]" header — YELLOW
     */
    fun itemVisionHeader(): MutableComponent = yellow("先前使用者[由最近往后]")

    /**
     * "并没有人使用过此物品" — WHITE
     */
    fun itemNoPrevious(): MutableComponent = white("并没有人使用过此物品")

    /**
     * "好像因为某种原因...幻视被干扰了..." — DARK RED
     */
    fun itemDeathInterference(): MutableComponent = darkRed("好像因为某种原因...幻视被干扰了...")

    /**
     * "幻视后有什么让你作呕..." — RED (nausea warning after death)
     */
    fun itemDeathNausea(): MutableComponent = red("幻视后有什么让你作呕...")

    /**
     * "幻视后一片黑暗...看来是幻视失败了..." — YELLOW (action bar)
     */
    fun itemVisionFail(): MutableComponent = yellow("幻视后一片黑暗...看来是幻视失败了...")

    /**
     * "[按住特定键进行幻视]" tooltip — GRAY
     */
    fun itemTooltipHint(): MutableComponent = gray("[按住特定键进行幻视]")

    /**
     * "再缓缓再幻视这东西吧" — GREEN (action bar, fail cooldown retry hint)
     */
    fun visionRetryHint(): MutableComponent = green("再缓缓再幻视这东西吧")

    /**
     * "似乎暂时使用不了..." — YELLOW (shown when a player is silenced and cannot use vision)
     */
    fun visionSilenced(): MutableComponent = yellow("似乎暂时使用不了...")

    // ===== Vision Level-Up Notifications (Green) =====

    /** 人物幻视 level1→level2 / level1→level3 */
    fun playerVisionLevelUpEnhanced(): MutableComponent = green("能力增强了...?")
    fun playerVisionLevelUpKeyHint(): MutableComponent =
        Component.empty()
            .append(green("好像可以用"))
            .append(Component.keybind("key.vision_optimize.player_select"))
            .append(green("对别人进行隔空幻视了..."))

    /** 人物幻视 level2→level3 */
    fun playerVisionLevelUp2to3(): MutableComponent = green("我的能力好像增强了...到时候试试吧")

    /** 物品幻视 level1→level2 */
    fun itemVisionLevelUp1to2(): MutableComponent = green("物品上的残留物好像停留时间更长了")

    /** 方块幻视 level1→level2 */
    fun blockVisionLevelUp1to2(): MutableComponent = green("别人在路上和墙上的印记好像更明显了")

    /** 方块幻视 level1→level3 / level2→level3 */
    fun blockVisionLevelUpTo3_line1(): MutableComponent = green("那是别人的影子吗...?")
    fun blockVisionLevelUpTo3_line2(): MutableComponent = green("不对好像周边的物品也变了")
    fun blockVisionLevelUpTo3_line3(): MutableComponent = green("可以大概判定他们经过的时间了...")

    // ===== Vision Level-Down Notification (Red, all types) =====

    fun visionLevelDown(): MutableComponent = red("能力好像减弱了...")

    // ===== Player Vision Look Warnings (L2/L3) =====

    /** P-key front: 60% red "\<caster\>好像默默的盯着你,眼神让你发寒" */
    fun playerVisionPKeyWarningFront(casterName: net.minecraft.network.chat.Component): MutableComponent =
        net.minecraft.network.chat.Component.empty()
            .append(casterName)
            .append(red("好像默默的盯着你,眼神让你发寒"))

    /** P-key behind: 45% yellow "你感到一道冰冷的视线盯着你..." */
    fun playerVisionPKeyWarningBehind(): MutableComponent =
        yellow("你感到一道冰冷的视线盯着你...")

    /** V-key front: 70% red "\<caster\>用一种古怪的眼神盯着你" */
    fun playerVisionVKeyWarningFront(casterName: net.minecraft.network.chat.Component): MutableComponent =
        net.minecraft.network.chat.Component.empty()
            .append(casterName)
            .append(red("用一种古怪的眼神盯着你"))

    /** V-key behind: 25% yellow "你感到背后传来喘息的热气..." */
    fun playerVisionVKeyWarningBehind(): MutableComponent =
        yellow("你感到背后传来喘息的热气...")

    // ===== Help =====

    fun creditLine(): MutableComponent = gray("This mod By.Momoi")

    // ===== Player Vision =====

    /** Red action bar: "\<caster\>的手向你扑来" */
    fun playerVisionInViewWarning(casterName: Component): MutableComponent =
        Component.empty()
            .append(casterName)
            .append(red("的手向你扑来"))

    /** Red action bar: "幻视被阻挠了..." */
    fun playerVisionFail(): MutableComponent =
        Component.literal("幻视被阻挠了...").withStyle(ChatFormatting.RED)

    /** Yellow chat: "好像被谁轻握住了手臂..." */
    fun playerVisionTargetSuccess(): MutableComponent =
        Component.literal("好像被谁轻握住了手臂...").withStyle(ChatFormatting.YELLOW)

    /** Gold chat with colored tag: "\<target\>的魔法是[\<tag\>]" */
    fun playerVisionBroadcast(targetName: Component, tagText: String, tagColor: Int): MutableComponent {
        return Component.empty()
            .append(targetName)
            .append(Component.literal("的魔法是[").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(tagText).withStyle { it.withColor(tagColor) })
            .append(Component.literal("]").withStyle(ChatFormatting.GOLD))
    }

    /** Red chat: "\<caster\>的手向你的手臂伸来" */
    fun playerVisionDiscovered(casterName: Component): MutableComponent =
        Component.empty()
            .append(casterName)
            .append(red("的手向你的手臂伸来"))

    /** Red action bar: "你被\<target\>发现了!" */
    fun playerVisionDiscoveredCaster(targetName: Component): MutableComponent =
        Component.empty()
            .append(red("你被"))
            .append(targetName)
            .append(red("发现了!"))

    /** Yellow chat third-party: "\<caster\>的手快速的向\<target\>抓去...!" */
    fun playerVisionThirdPartyRaid(casterName: Component, targetName: Component): MutableComponent =
        Component.empty()
            .append(casterName)
            .append(yellow("的手快速的向"))
            .append(targetName)
            .append(yellow("抓去...!"))

    /** Blue chat third-party: "\<caster\>的手好像在向\<target\>伸过去...?" */
    fun playerVisionThirdPartySneak(casterName: Component, targetName: Component): MutableComponent =
        Component.empty()
            .append(casterName)
            .append(Component.literal("的手好像在向").withStyle(ChatFormatting.BLUE))
            .append(targetName)
            .append(Component.literal("伸过去...?").withStyle(ChatFormatting.BLUE))

    /**
     * Result of a player vision success message with random body part.
     * @param component The formatted chat component.
     * @param shouldDamage Whether the combination (neck + grab action) triggers damage-over-time on the target.
     */
    data class PlayerVisionSuccessMessage(
        val component: MutableComponent,
        val shouldDamage: Boolean
    )

    /**
     * Random red chat for in-view success: "\<caster\>[抓住了/握住了]你的[左臂/右臂/脖子/左手/右手]"
     * "脖子" has reduced probability (1/9 vs 1/5 for other parts).
     * Neck damage triggers for both actions (both are grab-type).
     */
    fun playerVisionInViewSuccess(casterName: Component): PlayerVisionSuccessMessage {
        val actions = arrayOf("抓住了", "握住了")
        // Weighted: each non-neck part appears twice, neck appears once → 1/9 chance
        val bodyParts = arrayOf("左臂", "右臂", "左手", "右手", "左臂", "右臂", "左手", "右手", "脖子")

        val action = actions[Random.nextInt(actions.size)]
        val bp = bodyParts[Random.nextInt(bodyParts.size)]

        // Both actions are grab-type → neck always triggers damage
        val shouldDamage = bp == "脖子"

        val component = Component.empty()
            .append(casterName)
            .append(red("${action}你的${bp}"))
        return PlayerVisionSuccessMessage(component, shouldDamage)
    }

    /**
     * Random red chat for discovery success: "\<caster\>[顺势/快速的/不觉间][抓住了/握住了/摸到了/碰到]你的[左臂/右臂/左手/右手/脖子/脸部]"
     * "脖子" has reduced probability (1/11 vs 1/6 for other parts).
     * Neck damage only triggers for grab-type actions (抓住了/握住了), not touch-type (摸到了/碰到).
     */
    fun playerVisionDiscoverySuccess(casterName: Component): PlayerVisionSuccessMessage {
        val actions1 = arrayOf("顺势", "快速的", "不觉间")
        val actions2 = arrayOf("抓住了", "握住了", "摸到了", "碰到")
        // Weighted: each non-neck part appears twice, neck appears once → 1/11 chance
        val bodyParts = arrayOf("左臂", "右臂", "左手", "右手", "脸部", "左臂", "右臂", "左手", "右手", "脸部", "脖子")

        val a1 = actions1[Random.nextInt(actions1.size)]
        val a2 = actions2[Random.nextInt(actions2.size)]
        val bp = bodyParts[Random.nextInt(bodyParts.size)]

        // Only grab-type actions (抓住了/握住了) trigger neck damage
        val isGrabAction = a2 == "抓住了" || a2 == "握住了"
        val shouldDamage = bp == "脖子" && isGrabAction

        val component = Component.empty()
            .append(casterName)
            .append(red("${a1}${a2}你的${bp}"))
        return PlayerVisionSuccessMessage(component, shouldDamage)
    }

    /** Yellow chat: "你打断了\<caster\>伸手的动作..." */
    fun playerVisionInterrupted(casterName: Component): MutableComponent =
        Component.empty()
            .append(yellow("你打断了"))
            .append(casterName)
            .append(yellow("伸手的动作..."))

    /** Yellow action bar: "你感受到窒息感..." */
    fun playerVisionNeckSuffocationStart(): MutableComponent =
        yellow("你感受到窒息感...")

    /** Green chat: "强烈的窒息感缓缓消失..." */
    fun playerVisionNeckSuffocationEnd(): MutableComponent =
        green("强烈的窒息感缓缓消失...")

    /**
     * Join player names with "、" separator
     */
    private fun joinNames(names: List<String>, config: com.example.vision_optimize.config.ModConfig? = null): MutableComponent {
        val result = Component.empty()
        for (i in names.indices) {
            if (i > 0) result.append(green("、"))
            result.append(manosabaColoredName(names[i], config))
        }
        return result
    }
}
