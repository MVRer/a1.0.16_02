package com.forzacode.a1016_02.world.sc2.s18;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Visits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 18b, the way in closes (docs/SELF_CREATED_2.md, 18b; D-097, D-106), through the kit's {@link Answers} (out of view,
 * ledgered, the fair-danger checks for everyone inside, the caps, the event, the tension): once a player has been inside
 * {@code j} seconds, when everyone inside is at least {@code k} from the inner mouth, no player stands within
 * {@value #MOUTH_CLEAR} of it or of the outer mouth (inside or out), and everyone inside carries a pickaxe and has 10 health or more, four
 * blocks of the inside's wall beside the mouth (2 to 6 to either side, the lowest three cells, stone a wooden pickaxe
 * digs) are moved into the mouth's 2 x 2 ({@code world:s18/close}); their hollows are the clue. Once a place a day,
 * {@code l} a place ever, one a player a day. A pickaxe and a few seconds dig it out.
 *
 * <p>Visits come from the kit's events; a look once a second while anyone is inside one, nothing otherwise. The mouth is
 * read from the place's record (its shape's {@code extra}), so nothing is rebuilt from the terrain. Session only; server
 * thread.
 */
public final class HollowAnswers {
	public static final String CLOSE = S18.CAUSE + "/close";
	/** How often the visits are looked at (ticks). */
	public static final int EVERY_TICKS = 20;
	/** Nobody within this of the inner mouth. */
	public static final int MOUTH_CLEAR = 16;
	/** Everyone inside this healthy. */
	public static final float CLOSE_HEALTH = 10.0F;
	/** A refused ask is asked again after this long (ticks). */
	public static final int CLOSE_RETRY_TICKS = 600;

	/** A place's inner mouth: its lower left cell (looking out), the way out, and the outer mouth (where the land is). */
	public record Mouth(BlockPos lowerLeft, Direction facing, BlockPos outer) {
		/** The 2 x 2, the lower cells first. */
		public List<BlockPos> cells() {
			Direction right = facing.getClockWise();
			return List.of(lowerLeft, lowerLeft.relative(right), lowerLeft.above(), lowerLeft.relative(right).above());
		}

		public Vec3 middle() {
			Direction right = facing.getClockWise();
			return Vec3.atLowerCornerOf(lowerLeft).add(0.5 + right.getStepX() * 0.5, 1.0, 0.5 + right.getStepZ() * 0.5);
		}
	}

	/**
	 * What the answer reads: the kit's answers, who is around, the config, a place's mouth.
	 *
	 * @param people everyone online (spectators included; they are skipped where it matters)
	 */
	public record Env(Answers answers, Visits.People people, Supplier<Sc18Config> config, Function<Remains.Place, @Nullable Mouth> mouths,
			Function<Remains.Place, @Nullable BoundingBox> pieces, java.util.function.ToLongFunction<MinecraftServer> day) {
		/** Without 18c's piece (round 2's tests). */
		public Env(Answers answers, Visits.People people, Supplier<Sc18Config> config, Function<Remains.Place, @Nullable Mouth> mouths) {
			this(answers, people, config, mouths, place -> null, com.forzacode.a1016_02.core.GameClock::day);
		}
	}

	/** One player's visit to one place. */
	static final class Visit {
		final Remains.Place place;
		final UUID player;
		long entered;
		long askAt;
		/** 18c: since when this player stands within {@value HollowAnswers#PIECE_NEAR} of the square (-1: not). */
		long nearSince = -1;
		long darkAskAt;

		Visit(Remains.Place place, UUID player, long entered) {
			this.place = place;
			this.player = player;
			this.entered = entered;
			this.askAt = entered;
		}
	}

	public static final HollowAnswers LIVE = new HollowAnswers(new Env(Answers.LIVE, server -> List.copyOf(server.getPlayerList().getPlayers()),
			Sc18Config::get, HollowAnswers::liveMouth, HollowAnswers::livePiece, com.forzacode.a1016_02.core.GameClock::day));

	private final Env env;
	private final Map<UUID, Visit> visits = new LinkedHashMap<>();
	/** The clock of the last look (a waiting 18b asks it: someone inside must have been in long enough, R12-24). */
	private long lastLook;

	public HollowAnswers(Env env) {
		this.env = env;
	}

	// --- visits ---

	/** A player came inside a place (any kind: only these count). */
	public void entered(Remains.Place place, ServerPlayer player, long now) {
		if (place.kind() == Remains.Kind.HOLLOW) {
			visits.put(player.getUUID(), new Visit(place, player.getUUID(), now));
		}
	}

	/** A player left a place. */
	public void left(Remains.Place place, ServerPlayer player) {
		Visit visit = visits.get(player.getUUID());
		if (visit != null && visit.place.equals(place)) {
			visits.remove(player.getUUID());
		}
	}

	/** Forgets every visit (switch off, server stop). */
	public void clear() {
		visits.clear();
		darkening.clear();
		dark.clear();
	}

	public boolean inside(UUID player) {
		return visits.containsKey(player);
	}

	/** {@code /a1016 sc 18 arm}: this player's visit runs its time out now (every other rule kept). False if not inside one. */
	public boolean arm(ServerPlayer player, long now) {
		Visit visit = visits.get(player.getUUID());
		if (visit == null) {
			return false;
		}
		visit.entered = now - Math.round(env.config().get().closeAfterSeconds * 20);
		visit.askAt = now;
		return true;
	}

	/** What this player's visit has come to, in plain words (for {@code /a1016 sc 18}). */
	public @Nullable String describe(UUID player, long now) {
		Visit visit = visits.get(player);
		if (visit == null) {
			return null;
		}
		String waiting = env.answers().whyWaiting(visit.place, "18b");
		return String.format(Locale.ROOT, "inside %ds%s", (now - visit.entered) / 20, waiting == null ? "" : ", 18b waits: " + waiting);
	}

	/** The live tick: a look every {@value #EVERY_TICKS} ticks while anyone is inside one. */
	public void tick(MinecraftServer server) {
		if (!STAGED.isEmpty()) {
			for (StagedQuarter q : List.copyOf(STAGED)) {
				if (server.getTickCount() >= q.at()) {
					stageQuarter(q.level(), q.piece(), q.quarter());
					STAGED.remove(q);
				}
			}
		}
		if (visits.isEmpty() && darkening.isEmpty() && dark.isEmpty() || server.getTickCount() % EVERY_TICKS != 13 || !SelfCreated.enabled()) {
			return;
		}
		look(server, server.getTickCount());
	}

	/** One look at every visit. */
	public void look(MinecraftServer server, long now) {
		lastLook = now;
		Map<UUID, ServerPlayer> online = new HashMap<>();
		for (ServerPlayer player : env.people().online(server)) {
			online.put(player.getUUID(), player);
		}
		for (Visit visit : List.copyOf(visits.values())) {
			ServerPlayer player = online.get(visit.player);
			if (player == null || player.isSpectator() || !player.isAlive()) {
				continue;
			}
			ServerLevel level = server.getLevel(visit.place.dimension());
			if (level == null || player.level() != level) {
				continue;
			}
			Mouth mouth = env.mouths().apply(visit.place);
			if (mouth != null) {
				close(level, visit, mouth, now);
			}
			darken(level, visit, player, now);
		}
		darkenNext(server, now);
		relight(server);
	}

	// --- 18c ---

	/** 18c's ledger cause. */
	public static final String DARK = S18.CAUSE + "/dark";
	/** 18c: a participant this near the square (blocks, across) counts. */
	public static final int PIECE_NEAR = 48;
	/** 18c: the piece's quarters go this far apart (ticks: 5 s). */
	public static final int QUARTER_TICKS = 100;

	/** A piece going dark: its quarters still to go (the first gone), the next at {@code at}. */
	static final class Darkening {
		final BoundingBox piece;
		int next = 1;
		long at;
		boolean pending;

		Darkening(BoundingBox piece, long at) {
			this.piece = piece;
			this.at = at;
		}
	}

	private final Map<Remains.Place, Darkening> darkening = new LinkedHashMap<>();
	/** The pieces gone dark and the in-game day they went (their lights come back on a later day, nobody inside). */
	private final Map<Remains.Place, Long> dark = new LinkedHashMap<>();

	/** The rules of 18c's first quarter (the rest are its parts). */
	public static Answers.Rules darkRules() {
		return Answers.Rules.of("18c", DARK).perPlaceDay(1).limitSeconds(60);
	}

	/**
	 * 18c (R3-3, D-116): a participant inside within {@value #PIECE_NEAR} of the square for {@code n} seconds: out of
	 * everyone's view, the piece's lights go in four quarters {@value #QUARTER_TICKS} ticks apart ({@code world:s18/dark},
	 * ledgered), once an in-game day a hollow; every answer's rule.
	 */
	private void darken(ServerLevel level, Visit visit, ServerPlayer player, long now) {
		BoundingBox piece = env.pieces().apply(visit.place);
		if (piece == null) {
			return;
		}
		BlockPos square = visit.place.anchor();
		double dx = player.getX() - (square.getX() + 0.5);
		double dz = player.getZ() - (square.getZ() + 0.5);
		if (dx * dx + dz * dz > PIECE_NEAR * PIECE_NEAR) {
			visit.nearSince = -1;
			return;
		}
		if (visit.nearSince < 0) {
			visit.nearSince = now;
		}
		Sc18Config config = env.config().get();
		if (now - visit.nearSince < Math.round(config.pieceDarkAfterSeconds * 20) || now < visit.darkAskAt || darkening.containsKey(visit.place)
				|| dark.containsKey(visit.place)) {
			return;
		}
		Answers.Result result = submitQuarter(level, visit.place, piece, 0, darkRules());
		if (result.status() != Answers.Status.WAITING && result.status() != Answers.Status.DONE) {
			visit.darkAskAt = now + CLOSE_RETRY_TICKS;
		}
	}

	/** Asks for one quarter of this piece's lights to go (the planner finds them again at each try). */
	private Answers.Result submitQuarter(ServerLevel level, Remains.Place place, BoundingBox piece, int quarter, Answers.Rules rules) {
		return env.answers().submit(level, place, rules, (lvl, p, inside) -> {
			List<BlockPos> cells = quarter(lvl, piece, quarter);
			if (cells.isEmpty() || !Answers.loaded(lvl, List.of(new BlockPos(piece.minX(), piece.minY(), piece.minZ()),
					new BlockPos(piece.maxX(), piece.maxY(), piece.maxZ())))) {
				return null;
			}
			return Answers.Edit.of(cells, traces -> {
				TraceBatch batch = traces.batch(lvl, DARK);
				for (BlockPos cell : cells) {
					batch.remove(cell);
				}
				boolean done = batch.commit();
				if (done) {
					Darkening d = darkening.computeIfAbsent(p, k -> new Darkening(piece, lastLook));
					d.next = quarter + 1;
					d.at = lastLook + QUARTER_TICKS;
					d.pending = false;
					dark.put(p, env.day().applyAsLong(lvl.getServer()));
					if (d.next >= 4) {
						darkening.remove(p);
					}
				}
				return done;
			});
		});
	}

	/** The next quarter of every piece going dark, once its time comes. */
	private void darkenNext(MinecraftServer server, long now) {
		for (Map.Entry<Remains.Place, Darkening> e : List.copyOf(darkening.entrySet())) {
			Darkening d = e.getValue();
			if (d.pending || now < d.at) {
				continue;
			}
			ServerLevel level = server.getLevel(e.getKey().dimension());
			if (level == null) {
				darkening.remove(e.getKey());
				continue;
			}
			Answers.Result result = submitQuarter(level, e.getKey(), d.piece, d.next, darkRules().part());
			if (result.status() == Answers.Status.WAITING) {
				d.pending = true;
			} else if (result.status() != Answers.Status.DONE) {
				darkening.remove(e.getKey());
			}
		}
		// A part dropped by the kit (its time ran out) leaves the rest dark as it is.
		for (Map.Entry<Remains.Place, Darkening> e : List.copyOf(darkening.entrySet())) {
			if (e.getValue().pending && !env.answers().waiting(e.getKey(), "18c")) {
				darkening.remove(e.getKey());
			}
		}
	}

	/**
	 * A piece gone dark is lit again on a later in-game day while nobody is inside its hollow: its ledgered lights
	 * taken back out of view (the next visit finds it as it was).
	 */
	private void relight(MinecraftServer server) {
		for (Map.Entry<Remains.Place, Long> e : List.copyOf(dark.entrySet())) {
			Remains.Place place = e.getKey();
			ServerLevel level = server.getLevel(place.dimension());
			if (level == null || darkening.containsKey(place) || env.day().applyAsLong(server) <= e.getValue()) {
				continue;
			}
			boolean someone = false;
			for (Visit visit : visits.values()) {
				someone |= visit.place.equals(place);
			}
			BoundingBox piece = env.pieces().apply(place);
			if (someone || piece == null) {
				continue;
			}
			int left = 0;
			com.forzacode.a1016_02.core.TraceLedger ledger = com.forzacode.a1016_02.core.TraceLedger.get(server);
			for (com.forzacode.a1016_02.core.TraceLedger.Entry entry : List.copyOf(ledger.entries())) {
				if (entry.cause().equals(DARK) && entry.pos().dimension().equals(place.dimension()) && piece.isInside(entry.pos().pos())) {
					com.forzacode.a1016_02.core.TraceService.UndoResult r = com.forzacode.a1016_02.core.Services.traces().undo(level, entry, DARK + "/back");
					left += r == com.forzacode.a1016_02.core.TraceService.UndoResult.IN_VIEW || r == com.forzacode.a1016_02.core.TraceService.UndoResult.UNLOADED ? 1 : 0;
				}
			}
			if (left == 0) {
				dark.remove(place);
			}
		}
	}

	/**
	 * One quarter of the piece's lights (0 to 3: the square's quarters round its middle), found in the box under and
	 * round the piece.
	 */
	public static List<BlockPos> quarter(ServerLevel level, BoundingBox piece, int quarter) {
		// The lights still there, round the square's middle by angle, cut in four even runs: the last of them all goes
		// with the fourth (a piece standing on one side of its square has its lights there).
		List<BlockPos> all = new ArrayList<>();
		double midX = piece.minX() + 8.0;
		double midZ = piece.minZ() + 8.0;
		for (BlockPos pos : BlockPos.betweenClosed(piece.minX(), piece.minY(), piece.minZ(), piece.maxX(), piece.maxY(), piece.maxZ())) {
			if (com.forzacode.a1016_02.world.sc2.kit.Fault.isLight(level.getBlockState(pos))) {
				all.add(pos.immutable());
			}
		}
		all.sort(java.util.Comparator.comparingDouble((BlockPos p) -> Math.atan2(p.getZ() + 0.5 - midZ, p.getX() + 0.5 - midX)).thenComparingLong(BlockPos::asLong));
		int left = 4 - Math.clamp(quarter, 0, 3);
		int take = quarter >= 3 ? all.size() : Math.ceilDiv(all.size(), left);
		return List.copyOf(all.subList(0, Math.min(take, all.size())));
	}

	private record StagedQuarter(ServerLevel level, BoundingBox piece, int quarter, long at) {
	}

	private static final List<StagedQuarter> STAGED = new ArrayList<>();

	/** The gallery's staging of 18c (dev only, unledgered): the piece's quarters going {@code every} ticks apart from now. */
	public static void stageDark(ServerLevel level, BoundingBox piece, int every) {
		long now = level.getServer().getTickCount();
		for (int q = 0; q < 4; q++) {
			STAGED.add(new StagedQuarter(level, piece, q, now + (long) q * every));
		}
	}

	/** The gallery's staging (dev only): this quarter of the piece's lights gone now, unledgered. */
	public static int stageQuarter(ServerLevel level, BoundingBox piece, int quarter) {
		List<BlockPos> cells = quarter(level, piece, quarter);
		for (BlockPos cell : cells) {
			level.setBlock(cell, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		}
		return cells.size();
	}

	/** True while a piece of this place is going dark or dark (tests). */
	public boolean darkened(Remains.Place place) {
		return dark.containsKey(place);
	}

	// --- 18b ---

	/** The rules of 18b with this config. */
	public static Answers.Rules closeRules(Sc18Config config) {
		return Answers.Rules.of("18b", CLOSE).perPlaceEver(config.closePerPlaceMax).health(CLOSE_HEALTH).tool(Answers::pickaxe).limitSeconds(60);
	}

	private void close(ServerLevel level, Visit visit, Mouth mouth, long now) {
		Sc18Config config = env.config().get();
		if (now - visit.entered < Math.round(config.closeAfterSeconds * 20) || now < visit.askAt) {
			return;
		}
		Answers.Result result = env.answers().submit(level, visit.place, closeRules(config), (lvl, place, inside) -> closing(lvl, mouth, inside, config));
		if (result.status() != Answers.Status.WAITING) {
			visit.askAt = now + (result.status() == Answers.Status.DONE ? Long.MAX_VALUE / 2 : CLOSE_RETRY_TICKS);
		}
		if (result.status() == Answers.Status.DONE) {
			A1016_02.LOGGER.debug("[a1016] s2: 18b at {}", mouth.lowerLeft().toShortString());
		}
	}

	/**
	 * 18b's edit now, or null while it must wait: the mouth open, everyone inside far enough from it, nobody near it, four
	 * blocks of the wall beside it to move.
	 */
	Answers.@Nullable Edit closing(ServerLevel level, Mouth mouth, List<ServerPlayer> inside, Sc18Config config) {
		List<BlockPos> cells = mouth.cells();
		// R12-20: never a chunk loaded for it (the walls beside the mouth reach 10 out of it).
		if (!Answers.loaded(level, List.of(mouth.lowerLeft().offset(-10, -1, -10), mouth.lowerLeft().offset(10, 4, 10), mouth.lowerLeft().offset(-10, -1, 10),
				mouth.lowerLeft().offset(10, 4, -10)))) {
			return null;
		}
		for (BlockPos cell : cells) {
			if (!level.getBlockState(cell).isAir()) {
				return null;
			}
		}
		// R12-24: someone inside now has been inside long enough (a newcomer does not inherit a waiting closing).
		long after = Math.round(config.closeAfterSeconds * 20);
		boolean due = false;
		for (ServerPlayer player : inside) {
			Visit visit = visits.get(player.getUUID());
			due |= visit != null && lastLook - visit.entered >= after;
		}
		if (!due || Answers.occupied(level, cells)) {
			// R12-11: never into a cell a pet or anything living stands in.
			return null;
		}
		Vec3 middle = mouth.middle();
		for (ServerPlayer player : inside) {
			if (player.position().distanceTo(middle) < config.closeMinDistance) {
				return null;
			}
		}
		Vec3 outside = Vec3.atBottomCenterOf(mouth.outer());
		for (ServerPlayer player : env.people().online(level.getServer())) {
			if (!player.isSpectator() && player.level() == level
					&& (player.position().distanceTo(middle) < MOUTH_CLEAR || player.position().distanceTo(outside) < MOUTH_CLEAR)) {
				return null;
			}
		}
		List<BlockPos> from = beside(level, mouth);
		if (from.size() < cells.size()) {
			return null;
		}
		List<BlockPos> moved = List.copyOf(from.subList(0, cells.size()));
		List<BlockPos> touched = new ArrayList<>(moved);
		touched.addAll(cells);
		return Answers.Edit.of(touched, traces -> {
			TraceBatch batch = traces.batch(level, CLOSE);
			for (int i = 0; i < cells.size(); i++) {
				batch.move(moved.get(i), cells.get(i));
			}
			return batch.commit();
		});
	}

	/**
	 * The blocks of the inside's wall beside the mouth that 18b may move, nearest first: 2 to 6 to either side of the
	 * tunnel, in the lowest three cells, each the first block a wooden pickaxe digs found going out from the inside's air
	 * (the wall need not be straight).
	 */
	public static List<BlockPos> beside(ServerLevel level, Mouth mouth) {
		Direction right = mouth.facing().getClockWise();
		List<BlockPos> out = new ArrayList<>();
		for (int off = 2; off <= 6; off++) {
			for (int layer = 0; layer <= 2; layer++) {
				for (int side : new int[] {-off, 1 + off}) {
					BlockPos face = face(level, mouth.lowerLeft().relative(right, side).above(layer), mouth.facing());
					// R12-26: never a block a player set there (their wall, their torch's block).
					if (face != null && !out.contains(face) && !com.forzacode.a1016_02.core.Services.watch().wasPlacedByPlayer(level, face)) {
						out.add(face);
					}
				}
			}
		}
		return out;
	}

	/** Along the way out from 4 inside the mouth's plane to 4 past it: the first movable block with the inside's air before it. */
	private static @Nullable BlockPos face(ServerLevel level, BlockPos onPlane, Direction out) {
		BlockState before = level.getBlockState(onPlane.relative(out, -4));
		for (int k = -3; k <= 4; k++) {
			BlockPos cell = onPlane.relative(out, k);
			BlockState state = level.getBlockState(cell);
			if (before.isAir() && movable(state)) {
				return cell;
			}
			before = state;
		}
		return null;
	}

	/** Rock any pickaxe digs: no air, fluid, block entity, falling block or anything that needs more than a wooden pickaxe. */
	public static boolean movable(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && state.is(BlockTags.MINEABLE_WITH_PICKAXE)
				&& !state.is(BlockTags.NEEDS_STONE_TOOL) && !state.is(BlockTags.NEEDS_IRON_TOOL) && !state.is(BlockTags.NEEDS_DIAMOND_TOOL)
				&& !(state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock);
	}

	// --- live ---

	/** A recorded one's piece (version 3): the box its lights hang in, from its record's extra; null without one. */
	private static @Nullable BoundingBox livePiece(Remains.Place place) {
		Sc2Data.Known known = RemainsImpl.LIVE.record(place).orElse(null);
		return known == null ? null : HollowShape.pieceOf(known.extra()).orElse(null);
	}

	/** A recorded one's mouth, from its record's extra (never the terrain). */
	private static @Nullable Mouth liveMouth(Remains.Place place) {
		Sc2Data.Known known = RemainsImpl.LIVE.record(place).orElse(null);
		if (known == null) {
			return null;
		}
		return HollowShape.innerMouthOf(known.extra()).map(pos -> new Mouth(pos, place.entranceFacing(), place.entrance())).orElse(null);
	}

	/** Server stop and switch off: the visits go. */
	public static void reset() {
		LIVE.clear();
	}
}
