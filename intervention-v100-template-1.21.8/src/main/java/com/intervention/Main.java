// Copyright (c) 2026 才羽モモイ. Proprietary - Internal Use Only. 内部私有，严禁任何形式使用（包括游玩、复制、分发）。
package com.intervention;

// ============================================================
// Fabric API 导入
// ============================================================
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

// ============================================================
// Minecraft 原版导入（Mojang 官方映射）
// ============================================================
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;

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

    /**
     * 沉默标签 —— 拥有该标签（原版 /tag 添加）的玩家无法使用介入过去
     */
    private static final String SILENCED_TAG = "Silenced";

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
     * 待确认的传送信息（保留以兼容旧逻辑）
     * 键：玩家UUID
     * 值：传送目标和开始时间
     */
    private static final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();

    /**
     * 回溯记录（与介入时间对应的限时撤销窗口）
     * 键：玩家UUID
     * 值：传送前的位置（可用于回溯到原处）
     */
    private static final Map<UUID, RevertRecord> revertRecords = new HashMap<>();

    /**
     * 最近死亡的玩家记录（保留时间可配置）
     * 键：死亡玩家UUID
     * 值：死亡信息
     */
    private static final Map<UUID, DeathRecord> recentDeaths = new HashMap<>();

    /**
     * 待处理的复活（延迟切换游戏模式）
     * 键：玩家UUID，值：目标 tick（server.getTickCount() >= 该值时执行）
     */
    private static final Map<UUID, Integer> pendingRevivals = new HashMap<>();

    /**
     * 计分板数值区间（用于限制各选项是否可用）
     */
    private static class ScoreRange {
        String scoreboard;        // null = 不限制
        int min;                  // 最小值（Integer.MIN_VALUE = 无限小）
        int max;                  // 最大值（Integer.MAX_VALUE = 无限大）

        ScoreRange() {
            this.scoreboard = null;
            this.min = Integer.MIN_VALUE;
            this.max = Integer.MAX_VALUE;
        }

        void set(String sb, int mn, int mx) {
            this.scoreboard = sb;
            this.min = mn;
            this.max = mx;
        }

        boolean isConfigured() {
            return scoreboard != null;
        }

        boolean isWithin(int score) {
            return score >= min && score <= max;
        }
    }

    /** 介入20秒 计分板限制 */
    private static final ScoreRange sc20s = new ScoreRange();
    /** 介入1分钟 计分板限制 */
    private static final ScoreRange sc1min = new ScoreRange();
    /** 介入3分钟 计分板限制 */
    private static final ScoreRange sc3min = new ScoreRange();
    /** 介入玩家 计分板限制 */
    private static final ScoreRange scPlayer = new ScoreRange();

    /**
     * 可复活玩家的死亡时间上限（秒），默认60
     */
    private static int playerDeathTime = 60;

    /**
     * 介入玩家时使用者死亡概率（默认5%）
     */
    private static int intervenePlayerDeathPercent = 5;

    /**
     * 复活玩家概率（当前值，失败会提升）
     */
    private static int resurrectionPercent = 50;

    /**
     * 复活玩家概率（手动配置的基准值，-1表示从未手动设置）
     */
    private static int configuredResurrectionPercent = -1;

    // ========================================================
    // 内部数据类
    // ========================================================

    /**
     * 玩家位置快照 —— 记录某一时刻玩家的位置和朝向
     */
    private static class PositionSnapshot {
        final double x, y, z;       // 坐标
        final float yaw, pitch;      // 朝向（水平/垂直旋转角度）
        final ResourceKey<Level> dimension; // 所在维度（主世界/下界/末地）
        final long timestamp;        // 记录时间戳

        PositionSnapshot(ServerPlayer player) {
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.yaw = player.getYRot();
            this.pitch = player.getXRot();
            this.dimension = player.level().dimension();
            this.timestamp = System.currentTimeMillis();
        }

        PositionSnapshot(double x, double y, double z, float yaw, float pitch, ResourceKey<Level> dimension) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.dimension = dimension;
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
     * 回溯记录 —— 传送前保存的原位置，在对应时间内可以回溯撤销
     */
    private static class RevertRecord {
        final PositionSnapshot originalPosition; // 传送前的位置（回溯目标）
        final long timestamp;                    // 记录时间戳
        final int revertSeconds;                 // 回溯窗口时长（秒）

        RevertRecord(PositionSnapshot originalPosition, int revertSeconds) {
            this.originalPosition = originalPosition;
            this.timestamp = System.currentTimeMillis();
            this.revertSeconds = revertSeconds;
        }

        /** 是否已超过回溯窗口 */
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > revertSeconds * 1000L;
        }

        /** 剩余回溯时间（秒） */
        long remainingSeconds() {
            long elapsed = (System.currentTimeMillis() - timestamp) / 1000;
            return Math.max(0, revertSeconds - elapsed);
        }
    }

    /**
     * 死亡记录 —— 记录玩家死亡时的信息
     */
    private static class DeathRecord {
        final UUID playerUuid;
        final String playerName;
        final double x, y, z;
        final float yaw, pitch;
        final ResourceKey<Level> dimension;
        final long deathTime;

        DeathRecord(ServerPlayer player) {
            this.playerUuid = player.getUUID();
            this.playerName = player.getName().getString();
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.yaw = player.getYRot();
            this.pitch = player.getXRot();
            this.dimension = player.level().dimension();
            this.deathTime = System.currentTimeMillis();
        }

        /** 是否在可复活时间内死亡 */
        boolean isRecent() {
            return System.currentTimeMillis() - deathTime <= playerDeathTime * 1000L;
        }

        /** 转为位置快照 */
        PositionSnapshot toPositionSnapshot() {
            return new PositionSnapshot(x, y, z, yaw, pitch, dimension);
        }
    }

    // ========================================================
    // 模组初始化
    // ========================================================

    @Override
    public void onInitialize() {
        // --- 第1步：注册物品 ---
        ResourceKey<Item> getPassKey = ResourceKey.create(
                Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "get_pass")
        );
        GET_PASS = Registry.register(
                BuiltInRegistries.ITEM,
                getPassKey,
                new GetPassItem(new Item.Properties().setId(getPassKey).stacksTo(1))
        );

        ResourceKey<Item> magnifyingGlassKey = ResourceKey.create(
                Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "magnifying_glass")
        );
        MAGNIFYING_GLASS = Registry.register(
                BuiltInRegistries.ITEM,
                magnifyingGlassKey,
                new MagnifyingGlassItem(new Item.Properties().setId(magnifyingGlassKey).stacksTo(1))
        );

        // --- 第2步：将物品添加到创造模式物品栏（工具分类） ---
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            entries.accept(GET_PASS);
            entries.accept(MAGNIFYING_GLASS);
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
    private static void registerCommands(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("intervention")
                        // 允许任何玩家执行（不需要OP权限）
                        .requires(source -> source.hasPermission(0))

                        // ----- 取消 -----
                        .then(Commands.literal("cancel")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleCancel(player);
                                })
                        )

                        // ----- 确认传送（回溯） -----
                        .then(Commands.literal("confirm")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleConfirm(player);
                                })
                        )

                        // ----- 介入玩家 -----
                        .then(Commands.literal("intervene_player")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleIntervenePlayer(player);
                                })
                        )

                        // ----- 介入20秒 -----
                        .then(Commands.literal("intervene_20s")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleTimeIntervene(player, 20);
                                })
                        )

                        // ----- 介入1分钟（60秒） -----
                        .then(Commands.literal("intervene_1min")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleTimeIntervene(player, 60);
                                })
                        )

                        // ----- 介入3分钟（180秒） -----
                        .then(Commands.literal("intervene_3min")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleTimeIntervene(player, 180);
                                })
                        )

                        // ----- 计分板配置（创造模式KP_9触发） -----
                        .then(Commands.literal("scoreboard_config")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleScoreboardConfig(player);
                                })
                        )

                        // ----- 各选项计分板区间设置（创造模式可用） -----
                        .then(Commands.literal("intervene_20s_SCset")
                                .then(Commands.argument("sb", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .then(Commands.argument("min", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .then(Commands.argument("max", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                        .executes(context -> {
                                                            ServerPlayer player = context.getSource().getPlayer();
                                                            if (player == null) return 0;
                                                            return handleSCset(player, sc20s, "介入20秒",
                                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "sb"),
                                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "min"),
                                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "max"));
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("intervene_1min_SCset")
                                .then(Commands.argument("sb", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .then(Commands.argument("min", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .then(Commands.argument("max", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                        .executes(context -> {
                                                            ServerPlayer player = context.getSource().getPlayer();
                                                            if (player == null) return 0;
                                                            return handleSCset(player, sc1min, "介入1分钟",
                                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "sb"),
                                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "min"),
                                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "max"));
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("intervene_3min_SCset")
                                .then(Commands.argument("sb", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .then(Commands.argument("min", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .then(Commands.argument("max", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                        .executes(context -> {
                                                            ServerPlayer player = context.getSource().getPlayer();
                                                            if (player == null) return 0;
                                                            return handleSCset(player, sc3min, "介入3分钟",
                                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "sb"),
                                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "min"),
                                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "max"));
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("intervene_player_SCset")
                                .then(Commands.argument("sb", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .then(Commands.argument("min", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .then(Commands.argument("max", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                        .executes(context -> {
                                                            ServerPlayer player = context.getSource().getPlayer();
                                                            if (player == null) return 0;
                                                            return handleSCset(player, scPlayer, "介入玩家",
                                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "sb"),
                                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "min"),
                                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "max"));
                                                        })
                                                )
                                        )
                                )
                        )

                        // ----- 重置各选项计分板 -----
                        .then(Commands.literal("intervene_20s_SCreset")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleSCreset(player, sc20s, "介入20秒");
                                })
                        )
                        .then(Commands.literal("intervene_1min_SCreset")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleSCreset(player, sc1min, "介入1分钟");
                                })
                        )
                        .then(Commands.literal("intervene_3min_SCreset")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleSCreset(player, sc3min, "介入3分钟");
                                })
                        )
                        .then(Commands.literal("intervene_player_SCreset")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleSCreset(player, scPlayer, "介入玩家");
                                })
                        )

                        // ----- 触发介入（KP_9非创造模式，或右键物品） -----
                        .then(Commands.literal("trigger_intervene")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleTriggerIntervene(player);
                                })
                        )

                        // ----- 设置可复活时间（创造模式可用） -----
                        .then(Commands.literal("PlayerDeathTime")
                                .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayer();
                                            if (player == null) return 0;
                                            int secs = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seconds");
                                            return handlePlayerDeathTime(player, secs);
                                        })
                                )
                        )

                        // ----- 设置介入玩家死亡概率（创造模式可用） -----
                        .then(Commands.literal("intervene_player_DeathPercent")
                                .then(Commands.argument("percent", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayer();
                                            if (player == null) return 0;
                                            int pct = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "percent");
                                            return handleDeathPercent(player, pct);
                                        })
                                )
                        )

                        // ----- 重置冷却（创造模式可用） -----
                        .then(Commands.literal("resetcooldown")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleResetCooldown(player);
                                })
                        )

                        // ----- 设置复活概率（创造模式可用） -----
                        .then(Commands.literal("resurrection_percent")
                                .then(Commands.argument("percent", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayer();
                                            if (player == null) return 0;
                                            int percent = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "percent");
                                            return handleResurrectionPercent(player, percent);
                                        })
                                )
                        )

                        // ----- 撤销传送（30秒内可用） -----
                        .then(Commands.literal("undo")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleUndo(player);
                                })
                        )

                        // ----- 查看当前配置 -----
                        .then(Commands.literal("check_config")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleCheckConfig(player);
                                })
                        )

                        // ----- 帮助 -----
                        .then(Commands.literal("help")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    return handleHelp(player);
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
            if (entity instanceof ServerPlayer player) {
                DeathRecord record = new DeathRecord(player);
                recentDeaths.put(player.getUUID(), record);
            }
        });

        // ----- 玩家断开连接时清理相关数据 -----
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUUID();
            pendingTeleports.remove(uuid);
            positionHistory.remove(uuid);
        });
    }

    // ========================================================
    // 服务器每刻处理
    // ========================================================

    /** 记录在线玩家位置（每秒一次）+ 清理过期数据 */
    private static void serverTick(MinecraftServer server) {
        // 每20刻（1秒）记录一次所有在线玩家的位置
        if (server.getTickCount() % 20 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                recordPosition(player);
            }
        }

        // 处理待复活玩家（延迟切换游戏模式）
        int currentTick = server.getTickCount();
        Iterator<Map.Entry<UUID, Integer>> revivalIt = pendingRevivals.entrySet().iterator();
        while (revivalIt.hasNext()) {
            Map.Entry<UUID, Integer> entry = revivalIt.next();
            if (currentTick >= entry.getValue()) {
                revivalIt.remove();
                ServerPlayer deadPlayer = server.getPlayerList().getPlayer(entry.getKey());
                if (deadPlayer != null && deadPlayer.isSpectator()) {
                    // 使用 gamemode 指令等效方式切回生存模式
                    deadPlayer.setGameMode(GameType.SURVIVAL);
                    // 关闭旁观容器 → 恢复为物品栏界面
                    deadPlayer.doCloseContainer();
                    // 完整重发物品栏到客户端
                    deadPlayer.containerMenu.sendAllDataToRemote();
                    // 恢复满血
                    deadPlayer.setHealth(deadPlayer.getMaxHealth());
                    // 附加负面效果
                    deadPlayer.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 2));
                    deadPlayer.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2));
                    deadPlayer.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 1));
                    // 提示
                    deadPlayer.sendSystemMessage(
                            Component.literal("似乎有股神奇的力量将你复活了...身体涌上一股虚弱感")
                                    .withStyle(ChatFormatting.GREEN)
                    );
                    deadPlayer.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
                }
            }
        }

        // 每10秒清理一次过期数据
        if (server.getTickCount() % 200 == 0) {
            cleanupExpiredData(server);
        }
    }

    /** 记录单个玩家的位置 */
    private static void recordPosition(ServerPlayer player) {
        // 旁观者不记录位置（避免污染历史数据）
        if (player.isSpectator()) return;
        ArrayList<PositionSnapshot> history = positionHistory.computeIfAbsent(
                player.getUUID(), k -> new ArrayList<>());
        history.add(new PositionSnapshot(player));
        // 只保留最近200条（200秒 = 3分20秒，足够覆盖"介入3分钟"）
        while (history.size() > 200) {
            history.remove(0);
        }
    }

    /** 清理过期数据 */
    private static void cleanupExpiredData(MinecraftServer server) {
        long now = System.currentTimeMillis();

        // 清理过期的死亡记录（超过30秒的）
        recentDeaths.entrySet().removeIf(entry -> !entry.getValue().isRecent());

        // 清理过期的回溯记录，并通知玩家
        Iterator<Map.Entry<UUID, RevertRecord>> revertIt = revertRecords.entrySet().iterator();
        while (revertIt.hasNext()) {
            Map.Entry<UUID, RevertRecord> entry = revertIt.next();
            if (entry.getValue().isExpired()) {
                revertIt.remove();
                ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
                if (p != null) {
                    p.sendSystemMessage(
                            Component.literal("回溯失败(回溯窗口已失效)").withStyle(ChatFormatting.RED)
                    );
                    p.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F, 0.5F);
                }
            }
        }

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
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    player.sendSystemMessage(
                            Component.literal("确认超时！介入过去物品已进入14分30秒冷却。")
                                    .withStyle(ChatFormatting.RED)
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
    private static int handleCancel(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PendingTeleport pending = pendingTeleports.remove(uuid);

        if (pending != null) {
            // 有待确认传送时取消 → 14分30秒冷却
            itemCooldowns.put(uuid, System.currentTimeMillis() + 870_000);
            player.sendSystemMessage(
                    Component.literal("已取消介入。物品进入14分30秒冷却。")
                            .withStyle(ChatFormatting.RED)
            );
        } else {
            // 无待确认传送时取消 → 不消耗冷却
            player.sendSystemMessage(
                    Component.literal("已取消操作。（未消耗冷却）")
                            .withStyle(ChatFormatting.GRAY)
            );
        }
        return 1;
    }

    /**
     * 处理"回溯"（确认传送） —— 传送到目标位置，施加15分钟冷却
     */
    private static int handleConfirm(ServerPlayer player) {
        UUID uuid = player.getUUID();

        // --- 检查沉默标签（Silenced）---
        if (isSilenced(player)) {
            return 0;
        }

        PendingTeleport pending = pendingTeleports.remove(uuid);

        if (pending == null) {
            player.sendSystemMessage(
                    Component.literal("没有待确认的介入操作。").withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        if (pending.isExpired()) {
            itemCooldowns.put(uuid, System.currentTimeMillis() + 870_000);
            player.sendSystemMessage(
                    Component.literal("确认窗口已过期！物品进入14分30秒冷却。")
                            .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        // 执行传送
        PositionSnapshot target = pending.target;
        MinecraftServer server = player.getServer();
        boolean teleportSuccess = false;

        if (server != null) {
            ServerLevel targetWorld = server.getLevel(target.dimension);
            if (targetWorld != null) {
                teleportSuccess = player.teleportTo(targetWorld, target.x, target.y, target.z,
                        java.util.Set.<Relative>of(), target.yaw, target.pitch, false);
            } else {
                System.err.println("[Intervention] 传送失败：目标维度未加载 (" + target.dimension.location() + ")");
            }
        }

        if (!teleportSuccess) {
            // 传送失败：返还待确认状态，不消耗冷却
            pendingTeleports.put(uuid, pending);
            player.sendSystemMessage(
                    Component.literal("传送失败，请重试。").withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        // 15分钟冷却
        itemCooldowns.put(uuid, System.currentTimeMillis() + 900_000);

        player.sendSystemMessage(
                Component.literal("成功回溯到" + pending.durationSeconds + "秒前的位置！")
                        .withStyle(ChatFormatting.GREEN)
        );

        // 播放传送音效
        player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0));

        return 1;
    }

    /**
     * 处理"介入玩家" —— 复活最近可配置时间内死亡且处于旁观模式的玩家
     * 成功时传送至死亡前10秒位置，切换生存模式，附加负面效果
     */
    private static int handleIntervenePlayer(ServerPlayer user) {
        UUID userUuid = user.getUUID();

        // --- 检查沉默标签（Silenced）---
        if (isSilenced(user)) {
            return 0;
        }

        // --- 检查冷却 ---
        long cooldownRemaining = getCooldownRemaining(userUuid);
        if (cooldownRemaining > 0) {
            sendCooldownMessage(user, cooldownRemaining);
            return 0;
        }

        // --- 检查计分板区间 ---
        String blocked = checkScoreRange(user, scPlayer);
        if (blocked != null) {
            user.sendSystemMessage(
                    Component.literal("以我现在的能力做不到...").withStyle(ChatFormatting.YELLOW)
            );
            return 0;
        }

        // --- 使用者死亡概率检查 ---
        if (Math.random() * 100 < intervenePlayerDeathPercent) {
            user.kill(user.level());
            user.sendSystemMessage(
                    Component.literal("你被介入过去的力量反噬了...").withStyle(ChatFormatting.DARK_RED)
            );
            itemCooldowns.put(userUuid, System.currentTimeMillis() + 1_440_000);
            return 0;
        }

        // 找最近死亡且处于旁观模式的玩家（排除使用者自己）
        DeathRecord targetDeath = null;
        UUID targetUuid = null;
        for (Map.Entry<UUID, DeathRecord> entry : recentDeaths.entrySet()) {
            DeathRecord record = entry.getValue();
            if (record.isRecent() && !record.playerUuid.equals(userUuid)) {
                // 检查目标是否在线且处于旁观模式
                ServerPlayer target = user.getServer() != null
                        ? user.getServer().getPlayerList().getPlayer(entry.getKey())
                        : null;
                if (target != null && target.isSpectator()) {
                    targetDeath = record;
                    targetUuid = entry.getKey();
                    break;
                }
            }
        }

        if (targetDeath == null) {
            // 没有符合条件的目标 → 30秒冷却
            itemCooldowns.put(userUuid, System.currentTimeMillis() + 30_000);
            user.sendSystemMessage(
                    Component.literal("没有最近" + playerDeathTime + "秒内死亡的其他玩家可以介入。")
                            .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        // 按配置概率判定
        boolean success = (Math.random() * 100) < resurrectionPercent;

        if (success) {
            ServerPlayer deadPlayer = user.getServer().getPlayerList().getPlayer(targetUuid);
            if (deadPlayer != null) {
                // 传送到死亡前20秒的位置
                ArrayList<PositionSnapshot> deadHistory = positionHistory.get(targetUuid);
                PositionSnapshot reviveTarget = targetDeath.toPositionSnapshot();
                if (deadHistory != null && !deadHistory.isEmpty()) {
                    int idx = deadHistory.size() - 1 - 20;
                    if (idx < 0) idx = 0;
                    reviveTarget = deadHistory.get(idx);
                }
                ServerLevel reviveWorld = user.getServer().getLevel(reviveTarget.dimension);
                if (reviveWorld != null) {
                    deadPlayer.teleportTo(reviveWorld,
                            reviveTarget.x, reviveTarget.y, reviveTarget.z,
                            java.util.Set.<Relative>of(), reviveTarget.yaw, reviveTarget.pitch, false);
                }
                // 0.5秒（10 tick）后使用 gamemode 指令方式切回生存模式
                pendingRevivals.put(targetUuid, user.getServer().getTickCount() + 10);
            }

            user.sendSystemMessage(
                    Component.literal("介入成功！已复活玩家 " + targetDeath.playerName)
                            .withStyle(ChatFormatting.GREEN)
            );
            user.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);

            // 成功后重置概率
            int resetTo = (configuredResurrectionPercent > 0) ? configuredResurrectionPercent : 50;
            if (resurrectionPercent != resetTo) {
                resurrectionPercent = resetTo;
            }
        } else {
            int oldPercent = resurrectionPercent;
            resurrectionPercent = Math.min(100, resurrectionPercent + 15);
            user.sendSystemMessage(
                    Component.literal("介入失败……未能复活 " + targetDeath.playerName)
                            .withStyle(ChatFormatting.RED)
            );
            user.sendSystemMessage(
                    Component.literal("复活概率已从 " + oldPercent + "% 提升至 " + resurrectionPercent + "%")
                            .withStyle(ChatFormatting.YELLOW)
            );
            user.playSound(SoundEvents.GLASS_BREAK, 1.0F, 1.0F);
        }

        // 无论成功与否，施加24分钟冷却
        itemCooldowns.put(userUuid, System.currentTimeMillis() + 1_440_000);

        // 移除死亡记录
        recentDeaths.remove(targetUuid);

        return 1;
    }

    /**
     * 处理"介入X秒/分" —— 直接传送回指定时间前的位置
     */
    private static int handleTimeIntervene(ServerPlayer player, int secondsAgo) {
        UUID uuid = player.getUUID();

        // --- 检查沉默标签（Silenced）---
        if (isSilenced(player)) {
            return 0;
        }

        // --- 检查冷却（防止通过聊天按钮绕过冷却） ---
        long cooldownRemaining = getCooldownRemaining(uuid);
        if (cooldownRemaining > 0) {
            sendCooldownMessage(player, cooldownRemaining);
            return 0;
        }

        // --- 检查计分板区间 ---
        ScoreRange range = (secondsAgo == 20) ? sc20s : (secondsAgo == 60) ? sc1min : sc3min;
        String blocked = checkScoreRange(player, range);
        if (blocked != null) {
            player.sendSystemMessage(
                    Component.literal("以我现在的能力做不到...").withStyle(ChatFormatting.YELLOW)
            );
            return 0;
        }

        ArrayList<PositionSnapshot> history = positionHistory.get(uuid);

        if (history == null || history.isEmpty()) {
            player.sendSystemMessage(
                    Component.literal("没有足够的位置历史记录，请等待一段时间后再试。")
                            .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        // 获取 secondsAgo 秒前的位置
        int index = history.size() - 1 - secondsAgo;
        if (index < 0) {
            index = 0; // 如果历史不够，使用最旧的一条
        }
        PositionSnapshot target = history.get(index);

        // 保存回溯点（传送前的位置），回溯窗口：20秒→20秒，1分钟/3分钟→30秒
        int revertWindow = (secondsAgo == 20) ? 20 : 30;
        PositionSnapshot undoPoint = new PositionSnapshot(player);
        revertRecords.put(uuid, new RevertRecord(undoPoint, revertWindow));

        // 执行传送
        MinecraftServer server = player.getServer();
        boolean teleportSuccess = false;

        if (server != null) {
            ServerLevel targetWorld = server.getLevel(target.dimension);
            if (targetWorld != null) {
                teleportSuccess = player.teleportTo(targetWorld, target.x, target.y, target.z,
                        java.util.Set.<Relative>of(), target.yaw, target.pitch, false);
            }
        }

        if (!teleportSuccess) {
            // 传送失败：清除撤销记录
            revertRecords.remove(uuid);
            player.sendSystemMessage(
                    Component.literal("传送失败，请重试。").withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        // 15分钟冷却
        itemCooldowns.put(uuid, System.currentTimeMillis() + 900_000);

        String timeDesc;
        if (secondsAgo < 60) {
            timeDesc = secondsAgo + "秒前";
        } else {
            timeDesc = (secondsAgo / 60) + "分钟前";
        }

        // 成功消息 + 可点击回溯按钮
        MutableComponent backtrackBtn = Component.literal("[回溯]")
                .withStyle(ChatFormatting.GOLD)
                .withStyle(style -> style.withClickEvent(
                        new ClickEvent.RunCommand("/intervention undo")
                ));
        player.sendSystemMessage(
                Component.literal("成功回溯到 " + timeDesc + " 的位置！")
                        .withStyle(ChatFormatting.GREEN)
        );
        player.sendSystemMessage(
                Component.empty()
                        .append(Component.literal("（" + revertWindow + "秒内可点击 ").withStyle(ChatFormatting.GRAY))
                        .append(backtrackBtn)
                        .append(Component.literal(" 撤销传送回到原处）").withStyle(ChatFormatting.GRAY))
        );

        // 播放传送音效
        player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0));

        return 1;
    }

    /**
     * 处理"计分板配置" —— 显示4个选项各自的计分板限制（KP_9触发）
     */
    private static int handleScoreboardConfig(ServerPlayer player) {
        if (!player.isCreative()) {
            player.sendSystemMessage(
                    Component.literal("计分板配置仅限创造模式使用！").withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        player.sendSystemMessage(
                Component.literal("========== 计分板配置 ==========").withStyle(ChatFormatting.GOLD)
        );
        player.sendSystemMessage(Component.empty());

        // 列出所有可用计分板
        ServerScoreboard scoreboard = player.getServer() != null
                ? player.getServer().getScoreboard() : null;
        java.util.List<net.minecraft.world.scores.Objective> objList = scoreboard != null
                ? new java.util.ArrayList<>(scoreboard.getObjectives()) : java.util.Collections.emptyList();

        // 为每个选项生成配置链接 + 重置按钮
        String[] labels = {"介入20秒", "介入1分钟", "介入3分钟", "介入玩家"};
        String[] cmds = {"intervene_20s_SCset", "intervene_1min_SCset", "intervene_3min_SCset", "intervene_player_SCset"};
        String[] resetCmds = {"intervene_20s_SCreset", "intervene_1min_SCreset", "intervene_3min_SCreset", "intervene_player_SCreset"};
        ScoreRange[] ranges = {sc20s, sc1min, sc3min, scPlayer};

        for (int i = 0; i < labels.length; i++) {
            ScoreRange r = ranges[i];
            String status = r.isConfigured()
                    ? "[" + r.scoreboard + " " + fmtRange(r.min) + "~" + fmtRange(r.max) + "]"
                    : "[未限制]";
            String setCmd = "/intervention " + cmds[i] + " ? ? ?";
            String resetCmd = "/intervention " + resetCmds[i];
            MutableComponent btn = Component.literal("[" + labels[i] + "]")
                    .withStyle(r.isConfigured() ? ChatFormatting.GREEN : ChatFormatting.WHITE)
                    .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(setCmd)));
            MutableComponent resetBtn = Component.literal("[重置]")
                    .withStyle(ChatFormatting.RED)
                    .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(resetCmd)));
            player.sendSystemMessage(
                    Component.empty().append(btn).append(Component.literal(" " + status + " ").withStyle(ChatFormatting.GRAY)).append(resetBtn)
            );
        }

        player.sendSystemMessage(Component.empty());

        // 可用计分板列表
        if (!objList.isEmpty()) {
            player.sendSystemMessage(Component.literal("▸ 可用计分板:").withStyle(ChatFormatting.WHITE));
            for (var obj : objList) {
                player.sendSystemMessage(Component.literal("  " + obj.getName()).withStyle(ChatFormatting.GRAY));
            }
        } else {
            player.sendSystemMessage(Component.literal("暂无计分板，请用 /scoreboard objectives add 创建").withStyle(ChatFormatting.GRAY));
        }

        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(
                Component.literal("━━━━ [介入玩家] 专属设置 ━━━━").withStyle(ChatFormatting.GOLD)
        );

        // 死亡概率
        MutableComponent deathPctBtn = Component.literal("[死亡概率]")
                .withStyle(ChatFormatting.DARK_RED)
                .withStyle(style -> style.withClickEvent(
                        new ClickEvent.RunCommand("/intervention intervene_player_DeathPercent ?")
                ));
        player.sendSystemMessage(
                Component.empty().append(deathPctBtn)
                        .append(Component.literal(" 当前: " + intervenePlayerDeathPercent + "%（使用者介入时的死亡概率）").withStyle(ChatFormatting.GRAY))
        );

        // 可复活时间
        MutableComponent deathTimeBtn = Component.literal("[复活时间]")
                .withStyle(ChatFormatting.AQUA)
                .withStyle(style -> style.withClickEvent(
                        new ClickEvent.RunCommand("/intervention PlayerDeathTime ?")
                ));
        player.sendSystemMessage(
                Component.empty().append(deathTimeBtn)
                        .append(Component.literal(" 当前: " + playerDeathTime + "秒（死亡后可被复活的时间）").withStyle(ChatFormatting.GRAY))
        );

        // 复活成功率
        MutableComponent resPctBtn = Component.literal("[复活成功率]")
                .withStyle(ChatFormatting.GOLD)
                .withStyle(style -> style.withClickEvent(
                        new ClickEvent.RunCommand("/intervention resurrection_percent ?")
                ));
        player.sendSystemMessage(
                Component.empty().append(resPctBtn)
                        .append(Component.literal(" 当前: " + resurrectionPercent + "%（成功复活目标的概率）").withStyle(ChatFormatting.GRAY))
        );

        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(
                Component.literal("━━━━ 使用规则 ━━━━").withStyle(ChatFormatting.GOLD)
        );
        player.sendSystemMessage(
                Component.literal("格式: /intervention <选项>_SCset <计分板> <最小值|infinity> <最大值|infinity>")
                        .withStyle(ChatFormatting.WHITE)
        );
        player.sendSystemMessage(
                Component.literal("infinity = 无限小(左)或无限大(右)  |  计分板必须已存在  |  最小值 ≤ 最大值")
                        .withStyle(ChatFormatting.GRAY)
        );
        player.sendSystemMessage(
                Component.literal("在区间内 → 可用  |  在区间外 → 提示'以我现在的能力做不到...'且不进入冷却")
                        .withStyle(ChatFormatting.GRAY)
        );

        player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.5F, 1.0F);
        return 1;
    }

    /**
     * 重置指定选项的计分板限制
     */
    private static int handleSCreset(ServerPlayer player, ScoreRange range, String name) {
        if (!player.isCreative()) {
            player.sendSystemMessage(
                    Component.literal("计分板配置仅限创造模式使用！").withStyle(ChatFormatting.RED)
            );
            return 0;
        }
        range.scoreboard = null;
        range.min = Integer.MIN_VALUE;
        range.max = Integer.MAX_VALUE;
        player.sendSystemMessage(
                Component.literal("已重置 " + name + " 的计分板限制").withStyle(ChatFormatting.GREEN)
        );
        player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5F, 1.0F);
        return 1;
    }

    private static String fmtRange(int v) {
        if (v == Integer.MIN_VALUE) return "-∞";
        if (v == Integer.MAX_VALUE) return "∞";
        return String.valueOf(v);
    }

    /**
     * 通用 SCset 处理器 —— 设置指定选项的计分板区间
     */
    private static int handleSCset(ServerPlayer player, ScoreRange range,
                                    String name, String sbName, String minStr, String maxStr) {
        if (!player.isCreative()) {
            player.sendSystemMessage(
                    Component.literal("计分板配置仅限创造模式使用！").withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        // 解析最小值
        int min;
        if (minStr.equalsIgnoreCase("infinity")) {
            min = Integer.MIN_VALUE;
        } else {
            try { min = Integer.parseInt(minStr); } catch (NumberFormatException e) {
                player.sendSystemMessage(Component.literal("错误: 最小值必须是数字或 infinity").withStyle(ChatFormatting.RED));
                return 0;
            }
        }

        // 解析最大值
        int max;
        if (maxStr.equalsIgnoreCase("infinity")) {
            max = Integer.MAX_VALUE;
        } else {
            try { max = Integer.parseInt(maxStr); } catch (NumberFormatException e) {
                player.sendSystemMessage(Component.literal("错误: 最大值必须是数字或 infinity").withStyle(ChatFormatting.RED));
                return 0;
            }
        }

        // 验证 min <= max
        if (min > max) {
            player.sendSystemMessage(
                    Component.literal("错误: 最小值必须 ≤ 最大值").withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        if ("clear".equals(sbName)) {
            range.scoreboard = null;
            range.min = Integer.MIN_VALUE;
            range.max = Integer.MAX_VALUE;
            player.sendSystemMessage(
                    Component.literal("已清除 " + name + " 的计分板限制").withStyle(ChatFormatting.GREEN)
            );
        } else {
            // 验证计分板存在
            ServerScoreboard sb = player.getServer() != null
                    ? player.getServer().getScoreboard() : null;
            if (sb == null || sb.getObjective(sbName) == null) {
                player.sendSystemMessage(
                        Component.literal("错误: 计分板 '" + sbName + "' 不存在").withStyle(ChatFormatting.RED)
                );
                return 0;
            }
            range.set(sbName, min, max);
            player.sendSystemMessage(
                    Component.literal("已设置 " + name + ": " + sbName + " [" + fmtRange(min) + " ~ " + fmtRange(max) + "]")
                            .withStyle(ChatFormatting.GREEN)
            );
        }
        player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5F, 1.0F);
        return 1;
    }

    /**
     * 获取玩家在指定计分板上的分数
     */
    private static int getPlayerScore(ServerPlayer player, String scoreboardName) {
        if (scoreboardName == null) return 0;
        ServerScoreboard sb = player.getServer() != null
                ? player.getServer().getScoreboard()
                : null;
        if (sb == null) return 0;
        var obj = sb.getObjective(scoreboardName);
        if (obj == null) return 0;
        ReadOnlyScoreInfo info = sb.getPlayerScoreInfo(player, obj);
        return info != null ? info.value() : 0;
    }

    /**
     * 检查玩家是否在指定计分板区间内
     * @return null 表示通过，非null 为拒绝原因（用于显示消息）
     */
    private static String checkScoreRange(ServerPlayer player, ScoreRange range) {
        if (!range.isConfigured()) return null;
        int score = getPlayerScore(player, range.scoreboard);
        if (!range.isWithin(score)) {
            return range.scoreboard;
        }
        return null;
    }

    /**
     * 处理"重置冷却" —— 立即清除玩家的介入过去物品冷却时间
     * 仅创造模式可用
     */
    private static int handleResetCooldown(ServerPlayer player) {
        if (!player.isCreative()) {
            player.sendSystemMessage(
                    Component.literal("重置冷却仅限创造模式使用！").withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        UUID uuid = player.getUUID();
        if (itemCooldowns.remove(uuid) != null) {
            player.sendSystemMessage(
                    Component.literal("已重置介入过去冷却时间！").withStyle(ChatFormatting.GREEN)
            );
            player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5F, 1.0F);
        } else {
            player.sendSystemMessage(
                    Component.literal("当前没有在冷却中，无需重置。").withStyle(ChatFormatting.GRAY)
            );
        }
        return 1;
    }

    /**
     * 处理"设置复活概率" —— 设置介入玩家的复活成功率
     * 仅创造模式可用
     */
    private static int handleResurrectionPercent(ServerPlayer player, int percent) {
        if (!player.isCreative()) {
            player.sendSystemMessage(
                    Component.literal("设置复活概率仅限创造模式使用！").withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        resurrectionPercent = percent;
        configuredResurrectionPercent = percent;
        player.sendSystemMessage(
                Component.literal("已设置复活概率: " + percent + "%（成功后重置为此值）").withStyle(ChatFormatting.GREEN)
        );
        player.sendSystemMessage(
                Component.literal("现在介入玩家有 " + percent + "% 概率成功复活。")
                        .withStyle(ChatFormatting.GRAY)
        );
        player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5F, 1.0F);
        return 1;
    }

    /**
     * 设置可复活玩家的死亡时间上限
     */
    private static int handlePlayerDeathTime(ServerPlayer player, int seconds) {
        if (!player.isCreative()) {
            player.sendSystemMessage(
                    Component.literal("设置复活时间仅限创造模式使用！").withStyle(ChatFormatting.RED)
            );
            return 0;
        }
        playerDeathTime = seconds;
        player.sendSystemMessage(
                Component.literal("已设置可复活时间: " + seconds + " 秒").withStyle(ChatFormatting.GREEN)
        );
        player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5F, 1.0F);
        return 1;
    }

    /**
     * 设置介入玩家时使用者死亡概率
     */
    private static int handleDeathPercent(ServerPlayer player, int percent) {
        if (!player.isCreative()) {
            player.sendSystemMessage(
                    Component.literal("设置死亡概率仅限创造模式使用！").withStyle(ChatFormatting.RED)
            );
            return 0;
        }
        intervenePlayerDeathPercent = percent;
        player.sendSystemMessage(
                Component.literal("已设置介入玩家死亡概率: " + percent + "%").withStyle(ChatFormatting.GREEN)
        );
        player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5F, 1.0F);
        return 1;
    }

    /**
     * 查看当前计分板配置和玩家分数
     */
    private static int handleCheckConfig(ServerPlayer player) {
        player.sendSystemMessage(
                Component.literal("========== 当前配置 ==========").withStyle(ChatFormatting.GOLD)
        );

        ScoreRange[] ranges = {sc20s, sc1min, sc3min, scPlayer};
        String[] names = {"介入20秒", "介入1分钟", "介入3分钟", "介入玩家"};

        for (int i = 0; i < ranges.length; i++) {
            ScoreRange r = ranges[i];
            if (r.isConfigured()) {
                int score = getPlayerScore(player, r.scoreboard);
                boolean canUse = r.isWithin(score);
                player.sendSystemMessage(
                        Component.literal(names[i] + ": " + r.scoreboard
                                + " [" + fmtRange(r.min) + "~" + fmtRange(r.max) + "]"
                                + "  你的分数: " + score
                                + (canUse ? " ✓" : " ✗不可用"))
                                .withStyle(canUse ? ChatFormatting.GREEN : ChatFormatting.RED)
                );
            } else {
                player.sendSystemMessage(
                        Component.literal(names[i] + ": 未限制").withStyle(ChatFormatting.GRAY)
                );
            }
        }
        return 1;
    }

    /**
     * 处理"帮助" —— 显示所有指令的帮助信息
     */
    private static int handleHelp(ServerPlayer player) {
        // 灰色作者署名
        player.sendSystemMessage(
                Component.literal("This mod By.才羽モモイ").withStyle(ChatFormatting.GRAY)
        );

        player.sendSystemMessage(Component.empty());

        // 各指令说明：指令名绿色，描述黄色
        sendHelpLine(player, "/intervention cancel", "取消操作");
        sendHelpLine(player, "/intervention check_config", "查看配置");
        sendHelpLine(player, "/intervention confirm", "确认介入操作[几乎无用]");
        sendHelpLine(player, "/intervention intervene_1min", "介入1分钟");
        sendHelpLine(player, "/intervention intervene_1min_SCreset", "1分钟计分板重置");
        sendHelpLine(player, "/intervention intervene_1min_SCset <计分板项> <最小值 | infinity> <最大值 | infinity>", "设置计分板使1分钟介入限制");
        sendHelpLine(player, "/intervention intervene_20s", "介入20秒");
        sendHelpLine(player, "/intervention intervene_20s_SCreset", "20秒计分板重置");
        sendHelpLine(player, "/intervention intervene_20s_SCset <计分板项> <最小值 | infinity> <最大值 | infinity>", "设置计分板使20秒介入限制");
        sendHelpLine(player, "/intervention intervene_3min", "介入3分钟");
        sendHelpLine(player, "/intervention intervene_3min_SCreset", "3分钟计分板重置");
        sendHelpLine(player, "/intervention intervene_3min_SCset <计分板项> <最小值 | infinity> <最大值 | infinity>", "设置计分板使3分钟介入限制");
        sendHelpLine(player, "/intervention intervene_player", "介入玩家");
        sendHelpLine(player, "/intervention intervene_player_DeathPercent <0~100数值>", "介入玩家自身死亡概率");
        sendHelpLine(player, "/intervention intervene_player_SCreset", "介入玩家计分板重置");
        sendHelpLine(player, "/intervention intervene_player_SCset <计分板项> <最小值 | infinity> <最大值 | infinity>", "设置计分板使介入玩家限制");
        sendHelpLine(player, "/intervention PlayerDeathTime <单位为秒>", "设置可介入玩家死亡时间范围");
        sendHelpLine(player, "/intervention resetcooldown", "重置冷却");
        sendHelpLine(player, "/intervention resurrection_percent <0~100数值>", "设置介入玩家成功概率");
        sendHelpLine(player, "/intervention scoreboard_config", "设置面板 KP_9[小数字键盘9]也可打开 §7默认");
        sendHelpLine(player, "/intervention trigger_intervene", "介入界面");
        sendHelpLine(player, "/intervention undo", "回溯");

        player.sendSystemMessage(Component.empty());

        // 冷却说明
        player.sendSystemMessage(
                Component.literal("有人死亡使用介入玩家冷却为 25 分钟").withStyle(ChatFormatting.RED)
        );
        player.sendSystemMessage(
                Component.literal("介入时间冷却为 15 分钟").withStyle(ChatFormatting.YELLOW)
        );
        player.sendSystemMessage(
                Component.literal("无人死亡使用介入玩家冷却为 30 秒").withStyle(ChatFormatting.GREEN)
        );
        player.sendSystemMessage(
                Component.literal("每次介入玩家失败将会提升15%成功概率").withStyle(ChatFormatting.BLUE)
        );

        player.sendSystemMessage(
                Component.literal("~~感谢游玩魔溃喵~~").withStyle(ChatFormatting.GREEN)
        );

        return 1;
    }

    /**
     * 发送一条帮助行：指令名绿色 + 描述黄色
     */
    private static void sendHelpLine(ServerPlayer player, String command, String description) {
        player.sendSystemMessage(
                Component.empty()
                        .append(Component.literal(command).withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" " + description).withStyle(ChatFormatting.YELLOW))
        );
    }

    /**
     * 处理"回溯" —— 在限时内回溯到传送前原位置
     * 回溯后进入15分钟冷却
     */
    private static int handleUndo(ServerPlayer player) {
        UUID uuid = player.getUUID();

        // --- 检查沉默标签（Silenced）---
        if (isSilenced(player)) {
            return 0;
        }

        RevertRecord revert = revertRecords.get(uuid);

        if (revert == null) {
            player.sendSystemMessage(
                    Component.literal("没有可回溯的传送记录。").withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        if (revert.isExpired()) {
            revertRecords.remove(uuid);
            player.sendSystemMessage(
                    Component.literal("回溯失败(回溯窗口已失效)").withStyle(ChatFormatting.RED)
            );
            player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F, 0.5F);
            return 0;
        }

        // 移除记录并执行回溯
        revertRecords.remove(uuid);

        // 执行回溯传送
        PositionSnapshot target = revert.originalPosition;
        MinecraftServer server = player.getServer();
        boolean teleportSuccess = false;

        if (server != null) {
            ServerLevel targetWorld = server.getLevel(target.dimension);
            if (targetWorld != null) {
                teleportSuccess = player.teleportTo(targetWorld, target.x, target.y, target.z,
                        java.util.Set.<Relative>of(), target.yaw, target.pitch, false);
            }
        }

        if (!teleportSuccess) {
            // 回溯失败：恢复回溯记录，不消耗冷却
            revertRecords.put(uuid, revert);
            player.sendSystemMessage(
                    Component.literal("回溯失败，请重试。").withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        // 15分钟冷却
        itemCooldowns.put(uuid, System.currentTimeMillis() + 900_000);

        player.sendSystemMessage(
                Component.literal("已回溯到原位置！").withStyle(ChatFormatting.GREEN)
        );
        player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0));

        return 1;
    }

    /**
     * 触发介入操作（KP_9按下 或 右键物品时调用）
     * 先检查计分板要求，再显示主菜单
     */
    static int handleTriggerIntervene(ServerPlayer player) {
        UUID uuid = player.getUUID();

        // --- 检查沉默标签（Silenced）---
        if (isSilenced(player)) {
            return 0;
        }

        // --- 检查冷却 ---
        long cooldownRemaining = getCooldownRemaining(uuid);
        if (cooldownRemaining > 0) {
            sendCooldownMessage(player, cooldownRemaining);
            return 0;
        }

        // --- 显示主菜单 ---
        GetPassItem.showMainMenu(player);
        return 1;
    }

    // ========================================================
    // 辅助方法
    // ========================================================

    /**
     * 检查玩家是否被沉默（拥有 Silenced 标签）。
     * 被沉默的玩家无法使用介入过去，并收到黄色提示。
     * @return true 表示被沉默（已提示并阻止使用）
     */
    private static boolean isSilenced(ServerPlayer player) {
        if (player.getTags().contains(SILENCED_TAG)) {
            player.sendSystemMessage(
                    Component.literal("似乎暂时使用不了...").withStyle(ChatFormatting.YELLOW)
            );
            return true;
        }
        return false;
    }

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
    private static void sendCooldownMessage(ServerPlayer player, long remainingMs) {
        long seconds = remainingMs / 1000;
        long minutes = seconds / 60;
        long secs = seconds % 60;

        String timeStr;
        if (minutes > 0) {
            timeStr = minutes + "分" + secs + "秒";
        } else {
            timeStr = secs + "秒";
        }

        player.sendSystemMessage(
                Component.literal("正在冷却中... 剩余 " + timeStr)
                        .withStyle(ChatFormatting.RED)
        );
    }

    // ========================================================
    // 物品类 1 —— 介入过去
    // ========================================================

    /**
     * 介入过去 —— 右键打开操作菜单
     * <p>
     * 功能流程：
     * 1. 右键物品 → 检查冷却和计分板 → 显示主菜单
     * 2. 主菜单有5个选项（取消/介入玩家/介入20秒/介入1分钟/介入3分钟）
     * 3. 点击时间选项 → 直接传送回对应时间前的位置
     * 4. 点击介入玩家 → 按配置概率复活最近可配置时间内死亡的玩家
     * </p>
     */
    public static class GetPassItem extends Item {

        public GetPassItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (level.isClientSide()) {
                // 客户端仅返回 SUCCESS，不执行逻辑
                return InteractionResult.SUCCESS;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            // 核心逻辑：触发介入菜单或确认
            handleTriggerIntervene(serverPlayer);
            // 使用 SUCCESS 保证物品不被消耗
            return InteractionResult.SUCCESS;
        }

        /**
         * 显示主菜单（5个选项）
         */
        private static void showMainMenu(ServerPlayer player) {
            player.sendSystemMessage(
                    Component.empty().append(Component.literal("========== 魔法:介入过去 ==========").withStyle(ChatFormatting.GOLD))
            );

            // 显示玩家名称
            player.sendSystemMessage(
                    Component.literal("玩家: " + player.getName().getString()).withStyle(ChatFormatting.WHITE)
            );

            player.sendSystemMessage(Component.empty()); // 空行

            // 如果有有效的回溯记录，优先显示回溯选项
            RevertRecord revert = revertRecords.get(player.getUUID());
            if (revert != null && !revert.isExpired()) {
                long remainingSecs = revert.remainingSeconds();
                MutableComponent undoOption = Component.literal("[回溯]")
                        .withStyle(ChatFormatting.GOLD)
                        .withStyle(style -> style.withClickEvent(
                                new ClickEvent.RunCommand("/intervention undo")
                        ));
                player.sendSystemMessage(
                        Component.empty().append(undoOption)
                                .append(Component.literal(" - 撤销传送回到原处（剩余 " + remainingSecs + " 秒）").withStyle(ChatFormatting.GRAY))
                );
                player.sendSystemMessage(Component.empty()); // 空行
            }

            // 选项1：取消 —— 红色字体
            MutableComponent cancelOption = Component.literal("[取消]")
                    .withStyle(ChatFormatting.RED)
                    .withStyle(style -> style.withClickEvent(
                            new ClickEvent.RunCommand("/intervention cancel")
                    ));
            player.sendSystemMessage(
                    Component.empty().append(cancelOption).append(Component.literal(" - 关闭界面（不消耗冷却）").withStyle(ChatFormatting.GRAY))
            );

            // 选项2：介入玩家 —— 黄色字体
            MutableComponent playerOption = Component.literal("[介入玩家]")
                    .withStyle(ChatFormatting.YELLOW)
                    .withStyle(style -> style.withClickEvent(
                            new ClickEvent.RunCommand("/intervention intervene_player")
                    ));
            player.sendSystemMessage(
                    Component.empty().append(playerOption).append(Component.literal(" - " + resurrectionPercent + "%复活  " + intervenePlayerDeathPercent + "%自毙  最近" + playerDeathTime + "秒内").withStyle(ChatFormatting.GRAY))
            );

            // 选项3：介入20秒 —— 青色字体
            MutableComponent sec20Option = Component.literal("[介入20秒]")
                    .withStyle(ChatFormatting.AQUA)
                    .withStyle(style -> style.withClickEvent(
                            new ClickEvent.RunCommand("/intervention intervene_20s")
                    ));
            player.sendSystemMessage(
                    Component.empty().append(sec20Option).append(Component.literal(" - 传送回20秒前的位置").withStyle(ChatFormatting.GRAY))
            );

            // 选项4：介入1分钟 —— 黄色字体
            MutableComponent min1Option = Component.literal("[介入1分钟]")
                    .withStyle(ChatFormatting.YELLOW)
                    .withStyle(style -> style.withClickEvent(
                            new ClickEvent.RunCommand("/intervention intervene_1min")
                    ));
            player.sendSystemMessage(
                    Component.empty().append(min1Option).append(Component.literal(" - 传送回1分钟前的位置").withStyle(ChatFormatting.GRAY))
            );

            // 选项5：介入3分钟 —— 绿色字体
            MutableComponent min3Option = Component.literal("[介入3分钟]")
                    .withStyle(ChatFormatting.GREEN)
                    .withStyle(style -> style.withClickEvent(
                            new ClickEvent.RunCommand("/intervention intervene_3min")
                    ));
            player.sendSystemMessage(
                    Component.empty().append(min3Option).append(Component.literal(" - 传送回3分钟前的位置").withStyle(ChatFormatting.GRAY))
            );

            player.sendSystemMessage(
                    Component.literal("点击上方选项进行操作 ↑").withStyle(ChatFormatting.DARK_GRAY)
            );

            // 播放打开音效
            player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.8F, 1.2F);
        }

        /**
         * 显示确认/取消菜单（在选择了介入X秒之后）
         */
        private static void showConfirmMenu(ServerPlayer player, PendingTeleport pending) {
            String timeDesc;
            int secs = pending.durationSeconds;
            if (secs < 60) {
                timeDesc = secs + "秒前";
            } else {
                timeDesc = (secs / 60) + "分钟前";
            }
            long remainingSecs = 30 - (System.currentTimeMillis() - pending.startTime) / 1000;

            player.sendSystemMessage(
                    Component.literal("========== 确认回溯 ==========").withStyle(ChatFormatting.GOLD)
            );
            player.sendSystemMessage(
                    Component.literal("目标: 回溯到 " + timeDesc + " 的位置")
                            .withStyle(ChatFormatting.WHITE)
            );
            player.sendSystemMessage(
                    Component.literal("剩余确认时间: " + remainingSecs + " 秒")
                            .withStyle(ChatFormatting.YELLOW)
            );

            player.sendSystemMessage(Component.empty()); // 空行

            // 取消 —— 红色字体
            MutableComponent cancelOption = Component.literal("[取消]")
                    .withStyle(ChatFormatting.RED)
                    .withStyle(style -> style.withClickEvent(
                            new ClickEvent.RunCommand("/intervention cancel")
                    ));
            player.sendSystemMessage(
                    Component.empty().append(cancelOption).append(Component.literal(" - 放弃传送（14分30秒冷却）").withStyle(ChatFormatting.GRAY))
            );

            // 回溯 —— 黄色字体
            MutableComponent confirmOption = Component.literal("[回溯]")
                    .withStyle(ChatFormatting.YELLOW)
                    .withStyle(style -> style.withClickEvent(
                            new ClickEvent.RunCommand("/intervention confirm")
                    ));
            player.sendSystemMessage(
                    Component.empty().append(confirmOption).append(Component.literal(" - 传送回目标位置（15分钟冷却）").withStyle(ChatFormatting.GRAY))
            );

            // 播放音效
            player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.5F, 1.0F);
        }
    }

    // ========================================================
    // 物品类 2 —— 雪莉放大镜
    // ========================================================

    /**
     * 雪莉放大镜 —— 按住右键将镜片放在眼前 + 1.5x 视角变焦
     * <p>
     * 使用 NONE 动画避免望远镜遮罩，Mixin 实现 1.5x FOV，
     * using_item 模型条件切换为眼前大镜片
     * </p>
     */
    public static class MagnifyingGlassItem extends Item {

        public MagnifyingGlassItem(Properties properties) {
            super(properties);
        }

        @Override
        public ItemUseAnimation getUseAnimation(ItemStack stack) {
            return ItemUseAnimation.SPYGLASS;
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity user) {
            return 72000;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
    }
}
