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
 *
 * <p>Versioned (D-075): {@link #upgrade} brings a section written by an older version up one step at a time, taking
 * the new default only where a value still equals the old one. Version 1 is v1.2's pacing (D-070, D-071, D-074);
 * version 2 adds {@link #unfitParkMinutes}; version 3 (v1.3) adds {@link #blindHoldMinutes} and {@link #simBlindPerHour}.
 */
public final class DirectorConfig implements ModConfig.Versioned {
	/** The section's current version: 3 since v1.3. Raise it, and add a step to {@link #upgrade}, for every later default change. */
	public static final int VERSION = 3;

	/** The version this section was last written at; missing in a file (0) is v1.1 or older. Keep the field's default at 0. */
	public int version = 0;

	// --- rates (events per real hour of play, before pity) ---
	/**
	 * Ambient odds in Alone. Pacing caps Alone at {@code aloneMaxAmbient}: something small and deniable early in the
	 * first hour (D-070). D-070 named 3; the dry runs need 4.5 for the first fire by 30 minutes in 3 runs of 4 (at 3, 7
	 * in 10; the median first fire is 15 minutes at 4.5, 20 at 3).
	 */
	public double aloneAmbientPerHour = 4.5;
	public double proximityAmbientPerHour = 2.5;
	/** Telling and Removal. */
	public double tellingAmbientPerHour = 1.5;
	/** Traces: "a scar or two found while exploring", about one an hour. Proximity and later use Pacing's 1.5 to 2.5 per hour. */
	public double tracesMinorsPerHour = 1.0;
	/**
	 * Ambient and minor fires are spread out: after one, the next waits this fraction of the mean interval
	 * (1 / rate), and the odds afterwards rise to keep the mean. 0 = plain random clumping.
	 */
	public double rateRefractory = 0.5;
	/** First stage that draws minor and major cards. */
	public String minorMinStage = "TRACES";
	public String majorMinStage = "PROXIMITY";
	/** First stage that draws signature cards; each card's own earliest stage still applies on top. */
	public String signatureMinStage = "TRACES";
	/** First stage whose sessions may be rolled empty ({@code Pacing.emptySessionChance}); before it every session is lively (D-070). */
	public String emptySessionMinStage = "PROXIMITY";

	// --- friends (D-074) ---
	/**
	 * Players sharing a moment roll this many times one player's ambient and minor rate between them (each a share of
	 * it); apart, each keeps the solo pace. 1 is v1.1's exactly one solo rate.
	 */
	public double groupRateFactor = 1.5;

	// --- fog weather (D-071): during a fog spell that is not quiet ---
	/**
	 * Ambient and minor odds are multiplied by this. D-071 named 2.5; the gaps every fire keeps (15 minutes between
	 * minors, 5 between any two) take most of it back, so the dry runs need 3.5 for twice the fires per hour of a clear
	 * sky (at 2.5, about 1.9 times).
	 */
	public double fogWeatherOddsFactor = 3.5;
	/** The stage weight of SIGHTING cards is multiplied by this (as a pick weight, and as the keep chance while below 1). */
	public double fogWeatherSightingWeight = 3;
	/** A major slot due within this many real minutes may come now. */
	public double fogWeatherMajorPullMinutes = 60;

	// --- gates ---
	/** No two fires of any tier closer than this (real minutes). */
	public double minAnyGapMinutes = 5;
	/**
	 * A player's own first hour of play holds at most this many moments, theirs and felt ones (D-070: two or three,
	 * never more than five). Negative: no cap.
	 */
	public int firstHourMaxFires = 5;
	/** A drawn card whose moment never comes goes back into its deck after this much play (real minutes). */
	public double holdGiveUpMinutes = 60;
	/**
	 * A held ambient or minor card whose moment does not fit (its context, as a card only underground on the surface)
	 * goes back into its deck after this much play (real minutes) in which it was looked at every director tick (looks a
	 * gate held back do not count), so it never freezes its tier for the whole {@link #holdGiveUpMinutes}; the next draw
	 * passes it over if anything else can be drawn. Majors and signatures keep the long hold; BLIND cards have
	 * {@link #blindHoldMinutes}.
	 */
	public double heldUnfitGiveBackMinutes = 5;
	/**
	 * A held BLIND card (D-077), whose moment is a menu open, goes back into its deck after this much play (real minutes)
	 * looked at every director tick without its moment fitting (looks a gate held back do not count, and a recheck at
	 * which it fit without firing starts the count again), parked like any card whose moment did not come
	 * ({@link #unfitParkMinutes}). Longer than {@link #heldUnfitGiveBackMinutes}, since its moment is rarely there at a
	 * director tick and the blind moments' rechecks look for it; short enough that a player who never opens a menu, or a
	 * client without the blind signal, never freezes a tier.
	 */
	public double blindHoldMinutes = 15;
	/**
	 * A card given back because its moment did not come at any look of its hold (after
	 * {@link #heldUnfitGiveBackMinutes}, or a major's {@link #holdGiveUpMinutes}) is parked for this much play (real
	 * minutes): never drawn, and out of its deck for the run-out rule, so the deck refills with the cards that can fire
	 * instead of drawing it again and again (the tunnel ladder for someone who never walks a long dug tunnel), and an
	 * ambient or minor one hands the roll that drew it to the next card. After it, it is drawn as any other card and
	 * fires once its moment comes; until then a hold of it goes back, parked again, at its first look that does not fit.
	 * Its moment is looked at in passing every director tick: once it fits, the park and that first-look rule end.
	 * A BLIND card is parked the same way after {@link #blindHoldMinutes} (D-077). 0: never parked.
	 */
	public double unfitParkMinutes = 30;
	/** An empty session stays empty up to this long (real minutes); a marathon session is not empty forever. */
	public double emptySessionMaxMinutes = 180;

	// --- tension ---
	/** A fake still adds tension, scaled by this. */
	public double fakeTensionFactor = 0.5;
	/** When a quiet starts, tension drops to this: high tension buys days of nothing and is spent. */
	public double tensionAfterQuiet = 0;

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
	/**
	 * A deck reshuffles only when every card it has not fired this cycle is ruled out by the stage or the profile.
	 * Cards that only wait on a passing gate (no accident yet, a sighting today, a tag weight's skip) hold the
	 * reshuffle back, unless the deck has drawn nothing for this many in-game days.
	 */
	public double deckStallDays = 3;

	// --- attention triggers the director owns (D-017) ---
	/** LOW_RENDER_DISTANCE: this much of a player's own play at a low view distance counts once per session (real minutes). */
	public double lowRenderTriggerMinutes = 10;
	/** DAYLIGHT_OPEN_AREAS fires once per this much world play with anyone in clear daylight in the open (real minutes). */
	public double daylightTriggerMinutes = 10;
	/** Horizontal distance of the sky samples around the player for "open area". */
	public int daylightOpenRadius = 8;
	/** How many of the 8 samples (plus the player's own column) must see the sky. */
	public int daylightOpenMinSamples = 7;
	/** SLEPT counts at most once per in-game day, however many sleep. */
	public boolean sleptOncePerDay = true;
	/** Someone taking part must be this close to the jukebox for disc 13 to count (one count however many hear it). */
	public int disc13HearRadius = 64;
	/** DISC_13_UNDERGROUND counts at most once per this much world play (real minutes). */
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
	/**
	 * Hours into a dry run at which someone names him (a naming TELLING), so the run reaches Stage 3 and its
	 * pacing is exercised. Negative = never, the default: the director never enters Telling on its own (D-041).
	 * {@code /a1016 director sim} and the scripted playthrough use it; timewarp never does.
	 */
	public double simTellingAtHour = -1;
	/**
	 * Blind moments per hour of play in a dry run (D-077): the menus a player opens, each a recheck at which a held
	 * {@code BLIND} card's moment fits by {@link #simFitChance}. A BLIND card never fits at a director tick in a dry run
	 * (a menu is open at few of them), and a recheck gives other cards nothing there. 0: no blind moments.
	 */
	public double simBlindPerHour = 20;

	// --- debug ---
	/** Fires kept in the history (newest last). */
	public int historySize = 200;
	/** Log every director decision at info level instead of debug. */
	public boolean verboseLog = false;

	public static DirectorConfig get() {
		return ModConfig.section("director", DirectorConfig.class, DirectorConfig::new);
	}

	/**
	 * Step 0 to 1 (v1.2, D-070): ambients in Alone 4.5 an hour (was 0.3), in Proximity 2.5 (2), minors in Traces 1 an
	 * hour (0.25), Alone's tag weights SIGHTING 0.3, SCAR 0.6 and DIG 0.6 (0.15, 0.3, 0.3), Traces' SIGHTING 0.5
	 * (0.35); each only where it still equals v1.1's default. The fields new in v1.2 (the first hour's cap, the short
	 * give-back of unfit ambient and minor holds, empty sessions from Proximity, the group factor, the fog weather boost)
	 * come with their defaults when missing.
	 *
	 * <p>Step 1 to 2 (v1.2's fixes, R1): only the new {@link #unfitParkMinutes}, which comes with its default when
	 * missing; every other value stays as it is.
	 *
	 * <p>Step 2 to 3 (v1.3, D-077): only the new {@link #blindHoldMinutes} and {@link #simBlindPerHour}, which come with their defaults when missing;
	 * every other value stays as it is.
	 */
	@Override
	public boolean upgrade() {
		if (version >= VERSION) {
			return false;
		}
		DirectorConfig d = new DirectorConfig();
		if (version < 1) {
			aloneAmbientPerHour = ModConfig.Upgrades.keepOrNew(aloneAmbientPerHour, 0.3, d.aloneAmbientPerHour);
			proximityAmbientPerHour = ModConfig.Upgrades.keepOrNew(proximityAmbientPerHour, 2.0, d.proximityAmbientPerHour);
			tracesMinorsPerHour = ModConfig.Upgrades.keepOrNew(tracesMinorsPerHour, 0.25, d.tracesMinorsPerHour);
			stageTagWeights = ModConfig.Upgrades.tagWeights(stageTagWeights, v11StageTagWeights(), d.stageTagWeights);
		}
		// version < 2: unfitParkMinutes is new, nothing to step.
		// version < 3: blindHoldMinutes and simBlindPerHour are new, nothing to step.
		version = VERSION;
		return true;
	}

	private static Map<String, Map<String, Double>> defaultStageTagWeights() {
		Map<String, Map<String, Double>> map = new LinkedHashMap<>();
		// Alone: looks vanilla, almost. Now and then one far sighting, no accidents (D-070).
		map.put("ALONE", weights("SIGHTING", 0.3, "ACCIDENT", 0.0, "SCAR", 0.6, "TEXT", 0.0, "DIG", 0.6));
		// Traces: distant sightings.
		map.put("TRACES", weights("SIGHTING", 0.5, "TEXT", 0.3));
		map.put("PROXIMITY", weights());
		// Telling: sightings drop off (which is worse), faster removals, accidents closer to home.
		map.put("TELLING", weights("SIGHTING", 0.03, "TEXT", 1.5, "SCAR", 1.3, "ACCIDENT", 1.3));
		map.put("REMOVAL", weights("SIGHTING", 0.03, "TEXT", 1.5, "SCAR", 1.3, "ACCIDENT", 1.3));
		return map;
	}

	/** v1.1's {@link #stageTagWeights} (section version 0), for the upgrade step. */
	static Map<String, Map<String, Double>> v11StageTagWeights() {
		Map<String, Map<String, Double>> map = new LinkedHashMap<>();
		map.put("ALONE", weights("SIGHTING", 0.15, "ACCIDENT", 0.0, "SCAR", 0.3, "TEXT", 0.0, "DIG", 0.3));
		map.put("TRACES", weights("SIGHTING", 0.35, "TEXT", 0.3));
		map.put("PROXIMITY", weights());
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
