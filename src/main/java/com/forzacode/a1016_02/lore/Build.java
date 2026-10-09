package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

import org.jspecify.annotations.Nullable;

/**
 * One lore world edit, checked and applied as a single {@link TraceBatch}: his removals and moves, plus what
 * others left ({@code leave}, with its block entity contents: a chest's book, a sign's text, a jukebox's disc).
 * Causes: {@code lore:his/<id>} for his edits, {@code lore:left/<id>} for what others left (Ending D does not undo
 * those).
 */
final class Build {
	private static final int CHEST_SLOTS = 27;
	private static final int FURNACE_SLOTS = 3;

	private final ServerLevel level;
	private final TraceBatch batch;
	private final List<Runnable> after = new ArrayList<>();
	private final List<BlockPos> touched = new ArrayList<>();

	private Build(TraceService traces, ServerLevel level, String cause) {
		this.level = level;
		this.batch = traces.batch(level, cause);
	}

	/** A batch for what others left behind. */
	static Build left(TraceService traces, ServerLevel level, String fragmentId) {
		return new Build(traces, level, "lore:left/" + fragmentId);
	}

	/** A batch for his own edits (tunnels, bare trees, moved blocks). */
	static Build his(TraceService traces, ServerLevel level, String fragmentId) {
		return new Build(traces, level, "lore:his/" + fragmentId);
	}

	Build remove(BlockPos pos) {
		batch.remove(pos);
		touched.add(pos.immutable());
		return this;
	}

	Build move(BlockPos from, BlockPos to) {
		batch.move(from, to);
		touched.add(from.immutable());
		touched.add(to.immutable());
		return this;
	}

	Build convert(BlockPos pos, BlockState state) {
		batch.convert(pos, state);
		touched.add(pos.immutable());
		return this;
	}

	/** Leaves a block; a waterloggable one left straight into still water is waterlogged. */
	Build leave(BlockPos pos, BlockState state) {
		return leave(pos, state, null);
	}

	/** Leaves a block with its block entity contents ({@code saveCustomOnly} data, see {@link BlockEntityData}). */
	Build leave(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntityData) {
		BlockState placed = touched.contains(pos) ? state : withWater(pos, state);
		batch.leave(pos, placed, blockEntityData);
		touched.add(pos.immutable());
		return this;
	}

	/** Leaves a chest facing {@code facing} with these stacks in its first slots. */
	Build chest(BlockPos pos, Direction facing, List<ItemStack> contents) {
		return leave(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing),
				BlockEntityData.items(level.registryAccess(), CHEST_SLOTS, contents));
	}

	/** Leaves a furnace (or any 3-slot cooker) with these stacks: input, fuel, result. */
	Build furnace(BlockPos pos, BlockState state, List<ItemStack> contents) {
		return leave(pos, state, BlockEntityData.items(level.registryAccess(), FURNACE_SLOTS, contents));
	}

	/** Leaves a sign block (standing or wall) with this front text. */
	Build sign(BlockPos pos, BlockState signState, SignText text) {
		return sign(pos, signState, text, false);
	}

	/** Leaves a sign block with this front text, waxed (nobody can edit it) or not. */
	Build sign(BlockPos pos, BlockState signState, SignText text, boolean waxed) {
		return leave(pos, signState, BlockEntityData.sign(level.registryAccess(), text, waxed));
	}

	/** Leaves a jukebox holding {@code disc}, not playing. */
	Build jukebox(BlockPos pos, ItemStack disc) {
		return leave(pos, Blocks.JUKEBOX.defaultBlockState().setValue(JukeboxBlock.HAS_RECORD, true), BlockEntityData.jukebox(level.registryAccess(), disc));
	}

	/** Runs after a successful commit (bookkeeping only; never a world change). */
	Build then(Consumer<ServerLevel> action) {
		after.add(() -> action.accept(level));
		return this;
	}

	int size() {
		return batch.size();
	}

	Collection<BlockPos> touched() {
		return touched;
	}

	/** Checks the view once and applies everything, or nothing. */
	boolean commit() {
		if (!batch.commit()) {
			return false;
		}
		for (Runnable action : after) {
			action.run();
		}
		return true;
	}

	/** Puts a stack "left by others" into the first empty slot of an existing container, out of view. */
	static boolean insert(TraceService traces, ServerLevel level, BlockPos pos, ItemStack stack, String fragmentId) {
		return traces.leaveStack(level, pos, stack, "lore:left/" + fragmentId);
	}

	private BlockState withWater(BlockPos pos, BlockState state) {
		if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
			boolean water = level.getFluidState(pos).is(Fluids.WATER) && level.getFluidState(pos).isSource();
			return state.setValue(BlockStateProperties.WATERLOGGED, water);
		}
		return state;
	}
}
