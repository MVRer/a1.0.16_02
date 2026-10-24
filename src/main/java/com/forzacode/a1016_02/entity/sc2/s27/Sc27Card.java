package com.forzacode.a1016_02.entity.sc2.s27;

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
 * {@code sc_27} (docs/SELF_CREATED_2.md, 27): MINOR, Traces, FOG and UNSHARED, WATCHER, no fake. It fits a viewer outdoors at
 * night, dusk or in fog, alone, while another player is away ({@link Sc27#fit}); firing shows the copy on that viewer's
 * client only ({@link Sc27#fire}), and its caps count once that client saw it ({@link Sc27#seen}, R10-8; one never seen
 * may come again). UNSHARED: only the viewer has the moment (nobody beside them could see it;
 * the alone rule keeps them alone anyway). A forced fire skips the fit, never the story or the spot.
 */
final class Sc27Card implements EventCard {
	private final Sc27 sc27;

	Sc27Card(Sc27 sc27) {
		this.sc27 = sc27;
	}

	@Override
	public String id() {
		return Sc27.CARD_ID;
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
		return Set.of(Habit.WATCHER);
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.FOG, CardTag.UNSHARED);
	}

	@Override
	public boolean hasFake() {
		return false;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return sc27.fit(player).isEmpty();
	}

	@Override
	public FireResult fire(FireContext ctx) {
		if (ctx.fake()) {
			return FireResult.SKIPPED;
		}
		if (!ctx.forced() && !contextFits(ctx.player(), ctx.level())) {
			return FireResult.SKIPPED;
		}
		return sc27.fire(ctx.player(), ctx.random(), ctx.forced());
	}
}
