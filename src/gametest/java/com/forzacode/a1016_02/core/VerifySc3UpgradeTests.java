package com.forzacode.a1016_02.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.dig.DigConfig;
import com.forzacode.a1016_02.director.DirectorConfig;
import com.forzacode.a1016_02.ending.EndingConfig;
import com.forzacode.a1016_02.ending.d.EndingDConfig;
import com.forzacode.a1016_02.entity.EntityConfig;
import com.forzacode.a1016_02.lore.LoreConfig;
import com.forzacode.a1016_02.world.WorldConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * R3-V: a 1.5.0 install's config read by this build. {@code review/a1016_02_v15_sc1.json} is the file 1.5.0 writes
 * (the 1.5.0-self-created.1 jar's class defaults, each section's version stamped as that build stamps it). Every
 * default changed since must reach the file through an upgrade step, and every value a player changed must stay.
 */
public class VerifySc3UpgradeTests {
	private static final Gson GSON = new Gson();

	/** Every section a 1.5.0 file has, and those this build added, by name: the base mod's and the layer's. */
	private static Map<String, VerifyScSpoilerTests.Section> sections() {
		Map<String, VerifyScSpoilerTests.Section> all = new LinkedHashMap<>();
		all.put("accident", new VerifyScSpoilerTests.Section(AccidentConfig.class, AccidentConfig::new, 0));
		all.put("atmosphere", new VerifyScSpoilerTests.Section(AtmosphereConfig.class, AtmosphereConfig::new, 0));
		all.put("dig", new VerifyScSpoilerTests.Section(DigConfig.class, DigConfig::new, 0));
		all.put("director", new VerifyScSpoilerTests.Section(DirectorConfig.class, DirectorConfig::new, 0));
		all.put("ending", new VerifyScSpoilerTests.Section(EndingConfig.class, EndingConfig::new, 0));
		all.put("ending_d", new VerifyScSpoilerTests.Section(EndingDConfig.class, EndingDConfig::new, 0));
		all.put("entity", new VerifyScSpoilerTests.Section(EntityConfig.class, EntityConfig::new, 0));
		all.put("lore", new VerifyScSpoilerTests.Section(LoreConfig.class, LoreConfig::new, 0));
		all.put("world", new VerifyScSpoilerTests.Section(WorldConfig.class, WorldConfig::new, 0));
		all.putAll(VerifyScSpoilerTests.sections());
		return all;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static JsonObject read(JsonObject sections, String name, VerifyScSpoilerTests.Section section) {
		Object value = ModConfig.readSection(sections, name, (Class) section.type(), (Supplier) section.fresh());
		return GSON.toJsonTree(value).getAsJsonObject();
	}

	private static boolean same(JsonElement a, JsonElement b) {
		if (a == null || b == null) {
			return a == b;
		}
		if (a.isJsonPrimitive() && b.isJsonPrimitive() && a.getAsJsonPrimitive().isNumber() && b.getAsJsonPrimitive().isNumber()) {
			return a.getAsDouble() == b.getAsDouble();
		}
		return a.equals(b);
	}

	/** A hand edit of a number: one more, or a tenth less. */
	private static JsonPrimitive edited(JsonPrimitive value) {
		double d = value.getAsDouble();
		return d == Math.rint(d) && !value.getAsString().contains(".") ? new JsonPrimitive(value.getAsLong() + 1) : new JsonPrimitive(d * 0.9);
	}

	/**
	 * The 1.5.0 file, with hand edits in every section round 3 changed and in two it did not (one key whose default
	 * stayed, and in 16 and 17 also the distance whose default moved): every section reads at this build's version, every
	 * hand edit is kept, and every other value is this build's default (a default changed without a step would leave a
	 * 1.5.0 install on the old number).
	 */
	@GameTest
	public void aConfigWrittenBy150UpgradesAndKeepsHandEdits(GameTestHelper helper) throws IOException {
		Path path = FabricLoader.getInstance().getModContainer("a1016_02_gametest").orElseThrow().findPath("review/a1016_02_v15_sc1.json").orElseThrow();
		JsonObject file = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
		JsonObject sections = file.getAsJsonObject("sections");
		Map<String, VerifyScSpoilerTests.Section> all = sections();
		helper.assertTrue(sections.size() == 41 && !sections.has("sc32") && !sections.has("sc33"), "the fixture is not 1.5.0's file: " + sections.keySet());
		Map<String, Map<String, JsonElement>> edits = new LinkedHashMap<>();
		for (String name : List.of("sc01", "sc16", "sc17", "sc18", "sc19", "sc20", "entity", "sc25")) {
			JsonObject old = sections.getAsJsonObject(name);
			JsonObject now = read(new JsonObject(), name, all.get(name));
			for (Map.Entry<String, JsonElement> e : old.entrySet()) {
				if (!e.getKey().equals("version") && e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()
						&& same(e.getValue(), now.get(e.getKey()))) {
					edits.computeIfAbsent(name, n -> new LinkedHashMap<>()).put(e.getKey(), edited(e.getValue().getAsJsonPrimitive()));
					break;
				}
			}
		}
		// A distance whose default moved in round 3, changed by hand: a changed value is kept (keepOrNew).
		edits.computeIfAbsent("sc16", n -> new LinkedHashMap<>()).put("b", new JsonPrimitive(600));
		edits.computeIfAbsent("sc17", n -> new LinkedHashMap<>()).put("b", new JsonPrimitive(520));
		edits.forEach((name, keys) -> keys.forEach((key, value) -> sections.getAsJsonObject(name).add(key, value)));
		helper.assertTrue(edits.size() == 8, "not every section got a hand edit: " + edits);

		ModConfig loaded = ModConfig.read(file.toString());
		List<String> wrong = new ArrayList<>();
		if (loaded.configVersion != ModConfig.CONFIG_VERSION) {
			wrong.add("configVersion " + loaded.configVersion);
		}
		for (Map.Entry<String, VerifyScSpoilerTests.Section> e : all.entrySet()) {
			String name = e.getKey();
			JsonObject after = read(loaded.sections, name, e.getValue());
			JsonObject defaults = read(new JsonObject(), name, e.getValue());
			Map<String, JsonElement> mine = edits.getOrDefault(name, Map.of());
			for (Map.Entry<String, JsonElement> d : defaults.entrySet()) {
				JsonElement expected = mine.getOrDefault(d.getKey(), d.getValue());
				if (!same(expected, after.get(d.getKey()))) {
					JsonObject old = sections.has(name) ? sections.getAsJsonObject(name) : new JsonObject();
					wrong.add(name + "." + d.getKey() + " " + after.get(d.getKey()) + " (1.5.0's " + old.get(d.getKey()) + ", expected " + expected + ")");
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "a 1.5.0 config read by this build: " + String.join("; ", wrong));
		helper.succeed();
	}
}
