package com.forzacode.a1016_02.entity;

import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.FogWeatherRules;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Review R2 (v1.2 bug hunt): daytime fog sightings, each test failing on main at 1f78a6f. The live gate is
 * {@code SightingGates.check}: {@code SightingRules.daylight(variant, time, raining, SightingGates.fogWeather(server),
 * config)}, where {@code fogWeather} is {@code spell.activeAt(clock)}; these call the same two rules with the spell
 * and the clock.
 */
public class R2SightingReviewTests {
	private static final long DAY = FogWeatherRules.DAY;
	private static final FogWeatherRules.Settings D = FogWeatherRules.Settings.DEFAULT;

	/**
	 * A fog night starts easing in at 11500, but the clear-day window runs to 11800: for those 300 ticks the spell is
	 * "on" (activeAt) with almost no fog yet, and the daytime-fog variants may start in clear daylight on every fog night.
	 */
	@GameTest
	public void noDaytimeSightingBeforeAFogNightsFogComes(GameTestHelper helper) {
		EntityConfig config = new EntityConfig();
		long d = 3;
		FogSpell night = new FogSpell(FogWeatherRules.nightFrom(d), FogWeatherRules.nightUntil(d, false), 0.65F, false);
		for (long time : new long[] {11_520L, 11_650L, 11_780L}) {
			long clock = d * DAY + time;
			double fog = night.amount(clock, D);
			Optional<String> gate = SightingRules.daylight(Variant.COW, time, false, SightingRules.fogWeather(Optional.of(night), clock), config);
			helper.assertTrue(gate.isPresent(), String.format(Locale.ROOT,
					"a cow sighting may start at %d in clear daylight: the fog night has given %.5f fog so far (level %.2f)", time, fog, night.level()));
		}
		helper.succeed();
	}

	/**
	 * A morning carry-over fades from 4000 and is gone at 5500; up to its last tick the gate treats the morning as
	 * daytime fog, though at 5300 it gives under 1% of its level. A sighting started then stands unseen for up to
	 * {@code unseenLifetimeSeconds} (150 s, 3000 ticks): in full clear daylight long after the fog has gone.
	 */
	@GameTest
	public void noDaytimeSightingAsAMorningCarryOverLifts(GameTestHelper helper) {
		EntityConfig config = new EntityConfig();
		long d = 5;
		FogSpell carry = new FogSpell(FogWeatherRules.nightFrom(d), FogWeatherRules.nightUntil(d, true), 0.55F, false);
		for (long time : new long[] {5_300L, 5_450L, 5_499L}) {
			long clock = (d + 1) * DAY + time;
			double fog = carry.amount(clock, D);
			Optional<String> gate = SightingRules.daylight(Variant.CLOSE, time, false, SightingRules.fogWeather(Optional.of(carry), clock), config);
			helper.assertTrue(gate.isPresent(), String.format(Locale.ROOT,
					"the close one may start at %d in clear daylight: the carry-over gives %.5f fog (level %.2f), and he may stand %.0f s after",
					time, fog, carry.level(), config.unseenLifetimeSeconds));
		}
		helper.succeed();
	}
}
