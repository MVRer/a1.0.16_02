package com.forzacode.a1016_02.world.sc2.s17.books;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * Writes the archive's books from {@link Facts} and a {@link Voice} (docs/SELF_CREATED_2_R3.md, "How a player's record
 * is made", "Variation"): a player's record (an opening, their first day, 6 to 12 entries oldest first with the most
 * telling facts chosen and never two of one kind in a row, one calm wrong line in an edition in two, a closing or this
 * visit's line), an edition (the same, tattered, with an edition note), a day book (the day log's lines; today's breaks
 * off; days to come blank; days before the log "not kept"), and the six's fixed records. Every pick is a hash of (the
 * world's seed, the subject or the day, the kind, the edition, the slot), so two worlds and two editions differ and the
 * same inputs always write the same book. No line twice in a book, "i" at most once, no slot left unfilled (a line is
 * only chosen when its fact fills every slot it holds). Pure and quiet: any thread, never logs.
 */
public final class Composer {
	/** The chance of a wrong line in a record ({@code sc17.y}'s default). */
	public static final double WRONG_CHANCE = 0.5;
	/** Entries a record holds, at least and at most (fewer when the facts are fewer). */
	public static final int MIN_ENTRIES = 6;
	public static final int MAX_ENTRIES = 12;
	/** Entries of one kind a record holds at most. */
	public static final int KIND_CAP = 3;
	/** A day before the log: the one line it reads (the texts have no pool for it). */
	public static final String NOT_KEPT = "not kept.";

	private static final Pattern SLOT = Pattern.compile("\\{([a-z]+)}");

	private Composer() {
	}

	/** A written book before its pages: title, author, generation, and its entries (each a text and its pool's letter). */
	public record Written(String title, String author, int generation, List<String> entries, List<Character> kinds, boolean fixedPages) {
		/** The pages (entries kept whole; the six's pages as written). */
		public List<String> pages() {
			return fixedPages ? Pages.raw(entries) : Pages.paginate(entries);
		}

		/** The book. */
		public ItemStack stack() {
			return Pages.book(title, author, generation, pages());
		}
	}

	// --- records ---

	/**
	 * A player's record, edition {@code edition} (0 the first): an original "by" the player. With {@code visit}, its last
	 * line is this visit's ({@code r}, 17g) instead of a closing.
	 */
	public static Written record(Facts facts, Voice voice, int edition, double wrongChance, boolean visit) {
		return compose(facts, voice, edition, wrongChance, visit, false);
	}

	/** An edition: the record as it stood at {@code edition}, tattered, with an edition note first. */
	public static Written edition(Facts facts, Voice voice, int edition, double wrongChance) {
		return compose(facts, voice, edition, wrongChance, false, true);
	}

	private static Written compose(Facts facts, Voice voice, int edition, double wrongChance, boolean visit, boolean tattered) {
		String title = Pages.title(facts.name());
		int generation = tattered ? Pages.TATTERED : Pages.ORIGINAL;
		if (voice.blank()) {
			return new Written(title, title, generation, List.of(), List.of(), false);
		}
		long who = id(facts.subject());
		Book book = new Book(voice, facts.seed(), who, edition, facts.name());
		// the entries: the most telling facts, a little shuffled per edition, at most KIND_CAP of a kind
		List<Facts.Fact> pool = new ArrayList<>(facts.entries());
		List<Integer> score = new ArrayList<>();
		for (int i = 0; i < pool.size(); i++) {
			score.add(pool.get(i).rank() + (int) Math.floorMod(hash(facts.seed(), who, 'f', edition, i), 40L));
		}
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < pool.size(); i++) {
			order.add(i);
		}
		order.sort(Comparator.comparingInt((Integer i) -> -score.get(i)).thenComparingInt(i -> i));
		int want = MIN_ENTRIES + (int) Math.floorMod(hash(facts.seed(), who, 'N', edition, 0), (long) (MAX_ENTRIES - MIN_ENTRIES + 1));
		List<Facts.Fact> chosen = new ArrayList<>();
		Map<Character, Integer> perKind = new HashMap<>();
		for (int i : order) {
			Facts.Fact fact = pool.get(i);
			if (chosen.size() >= want || perKind.getOrDefault(fact.kind(), 0) >= KIND_CAP || book.eligible(fact.kind(), fact).isEmpty()
					|| chosen.stream().anyMatch(c -> c.kind() == fact.kind() && c.day() == fact.day())) {
				continue;
			}
			chosen.add(fact);
			perKind.merge(fact.kind(), 1, Integer::sum);
		}
		chosen.sort(Comparator.comparingInt(Facts.Fact::day).thenComparingInt(f -> -f.rank()));
		chosen = apart(chosen);
		// the wrong line: never a death, at most one; its day one they played
		Facts.Fact wrong = null;
		if (unit(hash(facts.seed(), who, 'p', edition, 0)) < wrongChance) {
			List<Integer> days = facts.played().isEmpty() ? List.of(facts.today()) : facts.played();
			int day = days.get((int) Math.floorMod(hash(facts.seed(), who, 'p', edition, 1), (long) days.size()));
			Map<String, String> slots = new HashMap<>(Map.of("day", String.valueOf(day)));
			if (!facts.friends().isEmpty()) {
				slots.put("friend", facts.friends().get((int) Math.floorMod(hash(facts.seed(), who, 'p', edition, 2), (long) facts.friends().size())));
			}
			wrong = new Facts.Fact('p', day, 0, 0, Map.copyOf(slots), Set.of(), 0);
		}
		// the opening and first day, the entries, the closing
		book.add('a', new Facts.Fact('a', 0, 0, 0, Map.of(), Set.of(), 0), rotate(facts, 'a', edition));
		book.add('b', facts.firstDayFact(), -1);
		int wrongAt = -1;
		if (wrong != null) {
			// in its day's place among the entries
			wrongAt = 0;
			while (wrongAt < chosen.size() && chosen.get(wrongAt).day() <= wrong.day()) {
				wrongAt++;
			}
		}
		for (int i = 0; i <= chosen.size(); i++) {
			if (i == wrongAt) {
				book.add('p', wrong, -1);
			}
			if (i < chosen.size()) {
				book.add(chosen.get(i).kind(), chosen.get(i), -1);
			}
		}
		Facts.Fact end = new Facts.Fact(visit ? 'r' : 'q', facts.today(), 0, 0, Map.of("day", String.valueOf(facts.today())), Set.of(), 0);
		book.add(end.kind(), end, rotate(facts, end.kind(), edition));
		List<String> entries = new ArrayList<>(book.texts);
		List<Character> kinds = new ArrayList<>(book.kinds);
		if (tattered) {
			String note = book.line('x', new Facts.Fact('x', facts.today(), 0, 0, Map.of("day", String.valueOf(facts.today())), Set.of(), 0), -1);
			if (note != null) {
				entries.addFirst(note);
				kinds.addFirst('x');
			}
		}
		// within sixteen pages: the least telling entries go first
		while (!Pages.fits(entries) && dropOne(entries, kinds)) {
			// dropped one
		}
		return new Written(title, title, generation, List.copyOf(entries), List.copyOf(kinds), false);
	}

	/** An edition note for a record of {@code name} moved on {@code day}, or null if the texts have none. */
	public static @Nullable String note(Voice voice, long seed, String name, int day) {
		if (voice.blank()) {
			return null;
		}
		Book book = new Book(voice, seed, Pages.title(name).hashCode(), day, Pages.title(name));
		return book.line('x', new Facts.Fact('x', day, 0, 0, Map.of("day", String.valueOf(day)), Set.of(), 0), -1);
	}

	/**
	 * Keeps the entries in their days' order with no two of one kind together: on a clash, another kind of the same day
	 * goes between; else the clashing entry is left out while at least {@link #MIN_ENTRIES} remain; else the next other
	 * kind is brought forward.
	 */
	static List<Facts.Fact> apart(List<Facts.Fact> facts) {
		List<Facts.Fact> rest = new ArrayList<>(facts);
		List<Facts.Fact> out = new ArrayList<>();
		while (!rest.isEmpty()) {
			Facts.Fact head = rest.getFirst();
			if (out.isEmpty() || out.getLast().kind() != head.kind()) {
				out.add(rest.removeFirst());
				continue;
			}
			Facts.Fact other = null;
			for (Facts.Fact f : rest) {
				if (f.kind() != head.kind()) {
					other = f;
					break;
				}
			}
			if (other != null && other.day() == head.day()) {
				rest.remove(other);
				out.add(other);
			} else if (other == null || out.size() + rest.size() - 1 >= MIN_ENTRIES) {
				rest.removeFirst();
			} else {
				rest.remove(other);
				out.add(other);
			}
		}
		return out;
	}

	private static boolean dropOne(List<String> entries, List<Character> kinds) {
		// the entries of the lowest rank (statistics first), never the opening, first day, wrong line, closing or note
		int worst = -1;
		int worstRank = Integer.MAX_VALUE;
		for (int i = 0; i < kinds.size(); i++) {
			char k = kinds.get(i);
			if ("abpqrx".indexOf(k) >= 0) {
				continue;
			}
			int rank = rankOf(k);
			if (rank < worstRank) {
				worstRank = rank;
				worst = i;
			}
		}
		if (worst < 0) {
			return false;
		}
		entries.remove(worst);
		kinds.remove(worst);
		return true;
	}

	private static int rankOf(char kind) {
		return switch (kind) {
			case 'd' -> 110;
			case 'c' -> 100;
			case 'j' -> 90;
			case 'i' -> 85;
			case 'k' -> 80;
			case 'l' -> 75;
			case 'n' -> 70;
			case 'h' -> 60;
			case 'g' -> 50;
			case 'f' -> 45;
			case 'e' -> 40;
			default -> 30;
		};
	}

	/**
	 * The rotating pick of an opening or closing: a permutation of the pool per (world, player, kind), stepped one a
	 * edition, so none comes back within the pool's size of editions (at least 30).
	 */
	private static int rotate(Facts facts, char kind, int edition) {
		return Math.max(0, edition) + (int) Math.floorMod(hash(facts.seed(), id(facts.subject()), kind, -1, 0), 1L << 20);
	}

	// --- day books ---

	/**
	 * Day {@code day}'s book ({@code today} the books' day now): a day to come is blank; a day before the log reads
	 * {@link #NOT_KEPT}; a past day is its log's lines (the weather and moon, who came, deaths, what he took, the places;
	 * a day nobody came one line); today's the same so far, then a line that breaks off. "day N", no author, an original.
	 */
	public static Written day(long seed, int day, int today, DayLog.@Nullable Day kept, List<String> names, Voice voice) {
		String title = dayTitle(day);
		if (day > today || voice.blank()) {
			return blankDay(day);
		}
		Book book = new Book(voice, seed, day, 0, names.isEmpty() ? "" : names.getFirst());
		if (kept == null) {
			if (day < today) {
				return new Written(title, "", Pages.ORIGINAL, List.of(NOT_KEPT), List.of('s'), false);
			}
		} else {
			List<String> who = new ArrayList<>();
			List<Boolean> out = new ArrayList<>();
			for (int i : kept.who()) {
				if (i < names.size()) {
					who.add(names.get(i));
					out.add(kept.out(i));
				}
			}
			Set<String> base = new HashSet<>();
			if (kept.weather() >= DayLog.RAIN) {
				base.add("rain");
			}
			if (kept.moonSeen()) {
				base.add("night");
			}
			if (who.size() == 1) {
				base.add("alone");
			}
			if (day == 1) {
				base.add("first");
			}
			String someone = who.isEmpty() ? null : who.getFirst();
			if (who.isEmpty() && kept.deaths().isEmpty() && day < today) {
				book.addDay(Voice.DayKind.NOBODY, Map.of(), base, 0);
			} else {
				Map<String, String> weather = new HashMap<>();
				weather.put("w", weatherKey(kept));
				if (kept.moonSeen()) {
					weather.put("moon", String.valueOf(kept.moon()));
				}
				put(weather, "name", someone);
				book.addDay(Voice.DayKind.WEATHER, weather, base, 0);
				// who came: everyone who played is named (a pair's line, then one each for the rest); a line about the
				// night only for who was out in it
				Set<String> inside = new HashSet<>(base);
				inside.remove("night");
				int from = 0;
				if (who.size() >= 2) {
					Voice.Line pair = book.addDay(Voice.DayKind.WHO, Map.of("name", who.get(0), "friend", who.get(1)),
							out.get(0) && out.get(1) ? base : inside, 0);
					from = pair == null ? 0 : pair.slots().contains("friend") ? 2 : 1;
				}
				for (int i = from; i < who.size(); i++) {
					book.addDay(Voice.DayKind.WHO, Map.of("name", who.get(i)), out.get(i) ? base : inside, 0);
				}
				int nth = 0;
				for (DayLog.Death death : kept.deaths()) {
					nth++;
					Set<String> tags = new HashSet<>(base);
					tags.add("cause:" + death.cause());
					if (death.night()) {
						tags.add("night");
					}
					Map<String, String> slots = new HashMap<>(Map.of("cause", death.cause(), "n", String.valueOf(nth)));
					put(slots, "name", death.player() < names.size() ? names.get(death.player()) : null);
					book.addDay(Voice.DayKind.DEATH, slots, tags, nth);
				}
				if (kept.traces() > 0 && kept.traceWord() != null) {
					Map<String, String> slots = new HashMap<>(Map.of("n", String.valueOf(kept.traces()), kept.traceItem() ? "item" : "block", kept.traceWord()));
					put(slots, "name", someone);
					book.addDay(Voice.DayKind.TRACES, slots, base, kept.traces());
				}
				for (int kind = 16; kind <= 20; kind++) {
					if (kept.entered(kind)) {
						Map<String, String> slots = new HashMap<>(Map.of("place", String.valueOf(kind)));
						put(slots, "name", someone);
						book.addDay(Voice.DayKind.PLACE, slots, base, 0);
					}
				}
			}
		}
		if (day == today) {
			Map<String, String> slots = new HashMap<>();
			slots.put("w", kept == null ? "clear" : weatherKey(kept));
			List<Integer> who = kept == null ? List.of() : kept.who();
			put(slots, "name", !who.isEmpty() && who.getFirst() < names.size() ? names.get(who.getFirst()) : names.isEmpty() ? null : names.getFirst());
			book.add('t', new Facts.Fact('t', day, 0, 0, Map.copyOf(slots), Set.of(), 0), -1);
		}
		return new Written(title, "", Pages.ORIGINAL, List.copyOf(book.texts), List.copyOf(book.kinds), false);
	}

	/** A day to come: titled, blank, no author. */
	public static Written blankDay(int day) {
		return new Written(dayTitle(day), "", Pages.ORIGINAL, List.of(), List.of(), false);
	}

	/** A day book's title. */
	public static String dayTitle(int day) {
		return "day " + day;
	}

	private static String weatherKey(DayLog.Day day) {
		return switch (day.weather()) {
			case DayLog.THUNDER -> "thunder";
			case DayLog.SNOW -> "snow";
			case DayLog.RAIN -> "rain";
			default -> day.fog() ? "fog" : "clear";
		};
	}

	private static void put(Map<String, String> slots, String key, @Nullable String value) {
		if (value != null && !value.isEmpty()) {
			slots.put(key, value);
		}
	}

	// --- the six ---

	/** One of the six's records, its variant by the world's seed (one of three); blank if the texts have none for it. */
	public static Written six(Voice voice, String name, long seed) {
		String title = Pages.title(name);
		List<List<String>> variants = voice.six(name);
		if (variants.isEmpty()) {
			return new Written(title, title, Pages.ORIGINAL, List.of(), List.of(), true);
		}
		List<String> pages = variants.get((int) Math.floorMod(hash(seed, title.hashCode(), 'u', 0, 0), (long) variants.size()));
		return new Written(title, title, Pages.ORIGINAL, pages, List.of(), true);
	}

	// --- picking ---

	/** One book being written: the lines used, whether it said "i", its texts and their kinds. */
	private static final class Book {
		final Voice voice;
		final long seed;
		final long who;
		final int edition;
		final String name;
		final Set<String> used = new HashSet<>();
		final List<String> texts = new ArrayList<>();
		final List<Character> kinds = new ArrayList<>();
		boolean saidI;
		int slot;

		Book(Voice voice, long seed, long who, int edition, String name) {
			this.voice = voice;
			this.seed = seed;
			this.who = who;
			this.edition = edition;
			this.name = name;
		}

		List<Voice.Line> eligible(char kind, Facts.Fact fact) {
			List<Voice.Line> out = new ArrayList<>();
			for (Voice.Line line : voice.pool(kind)) {
				if (fits(line, fact)) {
					out.add(line);
				}
			}
			return out;
		}

		boolean fits(Voice.Line line, Facts.Fact fact) {
			if (line.weight() <= 0 || used.contains(line.text()) || (line.firstPerson() && saidI)) {
				return false;
			}
			for (String slot : line.slots()) {
				if (value(slot, fact).isEmpty()) {
					return false;
				}
			}
			for (String condition : line.conditions()) {
				if (!fact.meets(condition)) {
					return false;
				}
			}
			return true;
		}

		/** Adds a line of {@code kind} for {@code fact} (by {@code rotation} when not negative, else hashed); none if none fits. */
		void add(char kind, Facts.Fact fact, int rotation) {
			String text = line(kind, fact, rotation);
			if (text != null) {
				texts.add(text);
				kinds.add(kind);
			}
		}

		@Nullable String line(char kind, Facts.Fact fact, int rotation) {
			List<Voice.Line> eligible = eligible(kind, fact);
			if (eligible.isEmpty()) {
				return null;
			}
			Voice.Line line;
			int at = slot++;
			if (rotation >= 0) {
				// a fixed permutation of the whole pool, stepped by the edition: the pick skips what does not fit
				List<Voice.Line> all = voice.pool(kind);
				List<Integer> perm = permutation(all.size(), hash(seed, who, kind, -2, 0));
				line = null;
				for (int step = 0; step < all.size() && line == null; step++) {
					Voice.Line candidate = all.get(perm.get(Math.floorMod(rotation + step, all.size())));
					if (eligible.contains(candidate)) {
						line = candidate;
					}
				}
				if (line == null) {
					return null;
				}
			} else {
				line = weighted(eligible, hash(seed, who, kind, edition, at));
			}
			used.add(line.text());
			saidI |= line.firstPerson();
			return fill(line.text(), fact);
		}

		/** Adds a day book line of that kind for these slots; the line used, or null if none fits. */
		Voice.@Nullable Line addDay(Voice.DayKind kind, Map<String, String> slots, Set<String> tags, int n) {
			Facts.Fact fact = new Facts.Fact('s', 0, n, 0, slots, tags, 0);
			List<Voice.Line> eligible = new ArrayList<>();
			for (Voice.Line line : voice.pool('s')) {
				if (line.dayKind() == kind && fits(line, fact)) {
					eligible.add(line);
				}
			}
			if (eligible.isEmpty()) {
				return null;
			}
			Voice.Line line = weighted(eligible, hash(seed, who, 's', edition, slot++));
			used.add(line.text());
			saidI |= line.firstPerson();
			texts.add(fill(line.text(), fact));
			kinds.add('s');
			return line;
		}

		String fill(String text, Facts.Fact fact) {
			Matcher m = SLOT.matcher(text);
			StringBuilder out = new StringBuilder();
			while (m.find()) {
				m.appendReplacement(out, Matcher.quoteReplacement(value(m.group(1), fact)));
			}
			m.appendTail(out);
			return out.toString();
		}

		String value(String slot, Facts.Fact fact) {
			String raw = slot.equals("name") ? fact.slots().getOrDefault("name", name) : fact.slots().getOrDefault(slot, "");
			return switch (slot) {
				case "cause" -> first(voice.words("cause", raw), raw.replace('_', ' '));
				case "place" -> {
					List<String> names = voice.words("place", raw);
					yield names.isEmpty() ? "" : names.get((int) Math.floorMod(hash(seed, raw.hashCode(), 'l', 0, 0), (long) names.size()));
				}
				case "w" -> first(voice.words("weather", raw), raw);
				case "moon" -> first(voice.words("moon", raw), "");
				default -> raw;
			};
		}
	}

	private static String first(List<String> words, String otherwise) {
		return words.isEmpty() ? otherwise : words.getFirst();
	}

	private static Voice.Line weighted(List<Voice.Line> lines, long hash) {
		long total = 0;
		for (Voice.Line line : lines) {
			total += line.weight();
		}
		long r = Math.floorMod(hash, total);
		for (Voice.Line line : lines) {
			r -= line.weight();
			if (r < 0) {
				return line;
			}
		}
		return lines.getLast();
	}

	private static List<Integer> permutation(int size, long hash) {
		List<Integer> out = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			out.add(i);
		}
		long h = hash;
		for (int i = size - 1; i > 0; i--) {
			h = mix(h + i);
			int j = (int) Math.floorMod(h, (long) (i + 1));
			int t = out.get(i);
			out.set(i, out.get(j));
			out.set(j, t);
		}
		return out;
	}

	// --- hashing ---

	static long id(UUID uuid) {
		return uuid.getMostSignificantBits() * 31 + uuid.getLeastSignificantBits();
	}

	static long hash(long seed, long subject, char kind, int edition, int slot) {
		long h = mix(seed ^ 0x9E3779B97F4A7C15L);
		h = mix(h ^ subject);
		h = mix(h ^ kind);
		h = mix(h ^ edition);
		return mix(h ^ slot);
	}

	static double unit(long hash) {
		return (hash >>> 11) * 0x1.0p-53;
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
