package com.forzacode.a1016_02.entity.mixin.client;

import com.forzacode.a1016_02.entity.client.sc2.s27.S27Client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;

/**
 * The self-created layer's 27, for the dev gallery only: the gallery shoots with the HUD hidden, which hides every name tag
 * (vanilla's F1), so the gallery's stand-in copy keeps its name as it would with the HUD on ({@link S27Client#standInName}).
 * A stand-in exists only in a development environment; nothing changes for any other avatar or in a release.
 */
@Mixin(AvatarRenderer.class)
public abstract class Sc27StandInNameMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("RETURN"), cancellable = true)
	private void a1016_02$standInName(Avatar avatar, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && S27Client.standInName(avatar)) {
			cir.setReturnValue(true);
		}
	}
}
