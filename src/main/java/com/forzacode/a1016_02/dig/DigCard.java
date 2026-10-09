package com.forzacode.a1016_02.dig;

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

/** Shared plumbing for the dig cards. */
abstract class DigCard implements EventCard {
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

	/** The network cell under the player closest to them (at least 4 below, within the home cue reach). */
	static Optional<BlockPos> networkCellBelow(ServerPlayer player) {
		ServerLevel level = player.level();
		Optional<Network> net = data(level).network().filter(n -> n.dimension.equals(level.dimension()));
		if (net.isEmpty()) {
			return Optional.empty();
		}
		BlockPos feet = player.blockPosition();
		int reach = DigConfig.get().homeCueReach;
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (long packed : net.get().cells) {
			BlockPos cell = BlockPos.of(packed);
			if (cell.getY() > feet.getY() - 4) {
				continue;
			}
			double dist = cell.distSqr(feet);
			if (dist < bestDist && dist <= (double) reach * reach) {
				bestDist = dist;
				best = cell;
			}
		}
		return Optional.ofNullable(best);
	}
}
