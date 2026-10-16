package com.forzacode.a1016_02.atmosphere.mixin.client;

import com.forzacode.a1016_02.atmosphere.client.ClientAtmosphere;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The layer's 04 (docs/SELF_CREATED.md): the moon phase the sky is drawn with. Vanilla 26.3 fills
 * {@code SkyRenderState.moonPhase} from the {@code MOON_PHASE} environment attribute in
 * {@code SkyRenderer.extractRenderState} (the overworld's skybox only); after it, {@link ClientAtmosphere#moonPhase}
 * gives the phase to draw (vanilla's unless the server's window holds the clock).
 */
@Mixin(SkyRenderer.class)
abstract class SkyMoonMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void a1016_02$moon(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state, CallbackInfo ci) {
		if (state.moonPhase != null) {
			state.moonPhase = ClientAtmosphere.moonPhase(level, state.moonPhase);
		}
	}
}
