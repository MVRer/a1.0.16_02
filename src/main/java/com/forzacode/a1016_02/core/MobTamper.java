package com.forzacode.a1016_02.core;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Every manipulation of an existing mob goes through here (never spawn one). The atmosphere workstream owns and
 * installs the real one (D-017); accident and entity use it. Each method returns false if it did nothing.
 * A mob held for players (frozen, facing or silenced for them) is {@link #bind bound} to them, so it is let go once
 * none of them is present any more (each left, died, turned spectator or changed dimension) and nobody can see it,
 * not only when its time runs out (D-062.5): a scene goes on while one of them is there, and never stops in view.
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

	/** Ends every effect on the mob now (a hunt is calmed, a steer stops where it is). */
	void release(Mob mob);

	/**
	 * Ties what is being done to {@code mob} right now to {@code player} (whoever it is held for): it is released once
	 * that player is no longer a living participant in the mob's level and nobody can see the mob. Call it right after
	 * the freeze, face or silence. Does nothing if the mob has no effect on it. The latest bind wins. Atmosphere's
	 * implementation also binds to several players at once ({@code MobTamperImpl.bind(mob, Collection<UUID>)}: the
	 * target and everyone sharing the moment), released once none of them is present and nobody can see it. Default:
	 * does nothing (the stub).
	 */
	default void bind(Mob mob, UUID player) {
	}

	/**
	 * Takes the mob's custom name away for good (D-086), ledgered so it can be given back: refused (false, nothing
	 * changed) if the mob has no custom name, the layer is switched off ({@link SelfCreated#enabled}) or its box is in
	 * anyone's view ({@link TraceService#isOutOfView}). The implementation records the name first
	 * ({@link TraceLedger#recordName} with {@code cause}, which may refuse) and only then clears {@code CustomName} and
	 * {@code CustomNameVisible}, so nothing visible is left and nothing is lost unrecorded. {@link TraceService#undo}
	 * gives it back (Ending D's afterward). Not an effect: {@link #isTampered} is not changed by it, and
	 * {@link #release} does not bring the name back. Default: false (the stub); atmosphere implements it.
	 */
	default boolean unname(Mob mob, String cause) {
		return false;
	}

	/**
	 * Walks the mob to {@code to} by its own navigation for {@code ticks} (D-096; docs/SELF_CREATED_2.md, "SC2-0a"):
	 * at {@code speed} (its walk speed factor, 1.0 its normal walk), its goals and target selector skipped while steered
	 * (they do not run: no grazing, panic, tempting, wandering or targeting; whatever ran is stopped), its look along its
	 * path unless a {@link #face} is on. A new call replaces the target and the deadline; the path is made again when the
	 * target moved more than 1.5 blocks (or the walk ended short of it), at most every 10 ticks. Once there it stands
	 * still. Ends at the deadline, {@link #release}, or a {@link #freeze} (a freeze wins: it ends the steer, and a frozen
	 * mob is not steered). Refused (false, nothing changed) for a mob on anyone's leash, riding or ridden, tamed or owned
	 * with its owner online within 48 blocks, a boss, one driven by a brain instead of goals (villagers, goats, axolotls,
	 * frogs, camels, sniffers, armadillos, allays, piglins, hoglins, wardens, breezes and the like), one without AI, one
	 * set on a player ({@link #hunt}: {@link #calm} it first), past the world's cap of steered mobs (48, D-104; a mob
	 * already steered may be steered again), and while the layer is switched off ({@link SelfCreated#enabled}; switching
	 * it off ends every steer within a tick). An effect: {@link #isTampered} is true while it lasts, and it can be
	 * {@link #bind bound}. Default: false (the stub).
	 */
	default boolean steer(Mob mob, Vec3 to, double speed, int ticks) {
		return false;
	}

	/** True while the mob is steered ({@link #steer}). Default: false. */
	default boolean isSteered(Mob mob) {
		return false;
	}

	/**
	 * Sets the mob on {@code target} for {@code ticks} (D-096). Callers: only {@code accident.sc2}, as an armed trap of
	 * that player's (a source test, {@code VerifySc2SideTests}, holds it). A {@code NeutralMob} gets the persistent anger
	 * timer and target (vanilla's angry wolf: angry face, attacks); a mob that is an {@code Enemy} gets the target. While
	 * it lasts the mob keeps that target out to twice its follow range (its follow range is doubled, and the target is
	 * put back each AI step while within it; past it the target is let go until they are within it again), its own goals
	 * do the attacking, and it ends a {@link #steer}. Bound to the
	 * target: calmed as soon as they are not a living player in the mob's level any more (left, died, turned spectator or
	 * creative, changed dimension). Ends at the deadline (calmed), {@link #calm} or {@link #release}; a hurt from another
	 * player ends it without calming, so vanilla's anger turns the mob on whoever hit it. Refused (false, nothing
	 * changed) for a mob that cannot attack (neither neutral nor an enemy), a tamed or owned mob, villagers and golems, a
	 * boss, one driven by a brain, one without AI, a target that is not a living player in the mob's level, in creative
	 * or spectator, and while the layer is switched off (switching it off calms every hunt within a tick). Default:
	 * false (the stub).
	 */
	default boolean hunt(Mob mob, ServerPlayer target, int ticks) {
		return false;
	}

	/**
	 * Calms the mob: ends its {@link #hunt} if it has one, and clears its anger (a {@code NeutralMob}'s), its target,
	 * whom it last was hurt by, and its running goals, so the mob's own AI starts over. Works on any mob, hunted or not
	 * (a hunt's end calms every mob of it, whoever angered it). Never refused. Default: does nothing.
	 */
	default void calm(Mob mob) {
	}

	/** True while the mob is set on a player ({@link #hunt}). Default: false. */
	default boolean isHunting(Mob mob) {
		return false;
	}

	/**
	 * True while any effect (freeze, face, silence, steer, hunt) is on the mob: from the call that started it until its
	 * deadline passes (cleared within a tick after) or {@link #release}. Use it so two cards never fight over one mob.
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
