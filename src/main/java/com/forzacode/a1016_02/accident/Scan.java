package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/** Small block questions the trap scanners share. */
public final class Scan {
	private Scan() {
	}

	/** Nothing to collide with and no fluid: air, plants, torches. */
	public static boolean open(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
	}

	/** Nothing to collide with (fluids allowed). */
	public static boolean passable(Level level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	public static boolean fullSolid(Level level, BlockPos pos) {
		return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
	}

	/**
	 * A plain full block he can take: no block entity, not unbreakable, not a fluid, and not a falling block (which
	 * would leave whatever it holds without support).
	 */
	public static boolean takeable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return diggable(level, pos) && !(state.getBlock() instanceof FallingBlock);
	}

	/** Like {@link #takeable} but falling blocks count, for columns taken from the top down. */
	public static boolean diggable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.isAir() && state.isCollisionShapeFullBlock(level, pos) && !state.hasBlockEntity() && state.getFluidState().isEmpty()
				&& state.getDestroySpeed(level, pos) >= 0;
	}

	public static boolean lava(Level level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.LAVA);
	}

	public static boolean water(Level level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.WATER);
	}

	public static boolean waterSource(Level level, BlockPos pos) {
		FluidState fluid = level.getFluidState(pos);
		return fluid.is(FluidTags.WATER) && fluid.isSource();
	}

	public static boolean ore(BlockState state) {
		return state.is(BlockTags.ORES) || state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.COPPER_ORES);
	}

	/** True if the block or any neighbour is an ore. */
	public static boolean oreAround(Level level, BlockPos pos) {
		if (ore(level.getBlockState(pos))) {
			return true;
		}
		for (Direction dir : Direction.values()) {
			if (ore(level.getBlockState(pos.relative(dir)))) {
				return true;
			}
		}
		return false;
	}

	/** Torches of every kind except redstone (they light a base). */
	public static boolean torch(BlockState state) {
		return state.getBlock() instanceof TorchBlock;
	}

	/** How many cells from {@code pos} down have nothing to land on (fluids count as nothing), up to {@code max}. */
	public static int depthBelow(Level level, BlockPos pos, int max) {
		int depth = 0;
		BlockPos.MutableBlockPos cursor = pos.mutable();
		while (depth < max && level.isInsideBuildHeight(cursor.getY()) && passable(level, cursor)) {
			depth++;
			cursor.move(Direction.DOWN);
		}
		return depth;
	}

	/** A column of ground that can be taken from the top so it opens onto a drop or lava. */
	public record Hollow(List<BlockPos> dig, BlockPos cavity, int depth, boolean lava) {
	}

	/**
	 * Takes {@code ground} and up to {@code maxDig - 1} blocks under it until the next cell is lava or a drop of at
	 * least {@code minDrop}. The cell above {@code ground} must not hold a falling block.
	 */
	public static Optional<Hollow> hollow(Level level, BlockPos ground, int maxDig, int minDrop) {
		if (level.getBlockState(ground.above()).getBlock() instanceof FallingBlock) {
			return Optional.empty();
		}
		List<BlockPos> dig = new ArrayList<>();
		BlockPos cursor = ground;
		while (dig.size() < maxDig && diggable(level, cursor)) {
			dig.add(cursor.immutable());
			cursor = cursor.below();
		}
		if (dig.isEmpty()) {
			return Optional.empty();
		}
		if (lava(level, cursor)) {
			return Optional.of(new Hollow(dig, cursor, 1, true));
		}
		if (open(level, cursor)) {
			int depth = depthBelow(level, cursor, minDrop + 64);
			BlockPos bottom = cursor.below(depth);
			boolean lavaBottom = lava(level, bottom.above()) || lava(level, bottom);
			if (depth >= minDrop || lavaBottom) {
				return Optional.of(new Hollow(dig, cursor, depth, lavaBottom));
			}
		}
		return Optional.empty();
	}

	/**
	 * Something solid (not leaves) above head height: a roof, a ceiling, rock. Uses the heightmap rather than sky light,
	 * which lags behind block changes.
	 */
	public static boolean covered(Level level, BlockPos pos) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) > pos.getY() + 2;
	}

	/** Time of day in the overworld, 0 to 23999. */
	public static long timeOfDay(ServerLevel level) {
		return Math.floorMod(level.getServer().overworld().getOverworldClockTime(), 24000L);
	}

	public static boolean night(ServerLevel level, AccidentConfig cfg) {
		long t = timeOfDay(level);
		return t >= cfg.nightStart && t < cfg.nightEnd;
	}

	public static double horizontalDistSqr(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}
}
