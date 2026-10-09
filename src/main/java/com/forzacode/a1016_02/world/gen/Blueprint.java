package com.forzacode.a1016_02.world.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.forzacode.a1016_02.core.TraceBatch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * A build or carve as an ordered list of block operations in absolute coordinates. Worldgen applies it one chunk
 * at a time ({@link #applyInChunk}), so a stair or a tunnel can cross many chunks and still line up; live code
 * turns it into one {@link TraceBatch} ({@link #queue}).
 */
public final class Blueprint {
	/** One operation. */
	public sealed interface Op permits Put, Clear, Column, Foundation, Chest, Unore {
		BlockPos pos();
	}

	/** Sets a block, replacing what is there. */
	public record Put(BlockPos pos, BlockState state) implements Op {
	}

	/** Removes a block (never bedrock). */
	public record Clear(BlockPos pos) implements Op {
	}

	/**
	 * Clears a column from {@code pos} up to {@code topY}: everything but bedrock, or only plants and tree parts
	 * when {@code vegetationOnly} (the margin around a build, so no tree grows through a wall).
	 */
	public record Column(BlockPos pos, int topY, boolean vegetationOnly) implements Op {
	}

	/** Fills downward from {@code pos} with {@code state} while the block is air, fluid or a plant (at most {@code depth}). */
	public record Foundation(BlockPos pos, BlockState state, int depth) implements Op {
	}

	/** A chest with ordinary contents. */
	public record Chest(BlockPos pos, BlockState state, List<ItemStack> items) implements Op {
	}

	/** Turns an ore into the stone around it (the stair has no ore). */
	public record Unore(BlockPos pos) implements Op {
	}

	private final List<Op> ops = new ArrayList<>();
	private @Nullable BoundingBox box;

	public Blueprint put(BlockPos pos, BlockState state) {
		return add(new Put(pos.immutable(), state));
	}

	public Blueprint clear(BlockPos pos) {
		return add(new Clear(pos.immutable()));
	}

	public Blueprint foundation(BlockPos pos, BlockState state, int depth) {
		return add(new Foundation(pos.immutable(), state, depth));
	}

	public Blueprint chest(BlockPos pos, BlockState state, List<ItemStack> items) {
		return add(new Chest(pos.immutable(), state, List.copyOf(items)));
	}

	public Blueprint unore(BlockPos pos) {
		return add(new Unore(pos.immutable()));
	}

	public Blueprint add(Op op) {
		ops.add(op);
		BoundingBox opBox = new BoundingBox(op.pos());
		if (op instanceof Foundation f) {
			opBox = new BoundingBox(f.pos().getX(), f.pos().getY() - f.depth(), f.pos().getZ(), f.pos().getX(), f.pos().getY(), f.pos().getZ());
		} else if (op instanceof Column c) {
			opBox = new BoundingBox(c.pos().getX(), c.pos().getY(), c.pos().getZ(), c.pos().getX(), Math.max(c.pos().getY(), c.topY()), c.pos().getZ());
		}
		box = box == null ? opBox : BoundingBox.encapsulating(box, opBox);
		return this;
	}

	public List<Op> ops() {
		return Collections.unmodifiableList(ops);
	}

	public boolean isEmpty() {
		return ops.isEmpty();
	}

	/** Every block it may touch, or null if empty. */
	public @Nullable BoundingBox box() {
		return box;
	}

	public boolean intersects(ChunkPos chunk) {
		return box != null && box.intersects(chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), chunk.getMaxBlockZ());
	}

	// --- worldgen ---

	/** Applies every operation whose position is inside {@code chunk}. Worldgen only (chunk generation). */
	public void applyInChunk(WorldGenLevel level, ChunkPos chunk) {
		if (!intersects(chunk)) {
			return;
		}
		for (Op op : ops) {
			BlockPos pos = op.pos();
			if (!chunk.contains(pos) || !canWrite(level, pos) || pos.getY() < level.getMinY() || pos.getY() > level.getMaxY()) {
				continue;
			}
			switch (op) {
				case Put put -> level.setBlock(pos, put.state(), Block.UPDATE_CLIENTS);
				case Clear clear -> {
					BlockState old = level.getBlockState(pos);
					if (!old.isAir() && !old.is(Blocks.BEDROCK)) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
				case Column c -> {
					BlockPos.MutableBlockPos cursor = pos.mutable();
					int top = Math.min(c.topY(), level.getMaxY());
					for (int y = pos.getY(); y <= top; y++) {
						cursor.setY(y);
						BlockState old = level.getBlockState(cursor);
						if (clears(old, c.vegetationOnly())) {
							level.setBlock(cursor, old.getFluidState().createLegacyBlock(), Block.UPDATE_CLIENTS);
						}
					}
				}
				case Foundation f -> {
					BlockPos.MutableBlockPos cursor = pos.mutable();
					for (int n = 0; n <= f.depth() && cursor.getY() >= level.getMinY(); n++) {
						BlockState old = level.getBlockState(cursor);
						if (!fillable(old)) {
							break;
						}
						level.setBlock(cursor, f.state(), Block.UPDATE_CLIENTS);
						cursor.move(0, -1, 0);
					}
				}
				case Chest chest -> {
					level.setBlock(pos, chest.state(), Block.UPDATE_CLIENTS);
					fill(level.getBlockEntity(pos), chest.items());
				}
				case Unore unore -> {
					BlockState old = level.getBlockState(pos);
					BlockState stone = unored(old);
					if (stone != null) {
						level.setBlock(pos, stone, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	/** Applies the whole blueprint, chunk by chunk (tests, and worldgen tools that own every chunk it touches). */
	public void applyAll(WorldGenLevel level) {
		if (box != null) {
			box.intersectingChunks().forEach(chunk -> applyInChunk(level, chunk));
		}
	}

	/** Worldgen may write only in the chunks around the one being decorated. */
	public static boolean canWrite(WorldGenLevel level, BlockPos pos) {
		return !(level instanceof WorldGenRegion region) || region.isWithinWriteZone(pos);
	}

	/** What a column clear removes. */
	static boolean clears(BlockState state, boolean vegetationOnly) {
		if (state.isAir() || state.is(Blocks.BEDROCK)) {
			return false;
		}
		if (vegetationOnly) {
			return Vegetation.dies(state) || Vegetation.isSnowLayer(state);
		}
		return state.getFluidState().isEmpty() || !state.getFluidState().isSource() || state.hasProperty(BlockStateProperties.WATERLOGGED);
	}

	/** Air, fluid, snow layers and plants can be filled by a foundation. */
	public static boolean fillable(BlockState state) {
		return state.isAir() || state.canBeReplaced() || Vegetation.isPlant(state) || !state.getFluidState().isEmpty() && !state.isSolid();
	}

	/** The stone an ore sits in, or null if it is not an ore. */
	public static @Nullable BlockState unored(BlockState state) {
		if (!isOre(state)) {
			return null;
		}
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("deepslate")
				? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}

	public static boolean isOre(BlockState state) {
		return state.is(BlockTags.ORES) && !state.is(Blocks.NETHER_QUARTZ_ORE);
	}

	private static void fill(@Nullable Object blockEntity, List<ItemStack> items) {
		if (blockEntity instanceof Container container) {
			for (int slot = 0; slot < items.size() && slot < container.getContainerSize(); slot++) {
				container.setItem(slot, items.get(slot).copy());
			}
			container.setChanged();
		}
	}

	// --- live ---

	/**
	 * Queues the blueprint into a trace batch: blocks in the way are removed, then the build is "left". A chest's
	 * contents cannot go through the batch; call {@link #fillChests} after a successful commit.
	 */
	public void queue(ServerLevel level, TraceBatch batch) {
		Map<BlockPos, BlockState> planned = new HashMap<>();
		for (Op op : ops) {
			BlockPos pos = op.pos();
			switch (op) {
				case Put put -> place(level, batch, planned, pos, put.state());
				case Chest chest -> place(level, batch, planned, pos, chest.state());
				case Clear clear -> {
					BlockState old = planned.getOrDefault(pos, level.getBlockState(pos));
					if (!old.isAir() && !old.is(Blocks.BEDROCK)) {
						batch.remove(pos);
						planned.put(pos, Blocks.AIR.defaultBlockState());
					}
				}
				case Column c -> {
					for (int y = pos.getY(); y <= Math.min(c.topY(), level.getMaxY()); y++) {
						BlockPos at = new BlockPos(pos.getX(), y, pos.getZ());
						BlockState old = planned.getOrDefault(at, level.getBlockState(at));
						if (clears(old, c.vegetationOnly())) {
							batch.remove(at);
							planned.put(at, old.getFluidState().createLegacyBlock());
						}
					}
				}
				case Foundation f -> {
					BlockPos.MutableBlockPos cursor = pos.mutable();
					for (int n = 0; n <= f.depth() && cursor.getY() >= level.getMinY(); n++) {
						BlockPos at = cursor.immutable();
						BlockState old = planned.getOrDefault(at, level.getBlockState(at));
						if (!fillable(old)) {
							break;
						}
						place(level, batch, planned, at, f.state());
						cursor.move(0, -1, 0);
					}
				}
				case Unore unore -> {
					BlockState old = planned.getOrDefault(pos, level.getBlockState(pos));
					BlockState stone = unored(old);
					if (stone != null) {
						batch.convert(pos, stone);
						planned.put(pos, stone);
					}
				}
			}
		}
	}

	/** Fills the chests of a committed live build. */
	public void fillChests(ServerLevel level) {
		for (Op op : ops) {
			if (op instanceof Chest chest) {
				fill(level.getBlockEntity(chest.pos()), chest.items());
			}
		}
	}

	private static void place(ServerLevel level, TraceBatch batch, Map<BlockPos, BlockState> planned, BlockPos pos, BlockState state) {
		BlockState old = planned.getOrDefault(pos, level.getBlockState(pos));
		if (old == state) {
			return;
		}
		if (!old.isAir() && !(old.canBeReplaced() && !old.hasBlockEntity())) {
			batch.remove(pos);
		}
		batch.leave(pos, state);
		planned.put(pos, state);
	}
}
