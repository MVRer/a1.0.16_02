package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.TraceService;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * Grows an "Under you" {@link Network} a few steps per night: straight 2x2 corridors with rare turns under every
 * room, a shaft that stops one block below the bed, and a dead end for the chest. Every step is checked by
 * {@link Tunnels#check} and carved out of view with {@link Tunnels#carve}; nothing is carved within
 * {@code digBelow} blocks of a player dig or build, next to an open space, or near an explored cave.
 */
public final class NetworkGrower {
	public static final String CAUSE = "dig:under_you";
	public static final String CAUSE_CHEST = "dig:under_you/chest";

	/**
	 * Everything a growth step needs.
	 *
	 * @param explored  caves the player explored in this dimension, or null
	 * @param digBelow  blocks kept from player digs ({@code Pacing.digBelow})
	 * @param night     the current night index (for once-per-night retries)
	 * @param playerPos where the subject is, or null (chest sources keep away from it)
	 */
	public record Ctx(ServerLevel level, Network net, @Nullable PosSet explored, DigConfig config, int digBelow, RandomSource random,
			TraceService traces, long night, @Nullable BlockPos playerPos) {
		Tunnels.Rules corridorRules() {
			return Tunnels.Rules.network(digBelow, config.networkExploredClearance, explored);
		}
	}

	private enum Result { CARVED, BLOCKED, DEFERRED }

	private record Move(BlockPos anchor, Direction dir) {
	}

	private NetworkGrower() {
	}

	/** Spends the network's budget, at most {@code maxSteps} steps. Returns the number of steps carved. */
	public static int grow(Ctx ctx, int maxSteps) {
		int carved = 0;
		while (ctx.net().budget > 0 && carved < maxSteps) {
			if (!step(ctx)) {
				break;
			}
			carved++;
		}
		return carved;
	}

	/** One step: founds the network, climbs the shaft, digs the dead end or advances a corridor. False if nothing was carved. */
	public static boolean step(Ctx ctx) {
		Network net = ctx.net();
		if (net.anchors.isEmpty()) {
			return found(ctx);
		}
		if (wantsShaft(ctx)) {
			if (net.shaftFoot != null) {
				Result climbed = climb(ctx);
				if (climbed == Result.CARVED) {
					return true;
				}
				if (climbed == Result.DEFERRED) {
					return false;
				}
				net.shaftStuckNight = ctx.night();
			} else {
				ensureShaftHead(ctx);
			}
		}
		if (wantsAlcove(ctx)) {
			Result alcove = digAlcove(ctx);
			if (alcove == Result.CARVED) {
				return true;
			}
			if (alcove == Result.DEFERRED) {
				return false;
			}
		}
		for (int tries = 0; tries < 8; tries++) {
			if (net.heads.isEmpty() && !spawnHead(ctx)) {
				return false;
			}
			Network.Head head = pickHead(net);
			Result result = advance(ctx, head);
			if (result == Result.CARVED) {
				return true;
			}
			if (result == Result.DEFERRED) {
				return false;
			}
			if (head.toShaft) {
				net.shaftStuckNight = ctx.night();
			}
			net.heads.remove(head);
		}
		return false;
	}

	/**
	 * Updates the bed from the player's respawn point (a bed's head). A moved bed gets a new shaft; the old one stays.
	 */
	public static boolean refreshBed(ServerLevel level, Network net, BlockPos respawn) {
		if (!level.isLoaded(respawn)) {
			return false;
		}
		BlockState state = level.getBlockState(respawn);
		if (!(state.getBlock() instanceof AbstractBedBlock) || respawn.equals(net.bedHead)) {
			return false;
		}
		net.bedHead = respawn.immutable();
		net.bedFoot = respawn.relative(AbstractBedBlock.getConnectedDirection(state)).immutable();
		net.shaftFoot = null;
		net.shaftDone = false;
		net.shaftStuckNight = Long.MIN_VALUE;
		net.shaftSite = -1;
		net.heads.removeIf(head -> head.toShaft);
		return true;
	}

	/** Recomputes the columns under the player's rooms (player-placed blocks near the base, on an 8-block grid). */
	public static void refreshTargets(Ctx ctx) {
		Network net = ctx.net();
		int radius = ctx.config().networkRadius;
		if (!Tunnels.chunksLoaded(ctx.level(), net.base, radius)) {
			return;
		}
		LongOpenHashSet grid = new LongOpenHashSet();
		List<BlockPos> targets = new ArrayList<>();
		for (BlockPos placed : Services.watch().placedNear(ctx.level(), net.base, radius, state -> !state.isAir())) {
			int gx = Math.floorDiv(placed.getX() - net.base.getX(), 8);
			int gz = Math.floorDiv(placed.getZ() - net.base.getZ(), 8);
			if (grid.add(BlockPos.asLong(gx, 0, gz))) {
				BlockPos target = new BlockPos(net.base.getX() + gx * 8 + 4, net.depth, net.base.getZ() + gz * 8 + 4);
				if (inRadius(ctx, target) && !reached(net, target)) {
					targets.add(target);
				}
			}
		}
		targets.sort(Comparator.comparingDouble(t -> t.distSqr(net.base)));
		net.targets.clear();
		net.targets.addAll(targets.subList(0, Math.min(16, targets.size())));
	}

	// --- founding ---

	private static boolean found(Ctx ctx) {
		Network net = ctx.net();
		ServerLevel level = ctx.level();
		PlayerWatch watch = Services.watch();
		int radius = ctx.config().networkRadius;
		if (!Tunnels.chunksLoaded(level, net.base, radius)) {
			return false;
		}
		int lowest = net.base.getY();
		for (BlockPos dug : watch.dugNear(level, net.base, radius)) {
			lowest = Math.min(lowest, dug.getY());
		}
		for (BlockPos placed : watch.placedNear(level, net.base, radius, state -> true)) {
			lowest = Math.min(lowest, placed.getY());
		}
		// Cells are depth and depth+1: digBelow solid blocks between the top cell and the lowest player space.
		int depth = Math.min(net.base.getY() - ctx.config().networkDepthBelowBase, lowest - (ctx.digBelow() + 2));
		depth = Math.max(depth, level.getMinY() + 6);
		Direction off = Direction.Plane.HORIZONTAL.getRandomDirection(ctx.random());
		BlockPos hub = new BlockPos(net.base.getX(), depth, net.base.getZ()).relative(off, 4 + ctx.random().nextInt(5));
		Tunnels.Rules rules = ctx.corridorRules();
		LongOpenHashSet none = new LongOpenHashSet();
		for (int r = 0; r <= 6; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy = 0; dy <= 12; dy += 2) {
						BlockPos anchor = hub.offset(dx, -dy, dz);
						if (anchor.getY() < level.getMinY() + 6 || !inRadius(ctx, anchor) || Tunnels.check(level, anchor, none, rules) != null) {
							continue;
						}
						if (!Tunnels.carve(level, ctx.traces(), List.of(anchor), none, CAUSE)) {
							return false;
						}
						net.depth = anchor.getY();
						net.addAnchor(anchor, false);
						net.budget--;
						Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(ctx.random());
						net.heads.add(new Network.Head(anchor, dir, run(ctx)));
						net.heads.add(new Network.Head(anchor, dir.getOpposite(), run(ctx)));
						refreshTargets(ctx);
						A1016_02.LOGGER.debug("[a1016] dig: network founded at {} under base {}", anchor, net.base);
						return true;
					}
				}
			}
		}
		return false;
	}

	// --- the shaft under the bed ---

	private static boolean wantsShaft(Ctx ctx) {
		Network net = ctx.net();
		return net.bedHead != null && !net.shaftDone && net.nights >= ctx.config().networkShaftAfterNights && net.shaftStuckNight != ctx.night()
				&& shaftTopAnchorY(net) > net.depth;
	}

	/** The (x, z) of the 2x2 column that covers both halves of the bed. */
	static BlockPos shaftColumn(Network net) {
		BlockPos head = net.bedHead;
		BlockPos foot = net.bedFoot != null ? net.bedFoot : head;
		return new BlockPos(Math.min(head.getX(), foot.getX()), net.depth, Math.min(head.getZ(), foot.getZ()));
	}

	/** The top shaft anchor: its upper cell is two below the bed, so exactly one block (the floor) is left between. */
	static int shaftTopAnchorY(Network net) {
		return net.bedHead.getY() - 3;
	}

	private static void ensureShaftHead(Ctx ctx) {
		Network net = ctx.net();
		for (Network.Head head : net.heads) {
			if (head.toShaft) {
				return;
			}
		}
		BlockPos column = shaftColumn(net);
		BlockPos best = null;
		long bestDist = Long.MAX_VALUE;
		for (BlockPos anchor : net.anchors) {
			if (net.shaftAnchors.contains(anchor.asLong())) {
				continue;
			}
			long dx = anchor.getX() - column.getX();
			long dz = anchor.getZ() - column.getZ();
			long dist = dx * dx + dz * dz;
			if (dist < bestDist) {
				bestDist = dist;
				best = anchor;
			}
		}
		if (best == null) {
			return;
		}
		if (bestDist == 0) {
			net.shaftFoot = best;
			return;
		}
		Network.Head head = new Network.Head(best, toward(best, column, Direction.NORTH), 0);
		head.target = column;
		head.toShaft = true;
		net.heads.add(0, head);
	}

	private static Result climb(Ctx ctx) {
		Network net = ctx.net();
		BlockPos top = net.shaftTop().map(BlockPos::below).orElse(net.shaftFoot);
		int topY = shaftTopAnchorY(net);
		if (top.getY() >= topY) {
			finishShaft(ctx);
			return Result.BLOCKED;
		}
		BlockPos next = top.above();
		if (Tunnels.check(ctx.level(), next, net.cells, shaftRules(ctx)) != null) {
			return Result.BLOCKED;
		}
		if (!Tunnels.carve(ctx.level(), ctx.traces(), List.of(next), net.cells, CAUSE)) {
			return Result.DEFERRED;
		}
		net.addAnchor(next, true);
		net.budget--;
		if (next.getY() >= topY) {
			finishShaft(ctx);
		}
		return Result.CARVED;
	}

	/**
	 * The shaft keeps {@code digBelow} from every player dig and build like the corridors. Only the bedroom it stops
	 * under is exempt: digs at the bed's height and above, and the blocks the player placed as the floor under it.
	 */
	static Tunnels.Rules shaftRules(Ctx ctx) {
		int bedY = ctx.net().bedHead.getY();
		PlayerWatch watch = Services.watch();
		ServerLevel level = ctx.level();
		return Tunnels.Rules.shaft(ctx.digBelow(), pos -> pos.getY() >= bedY || pos.getY() == bedY - 1 && !watch.wasDugByPlayer(level, pos));
	}

	private static void finishShaft(Ctx ctx) {
		Network net = ctx.net();
		net.shaftDone = true;
		if (net.shaftSite < 0) {
			BlockPos top = net.shaftTop().orElse(net.shaftFoot);
			int height = top.getY() - net.shaftFoot.getY() + 1;
			net.shaftSite = Services.sites().record(SiteType.TUNNEL_END, net.dimension, top, height).id();
		}
	}

	// --- the dead end with the chest ---

	private static boolean wantsAlcove(Ctx ctx) {
		Network net = ctx.net();
		return net.alcove == null && net.nights >= ctx.config().networkChestAfterNights && net.anchors.size() >= 8 && net.budget >= 2;
	}

	private static Result digAlcove(Ctx ctx) {
		Network net = ctx.net();
		List<BlockPos> corridor = new ArrayList<>();
		for (BlockPos anchor : net.anchors) {
			if (!net.shaftAnchors.contains(anchor.asLong()) && anchor.getY() == net.depth) {
				corridor.add(anchor);
			}
		}
		shuffle(corridor, ctx.random());
		Tunnels.Rules rules = ctx.corridorRules();
		for (BlockPos from : corridor.subList(0, Math.min(24, corridor.size()))) {
			List<Direction> dirs = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
			shuffle(dirs, ctx.random());
			for (Direction dir : dirs) {
				BlockPos a1 = from.relative(dir);
				BlockPos a2 = a1.relative(dir);
				if (net.anchorSet.contains(a1.asLong()) || !inRadius(ctx, a2) || Tunnels.check(ctx.level(), a1, net.cells, rules) != null) {
					continue;
				}
				LongOpenHashSet with = new LongOpenHashSet(net.cells);
				with.addAll(Tunnels.cells(List.of(a1)));
				if (Tunnels.check(ctx.level(), a2, with, rules) != null) {
					continue;
				}
				if (!Tunnels.carve(ctx.level(), ctx.traces(), List.of(a1, a2), net.cells, CAUSE)) {
					return Result.DEFERRED;
				}
				net.addAnchor(a1, false);
				net.addAnchor(a2, false);
				net.budget -= 2;
				net.alcove = (dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? a2.relative(dir) : a2).immutable();
				recordUnderBase(ctx, net.alcove);
				return Result.CARVED;
			}
		}
		return Result.BLOCKED;
	}

	/** Records the UNDER_BASE site once (at the dead end, or the first anchor if there is none yet). */
	static void recordUnderBase(Ctx ctx, BlockPos pos) {
		Network net = ctx.net();
		if (net.underBaseSite >= 0) {
			return;
		}
		int size = net.bounds().map(b -> Math.max(b[1].getX() - b[0].getX(), b[1].getZ() - b[0].getZ()) / 2).orElse(8);
		SiteRegistry.Site site = Services.sites().record(SiteType.UNDER_BASE, net.dimension, pos, Math.max(8, size));
		net.underBaseSite = site.id();
	}

	// --- corridors ---

	private static boolean spawnHead(Ctx ctx) {
		Network net = ctx.net();
		List<BlockPos> corridor = new ArrayList<>();
		for (BlockPos anchor : net.anchors) {
			if (!net.shaftAnchors.contains(anchor.asLong())) {
				corridor.add(anchor);
			}
		}
		if (corridor.isEmpty()) {
			return false;
		}
		Tunnels.Rules rules = ctx.corridorRules();
		for (int tries = 0; tries < 16; tries++) {
			BlockPos from = corridor.get(ctx.random().nextInt(corridor.size()));
			Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(ctx.random());
			BlockPos next = from.relative(dir);
			if (!net.anchorSet.contains(next.asLong()) && inRadius(ctx, next) && Tunnels.check(ctx.level(), next, net.cells, rules) == null) {
				net.heads.add(new Network.Head(from, dir, run(ctx)));
				return true;
			}
		}
		return false;
	}

	private static Network.Head pickHead(Network net) {
		for (Network.Head head : net.heads) {
			if (head.toShaft) {
				return head;
			}
		}
		return net.heads.get(Math.floorMod(net.nextHead++, net.heads.size()));
	}

	private static Result advance(Ctx ctx, Network.Head head) {
		Network net = ctx.net();
		if (head.target == null && head.run <= 0 && !net.targets.isEmpty() && ctx.random().nextFloat() < 0.7F) {
			head.target = nearest(net.targets, head.pos);
		}
		Tunnels.Rules rules = ctx.corridorRules();
		for (Move move : moves(ctx, head)) {
			BlockPos next = move.anchor();
			if (net.anchorSet.contains(next.asLong()) || !inRadius(ctx, next) || Tunnels.check(ctx.level(), next, net.cells, rules) != null) {
				continue;
			}
			if (!Tunnels.carve(ctx.level(), ctx.traces(), List.of(next), net.cells, CAUSE)) {
				return Result.DEFERRED;
			}
			net.addAnchor(next, false);
			net.budget--;
			boolean turned = move.dir() != head.dir;
			if (turned && head.target == null && net.heads.size() < ctx.config().networkMaxHeads && ctx.random().nextDouble() < ctx.config().networkBranchChance) {
				net.heads.add(new Network.Head(head.pos, move.dir().getOpposite(), run(ctx)));
			}
			head.pos = next;
			head.dir = move.dir();
			head.run = turned ? run(ctx) : head.run - 1;
			net.targets.removeIf(t -> Math.abs(t.getX() - next.getX()) <= 2 && Math.abs(t.getZ() - next.getZ()) <= 2);
			if (head.target != null) {
				if (head.toShaft && next.getX() == head.target.getX() && next.getZ() == head.target.getZ()) {
					net.shaftFoot = next;
					net.heads.remove(head);
				} else if (!head.toShaft && Math.abs(head.target.getX() - next.getX()) <= 2 && Math.abs(head.target.getZ() - next.getZ()) <= 2) {
					head.target = null;
				}
			}
			return Result.CARVED;
		}
		return Result.BLOCKED;
	}

	/** Candidate next anchors, best first: toward the target, straight on, or a turn once the run is over. */
	private static List<Move> moves(Ctx ctx, Network.Head head) {
		List<Direction> dirs = new ArrayList<>(4);
		Direction left = head.dir.getCounterClockWise();
		Direction right = head.dir.getClockWise();
		boolean leftFirst = ctx.random().nextBoolean();
		if (head.target != null) {
			int dx = head.target.getX() - head.pos.getX();
			int dz = head.target.getZ() - head.pos.getZ();
			Direction straight = head.dir;
			boolean keep = straight.getStepX() != 0 ? Integer.signum(dx) == straight.getStepX() : Integer.signum(dz) == straight.getStepZ();
			Direction alongX = dx > 0 ? Direction.EAST : Direction.WEST;
			Direction alongZ = dz > 0 ? Direction.SOUTH : Direction.NORTH;
			if (keep) {
				dirs.add(straight);
			}
			if (Math.abs(dx) >= Math.abs(dz)) {
				addIf(dirs, dx != 0, alongX);
				addIf(dirs, dz != 0, alongZ);
			} else {
				addIf(dirs, dz != 0, alongZ);
				addIf(dirs, dx != 0, alongX);
			}
			addIf(dirs, true, leftFirst ? left : right);
			addIf(dirs, true, leftFirst ? right : left);
		} else if (head.run <= 0) {
			dirs.add(leftFirst ? left : right);
			dirs.add(leftFirst ? right : left);
			dirs.add(head.dir);
		} else {
			dirs.add(head.dir);
			dirs.add(leftFirst ? left : right);
			dirs.add(leftFirst ? right : left);
		}
		Network net = ctx.net();
		List<Move> moves = new ArrayList<>();
		for (Direction dir : dirs) {
			BlockPos next = head.pos.relative(dir);
			if (head.pos.getY() < net.depth) {
				moves.add(new Move(next.above(), dir));
			}
			moves.add(new Move(next, dir));
		}
		if (head.pos.getY() > net.depth - 6) {
			for (Direction dir : dirs) {
				moves.add(new Move(head.pos.relative(dir).below(), dir));
			}
		}
		return moves;
	}

	// --- helpers ---

	static boolean inRadius(Ctx ctx, BlockPos anchor) {
		int radius = ctx.config().networkRadius;
		BlockPos base = ctx.net().base;
		return Math.abs(anchor.getX() - base.getX()) <= radius && Math.abs(anchor.getZ() - base.getZ()) <= radius;
	}

	private static boolean reached(Network net, BlockPos target) {
		for (BlockPos anchor : net.anchors) {
			if (Math.abs(anchor.getX() - target.getX()) <= 2 && Math.abs(anchor.getZ() - target.getZ()) <= 2) {
				return true;
			}
		}
		return false;
	}

	private static int run(Ctx ctx) {
		DigConfig config = ctx.config();
		return config.networkRunMin + ctx.random().nextInt(Math.max(1, config.networkRunMax - config.networkRunMin + 1));
	}

	private static Direction toward(BlockPos from, BlockPos to, Direction fallback) {
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		if (dx == 0 && dz == 0) {
			return fallback;
		}
		return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	private static BlockPos nearest(List<BlockPos> targets, BlockPos from) {
		BlockPos best = targets.getFirst();
		for (BlockPos target : targets) {
			if (horizontalDistSqr(target, from) < horizontalDistSqr(best, from)) {
				best = target;
			}
		}
		return best;
	}

	private static long horizontalDistSqr(BlockPos a, BlockPos b) {
		long dx = a.getX() - b.getX();
		long dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	private static void addIf(List<Direction> dirs, boolean condition, Direction dir) {
		if (condition && !dirs.contains(dir)) {
			dirs.add(dir);
		}
	}

	static <T> void shuffle(List<T> list, RandomSource random) {
		for (int i = list.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			T tmp = list.get(i);
			list.set(i, list.get(j));
			list.set(j, tmp);
		}
	}
}
