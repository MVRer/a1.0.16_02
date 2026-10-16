package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Figure;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceService;

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
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Someone behind you, the tunnel ladder (D-072): real vanilla footsteps behind a player in a straight tunnel players
 * dug ({@link TunnelRun}), sounds only. Each rung comes only after the one before it happened to that player:
 * <ol>
 * <li>{@code steps_behind_once}: one or two steps 10 to 12 blocks back, then nothing (once per player).</li>
 * <li>{@code steps_behind_coming}: 6 to 10 steps from about 16 back closing to 4 to 6, then silence; sometimes a
 * quieter sneaking walk.</li>
 * <li>{@code steps_behind_glimpse}: he makes the dark first (the lights players placed that light the tunnel 6 to 24
 * back go, out of view, FIX-GLIMPSE), then the steps come up to the darkest spot 14 to 20 back, and he is there
 * ({@link Figure}, D-073), unless the player turned first.</li>
 * </ol>
 * The gates (D-072): underground, a straight run of at least {@code ladderMinRun} dug cells behind along the heading,
 * nobody else within {@code sharedMomentRadius} (so nobody can confirm it: no other participant, and no other player
 * at all, spectators included, since the steps reach everyone in range, {@link #alone}), no combat for 30 s, not
 * riding, swimming or in fluid. Spacing per player on their own clock: one ladder event per {@code ladderGapMinutes},
 * rung 3 once per in-game day and {@code ladderGlimpseGapHours} apart, a used run never again. A forced fire skips the
 * rung order, the spacing, the used runs and the look back (and is not counted for them), never the gates. The only
 * edit is rung 3's lights, through the trace service ({@value #CAUSE_GLIMPSE_TORCHES}); the steps are packets
 * ({@link SoundCues#playAt}).
 */
final class Ladder {
	/**
	 * The farthest back rung 2's fake steps go: a step at volume 1 or less is heard within vanilla's 16 blocks, and a
	 * player following the run may be off its line by up to half a cell along it, a cell and a half across (a 2-wide
	 * tunnel) and a block up, so a step 14.5 back is still about 15 blocks from them.
	 */
	static final double FAKE_FARTHEST = 14.5;
	/** The cause rung 3's lights go with (ledgered; their supports stay, nothing drops). */
	static final String CAUSE_GLIMPSE_TORCHES = "dig:glimpse_torches";
	/** What a forced rung 3 with no spot adds while the player looks back down the tunnel (the real card waits for them to face away). */
	static final String FACE_AWAY = "you are looking back down the tunnel: face away from the tunnel behind you, then fire (the real card waits for that)";
	/** The brightest light a block gives (vanilla's 15): one farther than this less the dark limit lights no cell over it. */
	private static final int MAX_EMISSION = 15;
	/** The first number in parentheses of a reason ("too lit (light 6)"), for grouping the candidates' reasons. */
	private static final Pattern NUMBER = Pattern.compile("\\(([^()\\d]*)(\\d+)([^()]*)\\)");

	/** The three rungs, in order. */
	enum Rung {
		ONCE(1, "steps_behind_once", "once"),
		COMING(2, "steps_behind_coming", "coming"),
		GLIMPSE(3, "steps_behind_glimpse", "glimpse");

		final int level;
		final String id;
		/** The short name {@code /a1016 dig steps arm} takes. */
		final String word;

		Rung(int level, String id, String word) {
			this.level = level;
			this.id = id;
			this.word = word;
		}

		/** The rung by its short name or its card id. */
		static Optional<Rung> byName(String name) {
			for (Rung rung : values()) {
				if (rung.word.equals(name) || rung.id.equals(name)) {
					return Optional.of(rung);
				}
			}
			return Optional.empty();
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

	/** Why a light that lights the tunnel behind stays when rung 3 makes the dark. */
	enum Stays {
		NOBODYS("no player placed it"),
		NEVER_GOES("not a torch or lantern"),
		PROTECTED("in a protected area"),
		TOO_MANY("over the limit"),
		IN_VIEW("in view");

		final String text;

		Stays(String text) {
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
	 * the in-game day, the underground rule, the sound out, whether blocks are out of everyone's view and the trace
	 * service (rung 3's lights). {@link #live} for the game; tests make their own.
	 */
	record Env(PlayerWatch watch, DigData data, Figure figure, DigConfig config, ToLongFunction<UUID> playTicks, LongSupplier day,
			Predicate<ServerPlayer> underground, Sounds sounds, Blind.View view, TraceService traces) {
		static Env live(MinecraftServer server) {
			return new Env(Services.watch(), DigData.get(server), Services.figure(), DigConfig.get(), uuid -> GameClock.playTicks(server, uuid),
					() -> GameClock.day(server), player -> DigTicker.underground(player.level(), player.blockPosition()),
					(level, sound, source, pos, volume, pitch) -> SoundCues.playAt(level, sound, source, pos, volume, pitch),
					(level, positions) -> Services.traces().isOutOfView(level, positions), Services.traces());
		}
	}

	/**
	 * A rung's answer now: why not (null: it fits), the run behind the player, rung 3's spot (cells back, or -1) and its
	 * plan (the lights that go first and every candidate as checked; null for the other rungs and before the spot).
	 */
	record Check(@Nullable Why why, @Nullable TunnelRun run, int spot, @Nullable Plan plan) {
		Check(@Nullable Why why, @Nullable TunnelRun run, int spot) {
			this(why, run, spot, null);
		}

		boolean fits() {
			return why == null;
		}

		/** The lights rung 3 takes before its steps (none for the other rungs, or without a spot). */
		List<BlockPos> lights() {
			return plan == null || spot < 0 ? List.of() : plan.taken();
		}
	}

	/**
	 * One cell of the run that could be his spot, as checked: cells back, its light now and once the lights that go are
	 * gone (at most), and why he could not stand there (null: he could).
	 */
	record Candidate(int back, int light, int after, @Nullable String why) {
		boolean ok() {
			return why == null;
		}
	}

	/** A light that lights the tunnel behind over the dark limit: where, what, the most it gives a cell there, and why it stays (null: it goes). */
	record Light(BlockPos pos, BlockState state, int gives, @Nullable Stays stays) {
		boolean goes() {
			return stays == null;
		}
	}

	/**
	 * Rung 3's plan (D-072, FIX-GLIMPSE): the lights that light the tunnel from {@code ladderDarkFrom} to
	 * {@code ladderDarkTo} back (the spot's cells always included), which go and which stay; every candidate cell from
	 * {@code min} to {@code max} back as checked, nearest first; and the spot: the darkest one he could stand in once
	 * the lights are gone, the nearest of those (cells back, or -1).
	 */
	record Plan(int spot, List<Light> lights, List<Candidate> checked, int min, int max) {
		List<BlockPos> taken() {
			return lights.stream().filter(Light::goes).map(Light::pos).toList();
		}

		List<Light> staying() {
			return lights.stream().filter(light -> !light.goes()).toList();
		}

		/**
		 * NO_SPOT in one line: the reason most cells gave, with the nearest and farthest checked ("no spot: 14-18 back are
		 * in your view (face down the tunnel away from them)"), every reason with its count when they differ, and a hint
		 * for the main one when {@code hint}.
		 */
		String summary(ServerPlayer player, boolean hint) {
			if (checked.isEmpty()) {
				return String.format(Locale.ROOT, "no spot: the run behind is too short (his spot is %d to %d back, with 2 cells past it)", min, max);
			}
			Map<String, Group> groups = new LinkedHashMap<>();
			for (Candidate candidate : checked) {
				if (!candidate.ok()) {
					String text = display(candidate.why(), player);
					String key = text;
					int number = -1;
					Matcher m = NUMBER.matcher(text);
					if (m.find()) {
						number = Integer.parseInt(m.group(2));
						key = text.substring(0, m.start(2)) + "#" + text.substring(m.end(2));
					}
					groups.computeIfAbsent(key, Group::new).add(number);
				}
			}
			if (groups.isEmpty()) {
				return "no spot";
			}
			List<Group> ordered = new ArrayList<>(groups.values());
			ordered.sort(Comparator.comparingInt((Group g) -> -g.count));
			Group main = ordered.getFirst();
			int near = checked.getFirst().back();
			int far = checked.getLast().back();
			String range = near == far ? String.valueOf(near) : near + "-" + far;
			String text = main.render() + (hint ? hint(main.render()) : "");
			if (ordered.size() == 1) {
				boolean are = text.startsWith("in ") || text.startsWith("too ") || text.startsWith("not ");
				return "no spot: " + range + " back" + (are ? " are " : ": ") + text;
			}
			List<String> parts = new ArrayList<>();
			parts.add(main.count + " " + text);
			ordered.stream().skip(1).forEach(g -> parts.add(g.count + " " + g.render()));
			return "no spot: " + range + " back: " + String.join(", ", parts);
		}

		/** {@code /a1016 dig steps}: each candidate with its answer, and the spot. */
		String spotsLine(ServerPlayer player, DigConfig config) {
			List<String> parts = new ArrayList<>();
			for (Candidate candidate : checked) {
				String light = candidate.after() < candidate.light() ? "light " + candidate.light() + ", " + candidate.after() + " once the lights go"
						: "light " + candidate.light();
				parts.add(candidate.back() + " " + (candidate.ok() ? "ok" : display(candidate.why(), player)) + " (" + light + ")");
			}
			String past = max < config.ladderGlimpseSpotMax ? String.format(Locale.ROOT, "; %d to %d: past the run's end", Math.max(max + 1, min),
					config.ladderGlimpseSpotMax) : "";
			return String.format(Locale.ROOT, "steps_behind_glimpse spots (%d to %d back): %s%s -> %s", min, config.ladderGlimpseSpotMax,
					parts.isEmpty() ? "none" : String.join("; ", parts), past, spot < 0 ? "no spot" : "his spot " + spot + " back");
		}

		/** {@code /a1016 dig steps}: the lights that would go first, and those that stay and why. */
		String lightsLine(DigConfig config) {
			List<Light> go = lights.stream().filter(Light::goes).toList();
			List<Light> stay = staying();
			if (lights.isEmpty()) {
				return String.format(Locale.ROOT, "steps_behind_glimpse lights %d to %d back: none over light %d", config.ladderDarkFrom, config.ladderDarkTo,
						config.ladderGlimpseMaxBlockLight);
			}
			return String.format(Locale.ROOT, "steps_behind_glimpse lights %d to %d back: %d go%s; %d stay%s", config.ladderDarkFrom, config.ladderDarkTo,
					go.size(), go.isEmpty() ? "" : " (" + String.join(", ", go.stream().map(Ladder::describe).toList()) + ")", stay.size(),
					stay.isEmpty() ? "" : " (" + String.join(", ", stay.stream().map(light -> describe(light) + ": " + light.stays().text).toList()) + ")");
		}
	}

	/** One reason's candidates: how many, and the numbers they gave ("light 4" to "light 6"). */
	private static final class Group {
		final String key;
		int count;
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;

		Group(String key) {
			this.key = key;
		}

		void add(int number) {
			count++;
			if (number >= 0) {
				lowest = Math.min(lowest, number);
				highest = Math.max(highest, number);
			}
		}

		String render() {
			if (lowest > highest) {
				return key;
			}
			return key.replace("#", lowest == highest ? String.valueOf(lowest) : lowest + "-" + highest);
		}
	}

	/** What a fire came to: the result, the check it went by, and a note when the check alone does not say why (the lights refused). */
	record Attempt(FireResult result, Check check, @Nullable String note) {
	}

	private Ladder() {
	}

	/** {@link #check(Rung, ServerPlayer, Env, boolean, boolean)}: a forced check skips the look back too, an unforced one keeps it. */
	static Check check(Rung rung, ServerPlayer player, Env env, boolean forced) {
		return check(rung, player, env, forced, !forced);
	}

	/**
	 * Whether {@code rung} fits {@code player} now. {@code forced} (a debug fire) skips the rung order, the spacing and
	 * the used runs, never the gates; {@code lookBack} keeps the look back (the real card's rule, and the debug arm's,
	 * which waits for it). Cheap: the run is the detector's (cached for a sampled player), and rung 3 asks the figure
	 * for at most a few cells (and looks for the lights only while the tunnel behind is lit).
	 */
	static Check check(Rung rung, ServerPlayer player, Env env, boolean forced, boolean lookBack) {
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
		}
		if (lookBack && lookingBack(player, run.back(), config.ladderLookBackDegrees)) {
			return new Check(Why.LOOKING_BACK, run, -1);
		}
		if (rung == Rung.GLIMPSE) {
			Plan plan = plan(player, run, env, false, true);
			return plan.spot() < 0 ? new Check(Why.NO_SPOT, run, -1, plan) : new Check(null, run, plan.spot(), plan);
		}
		return new Check(null, run, -1);
	}

	/** {@link #attempt}'s result alone. */
	static FireResult fire(Rung rung, FireContext ctx, Env env) {
		return attempt(rung, ctx, env).result();
	}

	/**
	 * Fires {@code rung} for the context's player: checks again ({@link #check}), for rung 3 takes the lights first (one
	 * trace edit, all or none; if the trace service refuses them, the spot is looked for again without them), plans the
	 * steps and starts them ({@link LadderSteps}), and remembers the rung. {@code SKIPPED} when a gate fails,
	 * {@code NO_SPOT} when rung 3 has no spot he could stand in (the director keeps it).
	 */
	static Attempt attempt(Rung rung, FireContext ctx, Env env) {
		ServerPlayer player = ctx.player();
		boolean fake = ctx.fake() && rung != Rung.GLIMPSE;
		Check check = check(rung, player, env, ctx.forced());
		if (check.why() == Why.NO_SPOT) {
			return new Attempt(FireResult.NO_SPOT, check, null);
		}
		if (!check.fits() || check.run() == null) {
			return new Attempt(FireResult.SKIPPED, check, null);
		}
		TunnelRun run = check.run();
		RandomSource random = ctx.random();
		long start = ctx.level().getServer().getTickCount();
		int spot = check.spot();
		int darkened = 0;
		List<BlockPos> lights = check.lights();
		if (rung == Rung.GLIMPSE && !lights.isEmpty()) {
			TraceBatch batch = env.traces().batch(player.level(), CAUSE_GLIMPSE_TORCHES);
			lights.forEach(batch::remove);
			if (batch.commit()) {
				darkened = lights.size();
				A1016_02.LOGGER.debug("[a1016] dig: {} light(s) behind {} went for the glimpse: {}", darkened, player.getName().getString(),
						lights.stream().map(BlockPos::toShortString).toList());
			} else {
				Plan without = plan(player, run, env, false, false);
				if (without.spot() < 0) {
					return new Attempt(FireResult.NO_SPOT, new Check(Why.NO_SPOT, run, -1, without),
							"no spot: the lights behind could not go now (the trace service refused them)");
				}
				spot = without.spot();
			}
		}
		LadderSteps.Walk walk = switch (rung) {
			case ONCE -> fake ? ownStepLate(player, run, env, start) : once(player, run, env, random, start);
			case COMING -> fake ? fadingAway(player, run, env, start) : coming(player, run, env, random, start);
			case GLIMPSE -> glimpse(player, run, env, random, start, spot);
		};
		walk.darkened = darkened;
		DigTicker.INSTANCE.ladder().start(walk);
		remember(rung, player, run, env, fake, ctx.forced());
		return new Attempt(FireResult.FIRED, check, null);
	}

	/**
	 * The chat line a forced fire that did not fire answers ({@code /a1016 fire}): why, in the player's words; for rung
	 * 3's NO_SPOT the candidates' summary, and, while the player looks back down the tunnel (the real card's look-back
	 * rule, which a forced fire skips), {@link #FACE_AWAY}.
	 */
	static String forcedAnswer(Rung rung, Attempt attempt, ServerPlayer player, Env env) {
		Check check = attempt.check();
		boolean back = rung == Rung.GLIMPSE && check.why() == Why.NO_SPOT && check.run() != null
				&& lookingBack(player, check.run().back(), env.config().ladderLookBackDegrees);
		String why;
		if (attempt.note() != null) {
			why = attempt.note();
		} else if (check.why() == Why.NO_SPOT && check.plan() != null) {
			why = check.plan().summary(player, !back);
		} else {
			why = reason(check, player);
		}
		return "[a1016] dig: " + rung.id + ": " + why + (back ? "; " + FACE_AWAY : "");
	}

	/** Why a check does not fit, in the player's words (rung 3's NO_SPOT as its summary), or that it fits. */
	static String reason(Check check, ServerPlayer player) {
		if (check.fits()) {
			List<BlockPos> lights = check.lights();
			return check.spot() >= 0 ? "it fits now (his spot " + check.spot() + " back" + (lights.isEmpty() ? "" : ", " + lights.size() + " light(s) go first")
					+ ")" : "it fits now";
		}
		Why why = check.why();
		if (why == Why.NO_SPOT && check.plan() != null) {
			return check.plan().summary(player, true);
		}
		if (why == Why.LOOKING_BACK) {
			return why.text + " (face away from it)";
		}
		if (why == Why.NO_RUN) {
			return why.text + " (heading " + DigTicker.INSTANCE.ladder().heading(player).getSerializedName() + ")";
		}
		return why.text;
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
	 * step at volume 1 or less ({@link #FAKE_FARTHEST}, so even a player off their cell's centre hears the last one),
	 * and none past the run's end.
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
	 * heard: at most a step and a half apart, closer when the spot is far.
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

	// --- rung 3: the dark and the spot ---

	/**
	 * Rung 3's plan. With {@code darken}, the lights that light the tunnel behind go first ({@link #lights}); each cell
	 * of the run {@code ladderGlimpseSpotMin} to {@code ladderGlimpseSpotMax} back (with at least two cells of run past
	 * it for his steps) is a candidate: it must be dark ({@link #dark}: at most {@code ladderGlimpseMaxBlockLight} at his
	 * feet and eyes) once those lights are gone (its light now, or what the sky and the lights that stay give it, the
	 * smaller), and the figure must take it ({@link Figure#glimpseRefusal}, or {@link Figure#glimpseRefusalOnceDark} for
	 * a cell only the lights' going makes dark). The spot is the darkest such cell, the nearest of those. {@code all}
	 * checks every candidate (debug); otherwise it stops at the first cell that will be pitch dark.
	 */
	static Plan plan(ServerPlayer player, TunnelRun run, Env env, boolean all, boolean darken) {
		DigConfig config = env.config();
		ServerLevel level = player.level();
		int limit = config.ladderGlimpseMaxBlockLight;
		int min = Math.max(1, config.ladderGlimpseSpotMin);
		int max = Math.min(config.ladderGlimpseSpotMax, run.length() - 2);
		List<Light> lights = darken ? lights(player, run, env) : List.of();
		List<Light> staying = lights.stream().filter(light -> !light.goes()).toList();
		boolean anyGo = lights.stream().anyMatch(Light::goes);
		List<Candidate> checked = new ArrayList<>();
		int best = -1;
		int bestAfter = Integer.MAX_VALUE;
		for (int back = min; back <= max; back++) {
			BlockPos cell = run.cell(back);
			int now = light(level, cell);
			int after = anyGo ? Math.min(now, lightOnceGone(level, cell, staying)) : now;
			String why;
			if (after > limit) {
				why = tooLit(level, cell, now, staying, limit, config.ladderGlimpseMaxTorches);
			} else {
				Vec3 feet = Vec3.atBottomCenterOf(cell);
				why = (now > limit ? env.figure().glimpseRefusalOnceDark(player, feet) : env.figure().glimpseRefusal(player, feet)).orElse(null);
			}
			checked.add(new Candidate(back, now, after, why));
			if (why == null && after < bestAfter) {
				best = back;
				bestAfter = after;
				if (!all && after == 0) {
					break;
				}
			}
		}
		return new Plan(best, lights, checked, min, max);
	}

	/**
	 * The lights that light the tunnel behind the player over the dark limit: every block within reach whose light,
	 * less the fewest steps (Manhattan, vanilla's light spreading) to a cell of the run from {@code ladderDarkFrom} to
	 * {@code ladderDarkTo} back (his spot's cells always included; the cell or the one above, his eyes), is over
	 * {@code ladderGlimpseMaxBlockLight}. Nearest the spot's cells first. One goes if a player placed it, it is a torch
	 * or lantern ({@link Blind#isBlindLight}: soul and copper ones too, never a redstone torch), not in a protected area
	 * and out of every player's view (the trace service's own rule: never within 3 blocks of anyone), at most
	 * {@code ladderGlimpseMaxTorches} of them; the others stay, with why. Nothing is looked for while every cell there is
	 * dark. Never loads a chunk.
	 */
	static List<Light> lights(ServerPlayer player, TunnelRun run, Env env) {
		DigConfig config = env.config();
		ServerLevel level = player.level();
		int limit = config.ladderGlimpseMaxBlockLight;
		int reach = MAX_EMISSION - 1 - limit;
		int from = Math.max(1, Math.min(config.ladderDarkFrom, config.ladderGlimpseSpotMin));
		int to = Math.min(run.length(), Math.max(config.ladderDarkTo, config.ladderGlimpseSpotMax));
		if (reach < 0 || to < from) {
			return List.of();
		}
		List<BlockPos> stretch = new ArrayList<>();
		List<BlockPos> spots = new ArrayList<>();
		boolean lit = false;
		for (int back = from; back <= to; back++) {
			BlockPos cell = run.cell(back);
			stretch.add(cell);
			if (back >= config.ladderGlimpseSpotMin && back <= config.ladderGlimpseSpotMax) {
				spots.add(cell);
			}
			lit |= light(level, cell) > limit;
		}
		if (!lit) {
			return List.of();
		}
		record Found(BlockPos pos, BlockState state, int gives, int toSpot) {
		}
		List<Found> found = new ArrayList<>();
		BlockPos center = run.cell((from + to) / 2);
		int radius = (to - from) / 2 + 2 + reach;
		Blind.scan(level, center, radius, state -> state.getLightEmission() > limit, (pos, state) -> {
			int gives = state.getLightEmission() - nearest(pos, stretch);
			if (gives > limit) {
				found.add(new Found(pos.immutable(), state, gives, spots.isEmpty() ? 0 : nearest(pos, spots)));
			}
			return false;
		});
		found.sort(Comparator.comparingInt(Found::toSpot).thenComparingInt(f -> -f.gives()));
		int cap = Math.max(0, config.ladderGlimpseMaxTorches);
		int taken = 0;
		List<Light> lights = new ArrayList<>(found.size());
		for (Found f : found) {
			Stays stays = stays(level, f.pos(), f.state(), env);
			if (stays == null) {
				if (taken < cap) {
					taken++;
				} else {
					stays = Stays.TOO_MANY;
				}
			}
			lights.add(new Light(f.pos(), f.state(), f.gives(), stays));
		}
		return lights;
	}

	/** Why this light stays (null: it may go). The view last: it is the dearest to ask. */
	private static @Nullable Stays stays(ServerLevel level, BlockPos pos, BlockState state, Env env) {
		if (!env.watch().wasPlacedByPlayer(level, pos)) {
			return Stays.NOBODYS;
		}
		if (!Blind.isBlindLight(state)) {
			return Stays.NEVER_GOES;
		}
		if (Services.protectedAreas().isProtected(level.dimension(), pos)) {
			return Stays.PROTECTED;
		}
		if (!env.view().outOfView(level, List.of(pos))) {
			return Stays.IN_VIEW;
		}
		return null;
	}

	/**
	 * The most light {@code cell} (and the cell above, his eyes) can have once the lights that go are gone: the sky's
	 * part ({@link #skyPart}), or what a light that stays gives it, the larger.
	 */
	static int lightOnceGone(ServerLevel level, BlockPos cell, List<Light> staying) {
		int light = Math.max(skyPart(level, cell), skyPart(level, cell.above()));
		for (Light stay : staying) {
			light = Math.max(light, stay.state().getLightEmission() - Math.min(manhattan(stay.pos(), cell), manhattan(stay.pos(), cell.above())));
		}
		return Math.max(0, light);
	}

	/** Why a candidate stays lit: the sky (never fought), or the light that stays and gives it the most. */
	private static String tooLit(ServerLevel level, BlockPos cell, int now, List<Light> staying, int limit, int cap) {
		if (Math.max(skyPart(level, cell), skyPart(level, cell.above())) > limit) {
			return "too lit by the sky (light " + now + ")";
		}
		String lit = "too lit (light " + now + ")";
		Light worst = null;
		int most = limit;
		for (Light stay : staying) {
			int gives = stay.state().getLightEmission() - Math.min(manhattan(stay.pos(), cell), manhattan(stay.pos(), cell.above()));
			if (gives > most) {
				most = gives;
				worst = stay;
			}
		}
		if (worst == null || worst.stays() == null) {
			return lit;
		}
		String name = worst.state().getBlock().getName().getString();
		return lit + ": " + switch (worst.stays()) {
			case NOBODYS -> "a light no player placed (" + name + ")";
			case NEVER_GOES -> "a light that never goes (" + name + ")";
			case PROTECTED -> "a light in a protected area";
			case TOO_MANY -> "more than " + cap + " lights to take";
			case IN_VIEW -> "a light in view";
		};
	}

	/** The raw brightness a player would see at {@code cell} and the cell above it (his eyes), the brighter. */
	static int light(ServerLevel level, BlockPos cell) {
		return Math.max(level.getMaxLocalRawBrightness(cell), level.getMaxLocalRawBrightness(cell.above()));
	}

	/** The sky's part of the raw brightness at {@code pos}: its sky light less the level's sky darkening, at least 0. */
	static int skyPart(ServerLevel level, BlockPos pos) {
		return Math.max(0, level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken());
	}

	private static int manhattan(BlockPos a, BlockPos b) {
		return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
	}

	/** The fewest steps (Manhattan) from {@code pos} to one of {@code cells} or the cell above it. */
	private static int nearest(BlockPos pos, List<BlockPos> cells) {
		int best = Integer.MAX_VALUE;
		for (BlockPos cell : cells) {
			best = Math.min(best, Math.min(manhattan(pos, cell), manhattan(pos, cell.above())));
		}
		return best;
	}

	private static String describe(Light light) {
		return light.state().getBlock().getName().getString().toLowerCase(Locale.ROOT) + " at " + light.pos().toShortString();
	}

	/** A reason as the player reads it: the figure's "in someone's view: NAME" is "in your view" for themselves, else "in NAME's view". */
	static String display(@Nullable String why, ServerPlayer player) {
		if (why == null) {
			return "ok";
		}
		String prefix = "in someone's view: ";
		int at = why.indexOf(prefix);
		if (at < 0) {
			return why;
		}
		String name = why.substring(at + prefix.length());
		return why.substring(0, at) + "in " + (name.equals(player.getName().getString()) ? "your" : name + "'s") + " view";
	}

	/** The hint after a NO_SPOT summary's main reason: how the player can give him the spot. */
	private static String hint(String main) {
		if (main.startsWith("in your view") || main.endsWith("a light in view")) {
			return " (face down the tunnel away from them)";
		}
		if (main.startsWith("too lit by the sky")) {
			return " (daylight down a shaft or in from a mouth: he never stands in it)";
		}
		return "";
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
		return alone(player, env.watch(), env.sounds());
	}

	/** {@link #alone(ServerPlayer, Env)} with these players and listeners: the one alone rule for every PERSONAL dig card (D-077 too). */
	static boolean alone(ServerPlayer player, PlayerWatch watch, Sounds sounds) {
		if (watch.sharingWith(player).size() > 1) {
			return false;
		}
		ServerLevel level = player.level();
		double radius = ModConfig.pacing().sharedMomentRadius;
		for (ServerPlayer other : sounds.listeners(level)) {
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
	 * going to {@code ladderVolumeNear} at {@code ladderComingStopMin} (1.5 to 1.0: vanilla plays a sound at most at full
	 * loudness, a volume over 1 only reaches farther, {@code 16 * max(volume, 1)} blocks, so the steps grow louder at the
	 * ear as they near), never under 0.
	 */
	static float volumeAt(double distance, DigConfig config) {
		double far = config.ladderComingFrom;
		double near = config.ladderComingStopMin;
		double t = far > near ? Mth.clamp((far - distance) / (far - near), 0.0, 1.0) : 1.0;
		return (float) Math.max(0.0, Mth.lerp(t, config.ladderVolumeFar, config.ladderVolumeNear));
	}

	/** A rung 1 or 2 step's volume: {@link #volumeAt} times {@code factor}, held under what a friend outside the shared moment radius could hear ({@link #aloneVolume}). */
	static float stepVolume(double distance, float factor, DigConfig config) {
		return (float) Mth.clamp(volumeAt(distance, config) * factor, 0.0, aloneVolume(distance));
	}

	/**
	 * The loudest a step {@code distance} blocks from the player may play so that nobody outside the shared moment
	 * radius hears it: a friend there is at least that radius less {@code distance} from it, and vanilla's range is
	 * {@code 16 * max(volume, 1)}.
	 */
	static double aloneVolume(double distance) {
		return Math.max(0.0, (ModConfig.pacing().sharedMomentRadius - distance) / 16.0 * 0.99);
	}

	/**
	 * Rung 3's steps' volume {@code distance} blocks from the player: {@code ladderGlimpseVolumeMax} (1.8: vanilla's range
	 * {@code max(volume, 1) * 16}, about 29 blocks, so steps 14 to 20 back are heard clearly, louder as they come by the
	 * client's own fade), and always under what a friend outside the shared moment radius could hear.
	 */
	static float reachingVolume(double distance, DigConfig config) {
		return (float) Math.max(0.0, Math.min(config.ladderGlimpseVolumeMax, aloneVolume(distance)));
	}

	/**
	 * How far back rung 3's steps are heard: within {@code max(volume, 1) * 16} blocks of the step, at
	 * {@code ladderGlimpseVolumeMax} where the shared moment radius allows it ({@link #reachingVolume}); at least
	 * vanilla's 16.
	 */
	static double reach(DigConfig config) {
		double alone = ModConfig.pacing().sharedMomentRadius * 0.99 / 1.99;
		return Math.max(16.0, Math.min(Math.max(1.0F, config.ladderGlimpseVolumeMax) * 16.0, alone));
	}

	/** The yaw (degrees, the entity's) of someone at {@code from} facing {@code to}. */
	static float yawToward(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0F;
	}

	private static int between(RandomSource random, int min, int max) {
		return min + random.nextInt(Math.max(1, max - min + 1));
	}

	// --- debug ---

	/**
	 * {@code /a1016 dig steps}: the player's rung, last event, used runs, what the detector sees now, each rung's answer,
	 * rung 3's lights and each of its candidates with its answer, the steps in progress and the arm.
	 */
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
					: "no (" + (check.why() == Why.NO_SPOT ? reason(check, player) : check.why().text) + ")"));
		}
		if (run.isPresent()) {
			Plan plan = plan(player, run.get(), env, true, true);
			lines.add(plan.lightsLine(config));
			lines.add(plan.spotsLine(player, config));
		} else {
			lines.add("steps_behind_glimpse lights and spots: no run behind");
		}
		lines.add("steps now: " + DigTicker.INSTANCE.ladder().walk(uuid).map(LadderSteps.Walk::describe).orElse("none"));
		lines.add("armed: " + DigTicker.INSTANCE.ladder().armed(uuid).map(LadderSteps.Arm::describe).orElse("nothing"));
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
