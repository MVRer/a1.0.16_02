package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceService;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * 2x2 tunnel geometry and the carving rules. An <em>anchor</em> is the lowest corner of a 2x2x2 cube; anchors one
 * block apart (along x, z or y) make a 2x2 tunnel or shaft, and a change of direction is a clean corner. Every
 * carve goes through {@link TraceService#batch}: one view check, all or nothing.
 */
public final class Tunnels {
	/** {@link #check} reason when part of the tunnel is not loaded (try again later). */
	public static final String UNLOADED = "unloaded";

	/**
	 * What a carve must respect.
	 *
	 * @param clearance         new cells keep more than this many blocks (cube) from anything a player dug or placed
	 * @param exploredClearance new cells keep more than this many blocks from caves the player explored (0: ignore)
	 * @param sealed            every neighbour of a new cell must be an opaque solid block (or the tunnel): it never
	 *                          opens into a cave, a mine or a room
	 * @param allowAir          new cells may already be natural air (crossing a cave)
	 * @param ignored           player spaces this carve may ignore (the mine wall a card breaks through)
	 * @param explored          the explored-cave index, or null
	 */
	public record Rules(int clearance, int exploredClearance, boolean sealed, boolean allowAir, Predicate<BlockPos> ignored, @Nullable PosSet explored) {
		/** "Under you" corridors: sealed, {@code digBelow} from every player dig and build, away from explored caves. */
		public static Rules network(int digBelow, int exploredClearance, @Nullable PosSet explored) {
			return new Rules(digBelow, exploredClearance, true, false, pos -> false, explored);
		}

		/** The shaft under the bed: sealed and never into a player space, but allowed up to one block below it. */
		public static Rules shaft() {
			return new Rules(0, 0, true, false, pos -> false, null);
		}

		/** Card tunnels: may cross natural caves, keep {@code clearance} from player spaces. */
		public static Rules card(int clearance) {
			return new Rules(clearance, 0, false, true, pos -> false, null);
		}
	}

	private Tunnels() {
	}

	/** The eight cells of an anchor's cube. */
	public static List<BlockPos> cube(BlockPos anchor) {
		List<BlockPos> cells = new ArrayList<>(8);
		for (int dx = 0; dx <= 1; dx++) {
			for (int dy = 0; dy <= 1; dy++) {
				for (int dz = 0; dz <= 1; dz++) {
					cells.add(anchor.offset(dx, dy, dz));
				}
			}
		}
		return cells;
	}

	public static LongOpenHashSet cells(Collection<BlockPos> anchors) {
		LongOpenHashSet cells = new LongOpenHashSet();
		for (BlockPos anchor : anchors) {
			for (BlockPos cell : cube(anchor)) {
				cells.add(cell.asLong());
			}
		}
		return cells;
	}

	/** The anchor of the 2x2 cross-section whose near face is the block in front of {@code mouth} (feet level) along {@code dir}. */
	public static BlockPos anchorInFront(BlockPos mouth, Direction dir) {
		BlockPos front = mouth.relative(dir);
		// The cube spans [a, a+1] on every axis; for a negative direction its near face is a+1.
		return dir.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? front.relative(dir) : front;
	}

	/** Natural ground he can bore through: stone, deepslate, ores, dirt, terracotta and the like. Never falling blocks. */
	public static boolean carvable(BlockState state) {
		if (state.isAir() || state.getBlock() instanceof FallingBlock || !state.getFluidState().isEmpty()) {
			return false;
		}
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.ORES)
				|| state.is(BlockTags.DIRT) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.CALCITE) || state.is(Blocks.SMOOTH_BASALT)
				|| state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.CLAY) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE);
	}

	/** A block that closes a tunnel wall: opaque, solid, no fluid. */
	public static boolean seals(BlockState state) {
		return state.isSolidRender() && state.getFluidState().isEmpty();
	}

	/** Torches the player might have placed (not redstone torches, which are circuitry). */
	public static boolean isTorch(BlockState state) {
		return state.getBlock() instanceof BaseTorchBlock && !(state.getBlock() instanceof RedstoneTorchBlock);
	}

	/** Chebyshev (cube) distance. */
	public static int cheb(BlockPos a, BlockPos b) {
		return Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
	}

	/**
	 * Why {@code anchor} cannot be carved next to the {@code existing} tunnel cells, or null if it can. Reads the
	 * world and the player footprint; changes nothing.
	 */
	public static @Nullable String check(ServerLevel level, BlockPos anchor, LongSet existing, Rules rules) {
		PlayerWatch watch = Services.watch();
		List<BlockPos> fresh = new ArrayList<>(8);
		LongOpenHashSet freshSet = new LongOpenHashSet(8);
		for (BlockPos cell : cube(anchor)) {
			if (!existing.contains(cell.asLong())) {
				fresh.add(cell);
				freshSet.add(cell.asLong());
			}
		}
		if (fresh.isEmpty()) {
			return "nothing new";
		}
		int minY = level.getMinY() + 5;
		int maxY = level.getMaxY() - 2;
		for (BlockPos cell : fresh) {
			if (cell.getY() < minY || cell.getY() > maxY) {
				return "outside";
			}
			if (!level.isLoaded(cell)) {
				return UNLOADED;
			}
			BlockState state = level.getBlockState(cell);
			if (!state.getFluidState().isEmpty()) {
				return "fluid";
			}
			if (state.isAir()) {
				if (!rules.allowAir()) {
					return "open";
				}
				if (watch.wasDugByPlayer(level, cell) && !rules.ignored().test(cell)) {
					return "player space";
				}
			} else if (!carvable(state)) {
				return "not ground";
			}
			if (watch.wasPlacedByPlayer(level, cell)) {
				return "player block";
			}
		}
		for (BlockPos cell : fresh) {
			for (Direction dir : Direction.values()) {
				BlockPos n = cell.relative(dir);
				long packed = n.asLong();
				if (existing.contains(packed) || freshSet.contains(packed)) {
					continue;
				}
				if (!level.isLoaded(n)) {
					return UNLOADED;
				}
				BlockState state = level.getBlockState(n);
				if (!state.getFluidState().isEmpty()) {
					return "fluid";
				}
				if (dir == Direction.UP && state.getBlock() instanceof FallingBlock) {
					return "falling";
				}
				if (rules.sealed() && !rules.ignored().test(n) && !seals(state)) {
					return "open";
				}
			}
		}
		int r = rules.clearance();
		if (r > 0) {
			for (BlockPos dug : watch.dugNear(level, anchor, r + 1)) {
				if (!rules.ignored().test(dug) && within(fresh, dug, r)) {
					return "near a player dig";
				}
			}
			for (BlockPos placed : watch.placedNear(level, anchor, r + 1, state -> true)) {
				if (!rules.ignored().test(placed) && within(fresh, placed, r)) {
					return "near a player block";
				}
			}
		}
		PosSet explored = rules.explored();
		if (explored != null && rules.exploredClearance() > 0) {
			for (BlockPos cell : fresh) {
				if (explored.anyWithin(cell, rules.exploredClearance())) {
					return "explored cave";
				}
			}
		}
		return null;
	}

	private static boolean within(List<BlockPos> cells, BlockPos pos, int radius) {
		for (BlockPos cell : cells) {
			if (cheb(cell, pos) <= radius) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Carves these anchors (skipping cells in {@code existing} and air) as one {@link TraceBatch}. True if it
	 * happened, false if anything was in view or the edit was refused (nothing changed).
	 */
	public static boolean carve(ServerLevel level, TraceService traces, Collection<BlockPos> anchors, LongSet existing, String cause) {
		TraceBatch batch = traces.batch(level, cause);
		LongOpenHashSet queued = new LongOpenHashSet();
		for (BlockPos anchor : anchors) {
			for (BlockPos cell : cube(anchor)) {
				if (!existing.contains(cell.asLong()) && queued.add(cell.asLong()) && !level.getBlockState(cell).isAir()) {
					batch.remove(cell);
				}
			}
		}
		return batch.size() == 0 || batch.commit();
	}
}
