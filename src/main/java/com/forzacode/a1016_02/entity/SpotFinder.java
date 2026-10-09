package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Finds where he stands: in the band just inside the fog edge, never closer than the minimum distance, on solid
 * dry ground, with his whole bounding box out of view. Never loads chunks (unloaded columns are skipped).
 */
public final class SpotFinder {
	/**
	 * A place to stand.
	 *
	 * @param pos      his feet
	 * @param distance horizontal distance from the player
	 * @param anchor   the trunk or the light he relates to, if any
	 */
	public record Spot(Vec3 pos, double distance, double score, @Nullable BlockPos anchor) {
	}

	/**
	 * What to look for.
	 *
	 * @param feet        the player's feet
	 * @param eye         the player's eyes
	 * @param inner       nearest horizontal distance
	 * @param outer       farthest horizontal distance
	 * @param minDistance never closer than this to the eyes, whatever the band says
	 * @param hidden      true if a box (his {@link HimEntity#viewBox}) is out of view of every player
	 * @param allowed     extra rule for a feet block (loaded and ticking, not at the base, far from the last sighting)
	 */
	public record Query(ServerLevel level, Vec3 feet, Vec3 eye, double inner, double outer, double minDistance, EntityDimensions dims,
			Predicate<AABB> hidden, Predicate<BlockPos> allowed, RandomSource random, int samples) {
	}

	private static final int OPEN_ENOUGH = 16;

	private SpotFinder() {
	}

	/** Anywhere in the band. */
	public static Optional<Spot> open(Query q) {
		List<Spot> found = new ArrayList<>();
		for (Vec3 point : ring(q, 2)) {
			Vec3 feet = standAt(q.level(), point.x, point.z, q.dims());
			if (feet != null && valid(q, feet, q.inner(), q.outer())) {
				found.add(new Spot(feet, horizontal(q.feet(), feet), q.random().nextDouble(), null));
				if (found.size() >= OPEN_ENOUGH) {
					break;
				}
			}
		}
		return best(found);
	}

	/** Like {@link #open} but scored; spots scoring 0 or less are skipped (the "close" variant's known places). */
	public static Optional<Spot> scored(Query q, ToDoubleFunction<Vec3> score) {
		List<Spot> found = new ArrayList<>();
		for (Vec3 point : ring(q, 2)) {
			Vec3 feet = standAt(q.level(), point.x, point.z, q.dims());
			if (feet == null) {
				continue;
			}
			double s = score.applyAsDouble(feet);
			if (s > 0 && valid(q, feet, q.inner(), q.outer())) {
				found.add(new Spot(feet, horizontal(q.feet(), feet), s + q.random().nextDouble() * 0.5, null));
			}
		}
		return best(found);
	}

	/** A crest above the player's eyes with the ground falling away behind it, open to the sky. */
	public static Optional<Spot> ridge(Query q, double minRise) {
		ServerLevel level = q.level();
		List<Spot> found = new ArrayList<>();
		for (Vec3 point : ring(q, 3)) {
			Vec3 feet = standAt(level, point.x, point.z, q.dims());
			if (feet == null) {
				continue;
			}
			double rise = feet.y - q.eye().y;
			if (rise < minRise || !level.canSeeSky(BlockPos.containing(feet).above(2))) {
				continue;
			}
			Vec3 away = feet.subtract(q.feet()).horizontal().normalize();
			double behind = Math.max(surfaceY(level, feet.add(away.scale(3))), surfaceY(level, feet.add(away.scale(5))));
			if (behind > feet.y - 1 || !valid(q, feet, q.inner(), q.outer())) {
				continue;
			}
			found.add(new Spot(feet, horizontal(q.feet(), feet), rise + q.random().nextDouble() * 2.0, null));
		}
		return best(found);
	}

	/**
	 * Just behind a tree trunk as seen from the player, offset sideways so half of him shows past it.
	 *
	 * @param groveBonus extra score for a trunk position (for example inside a known bare grove)
	 */
	public static Optional<Spot> trunk(Query q, ToDoubleFunction<BlockPos> groveBonus) {
		ServerLevel level = q.level();
		List<Spot> found = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (Vec3 point : ring(q, 1)) {
			int px = Mth.floor(point.x);
			int pz = Mth.floor(point.z);
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					BlockPos base = trunkBase(level, px + dx, pz + dz, m);
					if (base == null) {
						continue;
					}
					Vec3 center = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
					Vec3 dir = center.subtract(q.feet()).horizontal().normalize();
					Vec3 side = new Vec3(-dir.z, 0.0, dir.x).scale(q.random().nextBoolean() ? 0.45 : -0.45);
					Vec3 spot = center.add(dir.scale(0.9)).add(side);
					Vec3 feet = standAt(level, spot.x, spot.z, q.dims());
					if (feet == null || Math.abs(feet.y - base.getY()) > 1.0 || !valid(q, feet, q.inner(), q.outer())) {
						continue;
					}
					double bareness = 1.0 - leavesAround(level, base, m) / 60.0;
					found.add(new Spot(feet, horizontal(q.feet(), feet), groveBonus.applyAsDouble(base) + bareness + q.random().nextDouble(), base));
				}
			}
		}
		return best(found);
	}

	/**
	 * At the edge of a light's glow (block light within {@code [minLight, maxLight]} at his feet), and that edge in the
	 * band like every other spot. Prefers spots where facing the light turns his back or side to the player.
	 */
	public static Optional<Spot> light(Query q, List<BlockPos> lights, int radius, int minLight, int maxLight) {
		ServerLevel level = q.level();
		List<Spot> found = new ArrayList<>();
		double mid = (minLight + maxLight) / 2.0;
		for (BlockPos light : lights) {
			Vec3 lightCenter = Vec3.atCenterOf(light);
			for (int r = 3; r <= radius; r++) {
				int steps = 8 + r * 2;
				double start = q.random().nextDouble() * Math.PI * 2;
				for (int i = 0; i < steps; i++) {
					double angle = start + i * Math.PI * 2 / steps;
					Vec3 feet = standAt(level, light.getX() + 0.5 + Math.cos(angle) * r, light.getZ() + 0.5 + Math.sin(angle) * r, q.dims());
					if (feet == null || Math.abs(feet.y - light.getY()) > 6) {
						continue;
					}
					int blockLight = level.getBrightness(LightLayer.BLOCK, BlockPos.containing(feet));
					if (blockLight < minLight || blockLight > maxLight || !valid(q, feet, q.inner(), q.outer())) {
						continue;
					}
					Vec3 toLight = lightCenter.subtract(feet).horizontal().normalize();
					Vec3 toPlayer = q.feet().subtract(feet).horizontal().normalize();
					double notFacingYou = 1.0 - toLight.dot(toPlayer);
					double score = notFacingYou - Math.abs(blockLight - mid) * 0.3 + q.random().nextDouble() * 0.5;
					found.add(new Spot(feet, horizontal(q.feet(), feet), score, light));
				}
			}
			if (found.size() >= OPEN_ENOUGH) {
				break;
			}
		}
		return best(found);
	}

	/** Dry ground on the far side of water: most of the line from the player to him is water, right up to his shore. */
	public static Optional<Spot> shore(Query q, double minWaterFraction) {
		ServerLevel level = q.level();
		List<Spot> found = new ArrayList<>();
		for (Vec3 point : ring(q, 3)) {
			Vec3 feet = standAt(level, point.x, point.z, q.dims());
			if (feet == null || level.getBlockState(BlockPos.containing(feet).below()).is(BlockTags.ICE)) {
				continue;
			}
			double fraction = waterBetween(level, q.feet(), feet);
			if (fraction < minWaterFraction || !valid(q, feet, q.inner(), q.outer())) {
				continue;
			}
			found.add(new Spot(feet, horizontal(q.feet(), feet), fraction + q.random().nextDouble() * 0.2, null));
		}
		return best(found);
	}

	/**
	 * Among existing mobs (D-034: the endermen in the End, the zombified piglins in the Nether): ground beside at least
	 * one of them, within {@code radius} blocks, never closer than {@code minGap} to any (he never stands inside one).
	 * More of them around scores higher. Ground is looked for near each mob's own height, so a ceiling (the Nether's
	 * bedrock roof) is never taken for the ground.
	 *
	 * @param mobs feet of the mobs, in or near the band
	 */
	public static Optional<Spot> among(Query q, List<Vec3> mobs, double radius, double minGap) {
		List<Spot> found = new ArrayList<>();
		double reach = Math.max(minGap + 0.5, radius);
		for (Vec3 mob : mobs) {
			double d = horizontal(q.feet(), mob);
			if (d < q.inner() - reach || d > q.outer() + reach) {
				continue;
			}
			for (int i = 0; i < 12; i++) {
				double angle = q.random().nextDouble() * Math.PI * 2;
				double r = minGap + q.random().nextDouble() * (reach - minGap);
				Vec3 feet = standNear(q.level(), mob.x + Math.cos(angle) * r, mob.z + Math.sin(angle) * r, mob.y, 2, 3, q.dims());
				if (feet == null || !valid(q, feet, q.inner(), q.outer())) {
					continue;
				}
				int around = 0;
				boolean clear = true;
				for (Vec3 other : mobs) {
					double h = horizontal(other, feet);
					if (h < minGap && Math.abs(other.y - feet.y) < 3.0) {
						clear = false;
						break;
					}
					if (h <= radius) {
						around++;
					}
				}
				if (clear && around > 0) {
					found.add(new Spot(feet, horizontal(q.feet(), feet), around + q.random().nextDouble(), null));
				}
			}
			if (found.size() >= OPEN_ENOUGH) {
				break;
			}
		}
		return best(found);
	}

	// --- geometry and terrain ---

	/**
	 * Feet position on the ground at this column near {@code aroundY} (from {@code up} blocks above it to {@code down}
	 * below), or null: dry, sturdy, not unsafe, and his box fits. Unlike {@link #standAt} it never looks at the
	 * heightmap, so it works under a ceiling and on narrow End islands.
	 */
	public static @Nullable Vec3 standNear(ServerLevel level, double x, double z, double aroundY, int up, int down, EntityDimensions dims) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		if (!level.hasChunkAt(bx, bz)) {
			return null;
		}
		int base = Mth.floor(aroundY + 1.0E-6);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		// Nearest height first: as it is, a block up, a block down, two up, two down...
		for (int k = 0; k <= 2 * Math.max(up, down); k++) {
			int dy = k % 2 == 1 ? (k + 1) / 2 : -(k / 2);
			if (dy > up || dy < -down) {
				continue;
			}
			int feetY = base + dy;
			m.set(bx, feetY - 1, bz);
			if (feetY - 1 < level.getMinY()) {
				continue;
			}
			BlockState ground = level.getBlockState(m);
			if (!ground.getFluidState().isEmpty() || !ground.isFaceSturdy(level, m, Direction.UP) || unsafe(ground)) {
				continue;
			}
			Vec3 feet = new Vec3(x, feetY, z);
			AABB box = dims.makeBoundingBox(feet);
			if (level.noCollision(box) && !level.containsAnyLiquid(box.inflate(0.1))) {
				return feet;
			}
		}
		return null;
	}

	/** Feet position on the ground at this column, or null if the column is unloaded, wet, unsafe or too tight. */
	public static @Nullable Vec3 standAt(ServerLevel level, double x, double z, EntityDimensions dims) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		if (!level.hasChunkAt(bx, bz)) {
			return null;
		}
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1, bz);
		for (int guard = 0; guard < 48 && m.getY() > level.getMinY(); guard++) {
			BlockState state = level.getBlockState(m);
			if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty()) {
				m.move(Direction.DOWN);
				continue;
			}
			break;
		}
		BlockState ground = level.getBlockState(m);
		if (!ground.getFluidState().isEmpty() || !ground.isFaceSturdy(level, m, Direction.UP) || unsafe(ground)) {
			return null;
		}
		Vec3 feet = new Vec3(x, m.getY() + 1.0, z);
		AABB box = dims.makeBoundingBox(feet);
		if (!level.noCollision(box) || level.containsAnyLiquid(box.inflate(0.1))) {
			return null;
		}
		return feet;
	}

	private static boolean unsafe(BlockState ground) {
		return ground.is(Blocks.MAGMA_BLOCK) || ground.is(BlockTags.CAMPFIRES) || ground.is(BlockTags.FIRE) || ground.is(BlockTags.LOGS)
				|| ground.is(BlockTags.LEAVES);
	}

	/** The lowest log of a trunk at least 3 logs tall standing on solid ground, or null. */
	private static @Nullable BlockPos trunkBase(ServerLevel level, int x, int z, BlockPos.MutableBlockPos m) {
		if (!level.hasChunkAt(x, z)) {
			return null;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		m.set(x, top, z);
		if (!level.getBlockState(m).is(BlockTags.LOGS)) {
			return null;
		}
		int y = top;
		while (top - y < 32 && level.getBlockState(m.set(x, y - 1, z)).is(BlockTags.LOGS)) {
			y--;
		}
		if (top - y < 2) {
			return null;
		}
		m.set(x, y - 1, z);
		BlockState under = level.getBlockState(m);
		if (!under.isFaceSturdy(level, m, Direction.UP) || under.is(BlockTags.LOGS) || under.is(BlockTags.LEAVES)) {
			return null;
		}
		return new BlockPos(x, y, z);
	}

	/** Leaves in a 5x5x4 box around the top of the trunk (a bare tree has none). */
	private static int leavesAround(ServerLevel level, BlockPos base, BlockPos.MutableBlockPos m) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ());
		int count = 0;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = -1; dy <= 2; dy++) {
					if (level.getBlockState(m.set(base.getX() + dx, top + dy, base.getZ() + dz)).is(BlockTags.LEAVES)) {
						count++;
					}
				}
			}
		}
		return count;
	}

	/** Top of the motion-blocking (non-leaf) terrain at a point, or +infinity if unloaded. */
	private static double surfaceY(ServerLevel level, Vec3 at) {
		int x = Mth.floor(at.x);
		int z = Mth.floor(at.z);
		return level.hasChunkAt(x, z) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : Double.POSITIVE_INFINITY;
	}

	/**
	 * Share of the columns between the two points whose surface is water (or ice), or 0 if the water does not reach
	 * within 4 blocks of {@code to} or any column is unloaded.
	 */
	public static double waterBetween(ServerLevel level, Vec3 from, Vec3 to) {
		double dist = horizontal(from, to);
		int n = Math.max(2, (int) (dist / 1.5));
		int water = 0;
		boolean reachesShore = false;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int i = 1; i < n; i++) {
			double t = i / (double) n;
			int x = Mth.floor(Mth.lerp(t, from.x, to.x));
			int z = Mth.floor(Mth.lerp(t, from.z, to.z));
			if (!level.hasChunkAt(x, z)) {
				return 0.0;
			}
			m.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
			BlockState top = level.getBlockState(m);
			if (top.getFluidState().is(FluidTags.WATER) || top.is(BlockTags.ICE)) {
				water++;
				if (dist * (1.0 - t) <= 4.0) {
					reachesShore = true;
				}
			}
		}
		return reachesShore ? water / (double) (n - 1) : 0.0;
	}

	/**
	 * In the band, no part of his rendered model ({@link HimEntity#viewBox}) closer than the minimum distance to the
	 * eyes, allowed, and that whole box out of view.
	 */
	private static boolean valid(Query q, Vec3 feet, double inner, double outer) {
		double h = horizontal(q.feet(), feet);
		if (h < inner - 0.5 || h > outer + 0.5) {
			return false;
		}
		AABB view = HimEntity.viewBox(feet);
		if (view.distanceToSqr(q.eye()) < q.minDistance() * q.minDistance()) {
			return false;
		}
		return q.allowed().test(BlockPos.containing(feet)) && q.hidden().test(view);
	}

	private static List<Vec3> ring(Query q, int perAngle) {
		List<Vec3> points = new ArrayList<>(q.samples() * perAngle);
		double start = q.random().nextDouble() * Math.PI * 2;
		for (int i = 0; i < q.samples(); i++) {
			double angle = start + i * Math.PI * 2 / q.samples();
			for (int k = 0; k < perAngle; k++) {
				double d = q.inner() + q.random().nextDouble() * (q.outer() - q.inner());
				points.add(new Vec3(q.feet().x + Math.cos(angle) * d, q.feet().y, q.feet().z + Math.sin(angle) * d));
			}
		}
		return points;
	}

	static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static Optional<Spot> best(List<Spot> found) {
		return found.stream().max(Comparator.comparingDouble(Spot::score));
	}
}
