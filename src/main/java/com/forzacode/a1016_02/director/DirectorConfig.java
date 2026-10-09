package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.forzacode.a1016_02.core.ModConfig;

/**
 * The director's own tunables, stored under {@code sections.director} in {@code config/a1016_02.json}. Shared
 * timings (gaps, stage windows, tension, pity, quiet) live in {@code Pacing}; this section only holds what
 * DESIGN.md leaves to the director. Real-time values are in natural units and go through
 * {@link ModConfig#realTicks(double)}.
 */
public final class DirectorConfig {
	// --- rates (events per real hour of play, before pity) ---
	/** Ambient odds in Alone. Pacing caps Alone at {@code aloneMaxAmbient}; "maybe nothing at all". */
	public double aloneAmbientPerHour = 0.3;
	public double proximityAmbientPerHour = 2.0;
	/** Telling and Removal. */
	public double tellingAmbientPerHour = 1.5;
	/** Traces: "a scar or two found while exploring". Proximity and later use Pacing's 1 to 2 per hour. */
	public double tracesMinorsPerHour = 0.25;
	/** First stage that draws minor and major cards. */
	public String minorMinStage = "TRACES";
	public String majorMinStage = "PROXIMITY";

	// --- gates ---
	/** No two fires of any tier closer than this (real minutes). */
	public double minAnyGapMinutes = 5;
	/** A drawn card whose moment never comes goes back into its deck after this much play (real minutes). */
	public double holdGiveUpMinutes = 60;
	/** An empty session stays empty up to this long (real minutes); a marathon session is not empty forever. */
	public double emptySessionMaxMinutes = 180;

	// --- tension ---
	/** A fake still adds tension, scaled by this. */
	public double fakeTensionFactor = 0.5;
	/** When a quiet starts, tension drops to this ("high tension buys days of nothing"). */
	public double tensionAfterQuiet = 15;

	// --- attention: speeds stages up or slows them down, within the band ---
	/** Attention at which stages run at the tempo's own pace. */
	public double attentionNeutral = 10;
	/** Attention distance from neutral where the full band applies. */
	public double attentionFullEffect = 50;
	/** Maximum change of stage times by attention (0.4 = 40%). */
	public double attentionBand = 0.4;

	// --- deck weights ---
	/** Weight of a card per matching profile habit (cards with no habits weigh 1). */
	public double habitMatchWeight = 3.0;
	/** Weight of a card that names habits, none of which this world has. */
	public double habitMissWeight = 0.4;
	/**
	 * Per stage and tag: multiplies the pick weight; values below 1 are also the chance that a drawn card is
	 * kept at all, so 0.03 is "almost never". Missing entries are 1. Cards with several tags multiply.
	 */
	public Map<String, Map<String, Double>> stageTagWeights = defaultStageTagWeights();

	// --- attention triggers the director owns (D-017) ---
	/** LOW_RENDER_DISTANCE fires once per this much play at a low view distance (real minutes). */
	public double lowRenderTriggerMinutes = 10;
	/** DAYLIGHT_OPEN_AREAS fires once per this much play in clear daylight in the open (real minutes). */
	public double daylightTriggerMinutes = 10;
	/** Horizontal distance of the sky samples around the player for "open area". */
	public int daylightOpenRadius = 8;
	/** How many of the 8 samples (plus the player's own column) must see the sky. */
	public int daylightOpenMinSamples = 7;
	/** SLEPT counts at most once per in-game day. */
	public boolean sleptOncePerDay = true;
	/** The subject must be this close to the jukebox for disc 13 to count. */
	public int disc13HearRadius = 64;
	/** DISC_13_UNDERGROUND counts at most once per this much play (real minutes). */
	public double disc13CooldownMinutes = 10;
	/** AVOIDED_TRACES: no visit near these site types for this many in-game days. */
	public int avoidDays = 4;
	public List<String> avoidSiteTypes = new ArrayList<>(List.of("TUNNEL_END", "OCEAN_PYRAMID", "CUT", "STAIR_BOTTOM"));
	/** Chunk radius around a site that counts as "near" it. */
	public int avoidVisitRadiusChunks = 1;

	// --- dry runs (/a1016 director sim, timewarp) ---
	/** Chance per director tick that a drawn card's moment fits. */
	public double simFitChance = 0.35;
	/** Chance that a fitting card still finds no out-of-view spot. */
	public double simNoSpotChance = 0.1;
	public double simSessionMinMinutes = 45;
	public double simSessionMaxMinutes = 180;

	// --- debug ---
	/** Fires kept in the history (newest last). */
	public int historySize = 200;
	/** Log every director decision at info level instead of debug. */
	public boolean verboseLog = false;

	public static DirectorConfig get() {
		return ModConfig.section("director", DirectorConfig.class, DirectorConfig::new);
	}

	private static Map<String, Map<String, Double>> defaultStageTagWeights() {
		Map<String, Map<String, Double>> map = new LinkedHashMap<>();
		// Alone: looks vanilla. A tiny chance per day of one far sighting, no accidents.
		map.put("ALONE", weights("SIGHTING", 0.15, "ACCIDENT", 0.0, "SCAR", 0.3, "TEXT", 0.0, "DIG", 0.3));
		// Traces: rare, distant sightings.
		map.put("TRACES", weights("SIGHTING", 0.35, "TEXT", 0.3));
		map.put("PROXIMITY", weights());
		// Telling: sightings drop off (which is worse), faster removals, accidents closer to home.
		map.put("TELLING", weights("SIGHTING", 0.03, "TEXT", 1.5, "SCAR", 1.3, "ACCIDENT", 1.3));
		map.put("REMOVAL", weights("SIGHTING", 0.03, "TEXT", 1.5, "SCAR", 1.3, "ACCIDENT", 1.3));
		return map;
	}

	private static Map<String, Double> weights(Object... pairs) {
		Map<String, Double> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			map.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
		}
		return map;
	}
}
