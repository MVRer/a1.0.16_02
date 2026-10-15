package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.core.Figure;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SoundCues;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Someone behind you, the tunnel ladder (D-072): real vanilla footsteps behind a player in a straight tunnel players
 * dug ({@link TunnelRun}), sounds only. Each rung comes only after the one before it happened to that player:
 * <ol>
 * <li>{@code steps_behind_once}: one or two steps 10 to 12 blocks back, then nothing (once per player).</li>
 * <li>{@code steps_behind_coming}: 6 to 10 steps from about 16 back closing to 4 to 6, then silence; sometimes a
 * quieter sneaking walk.</li>
 * <li>{@code steps_behind_glimpse}: the steps come up to a dark spot 14 to 20 back, and he is there ({@link Figure},
 * D-073), unless the player turned first.</li>
 * </ol>
 * The gates (D-072): underground, a straight run of at least {@code ladderMinRun} dug cells behind along the heading,
 * nobody else within {@code sharedMomentRadius} (so nobody can confirm it: no other participant, and no other player
 * at all, spectators included, since the steps reach everyone in range, {@link #alone}), no combat for 30 s, not
 * riding, swimming or in fluid. Spacing per player on their own clock: one ladder event per {@code ladderGapMinutes},
 * rung 3 once per in-game day and {@code ladderGlimpseGapHours} apart, a used run never again. A forced fire skips the
 * rung order, the spacing and the used runs (and is not counted for them), never the gates. Nothing in the world
 * changes: no block edit at all, the steps are packets ({@link SoundCues#playAt}).
 */
final class Ladder {
	/**
	 * The farthest back rung 2's fake steps go: a step at volume 1 or less is heard within vanilla's 16 blocks, and a
	 * player following the run may be off its line by up to half a cell along it, a cell and a half across (a 2-wide
	 * tunnel) and a block up, so a step 14.5 back is still about 15 blocks from them.
	 */
	static final double FAKE_FARTHEST = 14.5;

	/** The three rungs, in order. */
	enum Rung {
		ONCE(1, "steps_behind_once"),
		COMING(2, "steps_behind_coming"),
		GLIMPSE(3, "steps_behind_glimpse");

		final int level;
		final String id;

		Rung(int level, String id) {
			this.level = level;
			this.id = id;
		}
	}

	/** Why a rung does not fit right now. */
	enum Why {
		RIDING_OR_IN_FLUID("riding, swimming or in fluid"),
		COMBAT("combat too recently"),
		NOT_ALONE("someone else (a spectator too) within the shared moment radius"),
		NOT_UNDERGROUND("not underground"),
		NO_RUN("no straight player-dug run behind"),
		RUNG("not this rung's turn"),
		SPACING("a ladder event too recently"),
		COMING_TOO_RECENT("rung 2 too recently"),
		GLIMPSE_TODAY("a glimpse already today"),
		GLIMPSE_SPACING("a glimpse too recently"),
		RUN_USED("this run was used before"),
		LOOKING_BACK("looking back down the tunnel"),
		NO_SPOT("no dark spot he could stand in");

		final String text;

		Why(String text) {
			this.text = text;
		}
	}

	/** Where the steps go: {@link SoundCues#playAt} live, a recorder in tests. */
	interface Sounds {
		void play(ServerLevel level, Holder<SoundEvent> sound, SoundSource source, Vec3 pos, float volume, float pitch);

		/**
		 * Everyone in {@code level} a step played there could reach, spectators and the dead included: live, the level's
		 * players ({@link SoundCues#playAt} sends to each of them in range); a test's own players in tests.
		 */
		default List<ServerPlayer> listeners(ServerLevel level) {
			return level.players();
		}
	}

	/**
	 * What the ladder reads and uses: whose moment it is, the memory, the figure, the numbers, each player's own clock,
	 * the in-game day, the underground rule and the sound out. {@link #live} for the game; tests make their own.
	 */
	record Env(PlayerWatch watch, DigData data, Figure figure, DigConfig config, ToLongFunction<UUID> playTicks, LongSupplier day,
			Predicate<ServerPlayer> underground, Sounds sounds) {
		static Env live(MinecraftServer server) {
			return new Env(Services.watch(), DigData.get(server), Services.figure(), DigConfig.get(), uuid -> GameClock.playTicks(server, uuid),
					() -> GameClock.day(server), player -> DigTicker.underground(player.level(), player.blockPosition()),
					(level, sound, source, pos, volume, pitch) -> SoundCues.playAt(level, sound, source, pos, volume, pitch));
		}
	}

	/** A rung's answer now: why not (null: it fits), the run behind the player, and rung 3's spot (cells back, or -1). */
	record Check(@Nullable Why why, @Nullable TunnelRun run, int spot) {
		boolean fits() {
			return why == null;
		}
	}

	private Ladder() {
	}

	/**
	 * Whether {@code rung} fits {@code player} now. {@code forced} (a debug fire) skips the rung order, the spacing, the
	 * used runs and the look back, never the gates. Cheap: the run is the detector's (cached for a sampled player), and
	 * rung 3 asks {@link Figure#canGlimpse} for at most a few cells.
	 */
	static Check check(Rung rung, ServerPlayer player, Env env, boolean forced) {
		ServerLevel level = player.level();
		DigConfig config = env.config();
		BlockPos feet = player.blockPosition();
		if (player.isPassenger() || player.isSwimming() || player.isInLiquid() || !level.getFluidState(feet).isEmpty()
				|| !level.getFluidState(feet.above()).isEmpty()) {
			return new Check(Why.RIDING_OR_IN_FLUID, null, -1);
		}
		if (env.watch().ticksSinceCombat(player) <= ModConfig.realTicks(config.ladderNoCombatSeconds)) {
			return new Check(Why.COMBAT, null, -1);
		}
		if (!alone(player, env)) {
			return new Check(Why.NOT_ALONE, null, -1);
		}
		if (!env.underground().test(player)) {
			return new Check(Why.NOT_UNDERGROUND, null, -1);
		}
		Optional<TunnelRun> found = DigTicker.INSTANCE.ladder().run(player, env.watch(), config, level.getServer().getTickCount());
		if (found.isEmpty()) {
			return new Check(Why.NO_RUN, null, -1);
		}
		TunnelRun run = found.get();
		if (!forced) {
			UUID uuid = player.getUUID();
			LadderMemory memory = env.data().ladderOf(uuid).orElseGet(LadderMemory::new);
			long now = env.playTicks().applyAsLong(uuid);
			boolean turn = switch (rung) {
				case ONCE -> memory.rung < 1;
				case COMING -> memory.rung >= 1;
				case GLIMPSE -> memory.rung >= 2 && memory.lastComing != Long.MIN_VALUE;
			};
			if (!turn) {
				return new Check(Why.RUNG, run, -1);
			}
			if (memory.lastEvent != Long.MIN_VALUE && now - memory.lastEvent < ModConfig.realTicks(config.ladderGapMinutes * 60)) {
				return new Check(Why.SPACING, run, -1);
			}
			if (rung == Rung.GLIMPSE) {
				if (now - memory.lastComing < ModConfig.realTicks(config.ladderGlimpseAfterComingMinutes * 60)) {
					return new Check(Why.COMING_TOO_RECENT, run, -1);
				}
				if (memory.glimpseDay != Long.MIN_VALUE && memory.glimpseDay == env.day().getAsLong()) {
					return new Check(Why.GLIMPSE_TODAY, run, -1);
				}
				if (memory.lastGlimpse != Long.MIN_VALUE && now - memory.lastGlimpse < ModConfig.realTicks(config.ladderGlimpseGapHours * 3600)) {
					return new Check(Why.GLIMPSE_SPACING, run, -1);
				}
			}
			if (memory.used(level.dimension(), run.start(), config.ladderUsedRunRadius)) {
				return new Check(Why.RUN_USED, run, -1);
			}
			if (lookingBack(player, run.back(), config.ladderLookBackDegrees)) {
				return new Check(Why.LOOKING_BACK, run, -1);
			}
		}
		if (rung == Rung.GLIMPSE) {
			int spot = glimpseSpot(player, run, env);
			if (spot < 0) {
				return new Check(Why.NO_SPOT, run, -1);
			}
			return new Check(null, run, spot);
		}
		return new Check(null, run, -1);
	}

	/**
	 * Fires {@code rung} for the context's player: checks again ({@link #check}), plans the steps and starts them
	 * ({@link LadderSteps}), and remembers the rung. {@code SKIPPED} when a gate fails, {@code NO_SPOT} when rung 3 has
	 * no spot he could stand in (the director keeps it).
	 */
	static FireResult fire(Rung rung, FireContext ctx, Env env) {
		ServerPlayer player = ctx.player();
		boolean fake = ctx.fake() && rung != Rung.GLIMPSE;
		Check check = check(rung, player, env, ctx.forced());
		if (check.why() == Why.NO_SPOT) {
			return FireResult.NO_SPOT;
		}
		if (!check.fits() || check.run() == null) {
			return FireResult.SKIPPED;
		}
		TunnelRun run = check.run();
		DigConfig config = env.config();
		RandomSource random = ctx.random();
		long start = ctx.level().getServer().getTickCount();
		LadderSteps.Walk walk = switch (rung) {
			case ONCE -> fake ? ownStepLate(player, run, env, start) : once(player, run, env, random, start);
			case COMING -> fake ? fadingAway(player, run, env, start) : coming(player, run, env, random, start);
			case GLIMPSE -> glimpse(player, run, env, random, start, check.spot());
		};
		DigTicker.INSTANCE.ladder().start(walk);
		remember(rung, player, run, env, fake, ctx.forced());
		return FireResult.FIRED;
	}

	/** What a fire leaves in the player's memory. A forced fire counts for the rung order only, a fake for the spacing only. */
	private static void remember(Rung rung, ServerPlayer player, TunnelRun run, Env env, boolean fake, boolean forced) {
		LadderMemory memory = env.data().ladder(player.getUUID());
		long now = env.playTicks().applyAsLong(player.getUUID());
		if (!fake) {
			memory.rung = Math.max(memory.rung, rung.level);
			if (rung == Rung.COMING) {
				memory.lastComing = now;
			}
		}
		if (!forced) {
			memory.lastEvent = now;
			if (!fake) {
				memory.use(player.level().dimension(), run.start());
				if (rung == Rung.GLIMPSE) {
					memory.lastGlimpse = now;
					memory.glimpseDay = env.day().getAsLong();
				}
			}
		}
		env.data().setDirty();
	}

	// --- the walks ---

	/** Rung 1: one or two steps 10 to 12 back, following the player along the run. */
	private static LadderSteps.Walk once(ServerPlayer player, TunnelRun run, Env env, RandomSource random, long start) {
		DigConfig config = env.config();
		int min = Math.max(1, Math.min(config.ladderOnceBackMin, run.length()));
		int max = Mth.clamp(config.ladderOnceBackMax, min, run.length());
		List<LadderSteps.Step> steps = new ArrayList<>();
		if (max > min && random.nextDouble() < config.ladderOnceTwoStepsChance) {
			int first = min + 1 + random.nextInt(max - min);
			steps.add(new LadderSteps.Step(0, first, 1.0F));
			steps.add(new LadderSteps.Step(Math.max(1, config.ladderStepTicks), first - 1, 1.0F));
		} else {
			steps.add(new LadderSteps.Step(0, min + random.nextInt(max - min + 1), 1.0F));
		}
		return new LadderSteps.Walk(Rung.ONCE.id, player, run, env, steps, true, true, false, null, start);
	}

	/** Rung 1's fake: just the player's own step, late. */
	private static LadderSteps.Walk ownStepLate(ServerPlayer player, TunnelRun run, Env env, long start) {
		List<LadderSteps.Step> steps = List.of(new LadderSteps.Step(Math.max(1, env.config().ladderStepTicks), 0, 1.0F));
		return new LadderSteps.Walk(Rung.ONCE.id + " (fake)", player, run, env, steps, true, false, true, null, start);
	}

	/** Rung 2: steps from about 16 back closing to 4 to 6, a walk or (sometimes) a quieter, slower sneak. */
	private static LadderSteps.Walk coming(ServerPlayer player, TunnelRun run, Env env, RandomSource random, long start) {
		DigConfig config = env.config();
		boolean sneak = random.nextDouble() < config.ladderSneakChance;
		int cadence = Math.max(1, sneak ? config.ladderSneakStepTicks : config.ladderStepTicks);
		float factor = sneak ? config.ladderSneakVolumeFactor : 1.0F;
		int stopMin = Math.max(1, config.ladderComingStopMin);
		int stop = between(random, stopMin, Math.max(stopMin, config.ladderComingStopMax));
		int from = Mth.clamp(config.ladderComingFrom, stop, run.length());
		int count = Math.min(between(random, Math.max(1, config.ladderComingStepsMin), Math.max(1, config.ladderComingStepsMax)), from - stop + 1);
		List<LadderSteps.Step> steps = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			int back = count == 1 ? stop : (int) Math.round(from + (stop - from) * (i / (double) (count - 1)));
			steps.add(new LadderSteps.Step((long) i * cadence, back, factor));
		}
		return new LadderSteps.Walk(Rung.COMING.id + (sneak ? " (sneaking)" : ""), player, run, env, steps, true, true, false, null, start);
	}

	/**
	 * Rung 2's fake: a few steps far off that go away, fading out. The first {@code ladderComingFakeFrom} (12) back, the
	 * last {@code ladderComingFakeTo} (14) back, evenly between and one every {@code ladderStepTicks}, each quieter, down
	 * to {@code ladderComingFakeLastFactor} of a step's volume there. All of them inside vanilla's 16-block range of a
	 * step at volume 1 or less ({@link #FAKE_FARTHEST}, so even a player off their cell's centre hears the last one,
	 * about as well as rung 2's first step), and none past the run's end.
	 */
	private static LadderSteps.Walk fadingAway(ServerPlayer player, TunnelRun run, Env env, long start) {
		DigConfig config = env.config();
		int count = Math.max(1, config.ladderComingFakeSteps);
		int cadence = Math.max(1, config.ladderStepTicks);
		double to = Mth.clamp(config.ladderComingFakeTo, 1.0, Math.min(run.length(), FAKE_FARTHEST));
		double from = Mth.clamp(config.ladderComingFakeFrom, 1.0, to);
		float last = Mth.clamp(config.ladderComingFakeLastFactor, 0.0F, 1.0F);
		List<LadderSteps.Step> steps = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			double t = count == 1 ? 0.0 : i / (double) (count - 1);
			steps.add(new LadderSteps.Step((long) i * cadence, from + (to - from) * t, (float) Mth.lerp(t, 1.0, last)));
		}
		return new LadderSteps.Walk(Rung.COMING.id + " (fake)", player, run, env, steps, true, true, false, null, start);
	}

	/**
	 * Rung 3: steps coming up the tunnel to the spot ({@code spot} cells back, fixed), the last one on it; he is there
	 * after it. They start inside the reaching steps' hearing range ({@link #reach}), so the first is already faintly
	 * heard: at most a step and a half apart, closer when the spot is far, so a spot up to 19 back still gets three
	 * heard steps (only one 20 back starts past the edge to have room for three).
	 */
	private static LadderSteps.Walk glimpse(ServerPlayer player, TunnelRun run, Env env, RandomSource random, long start, int spot) {
		DigConfig config = env.config();
		int cadence = Math.max(1, config.ladderStepTicks);
		int wanted = between(random, Math.max(1, config.ladderGlimpseStepsMin), Math.max(1, config.ladderGlimpseStepsMax));
		double edge = reach(config) - 0.5;
		double spacing = Mth.clamp((edge - spot) / 2.0, 0.5, 1.5);
		double from = Math.min(run.length(), spot + spacing * (wanted - 1));
		from = Math.max(spot, Math.min(from, Math.max(edge, spot + 2 * spacing)));
		int count = Math.max(1, Math.min(wanted, (int) Math.floor((from - spot) / spacing + 1.0E-6) + 1));
		List<LadderSteps.Step> steps = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			double back = i == count - 1 ? spot : from + (spot - from) * (i / (double) (count - 1));
			steps.add(new LadderSteps.Step((long) i * cadence, back, 1.0F));
		}
		return new LadderSteps.Walk(Rung.GLIMPSE.id, player, run, env, steps, false, true, false, run.cell(spot), start);
	}

	// --- geometry and numbers ---

	/**
	 * Rung 3's spot: the nearest cell of the run {@code ladderGlimpseSpotMin} to {@code ladderGlimpseSpotMax} back, with
	 * at least two cells of run past it for his steps, that is dark ({@link #dark} at his feet and his eyes: past the
	 * torchlight and out of the daylight down a shaft or in from a hillside mouth) and where the figure could stand now
	 * ({@link Figure#canGlimpse}, asked only for a dark cell). Cells back, or -1.
	 */
	static int glimpseSpot(ServerPlayer player, TunnelRun run, Env env) {
		DigConfig config = env.config();
		ServerLevel level = player.level();
		int max = Math.min(config.ladderGlimpseSpotMax, run.length() - 2);
		for (int back = Math.max(1, config.ladderGlimpseSpotMin); back <= max; back++) {
			BlockPos cell = run.cell(back);
			if (dark(level, cell, config) && dark(level, cell.above(), config) && env.figure().canGlimpse(player, Vec3.atBottomCenterOf(cell))) {
				return back;
			}
		}
		return -1;
	}

	/**
	 * True if the light a player would see at {@code cell} is at most {@code ladderGlimpseMaxBlockLight} (3): its raw
	 * brightness, the block light or the sky light less the level's sky darkening, whichever is more (vanilla's
	 * {@code getMaxLocalRawBrightness}; the figure's {@code canGlimpse} holds his spot to the same rule). So a cell
	 * under an open shaft or near a tunnel mouth is lit by day, and the sky's last light at dusk counts too.
	 */
	static boolean dark(ServerLevel level, BlockPos cell, DigConfig config) {
		return level.getMaxLocalRawBrightness(cell) <= config.ladderGlimpseMaxBlockLight;
	}

	/**
	 * Alone (D-072, D-074): nobody shares the moment ({@link PlayerWatch#sharingWith}) and no other player at all,
	 * spectators and the dead included, is within {@code sharedMomentRadius} of them. The steps reach everyone in range
	 * ({@link SoundCues#playAt}), so a spectating friend beside the player would hear each one and could confirm it.
	 */
	static boolean alone(ServerPlayer player, Env env) {
		if (env.watch().sharingWith(player).size() > 1) {
			return false;
		}
		ServerLevel level = player.level();
		double radius = ModConfig.pacing().sharedMomentRadius;
		for (ServerPlayer other : env.sounds().listeners(level)) {
			if (!other.getUUID().equals(player.getUUID()) && other.level() == level && other.distanceToSqr(player) <= radius * radius) {
				return false;
			}
		}
		return true;
	}

	/** True if the player looks (horizontally) within {@code degrees} of the tunnel behind them. Looking straight up or down is not. */
	static boolean lookingBack(ServerPlayer player, Direction back, double degrees) {
		Vec3 look = player.getLookAngle();
		double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
		if (horizontal < 1.0E-3) {
			return false;
		}
		double cos = (look.x * back.getStepX() + look.z * back.getStepZ()) / horizontal;
		return cos >= Math.cos(Math.toRadians(degrees)) - 1.0E-9;
	}

	/**
	 * A step's volume {@code distance} blocks from the player: {@code ladderVolumeFar} at {@code ladderComingFrom},
	 * rising to {@code ladderVolumeNear} at {@code ladderComingStopMin}, never above 1 (vanilla's 16-block range).
	 */
	static float volumeAt(double distance, DigConfig config) {
		double far = config.ladderComingFrom;
		double near = config.ladderComingStopMin;
		double t = far > near ? Mth.clamp((far - distance) / (far - near), 0.0, 1.0) : 1.0;
		return (float) Mth.clamp(Mth.lerp(t, config.ladderVolumeFar, config.ladderVolumeNear), 0.0, 1.0);
	}

	/**
	 * Rung 3's steps' volume {@code distance} blocks from the player: {@code ladderGlimpseVolumeMax} (1.3: vanilla's range
	 * {@code max(volume, 1) * 16}, about 21 blocks, so steps 14 to 20 back are heard, louder as they near by the
	 * client's own fade), and always under what a friend outside the shared moment radius could hear (their distance
	 * from the step is at least that radius minus {@code distance}).
	 */
	static float reachingVolume(double distance, DigConfig config) {
		double alone = (ModConfig.pacing().sharedMomentRadius - distance) / 16.0 * 0.99;
		return (float) Math.max(0.0, Math.min(config.ladderGlimpseVolumeMax, alone));
	}

	/** How far rung 3's steps are heard at {@code ladderGlimpseVolumeMax} (vanilla's {@code max(volume, 1) * 16} blocks). */
	static double reach(DigConfig config) {
		return Math.max(1.0F, config.ladderGlimpseVolumeMax) * 16.0;
	}

	/** The yaw (degrees, the entity's) of someone at {@code from} facing {@code to}. */
	static float yawToward(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0F;
	}

	private static int between(RandomSource random, int min, int max) {
		return min + random.nextInt(Math.max(1, max - min + 1));
	}

	// --- debug ---

	/** {@code /a1016 dig steps}: the player's rung, last event, used runs, what the detector sees now and each rung's answer. */
	static List<String> debugLines(ServerPlayer player, Env env) {
		List<String> lines = new ArrayList<>();
		UUID uuid = player.getUUID();
		String name = player.getName().getString();
		long now = env.playTicks().applyAsLong(uuid);
		Optional<LadderMemory> stored = env.data().ladderOf(uuid);
		LadderMemory memory = stored.orElseGet(LadderMemory::new);
		lines.add(String.format(Locale.ROOT, "[a1016] dig: %s's tunnel ladder: highest rung %d; last event %s; last rung 2 %s; last glimpse %s%s", name,
				memory.rung, ago(now, memory.lastEvent), ago(now, memory.lastComing), ago(now, memory.lastGlimpse),
				memory.glimpseDay == Long.MIN_VALUE ? "" : " (day " + memory.glimpseDay + ", today is " + env.day().getAsLong() + ")"));
		BlockPos feet = player.blockPosition();
		String nearest = memory.usedRuns.stream().filter(used -> used.dimension().equals(player.level().dimension()))
				.min((a, b) -> Double.compare(a.pos().distSqr(feet), b.pos().distSqr(feet)))
				.map(used -> String.format(Locale.ROOT, ", nearest starts at %s (%.0f blocks)", used.pos().toShortString(), Math.sqrt(used.pos().distSqr(feet))))
				.orElse("");
		lines.add(String.format(Locale.ROOT, "used runs: %d (a run starting within %d of one never fires again)%s", memory.usedRuns.size(),
				env.config().ladderUsedRunRadius, nearest));
		DigConfig config = env.config();
		Direction heading = DigTicker.INSTANCE.ladder().heading(player);
		Optional<TunnelRun> run = DigTicker.INSTANCE.ladder().run(player, env.watch(), config, player.level().getServer().getTickCount());
		lines.add(run.map(r -> String.format(Locale.ROOT, "detector: a run of %d cells behind (heading %s), from %s back to %s; walls sealed on %d%s",
				r.length(), r.heading().getSerializedName(), r.cell(1).toShortString(), r.start().toShortString(), r.sealed(),
				env.underground().test(player) ? "" : " (but not underground)"))
				.orElse(String.format(Locale.ROOT, "detector: no straight dug run of %d+ cells behind (heading %s)%s", config.ladderMinRun,
						heading.getSerializedName(), env.underground().test(player) ? "" : "; not underground")));
		for (Rung rung : Rung.values()) {
			Check check = check(rung, player, env, false);
			lines.add(rung.id + ": " + (check.fits() ? "fits now" + (check.spot() >= 0 ? " (his spot " + check.spot() + " back)" : "")
					: "no (" + check.why().text + ")"));
		}
		lines.add("steps now: " + DigTicker.INSTANCE.ladder().walk(uuid).map(LadderSteps.Walk::describe).orElse("none"));
		return lines;
	}

	private static String ago(long now, long then) {
		return then == Long.MIN_VALUE ? "never" : String.format(Locale.ROOT, "%.1f min of play ago", (now - then) / 1200.0);
	}

	/** The used runs as places (tests and debug). */
	static List<GlobalPos> usedRuns(Env env, UUID player) {
		return env.data().ladderOf(player).map(memory -> List.copyOf(memory.usedRuns)).orElse(List.of());
	}
}
