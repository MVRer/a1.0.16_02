package com.forzacode.a1016_02.dig;

import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The tunnel ladder's three cards (D-072, {@link Ladder}): sounds only, for one player alone ({@link CardTag#PERSONAL},
 * D-074), no habits. The context check is the ladder's gates, rung order and spacing; the fire checks again and starts
 * the steps.
 */
final class LadderCards {
	private LadderCards() {
	}

	/** A rung of the ladder as a card. */
	abstract static class StepsCard extends DigCard {
		final Ladder.Rung rung;

		StepsCard(Ladder.Rung rung, Tier tier, Stage earliest, Set<CardTag> tags, boolean fake) {
			super(rung.id, tier, earliest, Set.of(), tags, fake);
			this.rung = rung;
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			return Ladder.check(rung, player, Ladder.Env.live(world.getServer()), false).fits();
		}

		@Override
		public FireResult fire(FireContext ctx) {
			return Ladder.fire(rung, ctx, Ladder.Env.live(ctx.level().getServer()));
		}
	}

	/** Rung 1: one or two steps 10 to 12 blocks back, then nothing. Fake: the player's own step, late. */
	static final class StepsBehindOnce extends StepsCard {
		static final String ID = "steps_behind_once";

		StepsBehindOnce() {
			super(Ladder.Rung.ONCE, Tier.AMBIENT, Stage.ALONE, Set.of(CardTag.SOUND, CardTag.PERSONAL), true);
		}
	}

	/** Rung 2: steps from about 16 back closing to 4 to 6, then silence. Fake: a few steps far off going away. */
	static final class StepsBehindComing extends StepsCard {
		static final String ID = "steps_behind_coming";

		StepsBehindComing() {
			super(Ladder.Rung.COMING, Tier.MINOR, Stage.TRACES, Set.of(CardTag.SOUND, CardTag.PERSONAL), true);
		}
	}

	/** Rung 3: the steps come up to a dark spot 14 to 20 back and he is there, the glimpse (D-073). No fake; a sighting too. */
	static final class StepsBehindGlimpse extends StepsCard {
		static final String ID = "steps_behind_glimpse";

		StepsBehindGlimpse() {
			super(Ladder.Rung.GLIMPSE, Tier.MINOR, Stage.TRACES, Set.of(CardTag.SOUND, CardTag.PERSONAL, CardTag.SIGHTING), false);
		}
	}
}
