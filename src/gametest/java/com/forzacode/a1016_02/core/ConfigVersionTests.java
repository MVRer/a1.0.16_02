package com.forzacode.a1016_02.core;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Versioned config (D-075) and v1.2's pacing (D-070): the root's {@code configVersion}, {@link Pacing}'s step 0 to 1,
 * {@link ModConfig.Upgrades} and {@link ModConfig.Versioned} sections. Pure: nothing here reads or writes the live
 * config file. {@link FogWeatherTests} extends this.
 */
public class ConfigVersionTests {
	/** A section with a version, as a workstream would write one: v0's defaults were radius 48, {0.15, 0.3}, "A", false. */
	public static final class VersionedSection implements ModConfig.Versioned {
		public int version = 0;
		public double radius = 24;
		public float[] byStage = {0.3F, 0.6F};
		public String mode = "B";
		public boolean loud = true;
		public long periodTicks = 400;

		@Override
		public boolean upgrade() {
			if (version >= 1) {
				return false;
			}
			VersionedSection d = new VersionedSection();
			radius = ModConfig.Upgrades.keepOrNew(radius, 48, d.radius);
			byStage = ModConfig.Upgrades.keepOrNew(byStage, new float[] {0.15F, 0.3F}, d.byStage);
			mode = ModConfig.Upgrades.keepOrNew(mode, "A", d.mode);
			loud = ModConfig.Upgrades.keepOrNew(loud, false, d.loud);
			periodTicks = ModConfig.Upgrades.keepOrNew(periodTicks, 200L, d.periodTicks);
			version = 1;
			return true;
		}
	}

	/** A v1.1 pacing: every field D-070 changed back at its v1.1 default. */
	private static Pacing v11Pacing() {
		Pacing p = new Pacing();
		p.tracesStartMinHours = 1;
		p.tracesStartMaxHours = 3;
		p.proximityStartMinHours = 5;
		p.proximityStartMaxHours = 6;
		p.tempoShift = 0.4;
		p.aloneMaxAmbient = 1;
		p.tracesAmbientPerHour = 1;
		p.proximityMinorsPerHourMin = 1;
		p.proximityMinorsPerHourMax = 2;
		p.proximityMajorEveryHoursMin = 2;
		p.proximityMajorEveryHoursMax = 4;
		p.firstAccidentMinHours = 6;
		p.emptySessionChance = 0.2;
		p.pityStartHours = 1;
		p.fogDriftMinSeconds = 3;
		p.fogDriftMaxSeconds = 8;
		return p;
	}

	@GameTest
	public void pacingDefaultsAreVersionTwelve(GameTestHelper helper) {
		Pacing p = new Pacing();
		helper.assertTrue(p.tracesStartMinHours == 0.4 && p.tracesStartMaxHours == 0.9, "Traces window " + p.tracesStartMinHours + "-" + p.tracesStartMaxHours);
		helper.assertTrue(p.proximityStartMinHours == 1.75 && p.proximityStartMaxHours == 2.75, "Proximity window");
		helper.assertTrue(p.tempoShift == 0.25 && p.paceFactor(Tempo.EARLY) == 0.75 && p.paceFactor(Tempo.VERY_LATE) == 1.25, "tempo shift");
		helper.assertTrue(p.aloneMaxAmbient == 3 && p.tracesAmbientPerHour == 2, "Alone and Traces rates");
		helper.assertTrue(p.proximityMinorsPerHourMin == 1.5 && p.proximityMinorsPerHourMax == 2.5, "Proximity minors");
		helper.assertTrue(p.proximityMajorEveryHoursMin == 1.5 && p.proximityMajorEveryHoursMax == 3, "Proximity majors");
		helper.assertTrue(p.firstAccidentMinHours == 3.5 && p.emptySessionChance == 0.1 && p.pityStartHours == 0.5, "accident, empty, pity");
		helper.assertTrue(p.fogDriftMinSeconds == 4 && p.fogDriftMaxSeconds == 10, "fog drift seconds");
		// Unchanged by D-070 (the literal-hour tests keep them).
		helper.assertTrue(p.joinGraceMinutes == 5 && p.minorGapMinutes == 15 && p.majorGapMinutes == 60, "gaps changed");
		helper.succeed();
	}

	/** D-075: a v1.1 file (no versions, old defaults, one value changed by hand) loads into v1.2's defaults, keeps the change, writes version 1. */
	@GameTest
	public void v11ConfigUpgradesToNewDefaultsAndKeepsChangedValues(GameTestHelper helper) {
		ModConfig old = new ModConfig();
		old.pacing = v11Pacing();
		old.pacing.tracesStartMaxHours = 2.5; // changed by hand
		old.pacing.emptySessionChance = (float) 0.2; // written back through a float: still the old default
		old.pacing.sightingMinSpacing = 250; // not part of the step: kept whatever it is
		old.sections.add("director", JsonParser.parseString("{\"aloneAmbientPerHour\": 0.3}"));
		JsonObject file = JsonParser.parseString(old.toJson()).getAsJsonObject();
		file.remove("configVersion"); // v1.1 wrote none
		helper.assertTrue(ModConfig.parse(file.toString()).configVersion == 0, "a file without configVersion did not read as 0");

		ModConfig loaded = ModConfig.read(file.toString());
		Pacing p = loaded.pacing;
		Pacing d = new Pacing();
		helper.assertTrue(loaded.configVersion == ModConfig.CONFIG_VERSION && ModConfig.CONFIG_VERSION == 1, "version " + loaded.configVersion);
		helper.assertTrue(p.tracesStartMaxHours == 2.5, "the hand-changed value was replaced: " + p.tracesStartMaxHours);
		helper.assertTrue(p.tracesStartMinHours == d.tracesStartMinHours && p.proximityStartMinHours == d.proximityStartMinHours
				&& p.proximityStartMaxHours == d.proximityStartMaxHours && p.tempoShift == d.tempoShift && p.aloneMaxAmbient == d.aloneMaxAmbient
				&& p.tracesAmbientPerHour == d.tracesAmbientPerHour && p.proximityMinorsPerHourMin == d.proximityMinorsPerHourMin
				&& p.proximityMinorsPerHourMax == d.proximityMinorsPerHourMax && p.proximityMajorEveryHoursMin == d.proximityMajorEveryHoursMin
				&& p.proximityMajorEveryHoursMax == d.proximityMajorEveryHoursMax && p.firstAccidentMinHours == d.firstAccidentMinHours
				&& p.pityStartHours == d.pityStartHours && p.fogDriftMinSeconds == d.fogDriftMinSeconds && p.fogDriftMaxSeconds == d.fogDriftMaxSeconds,
				"an old default was not upgraded: " + loaded.toJson());
		helper.assertTrue(p.emptySessionChance == d.emptySessionChance, "a float-rounded old default was kept: " + p.emptySessionChance);
		helper.assertTrue(p.sightingMinSpacing == 250 && p.joinGraceMinutes == 5, "a value outside the step changed");
		helper.assertTrue(loaded.sections.getAsJsonObject("director").get("aloneAmbientPerHour").getAsDouble() == 0.3,
				"the root step touched a section (sections upgrade themselves)");

		JsonObject written = JsonParser.parseString(loaded.toJson()).getAsJsonObject();
		helper.assertTrue(written.get("configVersion").getAsInt() == 1, "not written back with configVersion 1");
		helper.assertTrue(written.getAsJsonObject("pacing").get("tracesStartMinHours").getAsDouble() == 0.4
				&& written.getAsJsonObject("pacing").get("tracesStartMaxHours").getAsDouble() == 2.5, "the written pacing");
		// Read again: nothing moves (the step runs once).
		ModConfig again = ModConfig.read(loaded.toJson());
		helper.assertTrue(again.toJson().equals(loaded.toJson()), "a second load changed the file");
		helper.succeed();
	}

	/** A file already at version 1 is never stepped again, even where it holds v1.1's numbers; a partial file fills in. */
	@GameTest
	public void currentAndPartialConfigFilesLoad(GameTestHelper helper) {
		ModConfig v1 = ModConfig.read("{\"configVersion\": 1, \"pacing\": {\"tracesStartMinHours\": 1.0, \"aloneMaxAmbient\": 1}}");
		helper.assertTrue(v1.configVersion == 1 && v1.pacing.tracesStartMinHours == 1.0 && v1.pacing.aloneMaxAmbient == 1,
				"a version 1 file was upgraded again");
		helper.assertTrue(v1.pacing.tracesStartMaxHours == 0.9 && v1.sections != null && !v1.pacing.attentionWeights.isEmpty(), "missing keys");

		ModConfig partial = ModConfig.read("{\"pacing\": {\"aloneMaxAmbient\": 1, \"fogDriftMaxSeconds\": 12}}");
		helper.assertTrue(partial.configVersion == 1 && partial.pacing.aloneMaxAmbient == 3 && partial.pacing.fogDriftMaxSeconds == 12
				&& partial.pacing.tracesStartMinHours == 0.4, "a partial v1.1 file: " + partial.toJson());

		ModConfig noPacing = ModConfig.read("{\"devFastMode\": false}");
		helper.assertTrue(noPacing.configVersion == 1 && noPacing.pacing.tracesStartMinHours == 0.4, "a file without pacing");
		ModConfig empty = ModConfig.read("");
		helper.assertTrue(empty.configVersion == 1 && empty.pacing != null && empty.sections != null, "an empty file");
		helper.succeed();
	}

	@GameTest
	public void upgradesKeepChangedValuesAndTakeNewDefaults(GameTestHelper helper) {
		helper.assertTrue(ModConfig.Upgrades.keepOrNew(0.3, 0.3, 3.0) == 3.0 && ModConfig.Upgrades.keepOrNew(0.31, 0.3, 3.0) == 0.31, "double");
		helper.assertTrue(ModConfig.Upgrades.keepOrNew((double) 0.3F, 0.3, 3.0) == 3.0, "a float-rounded double is not the old default");
		helper.assertTrue(ModConfig.Upgrades.keepOrNew(Double.NaN, 0.3, 3.0) != 3.0, "NaN counted as the old default");
		helper.assertTrue(ModConfig.Upgrades.keepOrNew(100_000.5, 100_000.0, 1.0) == 100_000.5, "a real edit of a large value counted as unchanged");
		helper.assertTrue(ModConfig.Upgrades.keepOrNew(0.15F, 0.15F, 0.3F) == 0.3F && ModConfig.Upgrades.keepOrNew(0.2F, 0.15F, 0.3F) == 0.2F, "float");
		helper.assertTrue(ModConfig.Upgrades.keepOrNew(48, 48, 24) == 24 && ModConfig.Upgrades.keepOrNew(40, 48, 24) == 40, "int");
		helper.assertTrue(ModConfig.Upgrades.keepOrNew(100L, 100L, 160L) == 160L && ModConfig.Upgrades.keepOrNew(90L, 100L, 160L) == 90L, "long");
		helper.assertTrue(ModConfig.Upgrades.keepOrNew(true, true, false) == false && ModConfig.Upgrades.keepOrNew(false, true, false) == false
				&& ModConfig.Upgrades.keepOrNew(false, false, true), "boolean");
		helper.assertTrue("new".equals(ModConfig.Upgrades.keepOrNew("old", "old", "new")) && "mine".equals(ModConfig.Upgrades.keepOrNew("mine", "old", "new"))
				&& "new".equals(ModConfig.Upgrades.keepOrNew((String) null, null, "new")), "String");

		float[] stages = {0.15F, 0.9F, 0.45F};
		float[] upgraded = ModConfig.Upgrades.keepOrNew(stages, new float[] {0.15F, 0.3F, 0.45F}, new float[] {0.25F, 0.4F, 0.5F, 0.6F});
		helper.assertTrue(Arrays.equals(upgraded, new float[] {0.25F, 0.9F, 0.5F, 0.6F}) && stages[0] == 0.15F,
				"float[] element by element: " + Arrays.toString(upgraded));
		float[] reshaped = {0.1F, 0.2F};
		helper.assertTrue(Arrays.equals(ModConfig.Upgrades.keepOrNew(reshaped, new float[] {0.15F, 0.3F, 0.45F}, new float[] {1F, 1F, 1F}), reshaped)
				&& Arrays.equals(ModConfig.Upgrades.keepOrNew((float[]) null, new float[] {0F}, new float[] {2F}), new float[] {2F}),
				"a reshaped or missing float[]");
		helper.assertTrue(Arrays.equals(ModConfig.Upgrades.keepOrNew(new double[] {1, 5}, new double[] {1, 2}, new double[] {3, 4}),
				new double[] {3, 5}), "double[]");

		// The director's stage tag weights: v1.1's ALONE and TRACES to D-070's, one weight changed and one removed by hand.
		Map<String, Map<String, Double>> oldDefault = new LinkedHashMap<>();
		oldDefault.put("ALONE", weights("SIGHTING", 0.15, "ACCIDENT", 0.0, "SCAR", 0.3, "TEXT", 0.0, "DIG", 0.3));
		oldDefault.put("TRACES", weights("SIGHTING", 0.35, "TEXT", 0.3));
		oldDefault.put("PROXIMITY", weights());
		Map<String, Map<String, Double>> newDefault = new LinkedHashMap<>();
		newDefault.put("ALONE", weights("SIGHTING", 0.3, "ACCIDENT", 0.0, "SCAR", 0.6, "TEXT", 0.0, "DIG", 0.6, "PERSONAL", 1.0));
		newDefault.put("TRACES", weights("SIGHTING", 0.5, "TEXT", 0.3));
		newDefault.put("PROXIMITY", weights());
		newDefault.put("TELLING", weights("SIGHTING", 0.03));
		Map<String, Map<String, Double>> current = new LinkedHashMap<>();
		current.put("ALONE", weights("SIGHTING", 0.15, "ACCIDENT", 0.0, "SCAR", 0.5, "TEXT", 0.0, "DIG", 0.3));
		current.put("TRACES", weights("SIGHTING", 0.35));
		current.put("PROXIMITY", weights("MOB", 2.0));
		Map<String, Map<String, Double>> result = ModConfig.Upgrades.tagWeights(current, oldDefault, newDefault);
		helper.assertTrue(result.get("ALONE").equals(weights("SIGHTING", 0.3, "ACCIDENT", 0.0, "SCAR", 0.5, "TEXT", 0.0, "DIG", 0.6, "PERSONAL", 1.0)),
				"ALONE " + result.get("ALONE"));
		helper.assertTrue(result.get("TRACES").equals(weights("SIGHTING", 0.5)), "TRACES (the removed TEXT must stay removed) " + result.get("TRACES"));
		helper.assertTrue(result.get("PROXIMITY").equals(weights("MOB", 2.0)) && result.get("TELLING").equals(weights("SIGHTING", 0.03)),
				"PROXIMITY kept, TELLING added " + result);
		helper.assertTrue(current.get("ALONE").get("SIGHTING") == 0.15 && !current.containsKey("TELLING"), "the current map was modified");
		helper.assertTrue(ModConfig.Upgrades.weights(null, weights("A", 1.0), weights("A", 2.0)).equals(weights()),
				"a missing weight map: an entry missing where the old default had one is the player's removal");
		helper.succeed();
	}

	/** A {@link ModConfig.Versioned} section is upgraded as it is read: old defaults move, changes stay, fresh defaults stay. */
	@GameTest
	public void versionedSectionsUpgradeWhenRead(GameTestHelper helper) {
		JsonObject sections = JsonParser.parseString("{\"t\": {\"radius\": 48.0, \"byStage\": [0.15, 0.9], \"mode\": \"C\", \"loud\": false,"
				+ " \"periodTicks\": 300}}").getAsJsonObject();
		VersionedSection old = ModConfig.readSection(sections, "t", VersionedSection.class, VersionedSection::new);
		helper.assertTrue(old.version == 1 && old.radius == 24 && Arrays.equals(old.byStage, new float[] {0.3F, 0.9F}) && "C".equals(old.mode)
				&& old.loud && old.periodTicks == 300, "a v0 section: " + old.radius + " " + Arrays.toString(old.byStage) + " " + old.mode);

		VersionedSection fresh = ModConfig.readSection(new JsonObject(), "t", VersionedSection.class, VersionedSection::new);
		VersionedSection d = new VersionedSection();
		helper.assertTrue(fresh.version == 1 && fresh.radius == d.radius && Arrays.equals(fresh.byStage, d.byStage) && fresh.mode.equals(d.mode)
				&& fresh.loud == d.loud && fresh.periodTicks == d.periodTicks, "fresh defaults changed by the upgrade");

		JsonObject current = JsonParser.parseString("{\"t\": {\"version\": 1, \"radius\": 48.0, \"mode\": \"A\"}}").getAsJsonObject();
		VersionedSection kept = ModConfig.readSection(current, "t", VersionedSection.class, VersionedSection::new);
		helper.assertTrue(kept.version == 1 && kept.radius == 48 && "A".equals(kept.mode), "a version 1 section was stepped again");

		JsonObject broken = JsonParser.parseString("{\"t\": [1, 2]}").getAsJsonObject();
		VersionedSection fallback = ModConfig.readSection(broken, "t", VersionedSection.class, VersionedSection::new);
		helper.assertTrue(fallback.version == 1 && fallback.radius == 24, "an invalid section did not fall back to its defaults");
		helper.succeed();
	}

	private static Map<String, Double> weights(Object... pairs) {
		Map<String, Double> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			map.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
		}
		return map;
	}
}
