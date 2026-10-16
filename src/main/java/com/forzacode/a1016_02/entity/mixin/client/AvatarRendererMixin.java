package com.forzacode.a1016_02.entity.mixin.client;

import com.forzacode.a1016_02.entity.client.NamelessClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;

/**
 * The self-created layer's 02: vanilla's own rule for a player's name tag ({@code AvatarRenderer.shouldShowName}, with the
 * squared distance to the camera) answers false while {@link NamelessClient#hides} says so; otherwise vanilla decides.
 * Vanilla asks only for an avatar it extracts for this frame (one in view, within 64 blocks), so a name left out here is a
 * name that would have been on screen: only then does the hide time count ({@link NamelessClient#hiddenOnScreen}).
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("RETURN"), cancellable = true)
	private void a1016_02$nameless(Avatar avatar, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && NamelessClient.hides(avatar, distanceToCameraSq)) {
			cir.setReturnValue(false);
			NamelessClient.hiddenOnScreen();
		}
	}
}
