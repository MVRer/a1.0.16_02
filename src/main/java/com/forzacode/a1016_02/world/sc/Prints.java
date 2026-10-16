package com.forzacode.a1016_02.world.sc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.core.TraceBatch;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * A footprint on the ground, shared by the tracks to a door (09) and the spot where he stood (10). Pure over a
 * {@link BlockGetter}: which removals and conversions one ground cell takes. A print is only ever a removal (D-052:
 * vanilla grass grows back over the dirt within days, so the clue fades):
 * <ul>
 *   <li>a snow layer on the ground: its top layer goes (one layer left fewer, or none);</li>
 *   <li>farmland: its crop goes and it is dirt;</li>
 *   <li>grass, podzol or mycelium: the short grass, fern, flower or leaf litter on it goes and it is dirt;</li>
 *   <li>other dirt with such a plant on it: the plant goes;</li>
 *   <li>anything else (stone, sand, gravel, a path, a plank): no print.</li>
 * </ul>
 */
public final class Prints {
	/** One edit of a print: the block at {@code pos} becomes {@code to}, or goes ({@code to} null). */
	public record Edit(BlockPos pos, @Nullable BlockState to) {
		public boolean removes() {
			return to == null;
		}
	}

	/** How far above and below the expected height {@link #ground} looks. */
	private static final int SEARCH_UP = 3;
	private static final int SEARCH_DOWN = 5;

	private Prints() {
	}

	/** A plant a footstep clears: short grass and ferns (tall too), flowers, dry grass, bushes and leaf litter. */
	public static boolean isPlant(BlockState state) {
		return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)
				|| state.is(Blocks.SHORT_DRY_GRASS) || state.is(Blocks.TALL_DRY_GRASS) || state.is(Blocks.BUSH) || state.is(Blocks.LEAF_LITTER)
				|| state.is(Blocks.PINK_PETALS) || state.is(Blocks.WILDFLOWERS) || state.is(BlockTags.SMALL_FLOWERS)
				|| state.is(BlockTags.FLOWERS) && state.getBlock() instanceof DoublePlantBlock;
	}

	/** Grass-like ground a print bares to dirt. */
	public static boolean isGrass(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM);
	}

	/**
	 * The edits that leave a print on the ground cell {@code ground} (the block a foot stands on), in the order a batch
	 * takes them (what is on top first); empty if this ground takes no print.
	 */
	public static List<Edit> at(BlockGetter level, BlockPos ground) {
		BlockState below = level.getBlockState(ground);
		BlockPos up = ground.above();
		BlockState on = level.getBlockState(up);
		List<Edit> edits = new ArrayList<>(2);
		if (on.is(Blocks.SNOW)) {
			int layers = on.getValue(SnowLayerBlock.LAYERS);
			edits.add(new Edit(up, layers > 1 ? on.setValue(SnowLayerBlock.LAYERS, layers - 1) : null));
			return edits;
		}
		if (below.is(Blocks.FARMLAND)) {
			if (!on.isAir() && on.getCollisionShape(level, up).isEmpty() && on.getFluidState().isEmpty()) {
				edits.add(new Edit(up, null));
			}
			edits.add(new Edit(ground, Blocks.DIRT.defaultBlockState()));
			return edits;
		}
		if (isGrass(below)) {
			if (isPlant(on)) {
				edits.add(new Edit(up, null));
			}
			edits.add(new Edit(ground, Blocks.DIRT.defaultBlockState()));
			return edits;
		}
		if (below.is(BlockTags.DIRT) && isPlant(on)) {
			edits.add(new Edit(up, null));
		}
		return edits;
	}

	/** The plant on {@code ground} as an edit (it goes), or empty: a cleared circle around a print. */
	public static List<Edit> clear(BlockGetter level, BlockPos ground) {
		BlockPos up = ground.above();
		return isPlant(level.getBlockState(up)) ? List.of(new Edit(up, null)) : List.of();
	}

	/**
	 * The ground cell of the column {@code (x, z)} near height {@code yHint}: from {@code yHint + 3} down to
	 * {@code yHint - 5}, the first block a foot could stand on (solid, no fluid, not a plant or a snow layer) with
	 * room above it (air, a plant, a snow layer, a crop: nothing to bump into); null if none.
	 */
	public static @Nullable BlockPos ground(BlockGetter level, int x, int z, int yHint) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, yHint + SEARCH_UP, z);
		BlockState above = level.getBlockState(pos.above());
		for (int y = yHint + SEARCH_UP; y >= yHint - SEARCH_DOWN; y--) {
			pos.setY(y);
			BlockState state = level.getBlockState(pos);
			if (standable(level, pos, state) && open(level, pos.above(), above)) {
				return pos.immutable();
			}
			above = state;
		}
		return null;
	}

	private static boolean standable(BlockGetter level, BlockPos pos, BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.is(Blocks.SNOW) && !isPlant(state)
				&& !state.getCollisionShape(level, pos).isEmpty();
	}

	private static boolean open(BlockGetter level, BlockPos pos, BlockState state) {
		return state.isAir() || state.is(Blocks.SNOW) || state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
	}

	/** Queues these edits on a batch, in order. */
	public static void queue(TraceBatch batch, Collection<Edit> edits) {
		for (Edit edit : edits) {
			if (edit.removes()) {
				batch.remove(edit.pos());
			} else {
				batch.convert(edit.pos(), edit.to());
			}
		}
	}

	/** Every cell these edits change. */
	public static Set<BlockPos> cells(Collection<Edit> edits) {
		Set<BlockPos> cells = new LinkedHashSet<>();
		for (Edit edit : edits) {
			cells.add(edit.pos());
		}
		return cells;
	}

	/** Adds {@code more} to {@code edits}, skipping any cell already edited. */
	public static void addAll(List<Edit> edits, Collection<Edit> more) {
		Set<BlockPos> cells = cells(edits);
		for (Edit edit : more) {
			if (cells.add(edit.pos())) {
				edits.add(edit);
			}
		}
	}
}
