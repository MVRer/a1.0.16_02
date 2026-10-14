package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * "Torches gone" and "Torches behind you". The caves are any participant's (the explored points dig keeps for
 * everyone), base lights are any known player's, and a torch only goes where every participant has left it: one
 * friend still in the cave keeps its torches lit.
 */
final class TorchCards {
	static final String CAUSE_GONE = "dig:torches_gone";

	private TorchCards() {
	}

	/** Torches removed from a cave someone lit and everyone has left. The blocks they hung on stay. */
	static final class TorchesGone extends DigCard {
		static final String ID = "torches_gone";

		TorchesGone() {
			super(ID, Tier.MINOR, Stage.PROXIMITY, Set.of(Habit.STRIPPER), Set.of(CardTag.LIGHT), false);
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			return !data(world).explored(world.dimension()).isEmpty()
					&& Services.watch().ticksSinceCombat(player) > ModConfig.realTicks(DigConfig.get().torchesGoneNoCombatSeconds);
		}

		@Override
		public FireResult fire(FireContext ctx) {
			return removeFromLeftCave(ctx.level(), ctx.player(), ctx.random()) > 0 ? FireResult.FIRED : FireResult.NO_SPOT;
		}
	}

	/** {@link #caveTorchAwayFrom} with one base (or none). */
	static boolean caveTorch(ServerLevel level, BlockPos pos, @Nullable BlockPos base) {
		return caveTorchAwayFrom(level, pos, base == null ? List.of() : List.of(base));
	}

	/**
	 * A torch a player lit a cave with: underground (no sky access, well below the surface), hanging on or standing
	 * on natural ground (not a wall or floor they built), and outside the base radius of every one of {@code bases}.
	 * Base lights never count, a friend's base included.
	 */
	static boolean caveTorchAwayFrom(ServerLevel level, BlockPos pos, List<BlockPos> bases) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (!Tunnels.isTorch(state) || !DigTicker.underground(level, pos)) {
			return false;
		}
		int home = DigConfig.get().homeRadius;
		for (BlockPos base : bases) {
			if (DigTicker.horizontalDistSqr(pos, base) <= (long) home * home) {
				return false;
			}
		}
		BlockPos support = state.getBlock() instanceof WallTorchBlock ? pos.relative(state.getValue(WallTorchBlock.FACING).getOpposite()) : pos.below();
		return !Services.watch().wasPlacedByPlayer(level, support);
	}

	/** The players' cave torches within {@code radius} of {@code center} (none if those chunks are not loaded). */
	static List<BlockPos> caveTorches(ServerLevel level, BlockPos center, int radius, List<BlockPos> bases) {
		List<BlockPos> torches = new ArrayList<>();
		if (!Tunnels.chunksLoaded(level, center, radius)) {
			return torches;
		}
		for (BlockPos torch : Services.watch().placedNear(level, center, radius, Tunnels::isTorch)) {
			if (caveTorchAwayFrom(level, torch, bases)) {
				torches.add(torch);
			}
		}
		return torches;
	}

	/** {@link #removeFromLeftCave(ServerLevel, ServerPlayer, List, List, RandomSource)} for everyone in the live world. */
	static int removeFromLeftCave(ServerLevel level, ServerPlayer player, RandomSource random) {
		return removeFromLeftCave(level, player, DigCard.everyone(level), DigCard.bases(level), random);
	}

	/**
	 * Finds a cave someone explored and lit, at least {@link DigConfig#torchesGoneMinDistance} from {@code player} and
	 * from every one of {@code everyone} (all have left it), outside every one of {@code bases}, and removes up to
	 * {@link DigConfig#torchesGoneMax} of its torches, each out of view. Returns how many went.
	 */
	static int removeFromLeftCave(ServerLevel level, ServerPlayer player, List<BlockPos> everyone, List<BlockPos> bases, RandomSource random) {
		DigConfig config = DigConfig.get();
		BlockPos here = player.blockPosition();
		// Only loaded places can change; the view distance bounds them (at least twice the minimum distance).
		int maxReach = Math.max(config.torchesGoneMinDistance * 2, (level.getServer().getPlayerList().getViewDistance() + 1) * 16);
		long minSqr = (long) config.torchesGoneMinDistance * config.torchesGoneMinDistance;
		List<BlockPos> centers = new ArrayList<>();
		DigData.get(level.getServer()).explored(level.dimension()).forEachNear(here, maxReach, packed -> {
			BlockPos pos = BlockPos.of(packed);
			if (pos.distSqr(here) >= minSqr && level.isLoaded(pos)) {
				centers.add(pos);
			}
		});
		NetworkGrower.shuffle(centers, random);
		for (BlockPos center : centers.subList(0, Math.min(48, centers.size()))) {
			List<BlockPos> torches = new ArrayList<>(caveTorches(level, center, config.torchesGoneSearchRadius, bases));
			torches.removeIf(torch -> torch.distSqr(here) < minSqr || everyone.stream().anyMatch(p -> torch.distSqr(p) < minSqr));
			if (torches.size() < 2) {
				continue;
			}
			int removed = 0;
			for (BlockPos torch : torches) {
				if (removed >= config.torchesGoneMax) {
					break;
				}
				if (Services.traces().remove(level, torch, CAUSE_GONE)) {
					removed++;
				}
			}
			if (removed > 0) {
				return removed;
			}
		}
		return 0;
	}

	/**
	 * Deep in a lit cave, the torches behind the player go out one by one once they are 40+ blocks past them (and so
	 * is every other participant: a friend lagging behind keeps the lights around them). One follower per player.
	 */
	static final class TorchesBehindYou extends DigCard {
		static final String ID = "torches_behind_you";

		TorchesBehindYou() {
			super(ID, Tier.MAJOR, Stage.PROXIMITY, Set.of(Habit.WATCHER, Habit.STRIPPER), Set.of(CardTag.LIGHT), false);
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			return !DigTicker.INSTANCE.torchSessionActive(player.getUUID()) && DigTicker.underground(world, player.blockPosition())
					&& caveTorches(world, player.blockPosition(), 64, bases(world)).size() >= DigConfig.get().torchesBehindMinTorches;
		}

		@Override
		public FireResult fire(FireContext ctx) {
			List<BlockPos> torches = caveTorches(ctx.level(), ctx.player().blockPosition(), 96, bases(ctx.level()));
			if (torches.size() < 2) {
				return FireResult.NO_SPOT;
			}
			DigTicker.INSTANCE.startTorchSession(ctx.player(), torches);
			return FireResult.FIRED;
		}
	}
}
