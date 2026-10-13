/*
 * Copyright (c) 2026 Momoi. Proprietary - Internal Use Only.
 * 内部私有，严禁任何形式使用（包括游玩、复制、分发）。
 */
package com.example.vision_optimize.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin for LivingEntity.
 * Attack-during-discovery detection for player vision is handled
 * via {@link com.example.vision_optimize.event.ServerEventHandlers}
 * using Fabric API's {@code ServerLivingEntityEvents.AFTER_DAMAGE} event.
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {
}
