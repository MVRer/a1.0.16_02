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

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The tunnel ladder's live part (D-072), run by {@link DigTicker}: the steps in progress, at most one walk per player,
 * each step played when its tick comes and cancellable (the rest stop the moment the player looks back down the tunnel,
 * or anyone else, a spectator too, comes within the shared moment radius ({@link Ladder#alone}), or the player is
 * gone); and the detector's samples for each
 * underground participant (their positions over the last 10 s for the heading, and the run behind them), taken every
 * {@link DigTicker#LADDER_TICKS} ticks; and the debug arms ({@code /a1016 dig steps arm}, {@link Arm}). Per player by
 * UUID, looked up again every tick (D-062.1). Nothing is saved: a walk is a few seconds of sound, an arm two minutes.
 */
final class LadderSteps {
	/** Samples kept for the heading: 10 s of positions at the detector's cadence. */
	static final int SAMPLES = 200 / DigTicker.LADDER_TICKS;
	/** How long a debug arm waits for its moment: two minutes of server ticks (a debug timeout, not pacing). */
	static final long ARM_TICKS = 2400;

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
		/** Rung 3: how many lights went before the steps (FIX-GLIMPSE); the dark is checked again at the last step. */
		int darkened;

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
			return String.format(Locale.ROOT, "%s, %d of %d steps played%s%s", label, next, steps.size(), darkened > 0 ? ", " + darkened + " light(s) gone" : "",
					stopped == null ? "" : " (" + stopped + ")");
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
				if (!Ladder.alone(target, env)) {
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
					appear(target, level, spot);
				}
			}
			return next < steps.size();
		}

		/**
		 * Rung 3's last step: he is there. After the lights went (FIX-GLIMPSE) the spot is checked again first, dark at his
		 * feet and eyes ({@link Ladder#dark}) and taken by the figure; if not, no figure (the steps just stop, the lights
		 * stay gone).
		 */
		private void appear(ServerPlayer target, ServerLevel level, BlockPos cell) {
			Vec3 feet = Vec3.atBottomCenterOf(cell);
			if (darkened > 0) {
				if (!Ladder.dark(level, cell, env.config()) || !Ladder.dark(level, cell.above(), env.config())) {
					stopped = "still too lit (light " + Ladder.light(level, cell) + "): no figure";
					glimpse = FireResult.NO_SPOT;
					return;
				}
				Optional<String> refused = env.figure().glimpseRefusal(target, feet);
				if (refused.isPresent()) {
					stopped = "no figure: " + refused.get();
					glimpse = FireResult.NO_SPOT;
					return;
				}
			}
			glimpse = env.figure().glimpse(target, feet, Ladder.yawToward(feet, target.position()));
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
		 * The floor block's own step sound (stone if it is not solid), or what covers it where vanilla's step plays the
		 * cover ({@link SoundCards#stepSound}: a snow layer or leaf litter someone placed in the tunnel, glow lichen at the
		 * player's feet), PLAYERS, at {@code pos} on the cell's floor, for everyone in range: louder at the ear as it nears
		 * ({@link Ladder#stepVolume}; rung 3's reaching steps {@link Ladder#reachingVolume}), never heard outside the shared
		 * moment radius.
		 */
		private void play(ServerPlayer target, ServerLevel level, Vec3 pos, BlockPos cell, Step step) {
			SoundType type = SoundCards.stepSound(level, cell.below(), SoundCards.soundAt(level, cell.below()));
			double distance = pos.distanceTo(target.position());
			float volume;
			if (own) {
				volume = Mth.clamp(type.getVolume() * 0.15F * step.factor(), 0.0F, 1.0F);
			} else if (spot != null) {
				volume = Ladder.reachingVolume(distance, env.config());
			} else {
				volume = Ladder.stepVolume(distance, step.factor(), env.config());
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

	/** Where a debug arm reports and fires: live, the player's chat and the director; a recorder and the test's own ladder in tests. */
	interface Out {
		void tell(ServerPlayer player, String line);

		FireResult fire(MinecraftServer server, ServerPlayer player, Ladder.Rung rung, boolean fake);

		static Out live() {
			return new Out() {
				@Override
				public void tell(ServerPlayer player, String line) {
					DigCommands.tell(player, line);
				}

				@Override
				public FireResult fire(MinecraftServer server, ServerPlayer player, Ladder.Rung rung, boolean fake) {
					return Services.director().fire(server, player, rung.id, fake);
				}
			};
		}

		/** Tests: fires through this ladder (forced, as the director's debug fire), and keeps every line told. */
		static Out recording(Ladder.Env env, List<String> told) {
			return new Out() {
				@Override
				public void tell(ServerPlayer player, String line) {
					told.add(line);
				}

				@Override
				public FireResult fire(MinecraftServer server, ServerPlayer player, Ladder.Rung rung, boolean fake) {
					return Ladder.fire(rung, new FireContext(player, player.level(), fake, true, RandomSource.create(1L)), env);
				}
			};
		}
	}

	/**
	 * A debug arm ({@code /a1016 dig steps arm}): the rung fired for the player, forced (the rung order, the spacing and
	 * the used runs skipped, as {@code /a1016 fire}), the first moment its own context fits by the real card's rules
	 * (every gate, alone, and the look back: they face away from the tunnel behind them; rung 3 its spot), checked every
	 * {@link DigTicker#LADDER_TICKS}, within {@code ticks}. The player is told when it fires, and when it runs out with
	 * the last reason it did not fit.
	 */
	static final class Arm {
		final Ladder.Rung rung;
		final boolean fake;
		final UUID player;
		final String name;
		final long until;
		final long ticks;
		final Ladder.Env env;
		final Out out;
		long next;
		String last = "not checked yet";

		Arm(Ladder.Rung rung, boolean fake, ServerPlayer player, long now, long ticks, Ladder.Env env, Out out) {
			this.rung = rung;
			this.fake = fake;
			this.player = player.getUUID();
			this.name = player.getName().getString();
			this.until = now + ticks;
			this.ticks = ticks;
			this.env = env;
			this.out = out;
			this.next = now;
		}

		String describe() {
			return String.format(Locale.ROOT, "%s%s, waits for its moment (last: %s)", rung.id, fake ? " (fake)" : "", last);
		}
	}

	private final Map<UUID, Walk> walks = new LinkedHashMap<>();
	private final Map<UUID, Track> tracks = new HashMap<>();
	private final Map<UUID, Arm> arms = new LinkedHashMap<>();

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

	/** {@code /a1016 dig steps arm}: arms this rung for the arm's player (replacing an earlier arm of theirs). */
	void arm(Arm arm) {
		arms.put(arm.player, arm);
	}

	/** That player's armed debug fire. */
	Optional<Arm> armed(UUID player) {
		return Optional.ofNullable(arms.get(player));
	}

	/** A player stopped taking part: their steps stop, their samples and their arm go. */
	void forget(UUID player) {
		Walk walk = walks.remove(player);
		if (walk != null && walk.stopped == null) {
			walk.stopped = "the player is gone";
		}
		tracks.remove(player);
		arms.remove(player);
	}

	void clear() {
		walks.clear();
		tracks.clear();
		arms.clear();
	}

	/** Every tick: the walks in progress and the arms (nothing to do without one). */
	void tick(MinecraftServer server, long now) {
		if (!walks.isEmpty()) {
			Iterator<Walk> it = walks.values().iterator();
			while (it.hasNext()) {
				if (!it.next().tick(server, now)) {
					it.remove();
				}
			}
		}
		if (!arms.isEmpty()) {
			tickArms(server, now);
		}
	}

	private void tickArms(MinecraftServer server, long now) {
		Iterator<Arm> it = arms.values().iterator();
		List<Runnable> fires = new ArrayList<>();
		while (it.hasNext()) {
			Arm arm = it.next();
			Optional<ServerPlayer> found = arm.env.watch().participant(server, arm.player);
			if (now > arm.until) {
				it.remove();
				String line = String.format(Locale.ROOT, "[a1016] dig: the armed %s waited %d s and was dropped; last: %s", arm.rung.id, arm.ticks / 20, arm.last);
				A1016_02.LOGGER.info("{} ({})", line, arm.name);
				found.ifPresent(player -> arm.out.tell(player, line));
				continue;
			}
			if (now < arm.next || found.isEmpty() || !found.get().isAlive()) {
				continue;
			}
			arm.next = now + DigTicker.LADDER_TICKS;
			ServerPlayer player = found.get();
			Ladder.Check check = Ladder.check(arm.rung, player, arm.env, true, true);
			if (!check.fits()) {
				arm.last = Ladder.reason(check, player);
				continue;
			}
			it.remove();
			fires.add(() -> fire(server, arm, player));
		}
		fires.forEach(Runnable::run); // after the loop: a fire starts a walk, and may arm again
	}

	private static void fire(MinecraftServer server, Arm arm, ServerPlayer player) {
		FireResult result = arm.out.fire(server, player, arm.rung, arm.fake);
		String after = result == FireResult.FIRED
				? (arm.rung == Ladder.Rung.GLIMPSE ? "the steps are coming; when they stop, turn around" : "listen behind you")
				: Ladder.reason(Ladder.check(arm.rung, player, arm.env, true, false), player);
		String line = String.format(Locale.ROOT, "[a1016] dig: the armed %s%s fired for %s -> %s: %s", arm.rung.id,
				arm.fake && arm.rung != Ladder.Rung.GLIMPSE ? " (fake)" : "", arm.name, result, after);
		A1016_02.LOGGER.info(line);
		arm.out.tell(player, line);
	}

	/** Tests: these positions as the player's last samples (their heading follows them), with no run cached. */
	void samples(ServerPlayer player, List<Vec3> positions) {
		Track track = new Track(player.level().dimension());
		track.samples.addAll(positions);
		tracks.put(player.getUUID(), track);
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
