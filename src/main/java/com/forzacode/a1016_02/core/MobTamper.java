package com.forzacode.a1016_02.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Every manipulation of an existing mob goes through here (never spawn one). The accident workstream installs
 * the real one. Each method returns false if it did nothing.
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
	}
}
