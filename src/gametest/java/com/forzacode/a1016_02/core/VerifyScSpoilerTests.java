package com.forzacode.a1016_02.core;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.forzacode.a1016_02.accident.sc.Sc15Config;
import com.forzacode.a1016_02.atmosphere.sc.Sc04Config;
import com.forzacode.a1016_02.atmosphere.sc.Sc05Config;
import com.forzacode.a1016_02.atmosphere.sc.Sc06Config;
import com.forzacode.a1016_02.atmosphere.sc.Sc07Config;
import com.forzacode.a1016_02.dig.sc.Sc12Config;
import com.forzacode.a1016_02.dig.sc.Sc13Config;
import com.forzacode.a1016_02.director.sc.Sc01Config;
import com.forzacode.a1016_02.entity.sc.HisViewConfig;
import com.forzacode.a1016_02.entity.sc.NamelessConfig;
import com.forzacode.a1016_02.lore.sc.RecordConfig;
import com.forzacode.a1016_02.world.sc.TorchTrail;
import com.forzacode.a1016_02.world.sc.Tracks;
import com.forzacode.a1016_02.world.sc.Turned;
import com.forzacode.a1016_02.world.sc.WhereHeStood;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/**
 * SC-7, the verifier, and FIX-SC-FINAL: the spoiler rule D-080 for what Mariano reads, "the {@code /a1016} output, the
 * config and the log". (1) The config: every section of the layer is written to {@code config/a1016_02.json} with its
 * defaults the first time it is read, and every key written is opaque (a letter, never the thing; the switch's
 * {@code enabled} and each {@code version} aside; docs/SELF_CREATED.md maps them), in the class's defaults and in the
 * file this server wrote. (2) A dev config from before keeps its hand edits: each section's upgrade step carries a value
 * under an old key to its new one and writes only the new key. (3) The commands: every literal and argument under
 * {@code /a1016 sc} and its usage text (what tab completion and {@code /help a1016} show). (4) The log: every INFO, WARN
 * and ERROR line of the layer (its {@code sc} packages, its files outside them, and any line anywhere that names an
 * {@code sc} id), read from the sources: the format and every string in the call. All held to the layer's own list of
 * words that name a feature ({@code SelfCreatedDebugTests.NAMES}, which the index and the overlay already pass) and a
 * few more ({@link #MORE}). SC2-0a (D-103) adds the second wave's words ({@link #SC2}, {@link #SC2_GENERIC}), its
 * {@code sc2} packages to the log check, and its sections, found by themselves ({@link #secondWave}). R3-S (D-103
 * extended; docs/SELF_CREATED_2_R3.md, "Spoilers for round 3") adds round 3's words ({@link #SC3}, {@link #SC3_GENERIC}).
 */
public class VerifyScSpoilerTests {
	/** {@code SelfCreatedDebugTests.NAMES} (D-080), word for word. */
	static final List<String> NAMES = List.of("moon", "night", "name", "bed", "sleep", "rest", "torch", "step", "foot", "print", "track", "fog", "icon",
			"picture", "view", "seed", "version", "house", "home", "door", "chest", "pet", "animal", "friend", "cave", "lava", "shaft", "stat", "mined",
			"dropped", "turn", "trail", "brb", "record", "hole", "block", "wall", "way back", "stood", "stand");
	/** More words that would name a part (FIX-SC-FINAL: the old literals and the old log lines). */
	static final List<String> MORE = List.of("sky", "trust", "circle", "loop", "phantom", "sign", "spawn", "mob", "gap", "behind", "hollow", "list",
			"water", "landing");
	/**
	 * The second wave's words (D-103, SC2-0a; docs/SELF_CREATED_2.md, "Spoilers for this wave"): its places, its mobs, its
	 * hunts and its friend; {@code record}, {@code hollow}, {@code circle} and {@code friend} are in the lists above.
	 */
	public static final List<String> SC2 = List.of("pyramid", "brick", "archive", "library", "bookshelf", "glowstone", "mountain", "obsidian", "ring", "snake",
			"procession", "breath", "follower", "wolf", "wolves", "pack", "howl", "hunt", "horde", "elsewhere", "ghost", "borrow", "apex", "aisle", "trunk",
			"pit", "marker");
	/**
	 * The second wave's generic words, kept because no line of the layer trips them ({@code wall}, {@code house},
	 * {@code chest}, {@code door} and {@code step} are in {@link #NAMES}). Left out, as an existing line of the layer
	 * uses them and R13 watches them by hand: see {@link #SC2_DROPPED}.
	 */
	public static final List<String> SC2_GENERIC = List.of("line", "floor", "call", "book", "map");
	/**
	 * The generic words of the second wave that a line of the first already uses, so they are not held here (R13 watches
	 * them by hand): {@code file} (03's "could not read the file from before").
	 */
	static final List<String> SC2_DROPPED = List.of("file");
	/**
	 * Round 3's words (D-103 extended, R3-S; docs/SELF_CREATED_2_R3.md, "Spoilers for round 3"): the crumbs and their
	 * error signs, the archive's books and stacks, the hollow's cut, and the places' sleep; {@code bookshelf},
	 * {@code glowstone}, {@code brick} and {@code pyramid} are in {@link #SC2}.
	 */
	public static final List<String> SC3 = List.of("crumb", "breadcrumb", "fault", "glitch", "edition", "tattered", "summit", "tabletop", "hanging",
			"stacks", "dormant", "asleep", "awake", "depths", "warren", "barricade");
	/** Round 3's generic words, kept because no line of the layer trips them. Left out, watched by hand: {@link #SC3_WATCHED}. */
	public static final List<String> SC3_GENERIC = List.of("well", "day", "light", "lit", "rooms", "iron", "camp");
	/**
	 * The generic words of round 3 that an existing line of the layer already uses, so they are not held here (R3-A and
	 * R3-V watch them by hand): {@code piece} (the kit's "a {} piece failed in chunk {}", {@code Sc2Piece}).
	 */
	static final List<String> SC3_WATCHED = List.of("piece");
	/** Phrases that name no feature, taken out of a text before it is held to the lists: vanilla's and Iris's own words. */
	static final List<String> PLAIN_PHRASES = List.of("shader pack");
	/** Every list a word of the layer is held to. */
	static final List<List<String>> LISTS = List.of(NAMES, MORE, SC2, SC2_GENERIC, SC3, SC3_GENERIC);
	/** The keys every section may keep in plain words: its schema version, and the layer's switch. */
	private static final Set<String> PLAIN_KEYS = Set.of("version", "enabled");
	/** An opaque key: a letter, at most one more letter or digit. */
	private static final Pattern OPAQUE = Pattern.compile("[a-z][a-z0-9]?");

	/**
	 * A section: its class, a fresh one, the version its keys were made opaque at (a file below it has the old keys), the
	 * version a file is read up to now, and the fields added after the keys were made opaque (opaque from the start, so
	 * they have no key from before: an old file reads them at their defaults).
	 */
	record Section(Class<?> type, Supplier<?> fresh, int opaqueFrom, int current, Set<String> newer) {
		Section(Class<?> type, Supplier<?> fresh, int opaqueFrom) {
			this(type, fresh, opaqueFrom, opaqueFrom, Set.of());
		}
	}

	/** Every section of the layer, by its name under {@code sections}. */
	static Map<String, Section> sections() {
		Map<String, Section> sections = new LinkedHashMap<>();
		sections.put("sc", new Section(SelfCreated.Config.class, SelfCreated.Config::new, 0));
		// SC2-9, 01+ (docs/SELF_CREATED_2.md): version 4 adds o and p, which never had a key in words; FIX-SC2-PACE's version 5
		// adds q to z (the second wave's pacing), the same.
		sections.put("sc01", new Section(Sc01Config.class, Sc01Config::new, 3, Sc01Config.CURRENT, Set.of("remainsFactor", "simRemainsShare", "weights",
				"tracesShares", "laterShares", "gapMinutes", "repeatHours", "beatMinutes", "beatDarkMinutes", "huntFromHours", "huntGapHours",
				"huntPullMinutes")));
		sections.put("sc02", new Section(NamelessConfig.class, NamelessConfig::new, 2));
		sections.put("sc03", new Section(HisViewConfig.class, HisViewConfig::new, 2));
		sections.put("sc04", new Section(Sc04Config.class, Sc04Config::new, 2));
		sections.put("sc05", new Section(Sc05Config.class, Sc05Config::new, 2));
		sections.put("sc06", new Section(Sc06Config.class, Sc06Config::new, 2));
		sections.put("sc07", new Section(Sc07Config.class, Sc07Config::new, 2));
		sections.put("sc08", new Section(Turned.Config.class, Turned.Config::new, 2));
		sections.put("sc09", new Section(Tracks.Config.class, Tracks.Config::new, 2));
		sections.put("sc10", new Section(WhereHeStood.Config.class, WhereHeStood.Config::new, 2));
		sections.put("sc11", new Section(TorchTrail.Config.class, TorchTrail.Config::new, 2));
		sections.put("sc12", new Section(Sc12Config.class, Sc12Config::new, 2));
		sections.put("sc13", new Section(Sc13Config.class, Sc13Config::new, 2));
		sections.put("sc14", new Section(RecordConfig.class, RecordConfig::new, 2));
		sections.put("sc15", new Section(Sc15Config.class, Sc15Config::new, 3));
		sections.putAll(secondWave());
		return sections;
	}

	/** An sc2 section's class: {@code ScNNConfig}, in its feature's {@code sc2} package (docs/SELF_CREATED_2.md, "Config sections"). */
	private static final Pattern SC2_CONFIG = Pattern.compile("Sc(\\d\\d)Config\\.java");

	/**
	 * The second wave's sections, found by themselves so no later task edits this file (SC2-0a): every class
	 * {@code ScNNConfig} in an {@code sc2} package of the mod's sources, under its {@code SECTION} (else {@code scNN}),
	 * opaque from its first version (it has no keys from before).
	 */
	static Map<String, Section> secondWave() {
		Map<String, Section> found = new LinkedHashMap<>();
		Path sources = sourceRoot();
		if (sources == null) {
			return found;
		}
		try (Stream<Path> files = Files.walk(sources)) {
			for (Path file : files.filter(p -> p.toString().replace('\\', '/').contains("/sc2/")).sorted().toList()) {
				Matcher name = SC2_CONFIG.matcher(file.getFileName().toString());
				if (!name.matches()) {
					continue;
				}
				String rel = sources.relativize(file).toString().replace('\\', '/');
				Class<?> type = Class.forName(rel.substring(0, rel.length() - ".java".length()).replace('/', '.'));
				String section = "sc" + name.group(1);
				try {
					Field declared = type.getField("SECTION");
					if (Modifier.isStatic(declared.getModifiers()) && declared.get(null) instanceof String value) {
						section = value;
					}
				} catch (NoSuchFieldException none) {
					// scNN from the class name
				}
				found.put(section, new Section(type, () -> {
					try {
						return type.getDeclaredConstructor().newInstance();
					} catch (ReflectiveOperationException e) {
						throw new IllegalStateException("cannot make a fresh " + type.getName(), e);
					}
				}, 0));
			}
		} catch (IOException | ReflectiveOperationException e) {
			throw new IllegalStateException("the second wave's config sections could not be read from " + sources, e);
		}
		return found;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object read(JsonObject sections, String name, Section section) {
		return ModConfig.readSection(sections, name, (Class) section.type(), (Supplier) section.fresh());
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object section(String name, Section section) {
		return ModConfig.section(name, (Class) section.type(), (Supplier) section.fresh());
	}

	/** Why this key may not be written, or null. */
	private static String keyRefusal(String key) {
		if (PLAIN_KEYS.contains(key)) {
			return null;
		}
		String word = namedWord(key);
		if (word != null) {
			return "'" + word + "'";
		}
		return OPAQUE.matcher(key).matches() ? null : "not opaque";
	}

	@GameTest
	public void noConfigFieldNamesTheThing(GameTestHelper helper) throws IOException {
		Gson gson = new Gson();
		List<String> named = new ArrayList<>();
		for (Map.Entry<String, Section> entry : sections().entrySet()) {
			JsonObject written = gson.toJsonTree(read(new JsonObject(), entry.getKey(), entry.getValue())).getAsJsonObject();
			for (String key : written.keySet()) {
				String why = keyRefusal(key);
				if (why != null) {
					named.add(entry.getKey() + "." + key + " (" + why + ")");
				}
			}
		}
		// The file this server wrote, once every section of the layer was read (as the game reads it: each is written back
		// then, so a file left by an older build holds the new keys too).
		for (Map.Entry<String, Section> entry : sections().entrySet()) {
			section(entry.getKey(), entry.getValue());
		}
		Path file = FabricLoader.getInstance().getConfigDir().resolve("a1016_02.json");
		if (Files.isRegularFile(file)) {
			JsonElement root = JsonParser.parseString(Files.readString(file));
			JsonObject sections = root.isJsonObject() && root.getAsJsonObject().get("sections") instanceof JsonObject s ? s : new JsonObject();
			for (String name : sections.keySet()) {
				if (!name.equals("sc") && !name.matches("sc\\d\\d")) {
					continue;
				}
				helper.assertTrue(sections().containsKey(name), "the section " + name + " is not held to D-080 here (a second-wave section's class is ScNNConfig"
						+ " in its sc2 package; a first-wave one is listed in sections())");
				if (sections.get(name) instanceof JsonObject section) {
					for (String key : section.keySet()) {
						String why = keyRefusal(key);
						if (why != null) {
							named.add(file.getFileName() + ": " + name + "." + key + " (" + why + ")");
						}
					}
				}
			}
		}
		helper.assertTrue(named.isEmpty(), named.size() + " config keys name the thing (D-080): " + named);
		helper.succeed();
	}

	/**
	 * A dev config from before the keys were opaque keeps every hand edit: each old key's value (changed from its default)
	 * is read into its field, the section is at its version, and only the new keys are written back (none of the old).
	 */
	@GameTest
	public void everyOldKeyIsCarriedToItsNewKey(GameTestHelper helper) throws ReflectiveOperationException {
		Gson gson = new Gson();
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, Section> entry : sections().entrySet()) {
			Section s = entry.getValue();
			if (s.opaqueFrom() == 0) {
				continue;
			}
			Object defaults = s.fresh().get();
			JsonObject old = new JsonObject();
			old.addProperty("version", s.opaqueFrom() - 1);
			Map<Field, Object> edited = new LinkedHashMap<>();
			List<Field> newer = new ArrayList<>();
			for (Field field : s.type().getFields()) {
				SerializedName named = field.getAnnotation(SerializedName.class);
				if (Modifier.isStatic(field.getModifiers()) || field.getName().equals("version")) {
					continue;
				}
				if (s.newer().contains(field.getName())) {
					// Added after the keys were made opaque: no key from before, and an old file reads it at its default.
					if (named == null || named.alternate().length != 0) {
						wrong.add(entry.getKey() + "." + field.getName() + " is newer than its opaque step but has " + (named == null ? "no letter" : "an old key"));
					} else {
						newer.add(field);
					}
					continue;
				}
				if (named == null || named.alternate().length == 0) {
					wrong.add(entry.getKey() + "." + field.getName() + " has no key from before");
					continue;
				}
				Object value = edit(field.get(defaults));
				edited.put(field, value);
				old.add(named.alternate()[0], gson.toJsonTree(value));
			}
			if (newer.size() != s.newer().size()) {
				wrong.add(entry.getKey() + ": newer fields " + s.newer() + ", found " + newer.stream().map(Field::getName).toList());
			}
			JsonObject file = new JsonObject();
			file.add(entry.getKey(), old);
			Object read = read(file, entry.getKey(), s);
			if (s.type().getField("version").getInt(read) != s.current()) {
				wrong.add(entry.getKey() + " at version " + s.type().getField("version").getInt(read) + ", not " + s.current());
			}
			JsonObject writtenNewer = gson.toJsonTree(read).getAsJsonObject();
			for (Field field : newer) {
				if (!same(field.get(read), field.get(defaults))) {
					wrong.add(entry.getKey() + "." + field.getName() + " " + gson.toJson(field.get(read)) + " from a file without it, not its default");
				}
				if (!writtenNewer.has(field.getAnnotation(SerializedName.class).value())) {
					wrong.add(entry.getKey() + " wrote " + writtenNewer.keySet() + " without " + field.getName());
				}
			}
			for (Map.Entry<Field, Object> e : edited.entrySet()) {
				Object now = e.getKey().get(read);
				if (!same(now, e.getValue())) {
					wrong.add(entry.getKey() + "." + e.getKey().getName() + " " + gson.toJson(now) + ", the hand edit was " + gson.toJson(e.getValue()));
				}
			}
			JsonObject written = gson.toJsonTree(read).getAsJsonObject();
			for (Field field : edited.keySet()) {
				SerializedName named = field.getAnnotation(SerializedName.class);
				if (written.has(named.alternate()[0]) || !written.has(named.value())) {
					wrong.add(entry.getKey() + " wrote " + written.keySet() + " for " + field.getName());
				}
			}
			// Read again (a file already upgraded): the same values.
			JsonObject again = new JsonObject();
			again.add(entry.getKey(), written);
			Object twice = read(again, entry.getKey(), s);
			for (Map.Entry<Field, Object> e : edited.entrySet()) {
				if (!same(e.getKey().get(twice), e.getValue())) {
					wrong.add(entry.getKey() + "." + e.getKey().getName() + " lost on the second read");
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong);
		helper.succeed();
	}

	/**
	 * SC2-0a: the second wave's words are held in every form the checks read (a log line's words, a literal's letters, a
	 * key's camelCase parts, plurals and -ed), Iris's "shader pack" is not a word of the layer, and a dropped generic
	 * word is not held; an sc2 section is found by itself (none exists before the wave's features).
	 */
	@GameTest
	public void theSecondWavesWordsAreHeld(GameTestHelper helper) {
		helper.assertTrue("pyramid".equals(namedIn("[a1016] {}: the pyramid at {}", false)), "a log line's word");
		helper.assertTrue("wolves".equals(namedIn("[a1016] calmed 3 wolves", false)), "a plural listed as itself");
		helper.assertTrue("hunt".equals(namedIn("[a1016] hunted for 40 s", false)), "a word with -ed");
		helper.assertTrue("ring".equals(namedIn("noring", true)) && "apex".equals(namedIn("toapex", true)), "a word inside a literal");
		helper.assertTrue("hunt".equals(namedWord("huntSecondsMax")) && "follower".equals(namedWord("followerDays")), "a key's part");
		helper.assertTrue("map".equals(namedIn("[a1016] the map changed", false)) && "book".equals(namedIn("[a1016] 2 books moved", false)), "a generic word");
		helper.assertTrue(namedIn("[a1016] Iris is loaded; a shader pack counts as in use", false) == null, "Iris's shader pack counted as a word of the layer");
		helper.assertTrue(namedIn("[a1016] could not read the file", false) == null && SC2_DROPPED.contains("file"), "a dropped word is held");
		for (List<String> list : List.of(SC2, SC2_GENERIC)) {
			for (String word : list) {
				helper.assertTrue(word.equals(namedIn("[a1016] " + word, false)), "'" + word + "' is not held");
			}
		}
		for (Map.Entry<String, Section> entry : secondWave().entrySet()) {
			helper.assertTrue(entry.getKey().matches("sc\\d\\d") && entry.getValue().opaqueFrom() == 0, "an sc2 section: " + entry.getKey());
		}
		helper.succeed();
	}

	/**
	 * R3-S: round 3's words are held in every form the checks read, as the second wave's are; a word watched by hand is
	 * not held. The config, commands and log checks above read them through {@link #LISTS}.
	 */
	@GameTest
	public void theThirdRoundsWordsAreHeld(GameTestHelper helper) {
		helper.assertTrue("crumb".equals(namedIn("[a1016] {}: 3 crumbs at {}", false)), "a plural");
		helper.assertTrue("glitch".equals(namedIn("[a1016] glitched", false)) && "awake".equals(namedIn("[a1016] awaked", false)), "a word with -ed");
		helper.assertTrue(namedIn("lostbreadcrumb", true) != null && "summit".equals(namedIn("nosummit", true)), "a word inside a literal");
		helper.assertTrue("fault".equals(namedWord("faultEdge")) && "edition".equals(namedWord("editionsMax")), "a key's part");
		for (List<String> list : List.of(SC3, SC3_GENERIC)) {
			for (String word : list) {
				helper.assertTrue(word.equals(namedIn("[a1016] " + word, false)), "'" + word + "' is not held");
			}
		}
		for (String word : SC3_WATCHED) {
			helper.assertTrue(namedIn("[a1016] " + word, false) == null, "a watched word is held: " + word);
		}
		helper.succeed();
	}

	/** A hand edit of a default: a number one more (half more for a fraction), a flag flipped, a time an hour on, each stage's chance up. */
	private static Object edit(Object value) {
		return switch (value) {
			case Integer i -> i + 1;
			case Long l -> l + 1;
			case Double d -> d + 0.5;
			case Float f -> f + 0.5F;
			case Boolean b -> !b;
			case String text when text.matches("\\d\\d:\\d\\d") -> String.format(Locale.ROOT, "%02d%s", (Integer.parseInt(text.substring(0, 2)) + 1) % 24,
					text.substring(2));
			case String text -> text + "x";
			case double[] a -> Arrays.stream(a).map(x -> x + 0.01).toArray();
			default -> throw new IllegalArgumentException("no edit for " + value);
		};
	}

	private static boolean same(Object a, Object b) {
		if (a instanceof double[] x && b instanceof double[] y) {
			return Arrays.equals(x, y);
		}
		return a != null && a.equals(b);
	}

	/**
	 * The SC-7 row's spoiler grep on the commands: every literal and argument under {@code /a1016 sc}, and every line of
	 * its usage (what tab completion and {@code /help a1016} show), held to the list.
	 */
	@GameTest
	public void noCommandLiteralUnderScNamesAFeature(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommandNode<CommandSourceStack> root = server.getCommands().getDispatcher().getRoot().getChild("a1016");
		helper.assertTrue(root != null && root.getChild("sc") != null, "no /a1016 sc");
		List<String> named = new ArrayList<>();
		walk(root.getChild("sc"), "sc", named);
		String[] usage = server.getCommands().getDispatcher().getAllUsage(root.getChild("sc"), server.createCommandSourceStack(), false);
		helper.assertTrue(usage.length > 15, "no usage under /a1016 sc: " + Arrays.toString(usage));
		for (String line : usage) {
			String word = namedIn(line, true);
			if (word != null) {
				named.add("usage '/a1016 sc " + line + "' ('" + word + "')");
			}
		}
		helper.assertTrue(named.isEmpty(), named.size() + " literals, arguments or usage lines under /a1016 sc name a feature (D-080): " + named);
		helper.succeed();
	}

	/**
	 * The SC-7 row's spoiler grep on the log: no INFO, WARN or ERROR line of the layer names a feature. The layer's lines
	 * are those in its {@code sc} packages and in its files outside them ({@link #LAYER_FILES}), and any line anywhere whose
	 * strings name an {@code sc} id; each call's format and every string in it are held to the list, word by word.
	 */
	@GameTest
	public void noLogLineOfTheLayerNamesAFeature(GameTestHelper helper) throws IOException {
		Path sources = sourceRoot();
		helper.assertTrue(sources != null, "the mod's sources (src/main/java) were not found from " + Paths.get("").toAbsolutePath());
		List<String> named = new ArrayList<>();
		int lines = 0;
		try (Stream<Path> files = Files.walk(sources)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
				String rel = sources.relativize(file).toString().replace('\\', '/');
				boolean layer = rel.contains("/sc/") || rel.contains("/sc2/") || LAYER_FILES.contains(rel);
				String text = Files.readString(file);
				Matcher call = LOG_CALL.matcher(text);
				while (call.find()) {
					List<String> strings = strings(text, call.end());
					if (!layer && strings.stream().noneMatch(s -> SC_ID.matcher(s).find())) {
						continue;
					}
					lines++;
					for (String s : strings) {
						String word = namedInLog(s);
						if (word != null) {
							int line = (int) text.substring(0, call.start()).chars().filter(c -> c == '\n').count() + 1;
							named.add(rel + ":" + line + " \"" + s + "\" ('" + word + "')");
						}
					}
				}
			}
		}
		helper.assertTrue(lines >= 30, "only " + lines + " log lines of the layer were found in " + sources);
		helper.assertTrue(named.isEmpty(), named.size() + " log lines of the layer name a feature (D-080): " + named);
		helper.succeed();
	}

	/**
	 * R13-6: no INFO, WARN or ERROR line of the second wave gives a position (a stack moved between two bases, a spot in
	 * the rock: the evidence that it was the mod); where is at DEBUG. The dev-only galleries aside.
	 */
	@GameTest
	public void noLogLineOfTheSecondWaveGivesAPosition(GameTestHelper helper) throws IOException {
		Path sources = sourceRoot();
		helper.assertTrue(sources != null, "the mod's sources (src/main/java) were not found from " + Paths.get("").toAbsolutePath());
		List<String> placed = new ArrayList<>();
		int lines = 0;
		try (Stream<Path> files = Files.walk(sources)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
				String rel = sources.relativize(file).toString().replace('\\', '/');
				if (!rel.contains("/sc2/") || rel.contains("Gallery")) {
					continue;
				}
				String text = Files.readString(file);
				Matcher call = LOG_CALL.matcher(text);
				while (call.find()) {
					lines++;
					String args = arguments(text, call.end());
					if (args.contains("toShortString") || args.contains("getX()") || args.contains("blockPosition()")) {
						int line = (int) text.substring(0, call.start()).chars().filter(c -> c == '\n').count() + 1;
						placed.add(rel + ":" + line);
					}
				}
			}
		}
		helper.assertTrue(lines >= 10, "only " + lines + " log lines of the second wave were found in " + sources);
		helper.assertTrue(placed.isEmpty(), placed.size() + " log lines of the second wave give a position at INFO or above: " + placed);
		helper.succeed();
	}

	/** The text of the call's arguments whose list starts at {@code from} (just past its open parenthesis). */
	static String arguments(String text, int from) {
		int depth = 1;
		boolean quoted = false;
		for (int i = from; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quoted) {
				if (c == '\\') {
					i++;
				} else if (c == '"') {
					quoted = false;
				}
			} else if (c == '"') {
				quoted = true;
			} else if (c == '(') {
				depth++;
			} else if (c == ')' && --depth == 0) {
				return text.substring(from, i);
			}
		}
		return text.substring(from);
	}

	/**
	 * R13-5: no WARN or ERROR line of the layer hands its throwable to the logger, whose frames would print the layer's class
	 * names into latest.log: the layer's go through {@code ScLog} (its line, the exception's neutral name; the trace at
	 * DEBUG). The layer's lines as {@link #noLogLineOfTheLayerNamesAFeature} finds them; {@link #THROWN_PENDING} aside.
	 */
	@GameTest
	public void noWarnOrErrorOfTheLayerHandsItsThrowableToTheLogger(GameTestHelper helper) throws IOException {
		helper.assertTrue(thrownLast("A1016_02.LOGGER.error(\"[a1016] s2: {} failed\", kind, e);", 0) && !thrownLast("LOGGER.warn(\"[a1016] x {}\", e.toString());", 0)
				&& thrownLast("LOGGER.error(\"[a1016] x\", new IllegalStateException(\"y\"));", 0), "the rule's own check");
		Path sources = sourceRoot();
		helper.assertTrue(sources != null, "the mod's sources (src/main/java) were not found");
		List<String> found = new ArrayList<>();
		int calls = 0;
		try (Stream<Path> files = Files.walk(sources)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
				String rel = sources.relativize(file).toString().replace('\\', '/');
				boolean layer = rel.contains("/sc/") || rel.contains("/sc2/") || LAYER_FILES.contains(rel);
				String text = Files.readString(file);
				Matcher call = LOGGER_WARN.matcher(text);
				while (call.find()) {
					List<String> strings = strings(text, call.end());
					if (!layer && strings.stream().noneMatch(s -> SC_ID.matcher(s).find())) {
						continue;
					}
					calls++;
					if (thrownLast(text, call.start()) && !THROWN_PENDING.contains(rel)) {
						int line = (int) text.substring(0, call.start()).chars().filter(c -> c == '\n').count() + 1;
						found.add(rel + ":" + line);
					}
				}
			}
		}
		helper.assertTrue(calls >= 5, "only " + calls + " WARN and ERROR lines of the layer were found");
		helper.assertTrue(found.isEmpty(), found.size() + " WARN or ERROR lines of the layer hand their throwable to the logger (use ScLog): " + found);
		helper.succeed();
	}

	/**
	 * R13-16: what tab completion offers outside {@code /a1016 sc} names no feature of the layer either: every layer id it
	 * offers is opaque ({@code sc25}, {@code sc_21}, {@code accident_sc25}), the debug mark offers none of the layer's
	 * words for the list, and the figure's spawn offers none of the layer's variants.
	 */
	@GameTest
	public void noSuggestionOutsideScNamesAFeature(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		CommandSourceStack source = server.createCommandSourceStack();
		List<String> wrong = new ArrayList<>();
		int offered = 0;
		for (String input : List.of("a1016 accident arm ", "a1016 accident candidates ", "a1016 accident mark ", "a1016 fire ", "a1016 entity spawn ")) {
			List<String> texts = dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).join().getList().stream()
					.map(com.mojang.brigadier.suggestion.Suggestion::getText).toList();
			helper.assertTrue(!texts.isEmpty(), "nothing offered for '/" + input + "'");
			offered += texts.size();
			for (String text : texts) {
				if (LAYER_ID.matcher(text).find() && !OPAQUE_ID.matcher(text).matches()) {
					wrong.add("/" + input + text + " (not opaque)");
				}
				if (com.forzacode.a1016_02.accident.DeathCauses.UNLISTED.contains(text)) {
					wrong.add("/" + input + text + " (a word of the list before its death)");
				}
				if (input.contains("spawn") && (text.equals(com.forzacode.a1016_02.entity.Variant.APEX.shortName())
						|| text.equals(com.forzacode.a1016_02.entity.Variant.INSIDE_TRUNKS.shortName()))) {
					wrong.add("/" + input + text + " (a variant of the layer)");
				}
			}
		}
		helper.assertTrue(offered > 20, "only " + offered + " suggestions");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " suggestions name a feature: " + wrong);
		helper.succeed();
	}

	/** An id of the layer in a suggestion: {@code sc} and two digits anywhere in it. */
	private static final Pattern LAYER_ID = Pattern.compile("sc_?\\d\\d");
	/** An opaque id of the layer: a card ({@code sc_21}, {@code sc_31c}), a trap kind ({@code sc25}) or its card ({@code accident_sc25}). */
	private static final Pattern OPAQUE_ID = Pattern.compile("(accident_)?sc_?\\d\\d[a-z]?");

	/** True if the call starting at {@code from} (its name) passes, as its last argument, the caught exception. */
	static boolean thrownLast(String text, int from) {
		int open = text.indexOf('(', from);
		if (open < 0) {
			return false;
		}
		int depth = 0;
		int lastComma = -1;
		for (int i = open; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '"') {
				for (i++; i < text.length() && text.charAt(i) != '"'; i++) {
					if (text.charAt(i) == '\\') {
						i++;
					}
				}
			} else if (c == '\'') {
				i += text.charAt(i + 1) == '\\' ? 3 : 2;
			} else if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
				if (depth == 0) {
					String last = text.substring(lastComma < 0 ? open + 1 : lastComma + 1, i).trim();
					return lastComma >= 0 && THROWN_ARG.matcher(last).matches();
				}
			} else if (c == ',' && depth == 1) {
				lastComma = i;
			}
		}
		return false;
	}

	/** {@link #namedIn} for a log line, and the nouns that describe ({@link #LOG_NOUNS}, R13-6). */
	static String namedInLog(String text) {
		String word = namedIn(text, false);
		if (word != null) {
			return word;
		}
		String lower = text.toLowerCase(Locale.ROOT);
		for (String noun : LOG_NOUNS) {
			if (noun.contains(" ") ? lower.contains(noun) : Arrays.stream(lower.split("[^a-z]+")).anyMatch(t -> t.equals(noun) || t.equals(noun + "s"))) {
				return noun;
			}
		}
		return null;
	}

	/** The layer's own files outside its {@code sc} packages (from {@code main/java/com/forzacode/a1016_02}). */
	static final Set<String> LAYER_FILES = Set.of("com/forzacode/a1016_02/atmosphere/card/ComingHome.java",
			"com/forzacode/a1016_02/atmosphere/card/InStepCard.java", "com/forzacode/a1016_02/atmosphere/card/NameGoneCard.java",
			"com/forzacode/a1016_02/atmosphere/card/SomeoneHomeCard.java", "com/forzacode/a1016_02/atmosphere/card/TheyWereWaitingCard.java",
			"com/forzacode/a1016_02/atmosphere/client/ShaderPacks.java", "com/forzacode/a1016_02/atmosphere/mixin/EntityStepMixin.java",
			"com/forzacode/a1016_02/atmosphere/mixin/PhantomSpawnerMixin.java", "com/forzacode/a1016_02/atmosphere/mixin/client/SkyMoonMixin.java",
			"com/forzacode/a1016_02/core/FogPlace.java", "com/forzacode/a1016_02/core/SelfCreated.java",
			"com/forzacode/a1016_02/core/mixin/LevelSetBlockMixin.java", "com/forzacode/a1016_02/debug/ScIndex.java",
			"com/forzacode/a1016_02/dig/mixin/ServerPlayerSleepMixin.java", "com/forzacode/a1016_02/entity/client/HisViewClient.java",
			"com/forzacode/a1016_02/entity/client/NamelessClient.java", "com/forzacode/a1016_02/entity/mixin/client/AvatarRendererMixin.java",
			"com/forzacode/a1016_02/entity/mixin/client/CameraMixin.java", "com/forzacode/a1016_02/entity/mixin/client/ClientWaypointManagerMixin.java",
			"com/forzacode/a1016_02/entity/mixin/client/WorldListEntryMixin.java", "com/forzacode/a1016_02/lore/client/WorldListMarks.java",
			"com/forzacode/a1016_02/lore/mixin/SeedCommandMixin.java", "com/forzacode/a1016_02/lore/mixin/client/LevelSummaryMixin.java");
	/** A log call at INFO or above: the logger's own, or the layer's {@code ScLog} (R13-5). */
	private static final Pattern LOG_CALL = Pattern.compile("(?:LOGGER|ScLog)\\s*\\.\\s*(info|warn|error)\\s*\\(");
	/** A WARN or ERROR straight to the logger. */
	private static final Pattern LOGGER_WARN = Pattern.compile("LOGGER\\s*\\.\\s*(warn|error)\\s*\\(");
	/** A last argument that is the caught exception (a throwable logs its frames). */
	private static final Pattern THROWN_ARG = Pattern.compile("(e|ex|t|th|err|error|exception|thrown|cause|failure)|new\\s+[\\w.]*(Exception|Error)\\s*\\(.*");
	/**
	 * Files of the layer whose throwables still reach the logger, each with its reason; empty once every one goes through
	 * {@code ScLog}. FIX-SC2-REST left the world workstream's own to FIX-WORLD (R13-5, R13-9).
	 */
	static final Set<String> THROWN_PENDING = Set.of();
	/**
	 * R13-6: nouns that describe what happened without being a feature's name; held in the layer's log lines only, where a
	 * line at INFO carries ids and bare numbers (the index's and overlay's lines are SC2-9's).
	 */
	static final List<String> LOG_NOUNS = List.of("held", "sound", "pixel", "formed", "of them");
	/** An sc id in a log string: {@code sc_04}, {@code sc04}, {@code sc 04}, {@code sc:} or {@code sc off}. */
	private static final Pattern SC_ID = Pattern.compile("\\bsc(_?\\d\\d|\\b)");

	/** The string literals of the call whose argument list starts at {@code from} (just past its open parenthesis). */
	static List<String> strings(String text, int from) {
		List<String> out = new ArrayList<>();
		int depth = 1;
		for (int i = from; i < text.length() && depth > 0; i++) {
			char c = text.charAt(i);
			if (c == '"') {
				StringBuilder s = new StringBuilder();
				for (i++; i < text.length() && text.charAt(i) != '"'; i++) {
					if (text.charAt(i) == '\\' && i + 1 < text.length()) {
						i++;
					}
					s.append(text.charAt(i));
				}
				out.add(s.toString());
			} else if (c == '\'') {
				i += text.charAt(i + 1) == '\\' ? 3 : 2;
			} else if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
			}
		}
		return out;
	}

	/** The mod's {@code src/main/java}: found from the working directory or the mod's own classes, upward. */
	static Path sourceRoot() {
		List<Path> starts = new ArrayList<>();
		starts.add(Paths.get("").toAbsolutePath());
		FabricLoader.getInstance().getModContainer("a1016_02").ifPresent(mod -> starts.addAll(mod.getRootPaths().stream()
				.filter(p -> p.getFileSystem() == Paths.get("").getFileSystem()).toList()));
		for (Path start : starts) {
			for (Path at = start; at != null; at = at.getParent()) {
				Path src = at.resolve("src/main/java");
				if (Files.isDirectory(src.resolve("com/forzacode/a1016_02"))) {
					return src;
				}
			}
		}
		return null;
	}

	/** The first word of the lists that is one of this camelCase name's words (or its plural), or null. */
	static String namedWord(String camel) {
		for (String token : camel.split("(?<=[a-z0-9])(?=[A-Z])")) {
			String word = wordFor(token.toLowerCase(Locale.ROOT));
			if (word != null) {
				return word;
			}
		}
		return null;
	}

	/** The listed word this one is (itself, a plural, or with -ed or -ing), or null. */
	private static String wordFor(String token) {
		for (List<String> list : LISTS) {
			for (String word : list) {
				if (!word.contains(" ") && (token.equals(word) || token.equals(word + "s") || token.equals(word + "es") || token.equals(word + "ed")
						|| token.equals(word + "d") || token.equals(word + "ing"))) {
					return word;
				}
			}
		}
		return null;
	}

	/**
	 * The listed word this text names, or null: a word of it (split on anything but letters), a phrase of the list in it, or,
	 * for {@code joined} text (literals, usage), any listed word inside a word ({@code noshaft}).
	 */
	static String namedIn(String text, boolean joined) {
		String lower = text.toLowerCase(Locale.ROOT);
		for (String phrase : PLAIN_PHRASES) {
			lower = lower.replace(phrase, " ");
		}
		for (List<String> list : LISTS) {
			for (String word : list) {
				if (word.contains(" ") ? lower.contains(word) : joined && lower.contains(word)) {
					return word;
				}
			}
		}
		for (String token : lower.split("[^a-z]+")) {
			String word = wordFor(token);
			if (word != null) {
				return word;
			}
		}
		return null;
	}

	private static void walk(CommandNode<CommandSourceStack> node, String path, List<String> named) {
		for (CommandNode<CommandSourceStack> child : node.getChildren()) {
			String at = path + " " + child.getName();
			if (child instanceof LiteralCommandNode<?> || child instanceof ArgumentCommandNode<?, ?>) {
				String word = namedIn(child.getName(), true);
				if (word != null) {
					named.add("/a1016 " + at + " ('" + word + "')");
				}
			}
			walk(child, at, named);
		}
	}
}
