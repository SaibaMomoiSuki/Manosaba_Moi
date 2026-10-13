// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.event;

import com.example.network.BottleKeyPressPayload;
import com.example.network.BottleStruggleEndPayload;
import com.example.network.BottleStruggleStartPayload;
import com.example.network.BottleStruggleUpdatePayload;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class BottleStruggleClientHandler {
    public static final BottleStruggleClientHandler INSTANCE = new BottleStruggleClientHandler();
    private UUID attackerId;
    private UUID victimId;
    private float progress = 0.55F;
    private int attackerPresses;
    private int victimPresses;
    private boolean reachedThreshold;
    private boolean struggleEnded;
    private boolean attackerWon;
    private int endTicks;
    private boolean lastUp, lastDown, lastLeft, lastRight;

    private BottleStruggleClientHandler() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BottleStruggleStartPayload.ID, (payload, context) -> context.client().execute(() -> {
            INSTANCE.attackerId = payload.getAttackerId(); INSTANCE.victimId = payload.getVictimId();
            INSTANCE.progress = 0.55F; INSTANCE.attackerPresses = 0; INSTANCE.victimPresses = 0;
            INSTANCE.reachedThreshold = false; INSTANCE.struggleEnded = false; INSTANCE.attackerWon = false; INSTANCE.endTicks = 0;
        }));
        ClientPlayNetworking.registerGlobalReceiver(BottleStruggleUpdatePayload.ID, (payload, context) -> context.client().execute(() -> {
            if (!INSTANCE.struggleEnded) {
                INSTANCE.progress = payload.getProgress(); INSTANCE.attackerPresses = payload.getAttackerPresses();
                INSTANCE.victimPresses = payload.getVictimPresses(); INSTANCE.reachedThreshold = payload.getReachedThreshold();
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(BottleStruggleEndPayload.ID, (payload, context) -> context.client().execute(() -> {
            INSTANCE.reset();
        }));
        ClientTickEvents.END_CLIENT_TICK.register(INSTANCE::tick);
    }

    private void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (struggleEnded) {
            if (++endTicks >= 60 || player == null || !player.isAlive()) reset();
            else freeze(player);
            return;
        }
        if (!isInStruggle()) { lastUp = lastDown = lastLeft = lastRight = false; return; }
        if (player == null || !player.isAlive()) { reset(); return; }
        freeze(player);
        boolean u = client.options.keyUp.isDown(), d = client.options.keyDown.isDown();
        boolean l = client.options.keyLeft.isDown(), r = client.options.keyRight.isDown();
        if ((u && !lastUp) || (d && !lastDown) || (l && !lastLeft) || (r && !lastRight))
            ClientPlayNetworking.send(new BottleKeyPressPayload(isAttacker()));
        lastUp = u; lastDown = d; lastLeft = l; lastRight = r;
    }

    private static void freeze(LocalPlayer player) {
        player.xxa = player.zza = 0.0F; player.setSprinting(false); player.setJumping(false);
    }

    public void reset() {
        attackerId = victimId = null; progress = 0.55F; attackerPresses = victimPresses = 0;
        reachedThreshold = struggleEnded = attackerWon = false; endTicks = 0;
    }

    public boolean isInStruggle() { return (attackerId != null || victimId != null) && !struggleEnded; }
    public boolean isAttacker() { return Minecraft.getInstance().player != null && Minecraft.getInstance().player.getUUID().equals(attackerId); }
    public UUID getAttackerId() { return attackerId; }
    public UUID getVictimId() { return victimId; }
    public float getProgress() { return progress; }
    public int getAttackerPresses() { return attackerPresses; }
    public int getVictimPresses() { return victimPresses; }
    public boolean getReachedThreshold() { return reachedThreshold; }
    public boolean getStruggleEnded() { return struggleEnded; }
    public boolean getAttackerWon() { return attackerWon; }
}
