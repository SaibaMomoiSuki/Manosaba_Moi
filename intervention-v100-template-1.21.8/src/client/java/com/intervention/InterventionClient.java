// Copyright (c) 2026 才羽モモイ. Proprietary - Internal Use Only. 内部私有，严禁任何形式使用（包括游玩、复制、分发）。
package com.intervention;

// ============================================================
// Fabric API 导入（客户端）
// ============================================================
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

// ============================================================
// Minecraft 客户端导入（Mojang 官方映射）
// ============================================================
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;

// ============================================================
// LWJGL / 输入导入
// ============================================================
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

// ============================================================
// 客户端初始化
// ============================================================

/**
 * 客户端专用初始化
 * 负责注册按键绑定（KP_9 在创造模式下设置计分板 / 非创造触发介入）
 *
 * 注意：必须在 fabric.mod.json 的 "client" 入口点中声明此类，例如：
 *   "client": [
 *     "com.intervention.InterventionClient"
 *   ]
 */
public class InterventionClient implements ClientModInitializer {

    /** 打开设置界面按键绑定 */
    private static KeyMapping openSettingsKeyBinding;

    @Override
    public void onInitializeClient() {
        // ----- 注册 打开设置界面 按键 -----
        openSettingsKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.intervention.open_settings",       // 翻译键
                InputConstants.Type.KEYSYM,             // 键盘按键
                GLFW.GLFW_KEY_KP_9,                     // 小键盘数字9
                "category.intervention.main"            // 按键分类
        ));

        // ----- 每客户端刻检查按键 -----
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // while 循环处理按键（防止漏掉快速连按）
            while (openSettingsKeyBinding.consumeClick()) {
                onOpenSettingsPressed(client);
            }
        });
    }

    /**
     * 打开设置界面按键被按下时的处理
     * - 创造模式：打开计分板配置菜单
     * - 非创造模式：触发介入操作
     */
    private void onOpenSettingsPressed(Minecraft client) {
        if (client.player == null) return;
        if (client.player.connection == null) return;

        if (client.player.isCreative()) {
            // 创造模式：打开计分板配置菜单
            client.player.connection.sendCommand("intervention scoreboard_config");
        } else {
            // 非创造模式：触发介入操作（需通过计分板检查）
            client.player.connection.sendCommand("intervention trigger_intervene");
        }
    }
}
