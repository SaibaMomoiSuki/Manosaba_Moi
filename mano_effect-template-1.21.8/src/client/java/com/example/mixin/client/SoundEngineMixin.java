// Copyright (c) Mano Effect. All rights reserved. Proprietary - Internal Use Only.
package com.example.mixin.client;

import com.example.Mano_effectClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    @Unique
    private float manoEffect$lastAppliedMuffle = -1f;

    @Inject(method = "tick", at = @At("TAIL"))
    private void refreshWetSoundVolumes(boolean paused, CallbackInfo ci) {
        float muffle = Mano_effectClient.soundMuffleStrength;
        if (Math.abs(muffle - manoEffect$lastAppliedMuffle) < 0.012f) return;

        SoundEngine self = (SoundEngine) (Object) this;
        for (SoundSource source : SoundSource.values()) {
            if (source != SoundSource.MASTER) {
                self.updateCategoryVolume(source, 1f);
            }
        }
        manoEffect$lastAppliedMuffle = muffle;
    }

    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F",
        at = @At("RETURN"), cancellable = true)
    private void muffleWetWorldSounds(float volume, SoundSource source,
            CallbackInfoReturnable<Float> cir) {
        if (source == SoundSource.MASTER) return;
        float muffle = Mano_effectClient.soundMuffleStrength;
        if (muffle > 0.001f) {
            cir.setReturnValue(cir.getReturnValueF() * (1.0f - muffle * 0.68f));
        }
    }

    @Inject(method = "calculatePitch", at = @At("RETURN"), cancellable = true)
    private void softenWetWorldSounds(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound.isRelative()) return;
        float muffle = Mano_effectClient.soundMuffleStrength;
        if (muffle > 0.001f) {
            cir.setReturnValue(cir.getReturnValueF() * (1.0f - muffle * 0.18f));
        }
    }
}
