package com.forzacode.a1016_02.accident;

import com.forzacode.a1016_02.core.ModConfig;

/**
 * Accident tunables, stored under {@code sections.accident} in {@code config/a1016_02.json}. Real-time durations are
 * in seconds or minutes of play and go through {@link ModConfig#realTicks}; scan cadences are cost settings and are
 * never divided by {@code devFastMode}. Trap pacing itself (first accident, major gaps) belongs to the director.
 */
public final class AccidentConfig {
	// --- planner ---
	/** "Never two deaths' worth of setup in the same session." */
	public int maxTrapsPerSession = 1;
	/** How many candidates an arm attempt tries before giving up (each may be in view). */
	public int maxSetupTries = 6;
	/** Planner cadence for live traps, restores and crosses (cost setting, seconds). */
	public double plannerTickSeconds = 1;
	/** How often the candidate cache behind the cards' context gate is refreshed, one trap at a time (seconds). */
	public double candidateRefreshSeconds = 2;
	/** Candidates are searched this far from the player (blocks, cube). */
	public int scanRadius = 48;
	/** Nearest route points and dug blocks looked at per scan. */
	public int scanMaxPoints = 3000;

	// --- windows (real minutes of play) ---
	/** A preset trap stays armed this long; a death in it counts only inside this window. */
	public double deathWindowMinutes = 90;
	/** A live trap waits this long for its moment before it is dropped. */
	public double liveWatchMinutes = 60;
	/** The bed stays gone; phantom deaths count this long. */
	public double noBedWindowMinutes = 240;
	/** After the torches are back, a death in the base still counts this long. */
	public double darkCornerGraceMinutes = 15;
	/** Extra blocks around a trap's zone that still count as "there". */
	public int zoneSlack = 3;

	// --- routes ---
	public double routeSampleSeconds = 0.5;
	public int routeMaxPointsPerDimension = 20_000;
	/** Gaps up to this long between two samples are filled in. */
	public int routeInterpolateMax = 6;
	/** Passing the same spot again after this long counts as a new pass (seconds of play). */
	public double routePassGapSeconds = 60;

	// --- trap shapes ---
	public int gravelMinColumn = 2;
	public int gravelMinShaft = 5;
	public int dripstoneMinHeight = 4;
	public int dripstoneMaxHeight = 16;
	public int rungMinShaft = 8;
	public int bridgeMinDrop = 10;
	/** A drop deeper than this counts for hollow ground (cairn, sleep). */
	public int dropMinDepth = 6;
	/** At most this many blocks are taken out between the walking surface and a cavity. */
	public int hollowMaxDepth = 3;
	public int floodMinSources = 6;
	/** A flooded tunnel springs when the player is this far into the tunnel past the gap (blocks). */
	public int floodSpringMin = 6;
	public int floodSpringMax = 24;
	public int baseRadius = 24;
	public int darkCornerMinTorches = 6;
	public int darkCornerRadius = 4;
	public int darkCornerMaxTorches = 5;
	/** The base keeps at least this many torches lit while the corner is dark. */
	public int darkCornerKeepLit = 3;
	public int noBedAwayBlocks = 48;
	/** The bed is only taken from a player who slept within this many in-game days (TIME_SINCE_REST). */
	public double noBedRestedWithinDays = 1;
	/** A phantom counts if it appeared this close to the player during the sleepless nights after the bed went. */
	public int phantomNearBlocks = 64;
	/** House fire: fire this close to the gap (or to lava that came through it) is traced to it. */
	public int fireTraceRadius = 4;
	/** House fire: a burn counts this long after touching traced fire (seconds of play). */
	public double fireBurnMemorySeconds = 16;
	public int powderSourceRadius = 12;
	public int woolMinLine = 3;
	public int sculkSearchRadius = 8;
	public int mobSearchRadius = 64;
	/** A moved mob lands at least this far from the player. */
	public int mobMinFromPlayer = 8;

	// --- the zombie has your sword (D-032) ---
	/** The zombie is at least this far from the player (blocks). */
	public int swordZombieMinDistance = 24;
	/** And at most this far. */
	public int swordZombieMaxDistance = 48;
	/** A stack comes back only this many in-game days after he took it ("days later"). */
	public int swordMinDaysAfterTaken = 2;
	/** At most this many stack and zombie pairs are offered per scan. */
	public int swordMaxCandidates = 24;

	// --- bridges out of the overworld (D-034) ---
	/** The gap (or the moved enderman) is at least this far from the player (blocks). */
	public int bridgeMinFromPlayer = 6;
	/** Lava bridge: lava within this many blocks under the deck. */
	public int lavaBridgeMaxDrop = 48;
	/** Lava bridge: at least this many lava sources in the 5x5 where you would land (a lake, not a trickle). */
	public int lavaLakeMinSources = 9;
	/** A death counts within this many blocks (horizontally) of the gap or the moved enderman. */
	public int bridgeZoneRadius = 16;
	/** A death by the void, a fall or lava counts this long after dropping through the gap (seconds of game time). */
	public double bridgeFallMemorySeconds = 30;
	/**
	 * Enderman: how far a vanilla random teleport reaches, on x and on z separately (a box, so about 45 blocks on a
	 * diagonal), and upward (it may land any distance lower, since it drops to the ground). The moved one comes from
	 * beyond it, and no other enderman is within it, so the clue holds.
	 */
	public int endermanTeleportReach = 32;
	/** Enderman: looked for this far from the bridge. */
	public int endermanSearchRadius = 96;

	// --- lures ---
	public int cairnSearchRadius = 512;
	public int cairnVisitRadius = 16;
	public int cairnRouteRadius = 64;
	/** Visits before the route goes hollow ("by the third visit"). */
	public int cairnVisitsBeforeTrap = 2;
	/** A route spot counts as the usual route after this many passes. */
	public int usualRoutePasses = 2;
	public int groveSearchRadius = 64;
	public int groveLeavesMin = 6;
	public int groveMinHeight = 6;
	public double groveRecentSeconds = 15;
	public int whiteEyesRoomRadius = 12;
	/** Disc 13's length; the torches go out across it (seconds). */
	public double whiteEyesTrackSeconds = 178;
	public int sleepSiteRadius = 48;
	public int sleepFloorMinDistance = 4;
	public int sleepFloorMaxDistance = 7;
	public int sleepFloorMaxColumns = 6;

	// --- night (time of day, 0 to 24000) ---
	/** The dark corner is set from this time (around sunset) until night starts. */
	public int darkCornerArmFrom = 11500;
	public int nightStart = 13000;
	/** The dark corner's torches go back between this time and sunrise. */
	public int restoreFrom = 22500;
	public int nightEnd = 23500;

	// --- crosses ---
	public int crossMinHeight = 3;
	public int crossMaxHeight = 4;
	public int crossSearchRadius = 6;
	public int crossSourceRadius = 6;
	/** How often a waiting cross is tried again (cost setting, seconds). */
	public double crossRetrySeconds = 5;
	/** A waiting cross with no buildable spot at all (not just "in view") is given up after this long of retries (minutes). */
	public double crossGiveUpMinutes = 60;

	public static AccidentConfig get() {
		return ModConfig.section("accident", AccidentConfig.class, AccidentConfig::new);
	}

	/** Cost cadence in ticks, never divided by devFastMode. */
	static long cadenceTicks(double seconds) {
		return Math.max(1, Math.round(seconds * 20.0));
	}

	public long deathWindowTicks() {
		return ModConfig.realTicks(deathWindowMinutes * 60);
	}

	public long liveWatchTicks() {
		return ModConfig.realTicks(liveWatchMinutes * 60);
	}

	public long noBedWindowTicks() {
		return ModConfig.realTicks(noBedWindowMinutes * 60);
	}

	public long darkCornerGraceTicks() {
		return ModConfig.realTicks(darkCornerGraceMinutes * 60);
	}

	/** Game ticks (burning is game physics, not pacing, so never divided by devFastMode). */
	public long fireBurnMemoryTicks() {
		return Math.round(fireBurnMemorySeconds * 20.0);
	}

	/** Game ticks (falling is game physics, not pacing, so never divided by devFastMode). */
	public long bridgeFallMemoryTicks() {
		return Math.round(bridgeFallMemorySeconds * 20.0);
	}

	/** TIME_SINCE_REST counts game ticks. */
	public long noBedRestedWithinTicks() {
		return Math.round(noBedRestedWithinDays * 24000.0);
	}

	public long routePassGapTicks() {
		return ModConfig.realTicks(routePassGapSeconds);
	}

	public long groveRecentTicks() {
		return ModConfig.realTicks(groveRecentSeconds);
	}

	/** Failed plans before a waiting cross is given up: {@code crossGiveUpMinutes} of retries every {@code crossRetrySeconds}. */
	public int crossMaxPlanFailures() {
		return (int) Math.max(1, Math.min(Integer.MAX_VALUE, ModConfig.realTicks(crossGiveUpMinutes * 60) / cadenceTicks(crossRetrySeconds)));
	}

	public long whiteEyesTrackTicks() {
		return ModConfig.realTicks(whiteEyesTrackSeconds);
	}
}
