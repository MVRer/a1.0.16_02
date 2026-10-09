package com.forzacode.a1016_02.core;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Vanilla positional sounds that only one player hears. Nobody else, and nothing in the world, reacts to them. */
public final class SoundCues {
	private SoundCues() {
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
