package com.forzacode.a1016_02.core;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Every manipulation of an existing mob goes through here (never spawn one). The atmosphere workstream owns and
 * installs the real one (D-017); accident and entity use it. Each method returns false if it did nothing.
 * A mob held for one player (frozen, facing or silenced for them) is {@link #bind bound} to that player, so it is let
 * go as soon as they leave, die, turn spectator or change dimension, not only when its time runs out (D-062.5).
 * Server thread only.
 */
public interface MobTamper {
	/** Stops the mob's movement and AI for a while. */
	boolean freeze(Mob mob, int ticks);

	/** Makes the mob keep looking at a point for a while. */
	boolean face(Mob mob, Vec3 target, int ticks);

	/** Mutes the mob's ambient sounds for a while. */
	boolean silence(Mob mob, int ticks);

	/** Moves the mob to {@code pos}, only if both its current spot and {@code pos} are out of view. */
	boolean moveOutOfView(Mob mob, BlockPos pos);

	/** Ends every effect on the mob now. */
	void release(Mob mob);

	/**
	 * Ties what is being done to {@code mob} right now to {@code player} (whoever it is held for): it is released as
	 * soon as that player is no longer a living participant in the mob's level. Call it right after the freeze, face
	 * or silence. Does nothing if the mob has no effect on it. The latest bind wins. Default: does nothing (the stub).
	 */
	default void bind(Mob mob, UUID player) {
	}

	/**
	 * True while any effect (freeze, face, silence) is on the mob: from the call that started it until its deadline
	 * passes (cleared within a tick after) or {@link #release}. Use it so two cards never fight over one mob.
	 */
	boolean isTampered(Mob mob);

	/** Default: does nothing. */
	final class Stub implements MobTamper {
		@Override
		public boolean freeze(Mob mob, int ticks) {
			return false;
		}

		@Override
		public boolean face(Mob mob, Vec3 target, int ticks) {
			return false;
		}

		@Override
		public boolean silence(Mob mob, int ticks) {
			return false;
		}

		@Override
		public boolean moveOutOfView(Mob mob, BlockPos pos) {
			return false;
		}

		@Override
		public void release(Mob mob) {
		}

		@Override
		public boolean isTampered(Mob mob) {
			return false;
		}
	}
}
