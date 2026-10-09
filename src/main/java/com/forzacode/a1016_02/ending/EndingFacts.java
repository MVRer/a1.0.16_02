package com.forzacode.a1016_02.ending;

import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Stage;

/**
 * Everything the commit rules read, at one moment. Times are {@code GameClock.dayTicks} ({@link EndingState#NEVER}
 * if never); {@code lastTraceVisitDay} is an in-game day ({@code -1} if never). Built live by {@link EndingWatch};
 * tests build it by hand with a forced clock.
 *
 * @param tellingCount      every telling so far ({@code LoreApi.tellingCount})
 * @param tellingsSinceStop tellings after "Stop." was first seen
 * @param fragmentsBurned   fragment items the player threw into lava or fire
 * @param holdsFragment     the player carries a fragment item (inventory or ender chest)
 * @param housePeak         the most player-placed blocks ever seen around the home
 * @param houseLeft         player-placed blocks around the home now
 * @param ownBroken         of those, how many the player broke themselves
 */
public record EndingFacts(Stage stage, boolean stopFired, boolean tellingStarted, long now, long stopSeenAt, long lastTellingAt, long lastNamedAt,
		long lastReadAt, long lastTraceVisitDay, long lastFogStareAt, int tellingCount, int tellingsSinceStop, double attention, int markedDeaths,
		int fragmentsBurned, boolean holdsFragment, int housePeak, int houseLeft, int ownBroken) {

	/** In-game days since {@code at}, or {@link Double#POSITIVE_INFINITY} if it never happened. */
	public double daysSince(long at) {
		return at == EndingState.NEVER ? Double.POSITIVE_INFINITY : (now - at) / (double) GameClock.TICKS_PER_DAY;
	}

	/** Whole in-game days since the last visit near his traces, or infinity if never. */
	public double daysSinceTraceVisit() {
		return lastTraceVisitDay < 0 ? Double.POSITIVE_INFINITY : Math.floorDiv(now, GameClock.TICKS_PER_DAY) - lastTraceVisitDay;
	}

	/** A copy at another moment (tests move the clock). */
	public EndingFacts at(long newNow) {
		return new EndingFacts(stage, stopFired, tellingStarted, newNow, stopSeenAt, lastTellingAt, lastNamedAt, lastReadAt, lastTraceVisitDay,
				lastFogStareAt, tellingCount, tellingsSinceStop, attention, markedDeaths, fragmentsBurned, holdsFragment, housePeak, houseLeft,
				ownBroken);
	}
}
