package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * A large edit (a tunnel, a cut, a build) checked and applied as one: queue edits, then {@link #commit()}.
 * Either all of them happen, or none. The view check looks at every queued block that can be seen (has a
 * see-through neighbour), plus the blocks that would break or change shape with them.
 * <pre>{@code
 * boolean done = Services.traces().batch(level, "dig:tunnel").remove(a).remove(b).leave(c, torch).commit();
 * }</pre>
 */
public final class TraceBatch {
	/** One queued edit. */
	sealed interface Op permits Remove, Move, Convert, Leave, Restore {
	}

	record Remove(BlockPos pos) implements Op {
	}

	record Move(BlockPos from, BlockPos to) implements Op {
	}

	record Convert(BlockPos pos, BlockState state) implements Op {
	}

	/** A block "left by others", with optional block entity data. Not recorded in the ledger. */
	record Leave(BlockPos pos, BlockState state, @Nullable CompoundTag data) implements Op {
	}

	/** A block put back (restoreBlock, figureFill): placed like {@link Leave}, and it must survive where it goes. */
	record Restore(BlockPos pos, BlockState state, @Nullable CompoundTag data) implements Op {
	}

	private final TraceService service;
	private final ServerLevel level;
	private final String cause;
	private final List<Op> ops = new ArrayList<>();
	private boolean committed;

	TraceBatch(TraceService service, ServerLevel level, String cause) {
		this.service = service;
		this.level = level;
		this.cause = cause;
	}

	/** Removes the block silently. Skipped if it is already air when the batch runs. */
	public TraceBatch remove(BlockPos pos) {
		ops.add(new Remove(pos.immutable()));
		return this;
	}

	/** Moves a block (with its block entity data). The commit fails if {@code to} is not replaceable then. */
	public TraceBatch move(BlockPos from, BlockPos to) {
		ops.add(new Move(from.immutable(), to.immutable()));
		return this;
	}

	public TraceBatch convert(BlockPos pos, BlockState newState) {
		ops.add(new Convert(pos.immutable(), newState));
		return this;
	}

	/** Places a block "left by others". The commit fails if the target is not replaceable (air, plants, snow, fluid). */
	public TraceBatch leave(BlockPos pos, BlockState state) {
		return leave(pos, state, null);
	}

	/**
	 * Places a block "left by others" with its block entity contents ({@code BlockEntity#saveCustomOnly} data: a
	 * chest's {@code Items}, a sign's {@code front_text}). The commit fails if the target is not replaceable, or if
	 * there is data but the block has no block entity.
	 */
	public TraceBatch leave(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntityData) {
		ops.add(new Leave(pos.immutable(), state, blockEntityData == null ? null : blockEntityData.copy()));
		return this;
	}

	public int size() {
		return ops.size();
	}

	/**
	 * Plans every edit and the blocks that depend on them, checks the view once, then applies. Returns false and
	 * changes nothing if anything involved is in view (try again later). Succeeds at most once.
	 */
	public boolean commit() {
		if (committed || ops.isEmpty()) {
			return false;
		}
		committed = service.execute(level, cause, ops);
		return committed;
	}
}
