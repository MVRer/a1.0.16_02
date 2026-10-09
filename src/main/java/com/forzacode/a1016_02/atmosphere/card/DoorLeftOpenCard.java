package com.forzacode.a1016_02.atmosphere.card;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Gates;
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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Door left open: a wooden door the player placed (and that is closed now) is open when they come back. Only while
 * they are away, out of view, silently.
 */
public final class DoorLeftOpenCard extends AtmosphereCard {
	public static final String ID = "door_left_open";

	public DoorLeftOpenCard() {
		super(ID, Tier.MINOR, Stage.PROXIMITY, Set.of(Habit.VISITOR), Set.of(CardTag.SCAR), false);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		Optional<GlobalPos> base = Services.watch().base(player);
		if (base.isEmpty()) {
			return false;
		}
		boolean sameDimension = base.get().dimension() == world.dimension();
		return Gates.away(sameDimension, sameDimension ? player.blockPosition().distSqr(base.get().pos()) : 0, cfg().doorAwayBlocks);
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
		if (!WorldScan.areaLoaded(level, base.get().pos(), cfg().doorSearchRadius + 1)) {
			return FireResult.NO_SPOT;
		}
		List<BlockPos> doors = new ArrayList<>(closedPlacedDoors(level, base.get().pos(), cfg().doorSearchRadius));
		if (doors.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		AtmosphereConfig cfg = cfg();
		while (!doors.isEmpty()) {
			BlockPos lower = doors.remove(ctx.random().nextInt(doors.size()));
			if (!farFromEveryone(level, lower, cfg.doorAwayBlocks)) {
				continue;
			}
			BlockState lowerState = level.getBlockState(lower);
			BlockState upperState = level.getBlockState(lower.above());
			if (Services.traces().batch(level, cause())
					.convert(lower, lowerState.setValue(DoorBlock.OPEN, true))
					.convert(lower.above(), upperState.setValue(DoorBlock.OPEN, true))
					.commit()) {
				return FireResult.FIRED;
			}
		}
		return FireResult.NO_SPOT;
	}

	/** Lower halves of hand-opened doors a player placed near {@code center} that are closed now. Empty if the area is not loaded. */
	public static List<BlockPos> closedPlacedDoors(ServerLevel level, BlockPos center, int radius) {
		List<BlockPos> doors = new ArrayList<>();
		if (!WorldScan.areaLoaded(level, center, radius + 1)) {
			return doors;
		}
		for (BlockPos pos : Services.watch().placedNear(level, center, radius, DoorLeftOpenCard::isClosedLowerDoor)) {
			BlockState upper = level.getBlockState(pos.above());
			if (upper.getBlock() == level.getBlockState(pos).getBlock() && upper.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
				doors.add(pos);
			}
		}
		return doors;
	}

	static boolean isClosedLowerDoor(BlockState state) {
		return state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand()
				&& state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && !state.getValue(DoorBlock.OPEN);
	}

	private static boolean farFromEveryone(ServerLevel level, BlockPos pos, int blocks) {
		double r2 = (double) blocks * blocks;
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(pos) < r2) {
				return false;
			}
		}
		return true;
	}
}
