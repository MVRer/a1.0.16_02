package com.forzacode.a1016_02.entity;

import net.minecraft.core.GlobalPos;

import org.jspecify.annotations.Nullable;

/** The pure sighting rules, kept free of world access so the game tests can check them directly. */
public final class SightingRules {
	private SightingRules() {
	}

	/** Never two in one in-game day: false if {@code today} already has {@code maxPerDay} sightings. */
	public static boolean dayAllows(long lastDay, int countOnLastDay, long today, int maxPerDay) {
		return lastDay != today || countOnLastDay < maxPerDay;
	}

	/** Never the same variant twice in a row. */
	public static boolean variantAllows(String lastVariant, String variant) {
		return !variant.equals(lastVariant);
	}

	/** At least {@code spacing} blocks (horizontal) from the last sighting. Another dimension is always far enough. */
	public static boolean farEnough(@Nullable GlobalPos last, GlobalPos pos, int spacing) {
		if (last == null || !last.dimension().equals(pos.dimension())) {
			return true;
		}
		long dx = last.pos().getX() - pos.pos().getX();
		long dz = last.pos().getZ() - pos.pos().getZ();
		return dx * dx + dz * dz >= (long) spacing * spacing;
	}

	/** True if {@code timeOfDay} (0..23999) lies in {@code [from, to)}; a window may wrap past midnight. */
	public static boolean inWindow(long timeOfDay, int from, int to) {
		long t = Math.floorMod(timeOfDay, 24000L);
		return from <= to ? t >= from && t < to : t >= from || t < to;
	}

	/** Clear daylight: inside the day window and not raining. Never a sighting then. */
	public static boolean clearDaylight(long timeOfDay, boolean raining, EntityConfig config) {
		return !raining && inWindow(timeOfDay, config.clearDayFrom, config.clearDayTo);
	}

	/** Dusk or dawn, the ridge's sky. Rain counts too: a grey sky works as well as a dusk one. */
	public static boolean duskSky(long timeOfDay, boolean raining, EntityConfig config) {
		return raining || inWindow(timeOfDay, config.duskFrom, config.duskTo) || inWindow(timeOfDay, config.dawnFrom, config.dawnTo);
	}

	public static boolean night(long timeOfDay, EntityConfig config) {
		return inWindow(timeOfDay, config.nightFrom, config.nightTo);
	}

	/** Why a sighting ends this tick. */
	public enum EndCause { NONE, FLEE, STARE, APPROACH }

	/**
	 * What ends the sighting this tick. Coming within the flee distance always does. Otherwise nothing does until he
	 * has been seen for {@code minSeenTicks}; then a long enough stare, or enough distance closed.
	 *
	 * @param seenFor ticks since he was first seen, or -1 if nobody has seen him yet
	 */
	public static EndCause endCause(boolean withinFlee, boolean stareDone, boolean approached, long seenFor, long minSeenTicks) {
		if (withinFlee) {
			return EndCause.FLEE;
		}
		if (seenFor < 0 || seenFor < minSeenTicks) {
			return EndCause.NONE;
		}
		if (stareDone) {
			return EndCause.STARE;
		}
		return approached ? EndCause.APPROACH : EndCause.NONE;
	}

	/**
	 * Whether his out-of-view rules (gone once unseen, gone once he has left, the lifetime) may act yet. Once seen,
	 * not before {@code minSeenTicks}, unless he fled. The safety rules (past the render distance, the edge of the
	 * ticking range) do not ask.
	 */
	public static boolean mayEndOutOfView(boolean everSeen, long seenFor, long minSeenTicks, boolean fled) {
		return !everSeen || fled || seenFor >= minSeenTicks;
	}

	/**
	 * The flee distance for a figure that appeared {@code spawnDistance} blocks out (D-035): the configured one, but
	 * never more than {@code fraction} of the spawn distance, so a close one does not flee the moment he is seen.
	 * An unknown spawn distance (NaN or 0) keeps the configured value.
	 */
	public static double fleeDistance(double configured, double fraction, double spawnDistance) {
		return scaled(configured, fraction, spawnDistance);
	}

	/** The approach that ends the sighting, scaled the same way as {@link #fleeDistance}. */
	public static double approachBlocks(double configured, double fraction, double spawnDistance) {
		return scaled(configured, fraction, spawnDistance);
	}

	private static double scaled(double configured, double fraction, double spawnDistance) {
		if (Double.isNaN(spawnDistance) || spawnDistance <= 0.0 || Double.isNaN(fraction)) {
			return configured;
		}
		return Math.min(configured, Math.max(0.0, fraction) * spawnDistance);
	}

	/**
	 * His running speed in blocks per second (D-036): at least {@code base}, and {@code outrunFactor} times the chasing
	 * player's speed, so he always pulls away; never past {@code max}.
	 */
	public static double runSpeed(double base, double chaserSpeed, double outrunFactor, double max) {
		double chase = Double.isNaN(chaserSpeed) ? 0.0 : Math.max(0.0, chaserSpeed) * Math.max(1.0, outrunFactor);
		return Math.min(max, Math.max(base, chase));
	}

	/** Ground friction times air drag on ordinary blocks: horizontal speed kept from one tick to the next. */
	private static final double GROUND_DRAG = 0.6 * 0.91;
	/**
	 * The navigation speed modifier that makes a mob with this base movement speed cover {@code blocksPerSecond} on
	 * flat ground once up to speed. A walking mob's input and its speed are both {@code s = modifier * base}, so it
	 * gains {@code s * s} a tick and keeps {@link #GROUND_DRAG} of its speed: it settles at {@code s * s / (1 - drag)}
	 * blocks a tick. (Ice and other slippery blocks compensate in vanilla, so this holds there too.)
	 */
	public static double speedModifier(double blocksPerSecond, double baseSpeed) {
		double perTick = Math.max(0.0, blocksPerSecond) / 20.0;
		return Math.sqrt(perTick * (1.0 - GROUND_DRAG)) / baseSpeed;
	}

	/** The inverse of {@link #speedModifier}: blocks per second on flat ground at this modifier. */
	public static double groundSpeed(double modifier, double baseSpeed) {
		double s = modifier * baseSpeed;
		return s * s / (1.0 - GROUND_DRAG) * 20.0;
	}

	/**
	 * Whether a walking figure breaks into the run (D-036): a player closes on him faster than {@code fastSpeed}
	 * (blocks per second), or is already within the flee distance.
	 */
	public static boolean breaksIntoRun(double closingSpeed, double fastSpeed, boolean withinFlee) {
		return withinFlee || !Double.isNaN(closingSpeed) && closingSpeed > fastSpeed;
	}

	/**
	 * The close-chase rush (D-037): a player he cannot outrun ({@code outrunFactor} times their speed is past his
	 * {@code maxRunSpeed}: elytra, flight, a horse), closing in faster than {@code fastSpeed}, within
	 * {@code triggerDistance}. On foot nobody qualifies (sprint-jumping is about 7.1 blocks per second).
	 */
	public static boolean rushes(double distance, double triggerDistance, double chaserSpeed, double closingSpeed, double outrunFactor,
			double maxRunSpeed, double fastSpeed) {
		if (Double.isNaN(distance) || Double.isNaN(chaserSpeed) || Double.isNaN(closingSpeed)) {
			return false;
		}
		return distance < triggerDistance && closingSpeed > fastSpeed && chaserSpeed * Math.max(1.0, outrunFactor) > maxRunSpeed;
	}

	/** Nobody gets closer than this to him: standing still (staring back, hiding), he runs before that. */
	public static final double REACH_BLOCKS = 4.0;

	/**
	 * While he stands (the stare back, hiding behind a trunk): would a player closing at {@code closingSpeed} (blocks
	 * per second) get within {@link #REACH_BLOCKS} of him in the next {@code seconds}? Then he cuts it short and runs.
	 * Someone walking in after triggering him still gets the whole stare back from far enough away.
	 */
	public static boolean wouldBeReached(double distance, double closingSpeed, double seconds) {
		if (distance <= REACH_BLOCKS) {
			return true;
		}
		return !Double.isNaN(closingSpeed) && closingSpeed > 0.0 && distance - REACH_BLOCKS <= closingSpeed * Math.max(0.0, seconds);
	}
}
