package com.forzacode.a1016_02.lore.sc2.s30;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.core.Remains;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A fixed set of his places and who went into which, for the gallery's staged form 2 and the game tests (the live game
 * asks {@code Services.remains()}).
 */
public final class KnownPlaces implements Remains {
	private final List<Place> places;
	private final List<String> visits = new ArrayList<>();

	public KnownPlaces(List<Place> places) {
		this.places = List.copyOf(places);
	}

	/** This player went into this place. */
	public KnownPlaces visit(UUID player, Place place) {
		visits.add(player + "@" + places.indexOf(place));
		return this;
	}

	@Override
	public List<Place> known(ServerLevel level) {
		return places.stream().filter(p -> p.dimension().equals(level.dimension())).toList();
	}

	@Override
	public Optional<Place> containing(ServerLevel level, BlockPos pos) {
		return places.stream().filter(p -> p.dimension().equals(level.dimension()) && p.boxHolds(pos)).findFirst();
	}

	@Override
	public Optional<Place> nearest(ServerLevel level, BlockPos pos, Set<Kind> kinds, int radius) {
		Place best = null;
		for (Place place : known(level)) {
			if (kinds.contains(place.kind()) && place.distanceTo(pos) <= radius && (best == null || place.distanceTo(pos) < best.distanceTo(pos))) {
				best = place;
			}
		}
		return Optional.ofNullable(best);
	}

	@Override
	public boolean visited(UUID player, Place place) {
		return visits.contains(player + "@" + places.indexOf(place));
	}

	@Override
	public long trespassedAt(UUID player) {
		return -1;
	}
}
