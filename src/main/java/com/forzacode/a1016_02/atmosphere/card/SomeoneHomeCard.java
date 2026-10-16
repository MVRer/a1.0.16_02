package com.forzacode.a1016_02.atmosphere.card;

import java.util.Set;

import com.forzacode.a1016_02.atmosphere.sc.ScGates;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code sc_05} (docs/SELF_CREATED.md 05; MINOR, Traces, SOUND + PERSONAL, VISITOR + WATCHER, fake): fits while the
 * player has been away from their house long enough ({@link ComingHome#refusal}); the fire arms them
 * ({@link ComingHome#arm}, D-091: it counts when it arms) and the approach watcher plays it when they come back. A forced
 * fire skips the time away and the survey (the band and the view rules stay).
 */
public final class SomeoneHomeCard extends AtmosphereCard {
	public static final String ID = ComingHome.STEPS_ID;

	ComingHome home = ComingHome.LIVE;

	public SomeoneHomeCard() {
		super(ID, Tier.MINOR, Stage.TRACES, Set.of(Habit.VISITOR, Habit.WATCHER), Set.of(CardTag.SOUND, CardTag.PERSONAL), true);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return home.refusal(player, ComingHome.Kind.STEPS).isEmpty();
	}

	@Override
	public FireResult fire(FireContext ctx) {
		return fire(home, ctx, ComingHome.Kind.STEPS);
	}

	/** Both coming-home cards: arms the player (a forced fire skips the time away and the survey, never the story). */
	static FireResult fire(ComingHome home, FireContext ctx, ComingHome.Kind kind) {
		ServerPlayer player = ctx.player();
		if (!ScGates.storyAllows(ctx.level().getServer()) || !ScGates.overworld(ctx.level()) || home.base(player).isEmpty()) {
			return FireResult.SKIPPED;
		}
		if (!ctx.forced() && home.refusal(player, kind).isPresent()) {
			return FireResult.SKIPPED;
		}
		return home.arm(player, kind, ctx.fake(), null) ? FireResult.FIRED : FireResult.SKIPPED;
	}
}
