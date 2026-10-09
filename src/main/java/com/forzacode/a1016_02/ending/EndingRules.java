package com.forzacode.a1016_02.ending;

import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.ModConfig;

/**
 * When each path commits, as pure functions of {@link EndingFacts} and the config (tests drive them with a forced
 * clock). Each {@code xWhy} returns why the path does not commit now, or empty if it does.
 */
public final class EndingRules {
	/** A decision: commit this path, for this reason. */
	public record Decision(EndingPath path, String reason) {
	}

	private EndingRules() {
	}

	/**
	 * Which path commits now, if any. B first (telling is loud and cancels the quiet the others need), then C (a
	 * deliberate act), then A (the passive one). The third marked death is handled by the engine, not here.
	 */
	public static Optional<Decision> decide(EndingFacts f, EndingConfig cfg, int obeyDays) {
		Optional<String> b = bReason(f, cfg);
		if (b.isPresent()) {
			return Optional.of(new Decision(EndingPath.B, b.get()));
		}
		if (cWhy(f, cfg).isEmpty()) {
			return Optional.of(new Decision(EndingPath.C, "did what the team did"));
		}
		if (aWhy(f, cfg, obeyDays).isEmpty()) {
			return Optional.of(new Decision(EndingPath.A, "obeyed \"Stop.\""));
		}
		return Optional.empty();
	}

	/**
	 * Ending A: after "Stop.", the player obeys. OBEYED_AFTER_STOP's rule is the director's (its flag
	 * {@code director:obeyed_after_stop}: no telling for {@code pacing.obeyDays} since "Stop." or the last telling;
	 * {@code obeyDays} here only words the status), never his name since "Stop.", and no fragment read and no trace
	 * visited for {@code aQuietDays}.
	 */
	public static Optional<String> aWhy(EndingFacts f, EndingConfig cfg, int obeyDays) {
		if (!f.stage().atLeast(cfg.minStage())) {
			return Optional.of("stage " + f.stage() + " (needs " + cfg.minStage() + ")");
		}
		if (!f.stopFired() || f.stopSeenAt() == EndingState.NEVER) {
			return Optional.of("\"Stop.\" has not come");
		}
		if (f.lastNamedAt() != EndingState.NEVER && f.lastNamedAt() >= f.stopSeenAt()) {
			return Optional.of("named him after \"Stop.\"");
		}
		if (!f.obeyedAfterStop()) {
			return Optional.of(days("obeying", f.daysSince(Math.max(f.stopSeenAt(), f.lastTellingAt())), obeyDays) + " (the director's obeyed_after_stop)");
		}
		double read = f.daysSince(f.lastReadAt());
		if (read < cfg.aQuietDays) {
			return Optional.of(days("no reading", read, cfg.aQuietDays));
		}
		double traces = f.daysSinceTraceVisit();
		if (traces < cfg.aQuietDays) {
			return Optional.of(days("away from his traces", traces, cfg.aQuietDays));
		}
		return Optional.empty();
	}

	/** Ending B, why it commits (empty if it does not): the player keeps telling, or attention is at the top. */
	public static Optional<String> bReason(EndingFacts f, EndingConfig cfg) {
		if (!f.stage().atLeast(cfg.minStage()) && !f.tellingStarted()) {
			return Optional.empty();
		}
		if (f.stopSeenAt() != EndingState.NEVER && f.tellingsSinceStop() >= cfg.bTellingsAfterStop) {
			return Optional.of("kept telling after \"Stop.\" (" + f.tellingsSinceStop() + ")");
		}
		if (f.tellingCount() >= cfg.bTellingCount) {
			return Optional.of("told " + f.tellingCount() + " times");
		}
		if (f.attention() >= cfg.bAttention) {
			return Optional.of(String.format(Locale.ROOT, "attention %.0f", f.attention()));
		}
		return Optional.empty();
	}

	/** Why B does not commit now (for status). */
	public static Optional<String> bWhy(EndingFacts f, EndingConfig cfg) {
		if (bReason(f, cfg).isPresent()) {
			return Optional.empty();
		}
		return Optional.of(String.format(Locale.ROOT, "tellings %d/%d, after Stop. %d/%d, attention %.0f/%.0f, marked deaths %d/%d", f.tellingCount(),
				cfg.bTellingCount, f.tellingsSinceStop(), cfg.bTellingsAfterStop, f.attention(), cfg.bAttention, f.markedDeaths(), ModConfig.pacing().endingBMarkedDeaths));
	}

	/**
	 * Ending C: every fragment held thrown into lava or fire, the house taken apart by the player's own hand, and no
	 * name, no scar and no fog staring for {@code cQuietDays}.
	 */
	public static Optional<String> cWhy(EndingFacts f, EndingConfig cfg) {
		if (!f.stage().atLeast(cfg.minStage())) {
			return Optional.of("stage " + f.stage() + " (needs " + cfg.minStage() + ")");
		}
		if (f.fragmentsBurned() < cfg.cMinFragmentsBurned) {
			return Optional.of("burned " + f.fragmentsBurned() + " of " + cfg.cMinFragmentsBurned + " fragments");
		}
		if (f.fragmentsUnburned() > 0) {
			return Optional.of(f.fragmentsUnburned() + " fragments they held never burned");
		}
		if (f.holdsFragment()) {
			return Optional.of("still holds a fragment");
		}
		if (!houseTakenApart(f, cfg)) {
			return Optional.of(String.format(Locale.ROOT, "house: broke %d, %d left of %d (needs %.0f%% and at least %d)", f.ownBroken(), f.houseLeft(),
					f.housePeak(), cfg.cHouseTakenFraction * 100, cfg.cMinHouseBlocks));
		}
		double named = f.daysSince(f.lastNamedAt());
		if (named < cfg.cQuietDays) {
			return Optional.of(days("not naming him", named, cfg.cQuietDays));
		}
		double scars = f.daysSinceTraceVisit();
		if (scars < cfg.cQuietDays) {
			return Optional.of(days("away from scars", scars, cfg.cQuietDays));
		}
		double fog = f.daysSince(f.lastFogStareAt());
		if (fog < cfg.cQuietDays) {
			return Optional.of(days("not staring into the fog", fog, cfg.cQuietDays));
		}
		return Optional.empty();
	}

	/** Most of their own blocks around the base broken by them, and most of them gone. */
	public static boolean houseTakenApart(EndingFacts f, EndingConfig cfg) {
		int peak = f.housePeak();
		if (peak < cfg.cMinHouseBlocks) {
			return false;
		}
		double fraction = Math.clamp(cfg.cHouseTakenFraction, 0.0, 1.0);
		return f.ownBroken() >= Math.ceil(fraction * peak) && f.houseLeft() <= Math.floor((1.0 - fraction) * peak);
	}

	private static String days(String what, double done, double needed) {
		return String.format(Locale.ROOT, "%s %.1f of %s days", what, Math.max(0, done), trim(needed));
	}

	private static String trim(double value) {
		return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
	}
}
