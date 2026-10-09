package com.forzacode.a1016_02.ending;

import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * "Missing first block" (Event catalog: Signature, Removal, Ending B path). Lore's F13 already takes the first
 * crafting table, chest or block to its cairn in Stage 3 on every path, so this card never takes anything itself: it
 * is registered so the catalog is complete, its context only fits on the Ending B path while F13 has not taken the
 * block, and it always returns {@link FireResult#SKIPPED} (the block is F13's to take).
 */
final class MissingFirstBlockCard implements EventCard {
	static final String ID = "missing_first_block";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Tier tier() {
		return Tier.SIGNATURE;
	}

	@Override
	public Stage earliestStage() {
		return Stage.REMOVAL;
	}

	@Override
	public Set<Habit> habits() {
		return Set.of();
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.ITEM);
	}

	@Override
	public boolean hasFake() {
		return false;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return EndingState.get(world.getServer()).path() == EndingPath.B && !takenByF13(HerobrineState.get(world.getServer()), world);
	}

	@Override
	public FireResult fire(FireContext ctx) {
		boolean taken = takenByF13(HerobrineState.get(ctx.level().getServer()), ctx.level());
		A1016_02.LOGGER.debug("[a1016] ending: missing_first_block skipped ({})", taken ? "F13 already took it" : "left for F13 to take");
		return FireResult.SKIPPED;
	}

	/** F13's cairn is placed (it holds the first block), or no first block is where it was put any more. */
	static boolean takenByF13(HerobrineState state, ServerLevel level) {
		if (state.fragmentsPlaced().containsKey("F13")) {
			return true;
		}
		HerobrineState.FirstBlocks first = state.firstBlocks();
		for (PlacedBlock block : new PlacedBlock[] {first.craftingTable(), first.chest(), first.block()}) {
			if (block != null && block.pos().dimension().equals(level.dimension()) && level.isLoaded(block.pos().pos())
					&& level.getBlockState(block.pos().pos()).is(block.state().getBlock())) {
				return false;
			}
		}
		return true;
	}
}
