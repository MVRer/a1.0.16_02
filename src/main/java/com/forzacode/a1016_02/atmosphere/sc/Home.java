package com.forzacode.a1016_02.atmosphere.sc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.atmosphere.mixin.TrapDoorBlockAccessor;
import com.forzacode.a1016_02.core.PlayerWatch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

/**
 * 05's house (docs/SELF_CREATED.md): around a base, the covered floor cells (a cell a walker stands in, with a block
 * over it within {@link #ROOF_ABOVE}, leaves not counted) within {@code houseRadius} of the base and {@link #BELOW} to
 * {@link #ABOVE} of its height (at most 13 x 7 x 13 cells, one pass per column), at least {@code minCoveredCells}, and a
 * hand door, trapdoor or fence gate a player placed within {@link #DOOR_RADIUS} of the base whose inside (along its
 * facing) is one of those cells. Cheap, never loads a chunk (empty if the area is not loaded). Server thread.
 */
public final class Home {
	public static final int DOOR_RADIUS = 8;
	public static final int BELOW = 3;
	public static final int ABOVE = 3;
	/** A roof within this many blocks over the floor block. */
	public static final int ROOF_ABOVE = 6;
	/** A cover this thin (a carpet, a snow layer) is stood on, not walked into. */
	public static final double THIN = 0.2;

	/**
	 * A house: its base, its door (a door's lower half), the direction from the door into the house, the covered cells a
	 * walker stands in (feet cells), and how many.
	 */
	public record House(BlockPos base, BlockPos door, Direction inward, List<BlockPos> floor) {
		public House {
			floor = List.copyOf(floor);
		}

		/** Whether a cell is inside the house's box (its columns and heights). */
		public boolean inBox(BlockPos pos, int radius) {
			return Math.abs(pos.getX() - base.getX()) <= radius && Math.abs(pos.getZ() - base.getZ()) <= radius && pos.getY() >= base.getY() - BELOW - 1
					&& pos.getY() <= base.getY() + ABOVE + 1;
		}
	}

	private Home() {
	}

	/** A door (its lower half) or trapdoor a hand opens, or a fence gate. */
	public static boolean entry(BlockState state) {
		if (state.getBlock() instanceof DoorBlock door) {
			return door.type().canOpenByHand() && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
		}
		if (state.getBlock() instanceof TrapDoorBlock trapdoor) {
			return ((TrapDoorBlockAccessor) trapdoor).a1016_02$type().canOpenByHand();
		}
		return state.getBlock() instanceof FenceGateBlock;
	}

	/** A container whose own close sound the house can play ({@code ChestOpensCard.sounds}): a chest, barrel, shulker box or ender chest. */
	public static boolean container(BlockState state) {
		return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock || state.getBlock() instanceof ShulkerBoxBlock
				|| state.getBlock() instanceof EnderChestBlock;
	}

	/** Whether an entry stands open now. */
	public static boolean open(BlockState state) {
		return state.getValueOrElse(BlockStateProperties.OPEN, false);
	}

	/** The house at {@code base}, if there is one ({@link #why} says why not). */
	public static Optional<House> find(ServerLevel level, BlockPos base, PlayerWatch watch, Sc05Config cfg) {
		Result r = survey(level, base, watch, cfg);
		return Optional.ofNullable(r.house());
	}

	/** Why there is no house at {@code base}, or empty if there is one. */
	public static Optional<String> why(ServerLevel level, BlockPos base, PlayerWatch watch, Sc05Config cfg) {
		Result r = survey(level, base, watch, cfg);
		return r.house() != null ? Optional.empty() : Optional.of(r.why());
	}

	private record Result(@Nullable House house, String why) {
	}

	private static Result survey(ServerLevel level, BlockPos base, PlayerWatch watch, Sc05Config cfg) {
		int r = Math.max(0, cfg.houseRadius);
		if (!level.isLoaded(base.offset(-r, 0, -r)) || !level.isLoaded(base.offset(r, 0, r)) || !level.isLoaded(base.offset(-r, 0, r))
				|| !level.isLoaded(base.offset(r, 0, -r))) {
			return new Result(null, "not loaded");
		}
		Set<BlockPos> covered = covered(level, base, r);
		if (covered.size() < cfg.minCoveredCells) {
			return new Result(null, covered.size() + " covered cells (at least " + cfg.minCoveredCells + ")");
		}
		List<BlockPos> entries = new ArrayList<>(watch.placedNear(level, base, DOOR_RADIUS, Home::entry));
		if (entries.isEmpty()) {
			return new Result(null, "no door a player placed within " + DOOR_RADIUS);
		}
		entries.sort(Comparator.comparingDouble(p -> p.distSqr(base)));
		for (BlockPos door : entries) {
			Direction inward = inward(level, door, base, covered);
			if (inward != null) {
				return new Result(new House(base, door.immutable(), inward, sortedFloor(covered)), "");
			}
		}
		return new Result(null, "no door opens onto a covered cell");
	}

	private static List<BlockPos> sortedFloor(Set<BlockPos> covered) {
		List<BlockPos> floor = new ArrayList<>(covered);
		floor.sort(Comparator.comparingLong(BlockPos::asLong));
		return floor;
	}

	/** The direction from the door into the house: the side along its facing whose cell is covered (the nearer the base on a tie). */
	static @Nullable Direction inward(ServerLevel level, BlockPos door, BlockPos base, Set<BlockPos> covered) {
		BlockState state = level.getBlockState(door);
		if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			return null;
		}
		Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
		Direction best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Direction side : new Direction[] {facing, facing.getOpposite()}) {
			for (int dy = 0; dy >= -1; dy--) {
				BlockPos cell = door.relative(side).above(dy);
				if (covered.contains(cell)) {
					double d = cell.distSqr(base);
					if (d < bestDistance) {
						best = side;
						bestDistance = d;
					}
				}
			}
		}
		return best;
	}

	/** The covered feet cells around {@code base} (one pass per column). */
	static Set<BlockPos> covered(ServerLevel level, BlockPos base, int r) {
		Set<BlockPos> cells = new HashSet<>();
		int lo = base.getY() - BELOW;
		int hi = base.getY() + ABOVE;
		int top = hi + 1 + ROOF_ABOVE;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int x = base.getX() + dx;
				int z = base.getZ() + dz;
				int n = top - (lo - 1) + 1;
				boolean[] solid = new boolean[n];
				boolean[] free = new boolean[n];
				boolean[] roof = new boolean[n];
				for (int i = 0; i < n; i++) {
					at.set(x, lo - 1 + i, z);
					BlockState state = level.getBlockState(at);
					VoxelShape shape = state.getCollisionShape(level, at);
					boolean empty = shape.isEmpty();
					solid[i] = !empty;
					free[i] = empty || shape.max(Direction.Axis.Y) <= THIN;
					roof[i] = !empty && !state.is(BlockTags.LEAVES);
				}
				for (int y = lo; y <= hi; y++) {
					int i = y - (lo - 1);
					// The floor holds a walker and is not itself a thin cover; a thin cover in the feet cell is stood on.
					if (!solid[i - 1] || free[i - 1] || !free[i] || i + 1 >= n || solid[i + 1]) {
						continue;
					}
					for (int k = i + 2; k <= Math.min(n - 1, i - 1 + ROOF_ABOVE); k++) {
						if (roof[k]) {
							cells.add(new BlockPos(x, y, z));
							break;
						}
					}
				}
			}
		}
		return cells;
	}

	/**
	 * The walk inside: a first cell {@code 2..3} cells in from the door (nearer if the house is shallow), then cell by
	 * cell to a covered neighbor (four sides, a step up or down allowed) not walked yet that is farther from the door
	 * (as far, only when none is farther), the farthest first, ties at random; {@code stepsMin..stepsMax} cells, or none
	 * if fewer than {@code stepsMin} fit.
	 */
	public static List<BlockPos> walk(House house, RandomSource random, int stepsMin, int stepsMax) {
		Set<BlockPos> floor = new HashSet<>(house.floor());
		int lo = Math.max(1, Math.min(stepsMin, stepsMax));
		int wanted = lo + random.nextInt(Math.max(stepsMin, stepsMax) - lo + 1);
		BlockPos start = null;
		int in = 2 + random.nextInt(2);
		for (int d = in; d >= 1 && start == null; d--) {
			BlockPos cell = house.door().relative(house.inward(), d);
			for (int dy : new int[] {0, -1, 1}) {
				if (floor.contains(cell.above(dy))) {
					start = cell.above(dy);
					break;
				}
			}
		}
		if (start == null) {
			return List.of();
		}
		List<BlockPos> path = new ArrayList<>();
		Set<BlockPos> walked = new HashSet<>();
		path.add(start);
		walked.add(start);
		Vec3 door = Vec3.atCenterOf(house.door());
		while (path.size() < wanted) {
			BlockPos here = path.getLast();
			double now = away(here, door);
			List<BlockPos> farther = new ArrayList<>();
			List<BlockPos> level = new ArrayList<>();
			double best = now;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				for (int dy : new int[] {0, -1, 1}) {
					BlockPos next = here.relative(side).above(dy);
					if (!floor.contains(next) || walked.contains(next)) {
						continue;
					}
					double d = away(next, door);
					if (d > best + 1.0E-9) {
						best = d;
						farther.clear();
						farther.add(next);
					} else if (d > now + 1.0E-9 && Math.abs(d - best) < 1.0E-9) {
						farther.add(next);
					} else if (Math.abs(d - now) < 1.0E-9) {
						level.add(next);
					}
				}
			}
			List<BlockPos> pick = farther.isEmpty() ? level : farther;
			if (pick.isEmpty()) {
				break;
			}
			BlockPos next = pick.get(random.nextInt(pick.size()));
			path.add(next);
			walked.add(next);
		}
		return path.size() >= lo ? path : List.of();
	}

	private static double away(BlockPos cell, Vec3 door) {
		double dx = cell.getX() + 0.5 - door.x;
		double dz = cell.getZ() + 0.5 - door.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** The player-placed container in the house's box nearest {@code near}, if any. */
	public static Optional<BlockPos> container(ServerLevel level, House house, BlockPos near, PlayerWatch watch, int radius) {
		return watch.placedNear(level, house.base(), radius + 1, Home::container).stream().filter(p -> house.inBox(p, radius))
				.min(Comparator.comparingDouble(p -> p.distSqr(near)));
	}
}
