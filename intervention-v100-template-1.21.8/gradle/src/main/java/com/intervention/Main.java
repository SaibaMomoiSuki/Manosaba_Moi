1package com.intervention;

// ============================================================
// Fabric API 导入
// ============================================================
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

// ============================================================
// Minecraft 原版导入
// ============================================================
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

// ============================================================
// Java 标准库导入
// ============================================================
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ============================================================
// LWJGL 导入（键盘按键码）
// ============================================================
import org.lwjgl.glfw.GLFW;

// ============================================================
// 主类 —— 模组入口
// ============================================================
public class Main implements ModInitializer {

    // ========================================================
    // 物品实例（在 onInitialize 中完成注册）
    // ========================================================
    /** 介入过去 —— 可以回溯时间、复活玩家的魔法物品 */
    public static Item GET_PASS;
    /** 雪莉放大镜 —— 按住右键可以望远 */
    public static Item MAGNIFYING_GLASS;

    // ========================================================
    // 模组 ID（命名空间）
    // ====================================================
    public static final String MOD_ID = "intervention";

    // ========================================================
    // 数据存储区
    // ========================================================

    /**
     * 玩家位置历史记录
     * 键：玩家UUID
     * 值：按时间排序的位置快照列表（每秒记录一次，保留最近200条=3分20秒）
     */
    private static final Map<UUID, ArrayList<PositionSnapshot>> positionHistory = new HashMap<>();

    /**
     * 物品冷却时间表
     * 键：玩家UUID
     * 值：冷却结束的时间戳（System.currentTimeMillis()）
     */
    private static final Map<UUID, Long> itemCooldowns = new HashMap<>();

    /**
     * 待确认的传送信息
     * 键：玩家UUID
     * 值：传送目标和开始时间
     */
    private static final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();

    /**
     * 最近死亡的玩家记录（保留30秒）
     * 键：死亡玩家UUID
     * 值：死亡信息
     */
    private static final Map<UUID, DeathRecord> recentDeaths = new HashMap<>();

    /**
     * 计分板配置（创造模式KP_9设置）
     * requiredScoreboard: 选定的计分板目标名称（null表示未配置，不限制使用）
     * requiredScore: 最低分数要求（默认1）
     */
    private static String requiredScoreboard = null;
    private static int requiredScore = 1;

    // ========================================================
    // 内部数据类
    // ========================================================

    /**
     * 玩家位置快照 —— 记录某一时刻玩家的位置和朝向
     */
    private static class PositionSnapshot {
        final double x, y, z;       // 坐标
        final float yaw, pitch;      // 朝向（水平/垂直旋转角度）
        final RegistryKey<World> dimension; // 所在维度（主世界/下界/末地）
        final long timestamp;        // 记录时间戳

        PositionSnapshot(ServerPlayerEntity player) {
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.yaw = player.getYaw();
            this.pitch = player.getPitch();
            this.dimension = player.getServerWorld().getRegistryKey();
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * 待确认传送 —— 玩家选择了"介入X秒"但尚未确认
     */
    private static class PendingTeleport {
        final PositionSnapshot target; // 传送目标位置
        final long startTime;          // 开始时间（用于判断30秒超时）
        final int durationSeconds;     // 回溯的秒数（20/60/180）

        PendingTeleport(PositionSnapshot target, int durationSeconds) {
            this.target = target;
            this.startTime = System.currentTimeMillis();
            this.durationSeconds = durationSeconds;
        }

        /** 是否已超过30秒确认窗口 */
        boolean isExpired() {
            return System.currentTimeMillis() - startTime > 30_000; // 30秒
        }
    }

    /**
     * 死亡记录 —— 记录玩家死亡时的信息
     */
    private static class DeathRecord {
        final UUID playerUuid;
        final String playerName;
        final double x, y, z;
        final RegistryKey<World> dimension;
        final long deathTime;

        DeathRecord(ServerPlayerEntity player) {
            this.playerUuid = player.getUuid();
            this.playerName = player.getName().getString();
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.dimension = player.getServerWorld().getRegistryKey();
            this.deathTime = System.currentTimeMillis();
        }

        /** 是否在30秒内死亡 */
        boolean isRecent() {
            return System.currentTimeMillis() - deathTime <= 30_000;
        }
    }

    // ========================================================
    // 模组初始化
    // ========================================================

    @Override
    public void onInitialize() {
        // --- 第1步：注册物品（使用 FabricItemSettings，最大堆叠1）---
        GET_PASS = Registry.register(
                Registries.ITEM,
                Identifier.of(MOD_ID, "get_pass"),
                new GetPassItem(new FabricItemSettings().maxCount(1))
        );
        MAGNIFYING_GLASS = Registry.register(
                Registries.ITEM,
                Identifier.of(MOD_ID, "magnifying_glass"),
                new MagnifyingGlassItem(new FabricItemSettings().maxCount(1))
        );

        // --- 第2步：将物品添加到创造模式物品栏（工具分类） ---
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(GET_PASS);
            entries.add(MAGNIFYING_GLASS);
        });

        // --- 第3步：注册命令 ---
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });

        // --- 第4步：注册事件监听 ---
        registerEvents();

        // 模组初始化完成
        System.out.println("[Intervention] 介入过去模组已加载！");
    }

    // ========================================================
    // 命令注册
    // ========================================================

    /**
     * 注册 /intervention 命令及其所有子命令
     * 玩家通过点击聊天消息中的可点击文字来执行这些命令
     */
    private static void registerCommands(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("intervention")
                        // 允许任何玩家执行（不需要OP权限）
                        .requires(source -> source.hasPermissionLevel(0))

                        // ----- 取消 -----
                        .then(CommandManager.literal("cancel")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleCancel(player);
                                })
                        )

                        // ----- 确认传送（回溯） -----
                        .then(CommandManager.literal("confirm")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleConfirm(player);
                                })
                        )

                        // ----- 介入玩家 -----
                        .then(CommandManager.literal("intervene_player")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleIntervenePlayer(player);
                                })
                        )

                        // ----- 介入20秒 -----
                        .then(CommandManager.literal("intervene_20s")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleTimeIntervene(player, 20);
                                })
                        )

                        // ----- 介入1分钟（60秒） -----
                        .then(CommandManager.literal("intervene_1min")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleTimeIntervene(player, 60);
                                })
                        )

                        // ----- 介入3分钟（180秒） -----
                        .then(CommandManager.literal("intervene_3min")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleTimeIntervene(player, 180);
                                })
                        )

                        // ----- 计分板配置（创造模式KP_9触发） -----
                        .then(CommandManager.literal("scoreboard_config")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleScoreboardConfig(player);
                                })
                        )

                        // ----- 选择计分板目标 -----
                        .then(CommandManager.literal("select_scoreboard")
                                .then(CommandManager.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayer();
                                            if (player == null) return 0;
                                            String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name");
                                            return handleSelectScoreboard(player, name);
                                        })
                                )
                        )

                        // ----- 设置分数阈值 -----
                        .then(CommandManager.literal("set_threshold")
                                .then(CommandManager.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayer();
                                            if (player == null) return 0;
                                            int value = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "value");
                                            return handleSetThreshold(player, value);
                                        })
                                )
                        )

                        // ----- 触发介入（KP_9非创造模式，或右键物品） -----
                        .then(CommandManager.literal("trigger_intervene")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleTriggerIntervene(player);
                                })
                        )
        );
    }

    // ========================================================
    // 事件注册
    // ========================================================

    private static void registerEvents() {
        // ----- 服务器每刻触发：记录玩家位置 + 清理过期数据 -----
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            serverTick(server);
        });

        // ----- 玩家死亡时记录死亡信息 -----
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                DeathRecord record = new DeathRecord(player);
                recentDeaths.put(player.getUuid(), record);
            }
        });

        // ----- 玩家断开连接时清理相关数据 -----
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUuid();
            pendingTeleports.remove(uuid);
            positionHistory.remove(uuid);
        });
    }

    // ========================================================
    // 服务器每刻处理
    // ========================================================

    /** 记录在线玩家位置（每秒一次）+ 清理过期数据 */
    private static void serverTick(net.minecraft.server.MinecraftServer server) {
        // 每20刻（1秒）记录一次所有在线玩家的位置
        if (server.getTicks() % 20 == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                recordPosition(player);
            }
        }

        // 每10秒清理一次过期数据
        if (server.getTicks() % 200 == 0) {
            cleanupExpiredData(server);
        }
    }

    /** 记录单个玩家的位置 */
    private static void recordPosition(ServerPlayerEntity player) {
        ArrayList<PositionSnapshot> history = positionHistory.computeIfAbsent(
                player.getUuid(), k -> new ArrayList<>());
        history.add(new PositionSnapshot(player));
        // 只保留最近200条（200秒 = 3分20秒，足够覆盖"介入3分钟"）
        while (history.size() > 200) {
            history.remove(0);
        }
    }

    /** 清理过期数据 */
    private static void cleanupExpiredData(net.minecraft.server.MinecraftServer server) {
        long now = System.currentTimeMillis();

        // 清理过期的死亡记录（超过30秒的）
        recentDeaths.entrySet().removeIf(entry -> !entry.getValue().isRecent());

        // 清理过期/超时的待确认传送（超过30秒未确认）
        Iterator<Map.Entry<UUID, PendingTeleport>> it = pendingTeleports.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingTeleport> entry = it.next();
            PendingTeleport pending = entry.getValue();
            if (pending.isExpired()) {
                // 超时：施加14分30秒冷却
                itemCooldowns.put(entry.getKey(), now + 870_000); // 14分30秒 = 870000毫秒
                it.remove();

                // 通知玩家
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null) {
                    player.sendMessage(
                            Text.literal("确认超时！介入过去物品已进入14分30秒冷却。")
                                    .formatted(Formatting.RED),
                            false
                    );
                }
            }
        }
    }

    // ========================================================
    // 命令处理逻辑
    // ========================================================

    /**
     * 处理"取消" —— 关闭界面，施加14分30秒冷却
     */
    private static int handleCancel(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        PendingTeleport pending = pendingTeleports.remove(uuid);

        if (pending != null) {
            // 有待确认传送时取消 → 14分30秒冷却
            itemCooldowns.put(uuid, System.currentTimeMillis() + 870_000);
            player.sendMessage(
                    Text.literal("已取消介入。物品进入14分30秒冷却。")
                            .formatted(Formatting.RED),
                    false
            );
        } else {
            // 无待确认传送时取消 → 不消耗冷却
            player.sendMessage(
                    Text.literal("已取消操作。（未消耗冷却）")
                            .formatted(Formatting.GRAY),
                    false
            );
        }
        return 1;
    }

    /**
     * 处理"回溯"（确认传送） —— 传送到目标位置，施加15分钟冷却
     */
    private static int handleConfirm(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        PendingTeleport pending = pendingTeleports.remove(uuid);

        if (pending == null) {
            player.sendMessage(
                    Text.literal("没有待确认的介入操作。").formatted(Formatting.RED),
                    false
            );
            return 0;
        }

        if (pending.isExpired()) {
            // 理论上不会到这里（tick中已清理），但做防御性检查
            itemCooldowns.put(uuid, System.currentTimeMillis() + 870_000);
            player.sendMessage(
                    Text.literal("确认窗口已过期！物品进入14分30秒冷却。")
                            .formatted(Formatting.RED),
                    false
            );
            return 0;
        }

        // 执行传送
        PositionSnapshot target = pending.target;
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server != null) {
            ServerWorld targetWorld = server.getWorld(target.dimension);
            if (targetWorld != null) {
                // 1.21.8 兼容写法（Yarn 映射）
                player.teleport(targetWorld, target.x, target.y, target.z,
                        java.util.Set.of(), target.yaw, target.pitch);
            }
        }

        // 15分钟冷却
        itemCooldowns.put(uuid, System.currentTimeMillis() + 900_000);

        player.sendMessage(
                Text.literal("成功回溯到" + pending.durationSeconds + "秒前的位置！")
                        .formatted(Formatting.GREEN),
                false
        );

        // 播放传送音效
        player.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);

        return 1;
    }

    /**
     * 处理"介入玩家" —— 50%概率复活最近30秒内死亡的玩家
     */
    private static int handleIntervenePlayer(ServerPlayerEntity user) {
        UUID userUuid = user.getUuid();

        // 找最近死亡的玩家（排除使用者自己）
        DeathRecord targetDeath = null;
        UUID targetUuid = null;
        for (Map.Entry<UUID, DeathRecord> entry : recentDeaths.entrySet()) {
            DeathRecord record = entry.getValue();
            if (record.isRecent() && !record.playerUuid.equals(userUuid)) {
                targetDeath = record;
                targetUuid = entry.getKey();
                break;
            }
        }

        if (targetDeath == null) {
            user.sendMessage(
                    Text.literal("没有最近30秒内死亡的其他玩家可以介入。")
                            .formatted(Formatting.RED),
                    false
            );
            return 0;
        }

        // 50% 概率判定
        boolean success = Math.random() < 0.5;

        if (success) {
            // 成功：复活玩家
            ServerPlayerEntity deadPlayer = user.getServer() != null
                    ? user.getServer().getPlayerManager().getPlayer(targetUuid)
                    : null;

            if (deadPlayer != null) {
                // 恢复满血
                deadPlayer.setHealth(deadPlayer.getMaxHealth());
                // 传送到死亡地点
                ServerWorld deathWorld = user.getServer().getWorld(targetDeath.dimension);
                if (deathWorld != null) {
                    deadPlayer.teleport(deathWorld,
                            targetDeath.x, targetDeath.y, targetDeath.z,
                            java.util.Set.of(), deadPlayer.getYaw(), deadPlayer.getPitch());
                }
                deadPlayer.sendMessage(
                        Text.literal(user.getName().getString() + " 使用介入过去将你复活了！")
                                .formatted(Formatting.GOLD),
                        false
                );
            }

            user.sendMessage(
                    Text.literal("介入成功！已复活玩家 " + targetDeath.playerName)
                            .formatted(Formatting.GREEN),
                    false
            );
            user.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        } else {
            // 失败
            user.sendMessage(
                    Text.literal("介入失败……未能复活 " + targetDeath.playerName)
                            .formatted(Formatting.RED),
                    false
            );
            user.playSound(SoundEvents.BLOCK_GLASS_BREAK, 1.0F, 1.0F);
        }

        // 无论成功与否，施加24分钟冷却
        itemCooldowns.put(userUuid, System.currentTimeMillis() + 1_440_000);

        // 移除死亡记录
        recentDeaths.remove(targetUuid);

        return 1;
    }

    /**
     * 处理"介入X秒/分" —— 记录目标位置，进入30秒确认窗口
     */
    private static int handleTimeIntervene(ServerPlayerEntity player, int secondsAgo) {
        UUID uuid = player.getUuid();
        ArrayList<PositionSnapshot> history = positionHistory.get(uuid);

        if (history == null || history.isEmpty()) {
            player.sendMessage(
                    Text.literal("没有足够的位置历史记录，请等待一段时间后再试。")
                            .formatted(Formatting.RED),
                    false
            );
            return 0;
        }

        // 获取 secondsAgo 秒前的位置
        int index = history.size() - 1 - secondsAgo;
        if (index < 0) {
            index = 0; // 如果历史不够，使用最旧的一条
        }
        PositionSnapshot target = history.get(index);

        // 保存待确认传送信息
        pendingTeleports.put(uuid, new PendingTeleport(target, secondsAgo));

        // 通知玩家有30秒确认窗口
        String timeDesc;
        if (secondsAgo < 60) {
            timeDesc = secondsAgo + "秒前";
        } else {
            timeDesc = (secondsAgo / 60) + "分钟前";
        }

        player.sendMessage(
                Text.literal("已选择回溯到 " + timeDesc + " 的位置。")
                        .formatted(Formatting.AQUA),
                false
        );
        player.sendMessage(
                Text.literal("请在30秒内再次右键物品确认，超时将进入14分30秒冷却。")
                        .formatted(Formatting.GRAY),
                false
        );
        // 提示下一步操作
        player.sendMessage(
                Text.literal("右键物品后将显示确认选项。").formatted(Formatting.GRAY),
                false
        );

        return 1;
    }

    /**
     * 处理"计分板配置" —— 显示完整的配置界面
     * 仅创造模式可用
     */
    private static int handleScoreboardConfig(ServerPlayerEntity player) {
        if (!player.isCreative()) {
            player.sendMessage(
                    Text.literal("计分板配置仅限创造模式使用！").formatted(Formatting.RED),
                    false
            );
            return 0;
        }

        ServerScoreboard scoreboard = player.getServer() != null
                ? player.getServer().getScoreboard()
                : null;

        // --- 标题 ---
        player.sendMessage(
                Text.literal("========== 计分板配置 ==========").formatted(Formatting.GOLD),
                false
        );

        // --- 当前配置 ---
        if (requiredScoreboard != null) {
            player.sendMessage(
                    Text.literal("当前计分板: " + requiredScoreboard + "  最低分数: " + requiredScore)
                            .formatted(Formatting.YELLOW),
                    false
            );
        } else {
            player.sendMessage(
                    Text.literal("当前未配置计分板限制（所有玩家均可使用）")
                            .formatted(Formatting.GRAY),
                    false
            );
        }

        player.sendMessage(Text.literal(""), false);

        // --- 列出所有计分板目标 ---
        if (scoreboard != null) {
            player.sendMessage(
                    Text.literal("▸ 选择计分板目标：").formatted(Formatting.WHITE),
                    false
            );
            var objectives = new java.util.ArrayList<>(scoreboard.getObjectives());
            if (objectives.isEmpty()) {
                player.sendMessage(
                        Text.literal("  暂无计分板目标。请先使用 /scoreboard objectives add 创建。")
                                .formatted(Formatting.GRAY),
                        false
                );
            } else {
                for (var objective : objectives) {
                    String objName = objective.getName();
                    MutableText objOption = Text.literal("  [" + objName + "]")
                            .formatted(Formatting.GREEN, Formatting.BOLD)
                            .styled(style -> style.withClickEvent(
                                    new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                            "/intervention select_scoreboard " + objName)
                            ));
                    player.sendMessage(objOption, false);
                }
            }

            player.sendMessage(Text.literal(""), false);

            // --- 设置阈值 ---
            player.sendMessage(
                    Text.literal("▸ 设置最低分数阈值：").formatted(Formatting.WHITE),
                    false
            );
            int[] presetValues = {1, 5, 10, 50, 100, 500, 1000};
            MutableText thresholdLine = Text.literal("  ").formatted(Formatting.GRAY);
            for (int i = 0; i < presetValues.length; i++) {
                int val = presetValues[i];
                MutableText valOption = Text.literal("[" + val + "]")
                        .formatted(Formatting.AQUA, Formatting.BOLD)
                        .styled(style -> style.withClickEvent(
                                new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/intervention set_threshold " + val)
                        ));
                thresholdLine.append(valOption);
                if (i < presetValues.length - 1) {
                    thresholdLine.append(Text.literal(" ").formatted(Formatting.GRAY));
                }
            }
            player.sendMessage(thresholdLine, false);

            player.sendMessage(
                    Text.literal("  或输入 /intervention set_threshold <数值> 自定义")
                            .formatted(Formatting.GRAY),
                    false
            );
        }

        player.sendMessage(Text.literal(""), false);

        // --- 清除配置 ---
        MutableText clearOption = Text.literal("[清除配置]")
                .formatted(Formatting.RED, Formatting.BOLD)
                .styled(style -> style.withClickEvent(
                        new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/intervention select_scoreboard clear")
                ));
        player.sendMessage(
                Text.literal("").append(clearOption).append(Text.literal(" - 移除计分板限制").formatted(Formatting.GRAY)),
                false
        );

        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.5F, 1.0F);
        return 1;
    }

    /**
     * 处理"选择计分板目标" —— 保存选定的计分板名称
     */
    private static int handleSelectScoreboard(ServerPlayerEntity player, String name) {
        if (!player.isCreative()) {
            player.sendMessage(
                    Text.literal("计分板配置仅限创造模式使用！").formatted(Formatting.RED),
                    false
            );
            return 0;
        }

        if ("clear".equals(name)) {
            requiredScoreboard = null;
            requiredScore = 1;
            player.sendMessage(
                    Text.literal("已清除计分板配置，现在所有玩家均可使用介入过去。")
                            .formatted(Formatting.GREEN),
                    false
            );
            return 1;
        }

        requiredScoreboard = name;
        player.sendMessage(
                Text.literal("已选择计分板: " + name).formatted(Formatting.GREEN),
                false
        );
        player.sendMessage(
                Text.literal("当前阈值: " + requiredScore + "（点击上方阈值选项修改）")
                        .formatted(Formatting.GRAY),
                false
        );
        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 1.0F);
        return 1;
    }

    /**
     * 处理"设置阈值" —— 保存最低分数要求
     */
    private static int handleSetThreshold(ServerPlayerEntity player, int value) {
        if (!player.isCreative()) {
            player.sendMessage(
                    Text.literal("计分板配置仅限创造模式使用！").formatted(Formatting.RED),
                    false
            );
            return 0;
        }

        requiredScore = value;
        String target = requiredScoreboard != null ? requiredScoreboard : "未设置";
        player.sendMessage(
                Text.literal("已设置阈值: " + value + "（计分板: " + target + "）")
                        .formatted(Formatting.GREEN),
                false
        );
        player.sendMessage(
                Text.literal("玩家需要 " + target + " 分数 ≥ " + value + " 才能使用介入过去。")
                        .formatted(Formatting.GRAY),
                false
        );
        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 1.2F);
        return 1;
    }

    /**
     * 检查玩家是否满足计分板要求
     * @return true 表示允许使用，false 表示分数不足
     */
    private static boolean checkScoreboardRequirement(ServerPlayerEntity player) {
        // 未配置计分板 → 不限制
        if (requiredScoreboard == null) {
            return true;
        }

        // 创造模式不受限制
        if (player.isCreative()) {
            return true;
        }

        ServerScoreboard scoreboard = player.getServer() != null
                ? player.getServer().getScoreboard()
                : null;
        if (scoreboard == null) {
            return true;
        }

        var objective = scoreboard.getNullableObjective(requiredScoreboard);
        if (objective == null) {
            // 目标不存在 → 放行（避免卡住玩家）
            return true;
        }

        var score = scoreboard.getPlayerScore(player.getName().getString(), objective);
        return score != null && score.getScore() >= requiredScore;
    }

    /**
     * 触发介入操作（KP_9按下 或 右键物品时调用）
     * 先检查计分板要求，再显示主菜单
     */
    private static int handleTriggerIntervene(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        // --- 检查计分板要求 ---
        if (!checkScoreboardRequirement(player)) {
            String objName = requiredScoreboard != null ? requiredScoreboard : "?";
            ServerScoreboard sb = player.getServer() != null
                    ? player.getServer().getScoreboard()
                    : null;
            int currentScore = 0;
            if (sb != null && requiredScoreboard != null) {
                var obj = sb.getNullableObjective(requiredScoreboard);
                if (obj != null) {
                    var sc = sb.getPlayerScore(player.getName().getString(), obj);
                    if (sc != null) {
                        currentScore = sc.getScore();
                    }
                }
            }
            player.sendMessage(
                    Text.literal("你的「" + objName + "」分数不足！")
                            .formatted(Formatting.RED),
                    false
            );
            player.sendMessage(
                    Text.literal("需要: " + requiredScore + "  当前: " + currentScore)
                            .formatted(Formatting.RED),
                    false
            );
            player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.5F);
            return 0;
        }

        // --- 检查冷却 ---
        long cooldownRemaining = getCooldownRemaining(uuid);
        if (cooldownRemaining > 0) {
            sendCooldownMessage(player, cooldownRemaining);
            return 0;
        }

        // --- 检查是否有待确认的传送 ---
        PendingTeleport pending = pendingTeleports.get(uuid);
        if (pending != null) {
            if (pending.isExpired()) {
                pendingTeleports.remove(uuid);
                itemCooldowns.put(uuid, System.currentTimeMillis() + 870_000);
                player.sendMessage(
                        Text.literal("确认窗口已过期！物品进入14分30秒冷却。")
                                .formatted(Formatting.RED),
                        false
                );
                return 0;
            }
            // 显示确认/取消菜单
            GetPassItem.showConfirmMenu(player, pending);
            return 1;
        }

        // --- 显示主菜单 ---
        GetPassItem.showMainMenu(player);
        return 1;
    }

    // ========================================================
    // 辅助方法
    // ========================================================

    /**
     * 检查玩家是否处于物品冷却中
     * @return 剩余冷却秒数，如果不处于冷却则返回0
     */
    private static long getCooldownRemaining(UUID uuid) {
        Long cooldownEnd = itemCooldowns.get(uuid);
        if (cooldownEnd == null) return 0;
        long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * 向玩家显示冷却中提示
     */
    private static void sendCooldownMessage(ServerPlayerEntity player, long remainingMs) {
        long seconds = remainingMs / 1000;
        long minutes = seconds / 60;
        long secs = seconds % 60;

        String timeStr;
        if (minutes > 0) {
            timeStr = minutes + "分" + secs + "秒";
        } else {
            timeStr = secs + "秒";
        }

        player.sendMessage(
                Text.literal("正在冷却中... 剩余 " + timeStr)
                        .formatted(Formatting.RED, Formatting.BOLD),
                false
        );
    }

    // ========================================================
    // 物品类 1 —— 介入过去
    // ========================================================

    /**
     * 介入过去 —— 右键打开操作菜单
     * <p>
     * 功能流程：
     * 1. 右键物品 → 检查冷却 → 检查待确认传送 → 显示主菜单
     * 2. 主菜单有5个选项（取消/介入玩家/介入20秒/介入1分钟/介入3分钟）
     * 3. 选择介入X秒后 → 30秒内再次右键确认（回溯/取消）
     * 4. 选择介入玩家 → 50%概率复活最近死亡的玩家
     * </p>
     */
    public static class GetPassItem extends Item {

        public GetPassItem(Settings settings) {
            super(settings);
        }

        @Override
        public ActionResult use(World world, PlayerEntity user, Hand hand) {
            if (world.isClient()) {
                // 客户端仅返回 SUCCESS，不执行逻辑
                return ActionResult.SUCCESS;
            }
            if (!(user instanceof ServerPlayerEntity player)) {
                return ActionResult.PASS;
            }
            // 核心逻辑：触发介入菜单或确认
            handleTriggerIntervene(player);
            // 使用 SUCCESS 保证物品不被消耗
            return ActionResult.SUCCESS;
        }

        /**
         * 显示主菜单（5个选项）
         */
        private static void showMainMenu(ServerPlayerEntity player) {
            player.sendMessage(
                    Text.literal("").append(Text.literal("========== 魔法:介入过去 ==========").formatted(Formatting.GOLD)),
                    false
            );

            // 显示玩家名称
            player.sendMessage(
                    Text.literal("玩家: " + player.getName().getString()).formatted(Formatting.WHITE),
                    false
            );

            player.sendMessage(Text.literal(""), false); // 空行

            // 选项1：取消 —— 红色字体
            MutableText cancelOption = Text.literal("[取消]")
                    .formatted(Formatting.RED, Formatting.BOLD)
                    .styled(style -> style.withClickEvent(
                            new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/intervention cancel")
                    ));
            player.sendMessage(
                    Text.literal("").append(cancelOption).append(Text.literal(" - 关闭界面（不消耗冷却）").formatted(Formatting.GRAY)),
                    false
            );

            // 选项2：介入玩家 —— 黄色字体
            MutableText playerOption = Text.literal("[介入玩家]")
                    .formatted(Formatting.YELLOW, Formatting.BOLD)
                    .styled(style -> style.withClickEvent(
                            new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/intervention intervene_player")
                    ));
            player.sendMessage(
                    Text.literal("").append(playerOption).append(Text.literal(" - 50%概率复活最近死亡的玩家").formatted(Formatting.GRAY)),
                    false
            );

            // 选项3：介入20秒 —— 青色字体
            MutableText sec20Option = Text.literal("[介入20秒]")
                    .formatted(Formatting.AQUA, Formatting.BOLD)
                    .styled(style -> style.withClickEvent(
                            new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/intervention intervene_20s")
                    ));
            player.sendMessage(
                    Text.literal("").append(sec20Option).append(Text.literal(" - 传送回20秒前的位置").formatted(Formatting.GRAY)),
                    false
            );

            // 选项4：介入1分钟 —— 黄色字体
            MutableText min1Option = Text.literal("[介入1分钟]")
                    .formatted(Formatting.YELLOW, Formatting.BOLD)
                    .styled(style -> style.withClickEvent(
                            new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/intervention intervene_1min")
                    ));
            player.sendMessage(
                    Text.literal("").append(min1Option).append(Text.literal(" - 传送回1分钟前的位置").formatted(Formatting.GRAY)),
                    false
            );

            // 选项5：介入3分钟 —— 绿色字体
            MutableText min3Option = Text.literal("[介入3分钟]")
                    .formatted(Formatting.GREEN, Formatting.BOLD)
                    .styled(style -> style.withClickEvent(
                            new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/intervention intervene_3min")
                    ));
            player.sendMessage(
                    Text.literal("").append(min3Option).append(Text.literal(" - 传送回3分钟前的位置").formatted(Formatting.GRAY)),
                    false
            );

            player.sendMessage(
                    Text.literal("点击上方选项进行操作 ↑").formatted(Formatting.DARK_GRAY),
                    false
            );

            // 播放打开音效
            player.playSound(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 0.8F, 1.2F);
        }

        /**
         * 显示确认/取消菜单（在选择了介入X秒之后）
         */
        private static void showConfirmMenu(ServerPlayerEntity player, PendingTeleport pending) {
            String timeDesc;
            int secs = pending.durationSeconds;
            if (secs < 60) {
                timeDesc = secs + "秒前";
            } else {
                timeDesc = (secs / 60) + "分钟前";
            }
            long remainingSecs = 30 - (System.currentTimeMillis() - pending.startTime) / 1000;

            player.sendMessage(
                    Text.literal("========== 确认回溯 ==========").formatted(Formatting.GOLD),
                    false
            );
            player.sendMessage(
                    Text.literal("目标: 回溯到 " + timeDesc + " 的位置")
                            .formatted(Formatting.WHITE),
                    false
            );
            player.sendMessage(
                    Text.literal("剩余确认时间: " + remainingSecs + " 秒")
                            .formatted(Formatting.YELLOW),
                    false
            );

            player.sendMessage(Text.literal(""), false); // 空行

            // 取消 —— 红色字体
            MutableText cancelOption = Text.literal("[取消]")
                    .formatted(Formatting.RED, Formatting.BOLD)
                    .styled(style -> style.withClickEvent(
                            new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/intervention cancel")
                    ));
            player.sendMessage(
                    Text.literal("").append(cancelOption).append(Text.literal(" - 放弃传送（14分30秒冷却）").formatted(Formatting.GRAY)),
                    false
            );

            // 回溯 —— 黄色字体
            MutableText confirmOption = Text.literal("[回溯]")
                    .formatted(Formatting.YELLOW, Formatting.BOLD)
                    .styled(style -> style.withClickEvent(
                            new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/intervention confirm")
                    ));
            player.sendMessage(
                    Text.literal("").append(confirmOption).append(Text.literal(" - 传送回目标位置（15分钟冷却）").formatted(Formatting.GRAY)),
                    false
            );

            // 播放音效
            player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.5F, 1.0F);
        }
    }

    // ========================================================
    // 物品类 2 —— 雪莉放大镜
    // ========================================================

    /**
     * 雪莉放大镜 —— 按住右键可以像望远镜一样放大视野
     * <p>
     * 实现原理：使用 UseAction.SPYGLASS 让游戏自动渲染望远镜遮罩效果
     * </p>
     */
    public static class MagnifyingGlassItem extends Item {

        public MagnifyingGlassItem(Settings settings) {
            super(settings);
        }

        /**
         * 返回使用动作类型 —— 望远镜类型会让游戏自动渲染放大遮罩
         */
        @Override
        public UseAction getUseAction(ItemStack stack) {
            return UseAction.SPYGLASS;
        }

        /**
         * 最大使用时间（刻） —— 1200刻 = 60秒
         * 在这个时间内玩家可以一直按住右键
         */
        @Override
        public int getMaxUseTime(ItemStack stack, LivingEntity user) {
            return 1200; // 60秒 = 1200刻
        }

        /**
         * 玩家右键使用物品 —— 开始望远镜效果
         * 修正：必须返回 SUCCESS 并调用 setCurrentHand 才能真正开始使用动作
         */
        @Override
        public ActionResult use(World world, PlayerEntity user, Hand hand) {
            // 开始使用物品（望远镜效果）
            user.setCurrentHand(hand);
            return ActionResult.SUCCESS;
        }
    }

    // ========================================================
    // 客户端初始化（内部类）
    // ========================================================

    /**
     * 客户端专用初始化
     * 负责注册按键绑定（KP_9 在创造模式下设置计分板 / 非创造触发介入）
     *
     * 注意：必须在 fabric.mod.json 的 "client" 入口点中声明此类，例如：
     *   "client": [
     *     "com.intervention.Main$ClientInit"
     *   ]
     */
    public static class ClientInit implements ClientModInitializer {

        /** KP_9 按键绑定 */
        private static KeyBinding kp9KeyBinding;

        @Override
        public void onInitializeClient() {
            // ----- 注册 KP_9 按键 -----
            kp9KeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.intervention.set_score",          // 翻译键
                    InputUtil.Type.KEYSYM,                  // 键盘按键
                    GLFW.GLFW_KEY_KP_9,                     // 小键盘数字9
                    "category.intervention.main"            // 按键分类
            ));

            // ----- 每客户端刻检查按键 -----
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                // while 循环处理按键（防止漏掉快速连按）
                while (kp9KeyBinding.wasPressed()) {
                    onKp9Pressed(client);
                }
            });
        }

        /**
         * KP_9 按键被按下时的处理
         * - 创造模式：打开计分板配置菜单
         * - 生存/冒险模式：触发介入操作（需通过计分板检查）
         */
        private void onKp9Pressed(MinecraftClient client) {
            if (client.player == null) return;
            if (client.player.networkHandler == null) return;

            if (client.player.isCreative()) {
                // 创造模式：打开计分板配置菜单
                client.player.networkHandler.sendCommand("intervention scoreboard_config");
            } else {
                // 非创造模式：触发介入操作（需通过计分板检查）
                client.player.networkHandler.sendCommand("intervention trigger_intervene");
            }
        }
    }
}