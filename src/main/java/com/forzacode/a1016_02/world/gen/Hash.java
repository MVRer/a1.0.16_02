package com.forzacode.a1016_02.world.gen;

/** Stateless hashing for deterministic, thread-safe scar decisions (SplitMix64 finalizer). */
public final class Hash {
	private Hash() {
	}

	public static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	public static long of(long... parts) {
		long h = 0x9E3779B97F4A7C15L;
		for (long part : parts) {
			h = mix(h ^ mix(part + 0x632BE59BD9B4E019L));
		}
		return h;
	}

	/** A double in [0, 1). */
	public static double unit(long h) {
		return (mix(h) >>> 11) * 0x1.0p-53;
	}

	/** An int in [0, bound). */
	public static int below(long h, int bound) {
		return bound <= 1 ? 0 : (int) Math.floorMod(mix(h ^ 0x5DEECE66DL), (long) bound);
	}

	/** An int in [min, max]. */
	public static int between(long h, int min, int max) {
		return max <= min ? min : min + below(h, max - min + 1);
	}
}
