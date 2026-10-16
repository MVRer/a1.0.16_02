package com.forzacode.a1016_02.accident;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Learns each player's routes, separately (D-061): every half second it records the cell their feet are in (walking,
 * climbing or swimming, never flying or riding) in that player's own {@link RouteBook}, and fills short gaps between two
 * of their samples with the open cells in between. It also keeps which way each of them last walked
 * ({@link AccidentData.PlayerData#heading}), so a bridge trap knows what is ahead of them. Works the same in every
 * dimension: routes are kept per level. The last sample is kept by UUID, never by player object (D-062.1).
 * <p>
 * For the players {@link #ringFor} names (docs/SELF_CREATED.md, 15: an armed player), it also keeps a short ring of
 * where their feet stood on the ground at each sample, the last {@link #STANDING_TICKS} of server ticks (memory only).
 */
public final class RouteSampler {
	/** How long the standing ring keeps a stand (10 s of server ticks). */
	public static final long STANDING_TICKS = 200;

	private record Last(ResourceKey<Level> dimension, BlockPos pos) {
	}

	/** A cell the player's feet stood in on the ground, and the server tick. */
	public record Stand(ResourceKey<Level> dimension, BlockPos feet, long tick) {
	}

	private final Map<UUID, Last> last = new HashMap<>();
	private final Map<UUID, ArrayDeque<Stand>> standing = new HashMap<>();
	private Predicate<UUID> ringFor = player -> false;

	/** Forget every last sample (a new server). */
	public void reset() {
		last.clear();
		standing.clear();
	}

	/** Forget one player's last sample (a new session, a teleport). */
	public void reset(UUID player) {
		last.remove(player);
		standing.remove(player);
	}

	/** Who gets a standing ring (none by default). */
	public void ringFor(Predicate<UUID> which) {
		ringFor = which == null ? player -> false : which;
	}

	/** This player's standing ring, oldest first (empty if none). */
	public List<Stand> standing(UUID player) {
		ArrayDeque<Stand> ring = standing.get(player);
		return ring == null ? List.of() : List.copyOf(ring);
	}

	/** One stand in this player's ring; stands older than {@link #STANDING_TICKS} go. */
	public void stand(UUID player, Stand stand) {
		ArrayDeque<Stand> ring = standing.computeIfAbsent(player, k -> new ArrayDeque<>());
		ring.addLast(stand);
		while (!ring.isEmpty() && (stand.tick() - ring.getFirst().tick() > STANDING_TICKS || ring.size() > 64)) {
			ring.removeFirst();
		}
	}

	public void sample(ServerPlayer player, AccidentData data, AccidentConfig cfg, long now, long day) {
		UUID id = player.getUUID();
		if (!player.isAlive() || player.isSpectator() || player.getAbilities().flying || player.isPassenger() || player.isSleeping()) {
			reset(id);
			return;
		}
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		boolean grounded = player.onGround() || player.onClimbable() || player.isInWater();
		if (!grounded) {
			return;
		}
		if (ringFor.test(id)) {
			if (player.onGround()) {
				stand(id, new Stand(level.dimension(), feet.immutable(), level.getServer().getTickCount()));
			}
		} else if (!standing.isEmpty()) {
			standing.remove(id);
		}
		AccidentData.PlayerData mine = data.personal(id);
		boolean changed = record(level, mine.routes(), cfg, feet, now, day);
		Last before = last.get(id);
		if (before != null && level.dimension().equals(before.dimension())) {
			BlockPos from = before.pos();
			int dx = feet.getX() - from.getX();
			int dy = feet.getY() - from.getY();
			int dz = feet.getZ() - from.getZ();
			if (dx != 0 || dz != 0) {
				mine.heading = new Vec3(dx, 0, dz).normalize();
				mine.headingDimension = level.dimension();
			}
			int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
			if (steps > 1 && steps <= cfg.routeInterpolateMax) {
				for (int i = 1; i < steps; i++) {
					double t = (double) i / steps;
					BlockPos cell = new BlockPos(from.getX() + Mth.floor(dx * t + 0.5), from.getY() + Mth.floor(dy * t + 0.5), from.getZ() + Mth.floor(dz * t + 0.5));
					if (Scan.passable(level, cell) && (Scan.fullSolid(level, cell.below()) || level.getBlockState(cell).is(BlockTags.CLIMBABLE) || Scan.water(level, cell))) {
						changed |= record(level, mine.routes(), cfg, cell, now, day);
					}
				}
			}
		}
		last.put(id, new Last(level.dimension(), feet));
		if (changed) {
			data.setDirty();
		}
	}

	private static boolean record(ServerLevel level, RouteBook routes, AccidentConfig cfg, BlockPos cell, long now, long day) {
		return routes.record(level.dimension(), cell, (int) day, flags(level, cell), now, cfg.routePassGapTicks(), cfg.routeMaxPointsPerDimension);
	}

	/** What kind of place a walked cell is. */
	public static int flags(ServerLevel level, BlockPos cell) {
		int flags = 0;
		if (level.getBlockState(cell).is(BlockTags.CLIMBABLE)) {
			flags |= RouteBook.LADDER;
		}
		if (Scan.covered(level, cell)) {
			flags |= RouteBook.UNDER;
		}
		if (Scan.water(level, cell)) {
			flags |= RouteBook.WATER;
		}
		if (Services.watch().wasPlacedByPlayer(level, cell.below())) {
			flags |= RouteBook.ON_PLACED;
		}
		return flags;
	}
}
