package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
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

	/**
	 * The players of {@code candidates} in {@code level} within hearing range of {@code pos} (vanilla's range rule,
	 * {@code max(volume, 1) * 16} blocks), in the candidates' order. The rule {@link #playAt} uses; public so a test
	 * or a recording sound sink can hear with exactly the same rule over its own players.
	 */
	public static List<ServerPlayer> hearers(ServerLevel level, Collection<ServerPlayer> candidates, Vec3 pos, float volume) {
		double range = Math.max(volume, 1.0F) * 16.0;
		List<ServerPlayer> hearers = new ArrayList<>();
		for (ServerPlayer player : candidates) {
			if (player.level() == level && player.distanceToSqr(pos) < range * range) {
				hearers.add(player);
			}
		}
		return hearers;
	}

	/**
	 * The sounds of a step on {@code floor} (the block that holds the walker up), by vanilla 26.3's cover rule
	 * ({@code Entity.getPrimaryStepSoundBlockPos}): the block over the floor when it is in
	 * {@code #inside_step_sound_blocks} (leaf litter, pink petals, glow lichen, powder snow ...) or
	 * {@code #combination_step_sound_blocks} (a snow layer, carpets, moss carpet ...), so a step on snow cover sounds like
	 * snow, not the grass under it; else the floor's own. Vanilla's muffled second step of a combination block is left
	 * out. The same rule as dig's {@code SoundCards.stepSound} with the floor's own sounds. Play its
	 * {@code getStepSound()} at {@code getVolume() * 0.15F}, {@code getPitch()} as vanilla does, or louder to reach
	 * farther.
	 */
	public static SoundType stepType(ServerLevel level, BlockPos floor) {
		BlockState cover = level.getBlockState(floor.above());
		if (cover.is(BlockTags.INSIDE_STEP_SOUND_BLOCKS) || cover.is(BlockTags.COMBINATION_STEP_SOUND_BLOCKS)) {
			return cover.getSoundType();
		}
		return level.getBlockState(floor).getSoundType();
	}

	/**
	 * Alone for a sound only one player should hear (D-074): nobody shares the moment with them
	 * ({@link PlayerWatch#sharingWith} lists only them) and no other player in their level, spectators and the dead
	 * included, is within {@code radius} blocks of them (3D). A sound he makes reaches everyone in range
	 * ({@link #playAt}), so a spectating friend beside the player would hear it too. Dig's {@code Ladder.alone} rule,
	 * with the level's players as the listeners.
	 */
	public static boolean alone(ServerPlayer player, double radius) {
		return alone(player, radius, Services.watch(), player.level().players());
	}

	/** {@link #alone(ServerPlayer, double)} with this watch and these listeners (a test's own players). */
	public static boolean alone(ServerPlayer player, double radius, PlayerWatch watch, Collection<? extends ServerPlayer> listeners) {
		if (watch.sharingWith(player).size() > 1) {
			return false;
		}
		ServerLevel level = player.level();
		double r = Math.max(0.0, radius);
		for (ServerPlayer other : listeners) {
			if (!other.getUUID().equals(player.getUUID()) && other.level() == level && other.distanceToSqr(player) <= r * r) {
				return false;
			}
		}
		return true;
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
