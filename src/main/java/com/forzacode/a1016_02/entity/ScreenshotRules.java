package com.forzacode.a1016_02.entity;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.Stage;

/**
 * The screenshot's pure rules (D-078), free of client classes so the game tests reach them: the card's gate on the
 * server, when the story is over for it, and the conditions the client checks when the player takes a screenshot while
 * armed.
 */
public final class ScreenshotRules {
	/**
	 * The {@code HerobrineState} flags after which the story holds no screenshot, for good: the world is quiet for good
	 * ({@code director:silence_forever}, set by Endings A, C and D; {@code ending:path=C}, as atmosphere's
	 * {@code Gates.quietForGood} reads it) or an ending is done ({@code ending:ended}, {@code ending:d_complete}).
	 */
	public static final List<String> STORY_OVER_FLAGS = List.of("director:silence_forever", "ending:path=C", "ending:ended", "ending:d_complete");
	/**
	 * The fog the client draws is "pulled in" (a fog surge, Blindness, Darkness, a boss's fog: not weather) when its
	 * visible end is nearer than this share of the weather's fog end the server computes for the player
	 * ({@code FogLimits.of(player)}: the render distance, the dusk fog and a fog spell, never a surge or an effect). The
	 * slack covers the client's easing between dusk levels, its smoothed sky weight and the server's 2-block steps.
	 */
	public static final double PULLED_IN_SHARE = 0.85;

	private ScreenshotRules() {
	}

	/** True once the story is over for the screenshot ({@link #STORY_OVER_FLAGS}): no arm is kept or made. */
	public static boolean storyOver(Predicate<String> hasFlag) {
		for (String flag : STORY_OVER_FLAGS) {
			if (hasFlag.test(flag)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Why {@code the_screenshot} cannot arm this player now, or empty if it can: it only fits in the overworld, from
	 * Traces, while the story goes on (never once it is over or quiet for good, {@link #storyOver}), once per world
	 * (never again once it happened for anyone), never during Ending D's last minute (the ending owns him then), never
	 * while anyone is armed (one arm in the world at a time), nor for a player who is dead or spectating.
	 */
	public static Optional<String> gate(boolean overworld, Stage stage, boolean happened, boolean someoneArmed, boolean alive, boolean spectator,
			boolean lastMinute, boolean storyOver) {
		if (!overworld) {
			return Optional.of("not in the overworld");
		}
		if (!stage.atLeast(Stage.TRACES)) {
			return Optional.of("before " + Stage.TRACES);
		}
		if (storyOver) {
			return Optional.of("the story is over");
		}
		if (happened) {
			return Optional.of("it already happened in this world");
		}
		if (lastMinute) {
			return Optional.of("Ending D's last minute");
		}
		if (someoneArmed) {
			return Optional.of("someone is already armed");
		}
		if (!alive || spectator) {
			return Optional.of("player busy");
		}
		return Optional.empty();
	}

	/**
	 * Why the screenshot the client is taking now cannot show him, or empty if it can (a spot is looked for next): the
	 * overworld, no screen open, the camera in the air (not under water, in lava or powder snow), first person, the sky
	 * light at the eyes at least {@code minSkyLight}, no shaderpack drawing the frame (Iris: its temporal effects could
	 * carry the extra frame onto the screen), neither Blindness nor Darkness, and weather fog: a dusk fog or fog spell on
	 * at the player ({@code fogOn}, the server's flag), with nothing else pulling the drawn fog in ({@link #pulledIn}).
	 * Render distance alone is never fog. Any other answer and the arm waits for the next screenshot.
	 *
	 * @param drawnFogEnd   the visible fog end the client draws now in blocks, NaN if unknown
	 * @param weatherFogEnd the weather's fog end the server sent for this player in blocks, NaN if unknown
	 */
	public static Optional<String> clientConditions(boolean overworld, boolean screenOpen, boolean cameraInFluid, boolean firstPerson, int skyLight,
			int minSkyLight, boolean shaderPack, boolean blindOrDark, boolean fogOn, double drawnFogEnd, double weatherFogEnd) {
		if (!overworld) {
			return Optional.of("not in the overworld");
		}
		if (screenOpen) {
			return Optional.of("a screen is open");
		}
		if (cameraInFluid) {
			return Optional.of("under water");
		}
		if (!firstPerson) {
			return Optional.of("not in first person");
		}
		if (skyLight < minSkyLight) {
			return Optional.of("no open sky");
		}
		if (shaderPack) {
			return Optional.of("a shaderpack is in use");
		}
		if (blindOrDark) {
			return Optional.of("Blindness or Darkness");
		}
		if (!fogOn) {
			return Optional.of("no fog weather");
		}
		if (pulledIn(drawnFogEnd, weatherFogEnd)) {
			return Optional.of("the fog is pulled in by more than the weather");
		}
		return Optional.empty();
	}

	/**
	 * True if the drawn fog is not the weather's alone: its visible end nearer than {@link #PULLED_IN_SHARE} of the
	 * weather's fog end (a fog surge or an effect pulls it in), or either is unknown.
	 */
	public static boolean pulledIn(double drawnFogEnd, double weatherFogEnd) {
		if (!(drawnFogEnd > 0.0) || !Double.isFinite(drawnFogEnd) || !(weatherFogEnd > 0.0) || !Double.isFinite(weatherFogEnd)) {
			return true;
		}
		return drawnFogEnd < weatherFogEnd * PULLED_IN_SHARE;
	}

	/**
	 * Server: the dusk fog and the fog spell at the player together, as {@code FogLimits} combines them (the dusk fog's
	 * level by its weight at this time of day, the spell's amount weighted by the sky light at the eyes), 0 to 1.
	 *
	 * @param skyLight the raw sky light at the eyes, 0 to 15
	 */
	public static double fogAmount(float duskLevel, long timeOfDay, FogLimits.Shape shape, double spellAmount, int skyLight) {
		double dusk = Math.clamp(duskLevel, 0.0F, 1.0F) * FogLimits.duskWeight(timeOfDay, shape);
		double spell = spellAmount > 0.0 ? spellAmount * FogLimits.skyFactor(skyLight) : 0.0;
		return FogLimits.combine(dusk, spell);
	}

	/** Server: the fog is on for the screenshot when {@link #fogAmount} reaches {@code onAmount} (and is more than nothing). */
	public static boolean fogOn(double amount, double onAmount) {
		return amount > 0.0 && amount >= onAmount;
	}
}
