package com.forzacode.a1016_02.accident;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceBatch;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** One planned world edit. Every one goes through {@link com.forzacode.a1016_02.core.TraceService}. */
public sealed interface TraceOp {
	record Remove(BlockPos pos) implements TraceOp {
	}

	record Move(BlockPos from, BlockPos to) implements TraceOp {
	}

	static TraceOp remove(BlockPos pos) {
		return new Remove(pos.immutable());
	}

	static TraceOp move(BlockPos from, BlockPos to) {
		return new Move(from.immutable(), to.immutable());
	}

	/** Every block these ops touch. */
	static Set<BlockPos> positions(Collection<TraceOp> ops) {
		Set<BlockPos> all = new LinkedHashSet<>();
		for (TraceOp op : ops) {
			switch (op) {
				case Remove(BlockPos pos) -> all.add(pos);
				case Move(BlockPos from, BlockPos to) -> {
					all.add(from);
					all.add(to);
				}
			}
		}
		return all;
	}

	/** The blocks taken away (removed, or moved from). */
	static List<BlockPos> taken(Collection<TraceOp> ops) {
		return ops.stream().map(op -> switch (op) {
			case Remove(BlockPos pos) -> pos;
			case Move(BlockPos from, BlockPos to) -> from;
		}).toList();
	}

	/**
	 * Applies the ops as one trace batch, only if the gate says every touched block is out of view (core checks
	 * again, with dependents). All or nothing.
	 */
	static boolean apply(ServerLevel level, ViewGate view, String cause, List<TraceOp> ops) {
		if (ops.isEmpty() || !view.outOfView(level, positions(ops))) {
			return false;
		}
		TraceBatch batch = Services.traces().batch(level, cause);
		for (TraceOp op : ops) {
			switch (op) {
				case Remove(BlockPos pos) -> batch.remove(pos);
				case Move(BlockPos from, BlockPos to) -> batch.move(from, to);
			}
		}
		return batch.commit();
	}
}
