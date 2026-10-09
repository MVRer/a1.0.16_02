package com.forzacode.a1016_02.core;

/** Outcome of {@link EventCard#fire(FireContext)}. */
public enum FireResult {
	/** It happened. The director raises tension and fires {@link HerobrineEvents#CARD_FIRED}. */
	FIRED,
	/** No out-of-view place exists right now. The director keeps the card for later. */
	NO_SPOT,
	/** The card decided not to run (for example the context changed). */
	SKIPPED
}
