package com.example.item.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class RevolverSounds {
    private RevolverSounds() {}

    public static void play(Level level, Player source, String name, double radius, float baseVolume, boolean occlusion) {
        if (level.isClientSide()) return;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("manosaba_item", name);
        var sound = BuiltInRegistries.SOUND_EVENT.get(id).orElse(null);
        if (sound == null) return;
        Vec3 origin = source.position().add(0, source.getEyeHeight() * 0.65, 0);
        for (Player listener : level.players()) {
            double distance = listener.position().distanceTo(origin);
            if (distance > radius) continue;
            float volume = (float) (baseVolume * Math.max(0.0, 1.0 - distance / radius));
            if (occlusion) {
                int blocks = countOccludingBlocks(level, origin, listener.getEyePosition());
                if (blocks >= 3) continue;
                if (blocks == 1) volume *= 0.5F;
                if (blocks == 2) volume *= 0.25F;
            }
            if (volume <= 0.001F) continue;
            if (listener instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundSoundPacket(sound, SoundSource.PLAYERS,
                    origin.x, origin.y, origin.z, volume, 1.0F, level.random.nextLong()));
            }
        }
    }

    private static int countOccludingBlocks(Level level, Vec3 from, Vec3 to) {
        int blocks = 0;
        Vec3 delta = to.subtract(from);
        BlockPos previous = null;
        int steps = Math.max(1, (int) Math.ceil(delta.length() * 2.0));
        for (int i = 1; i < steps; i++) {
            BlockPos pos = BlockPos.containing(from.add(delta.scale((double) i / steps)));
            if (pos.equals(previous)) continue;
            previous = pos;
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) blocks++;
        }
        return blocks;
    }
}
