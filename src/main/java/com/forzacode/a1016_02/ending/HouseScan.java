package com.forzacode.a1016_02.ending;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.lore.FragmentItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Reads the player's real house around their base: what is inside it (Ending B empties it, the shell stays) and
 * where its doorways are (mobs wait there). Only blocks the player placed count. Read-only.
 */
public final class HouseScan {
	/** A spot just outside a door (or an opening) and the door itself. */
	public record Doorway(BlockPos spot, BlockPos door) {
	}

	private HouseScan() {
	}

	/**
	 * The furnishings the player placed within {@code houseRadius} of {@code home} that stand under a roof: chests,
	 * beds, furnaces and other block entities, crafting tables, torches, lanterns, carpets, pots and the like. Never a
	 * sign (lore's), never a fragment's spot, never a container or lectern holding a fragment. Nearest first.
	 */
	public static List<BlockPos> interior(ServerLevel level, BlockPos home, EndingConfig cfg, PlayerWatch watch, Collection<BlockPos> keep) {
		Set<BlockPos> kept = Set.copyOf(keep);
		List<BlockPos> found = new ArrayList<>();
		for (BlockPos pos : watch.placedNear(level, home, cfg.houseRadius, HouseScan::furnishing)) {
			if (!kept.contains(pos) && covered(level, pos, cfg.houseRoofScan) && !holdsFragment(level, pos)) {
				found.add(pos.immutable());
			}
		}
		found.sort(Comparator.comparingDouble(p -> p.distSqr(home)));
		return found;
	}

	/** Something the player furnished the house with (not part of its shell, never a sign). */
	public static boolean furnishing(BlockState state) {
		if (state.isAir() || state.is(BlockTags.ALL_SIGNS) || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)) {
			return false;
		}
		return state.hasBlockEntity() || state.is(BlockTags.BEDS) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.CANDLES)
				|| state.getBlock() instanceof CraftingTableBlock || state.getBlock() instanceof BaseTorchBlock
				|| state.getBlock() instanceof LanternBlock || state.getBlock() instanceof FlowerPotBlock || state.getBlock() instanceof AnvilBlock
				|| state.getBlock() instanceof LoomBlock || state.getBlock() instanceof StonecutterBlock || state.getBlock() instanceof GrindstoneBlock
				|| state.getBlock() instanceof ComposterBlock || state.getBlock() instanceof AbstractCauldronBlock;
	}

	/** A solid block somewhere above, within {@code scan} blocks (leaves are not a roof). */
	public static boolean covered(ServerLevel level, BlockPos pos, int scan) {
		for (int up = 1; up <= scan; up++) {
			BlockState above = level.getBlockState(pos.above(up));
			if (!above.isAir() && !above.is(BlockTags.LEAVES) && above.isSolid()) {
				return true;
			}
		}
		return false;
	}

	/** A container or lectern that holds a fragment item: lore's, left alone. */
	static boolean holdsFragment(ServerLevel level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof LecternBlockEntity lectern) {
			return FragmentItems.fragmentId(lectern.getBook()).isPresent();
		}
		if (be instanceof Container container) {
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				if (FragmentItems.fragmentId(container.getItem(slot)).isPresent()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Spots just outside the house's doors (the side away from the base, or the uncovered side), plus the cells beside
	 * and beyond each, where a mob can stand. Without a door, the openings: an uncovered cell next to a covered one.
	 * Nearest door first.
	 */
	public static List<Doorway> doorways(ServerLevel level, BlockPos home, EndingConfig cfg, PlayerWatch watch) {
		Set<BlockPos> used = new LinkedHashSet<>();
		List<Doorway> found = new ArrayList<>();
		List<BlockPos> doors = new ArrayList<>(watch.placedNear(level, home, cfg.houseRadius,
				s -> s.is(BlockTags.DOORS) && s.hasProperty(DoorBlock.HALF) && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER));
		doors.sort(Comparator.comparingDouble(p -> p.distSqr(home)));
		for (BlockPos door : doors) {
			Direction facing = level.getBlockState(door).getValue(DoorBlock.FACING);
			BlockPos a = door.relative(facing);
			BlockPos b = door.relative(facing.getOpposite());
			Direction out = outside(level, home, a, b, cfg) == a ? facing : facing.getOpposite();
			addAround(level, door, out, used, found);
		}
		if (found.isEmpty()) {
			for (BlockPos opening : openings(level, home, cfg)) {
				for (Direction dir : Direction.Plane.HORIZONTAL) {
					BlockPos inside = opening.relative(dir);
					if (covered(level, inside, cfg.houseRoofScan) && standable(level, inside)) {
						addAround(level, inside, dir.getOpposite(), used, found);
						break;
					}
				}
				if (found.size() >= 8) {
					break;
				}
			}
		}
		return found;
	}

	/** Outside the door, then one more out, then beside it on both sides. */
	private static void addAround(ServerLevel level, BlockPos door, Direction out, Set<BlockPos> used, List<Doorway> found) {
		BlockPos front = door.relative(out);
		Direction side = out.getClockWise();
		for (BlockPos spot : List.of(front, front.relative(side), front.relative(side.getOpposite()), front.relative(out))) {
			if (standable(level, spot) && used.add(spot)) {
				found.add(new Doorway(spot.immutable(), door.immutable()));
			}
		}
	}

	/** Of the two cells either side of a door, the one outside: uncovered first, else farther from the base. */
	private static BlockPos outside(ServerLevel level, BlockPos home, BlockPos a, BlockPos b, EndingConfig cfg) {
		boolean aCovered = covered(level, a, cfg.houseRoofScan);
		boolean bCovered = covered(level, b, cfg.houseRoofScan);
		if (aCovered != bCovered) {
			return aCovered ? b : a;
		}
		return a.distSqr(home) >= b.distSqr(home) ? a : b;
	}

	/** Uncovered standable cells next to a covered standable one, around the base. */
	private static List<BlockPos> openings(ServerLevel level, BlockPos home, EndingConfig cfg) {
		List<BlockPos> found = new ArrayList<>();
		int r = cfg.houseRadius;
		for (BlockPos pos : BlockPos.betweenClosed(home.offset(-r, -2, -r), home.offset(r, 2, r))) {
			if (!standable(level, pos) || covered(level, pos, cfg.houseRoofScan)) {
				continue;
			}
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos in = pos.relative(dir);
				if (standable(level, in) && covered(level, in, cfg.houseRoofScan)) {
					found.add(pos.immutable());
					break;
				}
			}
		}
		found.sort(Comparator.comparingDouble(p -> p.distSqr(home)));
		return found;
	}

	/** Two cells of open space with a sturdy floor. */
	static boolean standable(ServerLevel level, BlockPos pos) {
		BlockPos floor = pos.below();
		return level.isLoaded(pos) && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
				&& level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
				&& level.getFluidState(pos).isEmpty();
	}
}
