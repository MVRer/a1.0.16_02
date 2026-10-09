package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The close-chase rush (D-037) as plain geometry, free of the entity so the game tests can drive it. A player he
 * cannot outrun closes in on him; he turns and runs straight at them, passes beside them and keeps going behind
 * them, where he is gone the instant they cannot see him ({@link HimEntity}).
 *
 * <p>Horizontal only. He aims at a point {@link #AIM_MARGIN} times the pass offset to one side of the player, so his
 * line runs straight at them and bends out past them. Once he is level with or behind the player (along the line he
 * started on) he runs straight on. Every step is then pushed out of a capsule of radius {@code offset} around the
 * player's position now and where they will be next tick at their current velocity: his own movement never takes
 * him closer than the offset to the player. Only the player's own movement can (the player steering into him).
 */
public final class Rush {
	/** He never passes closer than this (center to center), whatever the config says. */
	public static final double MIN_OFFSET = 1.5;
	/** He aims this much wider than the offset, so the capsule rarely has to push him. */
	static final double AIM_MARGIN = 1.6;
	/** A planned path follows a chaser at constant velocity at most this many ticks. */
	static final int PLAN_TICKS = 80;

	/** One simulated tick: where he ends it, and where the player is then. */
	public record Step(Vec3 him, Vec3 player) {
		/** Horizontal unit vector from the player to him (out of the pass), or zero. */
		public Vec3 outward() {
			Vec3 out = flat(him.subtract(player));
			return out.lengthSqr() > 1.0E-8 ? out.normalize() : Vec3.ZERO;
		}
	}

	/** Horizontal unit vector from the player to him when the rush began. */
	private final Vec3 axis;
	/** +1 or -1: the side of the player he passes on. */
	private final int side;
	private final double offset;
	private boolean passed;

	Rush(Vec3 axis, int side, double offset) {
		this.axis = axis;
		this.side = side >= 0 ? 1 : -1;
		this.offset = Math.max(MIN_OFFSET, offset);
	}

	/** A rush from {@code him} at {@code player}, passing on {@code side}; null if they are on top of each other. */
	public static @Nullable Rush toward(Vec3 him, Vec3 player, int side, double offset) {
		Vec3 rel = flat(him.subtract(player));
		if (rel.lengthSqr() < 1.0E-8) {
			return null;
		}
		return new Rush(rel.normalize(), side, offset);
	}

	/** The side away from where the player drifts sideways, so they do not drift into his line. */
	public static int sideAwayFrom(Vec3 him, Vec3 player, Vec3 playerVelocity) {
		Vec3 rel = flat(him.subtract(player));
		if (rel.lengthSqr() < 1.0E-8) {
			return 1;
		}
		Vec3 u = rel.normalize();
		double drift = flat(playerVelocity).dot(new Vec3(-u.z, 0.0, u.x));
		return drift > 0.0 ? -1 : 1;
	}

	/**
	 * Plans a rush: the drift-away side first, then the other, the first whose whole path (simulated with the
	 * player at constant velocity) is {@code clear}: ground all the way, and room on the outer side of the pass, so
	 * no wall can hold him inside the offset. Empty if neither side is, or they are too close already to pass outside
	 * the offset: then he runs away instead.
	 *
	 * @param stepLength his speed in blocks per tick
	 */
	public static Optional<Rush> plan(Vec3 him, Vec3 player, Vec3 playerVelocity, double offset, double stepLength, Predicate<List<Step>> clear) {
		if (flat(him.subtract(player)).length() < Math.max(MIN_OFFSET, offset)) {
			return Optional.empty();
		}
		int first = sideAwayFrom(him, player, playerVelocity);
		for (int side : new int[] {first, -first}) {
			Rush rush = toward(him, player, side, offset);
			if (rush == null) {
				return Optional.empty();
			}
			List<Step> path = rush.copy().simulate(him, player, playerVelocity, stepLength, offset + 4.0);
			if (!path.isEmpty() && clear.test(path)) {
				return Optional.of(rush);
			}
		}
		return Optional.empty();
	}

	private Rush copy() {
		Rush copy = new Rush(axis, side, offset);
		copy.passed = passed;
		return copy;
	}

	public double offset() {
		return offset;
	}

	public int side() {
		return side;
	}

	/** True once he is level with or behind the player. */
	public boolean passed() {
		return passed;
	}

	/**
	 * His next position this tick ({@code y} kept from {@code him}), moving up to {@code stepLength}, then pushed out
	 * to the offset from the player's position now and next tick (the capsule between them).
	 *
	 * @param playerVelocity the player's horizontal velocity in blocks per tick
	 */
	public Vec3 step(Vec3 him, Vec3 player, Vec3 playerVelocity, double stepLength) {
		Vec3 rel = flat(him.subtract(player));
		if (!passed && rel.dot(axis) <= 0.0) {
			passed = true;
		}
		Vec3 u = rel.lengthSqr() > 1.0E-8 ? rel.normalize() : axis;
		Vec3 lateral = new Vec3(-u.z, 0.0, u.x).scale(side);
		Vec3 dir;
		if (passed) {
			dir = axis.scale(-1.0); // straight on, behind the player
		} else {
			Vec3 aim = flat(player).add(lateral.scale(offset * AIM_MARGIN));
			Vec3 to = aim.subtract(flat(him));
			dir = to.lengthSqr() > 1.0E-8 ? to.normalize() : axis.scale(-1.0);
		}
		Vec3 next = flat(him).add(dir.scale(Math.max(0.0, stepLength)));
		next = outOfCapsule(next, flat(player), flat(player).add(flat(playerVelocity)), offset, lateral);
		return new Vec3(next.x, him.y, next.z);
	}

	/**
	 * His positions tick by tick if the player keeps {@code playerVelocity}: until he has passed and is
	 * {@code behind} blocks from them, at most {@link #PLAN_TICKS}. Steps this rush (call it on a copy).
	 */
	public List<Step> simulate(Vec3 him, Vec3 player, Vec3 playerVelocity, double stepLength, double behind) {
		List<Step> path = new ArrayList<>();
		Vec3 h = him;
		Vec3 p = player;
		for (int i = 0; i < PLAN_TICKS; i++) {
			p = p.add(flat(playerVelocity));
			h = step(h, p, playerVelocity, stepLength);
			path.add(new Step(h, p));
			if (passed && flat(h.subtract(p)).length() >= behind) {
				break;
			}
		}
		return path;
	}

	/** {@code point} pushed out (sideways if it is on the segment) to {@code radius} from the segment a..b. */
	static Vec3 outOfCapsule(Vec3 point, Vec3 a, Vec3 b, double radius, Vec3 fallback) {
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSqr();
		double t = len2 < 1.0E-12 ? 0.0 : Math.clamp(point.subtract(a).dot(ab) / len2, 0.0, 1.0);
		Vec3 closest = a.add(ab.scale(t));
		Vec3 away = point.subtract(closest);
		double d = away.length();
		if (d >= radius) {
			return point;
		}
		Vec3 dir = d > 1.0E-6 ? away.scale(1.0 / d) : fallback.lengthSqr() > 1.0E-8 ? fallback.normalize() : new Vec3(1.0, 0.0, 0.0);
		// A hair past the radius, so rounding never leaves him inside it.
		return closest.add(dir.scale(radius + 1.0E-4));
	}

	/** Horizontal distance from {@code point} to the segment a..b. */
	public static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
		Vec3 p = flat(point);
		Vec3 fa = flat(a);
		Vec3 ab = flat(b).subtract(fa);
		double len2 = ab.lengthSqr();
		double t = len2 < 1.0E-12 ? 0.0 : Math.clamp(p.subtract(fa).dot(ab) / len2, 0.0, 1.0);
		return p.subtract(fa.add(ab.scale(t))).length();
	}

	static Vec3 flat(Vec3 v) {
		return new Vec3(v.x, 0.0, v.z);
	}
}
