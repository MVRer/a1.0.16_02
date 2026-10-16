package com.forzacode.a1016_02.atmosphere.card;

import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code sc_05b} (docs/SELF_CREATED.md 05; AMBIENT, Traces, MOB, WATCHER + VISITOR, fake): fits while the player has
 * been away from a base with enough animals long enough ({@link ComingHome#refusal}); the fire arms them and the
 * approach watcher holds the animals when they come back. A shared moment: the animals are bound to everyone sharing it.
 */
public final class TheyWereWaitingCard extends AtmosphereCard {
	public static final String ID = ComingHome.WAITING_ID;

	ComingHome home = ComingHome.LIVE;

	public TheyWereWaitingCard() {
		super(ID, Tier.AMBIENT, Stage.TRACES, Set.of(Habit.WATCHER, Habit.VISITOR), Set.of(CardTag.MOB), true);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return home.refusal(player, ComingHome.Kind.WAITING).isEmpty();
	}

	@Override
	public FireResult fire(FireContext ctx) {
		return SomeoneHomeCard.fire(home, ctx, ComingHome.Kind.WAITING);
	}
}
