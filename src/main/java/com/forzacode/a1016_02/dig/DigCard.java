package com.forzacode.a1016_02.dig;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared plumbing for the dig cards. A card fires for one player (the one whose pacing picked it, D-059); what it may
 * touch is checked against everyone: every participant's position and every known player's base.
 */
abstract class DigCard implements EventCard {
	/** A network cell under a player, and its network. */
	record Below(Network net, BlockPos cell) {
	}

	private final String id;
	private final Tier tier;
	private final Stage earliest;
	private final Set<Habit> habits;
	private final Set<CardTag> tags;
	private final boolean fake;

	DigCard(String id, Tier tier, Stage earliest, Set<Habit> habits, Set<CardTag> tags, boolean fake) {
		this.id = id;
		this.tier = tier;
		this.earliest = earliest;
		this.habits = Set.copyOf(habits);
		this.tags = Set.copyOf(tags);
		this.fake = fake;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public Tier tier() {
		return tier;
	}

	@Override
	public Stage earliestStage() {
		return earliest;
	}

	@Override
	public Set<Habit> habits() {
		return habits;
	}

	@Override
	public Set<CardTag> tags() {
		return tags;
	}

	@Override
	public boolean hasFake() {
		return fake;
	}

	static DigData data(ServerLevel level) {
		return DigData.get(level.getServer());
	}

	/** The player's base in this level, if any. */
	static Optional<BlockPos> base(ServerPlayer player) {
		return Services.watch().base(player).filter(pos -> pos.dimension().equals(player.level().dimension())).map(GlobalPos::pos);
	}

	/** Every known player's base in this level (offline friends' too). */
	static List<BlockPos> bases(ServerLevel level) {
		return Bases.live(level.getServer()).all(level.dimension());
	}

	/** Where every participant in this level stands now. */
	static List<BlockPos> everyone(ServerLevel level) {
		return Bases.live(level.getServer()).positionsIn(level);
	}

	/** The network under this player's base (the one its base shares), if there is one. */
	static Optional<Network> networkOf(ServerPlayer player) {
		return base(player).map(base -> data(player.level()).networkFor(player.level().dimension(), base, DigConfig.get().networkShareDistance));
	}

	/** Near the base (horizontally within {@link DigConfig#homeRadius}, and not far above or below). */
	static boolean atHome(ServerPlayer player) {
		Optional<BlockPos> base = base(player);
		if (base.isEmpty()) {
			return false;
		}
		int radius = DigConfig.get().homeRadius;
		BlockPos pos = player.blockPosition();
		return DigTicker.horizontalDistSqr(pos, base.get()) <= (long) radius * radius && Math.abs(pos.getY() - base.get().getY()) <= 12;
	}

	/**
	 * The network cell under the player closest to them (at least 4 below, within the home cue reach), in whichever
	 * network runs there (a friend's base's included), with that network.
	 */
	static Optional<Below> networkCellBelow(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		DigConfig config = DigConfig.get();
		int reach = config.homeCueReach;
		long around = (long) (config.networkRadius + reach + 16) * (config.networkRadius + reach + 16);
		Below best = null;
		double bestDist = Double.MAX_VALUE;
		for (Network net : data(level).networks) {
			if (!net.dimension.equals(level.dimension()) || DigTicker.horizontalDistSqr(net.base, feet) > around) {
				continue;
			}
			for (long packed : net.cells) {
				BlockPos cell = BlockPos.of(packed);
				if (cell.getY() > feet.getY() - 4) {
					continue;
				}
				double dist = cell.distSqr(feet);
				if (dist < bestDist && dist <= (double) reach * reach) {
					bestDist = dist;
					best = new Below(net, cell);
				}
			}
		}
		return Optional.ofNullable(best);
	}
}
