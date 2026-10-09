package com.forzacode.a1016_02.ending.d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.lore.LoreData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Step 4, under the seed: the team's stair to bedrock under the F07 pyramid. If it is not there it is built early,
 * as soon as F28 is read, as something left by others ({@value #CAUSE}, never undone), out of view, a few levels per
 * batch: a spiral stair one block wide round a stone pillar whose axis runs from the seed sign down to F30's twin,
 * into a small bedrock chamber around the twin (lore places the twin; this only builds around it). Then lore is asked
 * to put F25 at the bottom if it has not placed it elsewhere (a STAIR_BOTTOM site plus {@code FragmentService.place}).
 *
 * <p>Its dangers: the stairwell floods (a block between the sea and the player's hole is taken, step 4) and the stair
 * loses blocks while they are in the chamber, so the way out shrinks (steps 5 to 7, {@value #LOSS_CAUSE}).
 */
public final class Stair {
	/** The team's build: never undone. */
	public static final String CAUSE = "ending:d/left/stair";
	/** The stair losing blocks: put back in the last minute. */
	public static final String LOSS_CAUSE = "ending:d/stair";
	public static final String FLOOD_CAUSE = "ending:d/flood";
	/** The twin's anchor in lore's data. */
	public static final String TWIN_ANCHOR = "F30/bedrock";

	/** The spiral's eight cells round the axis, in walking order (each one orthogonally next to the one before). */
	static final int[][] RING = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
	static final TicketType TICKET = new TicketType(600L, TicketType.FLAG_LOADING);

	/** Why a build attempt stopped. */
	public enum Status { DONE, BUILT_SEGMENT, WAITING_FOR_LORE, WAITING_FOR_CHUNKS, IN_VIEW, BLOCKED }

	public record Attempt(Status status, String detail) {
	}

	private static int segmentLevels = -1;

	private Stair() {
	}

	// --- where things are ---

	/** The F07 seed sign. */
	public static Optional<GlobalPos> seed(MinecraftServer server) {
		return Optional.ofNullable(HerobrineState.get(server).fragmentsPlaced().get("F07"));
	}

	/** F30's twin on bedrock, once lore placed it. */
	public static Optional<GlobalPos> twin(MinecraftServer server) {
		return LoreData.get(server).anchor(TWIN_ANCHOR);
	}

	/**
	 * The highest bedrock in this column at or under {@code from} (searched a few blocks down; the bedrock floor
	 * varies by a few blocks), or the lowest searched level minus one if there is none.
	 */
	static int bedrockTop(ServerLevel level, int x, int z, int from) {
		int bottom = Math.max(level.getMinY(), from - 14);
		for (int y = from; y >= bottom; y--) {
			if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.BEDROCK)) {
				return y;
			}
		}
		return bottom - 1;
	}

	static BlockState wall(int y) {
		return (y < 0 ? Blocks.COBBLED_DEEPSLATE : Blocks.COBBLESTONE).defaultBlockState();
	}

	static BlockState step(int y, Direction up) {
		return (y < 0 ? Blocks.COBBLED_DEEPSLATE_STAIRS : Blocks.COBBLESTONE_STAIRS).defaultBlockState().setValue(StairBlock.FACING, up);
	}

	static boolean falls(BlockState state) {
		return state.getBlock() instanceof Fallable;
	}

	// --- planning ---

	/**
	 * Plans the stair from the seed sign down to the twin, or explains why not. The top is just under the first
	 * block below the sign that is solid and does not fall (the sea floor's sand and gravel stay on top of it).
	 */
	public static Optional<StairPlan> plan(ServerLevel level, BlockPos core, BlockPos twin, EndingDConfig cfg) {
		int ax = twin.getX();
		int az = twin.getZ();
		int chamberCeil = twin.getY() + cfg.chamberHeadroom + 1;
		int from = twin.getY() + 4;
		Integer ceiling = null;
		for (int y = core.getY() - 1; y > chamberCeil + 12; y--) {
			BlockPos pos = new BlockPos(ax, y, az);
			BlockState state = level.getBlockState(pos);
			if (!state.canBeReplaced() && !falls(state) && state.getFluidState().isEmpty() && level.getBlockEntity(pos) == null) {
				ceiling = y;
				break;
			}
		}
		if (ceiling == null) {
			return Optional.empty();
		}
		int yTop = ceiling - 1;
		int s0 = yTop - 3;
		if (s0 - 8 <= chamberCeil) {
			return Optional.empty();
		}
		List<BlockPos> stairs = new ArrayList<>();
		for (int i = 0; ; i++) {
			int[] r = RING[i % RING.length];
			int x = ax + r[0];
			int z = az + r[1];
			int s = s0 - i;
			if (s <= Math.max(twin.getY() - 6, bedrockTop(level, x, z, from))) {
				break;
			}
			stairs.add(new BlockPos(x, s, z));
		}
		return Optional.of(new StairPlan(level.dimension(), core.immutable(), twin.immutable(), yTop, s0, chamberCeil, cfg.chamberHalfWidth, stairs,
				Integer.MAX_VALUE, false));
	}

	/** Every cell the build empties or fills with a stair: the open top, the spiral, under the pillar, the chamber. */
	static Set<BlockPos> carved(ServerLevel level, StairPlan plan) {
		Set<BlockPos> cells = new LinkedHashSet<>();
		int ax = plan.axisX();
		int az = plan.axisZ();
		int from = plan.bedrockSearchTop();
		int floor = plan.chamberBox().minY();
		for (int y = plan.yTop(); y > plan.s0(); y--) {
			cells.add(new BlockPos(ax, y, az));
		}
		for (int k = 0; k < RING.length; k++) {
			int x = ax + RING[k][0];
			int z = az + RING[k][1];
			int top = Math.min(plan.yTop(), plan.s0() - k + 3);
			for (int y = top; y > Math.max(floor, bedrockTop(level, x, z, from)); y--) {
				cells.add(new BlockPos(x, y, z));
			}
		}
		int h = plan.half();
		for (int x = ax - h; x <= ax + h; x++) {
			for (int z = az - h; z <= az + h; z++) {
				for (int y = plan.chamberCeil() - 1; y > Math.max(floor, bedrockTop(level, x, z, from)); y--) {
					cells.add(new BlockPos(x, y, z));
				}
			}
		}
		cells.remove(plan.twin());
		return cells;
	}

	/** Which way each stair faces (its high side): toward the stair above it, the first one toward the pillar's top. */
	static Direction facing(StairPlan plan, int index) {
		BlockPos at = plan.stairs().get(index);
		BlockPos toward = index == 0 ? new BlockPos(plan.axisX(), at.getY(), plan.axisZ()) : plan.stairs().get(index - 1);
		return Direction.getApproximateNearest(toward.getX() - at.getX(), 0, toward.getZ() - at.getZ());
	}

	static int bottom(Set<BlockPos> cells) {
		return cells.stream().mapToInt(BlockPos::getY).min().orElse(0);
	}

	// --- building ---

	/** True if every chunk under the stair and chamber is loaded; otherwise asks for the missing ones. */
	static boolean chunksReady(ServerLevel level, int ax, int az, int half) {
		boolean ready = true;
		int r = half + 2;
		for (int cx = (ax - r) >> 4; cx <= (ax + r) >> 4; cx++) {
			for (int cz = (az - r) >> 4; cz <= (az + r) >> 4; cz++) {
				if (level.getChunkSource().getChunkNow(cx, cz) == null) {
					ready = false;
					level.getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(cx, cz), 0);
				}
			}
		}
		return ready;
	}

	/**
	 * One build attempt: plans the stair if there is none yet, then builds the next segment of levels (top down, the
	 * chamber last), each as one all-or-nothing batch. A segment that is refused (in view, or too many plants and
	 * vines on the walls) is tried again later, smaller.
	 */
	public static Attempt build(MinecraftServer server, EndingDState data, TraceService traces, EndingDConfig cfg) {
		Optional<StairPlan> known = data.stair();
		if (known.isPresent() && known.get().complete()) {
			return new Attempt(Status.DONE, "built");
		}
		Optional<GlobalPos> seed = seed(server);
		Optional<GlobalPos> twin = twin(server);
		if (seed.isEmpty() || twin.isEmpty()) {
			return new Attempt(Status.WAITING_FOR_LORE, seed.isEmpty() ? "F07 is not placed yet" : "F30's twin is not on bedrock yet");
		}
		ServerLevel level = server.getLevel(twin.get().dimension());
		if (level == null) {
			return new Attempt(Status.BLOCKED, "no level " + twin.get().dimension());
		}
		BlockPos twinPos = twin.get().pos();
		if (!chunksReady(level, twinPos.getX(), twinPos.getZ(), cfg.chamberHalfWidth)) {
			return new Attempt(Status.WAITING_FOR_CHUNKS, "loading the chunks under the pyramid");
		}
		StairPlan plan;
		if (known.isPresent()) {
			plan = known.get();
		} else {
			Optional<StairPlan> planned = plan(level, seed.get().pos(), twinPos, cfg);
			if (planned.isEmpty()) {
				return new Attempt(Status.BLOCKED, "no solid ground under the pyramid to start the stair from");
			}
			plan = planned.get();
			data.setStair(plan);
		}
		Attempt attempt = buildSegment(level, data, plan, traces, cfg);
		if (attempt.status() == Status.DONE) {
			data.stair().ifPresent(done -> recordSite(level, done));
		}
		return attempt;
	}

	/** Builds the next segment of this plan (or finishes it). */
	public static Attempt buildSegment(ServerLevel level, EndingDState data, StairPlan plan, TraceService traces, EndingDConfig cfg) {
		Set<BlockPos> cells = carved(level, plan);
		int low = bottom(cells);
		if (segmentLevels <= 0) {
			segmentLevels = Math.max(1, cfg.stairSegmentLevels);
		}
		int hi = Math.min(plan.builtTo() - 1, plan.yTop());
		if (hi < low) {
			finish(level, data, plan);
			return new Attempt(Status.DONE, "built");
		}
		int lo = Math.max(low, hi - segmentLevels + 1);
		Segment segment = segment(level, plan, cells, lo, hi);
		if (segment.blocked() != null) {
			return new Attempt(Status.BLOCKED, segment.blocked());
		}
		if (!segment.ops().isEmpty() && !segment.apply(traces.batch(level, CAUSE))) {
			segmentLevels = Math.max(1, segmentLevels / 2);
			return new Attempt(Status.IN_VIEW, "levels " + lo + " to " + hi + " were refused (in view, or too much grows on the walls)");
		}
		segmentLevels = Math.max(1, cfg.stairSegmentLevels);
		StairPlan built = plan.withBuiltTo(lo);
		data.setStair(built);
		if (lo <= low) {
			finish(level, data, built);
			return new Attempt(Status.DONE, "built");
		}
		return new Attempt(Status.BUILT_SEGMENT, "built down to " + lo);
	}

	/** One segment's edits. */
	record Segment(List<Op> ops, String blocked) {
		boolean apply(TraceBatch batch) {
			for (Op op : ops) {
				if (op.leave()) {
					batch.leave(op.pos(), op.state());
				} else {
					batch.convert(op.pos(), op.state());
				}
			}
			return batch.commit();
		}
	}

	record Op(BlockPos pos, BlockState state, boolean leave) {
	}

	/**
	 * The edits for levels {@code lo..hi}: each carved cell emptied (fluid too) or turned into its stair; and its
	 * walls made whole, so no sea, lava or cave gets in: air and fluid beside it filled with stone, sand or gravel over
	 * it turned to stone so nothing falls in.
	 */
	static Segment segment(ServerLevel level, StairPlan plan, Set<BlockPos> cells, int lo, int hi) {
		List<Op> ops = new ArrayList<>();
		Set<BlockPos> walled = new HashSet<>();
		List<BlockPos> stairs = plan.stairs();
		for (BlockPos cell : cells) {
			if (cell.getY() < lo || cell.getY() > hi) {
				continue;
			}
			BlockState state = level.getBlockState(cell);
			if (level.getBlockEntity(cell) != null) {
				return new Segment(List.of(), "a block entity at " + cell.toShortString() + " is in the way");
			}
			int index = stairs.indexOf(cell);
			BlockState want = index >= 0 ? step(cell.getY(), facing(plan, index)) : Blocks.AIR.defaultBlockState();
			if (state != want) {
				ops.add(new Op(cell, want, false));
			}
			for (Direction dir : Direction.values()) {
				BlockPos n = cell.relative(dir);
				if (cells.contains(n) || n.equals(plan.twin()) || !walled.add(n)) {
					continue;
				}
				BlockState ns = level.getBlockState(n);
				if (level.getBlockEntity(n) != null || ns.is(Blocks.BEDROCK)) {
					continue;
				}
				if (ns.canBeReplaced()) {
					// Air, water, lava, plants: filled (left), so nothing gets in.
					ops.add(new Op(n, wall(n.getY()), true));
				} else if (!ns.getFluidState().isEmpty() || dir == Direction.UP && falls(ns)) {
					// A waterlogged block, or sand and gravel over the shaft: turned to stone.
					ops.add(new Op(n, wall(n.getY()), false));
				}
			}
		}
		return new Segment(ops, null);
	}

	/** The stair is done: a STAIR_BOTTOM site at the chamber, for lore's F25. */
	private static void finish(ServerLevel level, EndingDState data, StairPlan plan) {
		data.setStair(plan.completed());
		A1016_02.LOGGER.info("[a1016] ending d: the team's stair goes down to bedrock under the seed at {} ({} steps)", plan.twin().toShortString(),
				plan.stairs().size());
	}

	/** The finished stair's bottom as a STAIR_BOTTOM site (once), so lore can put F25 there. */
	static void recordSite(ServerLevel level, StairPlan plan) {
		BlockPos corner = new BlockPos(plan.axisX() + plan.half(), bedrockTop(level, plan.axisX() + plan.half(), plan.axisZ() + plan.half(),
				plan.bedrockSearchTop()) + 1, plan.axisZ() + plan.half());
		if (Services.sites().find(SiteType.STAIR_BOTTOM, GlobalPos.of(level.dimension(), corner), 4).isEmpty()) {
			Services.sites().record(SiteType.STAIR_BOTTOM, level.dimension(), corner, 2);
		}
	}

	/** F25 at the bottom if lore has not placed it elsewhere: asks lore to place it near the chamber (once it can). */
	static void offerF25(MinecraftServer server, StairPlan plan) {
		if (!plan.complete() || HerobrineState.get(server).fragmentsPlaced().containsKey("F25")) {
			return;
		}
		ServerLevel level = server.getLevel(plan.dimension());
		if (level == null) {
			return;
		}
		List<SiteRegistry.Site> sites = Services.sites().find(SiteType.STAIR_BOTTOM, GlobalPos.of(level.dimension(), plan.twin()), plan.half() + 4);
		if (sites.isEmpty()) {
			recordSite(level, plan);
			sites = Services.sites().find(SiteType.STAIR_BOTTOM, GlobalPos.of(level.dimension(), plan.twin()), plan.half() + 4);
		}
		BlockPos hint = sites.isEmpty() ? plan.twin() : sites.getFirst().pos();
		Services.fragments().place("F25", level, hint);
	}

	// --- dangers ---

	/** True if the player is in the shaft, at least {@code depth} blocks under its top and above the chamber. */
	static boolean inShaftBelow(ServerPlayer player, StairPlan plan, int depth) {
		BlockPos at = player.blockPosition();
		return Math.abs(at.getX() - plan.axisX()) <= 1 && Math.abs(at.getZ() - plan.axisZ()) <= 1 && at.getY() <= plan.yTop() - depth
				&& at.getY() >= plan.chamberCeil();
	}

	/**
	 * Step 4's danger: the stairwell floods. Once the player is well down the stair, one block between the open sea
	 * and the hole they dug through the pyramid's floor is taken, out of view, and the sea comes down after them (the
	 * way one on the list drowned). Deniable: the sea got in. The clue: one block missing from the pyramid's side.
	 */
	public static boolean flood(ServerPlayer player, StairPlan plan, View view) {
		ServerLevel level = player.level();
		Set<BlockPos> hole = hole(level, plan);
		if (hole.isEmpty()) {
			return false;
		}
		List<BlockPos> barriers = new ArrayList<>();
		for (BlockPos cell : hole) {
			for (Direction dir : Direction.values()) {
				BlockPos n = cell.relative(dir);
				if (hole.contains(n) || barriers.contains(n)) {
					continue;
				}
				BlockState state = level.getBlockState(n);
				if (state.isAir() || !state.getFluidState().isEmpty() || state.is(Blocks.BEDROCK) || level.getBlockEntity(n) != null
						|| n.getY() <= plan.yTop()) {
					continue;
				}
				for (Direction side : Direction.values()) {
					BlockPos sea = n.relative(side);
					if (!hole.contains(sea) && level.getFluidState(sea).is(Fluids.WATER) && level.getFluidState(sea).isSource()) {
						barriers.add(n.immutable());
						break;
					}
				}
			}
		}
		barriers.sort(Comparator.comparingInt((BlockPos p) -> -p.getY()));
		for (BlockPos barrier : barriers) {
			if (view.outOfView(level, barrier) && Services.traces().remove(level, barrier, FLOOD_CAUSE)) {
				A1016_02.LOGGER.info("[a1016] ending d: the sea gets into the stairwell at {}", barrier.toShortString());
				return true;
			}
		}
		return false;
	}

	/**
	 * The hole the player dug from the shaft's open top up through the pyramid's floor: the open cells (air or water)
	 * above the shaft, within the pyramid's reach, below the sea's surface. Empty if they did not dig through.
	 */
	static Set<BlockPos> hole(ServerLevel level, StairPlan plan) {
		Set<BlockPos> found = new LinkedHashSet<>();
		BlockPos start = new BlockPos(plan.axisX(), plan.yTop() + 1, plan.axisZ());
		Deque<BlockPos> queue = new ArrayDeque<>();
		int top = Math.max(level.getSeaLevel(), plan.core().getY() + 4);
		BlockState first = level.getBlockState(start);
		if (!first.isAir() && first.getFluidState().isEmpty()) {
			return found;
		}
		queue.add(start);
		found.add(start);
		while (!queue.isEmpty() && found.size() < 256) {
			BlockPos cell = queue.poll();
			for (Direction dir : Direction.values()) {
				BlockPos n = cell.relative(dir);
				if (found.contains(n) || n.getY() <= plan.yTop() || n.getY() > top || Math.abs(n.getX() - plan.axisX()) > 4
						|| Math.abs(n.getZ() - plan.axisZ()) > 4) {
					continue;
				}
				BlockState state = level.getBlockState(n);
				// The sea itself is not the hole: only cells that are not still sea water (sources open to the sea).
				if (state.isAir() || !state.getFluidState().isEmpty() && !state.getFluidState().isSource()) {
					found.add(n);
					queue.add(n);
				}
			}
		}
		return found;
	}

	/**
	 * Steps 5 to 7: the stair above the chamber loses one block, out of view: one stair (lowest first), or once, one
	 * of the player's torches on the stairwell. Ledgered as {@value #LOSS_CAUSE}; the last minute puts them back.
	 */
	public static boolean loseBlock(ServerPlayer player, EndingDState data, StairPlan plan, View view) {
		ServerLevel level = player.level();
		if (!data.has(EndingDState.STAIR_TORCH)) {
			for (BlockPos torch : stairwellTorches(level, plan)) {
				if (view.outOfView(level, torch) && Services.traces().remove(level, torch, LOSS_CAUSE)) {
					data.set(EndingDState.STAIR_TORCH, true);
					return true;
				}
			}
		}
		List<BlockPos> remaining = new ArrayList<>();
		for (BlockPos stair : plan.stairs()) {
			if (stair.getY() >= plan.chamberCeil() && level.getBlockState(stair).getBlock() instanceof StairBlock) {
				remaining.add(stair);
			}
		}
		remaining.sort(Comparator.comparingInt(BlockPos::getY));
		for (BlockPos stair : remaining) {
			if (view.outOfView(level, stair) && Services.traces().remove(level, stair, LOSS_CAUSE)) {
				return true;
			}
		}
		return false;
	}

	/** Torches a player put up in the stairwell (the shaft and its walls, above the chamber). */
	static List<BlockPos> stairwellTorches(ServerLevel level, StairPlan plan) {
		List<BlockPos> torches = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(plan.axisX() - 2, plan.chamberCeil(), plan.axisZ() - 2, plan.axisX() + 2, plan.yTop(), plan.axisZ() + 2)) {
			if (level.getBlockState(pos).getBlock() instanceof BaseTorchBlock && Services.watch().wasPlacedByPlayer(level, pos)) {
				torches.add(pos.immutable());
			}
		}
		return torches;
	}

	/** Stair blocks still standing above the chamber. */
	static int standing(ServerLevel level, StairPlan plan) {
		int count = 0;
		for (BlockPos stair : plan.stairs()) {
			if (stair.getY() >= plan.chamberCeil() && level.getBlockState(stair).getBlock() instanceof StairBlock) {
				count++;
			}
		}
		return count;
	}

	static void clear() {
		segmentLevels = -1;
	}
}
