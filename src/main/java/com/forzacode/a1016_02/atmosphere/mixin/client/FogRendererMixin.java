package com.forzacode.a1016_02.atmosphere.mixin.client;

import com.forzacode.a1016_02.atmosphere.client.ClientAtmosphere;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;

import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dusk fog, fog weather and fog surges. At the start of {@code setupFog} the frame's spell fog at the camera is worked
 * out (the sky weight stepped); in {@code computeFogColor} the base color the color source gives is tinted for the
 * spell before vanilla darkens it (the void, Blindness and Darkness, a boss, night vision); after {@code setupFog} the
 * computed world fog is pulled in. In 26.3 {@code FogData.color} is both the fog color of the world's shaders (terrain,
 * sky, clouds fade into it) and the main target's clear color.
 */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("HEAD"))
	private void a1016_02$beginFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level,
			CallbackInfoReturnable<FogData> cir) {
		ClientAtmosphere.beginFog(camera, level, renderDistanceInChunks, deltaTracker.getGameTimeDeltaPartialTick(false));
	}

	@ModifyExpressionValue(method = "computeFogColor", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/fog/environment/FogEnvironment;getBaseColor(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/Camera;IF)Lorg/joml/Vector3fc;"))
	private Vector3fc a1016_02$tintBaseColor(Vector3fc original, Camera camera, float partialTicks, ClientLevel level, int renderDistance,
			float darkenWorldAmount, Vector4f dest) {
		return ClientAtmosphere.tintFogColor(original, camera);
	}

	@Inject(method = "setupFog", at = @At("RETURN"))
	private void a1016_02$atmosphereFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount,
			ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		ClientAtmosphere.applyFog(cir.getReturnValue(), camera, level, deltaTracker.getGameTimeDeltaPartialTick(false));
	}
}
