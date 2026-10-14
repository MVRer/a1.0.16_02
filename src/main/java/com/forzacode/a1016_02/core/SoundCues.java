package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla positional sounds sent as packets only. Nothing in the world (mobs, sculk) reacts to them. A sound he
 * makes uses {@link #playAt}: everyone who would naturally hear it does (D-060). {@link #playTo} is for the rare
 * sound only one player may hear.
 */
public final class SoundCues {
	private SoundCues() {
	}

	/**
	 * Plays at {@code pos} for every player in that level within vanilla hearing range of it
	 * ({@code max(volume, 1) * 16} blocks), spectators included, all with the same seed so everyone hears the same
	 * variant.
	 *
	 * @return who it was sent to
	 */
	public static List<ServerPlayer> playAt(ServerLevel level, Holder<SoundEvent> sound, SoundSource source, Vec3 pos, float volume, float pitch) {
		List<ServerPlayer> hearers = hearers(level, level.players(), pos, volume);
		if (!hearers.isEmpty()) {
			ClientboundSoundPacket packet = new ClientboundSoundPacket(sound, source, pos.x, pos.y, pos.z, volume, pitch, level.getRandom().nextLong());
			hearers.forEach(player -> player.connection.send(packet));
		}
		return hearers;
	}

	public static List<ServerPlayer> playAt(ServerLevel level, SoundEvent sound, SoundSource source, Vec3 pos, float volume, float pitch) {
		return playAt(level, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), source, pos, volume, pitch);
	}

	/** The players of {@code candidates} in {@code level} within hearing range of {@code pos} (vanilla's range rule). */
	static List<ServerPlayer> hearers(ServerLevel level, Collection<ServerPlayer> candidates, Vec3 pos, float volume) {
		double range = Math.max(volume, 1.0F) * 16.0;
		List<ServerPlayer> hearers = new ArrayList<>();
		for (ServerPlayer player : candidates) {
			if (player.level() == level && player.distanceToSqr(pos) < range * range) {
				hearers.add(player);
			}
		}
		return hearers;
	}

	/** Plays at {@code pos} for this player only, as an ambient sound. */
	public static void playTo(ServerPlayer player, Holder<SoundEvent> sound, Vec3 pos, float volume, float pitch) {
		playTo(player, sound, SoundSource.AMBIENT, pos, volume, pitch);
	}

	public static void playTo(ServerPlayer player, SoundEvent sound, Vec3 pos, float volume, float pitch) {
		playTo(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.AMBIENT, pos, volume, pitch);
	}

	public static void playTo(ServerPlayer player, Holder<SoundEvent> sound, SoundSource source, Vec3 pos, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(sound, source, pos.x, pos.y, pos.z, volume, pitch, player.getRandom().nextLong()));
	}
}
