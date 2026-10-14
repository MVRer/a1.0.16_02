package com.forzacode.a1016_02.lore;

import java.util.OptionalInt;
import java.util.OptionalLong;

import com.forzacode.a1016_02.core.HerobrineState;

/**
 * The late clues' clock (D-066, STORY_CLUES M7): when a group has been stuck on one step of Ending D for some in-game
 * days, a plainer clue turns up where it has been or was already pointed (F38 to F44, each one {@code "stuck"} in its
 * data). Ending D publishes where its chain is as two shared flags, {@value #STEP_FLAG}{@code <n>} and
 * {@value #STEP_DAY_FLAG}{@code <day>} (the day it got there; step 1's clock starts on the day F23 is first read). Days
 * are the world clock's ({@code GameClock.day}), which only runs while a participant is online, so it is the group's
 * clock, never one player's. Lore only reads the flags.
 */
final class LateClues {
	/** Ending D's current step, {@code ending:d_step=<n>} (set by ending; the name is ending's). */
	static final String STEP_FLAG = "ending:d_step=";
	/** The in-game day Ending D's chain reached that step, {@code ending:d_step_day=<day>} (set by ending). */
	static final String STEP_DAY_FLAG = "ending:d_step_day=";

	private LateClues() {
	}

	/** The step Ending D says its chain is on, if it says. */
	static OptionalInt step(HerobrineState state) {
		OptionalLong value = number(state, STEP_FLAG);
		return value.isPresent() ? OptionalInt.of((int) value.getAsLong()) : OptionalInt.empty();
	}

	/** The day the chain reached its step, if Ending D says. */
	static OptionalLong stepDay(HerobrineState state) {
		return number(state, STEP_DAY_FLAG);
	}

	/** Days the chain has been on {@code step} by {@code today}, or -1 while it is on another (or the flags are not there). */
	static long daysStuck(HerobrineState state, int step, long today) {
		OptionalInt now = step(state);
		OptionalLong since = stepDay(state);
		if (now.isEmpty() || now.getAsInt() != step || since.isEmpty()) {
			return -1;
		}
		return Math.max(0, today - since.getAsLong());
	}

	/** The days this late clue waits: the config's override for its id, else its data's. */
	static int days(Fragment fragment, Fragment.Stuck stuck, LoreConfig config) {
		Integer override = config.lateClueDays.get(fragment.id());
		return override == null ? stuck.days() : Math.max(0, override);
	}

	/** True if this is a late clue (its data has {@code "stuck"}). */
	static boolean isLate(Fragment fragment) {
		return fragment.requires().stuck().isPresent();
	}

	private static OptionalLong number(HerobrineState state, String prefix) {
		for (String flag : state.flags()) {
			if (flag.startsWith(prefix)) {
				try {
					return OptionalLong.of(Long.parseLong(flag.substring(prefix.length())));
				} catch (NumberFormatException e) {
					return OptionalLong.empty();
				}
			}
		}
		return OptionalLong.empty();
	}
}
