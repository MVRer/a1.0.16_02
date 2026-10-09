package com.forzacode.a1016_02.atmosphere.mixin.client;

import com.forzacode.a1016_02.atmosphere.client.ClientAtmosphere;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Music off: while it is on, the music manager neither ticks nor starts a track, and stops whatever is playing. */
@Mixin(MusicManager.class)
abstract class MusicManagerMixin {
	@Shadow
	private @Nullable SoundInstance currentMusic;

	@Shadow
	public abstract void stopPlaying();

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void a1016_02$musicOff(CallbackInfo ci) {
		if (ClientAtmosphere.musicOff()) {
			if (currentMusic != null) {
				stopPlaying();
			}
			ci.cancel();
		}
	}

	@Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
	private void a1016_02$noStart(Music music, CallbackInfo ci) {
		if (ClientAtmosphere.musicOff()) {
			ci.cancel();
		}
	}
}
