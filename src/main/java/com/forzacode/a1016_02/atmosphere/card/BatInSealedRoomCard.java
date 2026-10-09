package com.forzacode.a1016_02.atmosphere.card;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.WorldScan;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ambient.Bat;

/**
 * A bat in a sealed room: an existing bat is moved, out of view, into a room of the player's house that has no
 * openings at all (closed space bounded by full blocks; a door or pane counts as an opening).
 */
public final class BatInSealedRoomCard extends AtmosphereCard {
	public static final String ID = "bat_in_sealed_room";

	public BatInSealedRoomCard() {
		super(ID, Tier.MINOR, Stage.PROXIMITY, Set.of(Habit.VISITOR), Set.of(CardTag.MOB), false);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		Optional<GlobalPos> base = Services.watch().base(player);
		return base.isPresent() && base.get().dimension() == world.dimension() && !bats(world, player).isEmpty();
	}

	private static List<Bat> bats(ServerLevel level, ServerPlayer player) {
		return untampered(level, Bat.class, player.position(), cfg().batRadius, bat -> !bat.isLeashed() && !bat.isPassenger());
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		Optional<GlobalPos> base = Services.watch().base(player);
		if (base.isEmpty() || base.get().dimension() != level.dimension()) {
			return FireResult.SKIPPED;
		}
		AtmosphereConfig cfg = cfg();
		// Never load the base's chunks just to look: if they are not loaded, try again later.
		if (!WorldScan.areaLoaded(level, base.get().pos(), cfg.roomSearchRadius + 1)) {
			return FireResult.NO_SPOT;
		}
		List<Bat> bats = bats(level, player).stream()
				.filter(bat -> Services.traces().isOutOfView(level, bat.getBoundingBox()))
				.sorted(Comparator.comparingDouble(bat -> bat.distanceToSqr(player)))
				.toList();
		if (bats.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		for (List<BlockPos> room : WorldScan.sealedRoomsNear(level, base.get().pos(), cfg.roomSearchRadius, cfg.roomMaxCells, 4)) {
			List<BlockPos> cells = new ArrayList<>(room.stream().filter(p -> level.getBlockState(p).isAir() && level.getBlockState(p.below()).isAir()).toList());
			if (cells.isEmpty()) {
				cells = new ArrayList<>(room.stream().filter(p -> level.getBlockState(p).isAir()).toList());
			}
			while (!cells.isEmpty()) {
				BlockPos cell = cells.remove(ctx.random().nextInt(cells.size()));
				for (Bat bat : bats) {
					if (mobs().moveOutOfView(bat, cell)) {
						return FireResult.FIRED;
					}
				}
				if (cells.size() > 8) {
					cells = new ArrayList<>(cells.subList(0, 8));
				}
			}
		}
		return FireResult.NO_SPOT;
	}
}
