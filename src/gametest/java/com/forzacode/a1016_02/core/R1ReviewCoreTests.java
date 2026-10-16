package com.forzacode.a1016_02.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.dig.DigConfig;
import com.forzacode.a1016_02.director.CardInfo;
import com.forzacode.a1016_02.director.DirectorBrain;
import com.forzacode.a1016_02.director.DirectorConfig;
import com.forzacode.a1016_02.director.DirectorRules;
import com.forzacode.a1016_02.director.WorldMemory;
import com.forzacode.a1016_02.ending.EndingConfig;
import com.forzacode.a1016_02.ending.d.EndingDConfig;
import com.forzacode.a1016_02.entity.EntityConfig;
import com.forzacode.a1016_02.lore.LoreConfig;
import com.forzacode.a1016_02.world.WorldConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;

/**
 * Bug hunt R1 (v1.2): config versioning (D-075) against Mariano's real v1.1 file and hand edits. Pure: nothing here
 * reads or writes the live config file. {@link ConfigVersionTests} extends this.
 */
public class R1ReviewCoreTests {
	private static final Gson GSON = new Gson();

	/** Mariano's real v1.1 {@code config/a1016_02.json} (copied read only into the test resources). */
	private static String realV11File() {
		Path path = FabricLoader.getInstance().getModContainer("a1016_02_gametest").orElseThrow().findPath("review/a1016_02_v11.json").orElseThrow();
		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private record Section(Class<?> type, Supplier<?> defaults, Set<String> stepped) {
	}

	private static Map<String, Section> sections() {
		Map<String, Section> map = new LinkedHashMap<>();
		map.put("director", new Section(DirectorConfig.class, DirectorConfig::new,
				Set.of("version", "aloneAmbientPerHour", "proximityAmbientPerHour", "tracesMinorsPerHour", "stageTagWeights")));
		map.put("atmosphere", new Section(AtmosphereConfig.class, AtmosphereConfig::new, Set.of("version", "caveSoundAloneRadius", "fogDriftFadeTicks")));
		map.put("entity", new Section(EntityConfig.class, EntityConfig::new, Set.of("version")));
		map.put("dig", new Section(DigConfig.class, DigConfig::new, Set.of("version")));
		map.put("world", new Section(WorldConfig.class, WorldConfig::new, Set.of()));
		map.put("accident", new Section(AccidentConfig.class, AccidentConfig::new, Set.of()));
		map.put("ending", new Section(EndingConfig.class, EndingConfig::new, Set.of()));
		map.put("ending_d", new Section(EndingDConfig.class, EndingDConfig::new, Set.of()));
		map.put("lore", new Section(LoreConfig.class, LoreConfig::new, Set.of()));
		return map;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object read(JsonObject sections, String name, Section s) {
		return ModConfig.readSection(sections, name, (Class) s.type(), (Supplier) s.defaults());
	}

	/** As written to the file and read back (floats compare as their text, as in the file). */
	private static JsonObject tree(Object value) {
		return JsonParser.parseString(GSON.toJson(value)).getAsJsonObject();
	}

	private static final Set<String> PACING_STEP = Set.of("tracesStartMinHours", "tracesStartMaxHours", "proximityStartMinHours",
			"proximityStartMaxHours", "tempoShift", "aloneMaxAmbient", "tracesAmbientPerHour", "proximityMinorsPerHourMin", "proximityMinorsPerHourMax",
			"proximityMajorEveryHoursMin", "proximityMajorEveryHoursMax", "firstAccidentMinHours", "emptySessionChance", "pityStartHours",
			"fogDriftMinSeconds", "fogDriftMaxSeconds");

	/**
	 * Mariano's real v1.1 file, as {@code load} and every section's first read see it: the root to version 1, every
	 * stepped value at v1.2's default (none was changed by hand), every other value of the file kept exactly (the entity
	 * section at version 3 included), and a second load of what was written changes nothing.
	 */
	@GameTest
	public void r1RealV11ConfigUpgradesAndKeepsEverythingElse(GameTestHelper helper) {
		String text = realV11File();
		JsonObject file = JsonParser.parseString(text).getAsJsonObject();
		helper.assertTrue(!file.has("configVersion") && file.getAsJsonObject("sections").getAsJsonObject("entity").get("version").getAsInt() == 3,
				"the fixture is not the v1.1 file");
		ModConfig loaded = ModConfig.read(text);
		helper.assertTrue(loaded.configVersion == ModConfig.CONFIG_VERSION, "configVersion " + loaded.configVersion);
		JsonObject pacingOut = tree(loaded.pacing);
		JsonObject pacingNew = tree(new Pacing());
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : file.getAsJsonObject("pacing").entrySet()) {
			JsonElement expected = PACING_STEP.contains(e.getKey()) ? pacingNew.get(e.getKey()) : e.getValue();
			if (!expected.equals(pacingOut.get(e.getKey()))) {
				wrong.add("pacing." + e.getKey() + " " + e.getValue() + " -> " + pacingOut.get(e.getKey()) + " (expected " + expected + ")");
			}
		}
		JsonObject written = JsonParser.parseString(loaded.toJson()).getAsJsonObject();
		JsonObject writtenSections = written.getAsJsonObject("sections");
		for (Map.Entry<String, Section> s : sections().entrySet()) {
			String name = s.getKey();
			JsonObject before = file.getAsJsonObject("sections").getAsJsonObject(name);
			JsonObject after = tree(read(loaded.sections, name, s.getValue()));
			JsonObject fresh = tree(read(new JsonObject(), name, s.getValue()));
			for (Map.Entry<String, JsonElement> e : before.entrySet()) {
				JsonElement expected = s.getValue().stepped().contains(e.getKey()) ? fresh.get(e.getKey()) : e.getValue();
				if (!expected.equals(after.get(e.getKey()))) {
					wrong.add(name + "." + e.getKey() + " " + e.getValue() + " -> " + after.get(e.getKey()) + " (expected " + expected + ")");
				}
			}
			writtenSections.add(name, after);
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " values wrong: " + String.join("; ", wrong));

		// The file as load and every section's first read leave it, loaded again: nothing moves.
		ModConfig again = ModConfig.read(written.toString());
		JsonObject againRoot = JsonParser.parseString(again.toJson()).getAsJsonObject();
		helper.assertTrue(againRoot.getAsJsonObject("pacing").equals(written.getAsJsonObject("pacing"))
				&& againRoot.get("configVersion").equals(written.get("configVersion")), "a second load changed the root or the pacing");
		for (Map.Entry<String, Section> s : sections().entrySet()) {
			JsonObject second = tree(read(again.sections, s.getKey(), s.getValue()));
			helper.assertTrue(second.equals(writtenSections.getAsJsonObject(s.getKey())), "a second load changed section " + s.getKey());
		}
		helper.succeed();
	}

	/**
	 * FINDING (medium): a v1.1 file whose Traces window was slowed by hand (max 4.5 h, Proximity left at 5 to 6 h:
	 * ordered, Traces always before Proximity) upgrades into Traces 0.4 to 4.5 h and Proximity 1.75 to 2.75 h: the
	 * hand-changed max is kept and the untouched Proximity takes v1.2's window, so Proximity's window lies inside
	 * Traces'. The director then rolls {@code proximityAt = max(tracesAt + 1, ...)} (DirectorBrain.advanceWorld):
	 * in about half the worlds Traces lasts one tick and Proximity (majors, the first accident from 3.5 h) comes at
	 * the Traces point, sooner than the player ever configured.
	 */
	@GameTest
	public void r1AHandSlowedTracesWindowStaysBeforeProximity(GameTestHelper helper) {
		JsonObject file = JsonParser.parseString(realV11File()).getAsJsonObject();
		file.getAsJsonObject("pacing").addProperty("tracesStartMaxHours", 4.5); // the one hand edit
		Pacing p = ModConfig.read(file.toString()).pacing;
		WorldProfile profile = new WorldProfile(EnumSet.of(Habit.CARVER, Habit.MOURNER), Density.NORMAL, Tempo.SLOW_BURN, WorldProfile.FIXED_FRAGMENTS,
				Signature.CROSS_ROW);
		DirectorRules rules = DirectorRules.from(p, new DirectorConfig(), profile);
		DirectorBrain brain = new DirectorBrain(rules, List.<CardInfo>of(), 10);
		int oneTick = 0;
		int worlds = 200;
		RandomSource random = RandomSource.create(1016);
		for (int i = 0; i < worlds; i++) {
			WorldMemory w = new WorldMemory();
			Env env = new Env();
			brain.advanceWorld(w, new DirectorBrain.Clock(0, 0), env, random, DirectorBrain.Recorder.NONE);
			if (w.proximityAt() - w.tracesAt() <= rules.tickInterval) {
				oneTick++;
			}
		}
		helper.assertTrue(p.tracesStartMaxHours <= p.proximityStartMinHours && oneTick == 0,
				"upgraded windows: Traces " + p.tracesStartMinHours + " to " + p.tracesStartMaxHours + " h, Proximity " + p.proximityStartMinHours + " to "
						+ p.proximityStartMaxHours + " h; Traces lasts at most one director tick in " + oneTick + " of " + worlds + " worlds");
		helper.succeed();
	}

	/**
	 * FINDING (low, the same since v1.0): a hand-edit typo in the file. (1) A string in a number field of type double
	 * ({@code "minAnyGapMinutes": "5 min"}) makes Gson throw a bare {@code NumberFormatException}, which neither
	 * {@code readSection} nor {@code load} catches (they catch {@code JsonParseException}): {@code section} throws to
	 * whoever reads the section, and in the root or {@code pacing} {@code ModConfig.load} throws from
	 * {@code CoreInit}, so the game does not start. (2) Any other wrong-typed value ({@code "aloneMaxAmbient": 1.5},
	 * an int) is a {@code JsonSyntaxException}: the whole section falls back to its defaults and {@code section}
	 * writes them over the file, or {@code load} saves a fresh config over the whole file; every other hand edit in it
	 * is lost for good.
	 */
	@GameTest
	public void r1OneWrongTypedValueKeepsTheOtherHandEdits(GameTestHelper helper) {
		List<String> broken = new ArrayList<>();
		JsonObject typo = JsonParser.parseString("{\"director\": {\"aloneAmbientPerHour\": 0.6, \"minAnyGapMinutes\": \"5 min\"}}").getAsJsonObject();
		try {
			DirectorConfig director = ModConfig.readSection(typo, "director", DirectorConfig.class, DirectorConfig::new);
			if (director.aloneAmbientPerHour != 0.6) {
				broken.add("a string in a double field reset the section: the hand-set aloneAmbientPerHour 0.6 became " + director.aloneAmbientPerHour);
			}
		} catch (RuntimeException e) {
			broken.add("a string in a double field: readSection threw " + e);
		}
		JsonObject wrongInt = JsonParser.parseString("{\"director\": {\"aloneAmbientPerHour\": 0.6, \"firstHourMaxFires\": 4.5}}").getAsJsonObject();
		DirectorConfig director = ModConfig.readSection(wrongInt, "director", DirectorConfig.class, DirectorConfig::new);
		if (director.aloneAmbientPerHour != 0.6) {
			broken.add("a fraction in an int field reset the whole section: the hand-set aloneAmbientPerHour 0.6 became " + director.aloneAmbientPerHour);
		}
		try {
			ModConfig.parse("{\"pacing\": {\"tracesStartMaxHours\": \"2h\", \"aloneMaxAmbient\": 2}}");
		} catch (JsonParseException e) {
			broken.add("a string in a pacing double: load() would save fresh defaults over the whole file (" + e.getClass().getSimpleName() + ")");
		} catch (RuntimeException e) {
			broken.add("a string in a pacing double: parse threw " + e + ", which load() does not catch (the mod's init fails)");
		}
		try {
			ModConfig.parse("{\"pacing\": {\"aloneMaxAmbient\": 1.5, \"tracesStartMaxHours\": 2.0}}");
		} catch (JsonParseException e) {
			broken.add("a fraction in a pacing int: load() saves fresh defaults over the whole file, every section with it (" + e.getClass().getSimpleName() + ")");
		}
		helper.assertTrue(broken.isEmpty(), String.join("; ", broken));
		helper.succeed();
	}

	/** A bare env for {@link DirectorBrain#advanceWorld}: Alone, neutral attention. */
	private static final class Env implements DirectorBrain.Env {
		Stage stage = Stage.ALONE;

		@Override
		public Stage stage() {
			return stage;
		}

		@Override
		public void setStage(Stage stage) {
			this.stage = stage;
		}

		@Override
		public double attention() {
			return 10;
		}

		@Override
		public double tension() {
			return 0;
		}

		@Override
		public void addTension(double delta, String reason) {
		}

		@Override
		public boolean contextFits(CardInfo card) {
			return true;
		}

		@Override
		public FireResult fire(CardInfo card, boolean fake) {
			return FireResult.FIRED;
		}
	}
}
