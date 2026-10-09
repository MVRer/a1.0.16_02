package com.forzacode.a1016_02.ending;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.forzacode.a1016_02.core.TraceLedger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;

/**
 * Finds the cross accident built over a marked death, from the trace ledger: the cross is one batch of moves with
 * cause {@value #CAUSE} landing near the death spot. Its bottom block is the lowest cell of the post (the column with
 * the most cells; the arms stick out one block each side).
 */
public final class CrossFinder {
	/** Accident's ledger cause for the cross's moves. */
	public static final String CAUSE = "accident:cross";
	/** Accident looks this far around the death for a base, and the post is at most 4 high. */
	public static final int SEARCH_RADIUS = 10;

	private CrossFinder() {
	}

	/** The newest cross within {@code radius} of the death, or empty if none stands yet. */
	public static Optional<GlobalPos> find(List<TraceLedger.Entry> entries, GlobalPos death, int radius) {
		List<BlockPos> cells = new ArrayList<>();
		for (int i = entries.size() - 1; i >= 0; i--) {
			TraceLedger.Entry entry = entries.get(i);
			boolean ours = entry.cause().equals(CAUSE) || entry.cause().startsWith(CAUSE + "/");
			if (!cells.isEmpty() && !ours) {
				break;
			}
			if (!ours || !entry.cause().equals(CAUSE) || entry.kind() != TraceLedger.Kind.MOVE || entry.to().isEmpty()
					|| !entry.pos().dimension().equals(death.dimension())) {
				continue;
			}
			BlockPos to = entry.to().get();
			if (Math.abs(to.getX() - death.pos().getX()) > radius || Math.abs(to.getZ() - death.pos().getZ()) > radius
					|| Math.abs(to.getY() - death.pos().getY()) > radius) {
				continue;
			}
			cells.add(to);
		}
		if (cells.isEmpty()) {
			return Optional.empty();
		}
		Map<Long, Integer> columns = new HashMap<>();
		for (BlockPos cell : cells) {
			columns.merge(BlockPos.asLong(cell.getX(), 0, cell.getZ()), 1, Integer::sum);
		}
		long post = columns.entrySet().stream().max(Map.Entry.<Long, Integer>comparingByValue()).orElseThrow().getKey();
		return cells.stream()
				.filter(c -> BlockPos.asLong(c.getX(), 0, c.getZ()) == post)
				.min(Comparator.comparingInt(BlockPos::getY))
				.map(base -> GlobalPos.of(death.dimension(), base));
	}
}
