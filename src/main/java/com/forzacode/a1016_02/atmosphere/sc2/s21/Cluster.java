package com.forzacode.a1016_02.atmosphere.sc2.s21;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.world.phys.Vec3;

/**
 * 21's herd (docs/SELF_CREATED_2.md): at least {@code min} animals of one kind within {@code radius} of a point that is
 * {@code minDistance} to {@code maxDistance} from the target. Pure over positions and kinds, so the tests and the card
 * read the same answer.
 *
 * @param kind    the kind they share (any key: an entity type)
 * @param middle  the middle of the herd
 * @param members the herd, nearest to the middle first, at most {@code max}
 */
public record Cluster<T>(Object kind, Vec3 middle, List<T> members) {
	/** One animal: what it is ({@code item}), its kind and where it stands. */
	public record Candidate<T>(T item, Object kind, Vec3 pos) {
	}

	/**
	 * The best herd: the most animals, then the middle nearest the band's middle. Empty if no kind has {@code min}
	 * within {@code radius} of one point in the band.
	 */
	public static <T> Optional<Cluster<T>> find(List<Candidate<T>> candidates, Vec3 target, int min, int max, double minDistance, double maxDistance,
			double radius) {
		Map<Object, List<Candidate<T>>> byKind = new LinkedHashMap<>();
		for (Candidate<T> c : candidates) {
			byKind.computeIfAbsent(c.kind(), k -> new ArrayList<>()).add(c);
		}
		Cluster<T> best = null;
		double bestScore = Double.MAX_VALUE;
		int bestSize = 0;
		double band = (minDistance + maxDistance) / 2.0;
		for (Map.Entry<Object, List<Candidate<T>>> kind : byKind.entrySet()) {
			List<Candidate<T>> all = kind.getValue();
			if (all.size() < min) {
				continue;
			}
			for (Candidate<T> seed : all) {
				List<Candidate<T>> near = within(all, seed.pos(), radius);
				if (near.size() < min) {
					continue;
				}
				Vec3 middle = mean(near);
				// Once more round the middle, so the herd is the one around it, not around its first animal.
				near = within(all, middle, radius);
				if (near.size() < min) {
					continue;
				}
				middle = mean(near);
				double d = Math.sqrt(Herds.flatSqr(middle, target));
				if (d < minDistance || d > maxDistance) {
					continue;
				}
				int size = Math.min(near.size(), max);
				double score = Math.abs(d - band);
				if (size > bestSize || size == bestSize && score < bestScore) {
					Vec3 m = middle;
					List<T> members = near.stream().sorted(Comparator.comparingDouble(c -> Herds.flatSqr(c.pos(), m))).limit(max).map(Candidate::item)
							.toList();
					best = new Cluster<>(kind.getKey(), middle, members);
					bestSize = size;
					bestScore = score;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	private static <T> List<Candidate<T>> within(List<Candidate<T>> all, Vec3 at, double radius) {
		List<Candidate<T>> near = new ArrayList<>();
		double r2 = radius * radius;
		for (Candidate<T> c : all) {
			if (Herds.flatSqr(c.pos(), at) <= r2) {
				near.add(c);
			}
		}
		return near;
	}

	private static <T> Vec3 mean(List<Candidate<T>> list) {
		double x = 0;
		double y = 0;
		double z = 0;
		for (Candidate<T> c : list) {
			x += c.pos().x;
			y += c.pos().y;
			z += c.pos().z;
		}
		return new Vec3(x / list.size(), y / list.size(), z / list.size());
	}
}
