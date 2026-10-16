package com.forzacode.a1016_02.atmosphere.mixin;

import com.forzacode.a1016_02.atmosphere.sc.InStep;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The layer's 06 (docs/SELF_CREATED.md): each step a player's own movement makes on the server. In vanilla 26.3 the
 * server moves a player by their move packets ({@code ServerGamePacketListenerImpl.handleMovePlayer} calls
 * {@code player.move(MoverType.PLAYER, ...)}), and {@code Entity.move} plays the step on the server side through
 * {@code applyMovementEmissionAndPlaySound}, {@code vibrationAndSoundEffectsFromBlock} and {@code walkingStepSound},
 * which calls {@code playStepSound} (a player's own override picks the cover block, water and combination sounds).
 * A sneaking player on the ground has no movement emission ({@code Player.getMovementEmission}), so no step reaches
 * here, as none is heard. Only a map lookup, and only while some player has an episode ({@link InStep#onStep}).
 */
@Mixin(Entity.class)
abstract class EntityStepMixin {
	@Inject(method = "walkingStepSound", at = @At("HEAD"))
	private void a1016_02$step(BlockPos onPos, BlockState onState, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			InStep.onStep(player, onPos, onState);
		}
	}
}
