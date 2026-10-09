package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

/**
 * A large edit (a tunnel, a cut, a build) with one view check on the bounding box of everything it touches.
 * Queue edits, then {@link #commit()}: either all of them happen, or none.
 * <pre>{@code
 * boolean done = Services.traces().batch(level, "dig:tunnel").remove(a).remove(b).leave(c, torch).commit();
 * }</pre>
 */
public final class TraceBatch {
	private interface Op {
		void apply(TraceService service, ServerLevel level, String cause);
	}

	private final TraceService service;
	private final ServerLevel level;
	private final String cause;
	private final List<Op> ops = new ArrayList<>();
	private @Nullable AABB bounds;
	private boolean committed;

	TraceBatch(TraceService service, ServerLevel level, String cause) {
		this.service = service;
		this.level = level;
		this.cause = cause;
	}

	public TraceBatch remove(BlockPos pos) {
		BlockPos p = pos.immutable();
		include(p);
		ops.add((s, l, c) -> {
			if (!l.getBlockState(p).isAir()) {
				s.applyRemove(l, p, c);
			}
		});
		return this;
	}

	public TraceBatch move(BlockPos from, BlockPos to) {
		BlockPos f = from.immutable();
		BlockPos t = to.immutable();
		include(f);
		include(t);
		ops.add((s, l, c) -> {
			if (TraceService.canMove(l, f, t)) {
				s.applyMove(l, f, t, c);
			}
		});
		return this;
	}

	public TraceBatch convert(BlockPos pos, BlockState newState) {
		BlockPos p = pos.immutable();
		include(p);
		ops.add((s, l, c) -> s.applyConvert(l, p, newState, c));
		return this;
	}

	public TraceBatch leave(BlockPos pos, BlockState state) {
		BlockPos p = pos.immutable();
		include(p);
		ops.add((s, l, c) -> s.applyLeave(l, p, state, c));
		return this;
	}

	/** The box that gets the view check, or null if nothing is queued. */
	public @Nullable AABB bounds() {
		return bounds;
	}

	public int size() {
		return ops.size();
	}

	/** One view check on {@link #bounds()}; if it passes, applies every queued edit. Can only commit once. */
	public boolean commit() {
		if (committed || bounds == null || !service.allowedBatch(level, bounds)) {
			return false;
		}
		committed = true;
		for (Op op : ops) {
			op.apply(service, level, cause);
		}
		return true;
	}

	private void include(BlockPos pos) {
		AABB box = new AABB(pos);
		bounds = bounds == null ? box : bounds.minmax(box);
	}
}
