package com.forzacode.a1016_02.atmosphere.sc2.s22;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.phys.Vec3;

/**
 * 22's way (pure, horizontal; docs/SELF_CREATED_2.md): the straight line that ends at the destination and passes the
 * target {@code pass} blocks off (a tangent from the destination to the circle of that radius round the target), and its
 * start on that line {@code start} blocks from the target on the side away from the destination. The file walks it from
 * the start to the destination.
 *
 * @param start where the leader starts
 * @param end   the destination
 * @param dir   the unit direction they walk (start to end)
 */
public record Line(Vec3 start, Vec3 end, Vec3 dir) {
	/**
	 * The line for a target at {@code target}, a destination at {@code destination}, passing {@code pass} off, starting
	 * {@code start} from the target; {@code side} +1 or -1 picks one of the two tangents. Empty if the destination is not
	 * further than {@code start} from the target (no room for the file) or {@code pass} is not under {@code start}.
	 */
	public static Optional<Line> of(Vec3 target, Vec3 destination, double pass, double start, int side) {
		double tx = target.x - destination.x;
		double tz = target.z - destination.z;
		double length = Math.sqrt(tx * tx + tz * tz);
		if (length <= start || pass <= 0 || pass >= start) {
			return Optional.empty();
		}
		double alpha = Math.asin(pass / length) * Math.signum(side == 0 ? 1 : side);
		// From the destination toward the target, turned by alpha: the tangent.
		double ux = tx / length;
		double uz = tz / length;
		double cos = Math.cos(alpha);
		double sin = Math.sin(alpha);
		double dx = ux * cos - uz * sin;
		double dz = ux * sin + uz * cos;
		double nearest = tx * dx + tz * dz;
		double s = nearest + Math.sqrt(start * start - pass * pass);
		Vec3 begin = new Vec3(destination.x + dx * s, target.y, destination.z + dz * s);
		return Optional.of(new Line(begin, new Vec3(destination.x, destination.y, destination.z), new Vec3(-dx, 0, -dz)));
	}

	/** How far along the line (from its start, blocks) the point nearest {@code pos} is. */
	public double along(Vec3 pos) {
		return (pos.x - start.x) * dir.x + (pos.z - start.z) * dir.z;
	}

	/** The point {@code s} blocks along the line from its start (at the start's height). */
	public Vec3 at(double s) {
		return new Vec3(start.x + dir.x * s, start.y, start.z + dir.z * s);
	}

	/** Its length, start to end. */
	public double length() {
		double dx = end.x - start.x;
		double dz = end.z - start.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** How far (across, blocks) {@code pos} is from the line. */
	public double across(Vec3 pos) {
		double px = pos.x - start.x;
		double pz = pos.z - start.z;
		return Math.abs(px * dir.z - pz * dir.x);
	}

	/** The spots of a file of {@code n} on the line at its start, {@code spacing} apart: the leader first, each next one behind. */
	public List<Vec3> file(int n, double spacing) {
		List<Vec3> spots = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			spots.add(at(-i * spacing));
		}
		return spots;
	}
}
