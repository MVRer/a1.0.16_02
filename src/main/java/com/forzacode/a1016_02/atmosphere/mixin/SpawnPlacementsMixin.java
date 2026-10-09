package com.forzacode.a1016_02.atmosphere.mixin;

import com.forzacode.a1016_02.atmosphere.DeadMountains;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dead mountains: no animals ever spawn there. Natural and world-generation spawns of passive mobs inside a recorded
 * dead mountain fail their spawn rules ({@link DeadMountains#refusesSpawn}). Only prevents spawns; never removes a mob.
 */
@Mixin(SpawnPlacements.class)
abstract class SpawnPlacementsMixin {
	@Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
	private static void a1016_02$deadMountain(EntityType<?> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random,
			CallbackInfoReturnable<Boolean> cir) {
		if ((reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION)
				&& DeadMountains.refusesSpawn(type, reason, level.getLevel().dimension(), pos)) {
			cir.setReturnValue(false);
		}
	}
}
