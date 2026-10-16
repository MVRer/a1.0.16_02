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
 * few more ({@link #MORE}).
 */
public class VerifyScSpoilerTests {
	/** {@code SelfCreatedDebugTests.NAMES} (D-080), word for word. */
	static final List<String> NAMES = List.of("moon", "night", "name", "bed", "sleep", "rest", "torch", "step", "foot", "print", "track", "fog", "icon",
			"picture", "view", "seed", "version", "house", "home", "door", "chest", "pet", "animal", "friend", "cave", "lava", "shaft", "stat", "mined",
			"dropped", "turn", "trail", "brb", "record", "hole", "block", "wall", "way back", "stood", "stand");
	/** More words that would name a part (FIX-SC-FINAL: the old literals and the old log lines). */
	static final List<String> MORE = List.of("sky", "trust", "circle", "loop", "phantom", "sign", "spawn", "mob", "gap", "behind", "hollow", "list",
			"water", "landing");
	/** The keys every section may keep in plain words: its schema version, and the layer's switch. */
	private static final Set<String> PLAIN_KEYS = Set.of("version", "enabled");
	/** An opaque key: a letter, at most one more letter or digit. */
	private static final Pattern OPAQUE = Pattern.compile("[a-z][a-z0-9]?");

	/** A section: its class, a fresh one, and the version its keys were made opaque at (a file below it has the old keys). */
	record Section(Class<?> type, Supplier<?> fresh, int opaqueFrom) {
	}

	/** Every section of the layer, by its name under {@code sections}. */
	static Map<String, Section> sections() {
		Map<String, Section> sections = new LinkedHashMap<>();
		sections.put("sc", new Section(SelfCreated.Config.class, SelfCreated.Config::new, 0));
		sections.put("sc01", new Section(Sc01Config.class, Sc01Config::new, 3));
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
		return sections;
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
				helper.assertTrue(sections().containsKey(name), "the section " + name + " is not held to D-080 here");
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
			for (Field field : s.type().getFields()) {
				SerializedName named = field.getAnnotation(SerializedName.class);
				if (Modifier.isStatic(field.getModifiers()) || field.getName().equals("version")) {
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
			JsonObject file = new JsonObject();
			file.add(entry.getKey(), old);
			Object read = read(file, entry.getKey(), s);
			if (s.type().getField("version").getInt(read) != s.opaqueFrom()) {
				wrong.add(entry.getKey() + " at version " + s.type().getField("version").getInt(read));
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
				boolean layer = rel.contains("/sc/") || LAYER_FILES.contains(rel);
				String text = Files.readString(file);
				Matcher call = LOG_CALL.matcher(text);
				while (call.find()) {
					List<String> strings = strings(text, call.end());
					if (!layer && strings.stream().noneMatch(s -> SC_ID.matcher(s).find())) {
						continue;
					}
					lines++;
					for (String s : strings) {
						String word = namedIn(s, false);
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
	private static final Pattern LOG_CALL = Pattern.compile("LOGGER\\s*\\.\\s*(info|warn|error)\\s*\\(");
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
		for (List<String> list : List.of(NAMES, MORE)) {
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
		for (List<String> list : List.of(NAMES, MORE)) {
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
