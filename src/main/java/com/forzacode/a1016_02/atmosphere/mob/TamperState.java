package com.forzacode.a1016_02.atmosphere.mob;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
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

	// --- steer (D-096) ---
	long steerUntil = Long.MIN_VALUE;
	/** Where the mob is steered to, or null when it is not steered. */
	@Nullable Vec3 steerTarget;
	double steerSpeed = 1.0;
	/** The target the mob's current path was made for (null: none yet), and the first tick a new path may be made. */
	@Nullable Vec3 steerPathTarget;
	long steerNextPath = Long.MIN_VALUE;

	// --- hunt (D-096) ---
	long huntUntil = Long.MIN_VALUE;
	/** The player the mob is set on, or null when it is not hunting. Held like vanilla's own target (a strong reference). */
	@Nullable ServerPlayer huntTarget;
	/** The mob's own tick count when the hunt began: a hurt after it by another player ends the hunt (vanilla's anger then). */
	int huntSince;

	// --- a pose (23's held breath, R11-11) ---
	/** Its arms are held up ({@link MobTamperImpl#raiseArms}), and whether they were up before. */
	boolean armsRaised;
	boolean armsWere;

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

	/** Steered right now (a freeze ends a steer, so a frozen mob is never steered). */
	public boolean steered(long now) {
		return steerTarget != null && now < steerUntil;
	}

	/** Set on a player right now. */
	public boolean hunting(long now) {
		return huntTarget != null && now < huntUntil;
	}

	public @Nullable Vec3 faceTarget() {
		return faceTarget;
	}

	/** Where the mob is steered to, or null. */
	public @Nullable Vec3 steerTarget() {
		return steerTarget;
	}

	/** The player the mob is set on, or null. */
	public @Nullable ServerPlayer huntTarget() {
		return huntTarget;
	}

	boolean idle(long now) {
		return !frozen(now) && !facing(now) && !silenced(now) && !steered(now) && !hunting(now) && !armsRaised;
	}
}
