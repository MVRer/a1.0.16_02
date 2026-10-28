package com.forzacode.a1016_02.director.sc;

import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import com.forzacode.a1016_02.director.CardInfo;

/**
 * Dry runs (docs/SELF_CREATED_2.md, "Pacing and director integration"; SC2-9, FIX-SC2-PACE): how the second wave's moments
 * come in a dry run. Every other card keeps the usual dice ({@code DirectorConfig.simFitChance}, 0.35 a look); these come
 * as their gates have them in a world. {@code DirectorSim.Params.from} sets every table.
 * <ul>
 *   <li>{@link #CHANCES}: each card's chance a look that its moment is there. From Proximity the director looks at the
 *   wave's cards once at a draw from its group ({@link ScPacing}: a moment not there is passed over, never held), in Traces
 *   it holds a drawn one for its ten looks as any card. The moments that need mobs about (23: eight within 48, three in
 *   view) at 0.3; a herd at dusk, at night or in fog (21), the friends' chests (28) at 0.15; the follower (24) at 0.1; the
 *   procession (22: a herd and one of his places known within its reach) at 0.04; the sightings at his places (31 at an
 *   apex in sight at dusk, 31c among an 18's trunks) at 0.02 and 0.01; the blank map (30, a filled map of home in the pack
 *   64 from it) at 0.002. The cards' own world caps (a ring every 2 in-game days, a procession every 3, ...) are not in
 *   the dry runs, but the hunts' are ({@link #CAPS}).</li>
 *   <li>{@link #DARK}: the hunts' moments exist only at night on a night their player is out far from home
 *   ({@link #NIGHT_OUT} of the nights, {@link #CALLED_NIGHT_OUT} on a night within a day of a visit to one of his places:
 *   they walked far out to it). Their chance is a night's, rolled once a night (the pack's wild wolves near, half the
 *   time; the call's monsters, nine nights in ten, under a fog spell, {@link #FOG}), and a look finds a moment that is there
 *   half the time ({@code DirectorSim.DARK_LOOK}), so a moment that is there stays there through the night's gates. A
 *   trespass within a day calls them ({@link #CALLED}: the call then needs no fog). The visits: {@link #visitsPerHour}.</li>
 *   <li>{@link #ALONE}: moments that need their player alone (nobody within 48, the shared radius): never beside a
 *   sharer. The friend at a distance (27) and the hunts.</li>
 *   <li>{@link #COMPANY}: moments that need another player taking part (a friend far off, a friend's base, a friend's
 *   mining): never in a one-player run (27, 28, 29).</li>
 * </ul>
 * SC2-9 tuned the first tables against {@code DirectorMultiplayerTests.twoPlayersTogetherShareOneAndAHalfRates} (two
 * players together feel at least 0.95 of one player's minors): a moment one player has and the friend beside them does
 * not feel (UNSHARED: 27, 30, the follower 24) holds the friend's minors back by the target's own gap, and shared
 * moments that rarely fit hold both players' decks. FIX-SC2-PACE made the wave's draws from Proximity looks (nothing held,
 * nothing parked), gave the hunts their dark and a night's chance, and the places' moments their real reach (22, 31: one
 * of his places near, which R10 found far rarer than 0.15 and 0.05). The pair then feels 0.99 to 1.00 of one player's
 * minors.
 */
public final class SimFits {
	/** Each second-wave card's chance a look in a dry run (a dark one's: a night's, on a night its player is out). */
	public static final Map<String, Double> CHANCES = Map.ofEntries(
			Map.entry("sc_21", 0.15),
			Map.entry("sc_22", 0.14),
			Map.entry("sc_23", 0.3),
			Map.entry("sc_24", 0.1),
			Map.entry("sc_27", 0.035),
			Map.entry("sc_28", 0.15),
			Map.entry("sc_29", 0.035),
			Map.entry("sc_30", 0.002),
			Map.entry("sc_31", 0.08),
			Map.entry("sc_31c", 0.04),
			Map.entry("accident_sc25", 0.5),
			Map.entry("accident_sc26", 0.9));
	/** A dark card's chance a night while a trespass calls it (the call needs no fog then). */
	public static final Map<String, Double> CALLED = Map.of("accident_sc25", 0.5, "accident_sc26", 0.9);
	/** Moments that exist only at night, on a night their player is out far from home. */
	public static final Set<String> DARK = Set.of("accident_sc25", "accident_sc26");
	/** Dark moments that need a fog spell unless called. */
	public static final Set<String> FOG = Set.of("accident_sc26");
	/** The share of nights a player is out far from home (R10-4's "at least 15% of nights": an explorer's, twice that). */
	public static final double NIGHT_OUT = 0.3;
	/** The share of the nights within a day of a visit to one of his places that the player is out far from home. */
	public static final double CALLED_NIGHT_OUT = 0.6;
	/** Fires per world at most (sc25 {@code k}, sc26 {@code h}). */
	public static final Map<String, Integer> CAPS = Map.of("accident_sc25", 3, "accident_sc26", 2);
	/**
	 * Minutes of a visit inside one of his places, with the walk about its way in (sc01's share of play there). 12 since
	 * round 3 (R3-P): the stacks' well, the depths' four levels and the 191 pyramids take longer than round 2's 8.
	 */
	public static final double VISIT_MINUTES = 12;
	/**
	 * R3-P: the answers one visit brings above the pit, on average, of at most {@link #ANSWERS_PER_VISIT_MAX}: a place's
	 * first-visit answer (16e, 17's record of the player), its dark (16c, 17f, 18c, 20b), its close for one carrying a
	 * pickaxe (16b, 18b), 17g and the day books; most come once a place or once a player a day.
	 */
	public static final double ANSWERS_PER_VISIT = 1.0;
	public static final int ANSWERS_PER_VISIT_MAX = 4;
	/**
	 * R3-P: a player's answers in one in-game day at most, every place together: each answer comes once a player a day
	 * (16e twice), and the kinds one day's walk reaches hold about six that count.
	 */
	public static final int ANSWERS_PER_DAY = 6;
	/** The tension one answer gives each player inside ({@code world.sc2.kit.Answers.TENSION}). */
	public static final double ANSWER_TENSION = 4.0;
	/**
	 * R3-P: the share of visits that go below a pyramid's pit (33): pyramids are about two visits in five, 97 % of land
	 * starts have their depths, and the tunnel's lit door draws most who reach it. Their answers ({@link #DEPTHS_ANSWERS}
	 * more on average, of two: the lure, then the rooms; the light and the iron door follow as their parts) count as one of
	 * the wave's moments.
	 */
	public static final double DEPTHS_SHARE = 0.25;
	public static final double DEPTHS_ANSWERS = 1.0;
	/**
	 * R3-P: the crumbs' runtime moments a player goes through per hour of awake play: 32c (two crumbs of a line seen in a
	 * day and the line left, then a night with a far crumb to move out of view: about one in seven hours) and, with
	 * company, 32b (a crumb a friend saw taken before the other comes near: once a line, once a day a world).
	 */
	public static final double CRUMBS_PER_HOUR = 0.15;
	public static final double CRUMBS_SHARED_PER_HOUR = 0.1;
	/**
	 * R3-P, "what a player meets": the chance a session of play shows a player one of his places' signs. R3-0's T4 (the
	 * explorer model, 2 000 runs over 20 worlds) had a sign within 128 by the end of session 1 in every run; its model
	 * walks 7 700 blocks a session, so the rows take nine sessions in ten for players who walk less.
	 */
	public static final double SIGN_PER_SESSION = 0.9;
	/** Moments that need their player alone: never beside a sharer in a dry run. */
	public static final Set<String> ALONE = Set.of("sc_27", "accident_sc25", "accident_sc26");
	/** Moments that need another player taking part: never in a one-player dry run. */
	public static final Set<String> COMPANY = Set.of("sc_27", "sc_28", "sc_29");

	private SimFits() {
	}

	/** A card's chance a look: its own for a card of the table, else {@code usual}. */
	public static ToDoubleFunction<CardInfo> of(double usual) {
		return card -> CHANCES.getOrDefault(card.id(), usual);
	}

	/** A card's chance a look while called: its own called chance, else its chance ({@link #of}). */
	public static ToDoubleFunction<CardInfo> called(double usual) {
		return card -> CALLED.getOrDefault(card.id(), CHANCES.getOrDefault(card.id(), usual));
	}

	/** Visits inside one of his places per hour of play: the section's share of play at his places, a visit's minutes each. */
	public static double visitsPerHour(Sc01Config cfg) {
		return Math.max(0, cfg.simRemainsShare) * 60 / VISIT_MINUTES;
	}
}
