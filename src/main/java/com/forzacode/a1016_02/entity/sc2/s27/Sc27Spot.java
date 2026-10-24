package com.forzacode.a1016_02.entity.sc2.s27;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.entity.SpotFinder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Where 27's copy stands and which way it walks off (docs/SELF_CREATED_2.md, 27), on the server. The spot: on ground a
 * player could stand on, dry, with sky over it, in the band ({@link Sc27Rules#band}) and within {@link Sc27Rules#NAME_REACH}
 * of the viewer's eyes, {@link Sc27Rules#OFF_FACING_MIN} to {@link Sc27Rules#OFF_FACING_MAX} degrees off their facing (out of
 * their view now, there when they turn: the client only puts it there once it is off the screen), and wholly in plain sight
 * from their eyes once they turn ({@link Sight#wholly}). Better at the edge of trees, a little above them, and in the dark.
 * The way off: away from the viewer, turned toward whatever hides a walker soonest (trees, a crest, the fog) once past the
 * name's range, tried with the walk itself ({@link Walk}).
 */
public final class Sc27Spot {
	/** How many places the search tries. */
	static final int SAMPLES = 40;
	/** R10-8: a spot on the side the viewer is turning or walking toward is better by this... */
	public static final double TURNING_SIDE = 1.5;
	/** ...and one nearer the edge of their screen (40 off rather than 80) by up to this. */
	public static final double NEAR_EDGE = 0.8;
	/** The way off: this many steps tried along each bearing... */
	static final int WALK_STEPS = 44;
	/** ...turned this much from straight away. */
	static final double[] BEARINGS = {0, 25, -25, 50, -50, 75, -75};

	/** The spot: the copy's feet, the yaw that faces the viewer, the way it walks off, how far, the score. */
	public record Spot(Vec3 feet, float yaw, float walkYaw, double distance, double score) {
	}

	private Sc27Spot() {
	}

	/** A player's size. */
	static EntityDimensions dims() {
		return EntityTypes.PLAYER.getDimensions();
	}

	/**
	 * The best spot for a viewer with these eyes and feet looking along {@code viewerYaw}, in {@code band}, or empty.
	 *
	 * @param fogEnd the viewer's drawn fog end (NaN if unknown), for the way off: a walker past {@code 0.95} of it is gone
	 */
	public static Optional<Spot> find(ServerLevel level, Vec3 eye, Vec3 feet, float viewerYaw, Sc27Rules.Band band, double fogEnd, RandomSource random) {
		return find(level, eye, feet, viewerYaw, band, fogEnd, random, 0);
	}

	/**
	 * {@link #find(ServerLevel, Vec3, Vec3, float, Sc27Rules.Band, double, RandomSource)} for a viewer turning or walking
	 * toward {@code turning} (+1 a growing yaw, -1 the other way, 0 neither; R10-8): a spot on that side is better by
	 * {@link #TURNING_SIDE}, and one just off the edge of their screen (nearer the facing) by up to {@link #NEAR_EDGE}, so the
	 * copy is where they are about to look, never on the screen when it comes.
	 */
	public static Optional<Spot> find(ServerLevel level, Vec3 eye, Vec3 feet, float viewerYaw, Sc27Rules.Band band, double fogEnd, RandomSource random,
			int turning) {
		if (band.empty()) {
			return Optional.empty();
		}
		List<Spot> found = new ArrayList<>();
		for (int i = 0; i < SAMPLES; i++) {
			double off = Sc27Rules.OFF_FACING_MIN + random.nextDouble() * (Sc27Rules.OFF_FACING_MAX - Sc27Rules.OFF_FACING_MIN);
			int side = random.nextBoolean() ? 1 : -1;
			double bearing = viewerYaw + side * off;
			double d = band.inner() + random.nextDouble() * (band.outer() - band.inner());
			double rad = Math.toRadians(bearing);
			double x = feet.x - Math.sin(rad) * d;
			double z = feet.z + Math.cos(rad) * d;
			Vec3 at = SpotFinder.standAt(level, x, z, dims());
			if (at == null || !valid(level, eye, feet, viewerYaw, band, at)) {
				continue;
			}
			double toward = (turning != 0 && side == turning ? TURNING_SIDE : 0.0)
					+ NEAR_EDGE * (1.0 - (off - Sc27Rules.OFF_FACING_MIN) / (Sc27Rules.OFF_FACING_MAX - Sc27Rules.OFF_FACING_MIN));
			found.add(new Spot(at, Sc27Rules.yawToward(at.x, at.z, eye.x, eye.z), 0.0F, horizontal(feet, at),
					score(level, eye, at) + toward + random.nextDouble() * 0.5));
		}
		Optional<Spot> best = found.stream().max(Comparator.comparingDouble(Spot::score));
		return best.map(s -> new Spot(s.feet(), s.yaw(), (float) wayOff(level, eye, s.feet(), fogEnd), s.distance(), s.score()));
	}

	/**
	 * Whether the copy may stand with its feet at {@code at}: dry ground with sky over it, in the band, within the name's
	 * reach and at least the band's inner edge from the eyes, off the viewer's facing, wholly in plain sight from the eyes.
	 */
	public static boolean valid(ServerLevel level, Vec3 eye, Vec3 feet, float viewerYaw, Sc27Rules.Band band, Vec3 at) {
		double h = horizontal(feet, at);
		if (h < band.inner() - 0.5 || h > band.outer() + 0.5) {
			return false;
		}
		double d3 = eye.distanceTo(at);
		if (d3 > Sc27Rules.NAME_REACH || d3 < band.inner() - 0.5) {
			return false;
		}
		if (!Sc27Rules.offFacing(viewerYaw, Sc27Rules.yawToward(eye.x, eye.z, at.x, at.z))) {
			return false;
		}
		BlockPos cell = BlockPos.containing(at.x, at.y + 1.0E-4, at.z);
		if (!level.canSeeSky(cell)) {
			return false;
		}
		return Sight.wholly(level, eye, at);
	}

	/**
	 * The spot's score: trees near it (a trunk within 2 to 6 blocks: the edge of the trees) 1, standing above the viewer's
	 * feet up to 0.4, in the dark (no block light) 0.3.
	 */
	static double score(ServerLevel level, Vec3 eye, Vec3 at) {
		double score = 0.0;
		BlockPos base = BlockPos.containing(at);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		search:
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 < 4 || d2 > 36) {
					continue;
				}
				for (int dy = -1; dy <= 3; dy++) {
					if (level.getBlockState(m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz)).is(BlockTags.LOGS)) {
						score += 1.0;
						break search;
					}
				}
			}
		}
		double rise = at.y - (eye.y - 1.62);
		score += 0.4 * Mth.clamp(rise / 6.0, 0.0, 1.0);
		if (level.getBrightness(LightLayer.BLOCK, base.above()) == 0) {
			score += 0.3;
		}
		return score;
	}

	/**
	 * The way off from {@code from}: straight away from the eyes, or turned up to 75 degrees, whichever puts a walker out of
	 * the viewer's sight soonest ({@link #hiddenAfter}); straight away when none does within {@link #WALK_STEPS}.
	 */
	public static double wayOff(ServerLevel level, Vec3 eye, Vec3 from, double fogEnd) {
		double away = Sc27Rules.yawToward(eye.x, eye.z, from.x, from.z);
		double best = away;
		double bestCost = Double.POSITIVE_INFINITY;
		for (double turn : BEARINGS) {
			double bearing = away + turn;
			int steps = hiddenAfter(level, eye, from, bearing, fogEnd);
			double cost = (steps < 0 ? WALK_STEPS * 2 : steps) + Math.abs(turn) / 25.0;
			if (cost < bestCost) {
				bestCost = cost;
				best = bearing;
			}
		}
		return Mth.wrapDegrees(best);
	}

	/**
	 * How many steps a walker from {@code from} along {@code bearing} takes until the viewer at {@code eye} no longer sees it:
	 * past the name's range ({@link Sc27Rules#NAME_RANGE}) and either out of plain sight ({@link Sight#body}) or past 0.95 of
	 * the fog end; -1 if not within {@link #WALK_STEPS} (or the way is blocked first).
	 */
	public static int hiddenAfter(ServerLevel level, Vec3 eye, Vec3 from, double bearing, double fogEnd) {
		Vec3 at = from;
		double rad = Math.toRadians(bearing);
		for (int i = 1; i <= WALK_STEPS; i++) {
			// Never a chunk load: the way stops where the loaded land does.
			if (!level.isLoaded(BlockPos.containing(at.x - Math.sin(rad) * 3.0, at.y, at.z + Math.cos(rad) * 3.0))) {
				return -1;
			}
			Walk.Step step = Walk.next(level, at, bearing, 1.0);
			if (step == null) {
				return -1;
			}
			at = step.feet();
			double d = eye.distanceTo(at);
			if (d <= Sc27Rules.NAME_RANGE) {
				continue;
			}
			if (Double.isFinite(fogEnd) && d >= 0.95 * fogEnd || !Sight.body(level, eye, at)) {
				return i;
			}
		}
		return -1;
	}

	static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
