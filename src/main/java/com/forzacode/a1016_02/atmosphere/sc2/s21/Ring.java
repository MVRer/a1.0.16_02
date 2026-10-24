package com.forzacode.a1016_02.atmosphere.sc2.s21;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The pure geometry of 21 (docs/SELF_CREATED_2.md): the radius for a count of animals, their slots on the circle, the
 * way round (widdershins: counter-clockwise seen from above), the point ahead each one is walked to, the speed that keeps
 * it on its slot, and the cells of the way round. Angles are in radians, measured as {@code atan2(dz, dx)} from the
 * middle (0 east, pi/2 south); seen from above with north up that angle grows clockwise, so widdershins is a falling
 * angle ({@link #WAY} -1).
 */
public final class Ring {
	/** The way round: -1, a falling angle, counter-clockwise seen from above (widdershins). */
	public static final int WAY = -1;
	/** R11-10: a wider ring (4 to 9), so from the ground the far side shows through the gaps and it reads as a file going round. */
	public static final double MIN_RADIUS = 4.0;
	public static final double MAX_RADIUS = 9.0;
	/** Each animal is walked to the point this far ahead of its slot (25 degrees), and never under {@link #LEAD_MIN_BLOCKS}. */
	public static final double LEAD = Math.toRadians(25.0);
	/** The point ahead is at least this far round from the slot (blocks), so a walk never ends between two updates. */
	public static final double LEAD_MIN_BLOCKS = 2.6;
	/** The slots go round at this speed (blocks a tick along the way round): an unhurried walk, slower than any animal's own. */
	public static final double SLOT_SPEED = 0.085;
	/** How hard an animal behind or ahead of its slot is sped up or held back (per block of the way round). */
	public static final double CATCH_UP = 0.35;

	private Ring() {
	}

	/** The radius for {@code n} animals {@code spacing} blocks apart round the way: n x spacing / 2 pi, clamped 4 to 9. */
	public static double radius(int n, double spacing) {
		return Mth.clamp(n * spacing / (2.0 * Math.PI), MIN_RADIUS, MAX_RADIUS);
	}

	/** The angle of {@code pos} seen from {@code middle} (horizontal). */
	public static double angleOf(Vec3 middle, Vec3 pos) {
		return Math.atan2(pos.z - middle.z, pos.x - middle.x);
	}

	/** The point on the circle at {@code angle}, at {@code y}. */
	public static Vec3 at(Vec3 middle, double radius, double angle, double y) {
		return new Vec3(middle.x + Math.cos(angle) * radius, y, middle.z + Math.sin(angle) * radius);
	}

	/** The lead (radians) for this radius: {@link #LEAD}, or more on a small circle so it is at least {@link #LEAD_MIN_BLOCKS} round. */
	public static double lead(double radius) {
		return Math.max(LEAD, LEAD_MIN_BLOCKS / Math.max(radius, 0.5));
	}

	/** The angle ahead of {@code angle} by {@code by} radians the way round (widdershins). */
	public static double ahead(double angle, double by) {
		return angle + WAY * by;
	}

	/**
	 * The point an animal at slot angle {@code slot} is walked to: {@link #lead} ahead of it, on the circle of
	 * {@code radius / cos(lead)}, so the way to it from the slot is the circle's tangent: an animal that keeps walking at
	 * it stays on the circle instead of cutting inside it (a pursuit of a point on the circle itself settles on a smaller
	 * one: on the wider ring, a block in).
	 */
	public static Vec3 pointAhead(Vec3 middle, double radius, double slot, double y) {
		double lead = lead(radius);
		return at(middle, radius / Math.cos(lead), ahead(slot, lead), y);
	}

	/** Slot {@code i} of {@code n} when slot 0 is at {@code phase}: evenly spread, each the next one behind the one before. */
	public static double slot(double phase, int i, int n) {
		return phase - WAY * i * (2.0 * Math.PI / n);
	}

	/** How far the phase moves the way round in {@code ticks} at {@link #SLOT_SPEED} on this radius (radians, signed). */
	public static double turn(double radius, int ticks) {
		return WAY * SLOT_SPEED * ticks / Math.max(radius, 0.5);
	}

	/** The angle {@code a} minus {@code b}, wrapped to -pi to pi. */
	public static double diff(double a, double b) {
		return Mth.wrapDegrees(Math.toDegrees(a - b)) * Mth.DEG_TO_RAD;
	}

	/**
	 * How far (blocks round the way) an animal at angle {@code at} is behind its slot at {@code slot}: positive behind
	 * (it must catch up), negative ahead of it.
	 */
	public static double behindBy(double radius, double at, double slot) {
		// The way round is WAY; an animal behind its slot has the slot ahead of it, the way round.
		return WAY * diff(slot, at) * radius;
	}

	/**
	 * The speed (blocks a tick) an animal should walk to keep its slot: {@link #SLOT_SPEED}, faster when it is behind,
	 * slower when ahead, between half and 1.6 times it.
	 */
	public static double wantedSpeed(double behind) {
		return Mth.clamp(SLOT_SPEED * (1.0 + CATCH_UP * behind), SLOT_SPEED * 0.5, SLOT_SPEED * 1.6);
	}

	/**
	 * The walk speed factor (the steer's {@code speed}) that makes a mob of this movement speed attribute walk
	 * {@code blocksPerTick} on flat ground: a mob's walk on the ground settles at about 2.92 x (attribute x factor) squared
	 * blocks a tick (its input and its friction-scaled speed are both the attribute times the factor), so the factor is
	 * the square root of the wanted speed over 2.92, over the attribute. Clamped to 0.3 to 1.2 (never a run).
	 */
	public static double factorFor(double blocksPerTick, double movementSpeed) {
		if (!(movementSpeed > 1.0E-4)) {
			return 1.0;
		}
		return Mth.clamp(Math.sqrt(Math.max(0.0, blocksPerTick) / 2.92) / movementSpeed, 0.3, 1.2);
	}

	/**
	 * Which slot each of the animals at these angles takes: slots in the same order round as the animals stand, turned so
	 * the sum of the angles each must walk is the least (nobody crosses anybody). Returns, for animal k, its slot index;
	 * slot 0 is at {@code phase}.
	 */
	public static int[] assign(double[] angles, double phase) {
		int n = angles.length;
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) {
			order[i] = i;
		}
		// In the order of the slots: slot i is at phase - WAY * i * step, so sort by (angle - phase) * -WAY, wrapped to 0..2pi.
		java.util.Arrays.sort(order, (a, b) -> Double.compare(around(angles[a], phase), around(angles[b], phase)));
		int[] best = new int[n];
		double bestCost = Double.MAX_VALUE;
		for (int shift = 0; shift < n; shift++) {
			double cost = 0.0;
			int[] slots = new int[n];
			for (int k = 0; k < n; k++) {
				int animal = order[k];
				int s = (k + shift) % n;
				slots[animal] = s;
				cost += Math.abs(diff(angles[animal], slot(phase, s, n)));
			}
			if (cost < bestCost) {
				bestCost = cost;
				best = slots;
			}
		}
		return best;
	}

	/** How far round from {@code phase} (0 to 2 pi) an angle is, counted the way the slots are numbered. */
	private static double around(double angle, double phase) {
		double d = -WAY * (angle - phase);
		d = d % (2.0 * Math.PI);
		return d < 0 ? d + 2.0 * Math.PI : d;
	}

	/**
	 * The ground cells of the way round (the block each foot stands on, at {@code groundY}): the cells under the circle,
	 * at most {@code max}, spread evenly round it. In order round the way.
	 */
	public static List<BlockPos> cells(Vec3 middle, double radius, int groundY, int max) {
		// Every cell whose centre is within half a block of the circle: a ring one cell wide, in order round the way.
		List<BlockPos> list = new ArrayList<>();
		int reach = (int) Math.ceil(radius + 1.0);
		int mx = (int) Math.floor(middle.x);
		int mz = (int) Math.floor(middle.z);
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				double cx = mx + dx + 0.5 - middle.x;
				double cz = mz + dz + 0.5 - middle.z;
				if (Math.abs(Math.sqrt(cx * cx + cz * cz) - radius) <= 0.5) {
					list.add(new BlockPos(mx + dx, groundY, mz + dz));
				}
			}
		}
		list.sort(java.util.Comparator.comparingDouble(c -> -WAY * Math.atan2(c.getZ() + 0.5 - middle.z, c.getX() + 0.5 - middle.x)));
		if (max <= 0) {
			return List.of();
		}
		if (list.size() <= max) {
			return list;
		}
		List<BlockPos> spread = new ArrayList<>(max);
		for (int i = 0; i < max; i++) {
			spread.add(list.get((int) ((long) i * list.size() / max)));
		}
		return spread;
	}
}
