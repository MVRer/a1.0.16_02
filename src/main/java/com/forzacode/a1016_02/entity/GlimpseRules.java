package com.forzacode.a1016_02.entity;

import net.minecraft.world.phys.Vec3;

/**
 * The pure rules of the glimpse (D-073), free of world and client access so the game tests can check them: how far
 * off someone's look he is, when the client stops drawing him, when the server removes him, and whether his spot is
 * dark enough. Both sides use them: the renderer ({@code HimRenderer}) for its hide latch, {@link HimEntity} for the
 * removal, {@link GlimpseFigure} for the spot.
 */
public final class GlimpseRules {
	private GlimpseRules() {
	}

	/**
	 * The angle in degrees between {@code look} and the direction from {@code eye} to {@code target}, 0 to 180. A target
	 * at the eye (or no look at all) is 0: as good as looked at.
	 */
	public static double degreesOff(Vec3 eye, Vec3 look, Vec3 target) {
		Vec3 to = target.subtract(eye);
		double d = to.length();
		double l = look.length();
		if (d < 1.0E-6 || l < 1.0E-6) {
			return 0.0;
		}
		double cos = Math.clamp(to.dot(look) / (d * l), -1.0, 1.0);
		return Math.toDegrees(Math.acos(cos));
	}

	/** True if {@code target} is within {@code degrees} of the look from {@code eye} ({@link #degreesOff}). */
	public static boolean looksWithin(Vec3 eye, Vec3 look, Vec3 target, double degrees) {
		return degreesOff(eye, look, target) <= degrees;
	}

	/**
	 * The client's hide latch (D-073): the renderer stops drawing him, for good, the first frame his center is within
	 * {@code hideDegrees} of the camera's look or within {@code hideBlocks} of the camera. A fast turn still shows him for
	 * a few frames at the edge of the screen, a slow one longer; he is never drawn near the middle of it.
	 *
	 * @param camera the camera's position
	 * @param look   the camera's forward vector
	 * @param center the middle of his body
	 */
	public static boolean hiddenOnClient(Vec3 camera, Vec3 look, Vec3 center, double hideDegrees, double hideBlocks) {
		return camera.distanceTo(center) <= hideBlocks || looksWithin(camera, look, center, hideDegrees);
	}

	/** True once someone who stood {@code startDistance} from him stands {@code blocks} or more closer ({@code now}). */
	public static boolean closedOn(double startDistance, double now, double blocks) {
		return !Double.isNaN(startDistance) && !Double.isNaN(now) && startDistance - now >= blocks;
	}

	/**
	 * The light at his feet and at his eyes both at most {@code max}: past the last torch and out of the daylight (the
	 * caller passes the raw brightness, block light and sky light less the sky darkening, {@link GlimpseFigure#dark}).
	 */
	public static boolean darkEnough(int feetLight, int eyeLight, int max) {
		return feetLight <= max && eyeLight <= max;
	}
}
