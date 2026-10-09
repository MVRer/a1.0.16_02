package com.forzacode.a1016_02.atmosphere.card;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.atmosphere.Gates;
import com.forzacode.a1016_02.atmosphere.WorldScan;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * One block missing: a single block of the player's house wall is gone, a 1x1 window looking out at the fog.
 * Removed from outside, never the roof; see {@link WorldScan#wallSpot}. Prefers eye-level blocks.
 */
public final class OneBlockMissingCard extends AtmosphereCard {
	public static final String ID = "one_block_missing";

	public OneBlockMissingCard() {
		super(ID, Tier.MINOR, Stage.PROXIMITY, Set.of(Habit.VISITOR, Habit.WATCHER), Set.of(CardTag.SCAR), false);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		Optional<GlobalPos> base = Services.watch().base(player);
		if (base.isEmpty()) {
			return false;
		}
		boolean sameDimension = base.get().dimension() == world.dimension();
		return Gates.away(sameDimension, sameDimension ? player.blockPosition().distSqr(base.get().pos()) : 0, cfg().houseAwayBlocks);
	}

	@Override
	public FireResult fire(FireContext ctx) {
		Optional<GlobalPos> base = Services.watch().base(ctx.player());
		if (base.isEmpty()) {
			return FireResult.SKIPPED;
		}
		ServerLevel level = ctx.level().getServer().getLevel(base.get().dimension());
		if (level == null) {
			return FireResult.SKIPPED;
		}
		// Never load the base's chunks just to look: if they are not loaded, try again later.
		if (!WorldScan.areaLoaded(level, base.get().pos(), cfg().houseRadius + 1)) {
			return FireResult.NO_SPOT;
		}
		List<WorldScan.WallSpot> spots = WorldScan.houseWalls(level, base.get().pos(), cfg().houseRadius);
		List<WorldScan.WallSpot> eye = new ArrayList<>(spots.stream().filter(WorldScan.WallSpot::eyeLevel).toList());
		List<WorldScan.WallSpot> rest = new ArrayList<>(spots.stream().filter(s -> !s.eyeLevel()).toList());
		for (List<WorldScan.WallSpot> pool : List.of(eye, rest)) {
			while (!pool.isEmpty()) {
				WorldScan.WallSpot spot = pool.remove(ctx.random().nextInt(pool.size()));
				if (Services.traces().remove(level, spot.pos(), cause())) {
					return FireResult.FIRED;
				}
			}
		}
		return FireResult.NO_SPOT;
	}
}
