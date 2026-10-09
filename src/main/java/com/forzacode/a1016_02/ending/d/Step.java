package com.forzacode.a1016_02.ending.d;

import java.util.Locale;
import java.util.Optional;

/** Ending D's chain, in order (DESIGN.md "Ending D"), then the last minute and the afterward. */
public enum Step {
	MAP(1, "the map"),
	GROVE(2, "the untouched grove"),
	TAKE_BACK(3, "take it back"),
	UNDER_SEED(4, "under the seed"),
	TORCHES(5, "count your torches"),
	SENTENCE(6, "finish the sentence"),
	CROSS(7, "his cross"),
	LAST_MINUTE(8, "the last minute"),
	AFTERWARD(9, "afterward");

	private final int number;
	private final String title;

	Step(int number, String title) {
		this.number = number;
		this.title = title;
	}

	public int number() {
		return number;
	}

	public String title() {
		return title;
	}

	/** The step after this one (AFTERWARD stays). */
	public Step next() {
		return this == AFTERWARD ? AFTERWARD : values()[ordinal() + 1];
	}

	public boolean atLeast(Step other) {
		return ordinal() >= other.ordinal();
	}

	public static Optional<Step> byNumber(int number) {
		for (Step step : values()) {
			if (step.number == number) {
				return Optional.of(step);
			}
		}
		return Optional.empty();
	}

	@Override
	public String toString() {
		return number + " (" + title + ")";
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}
