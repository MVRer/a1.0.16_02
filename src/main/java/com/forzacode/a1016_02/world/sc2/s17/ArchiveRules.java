package com.forzacode.a1016_02.world.sc2.s17;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.Draw;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The archive's rules and answers at runtime (docs/SELF_CREATED_2.md, feature 17): once a second, for every archive with
 * someone inside, 17a (records), 17b (the lights, and 31b's glimpse after its fourth unit), and every
 * {@value #SCAN_EVERY} ticks 17d (a marker moved) and 17e (a book of one's own); for every known archive whose way in is
 * loaded, 17c (the other door). Each answer goes through the kit's {@link Answers} (caps, the fair-danger checks, out of
 * view, ledgered) but 17c, which happens to someone outside and keeps the same rules by itself. Session state only; what
 * must last is in the kit's data (visits, counts) and in the blocks. Server thread.
 */
final class ArchiveRules {
	static final String DARK = "17b";
	static final String MARKER = "17d";
	static final String BOOK = "17e";
	static final String DARK_CAUSE = S17.CAUSE + "/dark";
	static final String MARKER_CAUSE = S17.CAUSE + "/marker";
	static final String BOOK_CAUSE = S17.CAUSE + "/book";
	/** How often the rules look (ticks). */
	static final int EVERY = 20;
	/** How often markers and books are looked for (ticks). */
	static final int SCAN_EVERY = 100;
	/** A player this near the dais lectern has reached it (17a and 17e come before). */
	static final double DAIS_REACH = 10.0;
	/** At most this many lights go in one visit (the plan's 64 and the hall's sconces: 8 rows are 62 in the short hall). */
	static final int LIGHTS_MAX = 80;
	/** 17e: nobody within this of the container. */
	static final double BOOK_CLEAR = 16.0;

	/** The live rules. */
	static final ArchiveRules LIVE = new ArchiveRules(null);

	private final @Nullable ArchiveEnv fixed;
	private final Map<String, Visit> visits = new LinkedHashMap<>();
	private final Map<String, ArchivePlan> plans = new HashMap<>();
	private final Map<String, Long> nearMouth = new HashMap<>();
	private final Set<String> rolled = new HashSet<>();
	final Sightings sightings = new Sightings();

	private ArchiveRules(@Nullable ArchiveEnv fixed) {
		this.fixed = fixed;
	}

	/** A test's own rules over its env. */
	static ArchiveRules over(ArchiveEnv env) {
		return new ArchiveRules(env);
	}

	ArchiveEnv env(MinecraftServer server) {
		return fixed != null ? fixed : ArchiveEnv.live(server);
	}

	/** One archive's visit (from the first look someone is inside to the first nobody is). */
	static final class Visit {
		final Remains.Place place;
		final ArchivePlan plan;
		final Map<UUID, Long> deepSince = new HashMap<>();
		final Set<Integer> stoodIn = new HashSet<>();
		final Set<UUID> reachedDais = new HashSet<>();
		final Set<UUID> recorded = new HashSet<>();
		// 17b
		boolean darkStarted;
		boolean darkOver;
		@Nullable UUID darkFor;
		final Set<Integer> dark = new HashSet<>();
		int lightsGone;
		long nextDue;
		boolean glimpsed;
		@Nullable String darkWhy;
		// 17d
		int markersMoved;
		final Set<Integer> markedCells = new HashSet<>();

		Visit(Remains.Place place, ArchivePlan plan) {
			this.place = place;
			this.plan = plan;
		}

		/** Rows (hall) dark so far. */
		int darkRows() {
			int n = 0;
			for (int u : dark) {
				n += u >= 0 ? 1 : 0;
			}
			return n;
		}
	}

	static String key(Remains.Place place) {
		return place.dimension().identifier() + "/" + place.anchor().asLong();
	}

	/** The plan of a known archive (from its start and its tunnels kept with it). */
	@Nullable ArchivePlan plan(Sc2Data data, Remains.Place place) {
		String key = key(place);
		ArchivePlan plan = plans.get(key);
		if (plan == null) {
			Optional<Sc2Data.Known> known = data.known(place);
			if (known.isEmpty() || !known.get().kind().equals(S17.ID)) {
				return null;
			}
			plan = ArchivePlan.of(known.get().start(), ArchiveShape.lengths(known.get().extra()));
			plans.put(key, plan);
		}
		return plan;
	}

	@Nullable Visit visit(Remains.Place place) {
		return visits.get(key(place));
	}

	/** The live tick: a look every {@value #EVERY} ticks. */
	void tick(MinecraftServer server) {
		if (!SelfCreated.enabled()) {
			if (!visits.isEmpty()) {
				clear();
			}
			return;
		}
		long t = server.getTickCount();
		if (t % EVERY == 11) {
			look(server, t % SCAN_EVERY == 11);
		}
	}

	/** One look at every archive (tests call it directly). {@code scan}: markers and books too. */
	void look(MinecraftServer server, boolean scan) {
		ArchiveEnv env = env(server);
		// R12-10: nothing of the archive's (17a's records, 17c's door) once the story holds the layer back.
		if (env.answers().held(server) != null) {
			return;
		}
		Sc2Data data = env.data().get();
		if (scan) {
			scan(data, alive(env.people().get()));
		}
		Set<String> active = new HashSet<>();
		for (Sc2Data.Known known : data.places()) {
			if (!known.kind().equals(S17.ID)) {
				continue;
			}
			Remains.Place place = known.place();
			ServerLevel level = server.getLevel(place.dimension());
			ArchivePlan plan = plan(data, place);
			if (level == null || plan == null) {
				continue;
			}
			List<ServerPlayer> inside = env.inside().apply(place);
			if (!inside.isEmpty()) {
				String key = key(place);
				active.add(key);
				Visit visit = visits.computeIfAbsent(key, k -> new Visit(place, plan));
				step(env, level, visit, inside, scan);
			}
			door(env, level, place, plan, data, false);
		}
		visits.keySet().retainAll(active);
	}

	private static List<ServerPlayer> alive(List<ServerPlayer> people) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : people) {
			if (!p.isSpectator() && p.isAlive()) {
				out.add(p);
			}
		}
		return out;
	}

	/**
	 * 17e's look for books (R12-12): only while an archive in a player's level still has its 17e to come, only round the
	 * players there, never in one of his places (their boxes listed once a look).
	 */
	private void scan(Sc2Data data, List<ServerPlayer> people) {
		Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> waiting = new HashSet<>();
		Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, List<net.minecraft.world.level.levelgen.structure.BoundingBox>> boxes = new HashMap<>();
		for (Sc2Data.Known known : data.places()) {
			Remains.Place place = known.place();
			boxes.computeIfAbsent(place.dimension(), k -> new ArrayList<>()).add(place.box());
			if (known.kind().equals(S17.ID) && data.spent(place, BOOK).ever() == 0) {
				waiting.add(place.dimension());
			}
		}
		List<ServerPlayer> round = new ArrayList<>();
		for (ServerPlayer p : people) {
			if (waiting.contains(p.level().dimension())) {
				round.add(p);
			}
		}
		if (round.isEmpty()) {
			return;
		}
		sightings.scan(round, (level, pos) -> {
			for (net.minecraft.world.level.levelgen.structure.BoundingBox box : boxes.getOrDefault(level.dimension(), List.of())) {
				if (box.isInside(pos)) {
					return true;
				}
			}
			return false;
		});
	}

	// --- a visit ---

	void step(ArchiveEnv env, ServerLevel level, Visit visit, List<ServerPlayer> inside, boolean scan) {
		ArchivePlan plan = visit.plan;
		long now = env.now().getAsLong();
		Vec3 lectern = Vec3.atCenterOf(plan.lectern());
		for (ServerPlayer p : inside) {
			int cell = plan.cellOf(p.blockPosition());
			if (cell >= 0) {
				visit.stoodIn.add(cell);
			}
			if (p.position().distanceTo(lectern) <= DAIS_REACH) {
				visit.reachedDais.add(p.getUUID());
			}
		}
		records(env, level, visit, inside);
		dark(env, level, visit, inside, now, false);
		if (scan) {
			markers(env, level, visit, inside);
			book(env, level, visit, inside);
		}
	}

	// --- 17a ---

	private void records(ArchiveEnv env, ServerLevel level, Visit visit, List<ServerPlayer> inside) {
		Set<UUID> known = new HashSet<>();
		for (HerobrineState.Subject subject : env.known().get()) {
			known.add(subject.uuid());
		}
		for (ServerPlayer p : inside) {
			if (!known.contains(p.getUUID()) || visit.recorded.contains(p.getUUID()) || visit.reachedDais.contains(p.getUUID())) {
				continue;
			}
			// R12-10: never in a player's first hour (their first record comes in their quiet: their first visit, R10-7).
			if (env.playTicks().applyAsLong(p.getUUID()) < Answers.FIRST_HOUR_TICKS) {
				continue;
			}
			if (Records.leave(level, visit.plan, p.getName().getString(), env.traces())) {
				visit.recorded.add(p.getUUID());
			}
		}
	}

	// --- 17b ---

	/**
	 * The lights: each player's time deep inside counted; once one has been {@code darkDepth} in for
	 * {@code darkAfterSeconds}, a unit goes every {@code darkRowTicks} (out of view, through the kit) until
	 * {@link LightsOut#next} has nothing more; the glimpse after {@code glimpseAfterRows}. {@code force}: start now (the
	 * debug's {@code arm}), every other rule kept.
	 */
	void dark(ArchiveEnv env, ServerLevel level, Visit visit, List<ServerPlayer> inside, long now, boolean force) {
		Sc17Config config = env.config().get();
		ArchivePlan plan = visit.plan;
		int open = Math.max(0, OtherDoor.openWay(level, plan));
		int outer = plan.outerCell(open);
		for (ServerPlayer p : inside) {
			if (LightsOut.depth(plan, p.blockPosition(), outer) >= config.darkDepth) {
				visit.deepSince.putIfAbsent(p.getUUID(), now);
			} else {
				visit.deepSince.remove(p.getUUID());
			}
		}
		if (!visit.darkStarted && !visit.darkOver) {
			for (ServerPlayer p : inside) {
				Long since = visit.deepSince.get(p.getUUID());
				if (since != null && (force || now - since >= config.darkAfterSeconds * 20L)) {
					visit.darkStarted = true;
					visit.darkFor = p.getUUID();
					visit.nextDue = now;
					break;
				}
			}
		}
		if (!visit.darkStarted || visit.darkOver || now < visit.nextDue || env.answers().waiting(visit.place, DARK)) {
			return;
		}
		if (visit.dark.isEmpty()) {
			Sc2Data.Spent spent = env.data().get().spent(visit.place, DARK);
			if (spent.day() != Sc2Data.NEVER && env.day().getAsLong() - spent.day() < config.darkDaysApart) {
				visit.darkOver = true;
				visit.darkWhy = "the place had it within " + config.darkDaysApart + " days";
				return;
			}
		}
		List<BlockPos> feet = new ArrayList<>();
		for (ServerPlayer p : inside) {
			feet.add(p.blockPosition());
		}
		int unit = LightsOut.next(plan, feet, visit.dark, outer, config.darkRowsMin);
		if (unit == LightsOut.NONE || visit.lightsGone >= LIGHTS_MAX) {
			visit.darkOver = true;
			visit.darkWhy = unit == LightsOut.NONE ? "the dark is done" : "at its lights' cap";
			return;
		}
		Answers.Rules rules = Answers.Rules.of(DARK, DARK_CAUSE).perPlaceDay(1);
		if (!visit.dark.isEmpty()) {
			rules = rules.part();
		}
		Answers.Result result = env.answers().submit(level, visit.place, rules, (lvl, place, in) -> {
			// R12-9: planned for where everyone stood then; asked again now, it waits while anyone stands under it or next to it.
			if (!stillClear(plan, unit, in)) {
				return null;
			}
			List<BlockPos> lights = new ArrayList<>();
			for (BlockPos light : LightsOut.lights(plan, unit)) {
				if (lvl.isLoaded(light) && lvl.getBlockState(light).is(net.minecraft.world.level.block.Blocks.GLOWSTONE)) {
					lights.add(light);
				}
			}
			if (lights.isEmpty()) {
				visit.dark.add(unit); // gone already (dug or taken): nothing to do, the next one
				return null;
			}
			return Answers.Edit.of(lights, traces -> {
				var batch = traces.batch(lvl, DARK_CAUSE);
				lights.forEach(batch::remove);
				boolean ok = batch.commit();
				if (ok) {
					visit.dark.add(unit);
					visit.lightsGone += lights.size();
					visit.nextDue = env.now().getAsLong() + config.darkRowTicks;
					afterUnit(env, lvl, visit, config);
				}
				return ok;
			});
		});
		if (result.status() == Answers.Status.REFUSED && visit.dark.isEmpty()) {
			visit.darkOver = true;
			visit.darkWhy = result.reason();
		}
	}

	/**
	 * True if darkening this unit now keeps 17b's rule for everyone inside now (R12-9): never the hall row a player stands
	 * under nor either next to it, never a cell a player stands in nor one next to it, never a light within reach of anyone.
	 */
	static boolean stillClear(ArchivePlan plan, int unit, List<ServerPlayer> inside) {
		List<BlockPos> feet = new ArrayList<>();
		for (ServerPlayer p : inside) {
			BlockPos at = p.blockPosition();
			feet.add(at);
			if (unit >= 0 && plan.inHallAt(at) && Math.abs(ArchivePlan.rowAt(plan.localF(at.getX(), at.getZ())) - unit) <= 1) {
				return false;
			}
			if (unit < 0) {
				int cell = plan.cellOf(at);
				if (cell >= 0 && plan.cellApart(cell, LightsOut.cellOfUnit(unit)) < 2) {
					return false;
				}
			}
		}
		return LightsOut.clear(plan, unit, feet);
	}

	/** After a unit went: 31b's glimpse once {@code glimpseAfterRows} are dark (D-105; every glimpse rule its own). */
	private void afterUnit(ArchiveEnv env, ServerLevel level, Visit visit, Sc17Config config) {
		if (visit.glimpsed || visit.darkRows() < config.glimpseAfterRows || visit.darkFor == null) {
			return;
		}
		ServerPlayer player = null;
		for (ServerPlayer p : env.inside().apply(visit.place)) {
			if (p.getUUID().equals(visit.darkFor)) {
				player = p;
			}
		}
		if (player == null) {
			return;
		}
		Optional<Vec3> spot = LightsOut.glimpseSpot(visit.plan, player.blockPosition(), visit.dark);
		if (spot.isEmpty()) {
			return;
		}
		visit.glimpsed = true;
		double dx = player.getX() - spot.get().x;
		double dz = player.getZ() - spot.get().z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		var fired = env.figure().glimpse(player, spot.get(), yaw);
		A1016_02.LOGGER.debug("[a1016] s2: sc_31b at {} for {}: {}", spot.get(), player.getName().getString(), fired);
	}

	// --- 17d ---

	static boolean isMarker(BlockState state) {
		return state.getBlock() instanceof BaseTorchBlock || state.getBlock() instanceof LanternBlock;
	}

	private void markers(ArchiveEnv env, ServerLevel level, Visit visit, List<ServerPlayer> inside) {
		Sc17Config config = env.config().get();
		if (visit.markersMoved >= config.markerMovesPerVisit || env.answers().waiting(visit.place, MARKER)) {
			return;
		}
		ArchivePlan plan = visit.plan;
		BlockPos centre = plan.at(0, 2, (ArchivePlan.CELLS_BACK + plan.tunnelF0) / 2);
		int radius = 4 * plan.cols + ArchivePlan.CELL * plan.rows;
		List<int[]> where = playerCells(plan, inside);
		for (BlockPos torch : env.watch().placedNear(level, centre, radius, ArchiveRules::isMarker)) {
			int cell = plan.cellOf(torch);
			if (cell < 0 || visit.markedCells.contains(cell) || !apart(plan, cell, where)) {
				continue;
			}
			int to = sameLookingCell(level, visit, cell, torch, where);
			if (to < 0) {
				continue;
			}
			BlockPos target = torch.offset(plan.at(plan.cellR(to), 0, plan.cellF(to)).subtract(plan.at(plan.cellR(cell), 0, plan.cellF(cell))));
			Answers.Rules rules = Answers.Rules.of(MARKER, MARKER_CAUSE);
			if (visit.markersMoved > 0) {
				rules = rules.part();
			}
			int from = cell;
			Answers.Result result = env.answers().submit(level, visit.place, rules, (lvl, place, in) -> Answers.Edit.of(List.of(torch, target), traces -> {
				boolean ok = traces.move(lvl, torch, target, MARKER_CAUSE);
				if (ok) {
					visit.markersMoved++;
					visit.markedCells.add(from);
					visit.markedCells.add(to);
				}
				return ok;
			}));
			if (result.status() != Answers.Status.REFUSED) {
				return;
			}
		}
	}

	/** Each player's cell ({@code {cell}}), or the hall ({@code {-1}}): where they are among the cells. */
	static List<int[]> playerCells(ArchivePlan plan, List<ServerPlayer> people) {
		List<int[]> out = new ArrayList<>();
		for (ServerPlayer p : people) {
			out.add(new int[] {plan.cellOf(p.blockPosition())});
		}
		return out;
	}

	/** True if every player is 2+ cells from this one (a player in the hall counts as at the hall door's cell's back). */
	static boolean apart(ArchivePlan plan, int cell, List<int[]> where) {
		for (int[] w : where) {
			int theirs = w[0];
			if (theirs < 0) {
				// In the hall or a way in: as far as the cells go, beside the door cell.
				if (cell / plan.cols <= 0 && Math.abs(cell % plan.cols - plan.doorColumn()) <= 1) {
					return false;
				}
				continue;
			}
			if (plan.cellApart(cell, theirs) < 2) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A cell that looks the same as {@code cell} (the same detail), nobody has stood in this visit, 2+ cells from every
	 * player, with the marker's spot free and holding it; -1 if none. Cells are tried in an order from the place's seed.
	 */
	int sameLookingCell(ServerLevel level, Visit visit, int cell, BlockPos torch, List<int[]> where) {
		ArchivePlan plan = visit.plan;
		ArchivePlan.Detail detail = plan.detail(cell % plan.cols, cell / plan.cols);
		BlockState state = level.getBlockState(torch);
		int n = plan.cellCount();
		int startAt = Draw.between(plan.seed, 900 + cell, 0, n - 1);
		for (int k = 0; k < n; k++) {
			int to = (startAt + k) % n;
			if (to == cell || visit.stoodIn.contains(to) || visit.markedCells.contains(to) || plan.detail(to % plan.cols, to / plan.cols) != detail
					|| !apart(plan, to, where)) {
				continue;
			}
			BlockPos target = torch.offset(plan.at(plan.cellR(to), 0, plan.cellF(to)).subtract(plan.at(plan.cellR(cell), 0, plan.cellF(cell))));
			if (level.isLoaded(target) && level.getBlockState(target).isAir() && state.canSurvive(level, target)) {
				return to;
			}
		}
		return -1;
	}

	// --- 17e ---

	private void book(ArchiveEnv env, ServerLevel level, Visit visit, List<ServerPlayer> inside) {
		Sc17Config config = env.config().get();
		if (env.data().get().spent(visit.place, BOOK).ever() > 0 || env.answers().waiting(visit.place, BOOK)) {
			return;
		}
		ArchivePlan plan = visit.plan;
		for (ServerPlayer owner : inside) {
			if (visit.reachedDais.contains(owner.getUUID())) {
				continue;
			}
			Optional<Sightings.Seen> seen = sightings.of(owner.getUUID());
			if (seen.isEmpty() || !seen.get().dimension().equals(level.dimension())) {
				continue;
			}
			BlockPos container = seen.get().container();
			if (!level.isLoaded(container)) {
				// Loaded for a minute to look again (a ticket of its own, never generating anything new).
				level.getChunkSource().addTicketWithRadius(NewScarPlacer.TICKET, ChunkPos.containing(container), 0);
				continue;
			}
			if (!(level.getBlockEntity(container) instanceof Container held)) {
				sightings.forget(owner.getUUID());
				continue;
			}
			int slot = Sightings.slotOf(held, owner.getUUID());
			if (slot < 0) {
				sightings.forget(owner.getUUID());
				continue;
			}
			Vec3 at = Vec3.atCenterOf(container);
			if (owner.position().distanceTo(at) < config.bookAwayDistance || someoneWithin(env, level, at, BOOK_CLEAR)) {
				continue;
			}
			BlockPos shelf = plan.bookShelf();
			Answers.Rules rules = Answers.Rules.of(BOOK, BOOK_CAUSE).perPlaceEver(1);
			env.answers().submit(level, visit.place, rules, (lvl, place, in) -> Answers.Edit.of(List.of(container, shelf),
					traces -> traces.moveStack(lvl, container, slot, shelf, BOOK_CAUSE)));
			return;
		}
	}

	private static boolean someoneWithin(ArchiveEnv env, ServerLevel level, Vec3 at, double radius) {
		for (ServerPlayer p : env.people().get()) {
			if (p.level() == level && p.position().distanceTo(at) < radius) {
				return true;
			}
		}
		return false;
	}

	// --- 17c ---

	/**
	 * The other door, for one known archive: a newcomer near the open mouth while everyone who has been inside is far,
	 * or (alone) one coming back after a day away, by chance; out of view, the caps kept. {@code force}: now, for the
	 * debug ({@code sc 17 b}), the view and the caps kept, the people rule left aside. Returns why not, or null if moved.
	 */
	@Nullable String door(ArchiveEnv env, ServerLevel level, Remains.Place place, ArchivePlan plan, Sc2Data data, boolean force) {
		Sc17Config config = env.config().get();
		Vec3 first = OtherDoor.mouth(plan, 0);
		List<ServerPlayer> people = new ArrayList<>();
		for (ServerPlayer p : alive(env.people().get())) {
			if (p.level() == level && p.position().distanceTo(first) < 256) {
				people.add(p);
			}
		}
		if (people.isEmpty() && !force) {
			return "nobody near";
		}
		if (!force) {
			// R12-23: the people first (cheap): someone must be at one of the mouths at all before the ways are read.
			boolean atMouth = false;
			for (int t = 0; t < plan.tunnels.length && !atMouth; t++) {
				Vec3 m = OtherDoor.mouth(plan, t);
				for (ServerPlayer p : people) {
					atMouth |= p.position().distanceTo(m) <= config.doorNewcomerNear;
				}
			}
			if (!atMouth) {
				return "nobody at a mouth";
			}
		} else {
			// R12-27: forced (the debug) keeps the switch, the story and nobody inside or in a way.
			String held = env.answers().held(level.getServer());
			if (held != null) {
				return held;
			}
			for (ServerPlayer p : alive(env.people().get())) {
				if (p.level() == level && OtherDoor.inside(plan, p.blockPosition())) {
					return p.getName().getString() + " is inside";
				}
			}
		}
		if (!level.isLoaded(plan.entrance())) {
			return "not loaded";
		}
		int open = OtherDoor.openWay(level, plan);
		if (open < 0) {
			return "no single way open";
		}
		Sc2Data.Spent spent = data.spent(place, OtherDoor.ANSWER);
		if (spent.ever() >= config.doorMovesMax) {
			return "moved " + spent.ever() + " times";
		}
		int closed = OtherDoor.closedToOpen(level, plan, open, spent.ever());
		if (closed < 0) {
			return "no closed way whole";
		}
		Vec3 mouth = OtherDoor.mouth(plan, open);
		long today = env.day().getAsLong();
		ServerPlayer who = null;
		if (!force) {
			// A newcomer: someone who was never inside, near; everyone who was, far.
			String refusal = OtherDoor.refusal(plan, open, closed, people, p -> data.visited(p.getUUID(), place), config.doorNewcomerNear,
					config.doorFinderAway, false);
			if (refusal == null) {
				for (ServerPlayer p : people) {
					if (!data.visited(p.getUUID(), place) && p.position().distanceTo(mouth) <= config.doorNewcomerNear) {
						who = p;
					}
				}
			} else {
				who = returning(env, place, plan, open, closed, people, data, today, config);
				if (who == null) {
					return refusal;
				}
			}
			if (!env.answerable(who) || data.spent(who.getUUID(), OtherDoor.ANSWER).on(today) >= 1) {
				return who.getName().getString() + " may not have it now";
			}
		}
		if (!env.view().outOfView(level, OtherDoor.cells(plan, closed, open))) {
			return "in view";
		}
		if (!OtherDoor.move(env.traces(), level, plan, closed, open)) {
			return "could not be done";
		}
		List<UUID> to = who == null ? List.of() : List.of(who.getUUID());
		data.spend(place, OtherDoor.ANSWER, to, today);
		if (who != null) {
			env.sink().answered(level, place, OtherDoor.ANSWER, who);
		}
		A1016_02.LOGGER.debug("[a1016] s2: 17c at {}: way {} shut, way {} open", place.anchor().toShortString(), open, closed);
		return null;
	}

	/**
	 * 17c alone: someone who has been inside coming back to the open mouth after a day or more away, nobody else within
	 * the finders' distance: once per return, by chance ({@code doorMovedChance}); else null.
	 */
	private @Nullable ServerPlayer returning(ArchiveEnv env, Remains.Place place, ArchivePlan plan, int open, int closed, List<ServerPlayer> people,
			Sc2Data data, long today, Sc17Config config) {
		Vec3 mouth = OtherDoor.mouth(plan, open);
		ServerPlayer back = null;
		for (ServerPlayer p : people) {
			if (!data.visited(p.getUUID(), place) || p.position().distanceTo(mouth) > config.doorNewcomerNear) {
				continue;
			}
			// Each look near the mouth keeps the day; a look after a day or more away is a return, rolled once.
			String key = key(place) + "|" + p.getUUID();
			Long last = nearMouth.put(key, today);
			if (last != null && today - last >= 1 && Draw.unit(plan.seed, (int) today, p.getUUID().hashCode(), 17) < config.doorMovedChance) {
				rolled.add(key);
			}
			if (rolled.contains(key)) {
				back = p;
			}
		}
		if (back == null) {
			return null;
		}
		for (ServerPlayer p : people) {
			if (p != back && p.position().distanceTo(mouth) < config.doorFinderAway) {
				return null;
			}
		}
		String refusal = OtherDoor.refusal(plan, open, closed, people, p -> true, config.doorNewcomerNear, config.doorFinderAway, true);
		if (refusal != null) {
			return null;
		}
		rolled.remove(key(place) + "|" + back.getUUID());
		return back;
	}

	// --- debug ---

	/** {@code sc 17 arm}: for this player inside an archive, 17b starts now and the markers and the book are looked for now; rules kept. */
	@Nullable String arm(MinecraftServer server, ServerPlayer player) {
		ArchiveEnv env = env(server);
		// R12-27: the debug's arm keeps the switch and the story (no record left while off).
		String held = env.answers().held(server);
		if (held != null) {
			return held;
		}
		Sc2Data data = env.data().get();
		for (Sc2Data.Known known : data.places()) {
			Remains.Place place = known.place();
			ArchivePlan plan = plan(data, place);
			ServerLevel level = server.getLevel(place.dimension());
			if (plan == null || level == null || !env.inside().apply(place).contains(player)) {
				continue;
			}
			List<ServerPlayer> inside = env.inside().apply(place);
			Visit visit = visits.computeIfAbsent(key(place), k -> new Visit(place, plan));
			visit.darkOver = false;
			visit.deepSince.put(player.getUUID(), env.now().getAsLong());
			step(env, level, visit, inside, true);
			dark(env, level, visit, inside, env.now().getAsLong(), true);
			return null;
		}
		return "you are not inside one";
	}

	/** Forgets every visit (switch off, server stop). */
	void clear() {
		visits.clear();
		plans.clear();
		nearMouth.clear();
		rolled.clear();
		sightings.clear();
	}
}
