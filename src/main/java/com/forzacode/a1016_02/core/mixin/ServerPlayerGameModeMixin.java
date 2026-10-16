package com.forzacode.a1016_02.core.mixin;

import com.forzacode.a1016_02.core.Services;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Feeds {@code PlayerWatch}'s recent actions (D-076) with the doors, trapdoors and fence gates a player opens or closes
 * and the containers they open: the used block's state and the player's menu just before the use, compared after it.
 * One game mode per player, server thread only.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
	@Unique
	private BlockState a1016$stateBefore;
	@Unique
	private AbstractContainerMenu a1016$menuBefore;

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void a1016$beforeUse(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hit,
			CallbackInfoReturnable<InteractionResult> cir) {
		a1016$stateBefore = level.getBlockState(hit.getBlockPos());
		a1016$menuBefore = player.containerMenu;
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void a1016$afterUse(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hit,
			CallbackInfoReturnable<InteractionResult> cir) {
		BlockState before = a1016$stateBefore;
		AbstractContainerMenu menuBefore = a1016$menuBefore;
		a1016$stateBefore = null;
		a1016$menuBefore = null;
		if (level instanceof ServerLevel serverLevel) {
			Services.watch().onUsedBlock(player, serverLevel, hit.getBlockPos(), before, menuBefore);
		}
	}
}
