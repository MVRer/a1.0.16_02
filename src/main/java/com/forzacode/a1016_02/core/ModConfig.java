package com.forzacode.a1016_02.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import com.forzacode.a1016_02.A1016_02;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import org.jspecify.annotations.Nullable;

/**
 * {@code config/a1016_02.json}, read with Gson at startup and written back with every default filled in.
 * Shared pacing lives in {@link #pacing()}. Workstream tunables go in {@link #section(String, Class, Supplier)}
 * and are stored under {@code sections.<ws>}.
 *
 * <p>Versions (D-075): the file's root {@link #configVersion} covers the root and {@link Pacing}; each section keeps
 * its own {@code version} ({@link Versioned}). Missing means 0, which is v1.1 or older. On load every part is upgraded
 * one version at a time: a step replaces a value that still equals the old default with the new default and keeps
 * anything the player changed ({@link Upgrades}), then the new version is written. Every later default change adds a
 * step.
 */
public final class ModConfig {
	/**
	 * The root and {@link Pacing}'s current version. 1: v1.2's pacing (D-070, {@link Pacing#upgrade}). Add a step
	 * there and raise this for every later change of a default.
	 */
	public static final int CONFIG_VERSION = 1;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String FILE_NAME = A1016_02.MOD_ID + ".json";

	private static ModConfig instance = fresh();
	private static final Map<String, Object> SECTION_CACHE = new HashMap<>();

	/**
	 * The version the root and {@code pacing} were last written at. Missing in the file reads as 0 (v1.1 or older):
	 * the field's own default must stay 0 so Gson keeps it for a file without the key; a new file starts at
	 * {@link #CONFIG_VERSION}.
	 */
	public int configVersion = 0;
	/** Test and debug only: divides every real-time duration by {@link #devFastDivisor} (D-010). */
	public boolean devFastMode = false;
	public int devFastDivisor = 60;
	public Pacing pacing = new Pacing();
	public JsonObject sections = new JsonObject();

	/**
	 * A config section with a version (D-075). {@link #section} calls {@link #upgrade()} once, when the section is
	 * first read (from the file or from its defaults), before anyone sees it, and writes the result back. Keep a
	 * public {@code int version = 0} field (0, so a file without it reads as v1.1 or older; a fresh section is
	 * upgraded too, which leaves its defaults as they are) and bring it up one step at a time with {@link Upgrades}:
	 *
	 * <pre>{@code
	 * public boolean upgrade() {
	 *     if (version >= CURRENT) return false;
	 *     MySection d = new MySection();
	 *     if (version < 1) { radius = Upgrades.keepOrNew(radius, 48, d.radius); }
	 *     version = CURRENT;
	 *     return true;
	 * }
	 * }</pre>
	 */
	public interface Versioned {
		/** Brings this section up to its current version. Returns true if anything changed (the version included). */
		boolean upgrade();
	}

	public static ModConfig get() {
		return instance;
	}

	public static Pacing pacing() {
		return instance.pacing;
	}

	/**
	 * Converts a real-time duration to ticks, divided by {@code devFastDivisor} when {@code devFastMode} is on.
	 * Use it for every real-time tunable in your own section too.
	 */
	public static long realTicks(double seconds) {
		long ticks = Math.round(seconds * 20.0);
		if (instance.devFastMode && instance.devFastDivisor > 1) {
			ticks = Math.max(1, ticks / instance.devFastDivisor);
		}
		return ticks;
	}

	/**
	 * Your workstream's config section, stored under {@code sections.<name>}. Missing fields keep the defaults
	 * from {@code defaults} (use a class with public fields and a no-arg constructor, e.g. {@code Type::new}).
	 * A {@link Versioned} section is upgraded on this first read. The first call writes the section into the file
	 * so its values are easy to find and edit.
	 */
	@SuppressWarnings("unchecked")
	public static synchronized <T> T section(String name, Class<T> type, Supplier<T> defaults) {
		Object cached = SECTION_CACHE.get(name);
		if (type.isInstance(cached)) {
			return (T) cached;
		}
		T value = readSection(instance.sections, name, type, defaults);
		instance.sections.add(name, GSON.toJsonTree(value));
		SECTION_CACHE.put(name, value);
		save();
		return value;
	}

	/**
	 * Writes one section back to {@code config/a1016_02.json}, in memory and on disk (a live edit such as a tuning
	 * command). Only {@code sections.<name>} changes in the file; everything else is kept as it is there, so a hand
	 * edit made while the game runs is not lost. {@link #section} returns {@code value} from now on.
	 */
	public static synchronized void saveSection(String name, Object value) {
		JsonElement tree = GSON.toJsonTree(value);
		instance.sections.add(name, tree);
		SECTION_CACHE.put(name, value);
		Path path = path();
		JsonObject root = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				JsonElement read = JsonParser.parseReader(reader);
				root = read.isJsonObject() ? read.getAsJsonObject() : null;
			} catch (IOException | JsonParseException e) {
				A1016_02.LOGGER.warn("[a1016] could not read {}, rewriting it from memory", path, e);
			}
		}
		if (root == null) {
			root = GSON.toJsonTree(instance).getAsJsonObject();
		}
		JsonObject sections = root.has("sections") && root.get("sections").isJsonObject() ? root.getAsJsonObject("sections") : new JsonObject();
		sections.add(name, tree);
		root.add("sections", sections);
		write(path, root);
	}

	/**
	 * A section as {@link #section} reads it, without the cache or the file: parsed from {@code sections.<name>}
	 * (defaults if missing or invalid) and upgraded if it is {@link Versioned}. For tests of a section's upgrade steps
	 * (an old file's {@code sections} object in, the section as the game would see it out); game code uses
	 * {@link #section}.
	 */
	public static <T> T readSection(JsonObject sections, String name, Class<T> type, Supplier<T> defaults) {
		T value = defaults.get();
		if (sections.has(name)) {
			JsonElement json = sections.get(name);
			if (json.isJsonObject()) {
				Lenient.readInto(json.getAsJsonObject(), value, "sections." + name);
			} else if (!json.isJsonNull()) {
				A1016_02.LOGGER.warn("[a1016] config section '{}' is not an object, using its defaults", name);
			}
		}
		if (value instanceof Versioned versioned && versioned.upgrade()) {
			A1016_02.LOGGER.info("[a1016] config section '{}' upgraded to new defaults (values you changed are kept)", name);
		}
		return value;
	}

	/**
	 * Parses a config file's text with the same rules as {@link #load()} (unknown keys are skipped, a value that cannot
	 * be read keeps its default), without upgrading; null when the text is not a JSON object. Tests only.
	 */
	static @Nullable ModConfig parse(String json) {
		return parse(JsonParser.parseString(json));
	}

	/** {@link #parse(String)} from a parsed tree. */
	private static @Nullable ModConfig parse(JsonElement json) {
		if (!json.isJsonObject()) {
			return null;
		}
		ModConfig config = new ModConfig();
		Lenient.readInto(json.getAsJsonObject(), config, "");
		return config;
	}

	/** A config file's text as {@link #load()} reads it: parsed, missing parts filled in and upgraded. Tests only. */
	static ModConfig read(String json) {
		return prepare(parse(json));
	}

	/** What {@link #load()} writes for this config. Tests only. */
	String toJson() {
		return GSON.toJson(this);
	}

	/**
	 * Loads (or creates) the config file, upgrades it (D-075) and writes it back. Called by {@code CoreInit}. Lenient
	 * (the game always starts): a value that cannot be read (a string in a number, a fraction in a whole number) keeps
	 * its default with one warning naming its key, and every other value in the file is kept. A file that is not JSON
	 * at all is copied to {@code a1016_02.json.broken} before the defaults are written.
	 */
	public static synchronized void load() {
		Path path = path();
		ModConfig loaded = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = parse(JsonParser.parseReader(reader));
				if (loaded == null) {
					A1016_02.LOGGER.error("[a1016] {} is not a JSON object, using defaults", path);
				}
			} catch (IOException | RuntimeException e) {
				A1016_02.LOGGER.error("[a1016] could not read {}, using defaults", path, e);
			}
			if (loaded == null) {
				keepBroken(path);
			}
		}
		instance = loaded != null ? prepare(loaded) : fresh();
		SECTION_CACHE.clear();
		save();
		if (instance.devFastMode) {
			A1016_02.LOGGER.warn("[a1016] devFastMode is ON: real-time pacing is divided by {}", instance.devFastDivisor);
		}
	}

	/** A config with every default, at the current version (a new file). */
	private static ModConfig fresh() {
		ModConfig config = new ModConfig();
		config.configVersion = CONFIG_VERSION;
		return config;
	}

	/** Fills in missing parts and runs the upgrade steps from the file's version (D-075). */
	private static ModConfig prepare(@Nullable ModConfig config) {
		if (config == null) {
			return fresh();
		}
		if (config.pacing == null) {
			config.pacing = new Pacing();
		}
		if (config.sections == null) {
			config.sections = new JsonObject();
		}
		config.pacing.fillMissingWeights();
		if (config.upgrade()) {
			A1016_02.LOGGER.info("[a1016] config upgraded to version {} (values you changed are kept)", CONFIG_VERSION);
		}
		return config;
	}

	/** Runs every step from {@link #configVersion} to {@link #CONFIG_VERSION}. Returns true if it changed anything. */
	boolean upgrade() {
		if (configVersion >= CONFIG_VERSION) {
			return false;
		}
		pacing.upgrade(Math.max(0, configVersion));
		configVersion = CONFIG_VERSION;
		return true;
	}

	private static void save() {
		Path path = path();
		write(path, GSON.toJsonTree(instance));
	}

	/** A file that could not be read at all is kept next to the new one, so the player's edits are not lost for good. */
	private static void keepBroken(Path path) {
		Path copy = path.resolveSibling(FILE_NAME + ".broken");
		try {
			Files.copy(path, copy, StandardCopyOption.REPLACE_EXISTING);
			A1016_02.LOGGER.warn("[a1016] the unreadable config was kept as {}", copy);
		} catch (IOException e) {
			A1016_02.LOGGER.error("[a1016] could not keep a copy of {}", path, e);
		}
	}

	/**
	 * Lenient reading (the game always starts, a typo costs one value): a JSON object read field by field into an object
	 * that already holds its defaults, with Gson's rules for each value (unknown keys skipped, the fields Gson would
	 * fill: not static, not transient, the class's and its superclasses'). A value that cannot be read (a string in a
	 * number field, a fraction in an int, a list where a number goes, null) keeps that field's default and logs one
	 * warning naming its key; a field holding one of the mod's own config objects ({@link Pacing}) is read the same way,
	 * value by value.
	 */
	private static final class Lenient {
		private Lenient() {
		}

		static void readInto(JsonObject json, Object target, String where) {
			for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
				for (Field field : type.getDeclaredFields()) {
					int modifiers = field.getModifiers();
					if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
						continue;
					}
					String key = field.getName();
					if (!json.has(key)) {
						continue;
					}
					readField(json.get(key), target, field, where.isEmpty() ? key : where + "." + key);
				}
			}
		}

		private static void readField(JsonElement value, Object target, Field field, String key) {
			try {
				field.setAccessible(true);
				Object current = field.get(target);
				if (value.isJsonNull()) {
					if (current != null) {
						A1016_02.LOGGER.warn("[a1016] config value '{}' is null, keeping its default {}", key, GSON.toJson(current));
					}
					return;
				}
				if (current != null && value.isJsonObject() && ownConfig(field.getType())) {
					readInto(value.getAsJsonObject(), current, key);
					return;
				}
				// Through the text, as the file is read: Gson's stream reader refuses a fraction in an int (its tree reader
				// would cut 1.5 to 1 without a word).
				Object read = GSON.fromJson(value.toString(), field.getGenericType());
				if (read == null && current != null) {
					A1016_02.LOGGER.warn("[a1016] config value '{}' ({}) could not be read, keeping its default {}", key, value, GSON.toJson(current));
					return;
				}
				field.set(target, read);
			} catch (RuntimeException | IllegalAccessException e) {
				A1016_02.LOGGER.warn("[a1016] config value '{}' ({}) could not be read ({}), keeping its default", key, value, e.getMessage());
			}
		}

		/** One of the mod's own plain config classes (read value by value), not a record, enum or library type. */
		private static boolean ownConfig(Class<?> type) {
			return type.getName().startsWith("com.forzacode.") && !type.isEnum() && !type.isRecord() && !type.isInterface() && !type.isArray();
		}
	}

	private static void write(Path path, JsonElement root) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			A1016_02.LOGGER.error("[a1016] could not write {}", path, e);
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/**
	 * Keep-or-new for upgrade steps (D-075): each returns {@code newDefault} when {@code current} still equals
	 * {@code oldDefault} (the player never changed it) and {@code current} otherwise. Numbers compare within
	 * {@link #EPSILON} of the old default's size, so a float field, or a value written back through a float, still
	 * counts as unchanged; NaN never equals anything and is kept. Nothing passed in is modified.
	 */
	public static final class Upgrades {
		/** Relative tolerance for "still the old default": well above float rounding, far below any real edit. */
		public static final double EPSILON = 1.0E-6;

		private Upgrades() {
		}

		/** Whether {@code value} equals {@code oldDefault} within {@link #EPSILON} (relative, at least 1 absolute unit's worth). */
		public static boolean same(double value, double oldDefault) {
			return Math.abs(value - oldDefault) <= EPSILON * Math.max(1.0, Math.abs(oldDefault));
		}

		public static double keepOrNew(double current, double oldDefault, double newDefault) {
			return same(current, oldDefault) ? newDefault : current;
		}

		public static float keepOrNew(float current, float oldDefault, float newDefault) {
			return same(current, oldDefault) ? newDefault : current;
		}

		public static int keepOrNew(int current, int oldDefault, int newDefault) {
			return current == oldDefault ? newDefault : current;
		}

		public static long keepOrNew(long current, long oldDefault, long newDefault) {
			return current == oldDefault ? newDefault : current;
		}

		public static boolean keepOrNew(boolean current, boolean oldDefault, boolean newDefault) {
			return current == oldDefault ? newDefault : current;
		}

		/** Null counts as a value: a null current equals a null old default only. */
		public static @Nullable String keepOrNew(@Nullable String current, @Nullable String oldDefault, @Nullable String newDefault) {
			return Objects.equals(current, oldDefault) ? newDefault : current;
		}

		/**
		 * Element by element: each entry still equal to the old default's takes the new default's. A null current takes
		 * a copy of the new default; an array whose length differs from the old default's (the player reshaped it) is kept
		 * whole. When the new default is longer, the extra entries are appended. Always a new array.
		 */
		public static float[] keepOrNew(float @Nullable [] current, float[] oldDefault, float[] newDefault) {
			if (current == null) {
				return newDefault.clone();
			}
			if (current.length != oldDefault.length) {
				return current.clone();
			}
			float[] out = Arrays.copyOf(current, Math.max(current.length, newDefault.length));
			for (int i = 0; i < out.length; i++) {
				if (i >= current.length) {
					out[i] = newDefault[i];
				} else if (i < newDefault.length) {
					out[i] = keepOrNew(current[i], oldDefault[i], newDefault[i]);
				}
			}
			return out;
		}

		/** {@link #keepOrNew(float[], float[], float[])} for doubles. */
		public static double[] keepOrNew(double @Nullable [] current, double[] oldDefault, double[] newDefault) {
			if (current == null) {
				return newDefault.clone();
			}
			if (current.length != oldDefault.length) {
				return current.clone();
			}
			double[] out = Arrays.copyOf(current, Math.max(current.length, newDefault.length));
			for (int i = 0; i < out.length; i++) {
				if (i >= current.length) {
					out[i] = newDefault[i];
				} else if (i < newDefault.length) {
					out[i] = keepOrNew(current[i], oldDefault[i], newDefault[i]);
				}
			}
			return out;
		}

		/**
		 * A weight map ({@code Pacing.attentionWeights}, one stage of a tag-weight map), entry by entry: for every key the
		 * old or the new default names, an entry still equal to the old default's (both missing counts as equal) takes
		 * the new default's, and a missing new default removes it; entries the player changed, added or removed are kept
		 * as they are. Order: the current map's, then new keys in the new default's order. A null current is treated as
		 * empty. Always a new map.
		 */
		public static Map<String, Double> weights(@Nullable Map<String, Double> current, Map<String, Double> oldDefault,
				Map<String, Double> newDefault) {
			Map<String, Double> out = current != null ? new LinkedHashMap<>(current) : new LinkedHashMap<>();
			Set<String> keys = new LinkedHashSet<>(oldDefault.keySet());
			keys.addAll(newDefault.keySet());
			for (String key : keys) {
				Double old = oldDefault.get(key);
				Double now = out.get(key);
				boolean unchanged = old == null ? now == null : now != null && same(now, old);
				if (!unchanged) {
					continue;
				}
				Double next = newDefault.get(key);
				if (next == null) {
					out.remove(key);
				} else {
					out.put(key, next);
				}
			}
			return out;
		}

		/**
		 * A tag-weight map by stage (the director's {@code stageTagWeights}: stage name to tag name to weight), stage by
		 * stage with {@link #weights}. A stage the player removed whole while its old default had entries stays removed;
		 * a stage only the new default has is added. A null current is treated as empty. Always new maps.
		 */
		public static Map<String, Map<String, Double>> tagWeights(@Nullable Map<String, Map<String, Double>> current,
				Map<String, Map<String, Double>> oldDefault, Map<String, Map<String, Double>> newDefault) {
			Map<String, Map<String, Double>> out = new LinkedHashMap<>();
			if (current != null) {
				current.forEach((stage, weights) -> out.put(stage, weights != null ? new LinkedHashMap<>(weights) : new LinkedHashMap<>()));
			}
			Set<String> stages = new LinkedHashSet<>(oldDefault.keySet());
			stages.addAll(newDefault.keySet());
			for (String stage : stages) {
				Map<String, Double> old = oldDefault.getOrDefault(stage, Map.of());
				Map<String, Double> next = newDefault.getOrDefault(stage, Map.of());
				Map<String, Double> now = out.get(stage);
				if (now == null) {
					if (old.isEmpty() && !next.isEmpty()) {
						out.put(stage, new LinkedHashMap<>(next));
					}
					continue;
				}
				out.put(stage, weights(now, old, next));
			}
			return out;
		}
	}
}
