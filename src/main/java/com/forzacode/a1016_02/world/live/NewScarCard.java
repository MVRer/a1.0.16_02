package com.forzacode.a1016_02.world.live;

import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * "New scar out of view" (MINOR, Traces): a hill the player crossed days ago is now dead, or a grove is bare.
 * Only in chunks left for {@code newScarAwayDays} in-game days, never in view. The fake is one bare tree.
 */
public final class NewScarCard implements EventCard {
	public static final String ID = "new_scar";

	@Override
	public String id() {
		return ID;
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
		return Set.of(Habit.STRIPPER);
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.SCAR);
	}

	@Override
	public boolean hasFake() {
		return true;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return world.dimension() == Level.OVERWORLD && GameClock.day(world.getServer()) >= ModConfig.pacing().newScarAwayDays
				&& !NewScarPlacer.waiting();
	}

	@Override
	public FireResult fire(FireContext ctx) {
		NewScarPlacer.Outcome outcome = NewScarPlacer.place(ctx.player(), ctx.random(), ctx.fake(), Services.watch()::lastVisitDay,
				GameClock.day(ctx.level().getServer()), null);
		// Scheduled: its chunks load over the next ticks and it happens out of view then (never on camera).
		return outcome.placed() || outcome.scheduled() ? FireResult.FIRED : FireResult.NO_SPOT;
	}
}
