package com.forzacode.a1016_02.atmosphere.mob;

import org.jspecify.annotations.Nullable;

/**
 * Added to every {@code Mob} by the atmosphere mob mixin: the in-memory {@link TamperState}, never saved (a mob saved
 * while set on a player only carries a mark, {@link MobTamperImpl#HUNT_SAVED_KEY}, so it is calmed as it loads).
 */
public interface TamperedMob {
	@Nullable TamperState a1016_02$tamper();

	void a1016_02$setTamper(@Nullable TamperState state);

	/** Stops every running goal of the mob's goal and target selectors (each goal's own {@code stop}). */
	void a1016_02$stopGoals();

	/** True once if its saved data said it was saved while set on a player (R12-28); clears the mark. */
	boolean a1016_02$takeHuntSaved();
}
