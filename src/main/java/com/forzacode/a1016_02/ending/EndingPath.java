package com.forzacode.a1016_02.ending;

import java.util.Locale;
import java.util.Optional;

/**
 * Which ending the run is on. {@link #NONE} until a path commits (Stage 4 is entered then, D-006). A: "Stop." (the
 * false peace), B: "Removed" (escalation), C: "For the record" (survival, at a cost), D: "No longer with us" (the
 * true ending, owned by {@code ending.d}).
 */
public enum EndingPath {
	NONE,
	A,
	B,
	C,
	D;

	/** Parses "a", "B", "none"; empty if it is not a path. */
	public static Optional<EndingPath> parse(String text) {
		try {
			return Optional.of(valueOf(text.trim().toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException | NullPointerException e) {
			return Optional.empty();
		}
	}
}
