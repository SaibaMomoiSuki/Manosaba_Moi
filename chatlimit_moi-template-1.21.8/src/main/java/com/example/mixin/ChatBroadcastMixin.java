// Copyright (c) 2026 Moi. Proprietary - Internal Use Only.
package com.example.mixin;

import com.example.ChatLimit_Moi;
import com.example.config.ChatLimitConfig;
import com.example.util.PenetrationCalculator;
import com.example.util.PenetrationResult;
import com.example.util.SpeakVolumeTracker;
import com.example.network.ChatLimitNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.Random;
import java.util.UUID;

/**
 * Intercepts chat message broadcasting for per-player distance/penetration filtering.
 *
 * Primary hook: {@code PlayerList.broadcastChatMessage(PlayerChatMessage, ServerPlayer, ChatType.Bound)}.
 * This gives direct access to sender and message content — no packet reflection needed.
 */
@Mixin(value = PlayerList.class, remap = true)
public abstract class ChatBroadcastMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("chatlimit_moi");

    /** Smallest allowed normal-range multiplier, so the far-left "quiet" end
     *  never divides by zero on the server. */
    @Unique
    private static final double MIN_RANGE_MULTIPLIER = 0.1;

    /**
     * Primary injection point: the dedicated chat broadcast method.
     * Method: broadcastChatMessage(PlayerChatMessage, ServerPlayer, ChatType.Bound)
     */
    @Inject(
        method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onBroadcastChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound chatType, CallbackInfo ci) {
        ChatLimitConfig config = ChatLimit_Moi.INSTANCE.getConfig();

        // Always cancel vanilla broadcast — we handle chat format ourselves
        ci.cancel();

        PlayerList self = (PlayerList) (Object) this;
        String messageText = message.signedContent(); // Returns String in 1.21.8

        if (config.getPlayerSilenceEnabled() && SpeakVolumeTracker.INSTANCE.isPlayerMuted(sender)) {
            return;
        }

        if (!config.getEnabled()) {
            // Distance limits are disabled, but 心声 remains a private channel.
            LOGGER.info("[ChatLimit] broadcastChatMessage fired but mod is DISABLED — using custom format without limits");
            SpeakVolumeTracker.SpeakState disabledState = SpeakVolumeTracker.INSTANCE.getState(sender);
            for (ServerPlayer player : self.getServer().getPlayerList().getPlayers()) {
                if (isPrivateMode(disabledState.getMode())
                        && !player.equals(sender)
                        && !hasHeartListenerTag(player)) {
                    continue;
                }
                Component chat = Component.empty()
                    .append(sender.getDisplayName())
                    .append(Component.literal("\n"))
                    .append(Component.literal(" └─ ").withColor(0xAAAAAA))
                    .append(Component.literal(messageText).withColor(privateMessageColor(disabledState.getMode())));
                player.connection.send(new ClientboundSystemChatPacket(chat, false));
            }
            return;
        }

        LOGGER.info("[ChatLimit] broadcastChatMessage FIRED — sender={}, receivers={}",
            sender.getName().getString(),
            self.getServer().getPlayerList().getPlayerCount());

        SpeakVolumeTracker.SpeakState speakerState = SpeakVolumeTracker.INSTANCE.getState(sender);
        SpeakVolumeTracker.ProcessedSpeech processed = SpeakVolumeTracker.INSTANCE.processSpeech(
            sender, messageText, config.getShoutRangeMultiplier());
        messageText = processed.getText();
        speakerState = new SpeakVolumeTracker.SpeakState(speakerState.getMode(), processed.getMultiplier(),
            speakerState.getAttitude(), speakerState.getTransform());
        ChatLimitNetwork.INSTANCE.sendRestrictionToClient(sender,
            SpeakVolumeTracker.INSTANCE.maxAllowedSliderPosition(sender),
            SpeakVolumeTracker.INSTANCE.restrictionRemainingMillis(sender));
        if (processed.getShout()) SpeakVolumeTracker.INSTANCE.applyShoutDizziness(
            sender, processed.getShoutCharacterCount());

        for (ServerPlayer player : self.getServer().getPlayerList().getPlayers()) {
            if (player.equals(sender)) {
                // Sender sees custom chat format: PlayerName\n └─ Message
                Component senderChat = Component.empty()
                    .append(sender.getDisplayName())
                    .append(Component.literal("\n"))
                    .append(Component.literal(" └─ ").withColor(0xAAAAAA))
                    .append(Component.literal(messageText).withColor(
                        isPrivateMode(speakerState.getMode())
                            ? privateMessageColor(speakerState.getMode())
                            : 0xFFFFFF))
                    .append(hoverLabel(" 成句", processed.getOriginalText()));
                player.connection.send(new ClientboundSystemChatPacket(senderChat, false));
            } else {
                handleReceiver(sender, player, messageText, config, speakerState);
            }
        }
    }

    @Unique
    private void handleReceiver(
        ServerPlayer sender,
        ServerPlayer receiver,
        String messageText,
        ChatLimitConfig config,
        SpeakVolumeTracker.SpeakState state
    ) {
        // ── Mode: HEART — private channel, independent of distance ──
        if (isPrivateMode(state.getMode())) {
            handleHeartMessage(sender, receiver, messageText);
            return;
        }

        double dx = sender.getX() - receiver.getX();
        double dy = sender.getY() - receiver.getY();
        double dz = sender.getZ() - receiver.getZ();

        // ── Mode: NORMAL — continuous range scaling ─────────────
        // The slider multiplier scales how far normal speech carries: deltas are
        // divided by it, so ×2 halves the effective distance (2× range, the old
        // shout behaviour) while <1 shortens it toward a whisper.
        double mult = Math.max(state.getMultiplier(), MIN_RANGE_MULTIPLIER);
        double rangeDx = dx / mult;
        double rangeDy = dy / mult;
        double rangeDz = dz / mult;

        // 1. Check block penetration via raycasting (normal penetration levels)
        PenetrationResult penResult = PenetrationCalculator.INSTANCE.calculate(sender, receiver, config, 1.0);
        if (!penResult.getCanHear()) {
            LOGGER.debug("ChatLimit: blocked by terrain. score={}", penResult.getScore());
            return;
        }

        // 2. Compute continuous degradation factor from axis-scaled distance
        double factor = config.getDegradationFactor(rangeDx, rangeDy, rangeDz);

        LOGGER.info("[ChatLimit] receiver={}, mult={}, dx={}, dy={}, dz={}, effDist={}, factor={}, levelOneMax={}, levelThreeMax={}",
            receiver.getName().getString(), mult, rangeDx, rangeDy, rangeDz,
            config.effectiveDistance(rangeDx, rangeDy, rangeDz), factor,
            config.getChatLevelOneMax(), config.getChatLevelThreeMax());

        if (factor < 0) {
            LOGGER.info("[ChatLimit] BLOCKED — beyond audible range");
            return;
        }

        // 3. Apply gradient degradation: factor 0.0 = white + no ?, factor 1.0 = black + all ?
        String modifiedText;
        int color;
        if (factor <= 0.0) {
            // Clear range: full text, white
            modifiedText = messageText;
            color = 0xFFFFFF;
        } else {
            // Degraded: factor drives both ? probability and color darkness
            modifiedText = replaceWithQuestionMarks(messageText, factor);
            if (modifiedText.isBlank()) return;
            // White (255) → Black (0) as factor goes 0→1
            int gray = (int) (255 * (1.0 - factor));
            color = (gray << 16) | (gray << 8) | gray;
        }

        // Custom chat format: PlayerName\n └─ Message
        // └─ prefix is always gray, player name is never degraded,
        // only the message text goes through degradation
        MutableComponent chatComponent = Component.empty()
            .append(sender.getDisplayName())
            .append(Component.literal("\n"))
            .append(Component.literal(" └─ ").withColor(0xAAAAAA))
            .append(Component.literal(modifiedText).withColor(color));
        receiver.connection.send(new ClientboundSystemChatPacket(
            chatComponent.append(hoverLabel(" 状态", attitudeName(state.getAttitude()))), false));
    }

    @Unique
    private Component hoverLabel(String label, String text) {
        return Component.literal(label).withColor(0xAAAAAA)
            .withStyle(style -> style.withHoverEvent(
                new HoverEvent.ShowText(Component.literal(text))));
    }

    @Unique
    private String attitudeName(int attitude) {
        return SpeakVolumeTracker.INSTANCE.attitudeName(attitude);
    }

    /**
     * Delivers a "心声" (heart voice) message only to the sender and receivers
     * tagged changwu or master. No distance or block-penetration check applies.
     * Uses a dimmed gray style to distinguish it.
     */
    @Unique
    private void handleHeartMessage(ServerPlayer sender, ServerPlayer receiver, String messageText) {
        if (!receiver.equals(sender) && !hasHeartListenerTag(receiver)) {
            return;
        }

        Component heartChat = Component.empty()
            .append(sender.getDisplayName())
            .append(Component.literal("\n"))
            .append(Component.literal(" └─ ").withColor(0xAAAAAA))
            .append(Component.literal(messageText).withColor(privateMessageColor(
                SpeakVolumeTracker.INSTANCE.getState(sender).getMode())));
        receiver.connection.send(new ClientboundSystemChatPacket(heartChat, false));
    }

    @Unique
    private boolean hasHeartListenerTag(ServerPlayer player) {
        for (String tag : player.getTags()) {
            if (tag.equalsIgnoreCase("changwu") || tag.equalsIgnoreCase("master")) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private boolean isPrivateMode(int mode) {
        return mode == SpeakVolumeTracker.MODE_HEART || mode == SpeakVolumeTracker.MODE_ADMIN;
    }

    @Unique
    private int privateMessageColor(int mode) {
        if (mode == SpeakVolumeTracker.MODE_ADMIN) return 0x55FFFF;
        if (mode == SpeakVolumeTracker.MODE_HEART) return 0x666666;
        return 0xFFFFFF;
    }

    @Unique
    private String replaceWithQuestionMarks(String text, double probability) {
        if (text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (char ch : text.toCharArray()) {
            if (ch == ' ') {
                sb.append(' ');
            } else if (random.nextDouble() < probability) {
                sb.append("  ");
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    // ==================== Fallback: broadcastAll ====================

    /**
     * Fallback injection: intercepts {@code PlayerList.broadcastAll(Packet)}.
     * Used when {@code broadcastChatMessage} hook doesn't match.
     */
    @Inject(
        method = "broadcastAll(Lnet/minecraft/network/protocol/Packet;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onBroadcastAll(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof ClientboundPlayerChatPacket chatPacket)) return;

        ChatLimitConfig config = ChatLimit_Moi.INSTANCE.getConfig();

        // Try to extract sender and message from the packet
        UUID senderUUID = chatPacket.sender();
        PlayerList self = (PlayerList) (Object) this;
        ServerPlayer sender = self.getServer().getPlayerList().getPlayer(senderUUID);
        if (sender == null) return;

        String messageText = extractMessageText(chatPacket);
        if (messageText == null || messageText.isEmpty()) return;

        if (config.getPlayerSilenceEnabled() && SpeakVolumeTracker.INSTANCE.isPlayerMuted(sender)) {
            return;
        }

        ci.cancel();

        if (!config.getEnabled()) {
            // Distance limits are disabled, but 心声 remains a private channel.
            SpeakVolumeTracker.SpeakState disabledState = SpeakVolumeTracker.INSTANCE.getState(sender);
            for (ServerPlayer player : self.getServer().getPlayerList().getPlayers()) {
                if (isPrivateMode(disabledState.getMode())
                        && !player.equals(sender)
                        && !hasHeartListenerTag(player)) {
                    continue;
                }
                Component chat = Component.empty()
                    .append(sender.getDisplayName())
                    .append(Component.literal("\n"))
                    .append(Component.literal(" └─ ").withColor(0xAAAAAA))
                    .append(Component.literal(messageText).withColor(privateMessageColor(disabledState.getMode())));
                player.connection.send(new ClientboundSystemChatPacket(chat, false));
            }
            return;
        }

        SpeakVolumeTracker.SpeakState speakerState = SpeakVolumeTracker.INSTANCE.getState(sender);
        SpeakVolumeTracker.ProcessedSpeech processed = SpeakVolumeTracker.INSTANCE.processSpeech(
            sender, messageText, config.getShoutRangeMultiplier());
        messageText = processed.getText();
        speakerState = new SpeakVolumeTracker.SpeakState(speakerState.getMode(), processed.getMultiplier(),
            speakerState.getAttitude(), speakerState.getTransform());
        ChatLimitNetwork.INSTANCE.sendRestrictionToClient(sender,
            SpeakVolumeTracker.INSTANCE.maxAllowedSliderPosition(sender),
            SpeakVolumeTracker.INSTANCE.restrictionRemainingMillis(sender));
        if (processed.getShout()) SpeakVolumeTracker.INSTANCE.applyShoutDizziness(
            sender, processed.getShoutCharacterCount());

        for (ServerPlayer player : self.getServer().getPlayerList().getPlayers()) {
            if (player.equals(sender)) {
                // Sender sees custom chat format: PlayerName\n └─ Message
                Component senderChat = Component.empty()
                    .append(sender.getDisplayName())
                    .append(Component.literal("\n"))
                    .append(Component.literal(" └─ ").withColor(0xAAAAAA))
                    .append(Component.literal(messageText).withColor(privateMessageColor(speakerState.getMode())))
                    .append(hoverLabel(" 成句", processed.getOriginalText()));
                player.connection.send(new ClientboundSystemChatPacket(senderChat, false));
            } else {
                handleReceiver(sender, player, messageText, config, speakerState);
            }
        }
    }

    @Unique
    private String extractMessageText(ClientboundPlayerChatPacket packet) {
        // Try 1: body().plain() → Component (1.21.2+)
        try {
            Method bodyMethod = packet.getClass().getMethod("body");
            Object body = bodyMethod.invoke(packet);
            if (body != null) {
                Method plainMethod = body.getClass().getMethod("plain");
                Object result = plainMethod.invoke(body);
                if (result instanceof Component comp) {
                    return comp.getString();
                }
            }
        } catch (Exception ignored) {}

        // Try 2: body() is a Component directly (pre-1.21.2)
        try {
            Method bodyMethod = packet.getClass().getMethod("body");
            Object body = bodyMethod.invoke(packet);
            if (body instanceof Component comp) {
                return comp.getString();
            }
        } catch (Exception ignored) {}

        // Try 3: unsignedContent() → Component
        try {
            Method ucMethod = packet.getClass().getMethod("unsignedContent");
            Object result = ucMethod.invoke(packet);
            if (result instanceof Component comp) {
                return comp.getString();
            }
        } catch (Exception ignored) {}

        return null;
    }
}
