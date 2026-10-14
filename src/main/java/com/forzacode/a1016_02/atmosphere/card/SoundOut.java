package com.forzacode.a1016_02.atmosphere.card;

import java.util.List;

import com.forzacode.a1016_02.core.SoundCues;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Where a card's sound goes. Every sound he makes at a position is a shared moment (D-060): it plays to everyone in
 * hearing range of it in that dimension, all with the same seed ({@link #EVERYONE_IN_RANGE}, core's
 * {@code SoundCues.playAt}). A game test gives a card its own instance to see where and to whom a sound would go.
 */
@FunctionalInterface
public interface SoundOut {
	/** Core's {@code SoundCues.playAt}: every player in the level within vanilla hearing range, spectators included. */
	SoundOut EVERYONE_IN_RANGE = SoundCues::playAt;

	/** Plays {@code sound} at {@code pos}; returns who heard it. */
	List<ServerPlayer> play(ServerLevel level, Holder<SoundEvent> sound, SoundSource source, Vec3 pos, float volume, float pitch);
}
