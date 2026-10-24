package com.forzacode.a1016_02.entity.sc2.s31;

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
 * 31's two cards (docs/SELF_CREATED_2.md, "Pacing"): {@code sc_31} (MINOR, Traces, SIGHTING, WATCHER, fake: a zombie on the
 * top, {@link Apex}) and {@code sc_31c} (MINOR, Proximity, SIGHTING, STRIPPER, no fake, {@link InsideTrunks}). Each counts
 * as the day's sighting through the figure's own record. A forced fire skips the gates, never the story or the spot.
 */
final class Sc31Card implements EventCard {
	private final boolean trunks;

	private Sc31Card(boolean trunks) {
		this.trunks = trunks;
	}

	static Sc31Card apex() {
		return new Sc31Card(false);
	}

	static Sc31Card trunks() {
		return new Sc31Card(true);
	}

	@Override
	public String id() {
		return trunks ? InsideTrunks.CARD_ID : Apex.CARD_ID;
	}

	@Override
	public Tier tier() {
		return Tier.MINOR;
	}

	@Override
	public Stage earliestStage() {
		return trunks ? Stage.PROXIMITY : Stage.TRACES;
	}

	@Override
	public Set<Habit> habits() {
		return Set.of(trunks ? Habit.STRIPPER : Habit.WATCHER);
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.SIGHTING);
	}

	@Override
	public boolean hasFake() {
		return !trunks;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return (trunks ? InsideTrunks.fit(player) : Apex.fit(player)).isEmpty();
	}

	@Override
	public FireResult fire(FireContext ctx) {
		if (trunks) {
			return ctx.fake() ? FireResult.SKIPPED : InsideTrunks.fire(ctx.player(), ctx.forced());
		}
		return Apex.fire(ctx.player(), ctx.fake(), ctx.forced());
	}
}
