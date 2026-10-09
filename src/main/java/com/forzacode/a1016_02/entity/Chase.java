package com.forzacode.a1016_02.entity;

import net.minecraft.world.phys.Vec3;

/**
 * How fast one player moves, and how fast they close on him, over the last {@link #WINDOW} ticks. Fed the player's
 * horizontal position and distance to him every tick.
 *
 * <p>Player positions reach the server in packets that can bunch up (two moves in one tick, none in the next), so
 * speeds are averaged over the window instead of read per tick. A jump of more than {@link #TELEPORT_BLOCKS} in one
 * tick is a teleport or respawn, not running: the window starts over.
 */
public final class Chase {
	/** Ticks averaged over (half a second). */
	public static final int WINDOW = 10;
	/** More than this in one tick is not movement. */
	public static final double TELEPORT_BLOCKS = 4.0;

	private final double[] xs = new double[WINDOW + 1];
	private final double[] zs = new double[WINDOW + 1];
	private final double[] distances = new double[WINDOW + 1];
	private int count;
	private int head = -1;

	/** Records this tick. */
	public void update(double x, double z, double distance) {
		if (count > 0) {
			double dx = x - xs[head];
			double dz = z - zs[head];
			if (dx * dx + dz * dz > TELEPORT_BLOCKS * TELEPORT_BLOCKS) {
				count = 0;
			}
		}
		head = (head + 1) % xs.length;
		xs[head] = x;
		zs[head] = z;
		distances[head] = distance;
		count = Math.min(count + 1, xs.length);
	}

	/** Horizontal speed in blocks per second over the window, 0 until two ticks are known. */
	public double speed() {
		if (count < 2) {
			return 0.0;
		}
		int oldest = oldest();
		double dx = xs[head] - xs[oldest];
		double dz = zs[head] - zs[oldest];
		return Math.sqrt(dx * dx + dz * dz) / (count - 1) * 20.0;
	}

	/** How fast the distance to him shrinks, in blocks per second (negative while it grows), 0 until two ticks are known. */
	public double closingSpeed() {
		if (count < 2) {
			return 0.0;
		}
		return (distances[oldest()] - distances[head]) / (count - 1) * 20.0;
	}

	/** Horizontal velocity in blocks per tick over the window (y is 0), zero until two ticks are known. */
	public Vec3 velocity() {
		if (count < 2) {
			return Vec3.ZERO;
		}
		int oldest = oldest();
		return new Vec3((xs[head] - xs[oldest]) / (count - 1), 0.0, (zs[head] - zs[oldest]) / (count - 1));
	}

	private int oldest() {
		return Math.floorMod(head - (count - 1), xs.length);
	}
}
