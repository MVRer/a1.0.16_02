package com.forzacode.a1016_02.accident;

import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.TrapType;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import org.jspecify.annotations.Nullable;

/**
 * One kind of accident: where it can happen, what he takes or moves, and how it kills. A preset trap changes the
 * world when it is armed (while the player is away or looking elsewhere); a live trap is armed as a watch and
 * springs at its moment. Either way every edit goes through {@link TraceOp#apply} and is out of view.
 */
public interface TrapKind {
	/** Snake_case id, also the {@link TrapType} id. */
	String id();

	default TrapType type() {
		return new TrapType(id());
	}

	Set<Habit> habits();

	/** True if arming only starts a watch and the world changes later in {@link #tick}. */
	boolean live();

	/** The word for the list when nothing more specific fits ("fell", "lava"...). */
	String cause();

	/** Cheap card gate (time of day, dimension). */
	default boolean contextFits(ServerPlayer player, ServerLevel level, AccidentConfig cfg) {
		return true;
	}

	/** Why this trap cannot run at all right now (a stub service or a missing core opt-in), or null. */
	default @Nullable String blocked() {
		return null;
	}

	/** Spots near {@code ctx.center()} where it could be set now, nearest first. */
	List<Candidate> candidates(TrapContext ctx);

	/**
	 * Sets the trap at this spot. Preset traps make their edits here (false if they are in view or refused); live traps
	 * only check that the spot still holds.
	 */
	default boolean setup(TrapContext ctx, Candidate candidate) {
		return live() || TraceOp.apply(ctx.level(), ctx.view(), "accident:" + id(), candidate.ops);
	}

	/**
	 * One planner step while this trap is armed (live traps spring here, the dark corner puts its torches back).
	 * Returns the updated trap; null disarms it.
	 */
	default @Nullable ArmedTrap tick(TrapContext ctx, ArmedTrap armed) {
		return armed;
	}

	/** True if this damage is the way this trap kills. */
	boolean matches(DamageSource source, ArmedTrap armed);

	/**
	 * True if this death is this trap's doing, given what the planner saw (place and window are already checked).
	 * Defaults to {@link #matches}. False positives are worse than misses: a marked death ends a hardcore run.
	 */
	default boolean claims(ServerPlayer player, DamageSource source, ArmedTrap armed, AccidentData data, long now) {
		return matches(source, armed);
	}

	/** The trap was just armed at this spot: a last chance to remember what it needs (the dark corner's lit cells). */
	default ArmedTrap onArmed(TrapContext ctx, Candidate candidate, ArmedTrap armed) {
		return armed;
	}

	/** A mob was just added to the world (spawned, not loaded) while this trap is armed. Returns the updated trap. */
	default ArmedTrap onSpawned(TrapContext ctx, Entity entity, ArmedTrap armed) {
		return armed;
	}

	/** How long a death still counts once the world has changed. */
	default long window(AccidentConfig cfg) {
		return cfg.deathWindowTicks();
	}

	/** Overworld clock time at which the change is undone (the dark corner's morning), or {@link ArmedTrap#NO_CLOCK}. */
	default long clockUntil(ServerLevel level, AccidentConfig cfg) {
		return ArmedTrap.NO_CLOCK;
	}

	/** True once the trap is over: its play-time window, or its game-clock deadline, has passed (loaded or not). */
	default boolean expired(ArmedTrap armed, long now, long clock, AccidentConfig cfg) {
		return now > armed.until() || armed.hasClock() && clock > armed.clockUntil();
	}

	/** True if a death anywhere counts (the bed), not only in the trap's zone. */
	default boolean anywhere() {
		return false;
	}

	/** True if the trap is over once its mob dies (the zombie wearing the sword, the moved enderman). */
	default boolean endsWithMob() {
		return false;
	}

	/**
	 * The list word for a death this trap claimed, when the trap knows better than the damage type ("own sword" for
	 * the zombie that wore it), or null for {@link DeathCauses#word}.
	 */
	default @Nullable String word(DamageSource source, ArmedTrap armed) {
		return null;
	}

	/**
	 * Every tick while this trap is set and the subject is in its level: a chance to note what only shows for a moment
	 * (falling through a bridge's gap).
	 */
	default void watch(ServerPlayer subject, ArmedTrap armed, AccidentData data, AccidentConfig cfg, long now) {
	}
}
