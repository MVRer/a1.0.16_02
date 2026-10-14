package com.forzacode.a1016_02.ending;

import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * "Missing first block" (Event catalog: Signature, Removal, Ending B path). Lore's F13 already takes the first
 * crafting table, chest or block to its cairn in Stage 3 on every path, so this card never takes anything itself: it
 * is registered so the catalog is complete, its context only fits on the Ending B path while F13 has not taken the
 * block, and it always returns {@link FireResult#SKIPPED} (the block is F13's to take). The first block is one
 * player's, the {@code story:first_blocks} owner's (D-061), whoever the card is checked for.
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
		MinecraftServer server = world.getServer();
		return EndingState.get(server).path() == EndingPath.B && !takenByF13(HerobrineState.get(server), world, owner(server));
	}

	@Override
	public FireResult fire(FireContext ctx) {
		MinecraftServer server = ctx.level().getServer();
		boolean taken = takenByF13(HerobrineState.get(server), ctx.level(), owner(server));
		A1016_02.LOGGER.debug("[a1016] ending: missing_first_block skipped ({})", taken ? "F13 already took it" : "left for F13 to take");
		return FireResult.SKIPPED;
	}

	/** Whose first blocks the story uses ({@code story:first_blocks}, D-061: the same owner as lore's F13 and F10). */
	private static @Nullable UUID owner(MinecraftServer server) {
		return Services.watch().ownerFor(server, EndingApi.FIRST_BLOCKS_OWNER).orElse(null);
	}

	/**
	 * F13's cairn is placed (it holds the first block), or none of {@code owner}'s first blocks is where it was put any
	 * more (no owner: nothing to take).
	 */
	static boolean takenByF13(HerobrineState state, ServerLevel level, @Nullable UUID owner) {
		if (state.fragmentsPlaced().containsKey("F13") || owner == null) {
			return true;
		}
		HerobrineState.FirstBlocks first = state.firstBlocks(owner);
		for (PlacedBlock block : new PlacedBlock[] {first.craftingTable(), first.chest(), first.block()}) {
			if (block != null && block.pos().dimension().equals(level.dimension()) && level.isLoaded(block.pos().pos())
					&& level.getBlockState(block.pos().pos()).is(block.state().getBlock())) {
				return false;
			}
		}
		return true;
	}
}
