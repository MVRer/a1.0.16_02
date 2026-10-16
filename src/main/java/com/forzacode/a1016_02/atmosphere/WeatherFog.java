package com.forzacode.a1016_02.atmosphere;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.FogPlace;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.FogWeatherRules;
import com.forzacode.a1016_02.core.SelfCreated;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

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
 *
 * <p>Fog places (D-085, the layer's 10, docs/SELF_CREATED.md): the world's places as the server last sent them (the
 * {@code FogPlaces} payload, {@code Sync} on join, none after a disconnect or while the layer is switched off on this
 * client, {@link SelfCreated#enabled}). Their amount at the camera ({@link #placeAmount}, core's
 * {@link FogLimits#placeAmount}) is weather like the spell: weighted by the same smoothed sky weight and combined with
 * the spell's ({@link FogLimits#weatherAmount}) before everything above, so the places thicken the same fog, haze and
 * tint, and with no places every number is bit for bit the spell's alone. A place is also seen from outside it: each
 * place along the camera's view ({@link #seen}, core's {@link FogLimits#placeViews}) brings the far fog in as one would
 * see it from where the view enters the place ({@link #shape(Curves.Fog, double, double, double, double, List, Curves.Fog, AtmosphereConfig)}),
 * so from a distance a fog stands over the spot, and the frame's color follows it ({@link #tintAmount}).
 * Continuous in the camera's position, its yaw and the clock (each place is).
 */
public final class WeatherFog {
	/** Vanilla's rain fog multiplier moves this share of the way to its target per tick ({@code AtmosphericFogEnvironment}); the sky weight too. */
	public static final double SKY_EASE_PER_TICK = 0.2;
	/** The sky weight lands on its target once this close (otherwise it only ever nears it), so a tunnel gets exactly no spell fog. */
	public static final double SKY_SNAP = 1.0E-3;

	private Optional<FogSpell> spell = Optional.empty();
	private List<FogPlace> places = List.of();
	/** The smoothed sky weight; NaN until the first frame (which takes the target at once). */
	private double sky = Double.NaN;
	private double skyAt;

	/** The world's spell changed (a roll, a hand-over, a command, the end of one). */
	public void receive(ClientEffects.FogSpell payload) {
		spell = payload.spell();
	}

	/** The world's fog places changed (every place there is now). */
	public void receive(ClientEffects.FogPlaces payload) {
		places = List.copyOf(payload.places());
	}

	/** Joining: the spell and the fog places are part of every persistent effect. */
	public void receive(ClientEffects.Sync payload) {
		spell = payload.fogSpell();
		places = List.copyOf(payload.fogPlaces());
	}

	/** Disconnect: no fog weather until the next join says so. */
	public void reset() {
		spell = Optional.empty();
		places = List.of();
		resetSky();
	}

	public List<FogPlace> places() {
		return places;
	}

	/**
	 * The fog places' amount at {@code at} in {@code dimension} at this clock time, under open sky ({@link FogLimits#placeAmount}):
	 * 0 where time is fixed, with none, or while the layer is switched off here.
	 */
	public double placeAmount(ResourceKey<Level> dimension, Vec3 at, long clockTime, boolean fixedTime) {
		if (fixedTime || places.isEmpty() || !SelfCreated.enabled()) {
			return 0.0;
		}
		return FogLimits.placeAmount(places, dimension, at, clockTime);
	}

	/**
	 * One frame's weather fog at the camera ({@link #frame(long, boolean, int, double)} with the fog places): steps the
	 * sky weight and gives the spell's and the places' amounts, each times it, together
	 * ({@link FogLimits#weatherAmount}); exactly the spell's alone with no place here.
	 */
	public double frame(long clockTime, boolean fixedTime, int skyLight, double now, ResourceKey<Level> dimension, Vec3 camera) {
		double sky = sky(skyLight, now);
		double spell = amount(clockTime, fixedTime);
		double place = placeAmount(dimension, camera, clockTime, fixedTime);
		if (!(place > 0.0)) {
			return spell > 0.0 ? spell * sky : 0.0;
		}
		return FogLimits.weatherAmount(spell, place, sky);
	}

	/**
	 * A fog place seen along the frame's view, as the frame draws it: from {@code near} blocks out, the weather fog with
	 * the place's amount there ({@code weather}: the spell's and the place's amounts, each times the frame's sky weight,
	 * together, {@link FogLimits#weatherAmount}).
	 */
	public record Seen(double near, double weather) {
	}

	/**
	 * The fog places seen from the camera along its yaw this frame ({@link FogLimits#placeViews}, so the server's
	 * {@code FogLimits.of(player)} sees the same), each with its weather amount at the sky weight the last
	 * {@link #frame} stepped to: none where time is fixed, with no places, while the layer is switched off here, or
	 * with no sky at the camera (a tunnel or a closed house sees none, as it has none of the weather). Call after
	 * {@link #frame} in the same frame.
	 */
	public List<Seen> seen(ResourceKey<Level> dimension, Vec3 camera, double yawDegrees, long clockTime, boolean fixedTime) {
		if (fixedTime || places.isEmpty() || !SelfCreated.enabled()) {
			return List.of();
		}
		double weight = sky();
		if (!(weight > 0.0)) {
			return List.of();
		}
		List<FogLimits.PlaceView> views = FogLimits.placeViews(places, dimension, camera, yawDegrees, clockTime);
		if (views.isEmpty()) {
			return List.of();
		}
		double spell = amount(clockTime, fixedTime);
		List<Seen> seen = new ArrayList<>(views.size());
		for (FogLimits.PlaceView view : views) {
			seen.add(new Seen(view.near(), FogLimits.weatherAmount(spell, view.amount(), weight)));
		}
		return seen;
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

	/**
	 * {@link #shape(Curves.Fog, double, double, double, double, AtmosphereConfig)} for the camera's own weather, then
	 * each fog place seen along the view ({@link #seen}): the same fog shaped with that place's weather amount in place
	 * of the camera's, as one would see it standing where the view enters the place, {@code near} blocks further out
	 * ({@link Curves.Fog#seen}: the render-distance fog and the sky's end only, wherever nearer). So from outside a place
	 * the fog stands over it and hides what is behind it, nothing nearer than it gets more fog, and inside one the view
	 * toward its centre is the thicker. The visible fog end is exactly core's {@code FogLimits.seen}. With no seen place
	 * this is the camera's shaping alone, bit for bit. {@code scratch} is overwritten.
	 */
	public static boolean shape(Curves.Fog fog, double dusk, double duskHaze, double weather, double surge, List<Seen> seen, Curves.Fog scratch,
			AtmosphereConfig cfg) {
		if (seen.isEmpty()) {
			return shape(fog, dusk, duskHaze, weather, surge, cfg);
		}
		double es = fog.environmentalStart;
		double ee = fog.environmentalEnd;
		double rs = fog.renderDistanceStart;
		double re = fog.renderDistanceEnd;
		double sky = fog.skyEnd;
		double cloud = fog.cloudEnd;
		boolean changed = shape(fog, dusk, duskHaze, weather, surge, cfg);
		for (Seen s : seen) {
			scratch.set(es, ee, rs, re, sky, cloud);
			if (shape(scratch, dusk, duskHaze, s.weather(), surge, cfg)) {
				changed |= fog.seen(scratch, Math.max(0.0, s.near()));
			}
		}
		return changed;
	}

	/**
	 * The weather amount the frame's fog color follows ({@link #color}): the camera's own ({@code weather}) or a seen
	 * place's, weighted by how much of the camera's fog lies before it ({@code 1 - near / cameraEnd}, 0 once it begins
	 * past the camera's fog end), whichever is more. The color is one for the whole frame, so a place seen from outside
	 * tints it only as far as its fog shows: at night the fog over it reads as a pale wall in the dark, as a fog night
	 * does. Exactly {@code weather} with none seen; continuous in each input.
	 */
	public static double tintAmount(double weather, List<Seen> seen, double cameraEnd) {
		double amount = weather;
		for (Seen s : seen) {
			double share = cameraEnd > 0.0 ? Curves.clamp01(1.0 - s.near() / cameraEnd) : 0.0;
			amount = Math.max(amount, s.weather() * share);
		}
		return amount;
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
