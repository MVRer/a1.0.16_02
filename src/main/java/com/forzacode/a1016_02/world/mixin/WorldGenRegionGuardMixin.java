package com.forzacode.a1016_02.world.mixin;

import com.forzacode.a1016_02.world.sc2.kit.Guard;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * docs/SELF_CREATED_2.md, SC2-H: a decoration's write into a cell of another chunk that a place of the second wave
 * guards ({@link Guard}) is refused, as vanilla refuses a write out of the decoration's reach. Every worldgen block write
 * asks {@code ensureCanWrite} first (and ore blobs, which write sections directly, ask it too).
 */
@Mixin(WorldGenRegion.class)
abstract class WorldGenRegionGuardMixin {
	@Inject(method = "ensureCanWrite", at = @At("HEAD"), cancellable = true)
	private void a1016_02$guard(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (Guard.refuses((WorldGenRegion) (Object) this, pos)) {
			cir.setReturnValue(false);
		}
	}
}
