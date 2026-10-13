package com.example.vision_optimize.gui

import com.example.vision_optimize.client.ClientVisionState
import com.example.vision_optimize.client.VisionKeyBindings
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Tag settings sub-screen for managing access tags and assigning them to players.
 *
 * Layout:
 *   - Top: all existing accessTags shown as clickable buttons (chips)
 *   - Click a tag to select it → shows online player list with tag status
 *   - Players: green ✓ = has tag (click to select, select-key to remove), gray + = no tag (click to add)
 *   - [✕] button per player to quickly remove tag
 *   - Add new tag via input box + "+" button
 *   - Delete tag button removes tag from accessTags entirely
 *   - Select key (R) → removes tag from selected player
 *
 * Vision levels (stages) are NOT set here — they are managed via scoreboard
 * and the block/item vision settings screens.
 */
class TagSettingsScreen(private val parent: Screen) : Screen(Component.literal("特定标签设置")) {
    /** Currently selected tag for management. */
    private var selectedTag: String? = null
    /** Player selected for select-key deletion. */
    private var selectedPlayer: String? = null
    /** New tag name input. */
    private var newTagInput: EditBox? = null
    private val onlinePlayers = mutableListOf<String>()
    /** Players who currently have the selected tag. */
    private val playersWithTag = mutableSetOf<String>()
    /** Current page for tag chips (0-indexed). */
    private var tagPage: Int = 0
    private val tagPageSize: Int = 5

    override fun init() {
        super.init()
        val cx = width / 2
        var y = 12

        // Refresh online player list
        onlinePlayers.clear()
        val server = minecraft?.singleplayerServer
        if (server != null) {
            onlinePlayers.addAll(server.playerList.players.map { it.displayName!!.string })
        } else {
            val connection = minecraft?.connection
            if (connection != null) {
                onlinePlayers.addAll(connection.onlinePlayers.map { it.profile.name })
            }
        }
        val localName = minecraft?.player?.name?.string
        if (localName != null && localName !in onlinePlayers) {
            onlinePlayers.add(localName)
        }

        refreshPlayersWithTag()

        // ===== Row 1: Existing access tags (clickable chips) with pagination =====
        val config = ClientVisionState.cachedConfig
        val accessTags = config?.accessTags ?: emptySet()

        if (accessTags.isNotEmpty()) {
            val sortedTags = accessTags.sorted()
            val tagBtnW = 80
            val tagSpacing = 4
            val arrowBtnW = 16
            val arrowSpacing = 4

            val totalTagPages = ((sortedTags.size - 1) / tagPageSize) + 1
            // Clamp page if deletion reduced pages
            val clampedPage = tagPage.coerceAtMost(totalTagPages - 1)
            if (clampedPage != tagPage) { tagPage = clampedPage }

            val pageTags = sortedTags.drop(tagPage * tagPageSize).take(tagPageSize)
            val hasPrev = tagPage > 0
            val hasNext = tagPage < totalTagPages - 1

            val tagRowW = pageTags.size * (tagBtnW + tagSpacing) - tagSpacing
            var totalWidth = tagRowW
            if (hasPrev) totalWidth += arrowBtnW + arrowSpacing
            if (hasNext) totalWidth += arrowSpacing + arrowBtnW

            var curX = cx - totalWidth / 2

            // ← Previous page button
            if (hasPrev) {
                addRenderableWidget(
                    Button.Builder(
                        Component.literal("←").withStyle { it.withColor(0x000000) }
                    ) {
                        tagPage--
                        rebuildWidgets()
                    }.pos(curX, y).size(arrowBtnW, 18)
                        .tooltip(Tooltip.create(Component.literal("上一页")))
                        .build()
                )
                curX += arrowBtnW + arrowSpacing
            }

            val tagsStartX = curX

            for (tag in pageTags) {
                val isActive = tag == selectedTag
                val color = if (isActive) 0x55FF55 else 0xCCCCCC
                addRenderableWidget(
                    Button.Builder(
                        Component.literal(tag).withStyle { it.withColor(color) }
                    ) {
                        selectedTag = if (isActive) null else tag
                        selectedPlayer = null
                        refreshPlayersWithTag()
                        rebuildWidgets()
                    }.pos(curX, y).size(tagBtnW, 18).build()
                )
                curX += tagBtnW + tagSpacing
            }

            // → Next page button
            if (hasNext) {
                addRenderableWidget(
                    Button.Builder(
                        Component.literal("→").withStyle { it.withColor(0x000000) }
                    ) {
                        tagPage++
                        rebuildWidgets()
                    }.pos(tagsStartX + tagRowW + arrowSpacing, y).size(arrowBtnW, 18)
                        .tooltip(Tooltip.create(Component.literal("下一页")))
                        .build()
                )
            }

            y += 24
        } else {
            addRenderableWidget(
                Button.Builder(
                    Component.literal("(无标签)").withStyle { it.withColor(0x555555) }
                ) {}.pos(cx - 40, y).size(80, 18).build()
            )
            y += 24
        }

        // ===== Row 2: Add new tag =====
        newTagInput = EditBox(font, cx - 80, y, 120, 18, Component.literal("新标签名"))
        addRenderableWidget(newTagInput!!)

        addRenderableWidget(
            Button.Builder(
                Component.literal("+").withStyle { it.withColor(0x55FF55) }
            ) {
                val newTag = newTagInput?.value?.trim()
                if (!newTag.isNullOrEmpty()) {
                    VisionSettingsScreen.sendConfigUpdate("open_tag_add", newTag)
                    // Update local cache immediately for instant UI feedback
                    ClientVisionState.cachedConfig?.accessTags?.add(newTag)
                    ClientVisionState.cachedConfig?.tagVisionEnabled?.add(newTag)
                    selectedTag = newTag
                    newTagInput?.value = ""
                    tagPage = 0
                    refreshPlayersWithTag()
                    rebuildWidgets()
                }
            }.pos(cx + 46, y).size(20, 18).build()
        )
        y += 28

        // ===== Row 3+: Player list for selected tag =====
        val tag = selectedTag
        if (tag != null && tag.isNotEmpty()) {
            if (onlinePlayers.isNotEmpty()) {
                for (playerName in onlinePlayers) {
                    val hasTag = playerName in playersWithTag
                    val isSelected = playerName == selectedPlayer

                    val color = when {
                        isSelected -> 0xFFFF55  // Yellow = selected for deletion
                        hasTag -> 0x55FF55      // Green = has the tag
                        else -> 0xCCCCCC        // Gray = no tag
                    }
                    val statusIcon = if (hasTag) "✓" else "+"
                    val label = "$statusIcon $playerName" + if (isSelected) " ◀" else ""

                    // Player tag button
                    addRenderableWidget(
                        Button.Builder(
                            Component.literal(label).withStyle { it.withColor(color) }
                        ) {
                            if (hasTag) {
                                // Has tag → select for deletion (click again to confirm remove)
                                if (isSelected) {
                                    VisionSettingsScreen.sendConfigUpdate("player_tag_remove", "$playerName|$tag")
                                    // Update local cache immediately for instant UI feedback
                                    ClientVisionState.cachedConfig?.let { cfg ->
                                        cfg.playerTags[playerName]?.remove(tag)
                                        if (cfg.playerTags[playerName].isNullOrEmpty()) {
                                            cfg.playerTags.remove(playerName)
                                        }
                                    }
                                    playersWithTag.remove(playerName)
                                    selectedPlayer = null
                                } else {
                                    selectedPlayer = playerName
                                }
                            } else {
                                // No tag → ADD tag to player
                                VisionSettingsScreen.sendConfigUpdate("player_tag_add", "$playerName|$tag")
                                // Update local cache immediately for instant UI feedback
                                ClientVisionState.cachedConfig?.playerTags?.getOrPut(playerName) { mutableSetOf() }?.add(tag)
                                playersWithTag.add(playerName)
                                selectedPlayer = null
                            }
                            rebuildWidgets()
                        }.pos(cx - 105, y).size(180, 18).build()
                    )

                    // Small [✕] remove button for players who have the tag
                    if (hasTag) {
                        addRenderableWidget(
                            Button.Builder(
                                Component.literal("✕").withStyle { it.withColor(0xFF5555) }
                            ) {
                                VisionSettingsScreen.sendConfigUpdate("player_tag_remove", "$playerName|$tag")
                                // Update local cache immediately for instant UI feedback
                                ClientVisionState.cachedConfig?.let { cfg ->
                                    cfg.playerTags[playerName]?.remove(tag)
                                    if (cfg.playerTags[playerName].isNullOrEmpty()) {
                                        cfg.playerTags.remove(playerName)
                                    }
                                }
                                playersWithTag.remove(playerName)
                                selectedPlayer = null
                                rebuildWidgets()
                            }.pos(cx + 80, y).size(16, 18).build()
                        )
                    }

                    y += 20
                }
            }

            y += 5

            // Vision toggle button for the selected tag
            val visionEnabled = config?.tagVisionEnabled?.contains(tag) == true
            val visionToggleLabel = if (visionEnabled) "幻视: ✓ 开启" else "幻视: ✕ 关闭"
            val visionToggleColor = if (visionEnabled) 0x55FF55 else 0xFF5555
            addRenderableWidget(
                Button.Builder(
                    Component.literal(visionToggleLabel).withStyle { it.withColor(visionToggleColor) }
                ) {
                    if (visionEnabled) {
                        // Disable vision for this tag
                        VisionSettingsScreen.sendConfigUpdate("tag_vision_disable", tag)
                        ClientVisionState.cachedConfig?.tagVisionEnabled?.remove(tag)
                    } else {
                        // Enable vision for this tag
                        VisionSettingsScreen.sendConfigUpdate("tag_vision_enable", tag)
                        ClientVisionState.cachedConfig?.tagVisionEnabled?.add(tag)
                    }
                    rebuildWidgets()
                }.pos(cx - 110, y).size(220, 18).build()
            )
            y += 24

            // Delete tag button
            val playerCount = playersWithTag.size
            addRenderableWidget(
                Button.Builder(
                    Component.literal("删除标签 [$tag]${if (playerCount > 0) " ($playerCount 名玩家)" else ""}")
                        .withStyle { it.withColor(0xFF5555) }
                ) {
                    VisionSettingsScreen.sendConfigUpdate("open_tag_remove", tag)
                    // Update local cache immediately for instant UI feedback
                    ClientVisionState.cachedConfig?.let { cfg ->
                        cfg.accessTags.remove(tag)
                        cfg.tagVisionEnabled.remove(tag)
                        // Also remove this tag from all player assignments
                        for ((_, tags) in cfg.playerTags) {
                            tags.remove(tag)
                        }
                        // Clean up empty entries
                        cfg.playerTags.entries.removeAll { it.value.isEmpty() }
                    }
                    selectedTag = null
                    selectedPlayer = null
                    tagPage = 0
                    playersWithTag.clear()
                    rebuildWidgets()
                }.pos(cx - 110, y).size(220, 18).build()
            )
            y += 24
        }

        y += 5

        // ===== Bottom buttons =====
        addRenderableWidget(
            Button.Builder(Component.literal("返回")) {
                minecraft?.setScreen(parent)
            }.pos(cx - 50, y).size(100, 20).build()
        )
    }

    private fun refreshPlayersWithTag() {
        playersWithTag.clear()
        val tag = selectedTag ?: return
        val config = ClientVisionState.cachedConfig ?: return
        for ((playerName, tags) in config.playerTags) {
            if (tag in tags) {
                playersWithTag.add(playerName)
            }
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // Select key (R) → delete tag from selected player
        if (VisionKeyBindings.selectKey.matches(keyCode, scanCode)) {
            val player = selectedPlayer
            val tag = selectedTag
            if (player != null && tag != null && tag.isNotEmpty()) {
                VisionSettingsScreen.sendConfigUpdate("player_tag_remove", "$player|$tag")
                // Update local cache immediately for instant UI feedback
                ClientVisionState.cachedConfig?.let { cfg ->
                    cfg.playerTags[player]?.remove(tag)
                    if (cfg.playerTags[player].isNullOrEmpty()) {
                        cfg.playerTags.remove(player)
                    }
                }
                playersWithTag.remove(player)
                selectedPlayer = null
                rebuildWidgets()
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(guiGraphics, mouseX, mouseY, delta)
        val cx = this.width / 2

        guiGraphics.drawCenteredString(font, title, cx, 2, 0xFFFFFFFF.toInt())

        val config = ClientVisionState.cachedConfig

        // Status line
        val tagCount = config?.accessTags?.size ?: 0
        val visionTagCount = config?.tagVisionEnabled?.size ?: 0
        val modeStr = when (config?.mode) {
            com.example.vision_optimize.config.AccessMode.ALL_MEMBERS -> "全员模式"
            com.example.vision_optimize.config.AccessMode.TAG_MEMBER -> "标签模式"
            else -> "未知"
        }
        guiGraphics.drawCenteredString(
            font, "模式: $modeStr | 权限标签: $tagCount | 幻视标签: $visionTagCount",
            cx, 11, 0xFFAAAAAA.toInt()
        )

        // Divider / hint
        if (selectedTag != null) {
            val tagVisionOn = config?.tagVisionEnabled?.contains(selectedTag) == true
            val visionStatus = if (tagVisionOn) "幻视✓" else "幻视✕"
            guiGraphics.drawCenteredString(
                font, "── 管理标签: [$selectedTag] $visionStatus | 点击+添加 点击✓选中 选择键删除 ──",
                cx, 62, 0xFFFF55.toInt()
            )
        } else {
            guiGraphics.drawCenteredString(
                font, "点击上方标签进行管理 | 输入新标签名点击 + 创建",
                cx, 62, 0xFFAAAAAA.toInt()
            )
        }

        // Bottom summary
        val playerTagsMap = config?.playerTags ?: emptyMap()
        val summary = playerTagsMap
            .filter { it.value.isNotEmpty() }
            .flatMap { (player, playerTags) -> playerTags.map { "$player[$it]" } }
            .joinToString(", ")
        if (summary.isNotEmpty()) {
            guiGraphics.drawCenteredString(
                font, "已分配: $summary",
                cx, this.height - 14, 0xFF55FF55.toInt()
            )
        } else {
            guiGraphics.drawCenteredString(
                font, "暂无玩家标签分配",
                cx, this.height - 14, 0x555555.toInt()
            )
        }

        // Select key hint
        if (selectedPlayer != null) {
            guiGraphics.drawCenteredString(
                font, "按选择键(R)删除 [$selectedPlayer] 的标签 [$selectedTag]",
                cx, this.height - 28, 0xFFFF55.toInt()
            )
        }
    }
}
