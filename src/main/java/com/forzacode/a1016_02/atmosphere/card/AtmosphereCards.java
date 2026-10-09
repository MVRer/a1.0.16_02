package com.forzacode.a1016_02.atmosphere.card;

import java.util.List;

import com.forzacode.a1016_02.core.EventCard;

/** Every card of the atmosphere layer, in registration order. */
public final class AtmosphereCards {
	private AtmosphereCards() {
	}

	public static List<EventCard> all() {
		return List.of(
				new FogDriftCard(),
				new AnimalsFaceFogCard(),
				new DistantCaveSoundCard(),
				new ZombieAtDuskCard(),
				new SilenceCard(),
				new MiningInTheDarkCard(),
				new ChestOpensCard(),
				new DoorLeftOpenCard(),
				new OneBlockMissingCard(),
				new FootstepLateCard(),
				new CompassDriftCard(),
				new CowWhereNothingSpawnsCard(),
				new VillagersInsideCard(),
				new DogWontGoCard(),
				new CatHissesCornerCard(),
				new PatientSkeletonCard(),
				new EmptyWaterCard(),
				new BatInSealedRoomCard());
	}
}
