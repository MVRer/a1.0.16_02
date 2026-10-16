package com.forzacode.a1016_02.entity;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.FogSpell;

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

	/** Clear daylight: inside the day window and not raining. Never a sighting then. Without fog weather (see the four-argument form). */
	public static boolean clearDaylight(long timeOfDay, boolean raining, EntityConfig config) {
		return !raining && inWindow(timeOfDay, config.clearDayFrom, config.clearDayTo);
	}

	/**
	 * Clear daylight with the world's fog weather (D-071): fog that counts over the day window (a fog day, a fog night's
	 * morning carry-over while it still gives fog) is not clear daylight; a spell's first ticks before its fog comes and
	 * its last as it lifts are. {@code fogWeather}: {@link #fogWeather} now.
	 */
	public static boolean clearDaylight(long timeOfDay, boolean raining, boolean fogWeather, EntityConfig config) {
		return !fogWeather && clearDaylight(timeOfDay, raining, config);
	}

	/**
	 * Daytime fog (D-071): what would be clear daylight, but a fog spell is on. Only the variants that read in it may come
	 * then ({@link Variant#inDaytimeFog}).
	 */
	public static boolean daytimeFog(long timeOfDay, boolean raining, boolean fogWeather, EntityConfig config) {
		return fogWeather && clearDaylight(timeOfDay, raining, config);
	}

	/**
	 * True while the world's fog spell gives fog that counts at this overworld clock tick ({@link FogSpell#counts}: its
	 * amount at least {@code fogGateShare}, 0.5, of its level). A spell that is on but still easing in (a fog night's
	 * 11500 to 11800, inside the clear-day window) or nearly gone (the end of a morning carry-over) is no fog for the
	 * sightings.
	 */
	public static boolean fogWeather(Optional<FogSpell> spell, long clockTicks) {
		return spell.isPresent() && spell.get().counts(clockTicks);
	}

	/** The sky a sighting stands under, for the daytime-fog rules (D-071). */
	public enum Sky {
		/** The day window, no rain, no fog that counts: never a sighting. */
		CLEAR_DAYLIGHT,
		/** What would be clear daylight, but the fog counts: only {@link Variant#inDaytimeFog} come. */
		DAYTIME_FOG,
		/** Anything else: dusk, night, dawn, rain, or a dimension where time stands still. */
		OTHER
	}

	/** The sky now ({@link #clearDaylight}, {@link #daytimeFog}). */
	public static Sky sky(long timeOfDay, boolean raining, boolean fogWeather, EntityConfig config) {
		if (clearDaylight(timeOfDay, raining, fogWeather, config)) {
			return Sky.CLEAR_DAYLIGHT;
		}
		return daytimeFog(timeOfDay, raining, fogWeather, config) ? Sky.DAYTIME_FOG : Sky.OTHER;
	}

	/**
	 * D-071: a sighting that came in daytime fog ({@code came}, the sky on his first tick) walks away once the day is
	 * clear ({@code now}): the fog stopped counting while he was out. He leaves by the normal rules, out of view. A
	 * sighting that came under any other sky is left alone.
	 */
	public static boolean fogLifted(Sky came, Sky now) {
		return came == Sky.DAYTIME_FOG && now == Sky.CLEAR_DAYLIGHT;
	}

	/**
	 * The daylight gate for one variant (the overworld's; fixed-time dimensions have none): refused in clear daylight;
	 * in daytime fog only the variants that read in it ({@link Variant#inDaytimeFog}: not the ridge, which keeps its
	 * dusk sky, nor the light, which keeps its night). Empty when the variant may come.
	 */
	public static Optional<String> daylight(Variant variant, long timeOfDay, boolean raining, boolean fogWeather, EntityConfig config) {
		if (clearDaylight(timeOfDay, raining, fogWeather, config)) {
			return Optional.of("clear daylight");
		}
		if (daytimeFog(timeOfDay, raining, fogWeather, config) && !variant.inDaytimeFog()) {
			return Optional.of("not in daytime fog");
		}
		return Optional.empty();
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

	/**
	 * The speed he has to beat (D-036), in blocks per second: the fastest of the nearest player and everyone closing
	 * on him, so with several players he outruns the fastest chaser, not just the nearest one. 0 if nobody is there.
	 *
	 * @param chases  every player's {@link Chase}
	 * @param nearest the nearest player's, or null
	 */
	public static double chaserSpeed(List<Chase> chases, @Nullable Chase nearest) {
		double fastest = nearest == null ? 0.0 : nearest.speed();
		for (Chase chase : chases) {
			if (chase.closingSpeed() > 0.0) {
				fastest = Math.max(fastest, chase.speed());
			}
		}
		return fastest;
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
