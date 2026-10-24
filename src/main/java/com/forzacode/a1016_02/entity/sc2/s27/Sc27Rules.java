package com.forzacode.a1016_02.entity.sc2.s27;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.entity.EntityConfig;
import com.forzacode.a1016_02.entity.SightingRules;

/**
 * 27's pure rules (docs/SELF_CREATED_2.md, 27), free of world access so the game tests check them as they are: which other
 * player counts as away, the hour, the band the copy stands in, when it must end, the caps.
 *
 * <p>Two numbers are vanilla's, not the config's: a player's name is drawn within {@link #NAME_RANGE} of the camera
 * ({@code Attributes.NAME_TAG_DISTANCE}), through blocks unless they sneak. So the copy stands within {@link #NAME_REACH}
 * of the viewer's eyes (its name shows), and a player under the ground counts as away only past
 * {@link #UNDERGROUND_MIN}, where their real name can no longer show through the rock beside the copy's.
 */
public final class Sc27Rules {
	/** Vanilla draws a player's name tag within this many blocks of the camera (the attribute's default). */
	public static final double NAME_RANGE = 64.0;
	/** The copy's feet stand at most this far from the viewer's eyes (3D): its name shows, readable, and still shows as they step back. */
	public static final double NAME_REACH = 56.0;
	/** Under the ground, the other player counts as away only this far off (3D): their real name never shows with the copy's. */
	public static final double UNDERGROUND_MIN = NAME_RANGE + 8.0;
	/** Sky light at the other player's eyes under this: no sky over them (the plan's "under 4"). */
	public static final int NO_SKY = 4;
	/** The viewer is outdoors at this sky light at their eyes or more. */
	public static final int OUTDOORS_SKY = 10;
	/** Nobody, spectators included, within this of the viewer (the alone rule). */
	public static final double ALONE_RADIUS = 48.0;
	/** Once away by the fit, the other player stays away (no end) until nearer than this in the same level (blocks, 3D). */
	public static final double END_NEAR = 100.0;
	/** The fog band: the copy stands from this share of the drawn fog end... */
	public static final double FOG_SHARE_MIN = 0.6;
	/** ...to this share. */
	public static final double FOG_SHARE_MAX = 0.8;
	/** Without a fog that limits it, the band keeps its far part (the dark hides the model's detail): this deep. */
	public static final double FAR_PART = 16.0;
	/** A band shallower than this is none. */
	public static final double MIN_DEPTH = 2.0;
	/** The copy stands this many degrees off the viewer's facing at least... */
	public static final double OFF_FACING_MIN = 40.0;
	/** ...and at most. */
	public static final double OFF_FACING_MAX = 80.0;

	private Sc27Rules() {
	}

	/**
	 * Where the other player is, as the fit reads it.
	 *
	 * @param otherLevel      in another dimension than the viewer
	 * @param distance        3D from the viewer (NaN in another dimension)
	 * @param skyLight        raw sky light at their eyes
	 * @param depth           blocks from their eyes up to the surface over them (0 or less above it)
	 */
	public record Where(boolean otherLevel, double distance, int skyLight, double depth) {
		public static Where elsewhere() {
			return new Where(true, Double.NaN, 0, 0.0);
		}

		/** Under the ground: no sky at their eyes and at least {@code minDepth} under the surface. */
		public boolean underground(double minDepth) {
			return skyLight < NO_SKY && depth >= minDepth;
		}
	}

	/**
	 * True if the other player counts as away for the fit: in another dimension, at least {@code farDistance} off, or under
	 * the ground ({@code undergroundDepth}) and at least {@link #UNDERGROUND_MIN} off.
	 */
	public static boolean away(Where where, Sc27Config config) {
		if (where.otherLevel()) {
			return true;
		}
		if (!(where.distance() >= 0.0)) {
			return false;
		}
		return where.distance() >= config.farDistance || where.underground(config.undergroundDepth) && where.distance() >= UNDERGROUND_MIN;
	}

	/**
	 * True while the other player is still away once the copy shows (looser than {@link #away}, so a step does not end it):
	 * another dimension, at least {@link #END_NEAR} off, or still under the ground (half the depth) and out of name range
	 * ({@link #NAME_RANGE} plus 4).
	 */
	public static boolean stillAway(Where where, Sc27Config config) {
		if (where.otherLevel()) {
			return true;
		}
		if (!(where.distance() >= 0.0)) {
			return false;
		}
		return where.distance() >= END_NEAR || where.underground(config.undergroundDepth / 2.0) && where.distance() >= NAME_RANGE + 4.0;
	}

	/** The hour: night, dusk, or a fog that counts (the world's fog weather). */
	public static boolean hourFits(long timeOfDay, boolean fogCounts, EntityConfig entity) {
		return fogCounts || SightingRules.night(timeOfDay, entity) || SightingRules.inWindow(timeOfDay, entity.duskFrom, entity.duskTo);
	}

	/**
	 * Why 27 does not fit this viewer now, or empty (the common gates come first, elsewhere): outdoors, the hour, alone, and
	 * at least one other participant away ({@link #away}).
	 */
	public static Optional<String> fit(int viewerSky, long timeOfDay, boolean fogCounts, boolean alone, List<Where> others, Sc27Config config,
			EntityConfig entity) {
		if (viewerSky < OUTDOORS_SKY) {
			return Optional.of("not outdoors (sky " + viewerSky + ")");
		}
		if (!hourFits(timeOfDay, fogCounts, entity)) {
			return Optional.of("not night, dusk or fog");
		}
		if (!alone) {
			return Optional.of("someone within " + (int) ALONE_RADIUS);
		}
		if (others.isEmpty()) {
			return Optional.of("nobody else takes part");
		}
		for (Where where : others) {
			if (away(where, config)) {
				return Optional.empty();
			}
		}
		return Optional.of("nobody else is far, below or elsewhere");
	}

	/** Why the caps hold it, or empty: once every {@code daysApart} days per viewer, {@code maxPerWorld} in all. */
	public static Optional<String> caps(long lastDay, long today, int worldCount, Sc27Config config) {
		if (worldCount >= config.maxPerWorld) {
			return Optional.of("used up in this world (" + worldCount + ")");
		}
		if (lastDay >= 0 && today - lastDay < Math.max(0.0, config.daysApart)) {
			return Optional.of("last on day " + lastDay + " (every " + config.daysApart + " days)");
		}
		return Optional.empty();
	}

	/** The band the copy stands in (blocks from the viewer, horizontal), or none. */
	public record Band(double inner, double outer) {
		public boolean empty() {
			return !(outer - inner >= MIN_DEPTH);
		}
	}

	/**
	 * The band: {@code distanceMin} to {@code distanceMax}; with a fog whose drawn end ({@code fogEnd}, NaN if unknown) pulls
	 * {@link #FOG_SHARE_MAX} of it inside that, {@link #FOG_SHARE_MIN} to {@link #FOG_SHARE_MAX} of the fog end within it;
	 * else its far {@link #FAR_PART} (the dark hides the model's detail). Empty when the fog is too thick for it.
	 */
	public static Band band(double fogEnd, Sc27Config config) {
		double inner = Math.max(0.0, config.distanceMin);
		double outer = Math.min(Math.max(inner, config.distanceMax), NAME_REACH);
		if (Double.isFinite(fogEnd) && fogEnd > 0.0 && FOG_SHARE_MAX * fogEnd < outer) {
			outer = FOG_SHARE_MAX * fogEnd;
			inner = Math.max(inner, FOG_SHARE_MIN * fogEnd);
		} else {
			inner = Math.max(inner, outer - FAR_PART);
		}
		return new Band(inner, outer);
	}

	/** The smallest angle between two yaws, in degrees (0 to 180). */
	public static double yawGap(double a, double b) {
		double d = Math.abs(((a - b) % 360.0 + 540.0) % 360.0 - 180.0);
		return d;
	}

	/** True if a spot at bearing {@code toSpot} is {@link #OFF_FACING_MIN} to {@link #OFF_FACING_MAX} off the viewer's facing. */
	public static boolean offFacing(double viewerYaw, double toSpot) {
		double gap = yawGap(viewerYaw, toSpot);
		return gap >= OFF_FACING_MIN && gap <= OFF_FACING_MAX;
	}

	/** Minecraft's yaw from one point toward another (degrees, south 0, west 90). */
	public static float yawToward(double fromX, double fromZ, double toX, double toZ) {
		return (float) (Math.toDegrees(Math.atan2(toZ - fromZ, toX - fromX)) - 90.0);
	}
}
