package com.forzacode.a1016_02.world.sc2.s17.books;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * Feature 17's books (docs/SELF_CREATED_2_R3.md, "The books", D-114): the API the archive's code calls (R3-1 places
 * them; this package only writes them). Every method returns a new written book and changes nothing in the world; the
 * composing ones read the day log, the state and the watch, so call them on the server thread (except {@link #six}, which
 * worldgen may call). Texts come from {@link Voice#FILE}; without it every book is blank (titled, one empty page). Nothing
 * here logs a text (D-080).
 *
 * <ul>
 * <li>{@link #init()}: registers the day log's events once; called from {@code S17.init()}.</li>
 * <li>{@link #record}: a known player's record on the dais wall, edition {@code n} (0 the first, one more each time 17g
 * rewrites it): an original "by" the player, titled with their name (lowercase).</li>
 * <li>{@link #visitLine}: the record 17g leaves on the wall, its last line about this visit.</li>
 * <li>{@link #edition(ServerLevel, ItemStack)}: the record as it stood (the stack taken off the wall, or the one a player
 * carried) as a tattered edition for the stacks; {@link #edition(ServerLevel, UUID, int)} composes one.</li>
 * <li>{@link #day}: day {@code d}'s book (past: the log's; today: breaks off; to come: blank); {@link #blankDay} a day to
 * come; {@link #stale} whether a day book on a shelf should be replaced; {@link #dayOf} the day of a day book.</li>
 * <li>{@link #six}: the records of the six ({@link #SIX}), one of three variants by the world's seed.</li>
 * </ul>
 * Days are the books' days: 1 the world's first ({@link #today} is {@code GameClock.day + 1}). The wrong-line chance is
 * {@code sc17.y} (pass it; {@link Composer#WRONG_CHANCE} by default).
 */
public final class Books {
	/** The six's names, in the dais wall's order. */
	public static final List<String> SIX = Voice.SIX;

	private static final Pattern DAY_TITLE = Pattern.compile("day (\\d{1,9})");

	private Books() {
	}

	/** Registers what feeds the day log (once; later calls do nothing). Call from feature 17's init. */
	public static void init() {
		LogEvents.register();
	}

	/** The books' day now (1 the world's first). */
	public static int today(MinecraftServer server) {
		return (int) GameClock.day(server) + 1;
	}

	// --- records ---

	/** {@code subject}'s record, edition {@code edition}, with the default wrong-line chance. */
	public static ItemStack record(ServerLevel level, UUID subject, int edition) {
		return record(level, subject, edition, Composer.WRONG_CHANCE);
	}

	/** {@code subject}'s record, edition {@code edition}: an original "by" them, closing with a closing line. */
	public static ItemStack record(ServerLevel level, UUID subject, int edition, double wrongChance) {
		return Composer.record(Facts.of(level, subject), Voice.of(level.getServer()), edition, wrongChance, false).stack();
	}

	/** 17g's record with the default wrong-line chance. */
	public static ItemStack visitLine(ServerLevel level, UUID subject, int edition) {
		return visitLine(level, subject, edition, Composer.WRONG_CHANCE);
	}

	/** 17g's record: {@code subject}'s record, edition {@code edition}, its last line about this visit. */
	public static ItemStack visitLine(ServerLevel level, UUID subject, int edition, double wrongChance) {
		return Composer.record(Facts.of(level, subject), Voice.of(level.getServer()), edition, wrongChance, true).stack();
	}

	/** A composed edition: {@code subject}'s record as edition {@code edition} would have read, tattered, with a note. */
	public static ItemStack edition(ServerLevel level, UUID subject, int edition) {
		return Composer.edition(Facts.of(level, subject), Voice.of(level.getServer()), edition, Composer.WRONG_CHANCE).stack();
	}

	/**
	 * The record as it stood, as an edition: the same title, author and pages, tattered, with an edition note after its
	 * text where a page has room (never past sixteen pages). Anything not a written book comes back as a copy.
	 */
	public static ItemStack edition(ServerLevel level, ItemStack asItStood) {
		WrittenBookContent content = asItStood.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (content == null) {
			return asItStood.copy();
		}
		List<String> pages = new ArrayList<>(Pages.text(asItStood));
		String name = content.author().isEmpty() ? content.title().raw() : content.author();
		String note = Composer.note(Voice.of(level.getServer()), level.getSeed(), name, today(level.getServer()));
		if (note != null) {
			String last = pages.isEmpty() ? "" : pages.getLast();
			List<String> lines = last.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(last.split("\n", -1)));
			List<String> more = Pages.lines(note);
			if (!last.isEmpty() && lines.size() + 1 + more.size() <= Pages.LINES) {
				lines.add("");
				lines.addAll(more);
				pages.set(pages.size() - 1, String.join("\n", lines));
			} else if (last.isEmpty() && !pages.isEmpty()) {
				pages.set(pages.size() - 1, String.join("\n", more));
			} else if (pages.size() < Pages.PAGES) {
				pages.add(String.join("\n", more));
			}
		}
		return Pages.book(content.title().raw(), content.author(), Pages.TATTERED, pages);
	}

	// --- day books ---

	/** Day {@code day}'s book now: a past day's log, today's breaking off, a day to come blank; "day N", no author. */
	public static ItemStack day(ServerLevel level, int day) {
		MinecraftServer server = level.getServer();
		DayLog log = DayLog.get(server);
		List<String> names = new ArrayList<>();
		HerobrineState.get(server).players().forEach(p -> names.add(p.name().toLowerCase(Locale.ROOT)));
		return Composer.day(level.getSeed(), day, today(server), log.day(day), names, Voice.of(server)).stack();
	}

	/** A day to come: "day N", blank. */
	public static ItemStack blankDay(int day) {
		return Composer.blankDay(day).stack();
	}

	/** The day of a day book (its title "day N" and no author), or empty for any other stack. */
	public static OptionalInt dayOf(ItemStack stack) {
		WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (content == null || !content.author().isEmpty()) {
			return OptionalInt.empty();
		}
		Matcher m = DAY_TITLE.matcher(content.title().raw());
		return m.matches() ? OptionalInt.of(Integer.parseInt(m.group(1))) : OptionalInt.empty();
	}

	/**
	 * True if a day book on a shelf should be replaced by {@link #day} now: a past day whose text is not the full one (left
	 * before its day ended, or blank), or today's left blank. False for anything else.
	 */
	public static boolean stale(ServerLevel level, ItemStack stack) {
		OptionalInt day = dayOf(stack);
		int today = today(level.getServer());
		if (day.isEmpty() || day.getAsInt() > today) {
			return false;
		}
		List<String> pages = Pages.text(stack);
		boolean blank = pages.stream().allMatch(String::isEmpty);
		List<String> now = Pages.text(day(level, day.getAsInt()));
		if (day.getAsInt() == today) {
			return blank && !now.stream().allMatch(String::isEmpty);
		}
		return !pages.equals(now);
	}

	// --- the six ---

	/** One of the six's records in this world (its seed picks the variant); blank for a name that is not one of them. */
	public static ItemStack six(ServerLevel level, String name) {
		return six(level.getServer(), name, level.getSeed());
	}

	/** One of the six's records for a world of {@code seed}. Any thread (worldgen's too). */
	public static ItemStack six(MinecraftServer server, String name, long seed) {
		return Composer.six(Voice.of(server), name, seed).stack();
	}

	// --- titles ---

	/** A record's title for a player's name (lowercase, at most 32 characters). */
	public static String title(String name) {
		return Pages.title(name);
	}

	/** True if {@code stack} is a written book titled as {@code name}'s record (any case: round 2's are not lowercase). */
	public static boolean isRecordOf(ItemStack stack, String name) {
		WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		return content != null && content.title().raw().toLowerCase(Locale.ROOT).equals(Pages.title(name));
	}
}
