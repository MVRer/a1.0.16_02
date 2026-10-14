package com.forzacode.a1016_02.entity;

import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** One sighting variant as an event card ({@code sighting_<variant>}). Weighted toward the WATCHER habit. */
final class SightingCard implements EventCard {
	private final Variant variant;

	SightingCard(Variant variant) {
		this.variant = variant;
	}

	@Override
	public String id() {
		return variant.cardId();
	}

	@Override
	public Tier tier() {
		return variant.tier();
	}

	@Override
	public Stage earliestStage() {
		return variant.earliestStage();
	}

	@Override
	public Set<Habit> habits() {
		return Set.of(Habit.WATCHER);
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.SIGHTING);
	}

	@Override
	public boolean hasFake() {
		return variant.fake() != Variant.Fake.NONE;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return SightingGates.check(player, world, variant).isEmpty();
	}

	/**
	 * Fires for the player the director picked, in their own level. While he is already out (for anyone, in any
	 * dimension) a real sighting is {@link FireResult#NO_SPOT}: he is one being, and the director keeps the card for
	 * later. A debug fire tells him to walk away first ({@link FigureApi#spawnAtFogEdge}), and is refused the same.
	 */
	@Override
	public FireResult fire(FireContext ctx) {
		if (!ctx.fake() && !ctx.forced() && FigureApi.anyOut(ctx.level().getServer())) {
			return FireResult.NO_SPOT;
		}
		if (!ctx.forced()) {
			if (!contextFits(ctx.player(), ctx.level())) {
				return FireResult.SKIPPED;
			}
			if (ctx.random().nextDouble() >= stageChance(HerobrineState.get(ctx.level().getServer()).stage())) {
				return FireResult.SKIPPED;
			}
		}
		if (ctx.fake()) {
			return Fakes.fire(ctx.player(), variant.fake());
		}
		return FigureApi.spawnAtFogEdge(ctx.player(), variant, ctx.random(), ctx.forced()).result();
	}

	/** Alone: a tiny chance. Telling and after: almost none. Ending A's last one always goes ahead. */
	private double stageChance(Stage stage) {
		if (variant == Variant.LAST_ONE) {
			return 1.0;
		}
		EntityConfig config = EntityConfig.get();
		return switch (stage) {
			case ALONE -> config.aloneChance;
			case TELLING, REMOVAL -> config.tellingChance;
			default -> 1.0;
		};
	}
}
