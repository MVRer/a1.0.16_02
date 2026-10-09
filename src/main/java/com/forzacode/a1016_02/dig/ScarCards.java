package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/** Dig cards that leave a scar: the tunnel that grows, the tunnel into the player's mine, stripped trees. */
final class ScarCards {
	private ScarCards() {
	}

	/** A 2x2 tunnel pointing at the base, about 10 blocks longer each visit; it ends just short of the base. */
	static final class TunnelThatGrows extends DigCard {
		static final String ID = "tunnel_that_grows";

		TunnelThatGrows() {
			super(ID, Tier.MAJOR, Stage.PROXIMITY, Set.of(Habit.CARVER, Habit.VISITOR), Set.of(CardTag.DIG, CardTag.SCAR), false);
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			return data(world).growing == null && base(player).isPresent();
		}

		@Override
		public FireResult fire(FireContext ctx) {
			return startOrGrow(ctx.level(), ctx.player(), ctx.random(), ctx.forced(), Services.traces()) != null ? FireResult.FIRED : FireResult.NO_SPOT;
		}
	}

	/**
	 * Starts the tunnel that grows if there is none, otherwise grows it by one visit's length (only after a visit,
	 * unless {@code force}). Returns the tunnel if something was carved.
	 */
	static @Nullable GrowingTunnel startOrGrow(ServerLevel level, ServerPlayer player, RandomSource random, boolean force, TraceService traces) {
		DigData data = DigCard.data(level);
		DigConfig config = DigConfig.get();
		int growth = ModConfig.pacing().tunnelGrowthPerVisit;
		long day = GameClock.day(level.getServer());
		GrowingTunnel tunnel = data.growing;
		if (tunnel == null) {
			Optional<BlockPos> base = DigCard.base(player);
			if (base.isEmpty()) {
				return null;
			}
			tunnel = GrowingTunnel.start(level, base.get(), data.explored(level.dimension()), config, config.tunnelPlayerClearance, growth, random, traces, day);
			if (tunnel != null) {
				data.setGrowing(tunnel);
			}
			return tunnel;
		}
		if (tunnel.complete || !tunnel.dimension.equals(level.dimension()) || !(force || tunnel.visited)) {
			return null;
		}
		boolean wasComplete = tunnel.complete;
		boolean grown = tunnel.grow(level, growth, config.tunnelPlayerClearance, config, traces, day);
		if (grown) {
			tunnel.visited = false;
			data.changed();
		} else if (tunnel.complete != wasComplete) {
			data.setDirty();
		}
		return grown ? tunnel : null;
	}

	/** A 2x2 tunnel breaks through the wall of a tunnel the player dug, leads away, and ends in stone. */
	static final class TunnelIntoMine extends DigCard {
		static final String ID = "tunnel_into_mine";

		TunnelIntoMine() {
			super(ID, Tier.MAJOR, Stage.PROXIMITY, Set.of(Habit.CARVER), Set.of(CardTag.DIG, CardTag.SCAR), false);
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			return !Services.watch().dugNear(world, player.blockPosition(), 96).isEmpty();
		}

		@Override
		public FireResult fire(FireContext ctx) {
			return intoMine(ctx.level(), ctx.player(), ctx.random(), Services.traces()) != null ? FireResult.FIRED : FireResult.NO_SPOT;
		}
	}

	static CardTunnels.@Nullable Carved intoMine(ServerLevel level, ServerPlayer player, RandomSource random, TraceService traces) {
		DigConfig config = DigConfig.get();
		CardTunnels.Carved carved = CardTunnels.intoMine(level, player.blockPosition(), config, config.tunnelPlayerClearance, random, traces);
		if (carved != null) {
			DigCard.data(level).addTunnel(new DigData.CardTunnel(level.dimension(), carved.anchors(), ID_INTO_MINE, carved.siteId()));
		}
		return carved;
	}

	static final String ID_INTO_MINE = "tunnel_into_mine";

	/** Leaves gone from trees the player planted (saplings they placed that grew), only while they are away. */
	static final class TreesStripped extends DigCard {
		static final String ID = "trees_stripped";

		TreesStripped() {
			super(ID, Tier.MAJOR, Stage.PROXIMITY, Set.of(Habit.STRIPPER), Set.of(CardTag.SCAR), false);
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			return !data(world).planted(world.dimension()).isEmpty();
		}

		@Override
		public FireResult fire(FireContext ctx) {
			return stripPlanted(ctx.level(), ctx.player().blockPosition(), ctx.random(), Services.traces()) > 0 ? FireResult.FIRED : FireResult.NO_SPOT;
		}
	}

	/**
	 * Strips up to {@link DigConfig#treesPerFire} grown planted trees at least {@link DigConfig#treesAwayDistance}
	 * from {@code playerPos} (one, then the ones near it). Returns how many were stripped.
	 */
	static int stripPlanted(ServerLevel level, BlockPos playerPos, RandomSource random, TraceService traces) {
		DigData data = DigCard.data(level);
		DigConfig config = DigConfig.get();
		PosSet planted = data.planted(level.dimension());
		long awaySqr = (long) config.treesAwayDistance * config.treesAwayDistance;
		List<BlockPos> grown = new ArrayList<>();
		List<Long> dead = new ArrayList<>();
		planted.forEach(packed -> {
			BlockPos pos = BlockPos.of(packed);
			if (!level.isLoaded(pos)) {
				return;
			}
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.LOGS)) {
				if (pos.distSqr(playerPos) >= awaySqr) {
					grown.add(pos);
				}
			} else if (!state.is(BlockTags.SAPLINGS)) {
				dead.add(packed);
			}
		});
		dead.forEach(planted::remove);
		if (grown.isEmpty()) {
			if (!dead.isEmpty()) {
				data.setDirty();
			}
			return 0;
		}
		NetworkGrower.shuffle(grown, random);
		BlockPos first = grown.getFirst();
		grown.sort((a, b) -> Double.compare(a.distSqr(first), b.distSqr(first)));
		int stripped = 0;
		for (BlockPos root : grown) {
			if (stripped >= config.treesPerFire || stripped > 0 && root.distSqr(first) > 32 * 32) {
				break;
			}
			TreeStripper.Tree tree = TreeStripper.find(level, root);
			if (tree != null && TreeStripper.strip(level, tree, traces)) {
				planted.remove(root.asLong());
				stripped++;
			}
		}
		if (stripped > 0 || !dead.isEmpty()) {
			data.setDirty();
		}
		return stripped;
	}
}
