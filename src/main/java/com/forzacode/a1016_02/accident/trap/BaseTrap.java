package com.forzacode.a1016_02.accident.trap;

import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.accident.TrapKind;
import com.forzacode.a1016_02.core.Habit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/** Shared parts of a trap: id, habits, the list word and the damage types it kills with. */
abstract class BaseTrap implements TrapKind {
	private final String id;
	private final boolean live;
	private final String cause;
	private final Set<Habit> habits;
	private final List<ResourceKey<DamageType>> damage;

	@SafeVarargs
	BaseTrap(String id, boolean live, String cause, Set<Habit> habits, ResourceKey<DamageType>... damage) {
		this.id = id;
		this.live = live;
		this.cause = cause;
		this.habits = Set.copyOf(habits);
		this.damage = List.of(damage);
	}

	@Override
	public final String id() {
		return id;
	}

	@Override
	public final boolean live() {
		return live;
	}

	@Override
	public final String cause() {
		return cause;
	}

	@Override
	public final Set<Habit> habits() {
		return habits;
	}

	@Override
	public boolean matches(DamageSource source, ArmedTrap armed) {
		for (ResourceKey<DamageType> type : damage) {
			if (source.is(type)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The trap's player's base in this level ("your house" is theirs, D-061): their respawn point or their own first
	 * block, else the search center.
	 */
	static BlockPos base(TrapContext ctx) {
		ServerPlayer player = ctx.player();
		if (player != null) {
			GlobalPos base = ctx.watch().base(player).orElse(null);
			if (base != null && base.dimension().equals(ctx.level().dimension())) {
				return base.pos();
			}
		}
		return ctx.center();
	}

	static String at(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	/** The players in the context's level who could set the trap off now, in its order (the trap's player first). */
	static List<ServerPlayer> here(TrapContext ctx) {
		return ctx.players().stream().filter(p -> p.level() == ctx.level() && p.isAlive() && !p.isSpectator()).toList();
	}
}
