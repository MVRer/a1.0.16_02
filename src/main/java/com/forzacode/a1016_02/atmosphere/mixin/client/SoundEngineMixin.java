package com.forzacode.a1016_02.atmosphere.mixin.client;

import com.forzacode.a1016_02.atmosphere.client.ClientAtmosphere;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Silence: scales ambient, weather and music volume while a silence runs and fades back. */
@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {
	@Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"), cancellable = true)
	private void a1016_02$silence(float volume, SoundSource source, CallbackInfoReturnable<Float> cir) {
		float factor = ClientAtmosphere.volumeFactor(source);
		if (factor < 1.0F) {
			cir.setReturnValue(cir.getReturnValueF() * factor);
		}
	}
}
