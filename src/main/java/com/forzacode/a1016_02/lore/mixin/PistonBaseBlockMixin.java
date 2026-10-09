package com.forzacode.a1016_02.lore.mixin;

import com.forzacode.a1016_02.lore.UnbreakableSigns;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A piston can neither push nor break F30's signs: it is blocked, like by obsidian. */
@Mixin(PistonBaseBlock.class)
abstract class PistonBaseBlockMixin {
	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private static void a1016$blockProtectedSign(BlockState state, Level level, BlockPos pos, Direction direction, boolean allowDestroyable,
			Direction connectionDirection, CallbackInfoReturnable<Boolean> cir) {
		if (UnbreakableSigns.isProtected(level, pos)) {
			cir.setReturnValue(false);
		}
	}
}
