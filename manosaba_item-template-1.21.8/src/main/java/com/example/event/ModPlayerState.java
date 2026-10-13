// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.event;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class ModPlayerState {
    public static final ModPlayerState INSTANCE = new ModPlayerState();
    public static final ConcurrentHashMap<UUID, PoisonState> poison = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<UUID, TldjmState> tldjm = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<UUID, GolduckState> golduck = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<UUID, SleepingPillsState> sleepingPills = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<UUID, ZhangXuefengState> zhangXuefeng = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<UUID, BloodySwordWashState> bloodySwordWash = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<UUID, BottleStruggleState> bottleStruggle = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<UUID, BottleStabDamageState> bottleStabDamage = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<Long, BottleBreakParticle> bottleBreakParticles = new ConcurrentHashMap<>();
    private static final AtomicLong PARTICLE_IDS = new AtomicLong();
    private ModPlayerState() {}
    public ConcurrentHashMap<UUID, PoisonState> getPoison() { return poison; }

    public static final class PoisonState {
        public long eatTime; public boolean stage1; public boolean stage2; public boolean stage3;
        public PoisonState(long eatTime) { this(eatTime, false, false, false); }
        public PoisonState(long eatTime, boolean stage1, boolean stage2, boolean stage3) {
            this.eatTime = eatTime; this.stage1 = stage1; this.stage2 = stage2; this.stage3 = stage3;
        }
    }
    public static final class TldjmState { public long deathTime; public TldjmState(long deathTime) { this.deathTime = deathTime; } }
    public static final class GolduckState { public long eatTime; public GolduckState(long eatTime) { this.eatTime = eatTime; } }
    public static final class SleepingPillsState {
        public long eatTime; public boolean stage1; public boolean stage2; public boolean locked;
        public int visualPhase;
        public SleepingPillsState(long eatTime) { this(eatTime, false, false, false); }
        public SleepingPillsState(long eatTime, boolean stage1, boolean stage2, boolean locked) {
            this.eatTime = eatTime; this.stage1 = stage1; this.stage2 = stage2; this.locked = locked;
        }
    }
    public static final class ZhangXuefengState {
        public boolean active; public long eatTime; public double distance; public double lastX; public double lastZ;
        public ZhangXuefengState(boolean active, long eatTime, double distance, double lastX, double lastZ) {
            this.active = active; this.eatTime = eatTime; this.distance = distance; this.lastX = lastX; this.lastZ = lastZ;
        }
    }
    public static final class BloodySwordWashState {
        public long enterWaterTime; public BloodySwordWashState(long enterWaterTime) { this.enterWaterTime = enterWaterTime; }
    }
    public static final class BottleStruggleState {
        public final UUID attackerId; public final UUID victimId; public final long startTime;
        public int attackerPresses; public int victimPresses; public boolean finished; public boolean attackerWon;
        public double attackerStartX; public double attackerStartY = Double.NaN; public double attackerStartZ;
        public double victimStartX; public double victimStartY; public double victimStartZ;
        public boolean reachedThreshold; public long lastDamageTime; public boolean bleedingApplied;
        public long lastAttackerPressTick = Long.MIN_VALUE; public long lastVictimPressTick = Long.MIN_VALUE;
        public BottleStruggleState(UUID attackerId, UUID victimId, long startTime) {
            this.attackerId = attackerId; this.victimId = victimId; this.startTime = startTime;
        }
    }
    public static final class BottleStabDamageState {
        public final long startTime; public long lastDamageTime;
        public BottleStabDamageState(long startTime, long lastDamageTime) { this.startTime = startTime; this.lastDamageTime = lastDamageTime; }
    }
    public static final class BottleBreakParticle {
        public final ResourceKey<Level> dimension; public final double x; public final double y; public final double z;
        public final long expiryTime; public final int particleType;
        public BottleBreakParticle(ResourceKey<Level> dimension, double x, double y, double z, long expiryTime, int particleType) {
            this.dimension = dimension; this.x = x; this.y = y; this.z = z; this.expiryTime = expiryTime; this.particleType = particleType;
        }
    }
    public void addBreakParticle(ResourceKey<Level> dimension, double x, double y, double z, long expiryTime, int particleType) {
        bottleBreakParticles.put(PARTICLE_IDS.getAndIncrement(), new BottleBreakParticle(dimension, x, y, z, expiryTime, particleType));
    }
}
