package com.forzacode.a1016_02.world.sc2.kit;

import java.util.Arrays;
import java.util.Optional;

import com.forzacode.a1016_02.world.gen.Hash;
import com.forzacode.a1016_02.world.gen.Terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;

import org.jspecify.annotations.Nullable;

/**
 * Where a kind's start goes in one candidate chunk (its structure set's placement picked the chunk), or nowhere. Asked
 * on a worldgen thread by {@link Sc2Structure#findGenerationPoint}, only after the kit's own gates passed (a snapshot,
 * the switch on, the stage at least the kind's {@code fromStage}, far enough from the origin and the world spawn: see
 * {@link Snapshot#refusal}). Read only the query (its noise terrain and snapshot); at most 81 terrain samples (D-104).
 * The structure JSON's {@code biomes} is then checked by vanilla at the ground of the origin's column. The kit then makes
 * the start's {@link Shape} (or takes the one siting {@link #handOver handed over}) and refuses it if its box leaves the
 * kit's reach or the level's height ({@link Sc2Structure#site}, {@link Heights}): a kind never needs to check those, and
 * may keep a stricter rule of its own.
 */
@FunctionalInterface
public interface Siting {
	Optional<Start> find(Query query);

	/**
	 * One candidate chunk, as siting sees it.
	 *
	 * @param chunk     the candidate (the start's chunk: its box must stay within {@link Sc2Structure#MAX_REACH} chunks)
	 * @param terrain   the noise (thread-safe, the same answers on every thread and in every chunk, generated or not)
	 * @param snapshot  the server's numbers for worldgen, never null here
	 * @param worldSeed the world's seed
	 * @param random    vanilla's random for this chunk (the large-feature seed: deterministic)
	 * @param minY      the lowest block worldgen fills ({@link Heights#lowest}: the level's own, read from it)
	 * @param maxY      the level's highest block ({@link Heights#highest})
	 */
	record Query(ChunkPos chunk, Terrain terrain, Snapshot snapshot, long worldSeed, RandomSource random, int minY, int maxY) {
		/** The chunk's middle column at its ground. */
		public BlockPos center() {
			int x = chunk.getMiddleBlockX();
			int z = chunk.getMiddleBlockZ();
			return new BlockPos(x, terrain.ground(x, z), z);
		}

		/** A seed for this candidate and kind: the world's seed, the world's own worldgen salt, the chunk and the id. */
		public long seed(String kind) {
			return Hash.of(worldSeed, snapshot.salt(), chunk.x(), chunk.z(), kind.hashCode());
		}

		/**
		 * The ground on a {@code perSide} x {@code perSide} grid spread evenly over the square of half-size {@code half}
		 * around ({@code x}, {@code z}), row by row ({@code perSide} squared samples).
		 */
		public int[] groundGrid(int x, int z, int half, int perSide) {
			int n = Math.max(1, perSide);
			int[] out = new int[n * n];
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					int sx = n == 1 ? x : x - half + Math.round(2.0F * half * i / (n - 1));
					int sz = n == 1 ? z : z - half + Math.round(2.0F * half * j / (n - 1));
					out[i * n + j] = terrain.ground(sx, sz);
				}
			}
			return out;
		}
	}

	/**
	 * Hands the shape this siting made for {@code start} to the kit on this thread, so the kit does not make it again
	 * ({@link Sc2Structure#site}); a siting that has to make the shape to decide calls it just before it returns the
	 * start. The next {@link #takeHandedOver} on this thread takes it (or drops it if the start differs).
	 */
	static void handOver(@Nullable Start start, @Nullable Shape shape) {
		HandOver.SLOT.set(start == null || shape == null ? null : new HandOver(start, shape));
	}

	/** The shape handed over on this thread for this start, or null; the slot is emptied either way. */
	static @Nullable Shape takeHandedOver(Start start) {
		HandOver made = HandOver.SLOT.get();
		HandOver.SLOT.remove();
		return made != null && made.start().equals(start) ? made.shape() : null;
	}

	/** Highest minus lowest of these samples. */
	static int spread(int[] samples) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int s : samples) {
			min = Math.min(min, s);
			max = Math.max(max, s);
		}
		return samples.length == 0 ? 0 : max - min;
	}

	/** The median of these samples (the lower middle for an even count). */
	static int median(int[] samples) {
		if (samples.length == 0) {
			return 0;
		}
		int[] sorted = samples.clone();
		Arrays.sort(sorted);
		return sorted[(sorted.length - 1) / 2];
	}

	/** Horizontal distance from (0, 0). */
	static double fromOrigin(int x, int z) {
		return Math.sqrt((double) x * x + (double) z * z);
	}

	/**
	 * Of the four horizontal directions, the one whose outward direction is nearest the bearing from ({@code x}, {@code z})
	 * toward ({@code toX}, {@code toZ}) (D-095: every entrance faces the origin). NORTH when they coincide.
	 */
	static Direction facingToward(int x, int z, int toX, int toZ) {
		int dx = toX - x;
		int dz = toZ - z;
		if (dx == 0 && dz == 0) {
			return Direction.NORTH;
		}
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** {@link #facingToward} the world's origin. */
	static Direction facingToOrigin(int x, int z) {
		return facingToward(x, z, 0, 0);
	}
}

/** The shape a siting handed to the kit on this thread ({@link Siting#handOver}). */
record HandOver(Start start, Shape shape) {
	static final ThreadLocal<@Nullable HandOver> SLOT = new ThreadLocal<>();
}
