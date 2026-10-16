package com.forzacode.a1016_02.atmosphere;

import java.util.Optional;

import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.FogWeatherRules;

/**
 * Fog weather as the client draws it (D-071): the world's spell the server sent, and the pure math that puts it on
 * the frame's fog. Pure (no client classes) so the game tests check exactly what {@code ClientAtmosphere} does.
 *
 * <ul>
 * <li>Amount: the spell's ({@link FogSpell#amount}, its own ease, on the level's clock), weighted by the sky at the
 * camera ({@link #sky}: core's {@link FogLimits#skyFactor}, smoothed over time the way vanilla smooths its rain fog),
 * so a fog spell is outside weather and a tunnel or a closed house has none of it. The weighted amount and the dusk
 * fog's combine exactly as core's {@code FogLimits.of} combines them ({@link #combined}), so the client's fog ends where
 * the server thinks it does (the server weights by the sky at the player's eyes, unsmoothed; the client's fog report
 * covers the difference). None in fixed-time dimensions.</li>
 * <li>Haze: vanilla's faint far haze closes in to the fog end with the spell too ({@link #haze}), over its ease, so a
 * fog day is close and thick, not only a wall at the render limit.</li>
 * <li>Color ({@link #color}): at night lifted from vanilla's near black toward {@code fogNightColor} by
 * {@code fogNightColorLift} times the amount, so a fog night shows as a pale wall in the dark; by day desaturated
 * toward {@code fogDayColor} by the amount; between them it follows vanilla's own darkening ({@link Curves#daylight}).
 * It goes on vanilla's base fog color, before {@code FogRenderer.computeFogColor} darkens it (the void below
 * {@code minY + 32}, the Blindness and Darkness blends, a boss's darkening, night vision), so vanilla's darkening holds
 * over the tint with its own values: the bottom of the world stays black and the effects never snap. No spell, no
 * change: a normal night keeps vanilla's fog color and the faint stage dusk fog.</li>
 * </ul>
 *
 * Continuous in every input: the spell's amount is continuous in time (its ease, and every new spell the server
 * stores is joined to the one before), the sky weight moves at most a fifth of the way per tick, and each step above
 * is continuous in the amount, the dusk fog and the time of day.
 */
public final class WeatherFog {
	/** Vanilla's rain fog multiplier moves this share of the way to its target per tick ({@code AtmosphericFogEnvironment}); the sky weight too. */
	public static final double SKY_EASE_PER_TICK = 0.2;
	/** The sky weight lands on its target once this close (otherwise it only ever nears it), so a tunnel gets exactly no spell fog. */
	public static final double SKY_SNAP = 1.0E-3;

	private Optional<FogSpell> spell = Optional.empty();
	/** The smoothed sky weight; NaN until the first frame (which takes the target at once). */
	private double sky = Double.NaN;
	private double skyAt;

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
		resetSky();
	}

	/** A new level (joining, a dimension change): the next frame takes the sky weight there at once. */
	public void resetSky() {
		sky = Double.NaN;
	}

	public Optional<FogSpell> spell() {
		return spell;
	}

	/** The spell's fog at this clock time (the level's {@code getDefaultClockTime()}), its own ease, under open sky; 0 where time is fixed. */
	public double amount(long clockTime, boolean fixedTime) {
		return fixedTime ? 0.0 : FogLimits.spellAmount(spell, clockTime);
	}

	/** {@link #amount(long, boolean)}: the settings are not read (the spell's own ease counts; kept so older calls compile). */
	public double amount(long clockTime, boolean fixedTime, FogWeatherRules.Settings settings) {
		return amount(clockTime, fixedTime);
	}

	/**
	 * One frame's spell fog at the camera, as {@code ClientAtmosphere} draws it (the tint and the fog shape): steps the
	 * sky weight ({@link #sky(int, double)}) and gives the spell's amount times it; 0 without a spell, where time is
	 * fixed, or at a sky light of {@link FogLimits#SKY_NONE} or less once the weight has settled.
	 */
	public double frame(long clockTime, boolean fixedTime, int skyLight, double now) {
		double sky = sky(skyLight, now);
		double spell = amount(clockTime, fixedTime);
		return spell > 0.0 ? spell * sky : 0.0;
	}

	/**
	 * One frame's sky weight at the camera, 0 to 1: steps the smoothed weight toward {@link FogLimits#skyFactor} of the
	 * camera's sky light by the ticks since the last frame ({@code now}: client ticks plus the partial tick; a frame at
	 * the same moment changes nothing) and returns it. The first frame (after joining or a dimension change) takes the
	 * target at once.
	 */
	public double sky(int skyLight, double now) {
		double target = FogLimits.skyFactor(skyLight);
		sky = Double.isNaN(sky) ? target : skyStep(sky, target, now - skyAt);
		skyAt = now;
		return sky;
	}

	/** The smoothed sky weight as last stepped (1 before the first frame: open sky). */
	public double sky() {
		return Double.isNaN(sky) ? 1.0 : sky;
	}

	/**
	 * The sky weight smoothed as vanilla smooths its rain fog multiplier ({@code m += (target - m) * deltaTicks * 0.2}),
	 * never past the target on a long frame, landing on it within {@link #SKY_SNAP}. Nothing moves without time passing.
	 */
	public static double skyStep(double current, double target, double elapsedTicks) {
		if (!(elapsedTicks > 0.0)) {
			return current;
		}
		double next = current + (target - current) * Math.min(1.0, elapsedTicks * SKY_EASE_PER_TICK);
		return Math.abs(target - next) < SKY_SNAP ? target : next;
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
	 * Shapes the world fog for the dusk fog ({@code dusk} amount, {@code duskHaze}), the spell's amount (weighted by the
	 * sky) and a surge (its amount now), as {@code ClientAtmosphere.applyFog} does. Returns false and leaves {@code fog}
	 * as it is when none of them gives anything (vanilla's fog).
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
	 * this time of day. Nothing changes without a spell (amount 0). The client gives it vanilla's base fog color,
	 * before vanilla's darkness, so the darkness still applies over it.
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
