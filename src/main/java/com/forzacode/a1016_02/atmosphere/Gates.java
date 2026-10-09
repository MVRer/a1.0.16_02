package com.forzacode.a1016_02.atmosphere;

import com.forzacode.a1016_02.core.HerobrineState;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/** The deterministic context gates of atmosphere's cards, as pure functions so the game tests can pin them down. */
public final class Gates {
	private Gates() {
	}

	/** Time of day, 0 to 23999 (0 is sunrise), from the level's default clock. */
	public static long timeOfDay(Level level) {
		return Math.floorMod(level.getDefaultClockTime(), Curves.DAY);
	}

	/** True if the level has a day cycle (not the Nether or the End). */
	public static boolean hasDayCycle(Level level) {
		return !level.dimensionType().hasFixedTime();
	}

	/**
	 * Night by the clock: the level has a day cycle and its time of day is in the configured night window. Unlike
	 * {@code Level.isDarkOutside()}, a daytime thunderstorm does not count.
	 */
	public static boolean isNight(Level level) {
		AtmosphereConfig cfg = AtmosphereConfig.get();
		return hasDayCycle(level) && inWindow(timeOfDay(level), cfg.nightFrom, cfg.nightTo);
	}

	/** True if {@code time} is in {@code [from, to)} on a 24000-tick day; the window may wrap past midnight. */
	public static boolean inWindow(long time, long from, long to) {
		long t = Math.floorMod(time, Curves.DAY);
		long a = Math.floorMod(from, Curves.DAY);
		long b = Math.floorMod(to, Curves.DAY);
		return a <= b ? t >= a && t < b : t >= a || t < b;
	}

	/** Fog drift: never during combat. */
	public static boolean fogDrift(long ticksSinceCombat, long noCombatTicks) {
		return ticksSinceCombat >= noCombatTicks;
	}

	/**
	 * The director's "silent for good" flag ({@code director.DirectorFlags.SILENCE_FOREVER}), set by Endings C and D
	 * (and A's last stretch). While it is set no fog surge or drift ever happens, not even a forced one.
	 */
	public static final String SILENCE_FOREVER_FLAG = "director:silence_forever";

	/** True while the world is quiet for good ({@link #SILENCE_FOREVER_FLAG}): no surge, no drift. */
	public static boolean quietForGood(MinecraftServer server) {
		return HerobrineState.get(server).hasFlag(SILENCE_FOREVER_FLAG);
	}

	/** Distant cave sound: alone and still. */
	public static boolean caveSound(boolean alone, long stillTicks, long stillNeeded) {
		return alone && stillTicks >= stillNeeded;
	}

	/**
	 * Mining in the dark: at night, in bed or still long enough, no other sound card in the gap, and fewer than
	 * {@code perNight} plays tonight.
	 */
	public static boolean miningInTheDark(boolean night, boolean sleeping, long stillTicks, long stillNeeded, long ticksSinceSound,
			long soundGap, int playedTonight, int perNight) {
		return night && (sleeping || stillTicks >= stillNeeded) && ticksSinceSound >= soundGap && playedTonight < perNight;
	}

	/** Footstep that stops late: once per session, on foot. */
	public static boolean footstep(boolean usedThisSession, boolean armed, boolean onGround, boolean riding) {
		return !usedThisSession && !armed && onGround && !riding;
	}

	/** Away from a place: in another dimension, or at least {@code minBlocks} away. */
	public static boolean away(boolean sameDimension, double distanceSqr, int minBlocks) {
		return !sameDimension || distanceSqr >= (double) minBlocks * minBlocks;
	}
}
