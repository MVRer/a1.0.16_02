package com.forzacode.a1016_02.atmosphere.mob;

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
