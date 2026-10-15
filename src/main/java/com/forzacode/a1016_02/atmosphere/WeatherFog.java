package com.forzacode.a1016_02.atmosphere;

import java.util.Optional;

import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.FogWeatherRules;

/**
 * Fog weather as the client draws it (D-071): the world's spell the server sent, and the pure math that puts it on
 * the frame's fog. Pure (no client classes) so the game tests check exactly what {@code ClientAtmosphere.applyFog}
 * does.
 *
 * <ul>
 * <li>Amount: the spell's ({@link FogSpell#amount}, on the level's clock) and the dusk fog's combine exactly as core's
 * {@code FogLimits.of} combines them ({@link #combined}), so the client's fog ends where the server thinks it does.
 * None in fixed-time dimensions.</li>
 * <li>Haze: vanilla's faint far haze closes in to the fog end with the spell too ({@link #haze}), over its ease, so a
 * fog day is close and thick, not only a wall at the render limit.</li>
 * <li>Color ({@link #color}): at night lifted from vanilla's near black toward {@code fogNightColor} by
 * {@code fogNightColorLift} times the amount, so a fog night shows as a pale wall in the dark; by day desaturated
 * toward {@code fogDayColor} by the amount; between them it follows vanilla's own darkening ({@link Curves#daylight}).
 * No spell, no change: a normal night keeps vanilla's fog color and the faint stage dusk fog.</li>
 * </ul>
 *
 * Continuous in every input: the spell's amount is continuous in time (its ease, and every new spell the server
 * stores is joined to the one before), and each step above is continuous in the amount, the dusk fog and the time of
 * day.
 */
public final class WeatherFog {
	private Optional<FogSpell> spell = Optional.empty();

	/** The world's spell changed (a roll, a hand-over, a command, the end of one). */
	public void receive(ClientEffects.FogSpell payload) {
		spell = payload.spell();
	}

	/** Joining: the spell is part of every persistent effect. */
	public void receive(ClientEffects.Sync payload) {
		spell = payload.fogSpell();
	}

	/** Disconnect: no fog weather until the next join says so. */
	public void reset() {
		spell = Optional.empty();
	}

	public Optional<FogSpell> spell() {
		return spell;
	}

	/** The spell's fog at this clock time (the level's {@code getDefaultClockTime()}); 0 where time is fixed. */
	public double amount(long clockTime, boolean fixedTime, FogWeatherRules.Settings settings) {
		return fixedTime ? 0.0 : FogLimits.spellAmount(spell, clockTime, settings);
	}

	/** The fog amount drawn: the dusk fog's and the spell's, exactly as {@link FogLimits#combine} (either at 0 gives the other bit for bit). */
	public static double combined(double dusk, double spell) {
		return FogLimits.combine(dusk, spell);
	}

	/**
	 * The close-in haze drawn ({@link Curves#applyDusk}): the dusk fog's and the spell's combined, the spell's being its
	 * amount over {@code hazeFullAmount} (whole from there). Without a spell it is the dusk fog's, bit for bit.
	 */
	public static double haze(double duskHaze, double spell, double hazeFullAmount) {
		double s = Curves.clamp01(spell);
		double spellHaze = hazeFullAmount > 0.0 ? Curves.clamp01(s / hazeFullAmount) : s > 0.0 ? 1.0 : 0.0;
		return FogLimits.combine(duskHaze, spellHaze);
	}

	/**
	 * Shapes the world fog for the dusk fog ({@code dusk} amount, {@code duskHaze}), the spell's amount and a surge
	 * (its amount now), as {@code ClientAtmosphere.applyFog} does. Returns false and leaves {@code fog} as it is when
	 * none of them gives anything (vanilla's fog).
	 */
	public static boolean shape(Curves.Fog fog, double dusk, double duskHaze, double spell, double surge, AtmosphereConfig cfg) {
		if (dusk <= 0.0 && duskHaze <= 0.0 && !(spell > 0.0) && surge < 1.0E-3) {
			return false;
		}
		Curves.applyDusk(fog, combined(dusk, spell), haze(duskHaze, spell, cfg.fogSpellHazeFullAmount), cfg.duskMinFogBlocks, cfg.heavyFogStartFraction);
		Curves.applySurge(fog, surge, cfg.surgeMinFogBlocks, cfg.heavyFogStartFraction);
		return true;
	}

	/** {@link #color(float[], double, long, int, double, int)} with the config's colors and lift. */
	public static void color(float[] rgb, double spell, long timeOfDay, AtmosphereConfig cfg) {
		color(rgb, spell, timeOfDay, cfg.fogNightRgb(), cfg.fogNightColorLift, cfg.fogDayRgb());
	}

	/**
	 * The fog color (red, green, blue, 0 to 1, changed in place) under a spell of this amount: toward
	 * {@code lerp(daylight, night, day)} by {@code amount * lerp(daylight, lift, 1)}, with {@link Curves#daylight} at
	 * this time of day. Nothing changes without a spell (amount 0).
	 */
	public static void color(float[] rgb, double spell, long timeOfDay, int nightRgb, double lift, int dayRgb) {
		double amount = Curves.clamp01(spell);
		if (!(amount > 0.0)) {
			return;
		}
		double day = Curves.daylight(timeOfDay);
		double weight = amount * Curves.lerp(day, Curves.clamp01(lift), 1.0);
		for (int c = 0; c < 3; c++) {
			int shift = 16 - 8 * c;
			double night = ((nightRgb >> shift) & 0xFF) / 255.0;
			double grey = ((dayRgb >> shift) & 0xFF) / 255.0;
			rgb[c] = (float) Curves.lerp(weight, rgb[c], Curves.lerp(day, night, grey));
		}
	}

	/** How far the color moved toward the weather's: the lift's weight, 0 with no spell (for tests and the overlay). */
	public static double colorWeight(double spell, long timeOfDay, double lift) {
		double amount = Curves.clamp01(spell);
		return amount > 0.0 ? amount * Curves.lerp(Curves.daylight(timeOfDay), Curves.clamp01(lift), 1.0) : 0.0;
	}
}
