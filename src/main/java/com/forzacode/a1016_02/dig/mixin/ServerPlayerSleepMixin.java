package com.forzacode.a1016_02.dig.mixin;

import com.forzacode.a1016_02.dig.sc.RestRefusal;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * docs/SELF_CREATED.md, 12: at vanilla 26.3's monster check in {@code ServerPlayer.startSleepInBed} (after the sleep
 * rule, the distance and the obstruction checks, and only out of creative), the list of monsters that keep the player
 * from resting reads as not empty when {@link RestRefusal#refuse} says so, so vanilla returns {@code NOT_SAFE} itself,
 * with its own message. Only when vanilla found none: a real monster is vanilla's refusal.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerSleepMixin {
	@ModifyExpressionValue(method = "startSleepInBed", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"))
	private boolean a1016_02$notSafe(boolean noMonster, @Local(argsOnly = true) BlockPos bed) {
		return noMonster && !RestRefusal.refuse((ServerPlayer) (Object) this, bed);
	}
}
