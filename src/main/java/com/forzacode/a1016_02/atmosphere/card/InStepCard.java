package com.forzacode.a1016_02.atmosphere.card;

import java.util.Set;

import com.forzacode.a1016_02.atmosphere.sc.InStep;
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
 * {@code sc_06} (docs/SELF_CREATED.md 06; AMBIENT, Alone, SOUND + PERSONAL, WATCHER, fake): fits by
 * {@link InStep#refusal}; the fire starts the episode ({@link InStep#start}). A forced fire skips the fit (never the
 * story, the overworld or one at a time).
 */
public final class InStepCard extends AtmosphereCard {
	public static final String ID = "sc_06";

	InStep steps = InStep.LIVE;

	public InStepCard() {
		super(ID, Tier.AMBIENT, Stage.ALONE, Set.of(Habit.WATCHER), Set.of(CardTag.SOUND, CardTag.PERSONAL), true);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return steps.refusal(player, steps.ticks.applyAsLong(world.getServer()), false, false).isEmpty();
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerPlayer player = ctx.player();
		if (!ScGates.storyAllows(ctx.level().getServer()) || !ScGates.overworld(ctx.level())) {
			return FireResult.SKIPPED;
		}
		if (!ctx.forced() && steps.refusal(player, steps.ticks.applyAsLong(ctx.level().getServer()), false, false).isPresent()) {
			return FireResult.SKIPPED;
		}
		return steps.start(player, ctx.fake(), ctx.random()) ? FireResult.FIRED : FireResult.SKIPPED;
	}
}
