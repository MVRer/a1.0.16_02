package com.forzacode.a1016_02.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Every registered {@link EventCard}, by id. Filled during mod init through {@link Director#register(EventCard)}. */
public final class CardRegistry {
	private static final Map<String, EventCard> CARDS = new LinkedHashMap<>();

	private CardRegistry() {
	}

	static synchronized void register(EventCard card) {
		if (CARDS.putIfAbsent(card.id(), card) != null) {
			throw new IllegalStateException("Duplicate event card id: " + card.id());
		}
	}

	public static Optional<EventCard> get(String id) {
		return Optional.ofNullable(CARDS.get(id));
	}

	/** All cards in registration order. */
	public static Collection<EventCard> all() {
		return Collections.unmodifiableCollection(CARDS.values());
	}

	public static Set<String> ids() {
		return Collections.unmodifiableSet(CARDS.keySet());
	}

	public static List<EventCard> byTier(Tier tier) {
		return CARDS.values().stream().filter(card -> card.tier() == tier).toList();
	}
}
