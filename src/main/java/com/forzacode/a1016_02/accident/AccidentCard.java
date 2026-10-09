package com.forzacode.a1016_02.accident;

import java.util.EnumSet;
import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * One trap as a director card: MAJOR, ACCIDENT, from Proximity on, never fake. The director paces it (no accident
 * before the first-accident time, the major gap, quiet); the planner keeps one armed at a time and one per session.
 */
public final class AccidentCard implements EventCard {
	private final TrapKind trap;
	private final AccidentPlannerImpl planner;

	public AccidentCard(TrapKind trap, AccidentPlannerImpl planner) {
		this.trap = trap;
		this.planner = planner;
	}

	@Override
	public String id() {
		return Traps.cardId(trap);
	}

	@Override
	public Tier tier() {
		return Tier.MAJOR;
	}

	@Override
	public Stage earliestStage() {
		return Stage.PROXIMITY;
	}

	@Override
	public Set<Habit> habits() {
		return trap.habits().isEmpty() ? Set.of() : EnumSet.copyOf(trap.habits());
	}

	@Override
	public Set<CardTag> tags() {
		return EnumSet.of(CardTag.ACCIDENT);
	}

	@Override
	public boolean hasFake() {
		return false;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return planner.canArm(world.getServer()) && trap.blocked() == null && trap.contextFits(player, world, AccidentConfig.get())
				&& planner.cachedCandidates(trap) > 0;
	}

	@Override
	public FireResult fire(FireContext ctx) {
		if (ctx.fake()) {
			return FireResult.SKIPPED;
		}
		AccidentPlannerImpl.ArmResult result = planner.arm(ctx.player(), trap, ctx.forced());
		return switch (result.status()) {
			case ARMED -> FireResult.FIRED;
			case NO_SPOT, IN_VIEW -> FireResult.NO_SPOT;
			case BUSY, SESSION, BLOCKED -> FireResult.SKIPPED;
		};
	}
}
