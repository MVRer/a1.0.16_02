package com.forzacode.a1016_02.accident.mixin;

import com.forzacode.a1016_02.accident.AccidentInit;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Tells the planner when an entity is newly added to a level (natural spawns, phantoms, spawners, summons), as
 * opposed to loaded with its chunk. It only watches; the mod never spawns anything here.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelSpawnMixin {
	@Inject(method = "addFreshEntity", at = @At("RETURN"))
	private void a1016$onFreshEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			AccidentInit.planner().onSpawned((ServerLevel) (Object) this, entity);
		}
	}
}
