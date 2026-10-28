package com.forzacode.a1016_02.world.sc2.s17;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeSet;

import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.s17.books.Books;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * The day books in the well (docs/SELF_CREATED_2_R3.md, "The books", D-114): one written book a day of the world, 48 a
 * gallery from the first gallery under the land down (day {@code d} on gallery {@code kDays - (d - 1) / 48}, slot {@code (d - 1) % 48}
 * across its 8 shelves of 6, {@link Well#dayShelves}). Left at runtime only, while a participant is in the well, on the
 * galleries within {@value #NEAR} of theirs and the ground gallery, a shelf at a time and only while it is out of
 * everyone's view ({@code TraceService.leaveStack}, {@value #CAUSE}: never undone): past days as the day log has them,
 * today's unfinished (R3-B's {@link Books#day}), days to come blank up to {@code sc17.z} days past today, none further.
 * Idempotent by title ({@link Books#dayOf}): a day whose volume is on its shelf is not left again, unless it is
 * {@link Books#stale} (written before its day ended): then it is replaced. A shelf's days are left in their order, so each
 * lands in its own slot (the first empty one) unless something else sits there.
 */
final class DayBooks {
	static final String CAUSE = "world:left/s17/day";
	/** A gallery's days. */
	static final int PER_GALLERY = 48;
	/** A shelf's slots. */
	static final int PER_SHELF = 6;
	/** The galleries within this of a participant's get their volumes. */
	static final int NEAR = 2;
	/**
	 * At most this many books composed in one look (each volume left or read against the log is one): a visit's first look
	 * near five galleries would compose 240 in one tick; the rest come at the next looks.
	 */
	static final int WORK_PER_LOOK = 64;

	private DayBooks() {
	}

	/** Where the days come from: the books' own (R3-B's, live) or a test's. */
	interface Days {
		/** Today (1 the world's first). */
		int today(ServerLevel level);

		/** Day {@code day}'s volume now (past, today, or a day to come: blank). */
		ItemStack book(ServerLevel level, int day);

		/** True if this volume on a shelf should be replaced now. */
		boolean stale(ServerLevel level, ItemStack stack);

		/** R3-B's books. */
		Days LIVE = new Days() {
			@Override
			public int today(ServerLevel level) {
				return Books.today(level.getServer());
			}

			@Override
			public ItemStack book(ServerLevel level, int day) {
				return day > today(level) ? Books.blankDay(day) : Books.day(level, day);
			}

			@Override
			public boolean stale(ServerLevel level, ItemStack stack) {
				return Books.stale(level, stack);
			}
		};
	}

	/** The gallery day {@code day} belongs on. */
	static int galleryOf(Well well, int day) {
		return well.kDays - (day - 1) / PER_GALLERY;
	}

	/** The first day of gallery {@code k}. */
	static int firstDay(Well well, int k) {
		return (well.kDays - k) * PER_GALLERY + 1;
	}

	/** The galleries whose volumes come for participants on these galleries (within {@value #NEAR}, and the ground). */
	static TreeSet<Integer> galleries(Well well, List<Integer> at) {
		TreeSet<Integer> out = new TreeSet<>();
		if (at.isEmpty()) {
			return out;
		}
		out.add(well.kDays);
		for (int g : at) {
			for (int k = g - NEAR; k <= g + NEAR; k++) {
				if (k >= well.kMin && k <= well.kDays) {
					out.add(k);
				}
			}
		}
		return out;
	}

	/**
	 * Leaves what is missing on these galleries' shelves, out of view: how many volumes went. {@code checked}: the days
	 * found fresh this visit, with the day they were found on (so a volume is read against the log once a day a visit).
	 */
	static int place(ServerLevel level, Well well, Iterable<Integer> galleries, Days days, int ahead, Answers.View view, TraceService traces,
			Map<Integer, Integer> checked) {
		int today = days.today(level);
		int last = today + Math.max(0, ahead);
		int left = 0;
		int[] budget = {WORK_PER_LOOK};
		for (int k : galleries) {
			List<BlockPos> shelves = well.dayShelves(k);
			int first = firstDay(well, k);
			for (int s = 0; s < shelves.size(); s++) {
				int base = first + s * PER_SHELF;
				if (base > last) {
					break;
				}
				left += shelf(level, shelves.get(s), base, last, today, days, view, traces, checked, budget);
			}
		}
		return left;
	}

	/** One shelf: days {@code base} to {@code base + 5} (at most {@code last}). */
	private static int shelf(ServerLevel level, BlockPos pos, int base, int last, int today, Days days, Answers.View view, TraceService traces,
			Map<Integer, Integer> checked, int[] budget) {
		if (budget[0] <= 0 || !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof Container shelf)) {
			return 0;
		}
		Map<Integer, Integer> present = new HashMap<>();
		for (int i = 0; i < shelf.getContainerSize(); i++) {
			OptionalInt day = Books.dayOf(shelf.getItem(i));
			if (day.isPresent()) {
				present.putIfAbsent(day.getAsInt(), i);
			}
		}
		List<Integer> stale = new ArrayList<>();
		List<Integer> missing = new ArrayList<>();
		for (int d = base; d < base + PER_SHELF && d <= last; d++) {
			Integer slot = present.get(d);
			if (slot == null) {
				missing.add(d);
			} else if (d <= today && checked.getOrDefault(d, -1) != today && budget[0] > 0) {
				budget[0]--;
				if (days.stale(level, shelf.getItem(slot))) {
					stale.add(d);
				} else {
					checked.put(d, today);
				}
			}
		}
		if (missing.isEmpty() && stale.isEmpty() || budget[0] < missing.size() + stale.size()) {
			return 0;
		}
		if (!view.outOfView(level, List.of(pos))) {
			return 0;
		}
		// The stale ones out first, then every day to leave in its order: each takes the first empty slot, its own.
		for (int d : stale) {
			Records.take(level, pos, present.get(d));
		}
		List<Integer> leave = new ArrayList<>(missing);
		leave.addAll(stale);
		leave.sort(Integer::compare);
		int n = 0;
		for (int d : leave) {
			budget[0]--;
			if (traces.leaveStack(level, pos, days.book(level, d), CAUSE)) {
				n++;
				checked.put(d, today);
			}
		}
		return n;
	}

	/** Every day book on the well's day shelves now (loaded ones): for the debug's counts. */
	static int count(ServerLevel level, Well well) {
		int n = 0;
		for (int k = well.kMin; k <= well.kDays; k++) {
			for (Records.Slot s : Records.books(level, well.dayShelves(k))) {
				n += Books.dayOf(s.stack()).isPresent() ? 1 : 0;
			}
		}
		return n;
	}
}
