package com.forzacode.a1016_02.lore.mixin;

import com.forzacode.a1016_02.lore.PlacementLog;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Logs each player's oldest placed blocks for F13's fallback (D-066 M11; {@code PlacementLog}). */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
	@Inject(method = "placeBlock", at = @At("RETURN"))
	private void a1016$logPlaced(BlockPlaceContext context, BlockState placementState, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && context.getPlayer() instanceof ServerPlayer player && context.getLevel() instanceof ServerLevel level) {
			PlacementLog.onPlaced(player, level, context.getClickedPos(), level.getBlockState(context.getClickedPos()));
		}
	}
}
