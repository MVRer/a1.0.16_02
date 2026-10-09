package com.forzacode.a1016_02.lore;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;

/**
 * What lore offers other workstreams (the director and the endings). Server thread only. Every world change goes
 * through {@code TraceService}, out of view; a call that returns false changed nothing and can be retried later.
 */
public final class LoreApi {
	private LoreApi() {
	}

	/**
	 * How many times the player told about him: signs and books that name him or were written within
	 * {@code pacing.tellingRadius} of his traces, and chat that names him. "The more you tell, the faster things
	 * go." The count is {@code HerobrineState.tellingCount()}; also mirrored in its flags as {@code lore:telling_count=<n>}.
	 */
	public static int tellingCount(MinecraftServer server) {
		return HerobrineState.get(server).tellingCount();
	}

	/** Where the "Stop." sign (F03) stands, once it fired and while it still exists. */
	public static Optional<GlobalPos> stopSign(MinecraftServer server) {
		return TellingData.get(server).stopSign();
	}

	/**
	 * Ending B: F10 gains its last line, "* removed [PLAYER NAME]" (from F10's data), wherever it is. Copies in
	 * reach change now; any other copy shows it the next time it is opened. Always true.
	 */
	public static boolean finishF10(MinecraftServer server) {
		LiveBooks.finishF10(server);
		return true;
	}

	/**
	 * Ending B only: places F20 in a chest under where F10 was placed: directly under it, or beside that spot (or
	 * one lower) if a block entity or an unbreakable block is there. Taking the block out is his edit, ledgered like
	 * any other; the chest is left by others. True once it is there (also when it already was); false if F10 was
	 * never placed, the spot is in view or its chunk is still loading.
	 */
	public static boolean placeF20(MinecraftServer server) {
		return placeF20(server, HerobrineState.get(server), Services.traces());
	}

	static boolean placeF20(MinecraftServer server, HerobrineState state, TraceService traces) {
		if (state.fragmentsPlaced().containsKey("F20")) {
			return true;
		}
		GlobalPos f10 = state.fragmentsPlaced().get("F10");
		Optional<Fragment> f20 = FragmentData.get("F20");
		if (f10 == null || f20.isEmpty()) {
			return false;
		}
		ServerLevel level = server.getLevel(f10.dimension());
		if (level == null) {
			return false;
		}
		BlockPos under = f10.pos().below();
		if (!ChunkGate.request(level, under, 2)) {
			return false;
		}
		Optional<BlockPos> spot = underSpot(level, under);
		if (spot.isEmpty()) {
			return false;
		}
		BlockPos at = spot.get();
		if (!Terrain.isAirOrReplaceable(level.getBlockState(at)) && !Build.his(traces, level, "F20").remove(at).commit()) {
			return false;
		}
		String name = state.subject().map(HerobrineState.Subject::name).orElse("Steve");
		if (!Build.left(traces, level, "F20").chest(at, Direction.NORTH, List.of(FragmentItems.book(f20.get(), name))).commit()) {
			return false;
		}
		state.setFragmentPlaced("F20", GlobalPos.of(level.dimension(), at));
		A1016_02.LOGGER.info("[a1016] lore: F20 lies under F10 at {}", at.toShortString());
		return true;
	}

	/** Directly under F10, else beside that spot, else one lower: no block entity there, and breakable. */
	static Optional<BlockPos> underSpot(ServerLevel level, BlockPos under) {
		List<BlockPos> spots = new java.util.ArrayList<>();
		spots.add(under);
		Direction.Plane.HORIZONTAL.forEach(dir -> spots.add(under.relative(dir)));
		spots.add(under.below());
		for (BlockPos spot : spots) {
			BlockState there = level.getBlockState(spot);
			if (level.getBlockEntity(spot) == null && there.getDestroySpeed(level, spot) >= 0 && !UnbreakableSigns.isProtected(level, spot)) {
				return Optional.of(spot);
			}
		}
		return Optional.empty();
	}

	/**
	 * Ending A: the "Stop." sign now stands in front of the player's cross. {@code crossPos} is the cross's bottom
	 * block (standing on the ground). A standing sign is moved onto the ground beside it, a wall sign onto the
	 * cross's side, a hanging sign from the post's side or under an arm; if the player rewrote it, "Stop." comes back
	 * (F03's own text). False if there is no "Stop." sign, it is in another dimension, no spot fits, or the move
	 * would be seen; the sign is still known then (only a broken sign is forgotten), so it can be tried again.
	 */
	public static boolean moveStopSignToCross(MinecraftServer server, GlobalPos crossPos) {
		return moveStopSignToCross(server, HerobrineState.get(server), TellingData.get(server), crossPos, Services.traces(),
				SignEdits.editor(Services.traces()));
	}

	/** {@link #moveStopSignToCross(MinecraftServer, GlobalPos)} for a cross in the overworld. */
	public static boolean moveStopSignToCross(MinecraftServer server, BlockPos crossPos) {
		return moveStopSignToCross(server, GlobalPos.of(Level.OVERWORLD, crossPos));
	}

	static boolean moveStopSignToCross(MinecraftServer server, HerobrineState state, TellingData data, GlobalPos crossPos, TraceService traces,
			SignEdits.Editor editor) {
		Optional<GlobalPos> stop = data.stopSign();
		if (stop.isEmpty() || !stop.get().dimension().equals(crossPos.dimension())) {
			return false;
		}
		ServerLevel level = server.getLevel(crossPos.dimension());
		if (level == null) {
			return false;
		}
		BlockPos from = stop.get().pos();
		BlockPos cross = crossPos.pos();
		if (!ChunkGate.request(level, from, 0) || !ChunkGate.request(level, cross, 2)) {
			return false;
		}
		BlockState sign = level.getBlockState(from);
		if (!(level.getBlockEntity(from) instanceof SignBlockEntity)) {
			// The sign is gone (the player broke it): there is nothing left to move.
			data.setStopSign(null);
			return false;
		}
		Optional<BlockPos> moved = Optional.empty();
		for (Spot spot : spotsBeside(level, cross, from, sign)) {
			if (spot.pos().equals(from) && spot.state() == sign) {
				moved = Optional.of(from);
				break;
			}
			Build build = Build.his(traces, level, "F03");
			if (!spot.pos().equals(from)) {
				build.move(from, spot.pos());
			}
			build.convert(spot.pos(), spot.state());
			if (!build.commit()) {
				// Seen (or refused): keep the sign where it is and try again later.
				return false;
			}
			moved = Optional.of(spot.pos());
			break;
		}
		if (moved.isEmpty()) {
			return false;
		}
		BlockPos to = moved.get();
		GlobalPos at = GlobalPos.of(level.dimension(), to);
		data.sign(stop.get()).ifPresent(record -> {
			data.removeSign(stop.get());
			data.putSign(record.movedTo(at));
		});
		data.setStopSign(at);
		state.setFragmentPlaced("F03", at);
		LoreData lore = LoreData.get(server);
		lore.clearReadTargets("F03");
		lore.addReadTarget("F03", at);
		// If the player rewrote it, it says "Stop." again: F03's own words, never his.
		Optional<Fragment> f03 = FragmentData.get("F03");
		String name = state.subject().map(HerobrineState.Subject::name).orElse("Steve");
		if (f03.isPresent() && level.getBlockEntity(to) instanceof SignBlockEntity be && !SignEdits.front(be).equals(f03.get().linesFor(name))) {
			editor.edit(level, to, f03.get().linesFor(name), List.of(), "lore:his/F03");
		}
		A1016_02.LOGGER.info("[a1016] lore: the \"Stop.\" sign stands in front of the cross at {}", cross.toShortString());
		return true;
	}

	/** Where the moved sign goes and how it stands there. */
	record Spot(BlockPos pos, BlockState state) {
	}

	/**
	 * Where this kind of sign can be in front of the cross, best first (sides without an arm first): a standing sign
	 * on the ground beside it, a wall sign on the post, a wall hanging sign from the post's side, a ceiling hanging
	 * sign under an arm. Empty for any other kind (the reference is kept).
	 */
	static List<Spot> spotsBeside(ServerLevel level, BlockPos cross, BlockPos from, BlockState sign) {
		List<Spot> spots = new java.util.ArrayList<>();
		for (Direction dir : frontFirst(level, cross)) {
			if (sign.getBlock() instanceof StandingSignBlock) {
				BlockPos to = cross.relative(dir);
				if (to.equals(from) || Terrain.isFloor(level, to)) {
					spots.add(new Spot(to, sign.setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(dir))));
				}
			} else if (sign.getBlock() instanceof WallSignBlock) {
				for (int up = 0; up <= 1; up++) {
					BlockPos post = cross.above(up);
					BlockPos to = post.relative(dir);
					if (free(level, to, from) && level.getBlockState(post).isFaceSturdy(level, post, dir)) {
						spots.add(new Spot(to, sign.setValue(WallSignBlock.FACING, dir)));
					}
				}
			} else if (sign.getBlock() instanceof WallHangingSignBlock) {
				// Turned side-on, so it hangs from the post itself (facing clockwise of the side it is on).
				for (int up = 0; up <= 2; up++) {
					BlockPos post = cross.above(up);
					BlockPos to = post.relative(dir);
					if (free(level, to, from) && level.getBlockState(post).isFaceSturdy(level, post, dir, SupportType.FULL)) {
						spots.add(new Spot(to, sign.setValue(WallHangingSignBlock.FACING, dir.getClockWise())));
					}
				}
			} else if (sign.getBlock() instanceof CeilingHangingSignBlock) {
				for (int up = 1; up <= 4; up++) {
					BlockPos arm = cross.above(up).relative(dir);
					BlockPos to = arm.below();
					if (!Terrain.isAirOrReplaceable(level.getBlockState(arm)) && level.getBlockState(arm).isFaceSturdy(level, arm, Direction.DOWN,
							SupportType.CENTER) && free(level, to, from)) {
						spots.add(new Spot(to, sign.setValue(CeilingHangingSignBlock.ROTATION, RotationSegment.convertToSegment(dir))));
					}
				}
			}
		}
		return spots;
	}

	private static boolean free(ServerLevel level, BlockPos to, BlockPos from) {
		return to.equals(from) || Terrain.isAirOrReplaceable(level.getBlockState(to)) && level.getBlockEntity(to) == null;
	}

	/** The cross's sides without an arm first (its front and back), then the rest. */
	static List<Direction> frontFirst(ServerLevel level, BlockPos cross) {
		List<Direction> free = new java.util.ArrayList<>();
		List<Direction> armed = new java.util.ArrayList<>();
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			boolean arm = false;
			for (int up = 1; up <= 4; up++) {
				if (!Terrain.isAirOrReplaceable(level.getBlockState(cross.above(up).relative(dir)))) {
					arm = true;
				}
			}
			(arm ? armed : free).add(dir);
		}
		free.addAll(armed);
		return free;
	}
}
