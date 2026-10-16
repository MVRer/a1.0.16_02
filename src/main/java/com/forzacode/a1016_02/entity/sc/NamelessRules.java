package com.forzacode.a1016_02.entity.sc;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.entity.ScreenshotSpot;

import org.jspecify.annotations.Nullable;

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
	/** The card fits only while the target's drawn fog leaves a band at least this deep (blocks) where a name can go. */
	public static final double MIN_BAND_BLOCKS = 4.0;
	/** The client reports where its band starts at least this often (ticks)... */
	public static final int REACH_EVERY_TICKS = 20;
	/** ...and at once when that moved by more than this (blocks). */
	public static final double REACH_CHANGE_BLOCKS = 2.0;
	/** A report older than this (server ticks) is no longer the client's fog. */
	public static final long REACH_MAX_AGE_TICKS = 100L;

	private NamelessRules() {
	}

	/**
	 * Why the card does not fit now, or empty: another participant (not a spectator) in the target's level between
	 * {@code friendMinAtFire} and {@code maxDistance} blocks away, nobody else nearer than {@code friendMinAtFire} (they
	 * would share the moment and see the name the target does not), the target outdoors (sky light at the eyes
	 * {@link #OUTDOORS_SKY_LIGHT} or more) and a dusk fog or fog spell on at the target ({@link #FOG_ON_AMOUNT}). The
	 * target's own drawn fog has its rule too ({@link #bandRefusal}).
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
		for (double d : friendDistances) {
			if (d < config.friendMinAtFire) {
				return Optional.of(String.format(Locale.ROOT, "someone else %.0f blocks off (nearer than %.0f)", d, config.friendMinAtFire));
			}
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
	 * What the client reports: the nearest distance from its camera at which the fog it draws covers {@code fogShare} of a
	 * player ({@link ScreenshotSpot.Fog#coversFrom}: each fog on its own, as the shader has it), NaN if it draws none.
	 */
	public static double reach(ScreenshotSpot.Fog fog, NamelessConfig config) {
		return fog.coversFrom(config.fogShare);
	}

	/**
	 * Why the target's own drawn fog leaves their client no band to hide a name in, or empty: a still friend's name goes
	 * only from {@code minDistance} or from where that fog covers {@code fogShare} of them ({@code reach}, the client's
	 * report), whichever is farther, to {@code maxDistance} (vanilla draws no name past 64), and that band must be at least
	 * {@link #MIN_BAND_BLOCKS} deep. A clear dusk-fog night at 12 or 16 chunks draws its fog end past 128 blocks, so the
	 * band is empty and an arm would be spent on a moment that cannot come (D-091). NaN (no fresh report): no band.
	 */
	public static Optional<String> bandRefusal(double reach, NamelessConfig config) {
		if (!Double.isFinite(reach)) {
			return Optional.of("no fog report from their client");
		}
		double near = Math.max(config.minDistance, reach);
		if (near > config.maxDistance - MIN_BAND_BLOCKS) {
			return Optional.of(String.format(Locale.ROOT, "their fog covers a player only from %.0f blocks (a band needs %.0f or nearer)", reach,
					config.maxDistance - MIN_BAND_BLOCKS));
		}
		return Optional.empty();
	}

	/** Client: send where the band starts now? The first time, then every {@link #REACH_EVERY_TICKS}, and at once on a change. */
	public static boolean sendReach(float lastSent, float now, int ticksSinceSent) {
		if (!Float.isFinite(now) || now <= 0.0F) {
			return false;
		}
		return Float.isNaN(lastSent) || ticksSinceSent >= REACH_EVERY_TICKS || Math.abs(now - lastSent) > REACH_CHANGE_BLOCKS;
	}

	/** Server: the reported reach while it is fresh ({@link #REACH_MAX_AGE_TICKS}), else NaN. */
	public static double freshReach(double reported, long ageTicks) {
		if (!Double.isFinite(reported) || reported <= 0.0 || ageTicks < 0L || ageTicks > REACH_MAX_AGE_TICKS) {
			return Double.NaN;
		}
		return reported;
	}

	/**
	 * The client's rule: while armed, a remote player's name tag is left undrawn when they are {@code minDistance} to
	 * {@code maxDistance} blocks from the camera ({@code offset}: from the camera to their middle), the drawn fog covers at
	 * least {@code fogShare} of them ({@link ScreenshotSpot.Fog#value}: each fog on its own, as FIX-PLAIN reads it) and
	 * they have stood still for {@code stillSeconds}. Any of them failing (they move, come inside, leave the fog) and the
	 * name is back at once. A fog the client cannot read never hides anything.
	 */
	public static boolean hides(@Nullable Vec3 offset, ScreenshotSpot.@Nullable Fog fog, long stillTicks, boolean armed, NamelessConfig config) {
		if (!armed || offset == null || fog == null || !Double.isFinite(fog.visibleEnd())) {
			return false;
		}
		double distance = offset.length();
		if (!(distance >= config.minDistance && distance <= config.maxDistance)) {
			return false;
		}
		if (!(fog.value(offset) >= Math.clamp(config.fogShare, 0.0, 1.0))) {
			return false;
		}
		return stillTicks >= config.stillTicks();
	}

	/**
	 * True if someone else stands near the viewer: another remote player (besides the one whose name is in question)
	 * within {@code friendMinAtFire} of the camera would see that name and break the lie, so nothing is hidden then.
	 *
	 * @param nearestOther that nearest other player's distance from the camera, infinity if there is none
	 */
	public static boolean witnessed(double nearestOther, NamelessConfig config) {
		return nearestOther < config.friendMinAtFire;
	}

	/** True if a remote player at {@code now} has moved {@link #STILL_BLOCKS} or more from where they stood ({@code anchor}). */
	public static boolean moved(Vec3 anchor, Vec3 now) {
		return anchor.distanceToSqr(now) >= STILL_BLOCKS * STILL_BLOCKS;
	}
}
