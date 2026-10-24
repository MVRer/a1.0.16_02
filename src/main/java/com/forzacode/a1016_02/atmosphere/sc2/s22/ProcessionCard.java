package com.forzacode.a1016_02.atmosphere.sc2.s22;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.sc.ScGates;
import com.forzacode.a1016_02.atmosphere.sc2.s21.FitMemo;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Herds;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Sc2xData;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceLedger;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code sc_22} (docs/SELF_CREATED_2.md 22; MINOR, Traces, MOB, WATCHER + MOURNER, no fake): at dusk, at night or in a
 * counting fog, the target outdoors, one of his places (the pyramid, the hollow, the floor or the archive's mouth) within
 * {@code destinationRadius} and at least {@code minMobs} passive animals within 96: up to {@code maxMobs} of them, out of
 * view, are moved to a start 48 to 64 from the target on the side away from the place and walk in single file along the
 * line to it that passes the target {@code passMin} to {@code passMax} off ({@link Line}, {@link Processions}); later,
 * out of view, they are moved inside it. A shared moment. At most {@code maxPerWorld}, {@code minDaysApart} apart; a
 * forced fire skips the time of day and the caps.
 */
public final class ProcessionCard implements EventCard {
	public static final String ID = "sc_22";
	/** The ledger's cause for an animal taken from where it stood (R13-7): the switch and Ending D put it back. */
	public static final String CAUSE = "atmosphere:s22";
	/** The animals' view rule is asked for at most this many, the nearest (R12-29). */
	static final int VIEW_CHECKS = 64;
	/** The kinds of place a file walks to. */
	public static final Set<Remains.Kind> DESTINATIONS = EnumSet.of(Remains.Kind.PYRAMID, Remains.Kind.HOLLOW, Remains.Kind.FLOOR, Remains.Kind.ARCHIVE);
	/** An animal slower than this on its own (its movement speed attribute) is left out: it could not keep the pace. */
	static final double MIN_SPEED = 0.166;

	public Supplier<PlayerWatch> watch = Services::watch;
	public ToLongFunction<ServerLevel> timeOfDay = Herds::timeOfDay;
	public Predicate<MinecraftServer> fog = Herds::countingFog;
	public Predicate<ServerPlayer> outdoors = Herds::outdoors;
	public ToLongFunction<ServerPlayer> sinceCombat = player -> Services.watch().ticksSinceCombat(player);
	public Supplier<Remains> remains = Services::remains;
	/** Whether a box is out of everyone's view. A test swaps in its own. */
	public BiPredicate<ServerLevel, AABB> outOfView = (level, box) -> Services.traces().isOutOfView(level, box);
	/** Moves a mob to a cell out of view. A test swaps in its own (the live one asks the live view). */
	public BiPredicate<Mob, BlockPos> move = (mob, cell) -> Services.mobs().moveOutOfView(mob, cell);
	public Processions processions = Processions.LIVE;
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
		return Set.of(Habit.WATCHER, Habit.MOURNER);
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.MOB);
	}

	@Override
	public boolean hasFake() {
		return false;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return memo.fits(player, () -> refusal(player, false).isEmpty());
	}

	/** The way, the place, where they go in, and the file (the leader first) with each one's start cell. */
	public record Plan(Line line, Remains.Place place, Vec3 inside, List<Mob> file, List<BlockPos> cells) {
	}

	public Optional<String> refusal(ServerPlayer player, boolean forced) {
		Optional<String> gate = gates(player, forced);
		if (gate.isPresent()) {
			return gate;
		}
		return plan(player, (Sc22Config.get().passMin + Sc22Config.get().passMax) / 2.0).isPresent() ? Optional.empty()
				: Optional.of(why(player));
	}

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
		Sc22Config cfg = Sc22Config.get();
		if (!forced) {
			long t = timeOfDay.applyAsLong(level);
			if (!Herds.duskOrNight(t) && !fog.test(server)) {
				return Optional.of("neither dusk, night nor fog (" + t + ")");
			}
			Sc2xData.Caps caps = Sc2xData.get(server).caps("22");
			long day = GameClock.day(server);
			if (caps.count() >= cfg.maxPerWorld) {
				return Optional.of(caps.count() + " already (at most " + cfg.maxPerWorld + ")");
			}
			if (caps.lastDay() >= 0 && day - caps.lastDay() < cfg.minDaysApart) {
				return Optional.of("the last was on day " + caps.lastDay() + " (" + cfg.minDaysApart + " days apart)");
			}
		}
		if (processions.running()) {
			return Optional.of("one is walking already");
		}
		if (MobTamperImpl.MAX_STEERED - MobTamperImpl.INSTANCE.steeredCount() < cfg.minMobs) {
			return Optional.of("too many mobs are steered now");
		}
		return Optional.empty();
	}

	/** Why no plan (the first thing missing), for the debug lines. */
	String why(ServerPlayer player) {
		Optional<Remains.Place> place = destination(player);
		if (place.isEmpty()) {
			return "no place within " + Sc22Config.get().destinationRadius + " (at least " + (int) Sc22Config.DESTINATION_MIN + " off)";
		}
		List<Mob> animals = animals(player);
		if (animals.size() < Sc22Config.get().minMobs) {
			return animals.size() + " animals out of view within " + (int) Sc22Config.ANIMAL_RADIUS + " (at least " + Sc22Config.get().minMobs + ")";
		}
		return "no start out of view on open ground";
	}

	/**
	 * The place a file walks to, within {@code destinationRadius} and at least {@link Sc22Config#DESTINATION_MIN} off: the one
	 * 23 last pointed this player at if it is such a place (R10-6: the world keeps pointing the same way), else the nearest.
	 */
	Optional<Remains.Place> destination(ServerPlayer player) {
		ServerLevel level = player.level();
		double radius = Sc22Config.get().destinationRadius;
		double min = Sc22Config.DESTINATION_MIN;
		Optional<Vec3> pointed = Sc2xData.get(level.getServer()).pointed(player.getUUID());
		if (pointed.isPresent()) {
			for (Remains.Place place : remains.get().known(level)) {
				double d2 = Herds.flatSqr(Vec3.atCenterOf(place.entrance()), player.position());
				if (DESTINATIONS.contains(place.kind()) && place.dimension() == level.dimension() && Vec3.atCenterOf(place.anchor()).distanceToSqr(pointed.get()) < 4.0
						&& d2 >= min * min && d2 <= radius * radius) {
					return Optional.of(place);
				}
			}
		}
		return remains.get().nearest(level, player.blockPosition(), DESTINATIONS, (int) radius)
				.filter(p -> Herds.flatSqr(Vec3.atCenterOf(p.entrance()), player.position()) >= min * min);
	}

	/** Where they are moved once out of view: the archive's first cell inside its mouth; the place's anchor otherwise. */
	public static Vec3 inside(Remains.Place place) {
		if (place.kind() == Remains.Kind.ARCHIVE) {
			return Vec3.atBottomCenterOf(place.entrance().relative(place.entranceFacing().getOpposite(), 4));
		}
		return Vec3.atBottomCenterOf(place.anchor());
	}

	/** Passive animals within 96, free, quick enough, out of everyone's view; kinds taking turns (a cow, a sheep, a pig ...). */
	List<Mob> animals(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 at = player.position();
		double r = Sc22Config.ANIMAL_RADIUS;
		List<Mob> near = new ArrayList<>(level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(r, 32.0, r), mob -> Herds.PASSIVE.contains(mob.getType())
				&& Herds.free(mob) && mob.getAttributeValue(Attributes.MOVEMENT_SPEED) >= MIN_SPEED && Herds.flatSqr(mob.position(), at) <= r * r));
		near.sort((a, b) -> Double.compare(a.distanceToSqr(at), b.distanceToSqr(at)));
		// The view rule (a line of sight each) for the nearest VIEW_CHECKS only (R12-29: a farm of hundreds round a player).
		List<Mob> all = new ArrayList<>();
		for (int i = 0; i < near.size() && i < VIEW_CHECKS; i++) {
			if (outOfView.test(level, near.get(i).getBoundingBox())) {
				all.add(near.get(i));
			}
		}
		return interleave(all, Sc22Config.get().maxMobs);
	}

	/**
	 * At most {@code max}, one of each kind in turn (nearest first within a kind), then put in file order big and small in
	 * turn (R11-13: a cow, a chicken, a horse, a sheep...), so each one stands out from the next and it reads as a file.
	 */
	static List<Mob> interleave(List<Mob> mobs, int max) {
		Map<Object, List<Mob>> byKind = new LinkedHashMap<>();
		for (Mob mob : mobs) {
			byKind.computeIfAbsent(mob.getType(), k -> new ArrayList<>()).add(mob);
		}
		List<Mob> out = new ArrayList<>();
		for (int round = 0; out.size() < max; round++) {
			boolean any = false;
			for (List<Mob> kind : byKind.values()) {
				if (round < kind.size() && out.size() < max) {
					out.add(kind.get(round));
					any = true;
				}
			}
			if (!any) {
				break;
			}
		}
		return bigAndSmall(out);
	}

	/** Pure: the same animals, the biggest and the smallest in turn (by the volume of their box), the biggest first. */
	public static List<Mob> bigAndSmall(List<Mob> mobs) {
		List<Mob> bySize = new ArrayList<>(mobs);
		bySize.sort(java.util.Comparator.comparingDouble((Mob m) -> -m.getBbWidth() * m.getBbWidth() * m.getBbHeight()));
		List<Mob> out = new ArrayList<>(mobs.size());
		int lo = bySize.size() - 1;
		int hi = 0;
		while (hi <= lo) {
			out.add(bySize.get(hi++));
			if (hi <= lo) {
				out.add(bySize.get(lo--));
			}
		}
		return out;
	}

	/** A plan passing {@code pass} off: the place, the animals, and a line (either side) whose start cells are out of view on open ground. */
	public Optional<Plan> plan(ServerPlayer player, double pass) {
		Optional<Remains.Place> place = destination(player);
		if (place.isEmpty()) {
			return Optional.empty();
		}
		List<Mob> animals = animals(player);
		Sc22Config cfg = Sc22Config.get();
		// Never a file of none, whatever the config says (R12-31: a minimum of 0 left the start cells empty).
		if (animals.isEmpty() || animals.size() < cfg.minMobs) {
			return Optional.empty();
		}
		ServerLevel level = player.level();
		Vec3 dest = Vec3.atBottomCenterOf(place.get().entrance());
		double start = (Sc22Config.START_MIN + Sc22Config.START_MAX) / 2.0;
		for (int side : sides(player.position(), player.getViewVector(1.0F), dest, pass, start)) {
			Optional<Line> line = Line.of(player.position(), dest, pass, start, side);
			if (line.isEmpty()) {
				continue;
			}
			List<BlockPos> cells = cells(level, line.get(), animals);
			if (cells.size() == animals.size() && walkable(level, line.get())) {
				Vec3 s = Vec3.atBottomCenterOf(cells.getFirst());
				Line grounded = new Line(s, line.get().end(), line.get().dir());
				return Optional.of(new Plan(grounded, place.get(), inside(place.get()), animals, cells));
			}
		}
		return Optional.empty();
	}

	/**
	 * Pure (R11-13): the two tangents, the one that passes in front of the player first (its nearest point to them ahead of
	 * where they look), so the file crosses their view at a slant and goes away from them along it, and reads as a line.
	 */
	public static int[] sides(Vec3 target, Vec3 look, Vec3 dest, double pass, double start) {
		double score1 = ahead(target, look, Line.of(target, dest, pass, start, 1));
		double scoreMinus = ahead(target, look, Line.of(target, dest, pass, start, -1));
		return score1 >= scoreMinus ? new int[] {1, -1} : new int[] {-1, 1};
	}

	private static double ahead(Vec3 target, Vec3 look, Optional<Line> line) {
		if (line.isEmpty()) {
			return Double.NEGATIVE_INFINITY;
		}
		Vec3 nearest = line.get().at(line.get().along(target));
		double dx = nearest.x - target.x;
		double dz = nearest.z - target.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		double lx = look.x;
		double lz = look.z;
		double ll = Math.sqrt(lx * lx + lz * lz);
		return len < 1.0E-6 || ll < 1.0E-6 ? 0.0 : (dx * lx + dz * lz) / (len * ll);
	}

	/** The file's start cells on the ground, the leader's first; empty-short if any is not loaded, not standable or in view. */
	List<BlockPos> cells(ServerLevel level, Line line, List<Mob> animals) {
		List<BlockPos> cells = new ArrayList<>();
		List<Vec3> spots = line.file(animals.size(), Sc22Config.SPACING);
		for (int i = 0; i < spots.size(); i++) {
			Vec3 spot = spots.get(i);
			int x = (int) Math.floor(spot.x);
			int z = (int) Math.floor(spot.z);
			BlockPos column = new BlockPos(x, (int) spot.y, z);
			if (!level.isLoaded(column)) {
				return cells;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			OptionalInt feet = Herds.feet(level, x, z, top, 1, 2);
			if (feet.isEmpty()) {
				return cells;
			}
			BlockPos cell = new BlockPos(x, feet.getAsInt(), z);
			Mob mob = animals.get(i);
			AABB box = mob.getDimensions(mob.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(cell));
			if (!level.noCollision(mob, box) || !outOfView.test(level, box)) {
				return cells;
			}
			cells.add(cell);
		}
		return cells;
	}

	/** The first stretch of the way (every 4 blocks over 64, or to the place) has ground: no water, no wall over 2. */
	static boolean walkable(ServerLevel level, Line line) {
		double end = Math.min(64.0 + Sc22Config.START_MAX, line.length());
		int last = Integer.MIN_VALUE;
		for (double s = 0; s <= end; s += 4.0) {
			Vec3 at = line.at(s);
			int x = (int) Math.floor(at.x);
			int z = (int) Math.floor(at.z);
			if (!level.isLoaded(new BlockPos(x, (int) at.y, z))) {
				break;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			OptionalInt feet = Herds.feet(level, x, z, top, 1, 2);
			if (feet.isEmpty() || last != Integer.MIN_VALUE && Math.abs(feet.getAsInt() - last) > 2) {
				return false;
			}
			last = feet.getAsInt();
		}
		return true;
	}

	@Override
	public FireResult fire(FireContext ctx) {
		memo.clear();
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		if (gates(player, ctx.forced()).isPresent()) {
			return FireResult.SKIPPED;
		}
		Sc22Config cfg = Sc22Config.get();
		double pass = cfg.passMin + ctx.random().nextDouble() * Math.max(0.0, cfg.passMax - cfg.passMin);
		Optional<Plan> plan = plan(player, pass);
		if (plan.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		List<Mob> moved = new ArrayList<>();
		for (int i = 0; i < plan.get().file().size(); i++) {
			Mob mob = plan.get().file().get(i);
			BlockPos from = mob.blockPosition().immutable();
			BlockPos cell = plan.get().cells().get(i);
			if (move.test(mob, cell)) {
				moved.add(mob);
				// Taken from where it stood: ledgered, so the switch and Ending D put it back there (R13-7).
				TraceLedger.recordMobMove(level, mob, from, cell, CAUSE);
			}
		}
		if (moved.size() < Math.max(1, cfg.minMobs)) {
			return FireResult.NO_SPOT;
		}
		List<UUID> sharers = watch.get().sharingWith(player).stream().map(ServerPlayer::getUUID).toList();
		Optional<Processions.Procession> p = processions.start(level, plan.get().line(), moved, plan.get().inside(), sharers, cfg.walkSecondsMax * 20, false);
		if (p.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		if (!ctx.forced() && keepsCaps) {
			Sc2xData data = Sc2xData.get(level.getServer());
			data.setCaps("22", data.caps("22").plus(GameClock.day(level.getServer()), -1L));
		}
		Sc2xData.get(level.getServer()).clearPointed(player.getUUID()); // the file walked where 23 pointed (or elsewhere: the next 23 points again)
		A1016_02.LOGGER.info("[a1016] sc_22: fired ({}, {})", moved.size(), String.format(Locale.ROOT, "%.0f", pass));
		return FireResult.FIRED;
	}
}
