package com.forzacode.a1016_02.atmosphere.sc2.s24;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.phys.Vec3;

/**
 * The way someone came (pure): their feet sampled whenever they moved {@link #STEP} or more since the last point, at most
 * {@link #MAX} points, newest last, with whether they were outdoors there. {@link #back} walks it back from where they are
 * now by a length of way, so whatever follows goes where they went, not across.
 */
public final class Trail {
	/** A new point once they are this far from the last one (blocks). */
	public static final double STEP = 1.0;
	public static final int MAX = 160;

	/** A point of the way and whether they were outdoors there. */
	public record Point(Vec3 pos, boolean outdoors) {
	}

	private final Deque<Point> points = new ArrayDeque<>();

	/** Adds where they are now, if they moved {@link #STEP} or more since the last point. True if it was added. */
	public boolean add(Vec3 pos, boolean outdoors) {
		Point last = points.peekLast();
		if (last != null && last.pos().distanceToSqr(pos) < STEP * STEP) {
			return false;
		}
		points.addLast(new Point(pos, outdoors));
		while (points.size() > MAX) {
			points.removeFirst();
		}
		return true;
	}

	/** How many points it holds. */
	public int size() {
		return points.size();
	}

	/** The points, oldest first (a copy). */
	public List<Point> points() {
		return new ArrayList<>(points);
	}

	/**
	 * The point {@code length} blocks back along the way from {@code now} (where they are now: first the stretch from
	 * {@code now} to the newest point, then point to point). If the way is shorter, its oldest point; empty if it has none.
	 */
	public Optional<Vec3> back(Vec3 now, double length) {
		if (points.isEmpty()) {
			return Optional.empty();
		}
		double left = length;
		Vec3 from = now;
		Iterator<Point> it = points.descendingIterator();
		while (it.hasNext()) {
			Vec3 to = it.next().pos();
			double d = from.distanceTo(to);
			if (d >= left && d > 1.0E-6) {
				return Optional.of(from.add(to.subtract(from).scale(left / d)));
			}
			left -= d;
			from = to;
		}
		return Optional.of(points.peekFirst().pos());
	}

	/** The length of the way from {@code now} back to its oldest point (blocks). */
	public double length(Vec3 now) {
		double total = 0;
		Vec3 from = now;
		Iterator<Point> it = points.descendingIterator();
		while (it.hasNext()) {
			Vec3 to = it.next().pos();
			total += from.distanceTo(to);
			from = to;
		}
		return total;
	}

	/** The newest point where they were outdoors, and the one after it (the first point in), if they went in from it. */
	public Optional<Point[]> mouth() {
		Point after = null;
		Iterator<Point> it = points.descendingIterator();
		while (it.hasNext()) {
			Point p = it.next();
			if (p.outdoors()) {
				return Optional.of(new Point[] {p, after == null ? p : after});
			}
			after = p;
		}
		return Optional.empty();
	}

	/** Forgets every point. */
	public void clear() {
		points.clear();
	}
}
