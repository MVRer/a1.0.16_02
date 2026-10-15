package com.forzacode.a1016_02.atmosphere.mixin.client;

import com.forzacode.a1016_02.atmosphere.client.ClientAtmosphere;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dusk fog, fog weather and fog surges: pulls the computed world fog in, and tints its color for a fog spell, after
 * vanilla sets it up. {@code FogData.color} is already final here (vanilla computes it first); in 26.3 it is both the
 * fog color of the world's shaders (terrain, sky, clouds fade into it) and the main target's clear color.
 */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void a1016_02$atmosphereFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount,
			ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		ClientAtmosphere.applyFog(cir.getReturnValue(), camera, level, deltaTracker.getGameTimeDeltaPartialTick(false));
	}
}
