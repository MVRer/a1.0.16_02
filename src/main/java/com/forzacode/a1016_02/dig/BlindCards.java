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
 * Blind moments' three cards (D-077, {@link Blind}): for one player alone ({@link CardTag#PERSONAL}) and only while they
 * have a menu open ({@link CardTag#BLIND}), no habits. The context check is the gates and a spot; the fire checks the
 * gates again and does it.
 */
final class BlindCards {
	private BlindCards() {
	}

	/** A blind card. */
	abstract static class BlindCard extends DigCard {
		final Blind.Kind kind;

		BlindCard(Blind.Kind kind, Tier tier, Stage earliest, Set<CardTag> tags, boolean fake) {
			super(kind.id, tier, earliest, Set.of(), tags, fake);
			this.kind = kind;
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			return Blind.check(kind, player, Blind.Env.live(world.getServer())) == null;
		}

		@Override
		public FireResult fire(FireContext ctx) {
			return Blind.fire(kind, ctx, Blind.Env.live(ctx.level().getServer()));
		}
	}

	/** One or two footsteps 3 to 5 blocks behind the player. Fake: their own step, late. */
	static final class BlindStep extends BlindCard {
		static final String ID = "blind_step";

		BlindStep() {
			super(Blind.Kind.STEP, Tier.AMBIENT, Stage.ALONE, Set.of(CardTag.SOUND, CardTag.PERSONAL, CardTag.BLIND), true);
		}
	}

	/** A torch or lantern a player placed, 6 to 20 blocks away and out of everyone's view, is gone. No fake. */
	static final class BlindTorch extends BlindCard {
		static final String ID = "blind_torch";

		BlindTorch() {
			super(Blind.Kind.TORCH, Tier.MINOR, Stage.TRACES, Set.of(CardTag.LIGHT, CardTag.PERSONAL, CardTag.BLIND), false);
		}
	}

	/** A door, trapdoor or gate 4 to 12 blocks away and out of view opens and closes, by its sounds only. No fake. */
	static final class BlindDoor extends BlindCard {
		static final String ID = "blind_door";

		BlindDoor() {
			super(Blind.Kind.DOOR, Tier.MINOR, Stage.TRACES, Set.of(CardTag.SOUND, CardTag.PERSONAL, CardTag.BLIND), false);
		}
	}
}
