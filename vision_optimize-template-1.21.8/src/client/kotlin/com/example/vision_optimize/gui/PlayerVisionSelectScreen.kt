package com.example.vision_optimize.gui

import com.example.vision_optimize.client.ClientVisionState
import com.example.vision_optimize.network.PlayerVisionRequestC2SPacket
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3

/**
 * Level 2/3 player vision selection screen.
 * Pressing the player_select key (default P) opens this screen.
 * Shows players currently in the caster's field of view and within configured range.
 *
 * Clicking a player button executes the player vision on that target.
 * Auto-closes when all listed players leave FOV/range or when no players remain in range.
 */
class PlayerVisionSelectScreen(
    private val playersInFov: List<PlayerFovEntry>,
    private val parent: Screen?
) : Screen(Component.literal("视野内玩家")) {

    data class PlayerFovEntry(
        val playerName: String,
        val tagText: String
    )

    private var tickCounter: Int = 0

    override fun init() {
        super.init()
        tickCounter = 0
        rebuildButtons()
    }

    private fun rebuildButtons() {
        clearWidgets()
        val centerX = this.width / 2
        var y = 30

        val stillInFov = getCurrentFovPlayerNames()
        val visibleEntries = playersInFov.filter { it.playerName in stillInFov }

        if (visibleEntries.isEmpty()) {
            addRenderableWidget(
                Button.Builder(
                    Component.literal("视野内无其他玩家").withStyle { it.withColor(0xAAAAAA) }
                ) {}.pos(centerX - 100, y).size(200, 20).build()
            )
        } else {
            for (entry in visibleEntries) {
                addRenderableWidget(
                    Button.Builder(
                        Component.literal(entry.playerName)
                            .withStyle { it.withColor(0x55FFFF) }
                    ) {
                        // Execute player vision on selected target
                        executePlayerVision(entry.playerName)
                    }.pos(centerX - 100, y).size(200, 20).build()
                )
                y += 25
            }
        }

        y += 10
        addRenderableWidget(
            Button.Builder(Component.literal("关闭")) {
                onClose()
            }.pos(centerX - 50, y).size(100, 20).build()
        )
    }

    /**
     * Execute player vision on the selected target.
     * Estimates wasInView (caster in target's FOV) and sends the request packet.
     * Closes the screen after sending.
     */
    private fun executePlayerVision(targetName: String) {
        val caster = minecraft?.player ?: return
        val world = minecraft?.level ?: return
        val target = world.players().find { it.displayName!!.string == targetName } ?: return

        // Estimate whether caster is in target's FOV (same logic as charge mechanic)
        val wasInView = isCasterInTargetFov(caster, target)

        // Send vision request
        ClientPlayNetworking.send(PlayerVisionRequestC2SPacket(targetName, wasInView, fromPKey = true))

        // Close the screen
        minecraft?.setScreen(null)
    }

    /** Estimate if caster is in target's field of view (dot product > 0.2, ~78 degree cone). */
    private fun isCasterInTargetFov(caster: net.minecraft.world.entity.player.Player, target: net.minecraft.world.entity.player.Player): Boolean {
        val targetLook = target.lookAngle
        val toCaster = Vec3(
            caster.x - target.x,
            caster.eyeY - target.eyeY,
            caster.z - target.z
        ).normalize()
        return targetLook.dot(toCaster) > 0.2
    }

    override fun tick() {
        super.tick()
        tickCounter++
        // Check every 10 ticks (0.5 sec) if players are still in FOV/range
        if (tickCounter % 10 == 0) {
            val stillInFov = getCurrentFovPlayerNames()
            val anyVisible = playersInFov.any { it.playerName in stillInFov }
            if (!anyVisible) {
                // All players left FOV or range → auto-close
                minecraft?.player?.displayClientMessage(
                    Component.literal("目标已离开视野范围").withStyle { it.withColor(0xFF5555) },
                    true
                )
                minecraft?.setScreen(null)
                return
            }
            // Rebuild to update visible buttons
            rebuildButtons()
        }
    }

    /** Get names of players currently in caster's FOV and within spherical radius (L2=7, L3=14). */
    private fun getCurrentFovPlayerNames(): Set<String> {
        val caster = minecraft?.player ?: return emptySet()
        val world = minecraft?.level ?: return emptySet()
        val level = ClientVisionState.cachedPlayerVisionLevel
            ?: ClientVisionState.getPlayerVisionLevel()
        val maxDist = if (level == com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_3) 14.0 else 7.0
        val maxDistSq = maxDist * maxDist
        val lookDir = caster.lookAngle

        return world.players().filter { other ->
            other != caster &&
            caster.distanceToSqr(other) <= maxDistSq
        }.filter { other ->
            val toTarget = Vec3(
                other.x - caster.x,
                other.eyeY - caster.eyeY,
                other.z - caster.z
            ).normalize()
            lookDir.dot(toTarget) > 0.766  // 80° cone matching Vision_optimizeClient
        }.map { it.displayName!!.string }.toSet()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(guiGraphics, mouseX, mouseY, delta)
        guiGraphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF.toInt())
        val level = ClientVisionState.cachedPlayerVisionLevel ?: ClientVisionState.getPlayerVisionLevel()
        val levelStr = when (level) {
            com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_3 -> "三阶段"
            com.example.vision_optimize.config.VisionLevel.PLAYER_VISION_LEVEL_2 -> "二阶段"
            else -> ""
        }
        if (levelStr.isNotEmpty()) {
            guiGraphics.drawCenteredString(font, "人物幻视$levelStr", width / 2, 18, 0xFFAA00)
        }
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    companion object {
        fun open(playersInFov: List<PlayerFovEntry>) {
            val currentScreen = Minecraft.getInstance().screen
            Minecraft.getInstance().setScreen(PlayerVisionSelectScreen(playersInFov, currentScreen))
        }
    }
}
