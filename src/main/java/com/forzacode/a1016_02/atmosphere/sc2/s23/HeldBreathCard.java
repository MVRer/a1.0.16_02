package com.forzacode.a1016_02.atmosphere.sc2.s23;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.atmosphere.sc.ScGates;
import com.forzacode.a1016_02.atmosphere.sc2.s21.FitMemo;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Herds;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Sc2xData;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * {@code sc_23} (docs/SELF_CREATED_2.md 23; MINOR, Proximity, MOB, WATCHER, fake): from Proximity, with at least
 * {@code minMobs} mobs within {@code radius} of the target and 3 of them in the target's view: every mob near the target
 * and near everyone sharing the moment (at most {@code maxMobs}, nearest first) stops in the same tick and turns to face
 * one point far along the bearing ({@link HeldBreath#bearing}), silent, for {@code seconds}; everyone sharing gets the
 * same silence on their client; then all are let go in one tick. It may come mid-fight (D-101, {@code duringCombat}).
 * Once per in-game day per player. The fake: only the animals turn, no silence.
 */
public final class HeldBreathCard implements EventCard {
	public static final String ID = "sc_23";
	/** The fit looks for its seen mobs among at most this many, nearest first (R12-29). */
	static final int SEES_MAX = 48;

	/** Who takes part and shares the moment. A test swaps in its own. */
	public Supplier<PlayerWatch> watch = Services::watch;
	/** Whether the player sees the mob (in view, line of sight). A test swaps in its own. */
	public BiPredicate<ServerPlayer, Mob> sees = HeldBreathCard::seesLive;
	/** The silence on a client. A test swaps in its own (mock players have no client). */
	public BiConsumer<ServerPlayer, Integer> silence = (player, ticks) -> ClientEffects.silence(player, ticks, Sc23Config.SILENCE_FADE);
	/** Ticks since the target's combat. A test swaps in its own. */
	public ToLongFunction<ServerPlayer> sinceCombat = player -> Services.watch().ticksSinceCombat(player);
	/** His places (the bearing). A test swaps in its own. */
	public Supplier<Remains> remains = Services::remains;
	/** Where the breaths are held. */
	public HeldBreath breaths = HeldBreath.LIVE;
	/** Whether this card keeps each player's day (a test's card may not). */
	public boolean keepsDays = true;

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
		return Stage.PROXIMITY;
	}

	@Override
	public Set<Habit> habits() {
		return Set.of(Habit.WATCHER);
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

	/** Why it does not fit now, or empty. {@code forced}: the day and the counts relaxed. */
	public Optional<String> refusal(ServerPlayer player, boolean forced) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		Optional<String> story = ScGates.why(server);
		if (story.isPresent()) {
			return story;
		}
		if (!ScGates.overworld(level)) {
			return Optional.of("not in the overworld");
		}
		Sc23Config cfg = Sc23Config.get();
		if (!cfg.duringCombat && !ScGates.noCombat(sinceCombat.applyAsLong(player))) {
			return Optional.of("in combat lately");
		}
		if (breaths.holding(player.getUUID())) {
			return Optional.of("one is held now");
		}
		if (!forced && Sc2xData.get(server).breathDay(player.getUUID()) == GameClock.day(server)) {
			return Optional.of("once a day (day " + GameClock.day(server) + ")");
		}
		if (forced) {
			return Optional.empty();
		}
		List<Mob> near = HeldBreath.gather(level, List.of(player.position()), cfg.radius, Integer.MAX_VALUE, false);
		if (near.size() < cfg.minMobs) {
			return Optional.of(near.size() + " near (at least " + cfg.minMobs + ")");
		}
		int seen = 0;
		int looked = 0;
		for (Mob mob : near) {
			// At most the nearest SEES_MAX are looked at (R12-29: a farm of hundreds would cost a line of sight each).
			if (++looked > SEES_MAX) {
				break;
			}
			if (sees.test(player, mob) && ++seen >= Sc23Config.MIN_SEEN) {
				return Optional.empty();
			}
		}
		return Optional.of(seen + " in view (at least " + Sc23Config.MIN_SEEN + ")");
	}

	/** The live view rule: the mob's box is in the player's view cone and in sight. */
	static boolean seesLive(ServerPlayer player, Mob mob) {
		ServerLevel level = player.level();
		TraceService.Viewer viewer = TraceService.Viewer.of(player, level.getServer().getPlayerList().getViewDistance());
		return TraceService.sees(level, viewer, mob.getBoundingBox(), ModConfig.pacing().viewNearBlocks, ModConfig.pacing().viewConeDegrees);
	}

	/**
	 * The bearing for this player now: from Telling, with them {@link #HOME_FAR} or more from their base, their base (R10-9:
	 * every mob looked toward my house; nothing is done there, the absence is the scare); else the nearest place within
	 * {@code remainsRadius}, else where he last stood, else the origin.
	 */
	public HeldBreath.Bearing bearing(ServerPlayer player) {
		ServerLevel level = player.level();
		if (stage.apply(level.getServer()).atLeast(Stage.TELLING)) {
			Optional<Vec3> home = watch.get().base(player).filter(b -> b.dimension() == level.dimension()).map(b -> Vec3.atCenterOf(b.pos()));
			if (home.isPresent() && Math.sqrt(Herds.flatSqr(home.get(), player.position())) >= HOME_FAR) {
				return new HeldBreath.Bearing(home.get(), HeldBreath.From.HOME);
			}
		}
		Optional<Remains.Place> place = remains.get().nearest(level, player.blockPosition(), EnumSet.allOf(Remains.Kind.class), Sc23Config.get().remainsRadius);
		return HeldBreath.bearing(place, Sc2xData.get(level.getServer()).stood(), player.position());
	}

	/** R10-9: from Telling, a target at least this far from their base (blocks, horizontal) is looked past toward it. */
	public static final double HOME_FAR = 200.0;
	/** R10-6: a place the bearing points at this far off or more is remembered, and the next file walks there for them. */
	public static final double POINTS_FAR = 300.0;
	/** The story's stage now. A test swaps in its own. */
	public java.util.function.Function<MinecraftServer, Stage> stage = server -> com.forzacode.a1016_02.core.HerobrineState.get(server).stage();

	@Override
	public FireResult fire(FireContext ctx) {
		memo.clear();
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		if (refusal(player, ctx.forced()).isPresent()) {
			return FireResult.SKIPPED;
		}
		Sc23Config cfg = Sc23Config.get();
		List<ServerPlayer> sharers = watch.get().sharingWith(player);
		List<Vec3> around = new ArrayList<>();
		List<UUID> uuids = new ArrayList<>();
		for (ServerPlayer p : sharers) {
			around.add(p.position());
			uuids.add(p.getUUID());
		}
		List<Mob> mobs = HeldBreath.gather(level, around, cfg.radius, cfg.maxMobs, ctx.fake());
		if (mobs.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		HeldBreath.Bearing bearing = bearing(player);
		Vec3 point = HeldBreath.facePoint(player.getEyePosition(), bearing.toward(), Sc23Config.FACE_DISTANCE);
		int ticks = Math.max(1, (int) Math.round(cfg.seconds * 20.0));
		int held = breaths.hold(level, mobs, point, ticks, uuids, ctx.fake(), player.getEyePosition());
		if (held == 0) {
			return FireResult.NO_SPOT;
		}
		if (!ctx.fake() && bearing.from() == HeldBreath.From.PLACE && Math.sqrt(Herds.flatSqr(bearing.toward(), player.position())) >= POINTS_FAR) {
			// R10-6: the world keeps pointing: the next file for each of them walks to this place.
			Sc2xData data = Sc2xData.get(level.getServer());
			for (UUID uuid : uuids) {
				data.setPointed(uuid, bearing.toward());
			}
		}
		if (!ctx.fake()) {
			for (ServerPlayer p : sharers) {
				silence.accept(p, ticks);
			}
		}
		if (!ctx.forced() && keepsDays) {
			Sc2xData data = Sc2xData.get(level.getServer());
			long day = GameClock.day(level.getServer());
			for (UUID uuid : uuids) {
				data.setBreathDay(uuid, day);
			}
		}
		A1016_02.LOGGER.info("[a1016] sc_23: fired ({})", held);
		return FireResult.FIRED;
	}
}
