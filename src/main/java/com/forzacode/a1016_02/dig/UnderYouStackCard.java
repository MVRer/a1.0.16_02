package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;

import org.jspecify.annotations.Nullable;

/**
 * "Under you" home cue: a chest in the base is one stack short. The stack is moved into the chest in the network
 * ({@code TraceService.moveStack}); while the network has no chest yet, it is taken out and kept in the ledger, and
 * once the chest is in, those stacks are moved into it a few per night ({@link NetworkChest#restoreLedgered}). Only
 * ordinary stackable items, never tools or gear.
 */
final class UnderYouStackCard extends DigCard {
	static final String ID = "under_you_stack";
	static final String CAUSE = "dig:under_you/stack";

	UnderYouStackCard() {
		super(ID, Tier.MINOR, Stage.PROXIMITY, Set.of(Habit.COLLECTOR), Set.of(CardTag.ITEM), false);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		Optional<BlockPos> base = base(player);
		return base.isPresent() && data(world).network().isPresent() && baseLoaded(world, base.get()) && !playerChests(world, base.get()).isEmpty();
	}

	@Override
	public FireResult fire(FireContext ctx) {
		Optional<BlockPos> base = base(ctx.player());
		if (base.isEmpty() || !baseLoaded(ctx.level(), base.get())) {
			return FireResult.NO_SPOT;
		}
		Network net = data(ctx.level()).network().filter(n -> n.dimension.equals(ctx.level().dimension())).orElse(null);
		boolean taken = takeStack(ctx.level(), base.get(), net, ctx.random(), Services.traces());
		if (taken) {
			data(ctx.level()).setDirty();
		}
		return taken ? FireResult.FIRED : FireResult.NO_SPOT;
	}

	/** The chests near the base can only be looked at while their chunks are loaded (never load them for this). */
	static boolean baseLoaded(ServerLevel level, BlockPos base) {
		return Tunnels.chunksLoaded(level, base, ModConfig.pacing().chestRadius);
	}

	/** The player's chests near the base. Check {@link #baseLoaded} first. */
	static List<BlockPos> playerChests(ServerLevel level, BlockPos base) {
		return Services.watch().placedNear(level, base, ModConfig.pacing().chestRadius, state -> state.getBlock() instanceof ChestBlock);
	}

	/**
	 * Takes one stack from one of the player's chests near {@code base}: into the network's chest if it has one,
	 * otherwise into the ledger. True if a stack went.
	 */
	static boolean takeStack(ServerLevel level, BlockPos base, @Nullable Network net, RandomSource random, TraceService traces) {
		if (!baseLoaded(level, base)) {
			return false;
		}
		BlockPos into = net != null && net.chest != null && level.isLoaded(net.chest) && level.getBlockEntity(net.chest) instanceof Container ? net.chest
				: null;
		List<BlockPos> chests = new ArrayList<>(playerChests(level, base));
		NetworkGrower.shuffle(chests, random);
		for (BlockPos chest : chests) {
			if (!(level.getBlockEntity(chest) instanceof Container container)) {
				continue;
			}
			List<Integer> slots = new ArrayList<>();
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				ItemStack stack = container.getItem(slot);
				if (!stack.isEmpty() && stack.isStackable()) {
					slots.add(slot);
				}
			}
			if (slots.isEmpty()) {
				continue;
			}
			int slot = slots.get(random.nextInt(slots.size()));
			boolean taken = into != null ? traces.moveStack(level, chest, slot, into, CAUSE)
					: traces.removeStack(level, chest, slot, container.getItem(slot).getCount(), CAUSE);
			if (taken) {
				if (net != null) {
					if (into != null) {
						net.stacksMoved++;
					} else {
						net.stacksLedgered++;
					}
				}
				return true;
			}
		}
		return false;
	}
}
