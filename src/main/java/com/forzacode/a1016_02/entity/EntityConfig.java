package com.forzacode.a1016_02.entity;

import com.forzacode.a1016_02.core.ModConfig;

/**
 * The entity workstream's tunables, stored under {@code sections.entity} in {@code config/a1016_02.json}.
 * Shared rules (300-block spacing, one per day) come from {@code Pacing}; the minimum distance is {@link #minDistance}
 * here (D-035 replaced the 24-block floor).
 * Real-time values are in seconds and go through {@link ModConfig#realTicks(double)}. Versioned (D-075,
 * {@link ModConfig.Versioned}): {@link ModConfig#section} brings an older section up to date on its first read.
 */
public final class EntityConfig implements ModConfig.Versioned {
	private static final String SECTION = "entity";

	// --- where he stands (see FogEdge; D-035) ---
	/**
	 * Every variant but the close one stands this share of the visible fog end away: the fog end the client reports
	 * (dusk fog, surges and vanilla fog included), else the server's estimate. Tunable live.
	 */
	public double normalFractionMin = 0.55;
	public double normalFractionMax = 0.75;
	/** The close variant's share of the visible fog end, then clamped to {@link #closeMinDistance}..{@link #closeMaxDistance}. Tunable live. */
	public double closeFractionMin = 0.35;
	public double closeFractionMax = 0.50;
	/** The close variant's clamp in blocks. Tunable live. */
	public double closeMinDistance = 16;
	public double closeMaxDistance = 28;
	/**
	 * Between trunks (as of the trunk fix; new fields of version 5, no default changed): he stands this many blocks away,
	 * not a share of the fog end, never farther than the visible fog end less {@link #edgeMarginBlocks} and never closer
	 * than {@link #minDistance}, so half of him behind a trunk is still a figure on a clear day or in fog. Tunable live.
	 */
	public double trunkMinDistance = 18;
	public double trunkMaxDistance = 40;
	/** Never closer than this (horizontal, and from any player's eyes to any part of him). Tunable live, never under {@link #MIN_DISTANCE_FLOOR}. */
	public double minDistance = 12;
	/** A client fog report older than this is ignored and the server's estimate is used. */
	public double fogReportMaxAgeSeconds = 5;
	/** He stands at least this far inside the visible fog end. */
	public double edgeMarginBlocks = 2;
	/** Hard cap on the spawn distance, whatever the render distance. */
	public double maxSpawnDistance = 192;
	/** Directions tried around the player when looking for a spot. */
	public int spotSamples = 48;

	// --- gates ---
	/** Never near the base: neither the player nor his spot may be within this many blocks of it. */
	public int baseRadius = 64;
	public double combatCooldownSeconds = 30;
	/** Clear daylight is this time-of-day window (0 = sunrise), unless it rains. No sightings in it. */
	public int clearDayFrom = 0;
	public int clearDayTo = 11800;
	/** Dusk and dawn windows (the ridge needs one of them, or rain). */
	public int duskFrom = 11800;
	public int duskTo = 14000;
	public int dawnFrom = 22500;
	public int dawnTo = 24000;
	/** Night window (the light needs it). */
	public int nightFrom = 13000;
	public int nightTo = 23000;
	/** Chance that a drawn sighting goes ahead in Alone ("a tiny chance") and in Telling ("almost none"). */
	public double aloneChance = 0.3;
	public double tellingChance = 0.2;

	// --- behaviour (the tunable ones can be set live with /a1016 entity tune) ---
	/** Half-angle of the cone around the crosshair that counts as looking at him. */
	public double stareConeDegrees = 8;
	/** Looking straight at him this long (in total, see HimEntity) ends the sighting. Tunable live. */
	public double stareSeconds = 3;
	/**
	 * Closing this many blocks on him, counted since he was first seen ({@link Approach}), ends the sighting; at most
	 * {@link #approachSpawnFraction} of the distance he appeared at. Tunable live.
	 */
	public double approachBlocks = 10;
	public double approachSpawnFraction = 0.3;
	/** Closing less than this at a time is a step, not an approach, and does not count. */
	public double approachStepBlocks = 1.5;
	/**
	 * Coming this close (horizontal) ends the sighting at once, before {@link #minSeenSeconds} too; at most
	 * {@link #fleeSpawnFraction} of the distance he appeared at, so a close one does not flee at once. Tunable live.
	 */
	public double fleeDistance = 18;
	public double fleeSpawnFraction = 0.6;
	/** Once first seen, nothing but {@link #fleeDistance} ends the sighting (or removes him) before this. Tunable live. */
	public double minSeenSeconds = 3;
	/** He stares back this long before he turns away. Tunable live. */
	public double stareBackSeconds = 2;
	public double riseSeconds = 0.75;
	/** Once seen, he is gone after being out of view this long. */
	public double goneAfterUnseenSeconds = 1.0;
	/** The cow is meant to be read as a cow: it may be glanced at and looked back at for a while. */
	public double cowGoneAfterUnseenSeconds = 20;
	/** If nobody sees him, he despawns after this long. */
	public double unseenLifetimeSeconds = 150;
	/** After this long he leaves even if nothing happened. */
	public double maxLifetimeSeconds = 300;
	/** Trunk variant: how long he tries to slip behind the tree before he just leaves. */
	public double hideMaxSeconds = 10;
	/** Movement speed modifiers (on a 0.25 base speed) of the slow walk and the walk. */
	public double slowWalkSpeed = 0.7;
	public double walkSpeed = 0.9;
	/**
	 * The run (D-036), in blocks per second on the ground: never slower than {@link #baseRunSpeed}, and
	 * {@link #outrunFactor} times the chasing player's speed, up to {@link #maxRunSpeed} (sprint-jumping is about 7.1).
	 * Tunable live.
	 */
	public double baseRunSpeed = 5.8;
	public double outrunFactor = 1.1;
	public double maxRunSpeed = 9.0;
	/** A walking figure breaks into the run when a player closes on him faster than this (blocks per second). Tunable live. */
	public double closeInFastSpeed = 3.5;

	// --- goes under (D-030) ---
	/**
	 * Chance that a sighting which would end with him walking or running off ends with him digging straight down and
	 * covering the hole over himself instead, where the ground under him allows it ({@link GoUnder}). Tunable live.
	 */
	public double goUnderChance = 0.25;
	/** Time per block, dug or put back. */
	public double goUnderDigSeconds = 0.4;
	/** How deep he sinks, in blocks; never under {@link GoUnder#MIN_DEPTH} (he has to be under the 2 blocks put back). */
	public int goUnderMinDepth = 4;
	public int goUnderMaxDepth = 6;

	// --- the close-chase rush (D-037) ---
	/**
	 * A player he cannot outrun (elytra, flight, a horse) who closes in fast and gets this close (blocks) makes him turn
	 * and run straight at them, past them, once per sighting. Tunable live.
	 */
	public double rushTriggerDistance = 10;
	/** He passes the player at least this far to the side (center to center); never under {@link Rush#MIN_OFFSET}. */
	public double rushPassOffset = 2.0;
	/** A rush that has not got him out of view by then turns into a plain run away. */
	public double rushMaxSeconds = 4;

	// --- the End and the Nether (D-034) ---
	/** Among the endermen or zombified piglins: at least one of them within this many blocks of his spot. */
	public double amongMobsRadius = 6;
	/** Under a ceiling (the Nether), solid blocks this close over the player's head count as indoors. */
	public int ceilingIndoorsBlocks = 4;

	// --- variant spots ---
	/** The ridge: his feet at least this far above the player's eyes, so the sky is behind him. */
	public double ridgeMinRise = 2;
	/** The light: block light at his feet within this range is the edge of the glow. */
	public int lightEdgeMin = 3;
	public int lightEdgeMax = 7;
	public int lightSearchRadius = 9;
	/** Across the water: share of the line between you and him that has to be water. */
	public double waterFractionMin = 0.55;
	/** Close: a block you placed or dug within this radius makes a place you know. */
	public int closeKnownRadius = 10;

	// --- fakes ---
	public double fakeHoldSeconds = 30;

	// --- eyes (client look only; set live with /a1016 entity eyes, read by the renderer every frame) ---
	/** FLAT, BRIGHT or GLOW, see {@link EyeStyle}. Volatile: the server thread sets it, the render thread reads it. */
	public volatile EyeStyle eyeStyle = EyeStyle.BRIGHT;
	/** BRIGHT and GLOW: the eye pixels take fog at {@code 1 - this} strength. 0 = fogged like the body, 1 = never fogged. */
	public volatile double eyeFogResistance = 0.5;

	// --- the glimpse (D-073; dig's tunnel ladder sets it up through core's Figure) ---
	/**
	 * Never closer than this to any player (from their eyes to his feet, in 3D; the surface sighting's minimum distance
	 * does not apply underground). Never under {@link #MIN_DISTANCE_FLOOR}. Tunable live.
	 */
	public double glimpseMinDistance = 14;
	/**
	 * The raw brightness at his feet and at his eyes at most this (block light, and sky light less the level's sky
	 * darkening, as vanilla's {@code getMaxLocalRawBrightness}): past the last torch and out of the daylight at a shaft
	 * or a hillside mouth, a dim shape.
	 */
	public int glimpseMaxLight = 3;
	/**
	 * Client: the renderer stops drawing him, for good, the first frame his center is within this many degrees of the
	 * camera's look, or within {@link #glimpseHideBlocks} of the camera. Read from the client's own config. Tunable live.
	 */
	public double glimpseHideDegrees = 28;
	public double glimpseHideBlocks = 10;
	/**
	 * Server: he is removed the tick the player he appeared for looks within this many degrees of him (anyone else
	 * who can see him too). Tunable live.
	 */
	public double glimpseServerDegrees = 35;
	/**
	 * Server: he is removed once the player he appeared for steps this many blocks toward him (from the farthest they
	 * stood since he appeared). Anyone else ends him only through the in-view rules. Tunable live.
	 */
	public double glimpseApproachBlocks = 2;
	/** Server: he is removed once he has been in view (anyone's, the out-of-view rule) this long in all. Tunable live. */
	public double glimpseSeenSeconds = 3;
	/** Server: unseen this long in a row, he is gone, out of view. Tunable live. */
	public double glimpseWaitSeconds = 10;

	// --- the screenshot (D-078; new fields of version 4, no default changed) ---
	/** Server: {@code the_screenshot} arms that player's client for this many in-game days (or until they leave). */
	public double screenshotArmDays = 2;
	/**
	 * Server: a dusk fog or fog spell this strong at the player's eyes (the amount {@code FogLimits} pulls the fog in
	 * by, 0 to 1) counts as "the fog is on". Only this weather fog counts: never the render distance alone, an effect or
	 * a surge.
	 */
	public double screenshotFogOnAmount = 0.1;
	/** Client: the sky light at the eyes (raw, 0 to 15) at least this: outside. */
	public int screenshotMinSkyLight = 10;
	/**
	 * Client: he stands this share of the visible fog end away (from the camera to his middle, in 3D), never nearer than
	 * {@link #minDistance} nor farther than {@link #maxSpawnDistance}, as every sighting.
	 */
	public double screenshotFogShareMin = 0.6;
	public double screenshotFogShareMax = 0.75;
	/** Client: he is at least this many pixels tall in the saved image, or the arm waits (no speck spends the one picture). */
	public int screenshotMinPixels = 24;
	/**
	 * Client: the fog the frame is drawn with covers at least this much of him (0 to 1, the shader's fog at his middle):
	 * he stands past the visible fog start, tinted like the terrain around him.
	 */
	public double screenshotMinFog = 0.1;
	/** Client: his middle lands this share of the frame's width from the left or the right edge: inside it, off the middle. */
	public double screenshotEdgeShareMin = 0.15;
	public double screenshotEdgeShareMax = 0.35;
	/** Client: his feet and the top of his head stay at least this share of the frame's height inside it. */
	public double screenshotFrameMargin = 0.03;
	/** Client: spots tried before the arm waits for the next screenshot. */
	public int screenshotSpotTries = 64;

	/** Section version, so changed defaults reach files written before the change ({@link #upgrade}). */
	public int version = 0;
	static final int CURRENT_VERSION = 5;
	/** {@link #minDistance} is never taken under this. */
	public static final double MIN_DISTANCE_FLOOR = 8.0;

	public static EntityConfig get() {
		return ModConfig.section(SECTION, EntityConfig.class, EntityConfig::new);
	}

	/**
	 * Brings an older section up to date and saves it. {@link ModConfig#section} already upgrades the section on its
	 * first read ({@link #upgrade}), so this only saves a section that was changed in memory since.
	 */
	public void migrate() {
		if (upgrade()) {
			save();
		}
	}

	/**
	 * Brings an older section up to its current version ({@link ModConfig.Versioned}); true if anything changed. The steps
	 * from before versioned config (D-075) give way unconditionally, as they always did: version 2 (playtest): approach
	 * 10 blocks (was 6), stare cone 8 degrees (was 6), stare back 2 s (was 1); version 3 (D-035): the close clamp is 16
	 * to 28 blocks (was 24 to 36). Version 4 (v1.2, D-073) only adds the glimpse's values, which a section without them
	 * takes from the defaults by itself; no default changed, so nothing the player set is touched. v1.3's screenshot
	 * values (D-078) are new fields too, without a step (its review fix dropped {@code screenshotMaxFogEnd}: an older
	 * file's key is skipped on read and gone from the next save). Version 5 (the trunk fix) only adds
	 * {@code trunkMinDistance} and {@code trunkMaxDistance}; no default changed. Every later default change adds a step
	 * with {@link ModConfig.Upgrades#keepOrNew}.
	 */
	@Override
	public boolean upgrade() {
		if (version >= CURRENT_VERSION) {
			return false;
		}
		if (version < 2) {
			EntityConfig defaults = new EntityConfig();
			approachBlocks = defaults.approachBlocks;
			stareConeDegrees = defaults.stareConeDegrees;
			stareBackSeconds = defaults.stareBackSeconds;
		}
		if (version < 3) {
			EntityConfig defaults = new EntityConfig();
			closeMinDistance = defaults.closeMinDistance;
			closeMaxDistance = defaults.closeMaxDistance;
		}
		// Version 4: the glimpse's fields are new, nothing to replace.
		// Version 5: the trunk band's two fields are new, nothing to replace.
		version = CURRENT_VERSION;
		return true;
	}

	/** {@link #minDistance}, never under {@link #MIN_DISTANCE_FLOOR} (a bad value in the file included). */
	public double minDistance() {
		double min = minDistance;
		return Double.isNaN(min) ? MIN_DISTANCE_FLOOR : Math.max(MIN_DISTANCE_FLOOR, min);
	}

	/** {@link #goUnderChance} clamped to 0..1 (0 if the file holds NaN). */
	public double goUnderChance() {
		double chance = goUnderChance;
		return Double.isNaN(chance) ? 0.0 : Math.clamp(chance, 0.0, 1.0);
	}

	/** The depth range he sinks to: at least {@link GoUnder#MIN_DEPTH}, at most {@link GoUnder#MAX_DEPTH}, min never over max. */
	public int[] goUnderDepths() {
		int lo = Math.clamp(goUnderMinDepth, GoUnder.MIN_DEPTH, GoUnder.MAX_DEPTH);
		int hi = Math.clamp(goUnderMaxDepth, GoUnder.MIN_DEPTH, GoUnder.MAX_DEPTH);
		return new int[] {Math.min(lo, hi), Math.max(lo, hi)};
	}

	/** {@link #rushPassOffset}, never under {@link Rush#MIN_OFFSET}. */
	public double rushPassOffset() {
		double offset = rushPassOffset;
		return Double.isNaN(offset) ? Rush.MIN_OFFSET : Math.max(Rush.MIN_OFFSET, offset);
	}

	/** The eye style, BRIGHT if the file holds an unknown value. */
	public EyeStyle eyeStyle() {
		EyeStyle style = eyeStyle;
		return style != null ? style : EyeStyle.BRIGHT;
	}

	/** {@link #eyeFogResistance} clamped to 0..1. */
	public double eyeFogResistance() {
		double resistance = eyeFogResistance;
		return Double.isNaN(resistance) ? 0.5 : Math.clamp(resistance, 0.0, 1.0);
	}

	/** {@link #glimpseMinDistance}, never under {@link #MIN_DISTANCE_FLOOR}. */
	public double glimpseMinDistance() {
		double min = glimpseMinDistance;
		return Double.isNaN(min) ? MIN_DISTANCE_FLOOR : Math.max(MIN_DISTANCE_FLOOR, min);
	}

	/** {@link #glimpseHideDegrees} in 0 to 90 (28 if NaN). */
	public double glimpseHideDegrees() {
		return angle(glimpseHideDegrees, 28.0);
	}

	/** {@link #glimpseServerDegrees} in 0 to 90 (35 if NaN). */
	public double glimpseServerDegrees() {
		return angle(glimpseServerDegrees, 35.0);
	}

	/** {@link #screenshotArmDays} in ticks of in-game time, at least one tick (2 days if NaN). */
	public long screenshotArmTicks() {
		double days = Double.isNaN(screenshotArmDays) ? 2.0 : Math.clamp(screenshotArmDays, 0.0, 10_000.0);
		return Math.max(1L, Math.round(days * 24000.0));
	}

	/**
	 * The screenshot spot's rules ({@link ScreenshotSpot.Settings}, normalized there): the distance floor and cap are every
	 * sighting's ({@link #minDistance()}, {@link #maxSpawnDistance}).
	 */
	public ScreenshotSpot.Settings screenshotSpot() {
		return new ScreenshotSpot.Settings(screenshotFogShareMin, screenshotFogShareMax, screenshotEdgeShareMin, screenshotEdgeShareMax,
				screenshotFrameMargin, screenshotSpotTries, minDistance(), maxSpawnDistance, screenshotMinPixels, screenshotMinFog);
	}

	private static double angle(double degrees, double fallback) {
		return Double.isNaN(degrees) ? fallback : Math.clamp(degrees, 0.0, 90.0);
	}

	/**
	 * Writes this section back to {@code config/a1016_02.json}, in memory and on disk ({@link ModConfig#saveSection}):
	 * only {@code sections.entity} changes in the file; everything else is kept as it is there.
	 */
	public void save() {
		ModConfig.saveSection(SECTION, this);
	}
}
