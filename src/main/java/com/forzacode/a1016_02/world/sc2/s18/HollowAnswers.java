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
	public record Env(Answers answers, Visits.People people, Supplier<Sc18Config> config, Function<Remains.Place, @Nullable Mouth> mouths) {
	}

	/** One player's visit to one place. */
	static final class Visit {
		final Remains.Place place;
		final UUID player;
		long entered;
		long askAt;

		Visit(Remains.Place place, UUID player, long entered) {
			this.place = place;
			this.player = player;
			this.entered = entered;
			this.askAt = entered;
		}
	}

	public static final HollowAnswers LIVE = new HollowAnswers(new Env(Answers.LIVE, server -> List.copyOf(server.getPlayerList().getPlayers()),
			Sc18Config::get, HollowAnswers::liveMouth));

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
		if (visits.isEmpty() || server.getTickCount() % EVERY_TICKS != 13 || !SelfCreated.enabled()) {
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
		}
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
