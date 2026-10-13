package com.example.vision_optimize.gui

import com.example.vision_optimize.client.ClientVisionState
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Player vision tag settings sub-screen.
 * Manages player vision text tags with color settings and player assignment.
 * Pattern similar to TagSettingsScreen but with color picker and pagination.
 */
class PlayerVisionTagSettingsScreen(private val parent: Screen) : Screen(Component.literal("幻视文本设置")) {

    /** Currently selected tag for management. */
    private var selectedTag: String? = null
    /** New tag name input. */
    private var newTagInput: EditBox? = null
    /** Hex color input. */
    private var hexColorInput: EditBox? = null
    /** Currently selected color index for new tag (0-15 = Minecraft colors, -1 = custom hex). */
    private var selectedColorIndex: Int = 15 // WHITE default
    private var customHexColor: Int = 0xFFFFFFFF.toInt()
    /** Real-time preview color from hex input (ARGB). */
    private var previewColor: Int = 0xFFFFFFFF.toInt()

    private val onlinePlayers = mutableListOf<String>()
    /** Players who currently have the selected tag. */
    private val playersWithTag = mutableSetOf<String>()
    /** Current page for player list (0-indexed). */
    private var playerPage: Int = 0
    private val playerPageSize: Int = 5
    /** Current page for tag chips (0-indexed). */
    private var tagPage: Int = 0
    private val tagPageSize: Int = 5

    // Color grid position tracking for click detection
    private var colorGridX: Int = 0
    private var colorGridStartY: Int = 0
    private val colorBoxSize: Int = 16
    private val colorGridSpacing: Int = 2

    // Minecraft 16 colors with hex values
    private val mcColors = listOf(
        0xFF000000.toInt(),  // BLACK
        0xFF0000AA.toInt(),  // DARK_BLUE
        0xFF00AA00.toInt(),  // DARK_GREEN
        0xFF00AAAA.toInt(),  // DARK_AQUA
        0xFFAA0000.toInt(),  // DARK_RED
        0xFFAA00AA.toInt(),  // DARK_PURPLE
        0xFFFFAA00.toInt(),  // GOLD
        0xFFAAAAAA.toInt(),  // GRAY
        0xFF555555.toInt(),  // DARK_GRAY
        0xFF5555FF.toInt(),  // BLUE
        0xFF55FF55.toInt(),  // GREEN
        0xFF55FFFF.toInt(),  // AQUA
        0xFFFF5555.toInt(),  // RED
        0xFFFF55FF.toInt(),  // LIGHT_PURPLE
        0xFFFFFF55.toInt(),  // YELLOW
        0xFFFFFFFF.toInt()   // WHITE
    )

    override fun init() {
        super.init()
        val cx = width / 2
        var y = 14

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

        // ===== Row 1: Existing tag chips with pagination =====
        val config = ClientVisionState.cachedConfig
        val pvTags = config?.playerVisionTags ?: emptyMap()

        if (pvTags.isNotEmpty()) {
            val sortedTags = pvTags.keys.sorted()
            val tagBtnW = 90
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
            if (curX < 5) curX = 5

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
                val tagColor = pvTags[tag] ?: 0xFFFFFFFF.toInt()
                val displayColor = if (isActive) 0xFF55FF55.toInt() else tagColor
                addRenderableWidget(
                    Button.Builder(
                        Component.literal(tag).withStyle { it.withColor(displayColor) }
                    ) {
                        selectedTag = if (isActive) null else tag
                        playerPage = 0
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
        newTagInput = EditBox(font, cx - 100, y, 100, 18, Component.literal("新标签名"))
        addRenderableWidget(newTagInput!!)

        hexColorInput = EditBox(font, cx + 10, y, 70, 18, Component.literal("#FFFFFF"))
        hexColorInput?.value = "#FFFFFF"
        hexColorInput?.setResponder { hex ->
            parseHexColor(hex)?.let { previewColor = it }
        }
        addRenderableWidget(hexColorInput!!)

        addRenderableWidget(
            Button.Builder(
                Component.literal("+").withStyle { it.withColor(0x55FF55) }
            ) {
                val newTag = newTagInput?.value?.trim()
                if (!newTag.isNullOrEmpty()) {
                    // Determine color from hex input or selected MC color
                    val colorInt = parseHexColor(hexColorInput?.value?.trim() ?: "") ?: mcColors[selectedColorIndex]
                    VisionSettingsScreen.sendConfigUpdate("pv_tag_add", "$newTag|$colorInt")
                    // Update local cache
                    ClientVisionState.cachedConfig?.playerVisionTags?.put(newTag, colorInt)
                    selectedTag = newTag
                    newTagInput?.value = ""
                    previewColor = 0xFFFFFFFF.toInt()
                    hexColorInput?.value = "#FFFFFF"
                    tagPage = 0
                    playerPage = 0
                    refreshPlayersWithTag()
                    rebuildWidgets()
                }
            }.pos(cx + 102, y).size(20, 18).build()
        )
        y += 24

        // ===== Color grid positions (drawn in render, clicks handled in mouseClicked) =====
        colorGridX = cx - 75
        colorGridStartY = y
        y = colorGridStartY + 2 * (colorBoxSize + colorGridSpacing) + 8

        // ===== Row 4+: Player list for selected tag (paginated) =====
        val tag = selectedTag
        if (tag != null && tag.isNotEmpty()) {
            val totalPages = ((onlinePlayers.size - 1) / playerPageSize) + 1
            val startIdx = playerPage * playerPageSize
            val endIdx = minOf(startIdx + playerPageSize, onlinePlayers.size)
            val pagePlayers = onlinePlayers.subList(startIdx, endIdx)

            for (playerName in pagePlayers) {
                val hasTag = playerName in playersWithTag
                val isAssignedToOther = ClientVisionState.cachedConfig?.let { cfg ->
                    cfg.playerVisionPlayerTags.containsKey(playerName) &&
                    cfg.playerVisionPlayerTags[playerName] != tag
                } ?: false
                val tagTakenByOther = ClientVisionState.cachedConfig?.let { cfg ->
                    cfg.playerVisionPlayerTags.containsValue(tag) &&
                    cfg.playerVisionPlayerTags.entries.any { it.value == tag && it.key != playerName }
                } ?: false

                val canAssign = !isAssignedToOther && !tagTakenByOther
                val canRemove = hasTag

                val color = when {
                    hasTag -> 0x55FF55
                    !canAssign -> 0x555555
                    else -> 0xCCCCCC
                }
                val statusIcon = if (hasTag) "✓" else "+"
                val label = "$statusIcon $playerName"

                val playerBtn = Button.Builder(
                    Component.literal(label).withStyle { it.withColor(color) }
                ) {
                    if (hasTag) {
                        VisionSettingsScreen.sendConfigUpdate("pv_player_tag_clear", playerName)
                        ClientVisionState.cachedConfig?.playerVisionPlayerTags?.remove(playerName)
                        playersWithTag.remove(playerName)
                    } else if (canAssign) {
                        VisionSettingsScreen.sendConfigUpdate("pv_player_tag_add", "$playerName|$tag")
                        ClientVisionState.cachedConfig?.playerVisionPlayerTags?.put(playerName, tag)
                        playersWithTag.add(playerName)
                    }
                    rebuildWidgets()
                }.pos(cx - 110, y).size(180, 18).build()
                playerBtn.active = canAssign || canRemove
                addRenderableWidget(playerBtn)

                if (hasTag) {
                    addRenderableWidget(
                        Button.Builder(
                            Component.literal("✕").withStyle { it.withColor(0xFF5555) }
                        ) {
                            VisionSettingsScreen.sendConfigUpdate("pv_player_tag_clear", playerName)
                            ClientVisionState.cachedConfig?.playerVisionPlayerTags?.remove(playerName)
                            playersWithTag.remove(playerName)
                            rebuildWidgets()
                        }.pos(cx + 80, y).size(16, 18).build()
                    )
                }

                y += 20
            }

            // Pagination buttons
            if (totalPages > 1) {
                y += 2
                if (playerPage > 0) {
                    addRenderableWidget(
                        Button.Builder(
                            Component.literal("←").withStyle { it.withColor(0x000000) }
                        ) {
                            playerPage--
                            rebuildWidgets()
                        }.pos(cx - 50, y).size(16, 16)
                            .tooltip(Tooltip.create(Component.literal("上一页")))
                            .build()
                    )
                }
                addRenderableWidget(
                    Button.Builder(
                        Component.literal("${playerPage + 1}/$totalPages").withStyle { it.withColor(0xAAAAAA) }
                    ) {}.pos(cx - 20, y).size(40, 16).build()
                )
                if (playerPage < totalPages - 1) {
                    addRenderableWidget(
                        Button.Builder(
                            Component.literal("→").withStyle { it.withColor(0x000000) }
                        ) {
                            playerPage++
                            rebuildWidgets()
                        }.pos(cx + 25, y).size(16, 16)
                            .tooltip(Tooltip.create(Component.literal("下一页")))
                            .build()
                    )
                }
                y += 22
            }

            y += 5

            // Delete tag button
            val playerCount = playersWithTag.size
            addRenderableWidget(
                Button.Builder(
                    Component.literal("删除标签 [$tag]${if (playerCount > 0) " ($playerCount 名玩家)" else ""}")
                        .withStyle { it.withColor(0xFF5555) }
                ) {
                    VisionSettingsScreen.sendConfigUpdate("pv_tag_remove", tag)
                    ClientVisionState.cachedConfig?.let { cfg ->
                        cfg.playerVisionTags.remove(tag)
                        val assignedPlayer = cfg.playerVisionPlayerTags.entries.find { it.value == tag }?.key
                        if (assignedPlayer != null) {
                            cfg.playerVisionPlayerTags.remove(assignedPlayer)
                        }
                    }
                    selectedTag = null
                    tagPage = 0
                    playerPage = 0
                    playersWithTag.clear()
                    rebuildWidgets()
                }.pos(cx - 110, y).size(220, 18).build()
            )
            y += 24
        }

        y += 5

        // ===== Bottom buttons =====
        addRenderableWidget(
            Button.Builder(Component.literal("应用设置")) {
                minecraft?.setScreen(parent)
            }.pos(cx - 110, y).size(100, 20).build()
        )

        addRenderableWidget(
            Button.Builder(Component.literal("取消")) {
                minecraft?.setScreen(parent)
            }.pos(cx + 10, y).size(100, 20).build()
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Check color grid clicks first
        for (i in mcColors.indices) {
            val col = i % 8
            val row = i / 8
            val bx = colorGridX + col * (colorBoxSize + colorGridSpacing)
            val by = colorGridStartY + row * (colorBoxSize + colorGridSpacing)

            if (mouseX >= bx && mouseX < bx + colorBoxSize &&
                mouseY >= by && mouseY < by + colorBoxSize
            ) {
                selectedColorIndex = i
                val colorInt = mcColors[i]
                val hex = String.format("#%06X", colorInt and 0xFFFFFF)
                hexColorInput?.value = hex
                customHexColor = colorInt
                previewColor = colorInt
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun refreshPlayersWithTag() {
        playersWithTag.clear()
        val tag = selectedTag ?: return
        val config = ClientVisionState.cachedConfig ?: return
        for ((playerName, playerTag) in config.playerVisionPlayerTags) {
            if (playerTag == tag) {
                playersWithTag.add(playerName)
            }
        }
    }

    private fun parseHexColor(hex: String): Int? {
        val cleaned = hex.removePrefix("#").trim()
        if (cleaned.length != 6) return null
        return try {
            (0xFF000000.toInt() or Integer.parseInt(cleaned, 16))
        } catch (_: Exception) {
            null
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(guiGraphics, mouseX, mouseY, delta)
        val cx = this.width / 2

        guiGraphics.drawCenteredString(font, title, cx, 2, 0xFFFFFFFF.toInt())

        // Status line
        val config = ClientVisionState.cachedConfig
        val tagCount = config?.playerVisionTags?.size ?: 0
        guiGraphics.drawCenteredString(
            font, "人物幻视标签: $tagCount 个",
            cx, 12, 0xFFAAAAAA.toInt()
        )

        // Draw color grid (4 rows × 8 columns = 16 Minecraft colors)
        for (i in mcColors.indices) {
            val col = i % 8
            val row = i / 8
            val bx = colorGridX + col * (colorBoxSize + colorGridSpacing)
            val by = colorGridStartY + row * (colorBoxSize + colorGridSpacing)
            val colorInt = mcColors[i]
            val isSelected = selectedColorIndex == i

            // Fill the color square
            guiGraphics.fill(bx, by, bx + colorBoxSize, by + colorBoxSize, colorInt)

            // Draw selection border (white outline) or subtle border
            if (isSelected) {
                guiGraphics.renderOutline(bx - 1, by - 1, colorBoxSize + 2, colorBoxSize + 2, 0xFFFFFFFF.toInt())
            } else {
                guiGraphics.renderOutline(bx, by, colorBoxSize, colorBoxSize, 0xFF888888.toInt())
            }
        }

        // Color swatch preview next to hex input
        val hexInput = hexColorInput
        if (hexInput != null) {
            val swatchX = hexInput.x + hexInput.width + 4
            val swatchY = hexInput.y
            val swatchSize = hexInput.height
            guiGraphics.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, previewColor)
            guiGraphics.renderOutline(swatchX, swatchY, swatchSize, swatchSize, 0xFF888888.toInt())
        }

        // Divider / hint for selected tag
        if (selectedTag != null) {
            guiGraphics.drawCenteredString(
                font, "── 管理标签: [$selectedTag] | 点击玩家分配/取消 ──",
                cx, 72, 0xFFFF55.toInt()
            )
        } else {
            guiGraphics.drawCenteredString(
                font, "点击上方标签管理 | 选择颜色后输入文本点击 + 创建",
                cx, 72, 0xFFAAAAAA.toInt()
            )
        }

        // Bottom summary
        val playerAssignments = config?.playerVisionPlayerTags
            ?.filter { it.value.isNotEmpty() }
            ?.map { "${it.key}[${it.value}]" }
            ?.joinToString(", ") ?: ""
        if (playerAssignments.isNotEmpty()) {
            guiGraphics.drawCenteredString(
                font, "已分配: $playerAssignments",
                cx, this.height - 14, 0xFF55FF55.toInt()
            )
        } else {
            guiGraphics.drawCenteredString(
                font, "暂无玩家分配",
                cx, this.height - 14, 0x555555.toInt()
            )
        }
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }
}
