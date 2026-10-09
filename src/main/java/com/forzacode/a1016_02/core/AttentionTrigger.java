package com.forzacode.a1016_02.core;

/**
 * One row of DESIGN.md "Triggers". Apply with {@link Attention#trigger}. The weight is per occurrence; for
 * ongoing conditions (carrying the list, low render distance, daylight) the owning workstream picks the cadence.
 * Defaults here; the live values are {@code pacing.attentionWeights} in the config.
 */
public enum AttentionTrigger {
	// Raises attention
	DISC_13_UNDERGROUND(20.0),
	NAMED_HIM(15.0),
	WROTE_NEAR_TRACES(5.0),
	ENTERED_TUNNEL(4.0),
	DUG_INTO_PYRAMID(8.0),
	REPLANTED_GROVE(5.0),
	CARRYING_LIST(1.0),
	RULES_BOOK_NEAR_BASE(1.0),
	LOW_RENDER_DISTANCE(1.0),
	SLEPT(3.0),
	STARED_AT_HIM(10.0),
	// Lowers attention
	DESTROYED_OWN_WRITING(-6.0),
	OBEYED_AFTER_STOP(-15.0),
	AVOIDED_TRACES(-5.0),
	LEFT_GROVES_ALONE(-3.0),
	STOPPED_DISC_13(-8.0),
	DAYLIGHT_OPEN_AREAS(-1.0),
	// Special: drops sharply, but world spawns a new pyramid
	LIST_IN_LAVA(-25.0);

	private final double defaultWeight;

	AttentionTrigger(double defaultWeight) {
		this.defaultWeight = defaultWeight;
	}

	public double defaultWeight() {
		return defaultWeight;
	}
}
