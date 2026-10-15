package com.forzacode.a1016_02.atmosphere.mob;

import java.util.List;
import java.util.UUID;

import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * What {@link MobTamperImpl} is doing to one mob, as server tick deadlines (exclusive). Lives in a field the mob
 * mixin adds, so it is never saved with the mob.
 */
public final class TamperState {
	long freezeUntil = Long.MIN_VALUE;
	long faceUntil = Long.MIN_VALUE;
	@Nullable Vec3 faceTarget;
	long silenceUntil = Long.MIN_VALUE;
	/**
	 * The players the effect is for ({@link MobTamperImpl#bind}): the card's target first, then everyone who shared the
	 * moment. Kept while any of them is here; empty if unbound.
	 */
	List<UUID> owners = List.of();

	/** The first player the effect is for (the card's target), or null if unbound. */
	public @Nullable UUID owner() {
		return owners.isEmpty() ? null : owners.getFirst();
	}

	/** Everyone the effect is for, the card's target first (empty if unbound). */
	public List<UUID> owners() {
		return owners;
	}

	public boolean frozen(long now) {
		return now < freezeUntil;
	}

	public boolean facing(long now) {
		return faceTarget != null && now < faceUntil;
	}

	public boolean silenced(long now) {
		return now < silenceUntil;
	}

	public @Nullable Vec3 faceTarget() {
		return faceTarget;
	}

	boolean idle(long now) {
		return !frozen(now) && !facing(now) && !silenced(now);
	}
}
