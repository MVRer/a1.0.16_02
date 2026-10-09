package com.forzacode.a1016_02.lore.mixin;

import com.forzacode.a1016_02.lore.UnbreakableSigns;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * F30's signs can't be broken: on the server, nothing may replace a protected sign with another block (mining,
 * pistons, fluids, commands, neighbours that would make it drop), and {@code destroyBlock} refuses it before any
 * drop is made. Changing the sign's own state (waterlogging) is still allowed.
 */
@Mixin(Level.class)
abstract class LevelMixin {
	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
	private void a1016$keepProtectedSign(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
		Level level = (Level) (Object) this;
		if (level instanceof ServerLevel && UnbreakableSigns.isProtected(level, pos) && !state.is(level.getBlockState(pos).getBlock())) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
	private void a1016$keepProtectedSignWhole(BlockPos pos, boolean dropResources, Entity breaker, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
		Level level = (Level) (Object) this;
		if (level instanceof ServerLevel && UnbreakableSigns.isProtected(level, pos)) {
			cir.setReturnValue(false);
		}
	}
}
