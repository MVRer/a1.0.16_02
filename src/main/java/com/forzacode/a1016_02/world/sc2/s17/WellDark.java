package com.forzacode.a1016_02.world.sc2.s17;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 17f's plan (docs/SELF_CREATED_2_R3.md, "Answers (17)", "17f The well goes dark from above"): which gallery of the well
 * goes dark next, and where the glimpse stands. The galleries go from the top ({@link Well#kTop}) down, every light of a
 * gallery at once ({@link Well#lights}: its glowstone and, over the land, the fault's light blocks), skipping a gallery
 * with none left, never a gallery under {@code highest + 2} (the highest participant in the well: never their gallery
 * nor the one above it).
 */
final class WellDark {
	/** No gallery next. */
	static final int NONE = Integer.MIN_VALUE;
	/** The glimpse stands this far from the player (blocks). */
	static final double GLIMPSE_MIN = 20.0;
	static final double GLIMPSE_MAX = 30.0;
	/** ... on a gallery this many over theirs. */
	static final int GLIMPSE_UP_MIN = 1;
	static final int GLIMPSE_UP_MAX = 3;

	private WellDark() {
	}

	/** A light of the well: glowstone, or the fault's light block. */
	static boolean isLight(BlockState state) {
		return state.is(Blocks.GLOWSTONE) || state.is(Blocks.LIGHT);
	}

	/** Each well's lights by gallery, as generated (pure, so kept while its plan is: the rules keep their plans). */
	private static final Map<Well, Map<Integer, List<BlockPos>>> LIGHTS = new WeakHashMap<>();

	/** Gallery {@code k}'s lights as generated ({@link Well#lights}, kept). Server thread. */
	static List<BlockPos> lights(Well well, int k) {
		return LIGHTS.computeIfAbsent(well, w -> new HashMap<>()).computeIfAbsent(k, well::lights);
	}

	/** The lights of gallery {@code k} still there (loaded ones). */
	static List<BlockPos> present(ServerLevel level, Well well, int k) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos pos : lights(well, k)) {
			if (level.isLoaded(pos) && isLight(level.getBlockState(pos))) {
				out.add(pos);
			}
		}
		return out;
	}

	/** How many of these are glowstone (what the visit's cap counts). */
	static int glowstone(ServerLevel level, List<BlockPos> lights) {
		int n = 0;
		for (BlockPos pos : lights) {
			n += level.getBlockState(pos).is(Blocks.GLOWSTONE) ? 1 : 0;
		}
		return n;
	}

	/** The highest gallery a participant stands on in the well, or {@link #NONE} if nobody is in it. */
	static int highest(Well well, List<BlockPos> feet) {
		int out = NONE;
		for (BlockPos p : feet) {
			int g = well.galleryOf(p);
			if (g != Integer.MIN_VALUE) {
				out = Math.max(out, g);
			}
		}
		return out;
	}

	/** True if gallery {@code k} may go dark with these participants where they are (2 or more over the highest). */
	static boolean clear(Well well, int k, List<BlockPos> feet) {
		int highest = highest(well, feet);
		return highest == NONE || k >= highest + 2;
	}

	/** The next gallery to darken from the top down, or {@link #NONE}. */
	static int next(ServerLevel level, Well well, Set<Integer> dark, List<BlockPos> feet) {
		int highest = highest(well, feet);
		if (highest == NONE) {
			return NONE;
		}
		for (int k = well.kTop; k >= highest + 2 && k >= well.kMin; k--) {
			if (!dark.contains(k) && !present(level, well, k).isEmpty()) {
				return k;
			}
		}
		return NONE;
	}

	/** True if gallery {@code k} is dark: never lit, or every light of it gone. */
	static boolean dark(ServerLevel level, Well well, int k, Set<Integer> darkened) {
		return darkened.contains(k) || present(level, well, k).isEmpty();
	}

	/**
	 * Where the glimpse stands (D-105): on a dark gallery 1 to 3 over the player's, on the ring's middle line across the
	 * void from them, {@value #GLIMPSE_MIN} to {@value #GLIMPSE_MAX} off, its feet and head clear over a floor; empty if
	 * none.
	 */
	static Optional<Vec3> glimpseSpot(ServerLevel level, Well well, BlockPos player, Set<Integer> darkened) {
		int g = well.galleryOf(player);
		if (g == Integer.MIN_VALUE) {
			return Optional.empty();
		}
		Vec3 eye = Vec3.atBottomCenterOf(player);
		for (int k = g + GLIMPSE_UP_MIN; k <= Math.min(well.kTop, g + GLIMPSE_UP_MAX); k++) {
			if (!dark(level, well, k, darkened)) {
				continue;
			}
			BlockPos best = spot(level, well, k, eye);
			if (best != null) {
				return Optional.of(Vec3.atBottomCenterOf(best));
			}
		}
		return Optional.empty();
	}

	private static @Nullable BlockPos spot(ServerLevel level, Well well, int k, Vec3 from) {
		int feetY = well.floorY(k) + 1;
		BlockPos best = null;
		double bestOff = Double.MAX_VALUE;
		for (int s = Well.NEAR; s <= Well.FRONT; s++) {
			int m = Well.middle(s);
			for (int t = Well.IN0; t <= Well.IN1; t++) {
				int i = s == Well.NEAR || s == Well.FAR ? m : t;
				int j = s == Well.NEAR || s == Well.FAR ? t : m;
				BlockPos at = well.at(i, feetY, j);
				Vec3 c = Vec3.atBottomCenterOf(at);
				double d = c.distanceTo(from);
				double flat = Math.hypot(c.x - from.x, c.z - from.z);
				if (d < GLIMPSE_MIN || d > GLIMPSE_MAX || flat < Well.VOID1 - Well.VOID0 || !level.isLoaded(at)) {
					continue;
				}
				if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir() || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
					continue;
				}
				double off = Math.abs(d - (GLIMPSE_MIN + GLIMPSE_MAX) / 2);
				if (off < bestOff) {
					bestOff = off;
					best = at;
				}
			}
		}
		return best;
	}

	/** The galleries of the well that were lit at generation and are dark now (loaded ones): for the debug's counts. */
	static int darkGalleries(ServerLevel level, Well well) {
		int n = 0;
		for (int k = well.kMin; k <= well.kTop; k++) {
			List<BlockPos> lights = lights(well, k);
			if (!lights.isEmpty() && level.isLoaded(lights.getFirst()) && present(level, well, k).isEmpty()) {
				n++;
			}
		}
		return n;
	}
}
