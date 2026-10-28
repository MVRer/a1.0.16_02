package com.forzacode.a1016_02.world.sc2.s17;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.s17.books.Books;
import com.forzacode.a1016_02.world.sc2.s17.books.Pages;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;

import org.jspecify.annotations.Nullable;

/**
 * 17a, the records (docs/SELF_CREATED_2.md, feature 17; D-100; docs/SELF_CREATED_2_R3.md, "The books", D-114): every
 * known player has a record on the dais wall, the book R3-B's {@link Books#record} writes about them, left by others
 * ({@value #CAUSE}: never undone) in the first record shelf with room, the first time they are inside an archive whose
 * wall holds none, before they reach the dais and out of everyone's view ({@code TraceService.leaveStack}). Never twice:
 * the wall is read for the title first ({@link Books#isRecordOf}, any case: round 2's blank records keep theirs).
 *
 * <p>The count of a player's records per archive (the edition {@link Books#record} and 17g's {@link Books#visitLine} take)
 * is kept in the books themselves, not in saved data: each record and edition this code leaves carries its number in its
 * custom data ({@value #NUMBER}); a player's next is one more than the highest number among their record on the wall and
 * their editions in the stacks ({@link ArchivePlan#editionShelves}, {@link ArchivePlan#farShelves}), 0 if there is none.
 * A book without the number (round 2's blank record, a book put there by hand) counts as 0.
 */
final class Records {
	static final String CAUSE = "world:left/s17/record";
	/** 17g's editions moved into the stacks (left by others, never undone). */
	static final String EDITION_CAUSE = "world:left/s17/edition";
	/** A record's or an edition's number in its custom data (opaque). */
	static final String NUMBER = A1016_02.MOD_ID + ":s17";

	private Records() {
	}

	/** A book in a shelf's slot. */
	record Slot(BlockPos pos, int slot, ItemStack stack) {
		int number() {
			return Records.number(stack);
		}
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
		return firstFree(level, plan.recordShelves());
	}

	/** The first of these shelves (loaded) with an empty slot, or null. */
	static @Nullable BlockPos firstFree(ServerLevel level, List<BlockPos> shelves) {
		for (BlockPos pos : shelves) {
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

	/** {@code name}'s record on the dais wall (an original titled with their name), or null (taken, never left, not loaded). */
	static @Nullable Slot onWall(ServerLevel level, ArchivePlan plan, String name) {
		for (Slot s : books(level, plan.recordWall())) {
			if (Books.isRecordOf(s.stack(), name) && generation(s.stack()) != Pages.TATTERED) {
				return s;
			}
		}
		return null;
	}

	/** {@code name}'s editions (tattered, titled with their name) on these shelves, oldest (lowest number) first. */
	static List<Slot> editions(ServerLevel level, List<BlockPos> shelves, String name) {
		List<Slot> out = new ArrayList<>();
		for (Slot s : books(level, shelves)) {
			if (Books.isRecordOf(s.stack(), name) && generation(s.stack()) == Pages.TATTERED) {
				out.add(s);
			}
		}
		out.sort(Comparator.comparingInt(Slot::number));
		return out;
	}

	/** Every editions' shelf of the plan: the stacks' then the far end's. */
	static List<BlockPos> stacks(ArchivePlan plan) {
		List<BlockPos> out = new ArrayList<>(plan.editionShelves());
		out.addAll(plan.farShelves());
		return out;
	}

	/**
	 * The number of {@code name}'s next record in this archive (see the class comment). With their record gone from the
	 * wall but editions in the stacks, the one they took was most likely the one after the editions: the next skips it, so
	 * the wall's new record is never the one in their chest (D-098: records that disagree).
	 */
	static int next(ServerLevel level, ArchivePlan plan, String name) {
		int highest = -1;
		Slot wall = onWall(level, plan, name);
		if (wall != null) {
			highest = wall.number();
		}
		for (Slot s : editions(level, stacks(plan), name)) {
			highest = Math.max(highest, s.number());
		}
		return wall == null && highest >= 0 ? highest + 2 : highest + 1;
	}

	/** Every non-empty slot of these shelves (loaded containers only), in their order. */
	static List<Slot> books(ServerLevel level, List<BlockPos> shelves) {
		List<Slot> out = new ArrayList<>();
		for (BlockPos pos : shelves) {
			if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof Container shelf)) {
				continue;
			}
			for (int i = 0; i < shelf.getContainerSize(); i++) {
				ItemStack stack = shelf.getItem(i);
				if (!stack.isEmpty()) {
					out.add(new Slot(pos, i, stack));
				}
			}
		}
		return out;
	}

	/** A written book's generation, or -1 for anything else. */
	static int generation(ItemStack stack) {
		WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		return content == null ? -1 : content.generation();
	}

	/** A record's or an edition's number (0 without one). */
	static int number(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getIntOr(NUMBER, 0);
	}

	/** {@code stack} with its number set (the same stack). */
	static ItemStack numbered(ItemStack stack, int number) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(NUMBER, number));
		return stack;
	}

	/** Takes a stack out of a shelf's slot, as it was (no ledger: what is left by others is never given back). */
	static ItemStack take(ServerLevel level, BlockPos pos, int slot) {
		if (!(level.getBlockEntity(pos) instanceof Container shelf) || slot < 0 || slot >= shelf.getContainerSize()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = shelf.getItem(slot).copy();
		shelf.setItem(slot, ItemStack.EMPTY);
		shelf.setChanged();
		return stack;
	}

	/** Puts a stack back in a shelf's slot (undoing {@link #take} when what followed could not be done). */
	static void put(ServerLevel level, BlockPos pos, int slot, ItemStack stack) {
		if (level.getBlockEntity(pos) instanceof Container shelf && slot >= 0 && slot < shelf.getContainerSize() && shelf.getItem(slot).isEmpty()) {
			shelf.setItem(slot, stack);
			shelf.setChanged();
		}
	}

	/**
	 * Leaves {@code name}'s record if the wall has none: true if it is there now (left now or before). Its text is R3-B's
	 * ({@link Books#record}, the wrong-line chance {@code wrongChance}), its number the player's next in this archive. The
	 * shelf is asked about first ({@code view}): nothing is composed while it is in view.
	 */
	static boolean leave(ServerLevel level, ArchivePlan plan, UUID uuid, String name, TraceService traces, Answers.View view, double wrongChance) {
		if (onWall(level, plan, name) != null) {
			return true;
		}
		BlockPos shelf = firstFree(level, plan);
		if (shelf == null || !view.outOfView(level, List.of(shelf))) {
			return false;
		}
		int number = next(level, plan, name);
		return traces.leaveStack(level, shelf, numbered(titled(Books.record(level, uuid, number, wrongChance), name), number), CAUSE);
	}

	/**
	 * The composed record if it is {@code name}'s (titled with their name), else their name on a blank one: the books know
	 * a player from the story's state, and a record titled otherwise would never be found on the wall again (one more
	 * every visit). Round 2's records were these blank ones.
	 */
	static ItemStack titled(ItemStack composed, String name) {
		return Books.isRecordOf(composed, name) ? composed : Pages.book(Books.title(name), Books.title(name), Pages.ORIGINAL, List.of(""));
	}
}
