package com.forzacode.a1016_02.world.sc2.s19;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.Fault;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Visits;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import org.jspecify.annotations.Nullable;

/**
 * 19b, the wall mends (FIX-R3-B; Mariano: every place answers the one who reaches it). A participant who comes into the
 * line (stands in one of its openings or its cut): out of everyone's view, the opening next to theirs along the wall on
 * each side is whole wall again, obsidian in its four cells and its ring of stone, and the lights round it gone ({@code world:s19/close},
 * ledgered). Standing in the wall one sees nothing along it, so it happens while they are there; it is found, not seen:
 * whoever walks along the wall to the next hole meets plain wall, and by night the two lit holes nearest theirs are dark,
 * theirs still open. Deniable first: the holes come every 48 and nobody counts them.
 *
 * <p>Fair (D-097): never an opening anyone stands in or within {@value #CLEAR} of (their way through stays, so do the
 * cut and every other opening: the wall never shuts anyone in or out), never under {@value #HEALTH} health; the kit's
 * caps (once a line an in-game day, once a player a day), firsts, quiets, the first hour, asleep and creative (D-108,
 * D-116). An opening is open again on a later in-game day, out of view (its ledger undone); switched off, given back.
 * Server thread.
 */
public final class LineAnswers {
	/** The answer's id. */
	public static final String ID = "19b";
	/** Its ledger cause. */
	public static final String CLOSE = S19.CAUSE + "/close";
	/** How often the visits are looked at (ticks). */
	public static final int EVERY_TICKS = 20;
	/** How often the mended openings are tried for opening again (ticks). */
	public static final int REOPEN_EVERY_TICKS = 200;
	/** A refused or dropped ask waits this long before it is asked again for that line (ticks). */
	public static final int ASK_AGAIN_TICKS = 200;
	/** No opening is mended with anyone this near its middle (blocks). */
	public static final int CLEAR = 16;
	/** Everyone inside this healthy (a closing never happens to a player under 10: D-097.3). */
	public static final float HEALTH = 10.0F;
	/** The lights round an opening it takes: this far along the wall from it and out of the plane (the lit foot's reach). */
	static final int LIGHTS_ALONG = LineShape.LIT_ALONG + 2;
	static final int LIGHTS_UP = LineShape.WALL_LIGHT_UP + 2;
	private static final BlockState FILL = Blocks.OBSIDIAN.defaultBlockState();

	/**
	 * What 19b reads.
	 *
	 * @param answers  the kit's answers
	 * @param people   everyone online (spectators skipped)
	 * @param segments a recorded line's segment (its openings), or null
	 * @param day      today's in-game day
	 * @param traces   what opens a mended opening again (its ledger undone, out of view)
	 */
	public record Env(Answers answers, Visits.People people, Function<Remains.Place, LinePlan.@Nullable Segment> segments, ToLongFunction<MinecraftServer> day,
			java.util.function.Supplier<TraceService> traces) {
	}

	public static final LineAnswers LIVE = new LineAnswers(new Env(Answers.LIVE, server -> List.copyOf(server.getPlayerList().getPlayers()),
			LineAnswers::liveSegment, GameClock::day, Services::traces));

	private final Env env;
	private final Map<UUID, Remains.Place> visits = new LinkedHashMap<>();
	private final Map<Remains.Place, Long> askedAt = new HashMap<>();

	public LineAnswers(Env env) {
		this.env = env;
	}

	/** A player came inside a place (only lines count). */
	public void entered(Remains.Place place, ServerPlayer player) {
		if (place.kind() == Remains.Kind.LINE) {
			visits.put(player.getUUID(), place);
		}
	}

	/** A player left a place. */
	public void left(Remains.Place place, ServerPlayer player) {
		visits.remove(player.getUUID(), place);
	}

	/** Forgets every visit (switch off, server stop). */
	public void clear() {
		visits.clear();
		askedAt.clear();
	}

	/** The live tick: a look every {@value #EVERY_TICKS} ticks while someone stands in a line; the mended openings retried. */
	public void tick(MinecraftServer server) {
		if (!STAGED.isEmpty()) {
			tickStaged(server.getTickCount());
		}
		if (!SelfCreated.enabled()) {
			return;
		}
		if (!visits.isEmpty() && server.getTickCount() % EVERY_TICKS == 7) {
			look(server, server.getTickCount());
		}
		if (server.getTickCount() % REOPEN_EVERY_TICKS == 57) {
			reopen(server);
		}
	}

	/** One look: each line someone stands in asks for 19b (at most once every {@value #ASK_AGAIN_TICKS} ticks). */
	public void look(MinecraftServer server, long now) {
		List<Remains.Place> asked = new ArrayList<>();
		for (ServerPlayer player : env.people().online(server)) {
			Remains.Place place = visits.get(player.getUUID());
			if (place == null || player.isSpectator() || asked.contains(place) || !player.level().dimension().equals(place.dimension())) {
				continue;
			}
			Long last = askedAt.get(place);
			if (last != null && now - last < ASK_AGAIN_TICKS) {
				continue;
			}
			asked.add(place);
			askedAt.put(place, now);
			ask(player.level(), place);
		}
	}

	/** The rules of 19b: once a line a day, once a player a day, health 10. */
	public static Answers.Rules rules() {
		return Answers.Rules.of(ID, CLOSE).health(HEALTH).limitSeconds(30);
	}

	/** Asks for 19b at this line now (its planner finds the openings next to whoever is inside at each try). */
	public Answers.Result ask(ServerLevel level, Remains.Place place) {
		LinePlan.Segment segment = env.segments().apply(place);
		if (segment == null) {
			return new Answers.Result(Answers.Status.REFUSED, "no segment");
		}
		return env.answers().submit(level, place, rules(), (lvl, p, inside) -> plan(lvl, segment, inside, env.people().online(lvl.getServer())));
	}

	/** The edit of one try: the openings next to everyone inside, those clear of everyone, mended; null if none yet. */
	Answers.@Nullable Edit plan(ServerLevel level, LinePlan.Segment segment, List<ServerPlayer> inside, List<ServerPlayer> everyone) {
		List<Integer> holes = new ArrayList<>();
		for (ServerPlayer player : inside) {
			for (int hole : beside(segment, along(segment, player.getBlockX(), player.getBlockZ()))) {
				if (!holes.contains(hole)) {
					holes.add(hole);
				}
			}
		}
		List<BlockPos> fill = new ArrayList<>();
		List<BlockPos> lights = new ArrayList<>();
		for (int hole : holes) {
			List<BlockPos> cells = cells(level, segment, hole);
			if (cells.isEmpty() || Answers.occupied(level, cells) || near(level, segment, hole, everyone)) {
				continue;
			}
			fill.addAll(cells);
			fill.addAll(stoneRing(level, segment, hole, cells.getFirst().getY()));
			lights.addAll(lightsRound(level, segment, hole, cells.getFirst().getY()));
		}
		if (fill.isEmpty()) {
			return null;
		}
		List<BlockPos> touched = new ArrayList<>(fill);
		touched.addAll(lights);
		return Answers.Edit.of(touched, traces -> {
			TraceBatch batch = traces.batch(level, CLOSE);
			for (BlockPos cell : fill) {
				batch.convert(cell, FILL);
			}
			for (BlockPos light : lights) {
				if (!fill.contains(light)) {
					batch.remove(light);
				}
			}
			return batch.commit();
		});
	}

	/** Where along the wall's axis a column stands. */
	static int along(LinePlan.Segment segment, int x, int z) {
		return segment.arm().alongX() ? x : z;
	}

	/** The openings next to {@code t} along the wall, one each way (never the one at {@code t}). */
	static List<Integer> beside(LinePlan.Segment segment, int t) {
		Integer before = null;
		Integer after = null;
		for (int hole : segment.holes()) {
			if (hole + 1 < t) {
				before = hole;
			} else if (hole > t && after == null) {
				after = hole;
			}
		}
		List<Integer> out = new ArrayList<>(2);
		if (before != null) {
			out.add(before);
		}
		if (after != null) {
			out.add(after);
		}
		return out;
	}

	/** True if anyone (a spectator not) stands within {@value #CLEAR} of this opening's middle. */
	private static boolean near(ServerLevel level, LinePlan.Segment segment, int hole, List<ServerPlayer> everyone) {
		double mx = segment.x(hole) + (segment.arm().alongX() ? 1.0 : 0.5);
		double mz = segment.z(hole) + (segment.arm().alongX() ? 0.5 : 1.0);
		for (ServerPlayer player : everyone) {
			if (player.isSpectator() || player.level() != level) {
				continue;
			}
			double dx = player.getX() - mx;
			double dz = player.getZ() - mz;
			if (dx * dx + dz * dz <= CLEAR * CLEAR) {
				return true;
			}
		}
		return false;
	}

	/** True for a cell an opening leaves open: air, water or one of its lights. */
	public static boolean open(BlockState state) {
		return state.isAir() || Fault.isLight(state) || state.is(Blocks.WATER);
	}

	/**
	 * An opening's four cells as the live world has them (the two lowest open cells of its two columns in the plane, over
	 * the wall's own block, near the land beside it), or none if it is not open now (mended, or dug about) or not loaded.
	 */
	public static List<BlockPos> cells(ServerLevel level, LinePlan.Segment segment, int hole) {
		int plane = segment.plane();
		BlockPos first = new BlockPos(segment.x(hole), 0, segment.z(hole));
		BlockPos second = new BlockPos(segment.x(hole + 1), 0, segment.z(hole + 1));
		if (!level.hasChunkAt(first) || !level.hasChunkAt(second)) {
			return List.of();
		}
		int land = Integer.MIN_VALUE;
		for (int t = hole; t <= hole + 1; t++) {
			for (int side : new int[] {-1, 1}) {
				land = Math.max(land, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, segment.x(t, plane + side), segment.z(t, plane + side)));
			}
		}
		for (int y = land - 6; y <= land + 6; y++) {
			boolean whole = true;
			for (BlockPos column : List.of(first, second)) {
				whole &= !open(level.getBlockState(column.atY(y - 1))) && open(level.getBlockState(column.atY(y))) && open(level.getBlockState(column.atY(y + 1)));
			}
			if (whole) {
				return List.of(first.atY(y), first.atY(y + 1), second.atY(y), second.atY(y + 1));
			}
		}
		return List.of();
	}

	/**
	 * The ring of stone round an opening (version 4) as the live world has it: the cells of the 4 x 4 round its 2 x 2 in
	 * the wall's plane that are the ring's stone (a version 3 opening has none: its ring is the wall's own obsidian).
	 * 19b mends them with it, so the wall is whole there again, no frame left to tell where an opening was.
	 */
	static List<BlockPos> stoneRing(ServerLevel level, LinePlan.Segment segment, int hole, int low) {
		List<BlockPos> out = new ArrayList<>(12);
		for (int t = hole - 1; t <= hole + 2; t++) {
			for (int y = low - 1; y <= low + 2; y++) {
				if (t >= hole && t <= hole + 1 && y >= low && y <= low + 1) {
					continue;
				}
				BlockPos cell = new BlockPos(segment.x(t), y, segment.z(t));
				if (level.isLoaded(cell) && level.getBlockState(cell) == LineShape.ringStone(y)) {
					out.add(cell);
				}
			}
		}
		return out;
	}

	/** The lights of the lit foot round an opening (its squares, its sills, up the wall's foot), as the live world has them. */
	static List<BlockPos> lightsRound(ServerLevel level, LinePlan.Segment segment, int hole, int low) {
		List<BlockPos> out = new ArrayList<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int t = hole - LIGHTS_ALONG; t <= hole + 1 + LIGHTS_ALONG; t++) {
			for (int a = segment.plane() - LineShape.LIT_REACH; a <= segment.plane() + LineShape.LIT_REACH; a++) {
				for (int y = low - 4; y <= low + LIGHTS_UP; y++) {
					at.set(segment.x(t, a), y, segment.z(t, a));
					if (level.isLoaded(at) && Fault.isLight(level.getBlockState(at))) {
						out.add(at.immutable());
					}
				}
			}
		}
		return out;
	}

	/**
	 * The mended openings of an earlier in-game day are open again: their ledgered cells and lights taken back, each out
	 * of everyone's view (the ledger's undo asks it; a cell in view waits for the next try).
	 */
	public void reopen(MinecraftServer server) {
		long today = env.day().applyAsLong(server);
		TraceLedger ledger = TraceLedger.get(server);
		for (TraceLedger.Entry entry : List.copyOf(ledger.entries())) {
			if (!entry.cause().equals(CLOSE) || entry.day() >= today) {
				continue;
			}
			ServerLevel level = server.getLevel(entry.pos().dimension());
			if (level == null || !level.isLoaded(entry.pos().pos())) {
				continue;
			}
			env.traces().get().undo(level, entry, CLOSE + "/back");
		}
	}

	// --- the gallery's staging (dev only) ---

	private record Staged(ServerLevel level, LinePlan.Segment segment, List<Integer> holes, long at) {
	}

	private static final List<Staged> STAGED = new ArrayList<>();

	/** The gallery's 19b (dev only, unledgered): these openings mended {@code delay} ticks from now, the lights round them gone. */
	public static void stageMend(ServerLevel level, LinePlan.Segment segment, List<Integer> holes, int delay) {
		STAGED.add(new Staged(level, segment, List.copyOf(holes), level.getServer().getTickCount() + delay));
	}

	private static void tickStaged(long now) {
		for (Staged s : List.copyOf(STAGED)) {
			if (now < s.at()) {
				continue;
			}
			STAGED.remove(s);
			for (int hole : s.holes()) {
				List<BlockPos> cells = cells(s.level(), s.segment(), hole);
				if (cells.isEmpty()) {
					continue;
				}
				for (BlockPos light : lightsRound(s.level(), s.segment(), hole, cells.getFirst().getY())) {
					s.level().setBlock(light, Blocks.AIR.defaultBlockState(), 3);
				}
				for (BlockPos cell : cells) {
					s.level().setBlock(cell, FILL, 3);
				}
				for (BlockPos cell : stoneRing(s.level(), s.segment(), hole, cells.getFirst().getY())) {
					s.level().setBlock(cell, FILL, 3);
				}
			}
		}
	}

	// --- live ---

	/** A recorded line's segment, from its record's start (the one fixed in this world). */
	private static LinePlan.@Nullable Segment liveSegment(Remains.Place place) {
		Sc2Data.Known known = RemainsImpl.LIVE.record(place).orElse(null);
		return known == null ? null : LineShape.segmentOf(known.start());
	}

	/** Server stop and switch off: the visits go. */
	public static void reset() {
		LIVE.clear();
	}
}
