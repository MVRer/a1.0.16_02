package com.forzacode.a1016_02.core;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * At most one armed trap per player at a time (D-059: each player's own pace, at most one per session of theirs), never
 * two on the same ground: a new trap keeps away from every other armed trap and its zone never overlaps theirs. A
 * trap is armed for one player, on their own routes, base and level, and kills whoever sets it off; the death, its
 * cross and its cause are the victim's (D-063), and a trap that has just killed still claims a friend's death a moment
 * later (one accident, two marked deaths). A personal trap (a player's own bed) only ever counts its own player's
 * death. Traps stay armed and deniable when their player leaves (D-062.5); a personal one is dropped when its player
 * turns spectator. Every accident is deniable and leaves exactly one clue. The accident workstream installs the real
 * one.
 */
public interface AccidentPlanner {
	/** Whether any trap is armed: the newest armed trap, whoever's it is. See {@link #armed(UUID)} for one player's. */
	Optional<TrapType> armed();

	/** This player's armed trap, if any (one per player). */
	Optional<TrapType> armed(UUID player);

	/**
	 * Arms a trap for this player. False if they already have one armed, their session already had its trap, no spot
	 * clear of everyone else's traps exists, or it cannot be set up now.
	 */
	boolean arm(ServerPlayer player, TrapType type);

	/**
	 * Arms a trap for this player with its spot looked for around {@code center} (in the player's level) instead of
	 * around the player: the planner tries the spots nearest the center first. Same rules otherwise. Default: the
	 * plain {@link #arm(ServerPlayer, TrapType)}.
	 */
	default boolean arm(ServerPlayer player, TrapType type, BlockPos center) {
		return arm(player, type);
	}

	/** Drops every armed trap, everyone's (the endings stop them all). */
	void disarm();

	/** Drops this player's armed trap only; nothing if they have none. */
	void disarm(UUID player);

	/**
	 * True if this damage came from an armed trap (anyone's: a trap kills whoever sets it off; a personal trap only its
	 * own player), or from one that has just killed someone else.
	 */
	boolean causedBy(ServerPlayer player, DamageSource source);

	/** Default: never arms anything. */
	final class Stub implements AccidentPlanner {
		@Override
		public Optional<TrapType> armed() {
			return Optional.empty();
		}

		@Override
		public Optional<TrapType> armed(UUID player) {
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
		public void disarm(UUID player) {
		}

		@Override
		public boolean causedBy(ServerPlayer player, DamageSource source) {
			return false;
		}
	}
}
