package com.forzacode.a1016_02.dig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.PlayerWatch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The tunnel ladder's live part (D-072), run by {@link DigTicker}: the steps in progress, at most one walk per player,
 * each step played when its tick comes and cancellable (the rest stop the moment the player looks back down the tunnel,
 * or a friend comes within the shared moment radius, or the player is gone); and the detector's samples for each
 * underground participant (their positions over the last 10 s for the heading, and the run behind them), taken every
 * {@link DigTicker#LADDER_TICKS} ticks. Per player by UUID, looked up again every tick (D-062.1). Nothing is saved:
 * a walk is a few seconds of sound.
 */
final class LadderSteps {
	/** Samples kept for the heading: 10 s of positions at the detector's cadence. */
	static final int SAMPLES = 200 / DigTicker.LADDER_TICKS;

	/**
	 * One planned step: {@code at} ticks after the walk starts, {@code back} blocks behind the player along the run (0:
	 * at the player's own feet; it may fall between cell centres, the cell it is in must be the tunnel's), its volume
	 * times {@code factor}.
	 */
	record Step(long at, double back, float factor) {
	}

	/** One rung's steps in progress for one player. */
	static final class Walk {
		final String label;
		final UUID player;
		final ResourceKey<Level> dimension;
		final Ladder.Env env;
		/** The player's feet cell when it started, and their heading then: the run lies {@code heading.getOpposite()}. */
		final BlockPos origin;
		final Direction heading;
		final List<Step> steps;
		/** Each step's place follows the player's progress along the run (else it is fixed from {@link #origin}). */
		final boolean follows;
		/** The rest stop when the player looks back or a friend comes close. */
		final boolean cancellable;
		/** The step is the player's own (a fake): their floor, at their feet, at vanilla's step volume. */
		final boolean own;
		/** Rung 3: the cell he stands in once the last step was on it; null for the other walks. */
		final @Nullable BlockPos spot;
		final long start;
		int next;
		@Nullable String stopped;
		@Nullable FireResult glimpse;

		Walk(String label, ServerPlayer player, TunnelRun run, Ladder.Env env, List<Step> steps, boolean follows, boolean cancellable, boolean own,
				@Nullable BlockPos spot, long start) {
			this.label = label;
			this.player = player.getUUID();
			this.dimension = player.level().dimension();
			this.env = env;
			this.origin = run.feet();
			this.heading = run.heading();
			this.steps = List.copyOf(steps);
			this.follows = follows;
			this.cancellable = cancellable;
			this.own = own;
			this.spot = spot == null ? null : spot.immutable();
			this.start = start;
		}

		/** Steps played so far. */
		int played() {
			return next;
		}

		String describe() {
			return String.format(Locale.ROOT, "%s, %d of %d steps played%s", label, next, steps.size(), stopped == null ? "" : " (" + stopped + ")");
		}

		/** One tick: false once it is over (every step played, stopped, or the player gone). */
		boolean tick(MinecraftServer server, long now) {
			Optional<ServerPlayer> found = env.watch().participant(server, player);
			if (found.isEmpty() || !found.get().isAlive() || !found.get().level().dimension().equals(dimension)) {
				stopped = "the player is gone";
				return false;
			}
			ServerPlayer target = found.get();
			if (cancellable) {
				if (env.watch().sharingWith(target).size() > 1) {
					stopped = "someone came close";
					return false;
				}
				if (Ladder.lookingBack(target, heading.getOpposite(), env.config().ladderLookBackDegrees)) {
					stopped = "they turned";
					return false;
				}
			}
			ServerLevel level = target.level();
			while (next < steps.size() && start + steps.get(next).at() <= now) {
				Step step = steps.get(next);
				Vec3 pos = posFor(target, step);
				BlockPos cell = pos == null ? null : BlockPos.containing(pos);
				if (cell == null || (!own && !TunnelRun.tunnelCell(level, env.watch(), cell))) {
					stopped = "the tunnel ends there";
					return false;
				}
				play(target, level, pos, cell, step);
				next++;
				if (next == steps.size() && spot != null) {
					Vec3 feet = Vec3.atBottomCenterOf(spot);
					glimpse = env.figure().glimpse(target, feet, Ladder.yawToward(feet, target.position()));
				}
			}
			return next < steps.size();
		}

		/**
		 * Where a step falls, on the floor of the run's line: {@code back} blocks behind the player along the run, from
		 * where they are now (a walk that follows) or from where they were (rung 3's fixed spot); null once they left the
		 * run's line.
		 */
		private @Nullable Vec3 posFor(ServerPlayer target, Step step) {
			BlockPos feet = target.blockPosition();
			if (own) {
				return Vec3.atBottomCenterOf(feet);
			}
			BlockPos base = origin;
			if (follows) {
				int dx = feet.getX() - origin.getX();
				int dz = feet.getZ() - origin.getZ();
				int along = dx * heading.getStepX() + dz * heading.getStepZ();
				Direction side = heading.getClockWise();
				int across = dx * side.getStepX() + dz * side.getStepZ();
				if (Math.abs(across) > 1 || Math.abs(feet.getY() - origin.getY()) > 1) {
					return null;
				}
				base = origin.relative(heading, along);
			}
			Direction back = heading.getOpposite();
			return Vec3.atBottomCenterOf(base).add(back.getStepX() * step.back(), 0.0, back.getStepZ() * step.back());
		}

		/**
		 * The floor block's own step sound (stone if it is not solid), PLAYERS, at {@code pos} on the cell's floor, for
		 * everyone in range: louder as it nears, at most 1 (vanilla's 16 blocks), except rung 3's reaching steps
		 * ({@link Ladder#reachingVolume}).
		 */
		private void play(ServerPlayer target, ServerLevel level, Vec3 pos, BlockPos cell, Step step) {
			SoundType type = SoundCards.soundAt(level, cell.below());
			double distance = pos.distanceTo(target.position());
			float volume;
			if (own) {
				volume = Mth.clamp(type.getVolume() * 0.15F * step.factor(), 0.0F, 1.0F);
			} else if (spot != null) {
				volume = Ladder.reachingVolume(distance, env.config());
			} else {
				volume = Mth.clamp(Ladder.volumeAt(distance, env.config()) * step.factor(), 0.0F, 1.0F);
			}
			env.sounds().play(level, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(type.getStepSound()), SoundSource.PLAYERS, pos, volume, type.getPitch());
		}
	}

	/** The detector's samples for one underground participant. */
	private static final class Track {
		final ResourceKey<Level> dimension;
		final ArrayDeque<Vec3> samples = new ArrayDeque<>();
		@Nullable TunnelRun run;
		@Nullable BlockPos feet;
		@Nullable PlayerWatch watch;
		long tick = Long.MIN_VALUE;

		Track(ResourceKey<Level> dimension) {
			this.dimension = dimension;
		}
	}

	private final Map<UUID, Walk> walks = new LinkedHashMap<>();
	private final Map<UUID, Track> tracks = new HashMap<>();

	/** Starts a walk, replacing that player's walk in progress (if any). */
	void start(Walk walk) {
		Walk old = walks.put(walk.player, walk);
		if (old != null && old.stopped == null) {
			old.stopped = "replaced";
		}
	}

	/** That player's walk in progress. */
	Optional<Walk> walk(UUID player) {
		return Optional.ofNullable(walks.get(player));
	}

	/** Players with a walk in progress (tests and debug). */
	Set<UUID> walking() {
		return walks.keySet().stream().collect(Collectors.toUnmodifiableSet());
	}

	/** A player stopped taking part: their steps stop, their samples go. */
	void forget(UUID player) {
		Walk walk = walks.remove(player);
		if (walk != null && walk.stopped == null) {
			walk.stopped = "the player is gone";
		}
		tracks.remove(player);
	}

	void clear() {
		walks.clear();
		tracks.clear();
	}

	/** Every tick: the walks in progress (nothing to do without one). */
	void tick(MinecraftServer server, long now) {
		if (walks.isEmpty()) {
			return;
		}
		Iterator<Walk> it = walks.values().iterator();
		while (it.hasNext()) {
			if (!it.next().tick(server, now)) {
				it.remove();
			}
		}
	}

	/**
	 * Every {@link DigTicker#LADDER_TICKS}: for each underground participant, a position sample and the run behind them
	 * (the detector walks back at most {@link TunnelRun#MAX_BACK} cells); anyone else's samples go. The cost of the
	 * ladder: nothing for players above ground.
	 */
	void sample(List<ServerPlayer> players, PlayerWatch watch, DigConfig config, long now) {
		List<UUID> seen = new ArrayList<>(players.size());
		for (ServerPlayer player : players) {
			ServerLevel level = player.level();
			BlockPos feet = player.blockPosition();
			if (!level.isLoaded(feet) || !DigTicker.underground(level, feet)) {
				continue;
			}
			seen.add(player.getUUID());
			Track track = tracks.get(player.getUUID());
			if (track == null || !track.dimension.equals(level.dimension())) {
				track = new Track(level.dimension());
				tracks.put(player.getUUID(), track);
			}
			track.samples.addLast(player.position());
			while (track.samples.size() > SAMPLES) {
				track.samples.removeFirst();
			}
			track.run = TunnelRun.detect(level, watch, feet, heading(player), config.ladderMinRun, config.ladderMinSealedShare).orElse(null);
			track.feet = feet.immutable();
			track.watch = watch;
			track.tick = now;
		}
		tracks.keySet().retainAll(seen);
	}

	/** The player's heading: their movement over the last 10 s of samples, else their look ({@link TunnelRun#heading}). */
	Direction heading(ServerPlayer player) {
		Track track = tracks.get(player.getUUID());
		List<Vec3> samples = track == null || !track.dimension.equals(player.level().dimension()) ? List.of() : List.copyOf(track.samples);
		return TunnelRun.heading(samples, player.getYRot());
	}

	/**
	 * The run behind the player now: the detector's latest answer if it is fresh (this cadence, the same feet cell and
	 * the same watch), else worked out now.
	 */
	Optional<TunnelRun> run(ServerPlayer player, PlayerWatch watch, DigConfig config, long now) {
		Track track = tracks.get(player.getUUID());
		BlockPos feet = player.blockPosition();
		if (track != null && track.watch == watch && track.tick > now - DigTicker.LADDER_TICKS && feet.equals(track.feet)
				&& track.dimension.equals(player.level().dimension())) {
			return Optional.ofNullable(track.run);
		}
		return TunnelRun.detect(player.level(), watch, feet, heading(player), config.ladderMinRun, config.ladderMinSealedShare);
	}
}
