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
 * @param lastFogStareAt    the last time they stared into the fog: the ending's own guess (still, outdoors, looking
 *                          level at dusk) or the entity's stare-down of him ({@code FigureApi.STARED})
 * @param fragmentsBurned   fragment items the player threw into lava or fire
 * @param fragmentsUnburned fragments the player ever held that never went into lava or fire
 * @param holdsFragment     a fragment item is held anywhere: inventory (nested too), ender chest, containers at the base
 * @param housePeak         the most player-placed blocks ever seen around the home
 * @param houseLeft         player-placed blocks around the home now
 * @param ownBroken         of those, how many the player broke themselves
 * @param obeyedAfterStop   the director's {@code director:obeyed_after_stop}: OBEYED_AFTER_STOP fired and no telling
 *                          came since (Ending A's obeying rule is the director's)
 */
public record EndingFacts(Stage stage, boolean stopFired, boolean tellingStarted, long now, long stopSeenAt, long lastTellingAt, long lastNamedAt,
		long lastReadAt, long lastTraceVisitDay, long lastFogStareAt, int tellingCount, int tellingsSinceStop, double attention, int markedDeaths,
		int fragmentsBurned, int fragmentsUnburned, boolean holdsFragment, int housePeak, int houseLeft, int ownBroken, boolean obeyedAfterStop) {

	/** Without the director's obeyed flag (it reads as not obeyed yet). */
	public EndingFacts(Stage stage, boolean stopFired, boolean tellingStarted, long now, long stopSeenAt, long lastTellingAt, long lastNamedAt,
			long lastReadAt, long lastTraceVisitDay, long lastFogStareAt, int tellingCount, int tellingsSinceStop, double attention, int markedDeaths,
			int fragmentsBurned, int fragmentsUnburned, boolean holdsFragment, int housePeak, int houseLeft, int ownBroken) {
		this(stage, stopFired, tellingStarted, now, stopSeenAt, lastTellingAt, lastNamedAt, lastReadAt, lastTraceVisitDay, lastFogStareAt, tellingCount,
				tellingsSinceStop, attention, markedDeaths, fragmentsBurned, fragmentsUnburned, holdsFragment, housePeak, houseLeft, ownBroken, false);
	}

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
				lastFogStareAt, tellingCount, tellingsSinceStop, attention, markedDeaths, fragmentsBurned, fragmentsUnburned, holdsFragment, housePeak, houseLeft,
				ownBroken, obeyedAfterStop);
	}

	/** A copy with the director's obeyed flag set or not (tests). */
	public EndingFacts withObeyedAfterStop(boolean obeyed) {
		return new EndingFacts(stage, stopFired, tellingStarted, now, stopSeenAt, lastTellingAt, lastNamedAt, lastReadAt, lastTraceVisitDay,
				lastFogStareAt, tellingCount, tellingsSinceStop, attention, markedDeaths, fragmentsBurned, fragmentsUnburned, holdsFragment, housePeak, houseLeft,
				ownBroken, obeyed);
	}
}
