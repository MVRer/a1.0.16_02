package com.forzacode.a1016_02.director.sc;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.director.DirectorRules;

import org.jspecify.annotations.Nullable;

/**
 * The second wave's pacing in the director (docs/SELF_CREATED_2.md, "Pacing and director integration"; FIX-SC2-PACE, from
 * R10-1, R10-2 and R10-4): the numbers of {@link Sc01Config} {@code q} to {@code z} as {@link DirectorRules}' group, beats
 * and own clock. No tier's rate changes: the wave takes a share of the draws its tiers already make.
 * <ul>
 *   <li><b>The group</b> ({@link #WAVE}): from Traces on, a share of the ambient and minor tiers' due draws comes from the
 *   second wave's cards ({@code r} in Traces, {@code s} later), which then cycle among themselves (every card of the wave
 *   that can come before any comes again), each weighed by its habits (never under {@code u}) and its own weight
 *   ({@code q}); every other draw comes from the rest of the deck. Two of the wave's moments are at least {@code t} minutes
 *   of the player's play apart, their own or felt (the hunts too).</li>
 *   <li><b>The first beat</b> ({@link #BEATS}): from the start of the player's Proximity, the held breath is reserved as
 *   their next minor for {@code v[0]} minutes of play, then the ring for {@code v[1]}; every gate of the card and of the
 *   tier holds, a beat is never a fake, and for the first {@code w} minutes it looks for its moment only at dusk or at
 *   night. The first beat that comes (fired or felt) ends it.</li>
 *   <li><b>The hunts' own clock</b> ({@link #HUNTS}): never drawn from the major deck; from {@code x} hours of play (times
 *   the tempo, never before Proximity, instead of the first accident's time) a hunt comes at the first director look its
 *   moment fits, at most one per {@code y} hours of play, as the major it is (the major gap, the first day, every gate),
 *   taking the major slot: the player's first at once, a later one when the slot is due within {@code z} minutes, one the
 *   world calls (a trespass in one of his places within one in-game day: {@link #CALL_TICKS}) at once.</li>
 * </ul>
 * The live rules carry it only while the layer is on and its story goes on ({@link #live}); dry runs and tests take the
 * code defaults ({@link #defaults}).
 */
public final class ScPacing {
	/** The second wave's cards that the group paces: every one of its cards, the hunts' too (their moments are spaced). */
	public static final Set<String> WAVE = Set.of("sc_21", "sc_22", "sc_23", "sc_24", "sc_27", "sc_28", "sc_29", "sc_30", "sc_31", "sc_31c",
			"accident_sc25", "accident_sc26");
	/** The first beat's cards, in turn. */
	public static final List<String> BEATS = List.of("sc_23", "sc_21");
	/** The hunts: their own clock. */
	public static final Set<String> HUNTS = Set.of("accident_sc25", "accident_sc26");
	/**
	 * A card of the wave weighs at least this much for the world's habits: one whose habits miss the world's (23, 27 and 31
	 * carry only WATCHER, so they missed in two worlds of three at 0.4) still comes (R10-1).
	 */
	public static final double HABIT_FLOOR = 1.0;
	/** A trespass calls a hunt for this many game ticks after it (one in-game day). */
	public static final long CALL_TICKS = 24_000L;

	private ScPacing() {
	}

	/** The section at its code defaults. */
	public static Sc01Config defaults() {
		return new Sc01Config();
	}

	/**
	 * The live rules: the loaded section while the layer is on and its story goes on ({@code state} null: only the
	 * switch), none otherwise ({@link #clear}).
	 */
	public static DirectorRules live(DirectorRules rules, @Nullable HerobrineState state) {
		if (!SelfCreated.enabled() || state != null && Places.storyQuiet(state)) {
			clear(rules);
		} else {
			apply(rules, Sc01Config.get(), ModConfig.pacing().paceFactor(rules.profile.tempo()));
		}
		return rules;
	}

	/** No group, no beats, no own clock: every card is drawn as ever. */
	public static void clear(DirectorRules rules) {
		rules.groupCards = Set.of();
		rules.groupShare.clear();
		rules.cardWeights.clear();
		rules.groupHabitFloor = 0;
		rules.groupGap = 0;
		rules.groupRepeat = 0;
		rules.beats = List.of();
		rules.beatDarkFirst = 0;
		rules.ownClockCards = Set.of();
		rules.ownClockFrom = 0;
		rules.ownClockGap = 0;
		rules.ownClockPull = 0;
	}

	/** This section's group, beats and own clock into these rules, the hunts' clock times the tempo's {@code pace}. */
	public static void apply(DirectorRules rules, Sc01Config cfg, double pace) {
		clear(rules);
		rules.groupCards = WAVE;
		putShares(rules, Stage.TRACES, cfg.tracesShares);
		putShares(rules, Stage.PROXIMITY, cfg.laterShares);
		putShares(rules, Stage.TELLING, cfg.laterShares);
		if (cfg.weights != null) {
			cfg.weights.forEach((id, weight) -> {
				if (id != null && weight != null && Double.isFinite(weight)) {
					rules.cardWeights.put(id, Math.max(0, weight));
				}
			});
		}
		rules.groupHabitFloor = HABIT_FLOOR;
		rules.groupGap = minutes(cfg.gapMinutes);
		rules.groupRepeat = minutes(cfg.repeatHours * 60);
		List<DirectorRules.Beat> beats = new ArrayList<>();
		for (int i = 0; i < BEATS.size() && cfg.beatMinutes != null && i < cfg.beatMinutes.length; i++) {
			long window = minutes(cfg.beatMinutes[i]);
			if (window > 0) {
				beats.add(new DirectorRules.Beat(BEATS.get(i), window));
			}
		}
		rules.beats = List.copyOf(beats);
		rules.beatStage = Stage.PROXIMITY;
		rules.beatDarkFirst = minutes(cfg.beatDarkMinutes);
		rules.ownClockCards = HUNTS;
		rules.ownClockFrom = ModConfig.realTicks(Math.max(0, cfg.huntFromHours) * pace * 3600);
		rules.ownClockGap = ModConfig.realTicks(Math.max(0, cfg.huntGapHours) * 3600);
		rules.ownClockPull = minutes(cfg.huntPullMinutes);
	}

	/** True if a trespass at {@code trespassedAt} (game ticks, -1: never) calls a hunt at {@code gameTime}. */
	public static boolean calls(long trespassedAt, long gameTime) {
		return trespassedAt >= 0 && gameTime >= trespassedAt && gameTime - trespassedAt <= CALL_TICKS;
	}

	/** True if the world calls this own-clock card for a player who last trespassed at {@code trespassedAt}, now. */
	public static boolean called(String cardId, long trespassedAt, long gameTime) {
		return HUNTS.contains(cardId) && calls(trespassedAt, gameTime);
	}

	private static void putShares(DirectorRules rules, Stage stage, double @Nullable [] shares) {
		Map<Tier, Double> byTier = new EnumMap<>(Tier.class);
		if (shares != null && shares.length > 0 && shares[0] > 0) {
			byTier.put(Tier.AMBIENT, Math.min(1, shares[0]));
		}
		if (shares != null && shares.length > 1 && shares[1] > 0) {
			byTier.put(Tier.MINOR, Math.min(1, shares[1]));
		}
		if (!byTier.isEmpty()) {
			rules.groupShare.put(stage, byTier);
		}
	}

	private static long minutes(double minutes) {
		return Double.isFinite(minutes) && minutes > 0 ? Math.max(1, ModConfig.realTicks(minutes * 60)) : 0;
	}
}
