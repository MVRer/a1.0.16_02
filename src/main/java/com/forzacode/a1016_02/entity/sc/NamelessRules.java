package com.forzacode.a1016_02.entity.sc;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.world.phys.Vec3;

/**
 * 02's pure rules, free of client classes so the game tests reach them: when the card fits on the server, and when the
 * armed client leaves a friend's name tag undrawn (D-082: a vanilla UI element wrong, true in the story: "no name over
 * him").
 */
public final class NamelessRules {
	/** A dusk fog or a fog spell counts as on at the target from this much of it ({@code ScreenshotRules.fogAmount}). */
	public static final double FOG_ON_AMOUNT = 0.1;
	/** The sky light at the target's eyes that counts as outdoors. */
	public static final int OUTDOORS_SKY_LIGHT = 10;
	/** A remote player who moved this far (blocks) since they last stood still is moving again. */
	public static final double STILL_BLOCKS = 0.1;

	private NamelessRules() {
	}

	/**
	 * Why the card does not fit now, or empty: another participant (not a spectator) in the target's level between
	 * {@code friendMinAtFire} and {@code maxDistance} blocks away, the target outdoors (sky light at the eyes
	 * {@link #OUTDOORS_SKY_LIGHT} or more) and a dusk fog or fog spell on at the target ({@link #FOG_ON_AMOUNT}).
	 *
	 * @param friendDistances every other participant's distance (3D) in the target's level, spectators left out
	 */
	public static Optional<String> fit(List<Double> friendDistances, int skyLight, double fogAmount, NamelessConfig config) {
		boolean friend = false;
		for (double d : friendDistances) {
			if (d >= config.friendMinAtFire && d <= config.maxDistance) {
				friend = true;
				break;
			}
		}
		if (!friend) {
			return Optional.of(String.format(Locale.ROOT, "nobody else %.0f to %.0f blocks off (%s)", config.friendMinAtFire, config.maxDistance,
					friendDistances.isEmpty() ? "nobody else here" : distances(friendDistances)));
		}
		if (skyLight < OUTDOORS_SKY_LIGHT) {
			return Optional.of("not outdoors (sky light " + skyLight + ")");
		}
		if (!(fogAmount >= FOG_ON_AMOUNT)) {
			return Optional.of(String.format(Locale.ROOT, "no fog on (%.2f)", fogAmount));
		}
		return Optional.empty();
	}

	private static String distances(List<Double> distances) {
		StringBuilder line = new StringBuilder();
		for (double d : distances) {
			if (!line.isEmpty()) {
				line.append(", ");
			}
			line.append(Math.round(d));
		}
		return line.toString();
	}

	/**
	 * The client's rule: while armed, a remote player's name tag is left undrawn when they are {@code minDistance} to
	 * {@code maxDistance} blocks from the camera, past {@code fogShare} of the way from the visible fog start to its end,
	 * and have stood still for {@code stillSeconds}. Any of them failing (they move, come inside, leave the fog) and the
	 * name is back at once. A fog the client cannot read (NaN) never hides anything.
	 */
	public static boolean hides(double distance, double fogStart, double fogEnd, long stillTicks, boolean armed, NamelessConfig config) {
		if (!armed || !Double.isFinite(distance) || !Double.isFinite(fogEnd)) {
			return false;
		}
		if (distance < config.minDistance || distance > config.maxDistance) {
			return false;
		}
		double start = Double.isFinite(fogStart) ? Math.max(0.0, Math.min(fogStart, fogEnd)) : 0.0;
		if (distance < start + Math.clamp(config.fogShare, 0.0, 1.0) * (fogEnd - start)) {
			return false;
		}
		return stillTicks >= config.stillTicks();
	}

	/** True if a remote player at {@code now} has moved {@link #STILL_BLOCKS} or more from where they stood ({@code anchor}). */
	public static boolean moved(Vec3 anchor, Vec3 now) {
		return anchor.distanceToSqr(now) >= STILL_BLOCKS * STILL_BLOCKS;
	}
}
