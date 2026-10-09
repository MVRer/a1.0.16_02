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
import net.minecraft.world.level.block.StandingSignBlock;
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
	 * go." Also mirrored in {@code HerobrineState} flags as {@code lore:telling_count=<n>}.
	 */
	public static int tellingCount(MinecraftServer server) {
		return TellingData.get(server).count();
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
	 * Ending B only: places F20 in a chest directly under where F10 was placed (the block there is taken out). True
	 * once it is there (also when it already was); false if F10 was never placed, the spot is in view or its chunk
	 * is still loading.
	 */
	public static boolean placeF20(MinecraftServer server) {
		return placeF20(server, Services.traces());
	}

	static boolean placeF20(MinecraftServer server, TraceService traces) {
		HerobrineState state = HerobrineState.get(server);
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
		if (!ChunkGate.request(level, under, 1)) {
			return false;
		}
		BlockState there = level.getBlockState(under);
		if (level.getBlockEntity(under) != null || there.getDestroySpeed(level, under) < 0) {
			return false;
		}
		String name = state.subject().map(HerobrineState.Subject::name).orElse("Steve");
		Build build = Build.left(traces, level, "F20");
		if (!Terrain.isAirOrReplaceable(there)) {
			build.remove(under);
		}
		build.chest(under, Direction.NORTH, List.of(FragmentItems.book(f20.get(), name)));
		if (!build.commit()) {
			return false;
		}
		state.setFragmentPlaced("F20", GlobalPos.of(level.dimension(), under));
		A1016_02.LOGGER.info("[a1016] lore: F20 lies under F10 at {}", under.toShortString());
		return true;
	}

	/**
	 * Ending A: the "Stop." sign now stands in front of the player's cross. {@code crossPos} is the cross's bottom
	 * block (standing on the ground). A standing sign is moved onto the ground beside it, a wall sign onto the
	 * cross's side, facing away; if the player rewrote it, "Stop." comes back (F03's own text). False if there is no
	 * "Stop." sign, it is in another dimension, no side is free, or the move would be seen.
	 */
	public static boolean moveStopSignToCross(MinecraftServer server, GlobalPos crossPos) {
		return moveStopSignToCross(server, crossPos, Services.traces(), SignEdits.editor(Services.traces()));
	}

	/** {@link #moveStopSignToCross(MinecraftServer, GlobalPos)} for a cross in the overworld. */
	public static boolean moveStopSignToCross(MinecraftServer server, BlockPos crossPos) {
		return moveStopSignToCross(server, GlobalPos.of(Level.OVERWORLD, crossPos));
	}

	static boolean moveStopSignToCross(MinecraftServer server, GlobalPos crossPos, TraceService traces, SignEdits.Editor editor) {
		TellingData data = TellingData.get(server);
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
		if (!(level.getBlockEntity(from) instanceof SignBlockEntity) || !PlaceNotFound.isMovableSign(sign)) {
			data.setStopSign(null);
			return false;
		}
		Optional<BlockPos> moved = Optional.empty();
		for (Direction dir : frontFirst(level, cross)) {
			BlockPos to;
			BlockState placed;
			if (sign.getBlock() instanceof StandingSignBlock) {
				to = cross.relative(dir);
				placed = sign.setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(dir));
				if (!to.equals(from) && !Terrain.isFloor(level, to)) {
					continue;
				}
			} else {
				to = null;
				placed = sign.setValue(WallSignBlock.FACING, dir);
				for (int up = 0; up <= 1 && to == null; up++) {
					BlockPos post = cross.above(up);
					BlockPos spot = post.relative(dir);
					boolean free = spot.equals(from) || Terrain.isAirOrReplaceable(level.getBlockState(spot)) && level.getBlockEntity(spot) == null;
					if (free && level.getBlockState(post).isFaceSturdy(level, post, dir)) {
						to = spot;
					}
				}
				if (to == null) {
					continue;
				}
			}
			if (to.equals(from) && placed == sign) {
				moved = Optional.of(to);
				break;
			}
			Build build = Build.his(traces, level, "F03");
			if (!to.equals(from)) {
				build.move(from, to);
			}
			build.convert(to, placed);
			if (build.commit()) {
				moved = Optional.of(to);
				break;
			}
			return false;
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
		HerobrineState.get(server).setFragmentPlaced("F03", at);
		LoreData lore = LoreData.get(server);
		lore.clearReadTargets("F03");
		lore.addReadTarget("F03", at);
		// If the player rewrote it, it says "Stop." again: F03's own words, never his.
		Optional<Fragment> f03 = FragmentData.get("F03");
		String name = HerobrineState.get(server).subject().map(HerobrineState.Subject::name).orElse("Steve");
		if (f03.isPresent() && level.getBlockEntity(to) instanceof SignBlockEntity be && !SignEdits.front(be).equals(f03.get().linesFor(name))) {
			editor.edit(level, to, SignEdits.lines(f03.get().linesFor(name)), SignEdits.blank(), "lore:his/F03");
		}
		A1016_02.LOGGER.info("[a1016] lore: the \"Stop.\" sign stands in front of the cross at {}", cross.toShortString());
		return true;
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
