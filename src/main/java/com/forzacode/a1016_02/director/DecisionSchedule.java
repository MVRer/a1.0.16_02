package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * When each participant's next decision is due, in server ticks, kept by UUID only (D-062.1). Each player decides
 * once per director interval on their own cadence, staggered: a player who starts is given the first tick one
 * interval on whose phase no other player has, so two players never decide in the same tick (while there are fewer
 * players than ticks in an interval). Per session: forgotten when the player stops and on server stop.
 */
final class DecisionSchedule {
	private final Map<UUID, Long> next = new LinkedHashMap<>();

	/**
	 * Starts deciding for this player: the first decision one interval after {@code now}, moved on to the next tick
	 * whose phase no other scheduled player has. A player already scheduled keeps their tick.
	 *
	 * @return the tick of their first decision
	 */
	long start(UUID player, long now, long interval) {
		Long existing = next.get(player);
		if (existing != null) {
			return existing;
		}
		long step = Math.max(1, interval);
		Set<Long> taken = new HashSet<>();
		next.values().forEach(tick -> taken.add(Math.floorMod(tick, step)));
		long first = now + step;
		for (long i = 0; i < step && taken.contains(Math.floorMod(first, step)); i++) {
			first++;
		}
		next.put(player, first);
		return first;
	}

	/** Stops deciding for this player only. */
	void stop(UUID player) {
		next.remove(player);
	}

	boolean contains(UUID player) {
		return next.containsKey(player);
	}

	/** Every scheduled player, in the order they started. A copy. */
	List<UUID> players() {
		return new ArrayList<>(next.keySet());
	}

	boolean anyDue(long now) {
		for (long tick : next.values()) {
			if (tick <= now) {
				return true;
			}
		}
		return false;
	}

	/** The players whose decision is due at {@code now}, each moved on by whole intervals (their phase kept). */
	List<UUID> due(long now, long interval) {
		long step = Math.max(1, interval);
		List<UUID> out = new ArrayList<>();
		for (Map.Entry<UUID, Long> entry : next.entrySet()) {
			long tick = entry.getValue();
			if (tick <= now) {
				out.add(entry.getKey());
				entry.setValue(tick + ((now - tick) / step + 1) * step);
			}
		}
		return out;
	}

	/** The tick of this player's next decision, or -1 if they are not scheduled. */
	long nextTick(UUID player) {
		return next.getOrDefault(player, -1L);
	}

	void clear() {
		next.clear();
	}
}
