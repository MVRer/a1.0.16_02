package com.forzacode.a1016_02.world.sig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.world.WorldConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

/**
 * The subject's first shelter, read from the world: which player-placed blocks make its shell, which air is its
 * inside, which blocks are its roof, and which blocks can be taken without opening it. "Open" cells are those a mob
 * could pass (no collision taller than half a block: air, plants, carpets, torches, water) and furniture the player
 * may move any time; doors, glass and fences close. A house is closed when no open path leads from its inside out of
 * the area around it. Server thread only; the chunks involved must be loaded.
 */
public final class HouseShell {
	/** How far around the shelter's box the outside is looked at. */
	static final int MARGIN = 2;
	/** Blocks of the cluster are grown from player-placed blocks this close to an anchor. */
	private static final int SEED_REACH = 3;

	/**
	 * A captured first shelter.
	 *
	 * @param origin   the min corner of the shell and inside
	 * @param targets  the shell (relative to {@code origin}), floor first and the roof last
	 * @param interior the inside (relative)
	 * @param closed   no open path led from the inside out when it was captured
	 */
	public record Capture(BlockPos origin, List<HouseCopyState.Target> targets, List<BlockPos> interior, boolean closed) {
	}

	private HouseShell() {
	}

	// --- block rules ---

	/** True if a mob cannot pass this cell (a collision shape taller than half a block). */
	public static boolean closes(BlockGetter level, BlockPos pos, BlockState state) {
		if (state.isAir()) {
			return false;
		}
		VoxelShape shape = state.getCollisionShape(level, pos);
		return !shape.isEmpty() && shape.max(Direction.Axis.Y) > 0.5;
	}

	/**
	 * Furniture: what a player moves around inside (a crafting table, a bed, workstations, anything with a block
	 * entity such as chests and furnaces). It is never shell, never moved, and never counted on to keep the house
	 * closed: its cell counts as open.
	 */
	public static boolean furniture(BlockState state) {
		return state.hasBlockEntity() || state.is(Blocks.CRAFTING_TABLE) || state.is(BlockTags.BEDS) || state.is(Blocks.STONECUTTER)
				|| state.is(Blocks.LOOM) || state.is(Blocks.SMITHING_TABLE) || state.is(Blocks.CARTOGRAPHY_TABLE) || state.is(Blocks.FLETCHING_TABLE)
				|| state.is(Blocks.GRINDSTONE) || state.is(Blocks.CAULDRON) || state.is(Blocks.COMPOSTER);
	}

	/** Doors, trapdoors and gates: they close the house but are never moved (two halves, and the way in). */
	public static boolean opening(BlockState state) {
		return state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES);
	}

	/** True if a mob could pass the cell, or the player may clear it any time (furniture). */
	public static boolean passable(BlockGetter level, BlockPos pos, BlockState state) {
		return !closes(level, pos, state) || furniture(state);
	}

	/**
	 * A block that can be moved as a piece of shell: it closes, has no block entity, no fluid, is not furniture, an
	 * opening or a two-part block, and does not fall.
	 */
	public static boolean shellMaterial(BlockGetter level, BlockPos pos, BlockState state) {
		return closes(level, pos, state) && state.getFluidState().isEmpty() && !furniture(state) && !opening(state)
				&& !(state.getBlock() instanceof Fallable) && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
				&& !state.hasProperty(BlockStateProperties.BED_PART);
	}

	// --- capture ---

	/**
	 * Captures the first shelter around the anchors (first block, crafting table, chest, base; the one with the most
	 * player-placed blocks around it wins): the cluster of player-placed blocks connected to it, the inside it
	 * encloses (or, if it is open, the air under its roof), and its shell: cluster blocks that touch the outside,
	 * lie under the inside (the floor) or above it (the roof). Null if there is no shelter with at least
	 * {@code houseCopyMinShellBlocks} shell blocks.
	 */
	public static @Nullable Capture capture(ServerLevel level, List<BlockPos> anchors, WorldConfig config) {
		BlockPos anchor = null;
		int best = -1;
		for (BlockPos a : anchors) {
			int around = Services.watch().placedNear(level, a, 6, state -> !state.isAir()).size();
			if (around > best) {
				best = around;
				anchor = a;
			}
		}
		if (anchor == null || best <= 0) {
			return null;
		}
		Set<BlockPos> cluster = cluster(level, anchor, config);
		if (cluster.isEmpty()) {
			return null;
		}
		BoundingBox box = boxOf(cluster);
		BoundingBox region = box.inflatedBy(MARGIN);
		Set<BlockPos> outside = flood(level, region, borderCells(region), null);
		Set<BlockPos> inside = new LinkedHashSet<>();
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!outside.contains(pos) && passable(level, pos, level.getBlockState(pos))) {
				inside.add(pos.immutable());
			}
		}
		boolean closed = true;
		Set<BlockPos> interior = rooms(level, inside);
		if (interior.isEmpty()) {
			// Open (a doorway without a door): the inside is the air under its roof.
			closed = false;
			interior = covered(level, box, cluster);
		}
		if (interior.isEmpty()) {
			return null;
		}
		int interiorMaxY = interior.stream().mapToInt(BlockPos::getY).max().orElseThrow();
		BoundingBox interiorBox = boxOf(interior);
		List<BlockPos> shell = new ArrayList<>();
		for (BlockPos pos : cluster) {
			BlockState state = level.getBlockState(pos);
			if (!shellMaterial(level, pos, state)) {
				continue;
			}
			boolean roof = roof(pos, interior, interiorMaxY, interiorBox);
			boolean floor = interior.contains(pos.above());
			boolean touchesOutside = false;
			for (Direction dir : Direction.values()) {
				BlockPos n = pos.relative(dir);
				if (!interior.contains(n) && passable(level, n, level.getBlockState(n)) && !cluster.contains(n)) {
					touchesOutside = true;
					break;
				}
			}
			if (roof || floor || touchesOutside) {
				shell.add(pos);
			}
		}
		if (shell.size() < config.houseCopyMinShellBlocks) {
			return null;
		}
		BlockPos center = interiorBox.getCenter();
		if (shell.size() > config.houseCopyMaxShellBlocks) {
			shell.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
			shell = new ArrayList<>(shell.subList(0, config.houseCopyMaxShellBlocks));
		}
		Set<BlockPos> interiorFinal = interior;
		shell.sort(Comparator.comparingInt((BlockPos p) -> roof(p, interiorFinal, interiorMaxY, interiorBox) ? 1 : 0).thenComparingInt(BlockPos::getY)
				.thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
		BoundingBox all = BoundingBox.encapsulating(boxOf(shell), interiorBox);
		BlockPos origin = new BlockPos(all.minX(), all.minY(), all.minZ());
		List<HouseCopyState.Target> targets = new ArrayList<>();
		for (BlockPos pos : shell) {
			targets.add(new HouseCopyState.Target(pos.subtract(origin), level.getBlockState(pos), roof(pos, interior, interiorMaxY, interiorBox)));
		}
		List<BlockPos> inner = interior.stream().map(p -> p.subtract(origin)).toList();
		return new Capture(origin, targets, inner, closed);
	}

	/** The player-placed blocks connected (26 ways) to those near the anchor, within the capture radius. */
	static Set<BlockPos> cluster(ServerLevel level, BlockPos anchor, WorldConfig config) {
		Set<BlockPos> placed = new HashSet<>(Services.watch().placedNear(level, anchor, config.houseCopyCaptureRadius, state -> !state.isAir()));
		Set<BlockPos> cluster = new LinkedHashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos pos : placed) {
			if (Math.max(Math.abs(pos.getX() - anchor.getX()), Math.max(Math.abs(pos.getY() - anchor.getY()), Math.abs(pos.getZ() - anchor.getZ())))
					<= SEED_REACH) {
				if (cluster.add(pos)) {
					queue.add(pos);
				}
			}
		}
		int cap = Math.max(config.houseCopyMaxShellBlocks * 3, 64);
		while (!queue.isEmpty() && cluster.size() < cap) {
			BlockPos pos = queue.poll();
			for (BlockPos n : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
				if (placed.contains(n) && cluster.add(n.immutable())) {
					queue.add(n.immutable());
				}
			}
		}
		return cluster;
	}

	/** The pockets of enclosed air a player could stand in (an open cell with open air above it). */
	private static Set<BlockPos> rooms(ServerLevel level, Set<BlockPos> inside) {
		Set<BlockPos> rooms = new LinkedHashSet<>();
		Set<BlockPos> seen = new HashSet<>();
		for (BlockPos start : inside) {
			if (!seen.add(start)) {
				continue;
			}
			List<BlockPos> component = new ArrayList<>();
			Deque<BlockPos> queue = new ArrayDeque<>(List.of(start));
			boolean standing = false;
			while (!queue.isEmpty()) {
				BlockPos pos = queue.poll();
				component.add(pos);
				if (inside.contains(pos.above()) && !inside.contains(pos.below())) {
					standing = true;
				}
				for (Direction dir : Direction.values()) {
					BlockPos n = pos.relative(dir);
					if (inside.contains(n) && seen.add(n)) {
						queue.add(n);
					}
				}
			}
			if (standing) {
				rooms.addAll(component);
			}
		}
		return rooms;
	}

	/** Open cells in the box with a cluster block somewhere above them in their column (the air under a roof). */
	private static Set<BlockPos> covered(ServerLevel level, BoundingBox box, Set<BlockPos> cluster) {
		Set<BlockPos> covered = new LinkedHashSet<>();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				boolean roofed = false;
				for (int y = box.maxY(); y >= box.minY(); y--) {
					BlockPos pos = new BlockPos(x, y, z);
					if (cluster.contains(pos) && closes(level, pos, level.getBlockState(pos))) {
						roofed = true;
					} else if (roofed && passable(level, pos, level.getBlockState(pos))) {
						covered.add(pos);
					}
				}
			}
		}
		return covered;
	}

	/** The roof: anything with inside under it in its column, or above the inside within a block of its footprint. */
	static boolean roof(BlockPos pos, Set<BlockPos> interior, int interiorMaxY, BoundingBox interiorBox) {
		if (pos.getY() > interiorMaxY && pos.getX() >= interiorBox.minX() - 1 && pos.getX() <= interiorBox.maxX() + 1
				&& pos.getZ() >= interiorBox.minZ() - 1 && pos.getZ() <= interiorBox.maxZ() + 1) {
			return true;
		}
		for (int y = pos.getY() - 1; y >= interiorBox.minY(); y--) {
			if (interior.contains(new BlockPos(pos.getX(), y, pos.getZ()))) {
				return true;
			}
		}
		return false;
	}

	// --- the house now ---

	/** The real house as it stands now, for one decision. */
	public static final class Analysis {
		private final ServerLevel level;
		private final BoundingBox region;
		private final Set<BlockPos> interior;
		private final int interiorMaxY;
		private final BoundingBox interiorBox;
		private final boolean closed;

		Analysis(ServerLevel level, HouseCopyState state) {
			this.level = level;
			this.region = state.realBox().inflatedBy(MARGIN);
			Set<BlockPos> inner = new LinkedHashSet<>();
			for (BlockPos rel : state.interior()) {
				inner.add(state.realOrigin().offset(rel));
			}
			this.interior = inner;
			this.interiorMaxY = inner.stream().mapToInt(BlockPos::getY).max().orElse(Integer.MIN_VALUE);
			this.interiorBox = inner.isEmpty() ? new BoundingBox(state.realOrigin()) : boxOf(inner);
			this.closed = closedWith(null);
		}

		/** True if no open path leads from the inside out of the area around the house now. */
		public boolean closed() {
			return closed;
		}

		/** True if it would still be closed with {@code removed} taken out (null: as it is). */
		public boolean closedWith(@Nullable BlockPos removed) {
			List<BlockPos> start = new ArrayList<>();
			for (BlockPos pos : interior) {
				if (pos.equals(removed) || passable(level, pos, level.getBlockState(pos))) {
					start.add(pos);
				}
			}
			if (removed != null && interiorNeighbour(removed)) {
				start.add(removed);
			}
			Set<BlockPos> reached = flood(level, region, start, removed);
			for (BlockPos pos : reached) {
				if (onBorder(region, pos)) {
					return false;
				}
			}
			return true;
		}

		public boolean isRoof(BlockPos pos) {
			return !interior.isEmpty() && roof(pos, interior, interiorMaxY, interiorBox);
		}

		/**
		 * True if this block of the real house may be taken now: a player placed it, it is shell material, not the
		 * roof, nothing hangs on it or stands on it (no dependent and no fluid beside it), and taking it neither puts
		 * the inside next to the outside nor (for a closed house) opens any path out.
		 */
		public boolean canTake(BlockPos pos) {
			BlockState state = level.getBlockState(pos);
			if (!region.isInside(pos) || state.isAir() || !Services.watch().wasPlacedByPlayer(level, pos) || !shellMaterial(level, pos, state)
					|| isRoof(pos)) {
				return false;
			}
			boolean inside = false;
			boolean out = false;
			for (Direction dir : Direction.values()) {
				BlockPos n = pos.relative(dir);
				BlockState ns = level.getBlockState(n);
				if (!ns.isAir() && (!ns.isCollisionShapeFullBlock(level, n) || !ns.getFluidState().isEmpty())) {
					return false; // a torch, a door half, a carpet, water...: it would break or flow
				}
				if (passable(level, n, ns)) {
					if (interior.contains(n)) {
						inside = true;
					} else {
						out = true;
					}
				}
			}
			if (inside && out) {
				return false;
			}
			return !closed || closedWith(pos);
		}

		/** Every block that may be taken now, the outermost first. */
		public List<BlockPos> takeable() {
			BlockPos center = interiorBox.getCenter();
			int radius = Math.max(region.getXSpan(), Math.max(region.getYSpan(), region.getZSpan())) / 2 + 1;
			List<BlockPos> found = new ArrayList<>();
			for (BlockPos pos : Services.watch().placedNear(level, region.getCenter(), radius, state -> !state.isAir())) {
				if (canTake(pos)) {
					found.add(pos);
				}
			}
			found.sort(Comparator.comparingDouble((BlockPos p) -> p.distSqr(center)).reversed().thenComparingInt(BlockPos::getY));
			return found;
		}

		private boolean interiorNeighbour(BlockPos pos) {
			for (Direction dir : Direction.values()) {
				if (interior.contains(pos.relative(dir))) {
					return true;
				}
			}
			return false;
		}
	}

	/** The real house of {@code state} as it stands now. */
	public static Analysis analyze(ServerLevel level, HouseCopyState state) {
		return new Analysis(level, state);
	}

	// --- helpers ---

	/** Open cells reachable from {@code start} through open cells inside {@code region}; {@code extraOpen} counts as open. */
	static Set<BlockPos> flood(ServerLevel level, BoundingBox region, Iterable<BlockPos> start, @Nullable BlockPos extraOpen) {
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos pos : start) {
			if (region.isInside(pos) && (pos.equals(extraOpen) || passable(level, pos, level.getBlockState(pos))) && seen.add(pos)) {
				queue.add(pos);
			}
		}
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			for (Direction dir : Direction.values()) {
				BlockPos n = pos.relative(dir);
				if (region.isInside(n) && !seen.contains(n) && (n.equals(extraOpen) || passable(level, n, level.getBlockState(n)))) {
					seen.add(n);
					queue.add(n);
				}
			}
		}
		return seen;
	}

	static List<BlockPos> borderCells(BoundingBox region) {
		List<BlockPos> border = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(region.minX(), region.minY(), region.minZ(), region.maxX(), region.maxY(), region.maxZ())) {
			if (onBorder(region, pos)) {
				border.add(pos.immutable());
			}
		}
		return border;
	}

	static boolean onBorder(BoundingBox region, BlockPos pos) {
		return pos.getX() == region.minX() || pos.getX() == region.maxX() || pos.getY() == region.minY() || pos.getY() == region.maxY()
				|| pos.getZ() == region.minZ() || pos.getZ() == region.maxZ();
	}

	static BoundingBox boxOf(Iterable<BlockPos> positions) {
		BoundingBox box = null;
		for (BlockPos pos : positions) {
			box = box == null ? new BoundingBox(pos) : BoundingBox.encapsulating(box, new BoundingBox(pos));
		}
		return box == null ? new BoundingBox(BlockPos.ZERO) : box;
	}
}
