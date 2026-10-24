package com.forzacode.a1016_02.world.sc2.s17;

import java.util.LinkedHashSet;
import java.util.Set;

import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * 17a, the records (docs/SELF_CREATED_2.md, feature 17; D-100): every known player has a record on the dais wall, a
 * blank written book titled with their name, left by others ({@value #CAUSE}: never undone) in the first record shelf
 * with room, the first time they are inside an archive whose wall holds none, before they reach the dais and out of
 * everyone's view ({@code TraceService.leaveStack}). Never twice: the wall is read for the title first.
 */
final class Records {
	static final String CAUSE = "world:left/s17/record";

	private Records() {
	}

	/** The titles of every written book on the dais wall now (its loaded shelves). */
	static Set<String> titles(ServerLevel level, ArchivePlan plan) {
		Set<String> out = new LinkedHashSet<>();
		for (BlockPos pos : plan.recordWall()) {
			if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof Container shelf)) {
				continue;
			}
			for (int i = 0; i < shelf.getContainerSize(); i++) {
				ItemStack stack = shelf.getItem(i);
				String title = stack.isEmpty() ? null : ArchiveShape.titleOf(stack);
				if (title != null) {
					out.add(title);
				}
			}
		}
		return out;
	}

	/** The first record shelf with an empty slot, or null (the wall is full, or not loaded). */
	static @Nullable BlockPos firstFree(ServerLevel level, ArchivePlan plan) {
		for (BlockPos pos : plan.recordShelves()) {
			if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Container shelf) {
				for (int i = 0; i < shelf.getContainerSize(); i++) {
					if (shelf.getItem(i).isEmpty()) {
						return pos;
					}
				}
			}
		}
		return null;
	}

	/** Leaves {@code name}'s record if the wall has none: true if it is there now (left now or before). */
	static boolean leave(ServerLevel level, ArchivePlan plan, String name, TraceService traces) {
		if (titles(level, plan).contains(name)) {
			return true;
		}
		BlockPos shelf = firstFree(level, plan);
		return shelf != null && traces.leaveStack(level, shelf, ArchiveShape.record(name), CAUSE);
	}
}
