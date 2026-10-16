package com.forzacode.a1016_02.atmosphere.card;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Gates;
import com.forzacode.a1016_02.atmosphere.Tasks;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The echoes (D-076): minutes after the player did something, its own sounds again, where they can't see, with their
 * own rhythm. Only sounds; nothing in the world changes. What the player did comes from their watch's recent actions
 * ({@code PlayerWatch.recentActions}, ticks on the server's tick count, one session of theirs), read through
 * {@link #watch} so a game test can feed its own. Never within {@code echoNoCombatSeconds} (30) of their combat. The
 * sounds go through {@link #sounds} ({@code SoundCues.playAt}): anyone in range hears them (D-060).
 */
abstract class EchoCard extends AtmosphereCard {
	/** Ticks since the player's own combat ({@code PlayerWatch.ticksSinceCombat}). A game test swaps in its own, as {@link #sounds}. */
	ToLongFunction<ServerPlayer> sinceCombat = player -> watch.get().ticksSinceCombat(player);

	EchoCard(String id, Tier tier, Stage earliest, Set<Habit> habits, Set<CardTag> tags, boolean hasFake) {
		super(id, tier, earliest, habits, tags, hasFake);
	}

	/** Something of theirs to echo and no combat of theirs in the window ({@link Gates#echo}). */
	protected boolean fits(ServerPlayer player, boolean remembered) {
		return Gates.echo(remembered, sinceCombat.applyAsLong(player), AtmosphereConfig.ticks(cfg().echoNoCombatSeconds));
	}

	/** Now on the clock the recent actions are kept on: the server's tick count. */
	protected static long now(ServerLevel level) {
		return level.getServer().getTickCount();
	}

	/** {@code minutes} of real time in ticks ({@code ModConfig.realTicks}, so {@code devFastMode} shortens it too). */
	protected static long minutes(double minutes) {
		return AtmosphereConfig.ticks(minutes * 60.0);
	}

	/** One sound of a replay: {@code at} ticks after it starts. */
	record Beat(int at, Holder<SoundEvent> sound, float volume, float pitch) {
		Beat(int at, SoundEvent sound, float volume, float pitch) {
			this(at, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), volume, pitch);
		}
	}

	/** The beats in time order (a stable sort: two on one tick keep their order). */
	static List<Beat> inOrder(List<Beat> beats) {
		List<Beat> sorted = new ArrayList<>(beats);
		sorted.sort(Comparator.comparingInt(Beat::at));
		return sorted;
	}

	/**
	 * Plays beats at one place on their own ticks (the first tick of the episode is tick 0), for everyone in hearing range
	 * ({@link SoundOut}), {@code SoundSource.BLOCKS}. The player it fired for is looked up again every tick (D-062.1):
	 * once they are gone (left, dead, a spectator, another level) the rest is dropped.
	 */
	static final class Replay implements Tasks.Episode {
		private final PlayerWatch watch;
		private final UUID player;
		private final ServerLevel level;
		private final Vec3 pos;
		private final List<Beat> beats;
		private final SoundOut sounds;
		private int age;
		private int next;

		Replay(PlayerWatch watch, UUID player, ServerLevel level, Vec3 pos, List<Beat> beats, SoundOut sounds) {
			this.watch = watch;
			this.player = player;
			this.level = level;
			this.pos = pos;
			this.beats = inOrder(beats);
			this.sounds = sounds;
		}

		@Override
		public boolean tick(MinecraftServer server) {
			if (target(watch, server, player, level).isEmpty()) {
				return false;
			}
			while (next < beats.size() && beats.get(next).at() <= age) {
				Beat beat = beats.get(next++);
				sounds.play(level, beat.sound(), SoundSource.BLOCKS, pos, beat.volume(), beat.pitch());
			}
			age++;
			return next < beats.size();
		}

		Vec3 pos() {
			return pos;
		}

		List<Beat> beats() {
			return beats;
		}
	}
}
