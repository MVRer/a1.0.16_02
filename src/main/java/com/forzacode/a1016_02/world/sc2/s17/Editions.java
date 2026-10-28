package com.forzacode.a1016_02.world.sc2.s17;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.sc2.s17.books.Books;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 17g, your record, rewritten (docs/SELF_CREATED_2_R3.md, "Answers (17)"; a rule like 17a): once in a visit a player has
 * stood within {@value #NEAR} of the records wall and then gone {@value #AWAY} or more from it, with the wall out of
 * everyone's view, their record on the wall is replaced by a new one whose last line is about this visit
 * ({@link Books#visitLine}, their next number), and the one it replaces is moved up into the stacks as an edition
 * ({@link Books#edition(ServerLevel, ItemStack)}, its number kept) in the first free slot of
 * {@link ArchivePlan#editionShelves} (nearest the dais first). A player keeps at most {@code sc17.x} editions there: past
 * that their oldest move to the far end's highest shelves ({@link ArchivePlan#farShelves}), and when those are full they
 * are gone. With the stacks full of others' editions, the edition goes to the far shelves, else nowhere. If they took
 * their record away, the new one is just left on the wall (17a's rule) and nothing moves. Version 3 only (round 2's
 * archives have no stacks). Everything is left by others ({@value Records#CAUSE}, {@value Records#EDITION_CAUSE}): never
 * undone, through {@code TraceService.leaveStack}, out of everyone's view.
 */
final class Editions {
	/** Within this of the records wall (blocks)... */
	static final double NEAR = 6.0;
	/** ... then this far from it. */
	static final double AWAY = 24.0;

	private Editions() {
	}

	/** How far {@code at} is from the nearest shelf of the dais wall. */
	static double fromWall(ArchivePlan plan, Vec3 at) {
		double best = Double.MAX_VALUE;
		for (BlockPos pos : plan.recordWall()) {
			best = Math.min(best, Vec3.atCenterOf(pos).distanceTo(at));
		}
		return best;
	}

	/**
	 * What a rewrite will do, planned before anything moves so its shelves can be asked about first.
	 *
	 * @param record    their record on the wall (null: taken; the new one is left at {@code leaveAt})
	 * @param leaveAt   the wall shelf the new record goes into
	 * @param out       their oldest editions to move out of the stacks, oldest first
	 * @param outTo     where each goes (a far shelf), or null (gone)
	 * @param editionTo where the replaced record goes as an edition, or null (nowhere)
	 * @param number    the new record's number
	 */
	record Plan(Records.@Nullable Slot record, BlockPos leaveAt, List<Records.Slot> out, List<@Nullable BlockPos> outTo, @Nullable BlockPos editionTo,
			int number) {
		/** Every shelf it touches. */
		List<BlockPos> cells() {
			List<BlockPos> cells = new ArrayList<>();
			add(cells, leaveAt);
			add(cells, editionTo);
			out.forEach(s -> add(cells, s.pos()));
			outTo.forEach(p -> add(cells, p));
			return cells;
		}

		private static void add(List<BlockPos> cells, @Nullable BlockPos p) {
			if (p != null && !cells.contains(p)) {
				cells.add(p);
			}
		}
	}

	/** The plan for {@code name}'s rewrite now, or null if it cannot be done (no wall shelf free, not loaded). */
	static @Nullable Plan plan(ServerLevel level, ArchivePlan plan, String name, int editionsMax) {
		int number = Records.next(level, plan, name);
		Records.Slot record = Records.onWall(level, plan, name);
		if (record == null) {
			BlockPos free = Records.firstFree(level, plan);
			return free == null ? null : new Plan(null, free, List.of(), List.of(), null, number);
		}
		List<BlockPos> stacks = plan.editionShelves();
		List<Records.Slot> mine = Records.editions(level, stacks, name);
		int over = mine.size() + 1 - Math.max(1, editionsMax);
		List<Records.Slot> out = over > 0 ? List.copyOf(mine.subList(0, Math.min(over, mine.size()))) : List.of();
		Map<BlockPos, Integer> farFree = free(level, plan.farShelves());
		List<BlockPos> outTo = new ArrayList<>();
		for (int i = 0; i < out.size(); i++) {
			outTo.add(take(farFree));
		}
		BlockPos to = Records.firstFree(level, stacks);
		if (to == null && !out.isEmpty()) {
			to = out.getFirst().pos(); // its slot, freed first
		}
		if (to == null) {
			to = take(farFree); // the stacks full of others' editions (null: it is gone)
		}
		return new Plan(record, record.pos(), out, outTo, to, number);
	}

	/** The empty slots of each of these shelves (loaded ones), in their order. */
	private static Map<BlockPos, Integer> free(ServerLevel level, List<BlockPos> shelves) {
		Map<BlockPos, Integer> out = new LinkedHashMap<>();
		for (BlockPos pos : shelves) {
			if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Container shelf) {
				int n = 0;
				for (int i = 0; i < shelf.getContainerSize(); i++) {
					n += shelf.getItem(i).isEmpty() ? 1 : 0;
				}
				out.put(pos, n);
			}
		}
		return out;
	}

	/** The first shelf with an empty slot left, that slot taken; null if none. */
	private static @Nullable BlockPos take(Map<BlockPos, Integer> free) {
		for (Map.Entry<BlockPos, Integer> e : free.entrySet()) {
			if (e.getValue() > 0) {
				e.setValue(e.getValue() - 1);
				return e.getKey();
			}
		}
		return null;
	}

	/**
	 * Does a planned rewrite for this player now: true if their new record is on the wall. The caller asked about the
	 * plan's shelves (out of everyone's view); {@code traces} asks again for what it leaves.
	 */
	static boolean commit(ServerLevel level, Plan p, UUID uuid, String name, double wrongChance, TraceService traces) {
		ItemStack fresh = Records.numbered(Records.titled(Books.visitLine(level, uuid, p.number(), wrongChance), name), p.number());
		if (p.record() == null) {
			return traces.leaveStack(level, p.leaveAt(), fresh, Records.CAUSE);
		}
		// The wall first: if the new one cannot be left, nothing else moves.
		ItemStack old = Records.take(level, p.record().pos(), p.record().slot());
		if (old.isEmpty()) {
			return false;
		}
		if (!traces.leaveStack(level, p.record().pos(), fresh, Records.CAUSE)) {
			Records.put(level, p.record().pos(), p.record().slot(), old);
			return false;
		}
		for (int i = 0; i < p.out().size(); i++) {
			Records.Slot s = p.out().get(i);
			ItemStack moved = Records.take(level, s.pos(), s.slot());
			BlockPos to = p.outTo().get(i);
			if (!moved.isEmpty() && to != null) {
				traces.leaveStack(level, to, moved, Records.EDITION_CAUSE);
			}
		}
		if (p.editionTo() != null) {
			ItemStack edition = Records.numbered(Books.edition(level, old), Records.number(old));
			traces.leaveStack(level, p.editionTo(), edition, Records.EDITION_CAUSE);
		}
		return true;
	}
}
