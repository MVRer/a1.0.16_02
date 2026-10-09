package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * One lore world edit, checked and applied as a single {@link TraceBatch}: his removals and moves, plus what
 * others left ({@code leave}). Block entity contents of left blocks (a chest's book, a sign's text, a jukebox's
 * disc) are filled right after the commit, in the same tick, because {@link TraceService#leave} takes no block
 * entity data (requested as a core change). Causes: {@code lore:his/<id>} for his edits, {@code lore:left/<id>}
 * for what others left (Ending D should not undo those).
 */
final class Build {
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
		BlockState placed = touched.contains(pos) ? state : withWater(pos, state);
		batch.leave(pos, placed);
		touched.add(pos.immutable());
		return this;
	}

	/** Leaves a chest facing {@code facing} with these stacks in its first slots. */
	Build chest(BlockPos pos, Direction facing, List<ItemStack> contents) {
		leave(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
		BlockPos at = pos.immutable();
		after.add(() -> {
			if (level.getBlockEntity(at) instanceof Container container) {
				for (int slot = 0; slot < contents.size() && slot < container.getContainerSize(); slot++) {
					container.setItem(slot, contents.get(slot).copy());
				}
				container.setChanged();
			}
		});
		return this;
	}

	/** Leaves a sign block (standing or wall) with this front text. */
	Build sign(BlockPos pos, BlockState signState, SignText text) {
		leave(pos, signState);
		BlockPos at = pos.immutable();
		after.add(() -> {
			if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
				sign.setText(text, SignTextSlot.FRONT);
			}
		});
		return this;
	}

	/** Leaves a jukebox holding {@code disc}, not playing. */
	Build jukebox(BlockPos pos, ItemStack disc) {
		leave(pos, Blocks.JUKEBOX.defaultBlockState().setValue(JukeboxBlock.HAS_RECORD, true));
		BlockPos at = pos.immutable();
		after.add(() -> {
			if (level.getBlockEntity(at) instanceof JukeboxBlockEntity jukebox) {
				Tag item = ItemStack.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), disc).getOrThrow();
				CompoundTag tag = new CompoundTag();
				tag.put("RecordItem", item);
				// Loading without "ticks_since_song_started" sets the disc without playing it (no sound, notes or events).
				jukebox.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
				jukebox.setChanged();
			}
		});
		return this;
	}

	/** Runs after a successful commit (for example filling a block entity). */
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

	/** Puts a stack into the first empty slot of an existing container, only if the container is out of view. */
	static boolean insert(TraceService traces, ServerLevel level, BlockPos pos, ItemStack stack) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof Container container)) {
			return false;
		}
		if (traces != Services.traces().forced() && !traces.isOutOfView(level, List.of(pos))) {
			return false;
		}
		if (container instanceof net.minecraft.world.RandomizableContainer randomizable && randomizable.getLootTable() != null) {
			randomizable.unpackLootTable(null);
		}
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (container.getItem(slot).isEmpty()) {
				container.setItem(slot, stack.copy());
				container.setChanged();
				A1016_02.LOGGER.debug("[a1016] lore: left {} in the container at {}", stack, pos);
				return true;
			}
		}
		return false;
	}

	private BlockState withWater(BlockPos pos, BlockState state) {
		if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
			boolean water = level.getFluidState(pos).is(Fluids.WATER) && level.getFluidState(pos).isSource();
			return state.setValue(BlockStateProperties.WATERLOGGED, water);
		}
		return state;
	}
}
