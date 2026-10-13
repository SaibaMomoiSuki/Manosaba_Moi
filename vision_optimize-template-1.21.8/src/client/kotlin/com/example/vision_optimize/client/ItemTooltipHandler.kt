package com.example.vision_optimize.client

import com.example.vision_optimize.util.TextBuilder
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.Minecraft

/**
 * Adds the gray hint text "[按住鼠标中键进行幻视]" to item tooltips.
 * Only shown on items that can be tracked (non-empty stacks).
 */
object ItemTooltipHandler {

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, context, type, lines ->
            val player = Minecraft.getInstance().player
            if (player != null && ClientVisionState.hasBlockVisionPermission(player.displayName!!.string) && !stack.isEmpty) {
                lines.add(TextBuilder.itemTooltipHint())
            }
        }
    }
}
