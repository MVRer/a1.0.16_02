package com.forzacode.a1016_02.core;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * At most one armed trap at a time. Every accident is deniable and leaves exactly one clue. The accident
 * workstream installs the real one.
 */
public interface AccidentPlanner {
	/** The armed trap, if any. */
	Optional<TrapType> armed();

	/** Arms a trap for this player. False if one is already armed or it cannot be set up now. */
	boolean arm(ServerPlayer player, TrapType type);

	/**
	 * Arms a trap for this player with its spot looked for around {@code center} (in the player's level) instead of
	 * around the player: the planner tries the spots nearest the center first. Same rules otherwise. Default: the
	 * plain {@link #arm(ServerPlayer, TrapType)}.
	 */
	default boolean arm(ServerPlayer player, TrapType type, BlockPos center) {
		return arm(player, type);
	}

	void disarm();

	/** True if this damage came from the armed trap. */
	boolean causedBy(ServerPlayer player, DamageSource source);

	/** Default: never arms anything. */
	final class Stub implements AccidentPlanner {
		@Override
		public Optional<TrapType> armed() {
			return Optional.empty();
		}

		@Override
		public boolean arm(ServerPlayer player, TrapType type) {
			return false;
		}

		@Override
		public void disarm() {
		}

		@Override
		public boolean causedBy(ServerPlayer player, DamageSource source) {
			return false;
		}
	}
}
