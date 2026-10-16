package com.forzacode.a1016_02.entity;

import java.util.Optional;
import java.util.OptionalDouble;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

/**
 * Where he stands in the screenshot (D-078): on the terrain, {@link Settings#nearShare} to {@link Settings#farShare}
 * (0.6 to 0.75) of the visible fog end from the camera to his middle, never nearer than {@link Settings#minDistance}
 * (12, as every sighting, D-035) nor farther than {@link Settings#maxDistance} (192, every sighting's cap), past the
 * visible fog start so the fog the frame is drawn with covers at least {@link Settings#minFog} of him, and at least
 * {@link Settings#minPixels} tall in the saved image ({@link #band}); inside the frame but off the middle (his middle
 * {@link Settings#edgeMin} to {@link Settings#edgeMax}, 15 to 35%, of the frame's width from the left or the right edge),
 * his feet and the top of his head inside it, in plain sight of the camera, facing it. The math is pure
 * ({@link #find} with a {@link Fog}, a {@link Ground} and a {@link Sight}); {@link #feetY(Level, int, int)} and
 * {@link #clear(Level, Vec3, Vec3)} are the level's answers, the same on the client and in the game tests.
 */
public final class ScreenshotSpot {
	/** His height as drawn: the player model at the player's scale, hat layer included. */
	public static final double BODY_HEIGHT = 1.875;
	/** The distance band is measured from the camera to this point of him, his middle. */
	public static final double MIDDLE = 0.9;
	/** The camera must see these two points of him: his head and his chest. */
	public static final double HEAD = 1.6;
	public static final double CHEST = 1.1;
	/** A layer he may stand on (snow, a carpet) is at most this tall. */
	private static final double MAX_LAYER = 0.5;

	private ScreenshotSpot() {
	}

	/**
	 * The spot's rules, normalized: the shares of the fog end within 0.05 to 1 (near never past far), the edge shares
	 * within 0 to 0.5 (min never past max), the frame margin within 0 to 0.25, at least one try; the distances in blocks
	 * within 0 to 4096 (the cap never under the floor), his least height in the saved image in pixels within 0 to 4096,
	 * and the least fog on him within 0 to 1.
	 */
	public record Settings(double nearShare, double farShare, double edgeMin, double edgeMax, double frameMargin, int tries, double minDistance,
			double maxDistance, double minPixels, double minFog) {
		public static final Settings DEFAULT = new Settings(0.6, 0.75, 0.15, 0.35, 0.03, 64, 12.0, 192.0, 24.0, 0.1);

		public Settings {
			nearShare = clampOr(nearShare, 0.05, 1.0, 0.6);
			farShare = Math.max(nearShare, clampOr(farShare, 0.05, 1.0, 0.75));
			edgeMin = clampOr(edgeMin, 0.0, 0.5, 0.15);
			edgeMax = Math.max(edgeMin, clampOr(edgeMax, 0.0, 0.5, 0.35));
			frameMargin = clampOr(frameMargin, 0.0, 0.25, 0.03);
			tries = Math.clamp(tries, 1, 1024);
			minDistance = clampOr(minDistance, 0.0, 4096.0, 12.0);
			maxDistance = Math.max(minDistance, clampOr(maxDistance, 0.0, 4096.0, 192.0));
			minPixels = clampOr(minPixels, 0.0, 4096.0, 24.0);
			minFog = clampOr(minFog, 0.0, 1.0, 0.1);
		}

		private static double clampOr(double value, double min, double max, double fallback) {
			return Double.isNaN(value) ? fallback : Math.clamp(value, min, max);
		}
	}

	/** Where a point lands in the frame: {@code x} from the left edge and {@code y} from the top, each 0 to 1 inside it. */
	public record Screen(double x, double y) {
		public boolean inside(double margin) {
			return x >= margin && x <= 1.0 - margin && y >= margin && y <= 1.0 - margin;
		}
	}

	/**
	 * The camera: its eye, its yaw and pitch in degrees as Minecraft has them (yaw 0 looks toward +z, 90 toward -x; a
	 * positive pitch looks down), the vertical field of view in degrees, the frame's width over its height, and the
	 * saved image's height in pixels (the main render target's).
	 */
	public record View(Vec3 eye, float yaw, float pitch, double fovDegrees, double aspect, int imageHeight) {
		/** The look, as {@code Entity.calculateViewVector}. */
		public Vec3 forward() {
			double pitchRad = Math.toRadians(pitch);
			double yawRad = Math.toRadians(-yaw);
			double cosPitch = Math.cos(pitchRad);
			return new Vec3(Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad), Math.cos(yawRad) * cosPitch);
		}

		/** The frame's right, level. */
		public Vec3 right() {
			double yawRad = Math.toRadians(yaw);
			return new Vec3(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
		}

		/** The frame's up. */
		public Vec3 up() {
			return right().cross(forward());
		}

		double tanVertical() {
			return Math.tan(Math.toRadians(Math.clamp(fovDegrees, 1.0, 179.0) * 0.5));
		}

		double tanHorizontal() {
			return tanVertical() * (aspect > 0.0 && Double.isFinite(aspect) ? aspect : 1.0);
		}

		/**
		 * How far a figure standing level with the camera, his middle at {@code screenX} of the frame's width, is still
		 * {@code minPixels} tall in the saved image ({@link #BODY_HEIGHT} over the frame's height at his depth, which off
		 * the middle is less than his distance, so he is taller there). Infinite for no least height, 0 for an image of no
		 * height.
		 */
		public double reach(double minPixels, double screenX) {
			if (!(minPixels > 0.0)) {
				return Double.POSITIVE_INFINITY;
			}
			if (imageHeight <= 0) {
				return 0.0;
			}
			double side = (2.0 * screenX - 1.0) * tanHorizontal();
			return BODY_HEIGHT * imageHeight / (2.0 * tanVertical() * minPixels) * Math.sqrt(1.0 + side * side);
		}

		/** How tall a figure standing with his feet at {@code feet} is in the saved image, in pixels (0 if he is not in front). */
		public double pixelsTall(Vec3 feet) {
			Screen bottom = project(feet);
			Screen top = project(feet.add(0.0, BODY_HEIGHT, 0.0));
			return bottom == null || top == null ? 0.0 : Math.max(0.0, (bottom.y() - top.y()) * Math.max(0, imageHeight));
		}

		/** Where {@code point} lands in the frame (it may be outside it), or null if it is not in front of the camera. */
		public @Nullable Screen project(Vec3 point) {
			Vec3 v = point.subtract(eye);
			double depth = v.dot(forward());
			if (depth <= 1.0E-6) {
				return null;
			}
			double ndcX = v.dot(right()) / (depth * tanHorizontal());
			double ndcY = v.dot(up()) / (depth * tanVertical());
			return new Screen(0.5 + 0.5 * ndcX, 0.5 - 0.5 * ndcY);
		}
	}

	/**
	 * He stands with his feet at {@code feet}, facing the camera ({@code yaw}); his middle is at {@code screenX} of the
	 * width, {@code distance} away; he is {@code pixels} tall in the saved image and the fog covers {@code fog} of him.
	 */
	public record Spot(Vec3 feet, float yaw, double screenX, double distance, double pixels, double fog) {
	}

	/**
	 * The fog the frame is drawn with, as vanilla's {@code FogData} holds it (blocks): the environmental fog, spherical,
	 * and the render-distance fog, cylindrical; the shader draws the larger of the two ({@code fog.glsl}).
	 */
	public record Fog(double environmentalStart, double environmentalEnd, double renderDistanceStart, double renderDistanceEnd) {
		/** A fog drawn from the camera ({@code 0}) to {@code end} by both, the plain fog of the game tests. */
		public static Fog fromCamera(double end) {
			return new Fog(0.0, end, 0.0, end);
		}

		/** Where the fog is complete: the nearer usable end, NaN if neither is ({@code FogReport.visibleEnd}). */
		public double visibleEnd() {
			boolean env = usable(environmentalEnd);
			boolean render = usable(renderDistanceEnd);
			if (env && render) {
				return Math.min(environmentalEnd, renderDistanceEnd);
			}
			return env ? environmentalEnd : render ? renderDistanceEnd : Double.NaN;
		}

		/** Where the fog that ends at {@link #visibleEnd} starts (the nearer start if both end there), NaN if none does. */
		public double visibleStart() {
			boolean env = usable(environmentalEnd);
			boolean render = usable(renderDistanceEnd);
			if (env && render) {
				if (environmentalEnd == renderDistanceEnd) {
					return Math.min(environmentalStart, renderDistanceStart);
				}
				return environmentalEnd < renderDistanceEnd ? environmentalStart : renderDistanceStart;
			}
			return env ? environmentalStart : render ? renderDistanceStart : Double.NaN;
		}

		/**
		 * How much the drawn fog covers a point at {@code offset} from the camera, 0 to 1, as the shader has it: the larger
		 * of the environmental fog at the spherical distance and the render-distance fog at the cylindrical one
		 * ({@code max(horizontal, |dy|)}), each linear from its start to its end.
		 */
		public double value(Vec3 offset) {
			double spherical = offset.length();
			double cylindrical = Math.max(Math.sqrt(offset.x * offset.x + offset.z * offset.z), Math.abs(offset.y));
			double env = usable(environmentalEnd) ? linear(spherical, environmentalStart, environmentalEnd) : 0.0;
			double render = usable(renderDistanceEnd) ? linear(cylindrical, renderDistanceStart, renderDistanceEnd) : 0.0;
			return Math.max(env, render);
		}

		private static double linear(double distance, double start, double end) {
			if (distance <= start) {
				return 0.0;
			}
			if (distance >= end) {
				return 1.0;
			}
			return (distance - start) / (end - start);
		}

		private static boolean usable(double end) {
			return Double.isFinite(end) && end > 0.0;
		}
	}

	/** The distances from the camera to his middle a spot may have, {@code near} to {@code far}; empty when far is under near. */
	public record Band(double near, double far) {
		public boolean isEmpty() {
			return !(far >= near);
		}

		public boolean contains(double distance) {
			return distance >= near - 1.0E-9 && distance <= far + 1.0E-9;
		}
	}

	/** The height his feet stand at on the terrain in the column {@code x, z}, or empty if he cannot stand there. */
	@FunctionalInterface
	public interface Ground {
		OptionalDouble feetY(int x, int z);
	}

	/** True if nothing blocks the view from {@code from} to {@code to}. */
	@FunctionalInterface
	public interface Sight {
		boolean clear(Vec3 from, Vec3 to);
	}

	/**
	 * The distances he may stand at in this view and fog: from the farthest of {@code nearShare} of the visible fog end,
	 * {@code minDistance} and where the visible fog covers {@code minFog} (past its start), to the nearest of
	 * {@code farShare} of the end, {@code maxDistance} and where he can still be {@code minPixels} tall (at the side of
	 * the frame where he is tallest, {@code edgeMin} from an edge: {@link View#reach}; each spot is measured exactly
	 * after). Empty without a visible fog end, or when no distance meets them all (the arm then waits): at a render
	 * distance of 32 a thin dusk fog ends about 310 blocks out, and 0.6 of it is past both caps.
	 */
	public static Band band(View view, Fog fog, Settings settings) {
		double end = fog.visibleEnd();
		if (!(end > 0.0) || !Double.isFinite(end)) {
			return new Band(Double.NaN, Double.NaN);
		}
		double start = fog.visibleStart();
		double fogged = start < end ? start + settings.minFog() * (end - start) : end;
		double near = Math.max(settings.nearShare() * end, Math.max(settings.minDistance(), fogged));
		double reach = view.reach(settings.minPixels(), settings.edgeMin());
		double far = Math.min(settings.farShare() * end, Math.min(settings.maxDistance(), reach));
		return new Band(near, far);
	}

	/** True if a point at {@code screenX} of the width is off the middle, {@code edgeMin} to {@code edgeMax} from the nearer edge. */
	public static boolean offCentre(double screenX, Settings settings) {
		if (!(screenX >= 0.0 && screenX <= 1.0)) {
			return false;
		}
		double fromEdge = Math.min(screenX, 1.0 - screenX);
		return fromEdge >= settings.edgeMin() - 1.0E-9 && fromEdge <= settings.edgeMax() + 1.0E-9;
	}

	/** {@link #find(View, Fog, Settings, Ground, Sight, RandomSource)} in a fog drawn from the camera to {@code fogEnd} ({@link Fog#fromCamera}). */
	public static Optional<Spot> find(View view, double fogEnd, Settings settings, Ground ground, Sight sight, RandomSource random) {
		return find(view, Fog.fromCamera(fogEnd), settings, ground, sight, random);
	}

	/**
	 * A spot for him in this view and fog, or empty if none fits (the arm then waits for the next screenshot). Each try
	 * picks a side and a place in that side's band of the width, and a distance in the {@link #band}; walks out level
	 * along that column of the frame to the terrain ({@code ground}, block centres), once more with the height
	 * difference taken out; and keeps the first spot whose middle is in the band and off the middle of the frame, whose
	 * feet and head are inside the frame by the margin, who is {@code minPixels} tall in the saved image and covered by
	 * {@code minFog} of the drawn fog, and whose head and chest the camera sees ({@code sight}).
	 */
	public static Optional<Spot> find(View view, Fog fog, Settings settings, Ground ground, Sight sight, RandomSource random) {
		Band band = band(view, fog, settings);
		if (band.isEmpty()) {
			return Optional.empty();
		}
		Vec3 eye = view.eye();
		Vec3 forward = view.forward();
		Vec3 right = view.right();
		double tanH = view.tanHorizontal();
		for (int i = 0; i < settings.tries(); i++) {
			boolean left = random.nextBoolean();
			double fromEdge = Mth.lerp(random.nextDouble(), settings.edgeMin(), settings.edgeMax());
			double screenX = left ? fromEdge : 1.0 - fromEdge;
			double distance = Mth.lerp(random.nextDouble(), band.near(), band.far());
			// The column of the frame at that x, level: the look plus the sideways part of that column's ray.
			Vec3 ray = forward.add(right.scale((2.0 * screenX - 1.0) * tanH));
			double length = Math.sqrt(ray.x * ray.x + ray.z * ray.z);
			if (length < 1.0E-6) {
				continue; // looking straight up or down
			}
			double dx = ray.x / length;
			double dz = ray.z / length;
			double horizontal = distance;
			Optional<Spot> spot = Optional.empty();
			for (int pass = 0; pass < 2 && spot.isEmpty(); pass++) {
				int column = Mth.floor(eye.x + dx * horizontal);
				int row = Mth.floor(eye.z + dz * horizontal);
				OptionalDouble y = ground.feetY(column, row);
				if (y.isEmpty()) {
					break;
				}
				Vec3 feet = new Vec3(column + 0.5, y.getAsDouble(), row + 0.5);
				Vec3 middle = feet.add(0.0, MIDDLE, 0.0);
				double actual = middle.distanceTo(eye);
				if (band.contains(actual)) {
					spot = fits(view, fog, feet, actual, settings, sight);
					break;
				}
				double rise = middle.y - eye.y;
				if (Math.abs(rise) >= distance) {
					break;
				}
				horizontal = Math.sqrt(distance * distance - rise * rise);
			}
			if (spot.isPresent()) {
				return spot;
			}
		}
		return Optional.empty();
	}

	/** The spot at {@code feet} if he is off the middle, inside the frame, tall enough, fogged and seen; else empty. */
	static Optional<Spot> fits(View view, Fog fog, Vec3 feet, double distance, Settings settings, Sight sight) {
		Screen middle = view.project(feet.add(0.0, MIDDLE, 0.0));
		Screen bottom = view.project(feet);
		Screen top = view.project(feet.add(0.0, BODY_HEIGHT, 0.0));
		if (middle == null || bottom == null || top == null || !offCentre(middle.x(), settings)) {
			return Optional.empty();
		}
		if (!bottom.inside(settings.frameMargin()) || !top.inside(settings.frameMargin())) {
			return Optional.empty();
		}
		double pixels = view.pixelsTall(feet);
		if (!(pixels >= settings.minPixels())) {
			return Optional.empty();
		}
		Vec3 eye = view.eye();
		double fogged = fog.value(feet.add(0.0, MIDDLE, 0.0).subtract(eye));
		if (!(fogged >= settings.minFog())) {
			return Optional.empty();
		}
		if (!sight.clear(eye, feet.add(0.0, HEAD, 0.0)) || !sight.clear(eye, feet.add(0.0, CHEST, 0.0))) {
			return Optional.empty();
		}
		return Optional.of(new Spot(feet, HimEntity.yawToward(feet, eye), middle.x(), distance, pixels, fogged));
	}

	/**
	 * Where his feet stand on the terrain in this column, or empty: on the top of the column's highest block that
	 * blocks motion and is not leaves ({@code MOTION_BLOCKING_NO_LEAVES}, a heightmap every client has), which must be
	 * dry and hold him (a sturdy top face, or a snow layer on one), on any thin layer lying on it (snow, a carpet); with
	 * room for his body above, no block he would stand in (leaves over a trunk, a roof) and no water. A column in a
	 * chunk that is not loaded has no ground.
	 */
	public static OptionalDouble feetY(Level level, int x, int z) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return OptionalDouble.empty();
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (top <= level.getMinY() || top >= level.getMaxY()) {
			return OptionalDouble.empty();
		}
		BlockPos floor = new BlockPos(x, top - 1, z);
		BlockState floorState = level.getBlockState(floor);
		if (!floorState.getFluidState().isEmpty()) {
			return OptionalDouble.empty();
		}
		double feet;
		if (floorState.isFaceSturdy(level, floor, Direction.UP)) {
			feet = top;
			BlockPos above = floor.above();
			VoxelShape layer = level.getBlockState(above).getCollisionShape(level, above);
			if (!layer.isEmpty()) {
				double height = layer.max(Direction.Axis.Y);
				if (height > MAX_LAYER) {
					return OptionalDouble.empty();
				}
				feet = top + height;
			}
		} else {
			BlockPos below = floor.below();
			VoxelShape shape = floorState.getCollisionShape(level, floor);
			double height = shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y);
			if (height > MAX_LAYER || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
				return OptionalDouble.empty();
			}
			feet = floor.getY() + height;
		}
		return room(level, x, z, feet) ? OptionalDouble.of(feet) : OptionalDouble.empty();
	}

	/** Room for his body standing at {@code feet}: no water and nothing solid in any cell it reaches (the layer he stands on aside). */
	private static boolean room(Level level, int x, int z, double feet) {
		int from = Mth.floor(feet);
		int to = Mth.floor(feet + BODY_HEIGHT);
		for (int y = from; y <= to; y++) {
			BlockPos cell = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(cell);
			if (!state.getFluidState().isEmpty()) {
				return false;
			}
			VoxelShape shape = state.getCollisionShape(level, cell);
			if (shape.isEmpty()) {
				continue;
			}
			boolean underHim = y == from && y + shape.max(Direction.Axis.Y) <= feet + 1.0E-6;
			if (!underHim) {
				return false;
			}
		}
		return true;
	}

	/**
	 * True if the camera at {@code from} sees {@code to}: no block's visual shape on the line (leaves hide him, glass
	 * and plants do not; water does not count).
	 */
	public static boolean clear(Level level, Vec3 from, Vec3 to) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
	}
}
