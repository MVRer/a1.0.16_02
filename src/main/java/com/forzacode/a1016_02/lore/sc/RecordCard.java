package com.forzacode.a1016_02.lore.sc;

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
 * {@code sc_14} (14 a): MINOR, Traces, TEXT and UNSHARED, WATCHER and VISITOR, no fake. It fits only for the host of an
 * integrated server, once per world ({@link TheRecord#fit}); firing arms the world for the host's world list
 * ({@link TheRecord#armLast}) and counts when it arms (D-091), for the host alone: UNSHARED, so a guest beside them, who has
 * no world list for this world, never feels it. A forced fire skips the fit, never the switch or the story.
 */
final class RecordCard implements EventCard {
	private final TheRecord record;

	RecordCard(TheRecord record) {
		this.record = record;
	}

	@Override
	public String id() {
		return TheRecord.CARD_ID;
	}

	@Override
	public Tier tier() {
		return Tier.MINOR;
	}

	@Override
	public Stage earliestStage() {
		return Stage.TRACES;
	}

	@Override
	public Set<Habit> habits() {
		return Set.of(Habit.WATCHER, Habit.VISITOR);
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.TEXT, CardTag.UNSHARED);
	}

	@Override
	public boolean hasFake() {
		return false;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return record.fit(player).isEmpty();
	}

	@Override
	public FireResult fire(FireContext ctx) {
		if (ctx.fake()) {
			return FireResult.SKIPPED; // no fake
		}
		if (!ctx.forced() && !contextFits(ctx.player(), ctx.level())) {
			return FireResult.SKIPPED;
		}
		return record.armLast(ctx.level().getServer(), false);
	}
}
