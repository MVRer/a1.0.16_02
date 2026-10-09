package com.forzacode.a1016_02.lore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Plans for the minimal places lore builds itself when world or dig recorded none: tunnels, pyramids, bare
 * groves, crosses, huts, houses, towers, the F15 test room. Planning only reads the world; the caller adds the
 * plan to a {@link Build} and commits it (all or nothing, out of view). Pyramids and crosses are moved material,
 * never new blocks; tunnels and bare trees are removals; huts, houses and towers were left by others.
 */
final class Builders {
	private Builders() {
	}

	// --- tunnels (his pass: 2x2, straight) ---

	/**
	 * @param cells every block to carve
	 * @param end   the dead end's lower cell (on the floor, at the far end)
	 * @param dir   the direction the tunnel runs toward the dead end
	 */
	record Tunnel(List<BlockPos> cells, BlockPos end, Direction dir, int length) {
		/** The upper cell at the dead end, against the stone face. */
		BlockPos endUpper() {
			return end.above();
		}

		/** The stone face block behind the upper dead-end cell. */
		BlockPos face() {
			return endUpper().relative(dir);
		}
	}

	/** A 2x2 tunnel from {@code start} running {@code length} blocks along {@code dir}. Most of it must be natural rock. */
	static Optional<Tunnel> planTunnel(ServerLevel level, BlockPos start, Direction dir, int length) {
		Direction side = dir.getClockWise();
		List<BlockPos> cells = new ArrayList<>(length * 4);
		int open = 0;
		for (int k = 0; k < length; k++) {
			for (int w = 0; w < 2; w++) {
				for (int h = 0; h < 2; h++) {
					BlockPos cell = start.relative(dir, k).relative(side, w).above(h);
					BlockState state = level.getBlockState(cell);
					if (state.isAir()) {
						open++;
					} else if (!Terrain.isNaturalSolid(state)) {
						return Optional.empty();
					}
					if (Terrain.touchesFluid(level, cell) || h == 1 && level.getBlockState(cell.above()).getBlock() instanceof FallingBlock) {
						return Optional.empty();
					}
					cells.add(cell);
				}
			}
		}
		if (open > cells.size() / 4) {
			return Optional.empty();
		}
		BlockPos end = start.relative(dir, length - 1);
		if (!Terrain.isNaturalSolid(level.getBlockState(end.above().relative(dir))) || !level.getBlockState(end.below()).isSolidRender()) {
			return Optional.empty();
		}
		return Optional.of(new Tunnel(cells, end, dir, length));
	}

	/** Whether the chunks under a box are loaded (and, if not, asks for them): planning reads nothing else. */
	@FunctionalInterface
	interface Area {
		boolean ready(BlockPos a, BlockPos b);
	}

	/**
	 * A tunnel near this column: from a cave wall if there is a cave below, otherwise sealed in the rock. Stops
	 * (empty) as soon as an area it needs is not loaded yet.
	 */
	static Optional<Tunnel> findTunnel(ServerLevel level, BlockPos column, int length, RandomSource random, Area area) {
		if (!area.ready(column, column)) {
			return Optional.empty();
		}
		List<Direction> dirs = Terrain.shuffledHorizontal(random);
		Optional<BlockPos> cave = Terrain.caveFloor(level, column.getX(), column.getZ(), 40, -40);
		int y = Math.max(-40, Math.min(40, Terrain.ground(level, column.getX(), column.getZ()).getY() - 20));
		List<BlockPos> starts = new ArrayList<>();
		cave.ifPresent(floor -> dirs.forEach(dir -> starts.add(floor.relative(dir))));
		dirs.forEach(dir -> starts.add(new BlockPos(column.getX(), y, column.getZ()).relative(dir, 0)));
		for (int n = 0; n < starts.size(); n++) {
			Direction dir = dirs.get(n % dirs.size());
			BlockPos start = starts.get(n);
			if (!area.ready(start, start.relative(dir, length).relative(dir.getClockWise(), 1))) {
				return Optional.empty();
			}
			Optional<Tunnel> tunnel = planTunnel(level, start, dir, length);
			if (tunnel.isPresent()) {
				return tunnel;
			}
		}
		return Optional.empty();
	}

	static void carve(Build build, Tunnel tunnel) {
		for (BlockPos cell : tunnel.cells()) {
			build.remove(cell);
		}
	}

	// --- ocean pyramids (moved sand, 5x5 / 3x3 / 1x1, a 1x1 core) ---

	record Move(BlockPos from, BlockPos to) {
	}

	/** @param core the center of the middle layer; fill it in the same build (the top sand rests on it) */
	record Pyramid(List<Move> moves, BlockPos core) {
	}

	/** A small sand pyramid on a flat sea floor at this column, built from sea-floor sand nearby. */
	static Optional<Pyramid> planPyramid(ServerLevel level, BlockPos column) {
		int depth = Terrain.waterDepth(level, column.getX(), column.getZ());
		if (depth < 2 || depth > 12) {
			return Optional.empty();
		}
		int floorY = Terrain.seaFloor(level, column.getX(), column.getZ()).getY();
		List<BlockPos> targets = new ArrayList<>();
		for (int layer = 0; layer < 3; layer++) {
			int half = 2 - layer;
			for (int dx = -half; dx <= half; dx++) {
				for (int dz = -half; dz <= half; dz++) {
					BlockPos target = new BlockPos(column.getX() + dx, floorY + 1 + layer, column.getZ() + dz);
					if (layer == 1 && dx == 0 && dz == 0) {
						continue; // the core
					}
					BlockState state = level.getBlockState(target);
					if (!state.canBeReplaced() || state.hasBlockEntity()) {
						return Optional.empty();
					}
					if (layer == 0 && !level.getBlockState(target.below()).isSolidRender()) {
						return Optional.empty();
					}
					targets.add(target);
				}
			}
		}
		Deque<BlockPos> sources = new ArrayDeque<>();
		Set<BlockPos> used = new HashSet<>();
		for (int r = 4; r <= 12 && sources.size() < targets.size(); r++) {
			for (int dx = -r; dx <= r && sources.size() < targets.size(); dx++) {
				for (int dz = -r; dz <= r && sources.size() < targets.size(); dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					BlockPos top = Terrain.seaFloor(level, column.getX() + dx, column.getZ() + dz);
					if (!level.getBlockState(top.above()).is(Blocks.WATER)) {
						continue;
					}
					for (int down = 0; down < 3 && sources.size() < targets.size(); down++) {
						BlockPos source = top.below(down);
						if (!level.getBlockState(source).is(Blocks.SAND) || !used.add(source)) {
							break;
						}
						sources.add(source);
					}
				}
			}
		}
		if (sources.size() < targets.size()) {
			return Optional.empty();
		}
		List<Move> moves = new ArrayList<>();
		for (BlockPos target : targets) {
			moves.add(new Move(sources.poll(), target));
		}
		return Optional.of(new Pyramid(moves, new BlockPos(column.getX(), floorY + 2, column.getZ())));
	}

	static void raise(Build build, Pyramid pyramid) {
		for (Move move : pyramid.moves()) {
			build.move(move.from(), move.to());
		}
	}

	// --- trees and groves ---

	/** The lowest log of a tree whose trunk tops this column, if the column is a tree. */
	static Optional<BlockPos> trunkBase(ServerLevel level, int x, int z) {
		BlockPos top = Terrain.ground(level, x, z);
		if (!level.getBlockState(top).is(BlockTags.LOGS)) {
			return Optional.empty();
		}
		BlockPos base = top;
		while (level.getBlockState(base.below()).is(BlockTags.LOGS)) {
			base = base.below();
		}
		return Terrain.isSoil(level.getBlockState(base.below())) ? Optional.of(base) : Optional.empty();
	}

	/** Tree trunk bases within {@code radius} (horizontal) of {@code center}, nearest first. */
	static List<BlockPos> trunks(ServerLevel level, BlockPos center, int radius) {
		List<BlockPos> found = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				trunkBase(level, center.getX() + dx, center.getZ() + dz).ifPresent(found::add);
			}
		}
		found.sort((a, b) -> Double.compare(horizontalDistSqr(a, center), horizontalDistSqr(b, center)));
		return found;
	}

	/** The leaves of the tree standing on {@code base} (connected to its trunk), at most {@code max}. */
	static Set<BlockPos> leavesOf(ServerLevel level, BlockPos base, int max) {
		Set<BlockPos> leaves = new LinkedHashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		BlockPos log = base;
		while (level.getBlockState(log).is(BlockTags.LOGS)) {
			queue.add(log);
			log = log.above();
		}
		Set<BlockPos> seen = new HashSet<>(queue);
		while (!queue.isEmpty() && leaves.size() < max) {
			BlockPos pos = queue.poll();
			for (Direction dir : Direction.values()) {
				BlockPos next = pos.relative(dir);
				if (Math.abs(next.getX() - base.getX()) > 5 || Math.abs(next.getZ() - base.getZ()) > 5 || !seen.add(next)) {
					continue;
				}
				if (level.getBlockState(next).is(BlockTags.LEAVES)) {
					leaves.add(next.immutable());
					queue.add(next);
				}
			}
		}
		return leaves;
	}

	/** Where to bury something under a trunk: two blocks under its lowest log, in natural ground. */
	static Optional<BlockPos> burialUnder(ServerLevel level, BlockPos trunkBase) {
		BlockPos spot = trunkBase.below(2);
		return Terrain.isNaturalSolid(level.getBlockState(spot)) && !Terrain.touchesFluid(level, spot) ? Optional.of(spot) : Optional.empty();
	}

	// --- crosses (moved ground) ---

	/**
	 * A cross on the ground at {@code base} (post 3 high, arms on the middle block), from ground taken nearby but
	 * never from the columns {@code avoid} accepts (a house's footprint, a trunk).
	 */
	static Optional<List<Move>> planCross(ServerLevel level, BlockPos base, Direction arms, Predicate<BlockPos> avoid) {
		List<BlockPos> targets = List.of(base, base.above(), base.above(2), base.above().relative(arms), base.above().relative(arms.getOpposite()));
		for (BlockPos target : targets) {
			if (!Terrain.isAirOrReplaceable(level.getBlockState(target))) {
				return Optional.empty();
			}
		}
		if (!level.getBlockState(base.below()).isSolidRender()) {
			return Optional.empty();
		}
		List<BlockPos> sources = new ArrayList<>();
		for (int r = 2; r <= 5 && sources.size() < targets.size(); r++) {
			for (int dx = -r; dx <= r && sources.size() < targets.size(); dx++) {
				for (int dz = -r; dz <= r && sources.size() < targets.size(); dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					BlockPos top = Terrain.ground(level, base.getX() + dx, base.getZ() + dz);
					BlockState state = level.getBlockState(top);
					if (!avoid.test(top) && Math.abs(top.getY() - base.getY()) <= 2 && Terrain.isNaturalSolid(state) && !(state.getBlock() instanceof FallingBlock)
							&& Terrain.isAirOrReplaceable(level.getBlockState(top.above()))) {
						sources.add(top);
					}
				}
			}
		}
		if (sources.size() < targets.size()) {
			return Optional.empty();
		}
		List<Move> moves = new ArrayList<>();
		for (int n = 0; n < targets.size(); n++) {
			moves.add(new Move(sources.get(n), targets.get(n)));
		}
		return Optional.of(moves);
	}

	// --- builds left by others ---

	/** One block to leave. */
	record Piece(BlockPos pos, BlockState state) {
	}

	/** A small house: 5x5 planks, 3-high walls, flat roof, a doorway on {@code door}. */
	record House(BlockPos center, int floorY, Direction door, List<Piece> pieces, List<BlockPos> inside) {
	}

	/** A 5x5 house on flat ground at this column. {@code withDoor}: a wooden door in the doorway (a finished house). */
	static Optional<House> planHouse(ServerLevel level, BlockPos column, Direction door, boolean withDoor) {
		Optional<Integer> floor = Terrain.flatFloor(level, column, 5, 1);
		if (floor.isEmpty()) {
			return Optional.empty();
		}
		int y = floor.get();
		BlockPos center = new BlockPos(column.getX(), y, column.getZ());
		List<Piece> pieces = new ArrayList<>();
		List<BlockPos> inside = new ArrayList<>();
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		BlockPos doorway = center.relative(door, 2);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos cell = center.offset(dx, 0, dz);
				boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				for (int fy = Terrain.ground(level, cell.getX(), cell.getZ()).getY() + 1; fy < y; fy++) {
					pieces.add(new Piece(new BlockPos(cell.getX(), fy, cell.getZ()), Blocks.COBBLESTONE.defaultBlockState()));
				}
				for (int h = 0; h < 3; h++) {
					BlockPos pos = cell.above(h);
					if (!Terrain.isAirOrReplaceable(level.getBlockState(pos))) {
						return Optional.empty();
					}
					if (!wall) {
						if (h == 0) {
							inside.add(pos);
						}
						continue;
					}
					if (cell.getX() == doorway.getX() && cell.getZ() == doorway.getZ() && h < 2) {
						if (withDoor) {
							pieces.add(new Piece(pos, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, door.getOpposite())
									.setValue(DoorBlock.HALF, h == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)));
						}
						continue;
					}
					pieces.add(new Piece(pos, planks));
				}
				if (!Terrain.isAirOrReplaceable(level.getBlockState(cell.above(3)))) {
					return Optional.empty();
				}
				pieces.add(new Piece(cell.above(3), planks));
			}
		}
		return Optional.of(new House(center, y, door, pieces, inside));
	}

	/** A ruined cobblestone hut: 5x5, broken walls 0 to 3 high, no roof, a gap for a door. Returns the hut and its inside. */
	static Optional<House> planRuinedHut(ServerLevel level, BlockPos column, RandomSource random) {
		Optional<Integer> floor = Terrain.flatFloor(level, column, 5, 1);
		if (floor.isEmpty()) {
			return Optional.empty();
		}
		int y = floor.get();
		BlockPos center = new BlockPos(column.getX(), y, column.getZ());
		Direction door = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		BlockPos doorway = center.relative(door, 2);
		List<Piece> pieces = new ArrayList<>();
		List<BlockPos> inside = new ArrayList<>();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos cell = center.offset(dx, 0, dz);
				boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				if (!wall) {
					if (!Terrain.isAirOrReplaceable(level.getBlockState(cell)) || !Terrain.isAirOrReplaceable(level.getBlockState(cell.above()))) {
						return Optional.empty();
					}
					inside.add(cell);
					continue;
				}
				if (cell.getX() == doorway.getX() && cell.getZ() == doorway.getZ()) {
					continue;
				}
				boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
				int height = corner ? 2 + random.nextInt(2) : random.nextInt(3);
				int ground = Terrain.ground(level, cell.getX(), cell.getZ()).getY();
				for (int fy = ground + 1; fy < y + height; fy++) {
					BlockPos pos = new BlockPos(cell.getX(), fy, cell.getZ());
					if (Terrain.isAirOrReplaceable(level.getBlockState(pos))) {
						pieces.add(new Piece(pos, random.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState()));
					}
				}
			}
		}
		return Optional.of(new House(center, y, door, pieces, inside));
	}

	/** A one-block dirt pillar ("panic tower") of {@code height} on flat ground. Returns the pieces; the top is the last. */
	static Optional<List<Piece>> planPanicTower(ServerLevel level, BlockPos column, int height) {
		BlockPos ground = Terrain.ground(level, column.getX(), column.getZ());
		if (!level.getBlockState(ground).isSolidRender() || !level.getFluidState(ground.above()).isEmpty()) {
			return Optional.empty();
		}
		List<Piece> pieces = new ArrayList<>();
		for (int h = 1; h <= height; h++) {
			BlockPos pos = ground.above(h);
			if (!Terrain.isAirOrReplaceable(level.getBlockState(pos))) {
				return Optional.empty();
			}
			pieces.add(new Piece(pos, Blocks.DIRT.defaultBlockState()));
		}
		return Terrain.isAirOrReplaceable(level.getBlockState(ground.above(height + 1))) ? Optional.of(pieces) : Optional.empty();
	}

	static void place(Build build, List<Piece> pieces) {
		for (Piece piece : pieces) {
			build.leave(piece.pos(), piece.state());
		}
	}

	// --- the F15 test room (Y 12 to 20, 7x7, sealed) ---

	/** The room's lower north-west corner (shell included): x0..x0+6, floorY..floorY+8, z0..z0+6. */
	record Room(BlockPos corner) {
		BlockPos rulesChest() {
			return corner.offset(4, 1, 5);
		}

		BlockPos notesChest() {
			return corner.offset(2, 1, 5);
		}

		/** On the loft (slabs at floor + 4 along the north wall). */
		BlockPos loftChest() {
			return corner.offset(4, 5, 1);
		}

		/** Under the floor: an empty chest with a sign on it. */
		BlockPos belowChest() {
			return corner.offset(3, -2, 3);
		}

		BlockPos belowSign() {
			return corner.offset(3, -1, 3);
		}
	}

	/** True if the room and a one-block margin are all natural rock, dry, with nothing built. */
	static boolean roomFits(ServerLevel level, BlockPos corner) {
		for (BlockPos pos : BlockPos.betweenClosed(corner.offset(-1, -1, -1), corner.offset(7, 9, 7))) {
			BlockState state = level.getBlockState(pos);
			if (!Terrain.isNaturalSolid(state) || !level.getFluidState(pos).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Carves the inside, lines the shell with smooth stone, adds the loft and a ladder to it. */
	static void buildRoom(Build build, Room room) {
		BlockPos c = room.corner();
		BlockState shell = Blocks.SMOOTH_STONE.defaultBlockState();
		for (BlockPos pos : BlockPos.betweenClosed(c, c.offset(6, 8, 6))) {
			boolean inside = pos.getX() > c.getX() && pos.getX() < c.getX() + 6 && pos.getY() > c.getY() && pos.getY() < c.getY() + 8
					&& pos.getZ() > c.getZ() && pos.getZ() < c.getZ() + 6;
			if (inside) {
				build.remove(pos.immutable());
			} else {
				build.convert(pos.immutable(), shell);
			}
		}
		BlockState slab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
		for (int dx = 1; dx <= 5; dx++) {
			for (int dz = 1; dz <= 2; dz++) {
				build.leave(c.offset(dx, 4, dz), slab);
			}
		}
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
		for (int dy = 1; dy <= 5; dy++) {
			build.leave(c.offset(1, dy, 3), ladder);
		}
	}

	static double horizontalDistSqr(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}
}
