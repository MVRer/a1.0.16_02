package com.forzacode.a1016_02.core.mixin;

import com.forzacode.a1016_02.core.Services;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Feeds {@code PlayerWatch}'s footprint and first blocks whenever a player places a block. */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
	@Inject(method = "placeBlock", at = @At("RETURN"))
	private void a1016$onPlaced(BlockPlaceContext context, BlockState placementState, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && context.getPlayer() instanceof ServerPlayer player && context.getLevel() instanceof ServerLevel level) {
			Services.watch().onPlaced(player, level, context.getClickedPos(), level.getBlockState(context.getClickedPos()));
		}
	}
}
