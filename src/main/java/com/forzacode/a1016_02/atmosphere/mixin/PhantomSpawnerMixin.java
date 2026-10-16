package com.forzacode.a1016_02.atmosphere.mixin;

import com.forzacode.a1016_02.atmosphere.sc.EmptyNight;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.level.levelgen.PhantomSpawner;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The layer's 04 (docs/SELF_CREATED.md): no phantoms, and no village siege, in the overworld while
 * {@link EmptyNight#holdsSpawner} holds. Both are vanilla 26.3 {@code CustomSpawner}s with
 * {@code tick(ServerLevel, boolean)} (void), which spawn without the natural spawn rules; the tick is skipped, so
 * nothing they would have rolled happens and nothing else changes.
 */
@Mixin({PhantomSpawner.class, VillageSiege.class})
abstract class PhantomSpawnerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void a1016_02$emptyNight(ServerLevel level, boolean spawnEnemies, CallbackInfo ci) {
		if (EmptyNight.holdsSpawner(level)) {
			ci.cancel();
		}
	}
}
