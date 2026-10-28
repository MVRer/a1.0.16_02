package com.forzacode.a1016_02.world.sc2.s17.books;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.ScLog;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import org.jspecify.annotations.Nullable;

/**
 * The texts of the archive's books (docs/SELF_CREATED_2_R3.md, "The texts file"): {@value #FILE} read leniently into
 * pools of lines by letter ({@code a} openings to {@code x} edition notes), the six's fixed records ({@code u}) and the
 * word maps ({@code map}: cause, place, weather, moon). A line that cannot be used (no text, an unknown slot or condition)
 * is left out, never half used; a file that is missing or cannot be parsed gives {@link #EMPTY}, which writes blank
 * pages. Neither ever throws, and what is logged is neutral (counts only, never a text; D-080). Immutable once made: any
 * thread. {@link #validate} is the writer's mechanical check (the game tests run it over the real file).
 */
public final class Voice {
	/** The texts file, in the mod's data. */
	public static final String FILE = "data/a1016_02/world/sc2/b17.json";
	static final Identifier ID = A1016_02.id("world/sc2/b17.json");

	/** The slots a line may hold. */
	public static final Set<String> SLOTS = Set.of("name", "friend", "day", "n", "block", "item", "biome", "x", "z", "cause", "place", "w",
			"moon");
	/** The pools by letter, and the fewest lines each must hold (the plan's minimums; {@code l}'s per kind is the map's). */
	public static final Map<Character, Integer> MINIMUMS;
	/** Words a text never holds (the voice's rules: no "you", nothing of his, of the mod or of the world outside). */
	public static final Set<String> FORBIDDEN = Set.of("herobrine", "brother", "you", "your", "yours", "yourself", "minecraft", "mojang", "notch",
			"mod", "mods", "jar", "computer", "server", "ending", "endings", "fragment", "fragments", "java");
	/** The names of the six, whose records are fixed ({@code u}). */
	public static final List<String> SIX = List.of("kirbyfan_92", "stoneslab", "dyl4n_m", "oreo_tm", "pk_alpha", "t0rch");

	/** The kinds of a day book's lines, read from their slots (the writer's convention). */
	public enum DayKind {
		WEATHER(20), WHO(15), DEATH(15), TRACES(10), PLACE(10), NOBODY(10);

		final int minimum;

		DayKind(int minimum) {
			this.minimum = minimum;
		}
	}

	/** A line: its text with slots, its weight, its conditions, the slots it holds, whether it says "i". */
	public record Line(String text, int weight, List<String> conditions, Set<String> slots, boolean firstPerson) {
		/** A day book line's kind by its slots, or null for one that is none. */
		public @Nullable DayKind dayKind() {
			if (slots.contains("cause")) {
				return DayKind.DEATH;
			}
			if (slots.contains("place")) {
				return DayKind.PLACE;
			}
			if (slots.contains("n") && (slots.contains("block") || slots.contains("item"))) {
				return DayKind.TRACES;
			}
			if (slots.contains("w") || slots.contains("moon")) {
				return DayKind.WEATHER;
			}
			if (slots.contains("name")) {
				return DayKind.WHO;
			}
			return slots.isEmpty() ? DayKind.NOBODY : null;
		}
	}

	/** No texts: every book it writes is blank. */
	public static final Voice EMPTY = new Voice(Map.of(), Map.of(), Map.of(), 0);

	static {
		Map<Character, Integer> min = new LinkedHashMap<>();
		String letters = "abcdefghijklmnpqrstx";
		int[] counts = {30, 15, 30, 12, 20, 20, 20, 15, 20, 30, 20, 20, 25, 15, 30, 30, 20, 80, 15, 10};
		for (int i = 0; i < letters.length(); i++) {
			min.put(letters.charAt(i), counts[i]);
		}
		MINIMUMS = Map.copyOf(min);
	}

	private static final Pattern SLOT = Pattern.compile("\\{([a-z]+)}");
	private static final Pattern WORD = Pattern.compile("[a-z0-9']+");
	private static final Pattern CONDITION = Pattern.compile("friend|night|first|rain|alone|cause:[a-z0-9_]+|(n|away)>=\\d{1,6}");

	private final Map<Character, List<Line>> pools;
	private final Map<String, List<List<String>>> six;
	private final Map<String, Map<String, List<String>>> maps;
	private final int skipped;

	private Voice(Map<Character, List<Line>> pools, Map<String, List<List<String>>> six, Map<String, Map<String, List<String>>> maps, int skipped) {
		this.pools = pools;
		this.six = six;
		this.maps = maps;
		this.skipped = skipped;
	}

	/** The usable lines of a pool, in the file's order (empty for none). */
	public List<Line> pool(char letter) {
		return pools.getOrDefault(letter, List.of());
	}

	/** The six's variants for {@code name} (each a list of pages), empty for none. */
	public List<List<String>> six(String name) {
		return six.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
	}

	/** The words {@code map.<table>} gives {@code key}, empty for none. */
	public List<String> words(String table, String key) {
		Map<String, List<String>> map = maps.get(table);
		return map == null ? List.of() : map.getOrDefault(key, List.of());
	}

	/** Entries the reading left out (malformed, or an unknown slot or condition). */
	public int skipped() {
		return skipped;
	}

	/** True for {@link #EMPTY} or a file with no usable line. */
	public boolean blank() {
		return pools.isEmpty();
	}

	// --- reading ---

	private static final Object LOCK = new Object();
	private static volatile @Nullable Cached cache;

	private record Cached(ResourceManager manager, Voice voice) {
	}

	/**
	 * The server's texts: read once per resource manager (a data pack reload reads them again), else {@link #EMPTY}, logged
	 * neutrally. Any thread (worldgen's included).
	 */
	public static Voice of(MinecraftServer server) {
		ResourceManager manager = server.getResourceManager();
		Cached seen = cache;
		if (seen != null && seen.manager() == manager) {
			return seen.voice();
		}
		synchronized (LOCK) {
			seen = cache;
			if (seen != null && seen.manager() == manager) {
				return seen.voice();
			}
			Voice voice = load(manager);
			cache = new Cached(manager, voice);
			return voice;
		}
	}

	static Voice load(ResourceManager manager) {
		Optional<Resource> resource = manager.getResource(ID);
		if (resource.isEmpty()) {
			ScLog.warn("[a1016] s2: b17 is missing");
			return EMPTY;
		}
		try (Reader reader = resource.get().openAsReader()) {
			return readOrEmpty(reader);
		} catch (Exception e) {
			ScLog.warn("[a1016] s2: b17 could not be read", e);
			return EMPTY;
		}
	}

	/** Reads a texts file, else {@link #EMPTY}; what it left out or why it failed is logged neutrally (counts only). */
	public static Voice readOrEmpty(Reader reader) {
		try {
			Voice voice = read(reader);
			if (voice.skipped() > 0) {
				ScLog.warn("[a1016] s2: b17 has {} entries it could not use", voice.skipped());
			}
			return voice;
		} catch (RuntimeException e) {
			ScLog.warn("[a1016] s2: b17 could not be read", e);
			return EMPTY;
		}
	}

	/** Reads a texts file; throws only when it is not JSON at all (the caller falls back). */
	public static Voice read(Reader reader) {
		return parse(JsonParser.parseReader(reader));
	}

	/** The texts in {@code json}, leniently: whatever is not usable is left out and counted; never throws. */
	public static Voice parse(@Nullable JsonElement json) {
		if (json == null || !json.isJsonObject()) {
			return EMPTY;
		}
		JsonObject root = json.getAsJsonObject();
		int[] skipped = {0};
		Map<Character, List<Line>> pools = new HashMap<>();
		JsonObject l = object(root, "l");
		if (l != null) {
			for (Map.Entry<String, JsonElement> pool : l.entrySet()) {
				if (pool.getKey().length() != 1) {
					continue;
				}
				List<Line> lines = new ArrayList<>();
				for (JsonElement entry : entries(pool.getValue())) {
					Line line = line(entry);
					if (line == null) {
						skipped[0]++;
					} else {
						lines.add(line);
					}
				}
				if (!lines.isEmpty()) {
					pools.put(pool.getKey().charAt(0), List.copyOf(lines));
				}
			}
		}
		Map<String, List<List<String>>> six = new HashMap<>();
		JsonObject u = object(root, "u");
		if (u != null) {
			for (Map.Entry<String, JsonElement> one : u.entrySet()) {
				List<List<String>> variants = new ArrayList<>();
				for (JsonElement variant : array(one.getValue())) {
					List<String> pages = new ArrayList<>();
					for (JsonElement page : array(variant)) {
						if (page.isJsonPrimitive() && page.getAsJsonPrimitive().isString()) {
							pages.add(page.getAsString());
						}
					}
					if (pages.isEmpty()) {
						skipped[0]++;
					} else {
						variants.add(List.copyOf(pages));
					}
				}
				if (!variants.isEmpty()) {
					six.put(one.getKey().toLowerCase(Locale.ROOT), List.copyOf(variants));
				}
			}
		}
		Map<String, Map<String, List<String>>> maps = new HashMap<>();
		JsonObject map = object(root, "map");
		if (map != null) {
			for (Map.Entry<String, JsonElement> table : map.entrySet()) {
				if (!table.getValue().isJsonObject()) {
					continue;
				}
				Map<String, List<String>> words = new HashMap<>();
				for (Map.Entry<String, JsonElement> word : table.getValue().getAsJsonObject().entrySet()) {
					List<String> list = new ArrayList<>();
					for (JsonElement each : word.getValue().isJsonArray() ? array(word.getValue()) : List.of(word.getValue())) {
						if (each.isJsonPrimitive() && each.getAsJsonPrimitive().isString() && !each.getAsString().isBlank()) {
							list.add(each.getAsString());
						}
					}
					if (!list.isEmpty()) {
						words.put(word.getKey(), List.copyOf(list));
					}
				}
				maps.put(table.getKey(), Map.copyOf(words));
			}
		}
		return new Voice(Map.copyOf(pools), Map.copyOf(six), Map.copyOf(maps), skipped[0]);
	}

	private static @Nullable Line line(JsonElement entry) {
		if (!entry.isJsonObject()) {
			return null;
		}
		JsonObject object = entry.getAsJsonObject();
		JsonElement t = object.get("t");
		if (t == null || !t.isJsonPrimitive() || !t.getAsJsonPrimitive().isString() || t.getAsString().isBlank()) {
			return null;
		}
		String text = t.getAsString();
		int weight = 1;
		JsonElement w = object.get("w");
		if (w != null) {
			if (!w.isJsonPrimitive() || !w.getAsJsonPrimitive().isNumber()) {
				return null;
			}
			weight = Math.clamp(w.getAsInt(), 0, 1000);
		}
		List<String> conditions = new ArrayList<>();
		JsonElement cond = object.get("if");
		if (cond != null) {
			if (!cond.isJsonArray()) {
				return null;
			}
			for (JsonElement c : cond.getAsJsonArray()) {
				if (!c.isJsonPrimitive() || !CONDITION.matcher(c.getAsString()).matches()) {
					return null;
				}
				conditions.add(c.getAsString());
			}
		}
		Set<String> slots = slots(text);
		if (slots == null) {
			return null;
		}
		return new Line(text, weight, List.copyOf(conditions), slots, firstPersons(text) > 0);
	}

	/** The slots in a text, or null if it holds one that is not known or an unclosed brace. */
	static @Nullable Set<String> slots(String text) {
		Set<String> slots = new LinkedHashSet<>();
		Matcher m = SLOT.matcher(text);
		while (m.find()) {
			if (!SLOTS.contains(m.group(1))) {
				return null;
			}
			slots.add(m.group(1));
		}
		String rest = SLOT.matcher(text).replaceAll("");
		return rest.indexOf('{') >= 0 || rest.indexOf('}') >= 0 ? null : Set.copyOf(slots);
	}

	/** How many times a text says "i" (a word of its own). */
	static int firstPersons(String text) {
		int count = 0;
		Matcher m = WORD.matcher(text);
		while (m.find()) {
			if (m.group().equals("i")) {
				count++;
			}
		}
		return count;
	}

	private static @Nullable JsonObject object(JsonObject parent, String key) {
		JsonElement e = parent.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	private static List<JsonElement> array(@Nullable JsonElement e) {
		List<JsonElement> out = new ArrayList<>();
		if (e != null && e.isJsonArray()) {
			e.getAsJsonArray().forEach(out::add);
		}
		return out;
	}

	/** A pool's entries: an array, or an object of arrays (sub-pools) read in order. */
	private static List<JsonElement> entries(JsonElement pool) {
		if (pool.isJsonObject()) {
			List<JsonElement> out = new ArrayList<>();
			pool.getAsJsonObject().entrySet().forEach(sub -> out.addAll(array(sub.getValue())));
			return out;
		}
		return array(pool);
	}

	// --- the writer's check ---

	/**
	 * The mechanical rules of the texts file (docs/SELF_CREATED_2_R3.md, "The voice", "The texts file"), each broken one a
	 * problem in neutral words (a pool, an index, a count; never a text). Empty when the file passes: it parses, every
	 * pool holds its minimum (the day books' kinds and the places' names too), every text is lowercase with no {@code ?},
	 * {@code !}, forbidden word or character outside plain ASCII, says "i" at most once, uses only known slots and
	 * conditions and fits a page with its slots filled long; no wrong line is a death; the six have three variants of 2
	 * to 4 pages, each page within 13 lines of 19.
	 */
	public static List<String> validate(@Nullable JsonElement json) {
		List<String> problems = new ArrayList<>();
		if (json == null || !json.isJsonObject()) {
			problems.add("not an object");
			return problems;
		}
		JsonObject root = json.getAsJsonObject();
		JsonObject l = object(root, "l");
		if (l == null) {
			problems.add("no l");
			return problems;
		}
		Map<DayKind, Integer> dayKinds = new EnumMap<>(DayKind.class);
		for (Map.Entry<Character, Integer> min : MINIMUMS.entrySet()) {
			String key = String.valueOf(min.getKey());
			JsonElement pool = l.get(key);
			int usable = 0;
			int index = 0;
			for (JsonElement entry : pool == null ? List.<JsonElement>of() : entries(pool)) {
				String at = "l." + key + "[" + index++ + "]";
				Line line = line(entry);
				if (line == null) {
					problems.add(at + ": malformed, an unknown slot or condition");
					continue;
				}
				usable++;
				text(problems, at, line.text());
				if (!fitsFilled(line.text())) {
					problems.add(at + ": longer than a page with its slots filled");
				}
				if (min.getKey() == 'p' && (line.slots().contains("cause") || line.conditions().stream().anyMatch(c -> c.startsWith("cause:")))) {
					problems.add(at + ": a wrong line about a death");
				}
				if (min.getKey() == 's') {
					DayKind kind = line.dayKind();
					if (kind == null) {
						problems.add(at + ": no day book kind");
					} else {
						dayKinds.merge(kind, 1, Integer::sum);
					}
				}
			}
			if (usable < min.getValue()) {
				problems.add("l." + key + ": " + usable + " lines, at least " + min.getValue());
			}
		}
		for (DayKind kind : DayKind.values()) {
			int count = dayKinds.getOrDefault(kind, 0);
			if (count < kind.minimum) {
				problems.add("l.s " + kind.name().toLowerCase(Locale.ROOT) + ": " + count + " lines, at least " + kind.minimum);
			}
		}
		JsonObject map = object(root, "map");
		if (map == null) {
			problems.add("no map");
		} else {
			Voice voice = parse(root);
			for (String kind : List.of("16", "17", "18", "19", "20")) {
				int names = voice.words("place", kind).size();
				if (names < 4) {
					problems.add("map.place." + kind + ": " + names + " names, at least 4");
				}
			}
			for (String weather : List.of("clear", "rain", "thunder", "snow")) {
				if (voice.words("weather", weather).isEmpty()) {
					problems.add("map.weather." + weather + ": missing");
				}
			}
			for (int phase = 0; phase < 8; phase++) {
				if (voice.words("moon", String.valueOf(phase)).isEmpty()) {
					problems.add("map.moon." + phase + ": missing");
				}
			}
			for (String table : List.of("cause", "place", "weather", "moon")) {
				if (object(map, table) == null) {
					problems.add("map." + table + ": missing");
					continue;
				}
				for (Map.Entry<String, JsonElement> word : object(map, table).entrySet()) {
					for (JsonElement each : word.getValue().isJsonArray() ? array(word.getValue()) : List.of(word.getValue())) {
						if (!each.isJsonPrimitive()) {
							problems.add("map." + table + "." + word.getKey() + ": not a word");
						} else {
							text(problems, "map." + table + "." + word.getKey(), each.getAsString());
						}
					}
				}
			}
		}
		JsonObject u = object(root, "u");
		for (String name : SIX) {
			JsonElement one = u == null ? null : u.get(name);
			List<JsonElement> variants = array(one);
			if (variants.size() != 3) {
				problems.add("u." + name + ": " + variants.size() + " variants, 3");
			}
			for (int v = 0; v < variants.size(); v++) {
				List<JsonElement> pages = array(variants.get(v));
				if (pages.size() < 2 || pages.size() > 4) {
					problems.add("u." + name + "[" + v + "]: " + pages.size() + " pages, 2 to 4");
				}
				for (int p = 0; p < pages.size(); p++) {
					String at = "u." + name + "[" + v + "][" + p + "]";
					if (!pages.get(p).isJsonPrimitive()) {
						problems.add(at + ": not a page");
						continue;
					}
					String page = pages.get(p).getAsString();
					text(problems, at, page);
					String[] lines = page.split("\n", -1);
					if (lines.length > Pages.LINES) {
						problems.add(at + ": " + lines.length + " lines, at most " + Pages.LINES);
					}
					for (String line : lines) {
						if (line.length() > Pages.WIDTH) {
							problems.add(at + ": a line over " + Pages.WIDTH);
							break;
						}
					}
					if (slots(page) == null || !slots(page).isEmpty()) {
						problems.add(at + ": a slot in a fixed page");
					}
				}
			}
		}
		return problems;
	}

	private static void text(List<String> problems, String at, String text) {
		String plain = SLOT.matcher(text).replaceAll("");
		if (!plain.equals(plain.toLowerCase(Locale.ROOT))) {
			problems.add(at + ": capitals");
		}
		if (plain.indexOf('?') >= 0 || plain.indexOf('!') >= 0) {
			problems.add(at + ": a question or exclamation mark");
		}
		if (!plain.chars().allMatch(c -> c == '\n' || (c >= 0x20 && c < 0x7F))) {
			problems.add(at + ": a character outside plain text");
		}
		Matcher m = WORD.matcher(plain);
		while (m.find()) {
			if (FORBIDDEN.contains(m.group())) {
				problems.add(at + ": a forbidden word");
				break;
			}
		}
		if (firstPersons(plain) > 1) {
			problems.add(at + ": i twice");
		}
	}

	/** True if the text, every slot filled with a 16-character word, fits one page. */
	static boolean fitsFilled(String text) {
		return Pages.lines(SLOT.matcher(text).replaceAll("abcdefghijklmnop")).size() <= Pages.LINES;
	}

	/** Reads and checks a texts file's JSON in one go (any failure to parse is one problem). */
	public static List<String> validate(Reader reader) {
		try {
			JsonElement json = JsonParser.parseReader(reader);
			return validate(json);
		} catch (RuntimeException e) {
			return List.of("does not parse");
		}
	}
}
