package com.forzacode.a1016_02.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.forzacode.a1016_02.A1016_02;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

/**
 * {@code config/a1016_02.json}, read with Gson at startup and written back with every default filled in.
 * Shared pacing lives in {@link #pacing()}. Workstream tunables go in {@link #section(String, Class, Supplier)}
 * and are stored under {@code sections.<ws>}.
 */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String FILE_NAME = A1016_02.MOD_ID + ".json";

	private static ModConfig instance = new ModConfig();
	private static final Map<String, Object> SECTION_CACHE = new HashMap<>();

	/** Test and debug only: divides every real-time duration by {@link #devFastDivisor} (D-010). */
	public boolean devFastMode = false;
	public int devFastDivisor = 60;
	public Pacing pacing = new Pacing();
	public JsonObject sections = new JsonObject();

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
	 * The first call writes the defaults into the file so they are easy to find and edit.
	 */
	@SuppressWarnings("unchecked")
	public static synchronized <T> T section(String name, Class<T> type, Supplier<T> defaults) {
		Object cached = SECTION_CACHE.get(name);
		if (type.isInstance(cached)) {
			return (T) cached;
		}
		T value = null;
		if (instance.sections.has(name)) {
			try {
				value = GSON.fromJson(instance.sections.get(name), type);
			} catch (JsonParseException e) {
				A1016_02.LOGGER.error("[a1016] config section '{}' is invalid, using defaults", name, e);
			}
		}
		if (value == null) {
			value = defaults.get();
		}
		instance.sections.add(name, GSON.toJsonTree(value));
		SECTION_CACHE.put(name, value);
		save();
		return value;
	}

	/** Parses a config file's text with the same rules as {@link #load()} (unknown keys are skipped). Tests only. */
	static ModConfig parse(String json) {
		return GSON.fromJson(json, ModConfig.class);
	}

	/** Loads (or creates) the config file. Called by {@code CoreInit}. */
	public static synchronized void load() {
		Path path = path();
		ModConfig loaded = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = GSON.fromJson(reader, ModConfig.class);
			} catch (IOException | JsonParseException e) {
				A1016_02.LOGGER.error("[a1016] could not read {}, using defaults", path, e);
			}
		}
		instance = loaded != null ? loaded : new ModConfig();
		if (instance.pacing == null) {
			instance.pacing = new Pacing();
		}
		if (instance.sections == null) {
			instance.sections = new JsonObject();
		}
		instance.pacing.fillMissingWeights();
		SECTION_CACHE.clear();
		save();
		if (instance.devFastMode) {
			A1016_02.LOGGER.warn("[a1016] devFastMode is ON: real-time pacing is divided by {}", instance.devFastDivisor);
		}
	}

	private static void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			A1016_02.LOGGER.error("[a1016] could not write {}", path, e);
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}
}
