package com.forzacode.a1016_02.ending.mixin;

import com.forzacode.a1016_02.ending.EndingWatch;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;

/** Observes pickups: every fragment the subject ever picks up must later go into lava or fire for Ending C. */
@Mixin(ServerPlayer.class)
abstract class FragmentPickupMixin {
	@Inject(method = "onItemPickup", at = @At("HEAD"))
	private void a1016$endingFragmentPickedUp(ItemEntity item, CallbackInfo ci) {
		EndingWatch.onPickedUp((ServerPlayer) (Object) this, item.getItem());
	}
}
