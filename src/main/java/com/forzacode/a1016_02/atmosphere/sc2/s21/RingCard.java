package com.forzacode.a1016_02.atmosphere.sc2.s21;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.sc.ScGates;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * {@code sc_21} (docs/SELF_CREATED_2.md 21; MINOR, Traces, MOB, WATCHER + VISITOR, fake): at dusk, at night or in a
 * counting fog, the target outdoors, a herd of one kind 24 to 64 off on open ground: the herd walks round a circle
 * ({@link Rings}). A shared moment: the animals are bound to everyone sharing it, and anyone coming near breaks it. The
 * fake: the herd stands still, every one facing one far point, for 20 s. At most {@code maxPerWorld}, {@code minDaysApart}
 * in-game days apart, one a night; a forced fire skips the time of day, the caps and the herd's size (4 do).
 */
public final class RingCard implements EventCard {
	public static final String ID = "sc_21";
	/** The fake lasts this long (ticks). */
	public static final int FAKE_TICKS = 400;
	/** A forced fire needs only this many animals. */
	public static final int FORCED_MIN = 4;
	/** The herd is looked for among at most this many animals of each kind, the nearest (R12-29). */
	static final int PER_KIND = 64;

	/** Who takes part and shares a moment. A test swaps in its own. */
	public Supplier<PlayerWatch> watch = Services::watch;
	/** The time of day of a level. A test swaps in its own. */
	public ToLongFunction<ServerLevel> timeOfDay = Herds::timeOfDay;
	/** A counting fog is on. A test swaps in its own. */
	public Predicate<MinecraftServer> fog = Herds::countingFog;
	/** The target is outdoors. A test swaps in its own. */
	public Predicate<ServerPlayer> outdoors = Herds::outdoors;
	/** Ticks since the target's combat. A test swaps in its own. */
	public ToLongFunction<ServerPlayer> sinceCombat = player -> Services.watch().ticksSinceCombat(player);
	/** Where the scenes run. A test may give its own. */
	public Rings rings = Rings.LIVE;
	/** Whether this card keeps the world's caps (a test's card may not, to keep the world's record clean). */
	public boolean keepsCaps = true;

	/** The last answer per player, kept two seconds (the director looks often). */
	private final FitMemo memo = new FitMemo();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Tier tier() {
		return Tier.MINOR;
	}

	@Override
	public Stage earliestStage() {
		return Stage.TRACES;
	}

	@Override
	public Set<Habit> habits() {
		return Set.of(Habit.WATCHER, Habit.VISITOR);
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.MOB);
	}

	@Override
	public boolean hasFake() {
		return true;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return memo.fits(player, () -> refusal(player, false).isEmpty());
	}

	/** What the card would do now for {@code player}: the herd, its site and the animals. */
	public record Plan(Vec3 middle, Rings.Site site, List<Mob> animals) {
	}

	/** Why it does not fit now (in plain words), or empty. {@code forced}: the time, the caps and the herd's size relaxed. */
	public Optional<String> refusal(ServerPlayer player, boolean forced) {
		Optional<String> gate = gates(player, forced);
		if (gate.isPresent()) {
			return gate;
		}
		return plan(player, forced).isPresent() ? Optional.empty() : Optional.of("no herd of " + (forced ? FORCED_MIN : Sc21Config.get().minMobs)
				+ " of one kind on open ground " + Sc21Config.MIN_DISTANCE + " to " + Sc21Config.get().maxDistance + " off");
	}

	/** Every rule but the herd: the story, the overworld, combat, outdoors, the time, the caps, one at a time, the steer cap. */
	Optional<String> gates(ServerPlayer player, boolean forced) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		Optional<String> story = ScGates.why(server);
		if (story.isPresent()) {
			return story;
		}
		if (!ScGates.overworld(level)) {
			return Optional.of("not in the overworld");
		}
		if (!ScGates.noCombat(sinceCombat.applyAsLong(player))) {
			return Optional.of("in combat lately");
		}
		if (!outdoors.test(player)) {
			return Optional.of("not outdoors");
		}
		Sc21Config cfg = Sc21Config.get();
		if (!forced) {
			long t = timeOfDay.applyAsLong(level);
			if (!Herds.duskOrNight(t) && !fog.test(server)) {
				return Optional.of("neither dusk, night nor fog (" + t + ")");
			}
			Optional<String> caps = capsRefusal(Sc2xData.get(server).caps("21"), GameClock.day(server), nightOf(level.getDefaultClockTime()), cfg);
			if (caps.isPresent()) {
				return caps;
			}
		}
		if (rings.running()) {
			return Optional.of("one is running already");
		}
		if (MobTamperImpl.MAX_STEERED - MobTamperImpl.INSTANCE.steeredCount() < cfg.minMobs) {
			return Optional.of("too many mobs are steered now");
		}
		return Optional.empty();
	}

	/** Pure: the world's caps (count, days apart, one a night), or empty. */
	public static Optional<String> capsRefusal(Sc2xData.Caps caps, long day, long night, Sc21Config cfg) {
		if (caps.count() >= cfg.maxPerWorld) {
			return Optional.of(caps.count() + " already (at most " + cfg.maxPerWorld + ")");
		}
		if (caps.lastDay() >= 0 && day - caps.lastDay() < cfg.minDaysApart) {
			return Optional.of("the last was on day " + caps.lastDay() + " (" + cfg.minDaysApart + " days apart)");
		}
		if (caps.lastNight() >= 0 && caps.lastNight() == night) {
			return Optional.of("one tonight already");
		}
		return Optional.empty();
	}

	/** The night a clock tick belongs to: its 24000-tick period counted from 12000 (dusk). */
	public static long nightOf(long clock) {
		return Math.floorDiv(clock - 12_000L, 24_000L);
	}

	/**
	 * The herd and its site for {@code player} now: band 24 to {@code maxDistance} off; forced, 4 animals do. From Proximity
	 * at night (R10-9), a herd within {@link #NEAR_BASE} of their base first: their own field, so the bare ring lies in their
	 * own grass; any herd otherwise, as before.
	 */
	public Optional<Plan> plan(ServerPlayer player, boolean forced) {
		Sc21Config cfg = Sc21Config.get();
		int min = forced ? FORCED_MIN : cfg.minMobs;
		ServerLevel level = player.level();
		Optional<BlockPos> base = homeHerdBase(player);
		if (base.isPresent()) {
			Optional<Plan> home = plan(level, player.position(), min, Sc21Config.MIN_DISTANCE, cfg.maxDistance, Vec3.atBottomCenterOf(base.get()), NEAR_BASE);
			if (home.isPresent()) {
				return home;
			}
		}
		return plan(level, player.position(), min, Sc21Config.MIN_DISTANCE, cfg.maxDistance);
	}

	/** R10-9: a herd this near the target's base is theirs (blocks, horizontal). */
	public static final double NEAR_BASE = 48.0;

	/** Their base in this level, when a herd near it comes first (R10-9: from Proximity, at night), else empty. */
	Optional<BlockPos> homeHerdBase(ServerPlayer player) {
		ServerLevel level = player.level();
		if (!stage.apply(level.getServer()).atLeast(Stage.PROXIMITY) || !Herds.night(timeOfDay.applyAsLong(level))) {
			return Optional.empty();
		}
		return watch.get().base(player).filter(b -> b.dimension() == level.dimension()).map(GlobalPos::pos);
	}

	/** The story's stage now. A test swaps in its own. */
	public java.util.function.Function<MinecraftServer, Stage> stage = server -> HerobrineState.get(server).stage();

	/** The herd of at least {@code min} animals of one kind round a point {@code minDistance} to {@code maxDistance} from {@code from}, and its site. */
	public static Optional<Plan> plan(ServerLevel level, Vec3 from, int min, double minDistance, double maxDistance) {
		return plan(level, from, min, minDistance, maxDistance, null, 0.0);
	}

	/**
	 * {@link #plan(ServerLevel, Vec3, int, double, double)} among the animals within {@code near} of {@code home} only, the
	 * herd's middle too (R10-9: their own field); {@code home} null: any.
	 */
	public static Optional<Plan> plan(ServerLevel level, Vec3 from, int min, double minDistance, double maxDistance, @Nullable Vec3 home, double near) {
		Sc21Config cfg = Sc21Config.get();
		double reach = maxDistance + Sc21Config.CLUSTER_RADIUS;
		List<Mob> mobs = new ArrayList<>(level.getEntitiesOfClass(Mob.class, new AABB(from, from).inflate(reach, 24.0, reach),
				mob -> Herds.RING_KINDS.contains(mob.getType()) && Herds.free(mob) && (home == null || Herds.flatSqr(mob.position(), home) <= near * near)));
		// The nearest PER_KIND of each kind only (R12-29: the herd search is square in the animals it looks at, and a farm
		// of hundreds round a player would cost milliseconds at every look).
		mobs.sort(java.util.Comparator.comparingDouble(mob -> mob.position().distanceToSqr(from)));
		java.util.Map<Object, Integer> perKind = new java.util.HashMap<>();
		List<Cluster.Candidate<Mob>> candidates = new ArrayList<>();
		for (Mob mob : mobs) {
			if (perKind.merge(mob.getType(), 1, Integer::sum) <= PER_KIND) {
				candidates.add(new Cluster.Candidate<>(mob, mob.getType(), mob.position()));
			}
		}
		Optional<Cluster<Mob>> herd = Cluster.find(candidates, from, min, cfg.maxMobs, minDistance, maxDistance, Sc21Config.CLUSTER_RADIUS);
		if (herd.isEmpty() || home != null && Herds.flatSqr(herd.get().middle(), home) > near * near) {
			return Optional.empty();
		}
		List<Mob> animals = herd.get().members();
		Optional<Rings.Site> site = Rings.site(level, herd.get().middle(), animals.size(), cfg.spacing);
		return site.map(s -> new Plan(herd.get().middle(), s, animals));
	}

	@Override
	public FireResult fire(FireContext ctx) {
		memo.clear();
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		MinecraftServer server = level.getServer();
		if (gates(player, ctx.forced()).isPresent()) {
			return FireResult.SKIPPED;
		}
		Optional<Plan> plan = plan(player, ctx.forced());
		if (plan.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		List<UUID> sharers = watch.get().sharingWith(player).stream().map(ServerPlayer::getUUID).toList();
		if (ctx.fake()) {
			return fake(level, plan.get(), sharers, ctx.random()) ? FireResult.FIRED : FireResult.NO_SPOT;
		}
		Sc21Config cfg = Sc21Config.get();
		int seconds = cfg.circleSecondsMin + (cfg.circleSecondsMax > cfg.circleSecondsMin ? ctx.random().nextInt(cfg.circleSecondsMax - cfg.circleSecondsMin + 1) : 0);
		Optional<Rings.Scene> scene = rings.start(level, plan.get().site(), plan.get().animals(), sharers, seconds * 20, false);
		if (scene.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		if (!ctx.forced() && keepsCaps) {
			Sc2xData data = Sc2xData.get(server);
			data.setCaps("21", data.caps("21").plus(GameClock.day(server), nightOf(level.getDefaultClockTime())));
		}
		A1016_02.LOGGER.info("[a1016] sc_21: fired ({}, {})", scene.get().count(), seconds);
		return FireResult.FIRED;
	}

	/** The fake: every animal of the herd stands still and faces one far point (48 off the herd's middle) for {@link #FAKE_TICKS}. */
	public static boolean fake(ServerLevel level, Plan plan, List<UUID> sharers, RandomSource random) {
		double bearing = random.nextDouble() * 2.0 * Math.PI;
		Vec3 point = plan.middle().add(Math.cos(bearing) * 48.0, 1.2, Math.sin(bearing) * 48.0);
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		int held = 0;
		for (Mob mob : plan.animals()) {
			if (tamper.freeze(mob, FAKE_TICKS) && tamper.face(mob, point, FAKE_TICKS)) {
				tamper.bind(mob, sharers);
				Rings.LIVE.heldFake(mob); // the switch lets it go (R12-33)
				held++;
			}
		}
		return held > 0;
	}

	/** Lines for {@code /a1016 sc 21}: the caps, the scenes and the answer for {@code player}. */
	public List<String> describe(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Sc2xData.Caps caps = Sc2xData.get(server).caps("21");
		List<String> lines = new ArrayList<>();
		lines.add(String.format(Locale.ROOT, "world: %d of %d, last day %d, day now %d", caps.count(), Sc21Config.get().maxPerWorld, caps.lastDay(),
				GameClock.day(server)));
		lines.addAll(rings.describe());
		lines.add("for " + player.getPlainTextName() + ": " + refusal(player, false).orElse("fits now"));
		lines.add("forced: " + refusal(player, true).orElse("would fire"));
		return lines;
	}
}
