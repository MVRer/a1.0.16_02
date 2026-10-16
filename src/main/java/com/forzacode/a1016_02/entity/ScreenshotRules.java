package com.forzacode.a1016_02.entity;

import java.util.Optional;

import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.Stage;

/**
 * The screenshot's pure rules (D-078), free of client classes so the game tests reach them: the card's gate on the
 * server, and the conditions the client checks when the player takes a screenshot while armed.
 */
public final class ScreenshotRules {
	private ScreenshotRules() {
	}

	/**
	 * Why {@code the_screenshot} cannot arm this player now, or empty if it can: it only fits in the overworld, from
	 * Traces, once per world (never again once it happened for anyone), never for a player already armed, nor one who
	 * is dead or spectating, nor during Ending D's last minute (the ending owns him then).
	 */
	public static Optional<String> gate(boolean overworld, Stage stage, boolean happened, boolean armed, boolean alive, boolean spectator,
			boolean lastMinute) {
		if (!overworld) {
			return Optional.of("not in the overworld");
		}
		if (!stage.atLeast(Stage.TRACES)) {
			return Optional.of("before " + Stage.TRACES);
		}
		if (happened) {
			return Optional.of("it already happened in this world");
		}
		if (armed) {
			return Optional.of("already armed");
		}
		if (!alive || spectator) {
			return Optional.of("player busy");
		}
		if (lastMinute) {
			return Optional.of("Ending D's last minute");
		}
		return Optional.empty();
	}

	/**
	 * Why the screenshot the client is taking now cannot show him, or empty if it can (the arm then fires): the
	 * overworld, no screen open, the camera in the air (not under water, in lava or powder snow), first person, the sky
	 * light at the eyes at least {@code minSkyLight}, and fog: the fog end the client draws inside {@code maxFogEnd}
	 * blocks, or a dusk fog or fog spell on ({@code fogOn}, from the server). Any other answer and the arm waits for
	 * the next screenshot.
	 *
	 * @param fogEnd the fog end the client draws now in blocks, NaN if unknown
	 */
	public static Optional<String> clientConditions(boolean overworld, boolean screenOpen, boolean cameraInFluid, boolean firstPerson, int skyLight,
			double fogEnd, boolean fogOn, int minSkyLight, double maxFogEnd) {
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
		if (!(fogEnd < maxFogEnd) && !fogOn) {
			return Optional.of("no fog");
		}
		return Optional.empty();
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
