package com.forzacode.a1016_02.core.mixin;

import com.forzacode.a1016_02.core.TraceLedger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Feeds the ledger's CONVERT marks ({@code TraceLedger.changedSince}): every block that changed in a server level, so a
 * conversion the player (or redstone, or a piston) changed since stays theirs. Every {@code setBlock},
 * {@code removeBlock} and {@code destroyBlock} ends here.
 */
@Mixin(Level.class)
abstract class LevelSetBlockMixin {
	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
	private void a1016$onBlockChanged(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && (Object) this instanceof ServerLevel level) {
			TraceLedger.blockChanged(level, pos);
		}
	}
}
