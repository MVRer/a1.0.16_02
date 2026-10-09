package com.forzacode.a1016_02.director;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

/** What the director's pure logic knows about a card: everything except the world-facing parts. */
public record CardInfo(String id, Tier tier, Stage earliestStage, Set<Habit> habits, Set<CardTag> tags, boolean hasFake) {
	/** Card ids of the three profile signatures (world builds the cards). */
	public static final String SIGNATURE_STILL_BURNING = "signature_still_burning";
	public static final String SIGNATURE_HOUSE_ELSEWHERE = "signature_house_elsewhere";
	public static final String SIGNATURE_CROSS_ROW = "signature_cross_row";

	public CardInfo {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(tier, "tier");
		Objects.requireNonNull(earliestStage, "earliestStage");
		habits = habits == null || habits.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(habits));
		tags = tags == null || tags.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(tags));
	}

	public static CardInfo of(EventCard card) {
		return new CardInfo(card.id(), card.tier(), card.earliestStage(), card.habits(), card.tags(), card.hasFake());
	}

	public boolean has(CardTag tag) {
		return tags.contains(tag);
	}
}
