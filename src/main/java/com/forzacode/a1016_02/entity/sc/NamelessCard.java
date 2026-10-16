package com.forzacode.a1016_02.entity.sc;

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
 * {@code sc_02} (02): AMBIENT, Traces, FOG and PERSONAL, WATCHER, no fake. It fits only with a friend 48 to 64 blocks off,
 * nobody nearer, while the target is outdoors in a fog their client draws thick enough ({@link Nameless#fit}); firing arms
 * the target's client ({@link Nameless#arm}) and counts when it arms (D-091). PERSONAL: only the target's client hides the
 * name, so nobody beside them may share (feel) it. A forced fire skips the fit, never the switch or the story.
 */
final class NamelessCard implements EventCard {
	private final Nameless nameless;

	NamelessCard(Nameless nameless) {
		this.nameless = nameless;
	}

	@Override
	public String id() {
		return Nameless.CARD_ID;
	}

	@Override
	public Tier tier() {
		return Tier.AMBIENT;
	}

	@Override
	public Stage earliestStage() {
		return Stage.TRACES;
	}

	@Override
	public Set<Habit> habits() {
		return Set.of(Habit.WATCHER);
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.FOG, CardTag.PERSONAL);
	}

	@Override
	public boolean hasFake() {
		return false;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return nameless.fit(player).isEmpty();
	}

	@Override
	public FireResult fire(FireContext ctx) {
		if (ctx.fake()) {
			return FireResult.SKIPPED; // no fake
		}
		if (!ctx.forced() && !contextFits(ctx.player(), ctx.level())) {
			return FireResult.SKIPPED;
		}
		return nameless.arm(ctx.player(), NamelessConfig.get().armTicks(), ctx.forced());
	}
}
