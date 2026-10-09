package com.forzacode.a1016_02.director.mixin;

import com.forzacode.a1016_02.director.DirectorTriggers;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/** Disc 13 underground raises attention; stopping it mid-track lowers it (DESIGN.md "Triggers"). */
@Mixin(JukeboxSongPlayer.class)
abstract class JukeboxSongPlayerMixin {
	@Shadow
	private long ticksSinceSongStarted;
	@Shadow
	private Holder<JukeboxSong> song;
	@Shadow
	@Final
	private BlockPos blockPos;

	@Inject(method = "play", at = @At("TAIL"))
	private void a1016$onPlay(LevelAccessor level, Holder<JukeboxSong> newSong, CallbackInfo ci) {
		if (level instanceof ServerLevel serverLevel) {
			DirectorTriggers.onJukeboxPlay(serverLevel, blockPos, newSong);
		}
	}

	@Inject(method = "stop", at = @At("HEAD"))
	private void a1016$onStop(LevelAccessor level, BlockState state, CallbackInfo ci) {
		if (level instanceof ServerLevel serverLevel && song != null) {
			DirectorTriggers.onJukeboxStop(serverLevel, blockPos, song, ticksSinceSongStarted);
		}
	}
}
