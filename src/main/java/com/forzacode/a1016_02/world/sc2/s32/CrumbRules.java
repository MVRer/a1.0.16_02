package com.forzacode.a1016_02.world.sc2.s32;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.Bases;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.s32.CrumbData.Crumb;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Line;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

/**
 * Feature 32's runtime rules (docs/SELF_CREATED_2_R3.md, "32b", "32c"), multiplayer, server thread, only while his
 * places are awake ({@link Remains#awake}: the switch, the story's gates, Traces or later), only for participants in
 * survival or adventure (a creative or spectating player sees nothing and moves nothing).
 * <ul>
 * <li><b>Sightings.</b> Every {@value #SAMPLE_TICKS} ticks, a participant sees a crumb within {@value #SEE} that lies in
 * their view cone ({@value #CONE} degrees) and line of sight (the crumbs of the chunks within {@value #LOOK} of them: a
 * chunk-grid index).</li>
 * <li><b>32b, it was right here.</b> A participant who has not seen a crumb comes within {@code j} of it while everyone
 * who has seen it is at least {@code i} away and it is out of everyone's view: the crumb is taken (a post only: one
 * column and its torch), through the ledger ({@code world:s32/gone}, given back by the switch and Ending D). Once a
 * line, at most one a day in a world.</li>
 * <li><b>32c, it came closer.</b> A participant who saw two or more crumbs of one line in one day and then did not follow
 * it (the next ten minutes of their play took them {@value #AWAY} farther from its place than the nearest crumb they
 * saw, or they went home): that night the line's farthest far crumb still standing is moved, every block of it
 * ({@code world:s32/pull}), to {@code k} to {@code l} from their base on the straight line from the base toward the
 * nearest crumb they saw, out of everyone's view, on clear footing, never within {@value #PLACED_CLEAR} of a block a
 * player placed, never within {@code n} of any base; the next such night the next one 64 farther out on the same line.
 * At most one a day a player, {@code m} a line.</li>
 * </ul>
 * Each is one of the second wave's moments for the director (R3-P, D-119): it waits while a player it comes to is in
 * their wave gap ({@code Director.waveMomentMay}) and starts their gap when it comes ({@code Director.waveMoment}): the
 * newcomer and those who saw the crumb (32b), the player whose base the spur grows toward (32c).
 */
public final class CrumbRules {
	public static final CrumbRules LIVE = new CrumbRules();

	static final int SAMPLE_TICKS = 20;
	/** A crumb is seen within this. */
	static final int SEE = 32;
	/** The crumbs of the chunks within this of a participant are looked at. */
	static final int LOOK = 48;
	/** The view cone a sighting needs (degrees). */
	static final double CONE = 70;
	/** 32c: how much farther from the place than the nearest crumb seen is "not following". */
	static final int AWAY = 160;
	/** 32c: how long after the second sighting the not-following is watched (ticks of their play: ten minutes). */
	static final int WATCH_TICKS = 10 * 60 * 20;
	/** 32c: never within this of a block a player placed. */
	static final int PLACED_CLEAR = 8;
	/** 32c: the next step of a spur goes this much farther out. */
	static final int SPUR_STEP = 64;
	/** 32c: "home" is within this of their base. */
	static final int HOME = 24;
	/** 32c: the night it waits for (day time). */
	static final long NIGHT_FROM = 13_000L;
	static final long NIGHT_TO = 23_000L;

	/** Where the players' bases are (32c): the world's {@link Bases} live, a test's own otherwise. */
	public interface Homes {
		Optional<BlockPos> base(UUID player);

		boolean nearAny(BlockPos pos, double radius);

		static Homes of(MinecraftServer server) {
			Bases bases = Bases.live(server);
			return new Homes() {
				@Override
				public Optional<BlockPos> base(UUID player) {
					return bases.base(player).filter(g -> g.dimension() == Level.OVERWORLD).map(GlobalPos::pos);
				}

				@Override
				public boolean nearAny(BlockPos pos, double radius) {
					return bases.nearAny(Level.OVERWORLD, pos, radius);
				}
			};
		}
	}

	/** One participant's watch of one line today (32c). */
	static final class Watch {
		long day;
		int seen;
		@Nullable Crumb nearest;
		int nearestFromPlace;
		int watched = -1;
		boolean done;
	}

	/** A move owed to a participant tonight: the line and the nearest crumb they saw of it. */
	record Owed(String line, BlockPos nearest, long day) {
	}

	private final Map<UUID, Map<String, Watch>> watches = new HashMap<>();
	private final Map<UUID, Owed> owed = new HashMap<>();
	/** The last outcome of each rule, for {@code /a1016 sc 32} (debug only, never logged at INFO). */
	private String lastGone = "never";
	private String lastPull = "never";

	private CrumbRules() {
	}

	static void init() {
		Answers.giveBackWhenOff("sc32", S32.CAUSE);
	}

	/** Server stop. */
	public void reset() {
		watches.clear();
		owed.clear();
	}

	public void tick(MinecraftServer server) {
		if (server.getTickCount() % SAMPLE_TICKS != 0) {
			return;
		}
		run(server, false);
	}

	/** One pass of the rules now ({@code force}: the sampling's turn not waited for; the rules' own conditions kept). */
	public String run(MinecraftServer server, boolean force) {
		Sc32Config c = S32.config();
		if (c == null || !Remains.awake(server)) {
			return "asleep";
		}
		ServerLevel level = server.overworld();
		CrumbData data = CrumbData.get(server);
		if (data.size() == 0) {
			return "none built";
		}
		List<ServerPlayer> players = players(server, level);
		long day = com.forzacode.a1016_02.core.GameClock.day(server);
		Homes homes = Homes.of(server);
		for (ServerPlayer p : players) {
			sample(level, data, p, day, homes);
		}
		String gone = gone(level, data, players, day, c);
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		String pull = pull(level, data, day, c, homes, time >= NIGHT_FROM && time <= NIGHT_TO);
		return "32b " + gone + "; 32c " + pull;
	}

	/** The participants in the overworld who count (survival or adventure). */
	static List<ServerPlayer> players(MinecraftServer server, ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : Services.watch().participants(server)) {
			GameType mode = p.gameMode.getGameModeForPlayer();
			if (p.level() == level && (mode == GameType.SURVIVAL || mode == GameType.ADVENTURE) && p.isAlive()) {
				out.add(p);
			}
		}
		return out;
	}

	// --- sightings ---

	/** One participant's sightings now, and 32c's watch of them. */
	public void sample(ServerLevel level, CrumbData data, ServerPlayer p, long day, Homes homes) {
		BlockPos at = p.blockPosition();
		for (Crumb crumb : data.near(at.getX(), at.getZ(), LOOK)) {
			if (crumb.at().distSqr(at) > (double) SEE * SEE || data.hasSeen(p.getUUID(), crumb.id()) || !standing(level, crumb)) {
				continue;
			}
			if (TraceService.sees(level, TraceService.Viewer.of(p, 4), box(crumb), 0.0, CONE)) {
				data.see(p.getUUID(), crumb.id());
				watch(p, crumb, day);
			}
		}
		follow(p, day, homes);
	}

	static AABB box(Crumb crumb) {
		AABB box = new AABB(crumb.at());
		for (BlockPos cell : crumb.cells()) {
			box = box.minmax(new AABB(cell));
		}
		return box;
	}

	/** True if the crumb's foot still holds something (a taken or given-back crumb is not there). */
	static boolean standing(ServerLevel level, Crumb crumb) {
		return level.isLoaded(crumb.at()) && !level.getBlockState(crumb.at()).isAir();
	}

	/** True if no block of the crumb is one a player placed (a player's block is never taken or moved). */
	static boolean untouched(ServerLevel level, Crumb crumb) {
		for (BlockPos cell : crumb.cells()) {
			if (Services.watch().wasPlacedByPlayer(level, cell)) {
				return false;
			}
		}
		return true;
	}

	private void watch(ServerPlayer p, Crumb crumb, long day) {
		Line line = line(crumb.line());
		if (line == null) {
			return;
		}
		Watch w = watches.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).computeIfAbsent(crumb.line(), k -> new Watch());
		if (w.day != day) {
			w.day = day;
			w.seen = 0;
			w.nearest = null;
			w.watched = -1;
			w.done = false;
		}
		w.seen++;
		int fromPlace = (int) Math.hypot(crumb.at().getX() - line.x(), crumb.at().getZ() - line.z());
		if (w.nearest == null || fromPlace < w.nearestFromPlace) {
			w.nearest = crumb;
			w.nearestFromPlace = fromPlace;
		}
		if (w.seen >= 2 && w.watched < 0) {
			w.watched = 0;
		}
	}

	/** 32c's watch: did they follow, or turn away or go home? */
	private void follow(ServerPlayer p, long day, Homes homes) {
		Map<String, Watch> mine = watches.get(p.getUUID());
		if (mine == null) {
			return;
		}
		for (Map.Entry<String, Watch> e : mine.entrySet()) {
			Watch w = e.getValue();
			if (w.watched < 0 || w.done || w.nearest == null) {
				continue;
			}
			Line line = line(e.getKey());
			if (line == null) {
				w.done = true;
				continue;
			}
			w.watched += SAMPLE_TICKS;
			double fromPlace = Math.hypot(p.getX() - line.x(), p.getZ() - line.z());
			Optional<BlockPos> base = homes.base(p.getUUID());
			boolean home = base.isPresent() && base.get().distSqr(p.blockPosition()) <= HOME * HOME;
			if (fromPlace >= w.nearestFromPlace + AWAY || home) {
				w.done = true;
				owed.put(p.getUUID(), new Owed(e.getKey(), w.nearest.at(), day));
			} else if (w.watched >= WATCH_TICKS || fromPlace < Math.max(32, w.nearestFromPlace - 128)) {
				w.done = true;
			}
		}
	}

	// --- 32b ---

	/** 32b's pass over these participants. */
	public String gone(ServerLevel level, CrumbData data, List<ServerPlayer> players, long day, Sc32Config c) {
		if (data.goneDay() == day) {
			return "had today";
		}
		for (ServerPlayer p : players) {
			BlockPos at = p.blockPosition();
			for (Crumb crumb : data.near(at.getX(), at.getZ(), c.goneNewcomerNear)) {
				if (!crumb.post() || data.goneFrom(crumb.line()) || data.hasSeen(p.getUUID(), crumb.id())
						|| crumb.at().distSqr(at) > (double) c.goneNewcomerNear * c.goneNewcomerNear || !standing(level, crumb)) {
					continue;
				}
				List<UUID> seers = data.seers(crumb.id());
				if (seers.isEmpty() || !seersAway(players, seers, crumb, c.goneSeerAway) || !waveMay(level.getServer(), p.getUUID(), seers)) {
					continue;
				}
				List<BlockPos> cells = crumb.cells();
				if (!untouched(level, crumb) || !Services.traces().isOutOfView(level, cells)) {
					continue;
				}
				TraceBatch batch = Services.traces().batch(level, S32.CAUSE + "/gone");
				// The torch first, then the column from the top (nothing is left hanging).
				for (int i = cells.size() - 1; i >= 0; i--) {
					if (!level.getBlockState(cells.get(i)).isAir()) {
						batch.remove(cells.get(i));
					}
				}
				if (batch.size() > 0 && batch.commit()) {
					data.gone(crumb.line(), day);
					wave(level.getServer(), p.getUUID(), seers);
					lastGone = "took one (" + crumb.tier().name().toLowerCase(java.util.Locale.ROOT) + ")";
					return lastGone;
				}
			}
		}
		return "waits";
	}

	/** R3-P: true if the second wave's moment may come to the newcomer and every seer now (none in their wave gap). */
	static boolean waveMay(MinecraftServer server, UUID newcomer, List<UUID> seers) {
		if (!Services.director().waveMomentMay(server, newcomer)) {
			return false;
		}
		for (UUID seer : seers) {
			if (!Services.director().waveMomentMay(server, seer)) {
				return false;
			}
		}
		return true;
	}

	/** R3-P: the second wave's moment came to the newcomer and every seer: it starts their wave gap. */
	static void wave(MinecraftServer server, UUID newcomer, List<UUID> seers) {
		Services.director().waveMoment(server, newcomer);
		for (UUID seer : seers) {
			if (!seer.equals(newcomer)) {
				Services.director().waveMoment(server, seer);
			}
		}
	}

	/** True if every one who has seen the crumb and is here stands at least {@code away} from it (one not here is away). */
	static boolean seersAway(List<ServerPlayer> here, List<UUID> seers, Crumb crumb, int away) {
		for (ServerPlayer p : here) {
			if (seers.contains(p.getUUID()) && p.blockPosition().distSqr(crumb.at()) < (double) away * away) {
				return false;
			}
		}
		return true;
	}

	// --- 32c ---

	/** 32c's pass: each move owed tonight that can be done now (out of view, on footing). */
	public String pull(ServerLevel level, CrumbData data, long day, Sc32Config c, Homes homes, boolean night) {
		if (owed.isEmpty()) {
			return "none owed";
		}
		if (!night) {
			return "waits for the night";
		}
		for (Map.Entry<UUID, Owed> e : new ArrayList<>(owed.entrySet())) {
			UUID player = e.getKey();
			Owed o = e.getValue();
			if (data.pullDay(player) == day || data.pulled(o.line()) >= c.pullPerLine) {
				owed.remove(player);
				continue;
			}
			Optional<BlockPos> base = homes.base(player);
			if (base.isEmpty()) {
				continue;
			}
			if (!Services.director().waveMomentMay(level.getServer(), player)) {
				// R3-P: it waits for the end of the player's wave gap (still tonight, or the next night it is owed).
				lastPull = "waits for the wave's gap";
				continue;
			}
			Crumb far = farthest(level, data, o.line());
			if (far == null) {
				owed.remove(player);
				continue;
			}
			int step = data.spur(o.line(), player);
			int distance = step == 0 ? c.pullMin + (int) Math.floorMod(CrumbLines.mix(player.getLeastSignificantBits(), day), Math.max(1, c.pullMax - c.pullMin + 1))
					: step + SPUR_STEP;
			BlockPos to = spot(level, homes, base.get(), o.nearest(), distance, far, c);
			if (to == null) {
				lastPull = "no footing";
				continue;
			}
			if (move(level, far, to)) {
				data.moved(far, to);
				data.pull(o.line(), player, day, distance);
				owed.remove(player);
				Services.director().waveMoment(level.getServer(), player);
				lastPull = "moved one";
				return lastPull;
			}
			lastPull = "in view";
		}
		return lastPull;
	}

	/** The line's far post farthest from its place that still stands where it was built. */
	static @Nullable Crumb farthest(ServerLevel level, CrumbData data, String line) {
		Crumb best = null;
		for (Crumb crumb : data.line(line).values()) {
			if (crumb.tier() == Tier.FAR && crumb.post() && !crumb.moved() && standing(level, crumb) && untouched(level, crumb)
					&& (best == null || crumb.index() > best.index())) {
				best = crumb;
			}
		}
		return best;
	}

	/**
	 * Where the moved crumb's foot goes: {@code distance} from the base toward the nearest crumb seen (tried a few blocks
	 * either way along it), on solid ground, its cells clear, never within {@code n} of a base or {@value #PLACED_CLEAR} of
	 * a placed block, loaded; null if none.
	 */
	static @Nullable BlockPos spot(ServerLevel level, Homes homes, BlockPos base, BlockPos toward, int distance, Crumb crumb, Sc32Config c) {
		double dx = toward.getX() - base.getX();
		double dz = toward.getZ() - base.getZ();
		double len = Math.hypot(dx, dz);
		if (len < 1) {
			return null;
		}
		dx /= len;
		dz /= len;
		for (int shift : CrumbFooting.SHIFTS) {
			int x = (int) Math.floor(base.getX() + 0.5 + dx * (distance + shift));
			int z = (int) Math.floor(base.getZ() + 0.5 + dz * (distance + shift));
			BlockPos column = new BlockPos(x, 0, z);
			if (!level.isLoaded(column)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos foot = new BlockPos(x, y, z);
			if (homes.nearAny(foot, c.pullBaseClear) || !fits(level, crumb, foot) || placedNear(level, foot)) {
				continue;
			}
			return foot;
		}
		return null;
	}

	/** True if the crumb's cells fit at this foot: solid ground under, its cells replaceable and loaded. */
	static boolean fits(ServerLevel level, Crumb crumb, BlockPos foot) {
		BlockPos under = foot.below();
		if (!level.getBlockState(under).isFaceSturdy(level, under, Direction.UP) || !level.getFluidState(foot).isEmpty()) {
			return false;
		}
		for (BlockPos cell : shifted(crumb, foot)) {
			BlockState s = level.getBlockState(cell);
			if (!level.isLoaded(cell) || !(s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty()) || level.getBlockEntity(cell) != null) {
				return false;
			}
		}
		BlockPos torch = crumb.torchAt();
		if (torch != null && crumb.torch() != 1) {
			BlockPos t = torch.offset(foot.subtract(crumb.at()));
			return level.getBlockState(t.below()).isFaceSturdy(level, t.below(), Direction.UP);
		}
		return true;
	}

	static List<BlockPos> shifted(Crumb crumb, BlockPos foot) {
		List<BlockPos> out = new ArrayList<>();
		BlockPos d = foot.subtract(crumb.at());
		for (BlockPos cell : crumb.cells()) {
			out.add(cell.offset(d));
		}
		return out;
	}

	/** True if a player placed a block within {@value #PLACED_CLEAR} of the spot. */
	static boolean placedNear(ServerLevel level, BlockPos foot) {
		for (BlockPos p : BlockPos.betweenClosed(foot.offset(-PLACED_CLEAR, -PLACED_CLEAR, -PLACED_CLEAR), foot.offset(PLACED_CLEAR, PLACED_CLEAR, PLACED_CLEAR))) {
			if (Services.watch().wasPlacedByPlayer(level, p)) {
				return true;
			}
		}
		return false;
	}

	/** Moves every block of the crumb, its foot first (the torch last), out of everyone's view; true if done. */
	static boolean move(ServerLevel level, Crumb crumb, BlockPos foot) {
		List<BlockPos> from = crumb.cells();
		List<BlockPos> to = shifted(crumb, foot);
		List<BlockPos> all = new ArrayList<>(from);
		all.addAll(to);
		if (!Services.traces().isOutOfView(level, all)) {
			return false;
		}
		TraceBatch batch = Services.traces().batch(level, S32.CAUSE + "/pull");
		for (int i = 0; i < from.size(); i++) {
			if (!level.getBlockState(from.get(i)).isAir()) {
				batch.move(from.get(i), to.get(i));
			}
		}
		return batch.size() > 0 && batch.commit();
	}

	/** A line known this run by its key (the index's), or null. */
	static @Nullable Line line(String key) {
		Line fixed = CrumbIndex.fixed(key);
		if (fixed != null) {
			return fixed;
		}
		for (Line l : CrumbIndex.known()) {
			if (l.key().equals(key)) {
				return l;
			}
		}
		return null;
	}

	// --- debug ---

	/** What the rules did last (debug). */
	public String last() {
		return "32b " + lastGone + ", 32c " + lastPull;
	}

	/** How many lines a player watches today and how many moves are owed (debug). */
	public String watching(UUID player) {
		Map<String, Watch> mine = watches.getOrDefault(player, Map.of());
		return mine.size() + " watched, " + (owed.containsKey(player) ? "one owed" : "none owed");
	}

	/** Tests: a move owed to a player now. */
	public void owe(UUID player, String line, BlockPos nearest, long day) {
		owed.put(player, new Owed(line, nearest.immutable(), day));
	}
}
