package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Every block, item or light change "he" makes goes through here, plus the out-of-view check (D-012).
 * Every edit returns false and changes nothing if anything it would change is in view of any player. Edits are
 * silent: no drops, particles or sounds, containers never spill, and neighbours that would break (a torch on a
 * removed block, the other door half) are removed silently too and written to the ledger. Removals, moves,
 * conversions and stack changes go to the {@link TraceLedger} so Ending D can undo them; {@link #leave} places
 * things "left by others" and is never undone. Server thread only.
 */
public final class TraceService {
	/**
	 * Where a player looks from.
	 *
	 * @param eye          eye position
	 * @param look         unit look vector
	 * @param body         the player's bounding box, for the "within 3 blocks" rule
	 * @param viewDistance how far they can see, in blocks
	 */
	public record Viewer(Vec3 eye, Vec3 look, AABB body, double viewDistance) {
		public static Viewer of(Player player, int viewDistanceChunks) {
			return new Viewer(player.getEyePosition(), player.getViewVector(1.0F), player.getBoundingBox(), (viewDistanceChunks + 1) * 16.0);
		}
	}

	/** Half the diagonal of a block: how far a block's corners reach from its center. */
	private static final double BLOCK_RADIUS = 0.87;

	private final boolean force;
	private @Nullable TraceService forced;

	TraceService(boolean force) {
		this.force = force;
	}

	/** Tests and debug only: the same service without the view check. Edits are still silent and ledgered. */
	public TraceService forced() {
		if (force) {
			return this;
		}
		if (forced == null) {
			forced = new TraceService(true);
		}
		return forced;
	}

	// --- out-of-view check ---

	public boolean isOutOfView(ServerLevel level, BlockPos pos) {
		return isOutOfView(level, new AABB(pos));
	}

	/**
	 * False if any player in the level is within {@code viewNearBlocks} (3) of the box, or has line of sight to it
	 * inside a {@code viewConeDegrees} (160°) cone within their view distance. Only opaque full blocks block sight;
	 * leaves, glass, ice and the like are see-through. Unloaded points count as unseen.
	 */
	public boolean isOutOfView(ServerLevel level, AABB box) {
		Pacing pacing = ModConfig.pacing();
		return isOutOfView(level, box, viewers(level), pacing.viewNearBlocks, pacing.viewConeDegrees);
	}

	/**
	 * Like {@link #isOutOfView(ServerLevel, AABB)} for a set of blocks, checking each one that can be seen at all
	 * (it has a neighbour that is not an opaque full block). This is what every edit uses.
	 */
	public boolean isOutOfView(ServerLevel level, Collection<BlockPos> positions) {
		Pacing pacing = ModConfig.pacing();
		return positionsOutOfView(level, positions, viewers(level), pacing.viewNearBlocks, pacing.viewConeDegrees);
	}

	/** Every player in the level as a {@link Viewer}, using the server view distance (the conservative bound). */
	public static List<Viewer> viewers(ServerLevel level) {
		int chunks = level.getServer().getPlayerList().getViewDistance();
		List<Viewer> viewers = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			viewers.add(Viewer.of(player, chunks));
		}
		return viewers;
	}

	/** The geometry behind {@link #isOutOfView(ServerLevel, AABB)}, usable with any viewpoints. */
	public static boolean isOutOfView(Level level, AABB box, List<Viewer> viewers, double nearBlocks, double coneDegrees) {
		for (Viewer viewer : viewers) {
			if (sees(level, viewer, box, nearBlocks, coneDegrees)) {
				return false;
			}
		}
		return true;
	}

	/** The geometry behind {@link #isOutOfView(ServerLevel, Collection)}, usable with any viewpoints. */
	public static boolean positionsOutOfView(Level level, Collection<BlockPos> positions, List<Viewer> viewers, double nearBlocks, double coneDegrees) {
		if (viewers.isEmpty() || positions.isEmpty()) {
			return true;
		}
		AABB bounds = null;
		for (BlockPos pos : positions) {
			bounds = bounds == null ? new AABB(pos) : bounds.minmax(new AABB(pos));
		}
		List<BlockPos> exposed = positions.stream().filter(pos -> isExposed(level, pos)).toList();
		double halfCone = Math.toRadians(coneDegrees / 2.0);
		for (Viewer viewer : viewers) {
			AABB reach = viewer.body().inflate(nearBlocks);
			if (reach.intersects(bounds)) {
				for (BlockPos pos : positions) {
					if (reach.intersects(pos)) {
						return false;
					}
				}
			}
			if (bounds.distanceToSqr(viewer.eye()) > viewer.viewDistance() * viewer.viewDistance()) {
				continue;
			}
			for (BlockPos pos : exposed) {
				if (seesBlock(level, viewer, pos, halfCone)) {
					return false;
				}
			}
		}
		return true;
	}

	/** True if this viewer is near the box or can see any sample point of it. */
	public static boolean sees(Level level, Viewer viewer, AABB box, double nearBlocks, double coneDegrees) {
		if (viewer.body().inflate(nearBlocks).intersects(box)) {
			return true;
		}
		double cosHalfCone = Math.cos(Math.toRadians(coneDegrees / 2.0));
		for (Vec3 point : samplePoints(box)) {
			if (pointInView(viewer, point, cosHalfCone) && level.isLoaded(BlockPos.containing(point)) && lineOfSight(level, viewer.eye(), point, box)) {
				return true;
			}
		}
		return false;
	}

	/** A block can only be seen if one of its neighbours is not an opaque full block (unloaded counts as open). */
	static boolean isExposed(Level level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			BlockPos n = pos.relative(dir);
			if (!level.isLoaded(n) || !level.getBlockState(n).isSolidRender()) {
				return true;
			}
		}
		return false;
	}

	/** Cheap distance and cone rejects for the whole block first, then line of sight to 15 points on it. */
	private static boolean seesBlock(Level level, Viewer viewer, BlockPos pos, double halfCone) {
		Vec3 toCenter = Vec3.atCenterOf(pos).subtract(viewer.eye());
		double dist = toCenter.length();
		if (dist - BLOCK_RADIUS > viewer.viewDistance() || !level.isLoaded(pos)) {
			return false;
		}
		if (dist > BLOCK_RADIUS) {
			double angle = Math.acos(Mth.clamp(toCenter.dot(viewer.look()) / dist, -1.0, 1.0));
			if (angle - Math.asin(BLOCK_RADIUS / dist) > halfCone) {
				return false;
			}
		}
		AABB box = new AABB(pos);
		double cosHalfCone = Math.cos(halfCone);
		for (Vec3 point : blockPoints(box)) {
			if (pointInView(viewer, point, cosHalfCone) && lineOfSight(level, viewer.eye(), point, box)) {
				return true;
			}
		}
		return false;
	}

	private static boolean pointInView(Viewer viewer, Vec3 point, double cosHalfCone) {
		Vec3 toPoint = point.subtract(viewer.eye());
		double distSqr = toPoint.lengthSqr();
		if (distSqr > viewer.viewDistance() * viewer.viewDistance()) {
			return false;
		}
		return distSqr < 1.0E-6 || toPoint.dot(viewer.look()) >= cosHalfCone * Math.sqrt(distSqr);
	}

	/**
	 * Walks the blocks from the eye to the point. Reaching the target box means it is seen; only an opaque full
	 * block ({@code isSolidRender}) blocks sight. The block the eye is in and unloaded blocks never block.
	 */
	static boolean lineOfSight(Level level, Vec3 eye, Vec3 point, AABB target) {
		BlockPos eyeBlock = BlockPos.containing(eye);
		return BlockGetter.traverseBlocks(eye, point, target, (box, pos) -> {
			if (box.intersects(pos)) {
				return Boolean.TRUE;
			}
			if (pos.equals(eyeBlock) || !level.isLoaded(pos)) {
				return null;
			}
			return level.getBlockState(pos).isSolidRender() ? Boolean.FALSE : null;
		}, box -> Boolean.TRUE);
	}

	/** A grid of points through the box, slightly inset, about every 4 blocks (3 to 6 per axis). */
	static List<Vec3> samplePoints(AABB box) {
		AABB inner = inset(box);
		int nx = samples(inner.getXsize());
		int ny = samples(inner.getYsize());
		int nz = samples(inner.getZsize());
		List<Vec3> points = new ArrayList<>(nx * ny * nz);
		for (int ix = 0; ix < nx; ix++) {
			for (int iy = 0; iy < ny; iy++) {
				for (int iz = 0; iz < nz; iz++) {
					points.add(new Vec3(
							Mth.lerp(ix / (nx - 1.0), inner.minX, inner.maxX),
							Mth.lerp(iy / (ny - 1.0), inner.minY, inner.maxY),
							Mth.lerp(iz / (nz - 1.0), inner.minZ, inner.maxZ)));
				}
			}
		}
		return points;
	}

	/** Center, 8 corners and 6 face centers of a block, slightly inset. */
	private static List<Vec3> blockPoints(AABB block) {
		AABB inner = inset(block);
		Vec3 c = inner.getCenter();
		List<Vec3> points = new ArrayList<>(15);
		points.add(c);
		for (double x : new double[] {inner.minX, inner.maxX}) {
			for (double y : new double[] {inner.minY, inner.maxY}) {
				for (double z : new double[] {inner.minZ, inner.maxZ}) {
					points.add(new Vec3(x, y, z));
				}
			}
		}
		points.add(new Vec3(inner.minX, c.y, c.z));
		points.add(new Vec3(inner.maxX, c.y, c.z));
		points.add(new Vec3(c.x, inner.minY, c.z));
		points.add(new Vec3(c.x, inner.maxY, c.z));
		points.add(new Vec3(c.x, c.y, inner.minZ));
		points.add(new Vec3(c.x, c.y, inner.maxZ));
		return points;
	}

	private static AABB inset(AABB box) {
		return box.deflate(Math.min(0.05, Math.min(box.getXsize(), Math.min(box.getYsize(), box.getZsize())) / 4.0));
	}

	private static int samples(double length) {
		return Mth.clamp((int) Math.ceil(length / 4.0) + 2, 3, 6);
	}

	// --- block edits ---

	/** Removes a block silently (leaves its fluid if it was waterlogged). False if it is air. */
	public boolean remove(ServerLevel level, BlockPos pos, String cause) {
		return !level.getBlockState(pos).isAir() && execute(level, cause, List.of(new TraceBatch.Remove(pos.immutable())));
	}

	/** Moves a block (and its block entity data) to a replaceable spot without a block entity. */
	public boolean move(ServerLevel level, BlockPos from, BlockPos to, String cause) {
		return execute(level, cause, List.of(new TraceBatch.Move(from.immutable(), to.immutable())));
	}

	/** Swaps a block for another (for example grass to dirt). False if it already is that state. */
	public boolean convert(ServerLevel level, BlockPos pos, BlockState newState, String cause) {
		return execute(level, cause, List.of(new TraceBatch.Convert(pos.immutable(), newState)));
	}

	/**
	 * Places something "left by others" (fragments, builds, lights). Only into a replaceable spot (air, replaceable
	 * plants, snow layer, fluid) without a block entity; false otherwise. Not written to the ledger, never undone.
	 */
	public boolean leave(ServerLevel level, BlockPos pos, BlockState state, String cause) {
		return execute(level, cause, List.of(new TraceBatch.Leave(pos.immutable(), state)));
	}

	/** Takes up to {@code count} items out of a container slot. The removed stack is kept in the ledger. */
	public boolean removeStack(ServerLevel level, BlockPos pos, int slot, int count, String cause) {
		if (!(level.getBlockEntity(pos) instanceof Container container) || slot < 0 || slot >= container.getContainerSize()
				|| container.getItem(slot).isEmpty() || !allowed(level, List.of(pos))) {
			return false;
		}
		ItemStack removed = container.removeItem(slot, count);
		container.setChanged();
		logStack(level, TraceLedger.Kind.REMOVE_STACK, pos, null, removed, slot, -1, cause);
		return true;
	}

	/** Moves a whole stack from a container slot into the first empty slot of another container. */
	public boolean moveStack(ServerLevel level, BlockPos from, int slot, BlockPos to, String cause) {
		if (!(level.getBlockEntity(from) instanceof Container source) || !(level.getBlockEntity(to) instanceof Container target)
				|| slot < 0 || slot >= source.getContainerSize() || source.getItem(slot).isEmpty()) {
			return false;
		}
		ItemStack stack = source.getItem(slot);
		int targetSlot = -1;
		for (int i = 0; i < target.getContainerSize(); i++) {
			if (target.getItem(i).isEmpty() && target.canPlaceItem(i, stack)) {
				targetSlot = i;
				break;
			}
		}
		if (targetSlot < 0 || !allowed(level, List.of(from, to))) {
			return false;
		}
		target.setItem(targetSlot, stack.copy());
		source.setItem(slot, ItemStack.EMPTY);
		source.setChanged();
		target.setChanged();
		logStack(level, TraceLedger.Kind.MOVE_STACK, from, to, stack.copy(), slot, targetSlot, cause);
		return true;
	}

	/** A large edit checked and applied as one. See {@link TraceBatch}. */
	public TraceBatch batch(ServerLevel level, String cause) {
		return new TraceBatch(this, level, cause);
	}

	public static TraceLedger ledger(MinecraftServer server) {
		return TraceLedger.get(server);
	}

	/** Plans the ops and their dependents, checks every affected block, then applies. All or nothing. */
	boolean execute(ServerLevel level, String cause, List<TraceBatch.Op> ops) {
		TraceEdit edit = new TraceEdit(level, cause);
		for (TraceBatch.Op op : ops) {
			if (!edit.add(op)) {
				return false;
			}
		}
		if (edit.isEmpty() || !edit.expand() || !allowed(level, edit.checkedPositions())) {
			return false;
		}
		edit.apply();
		return true;
	}

	private boolean allowed(ServerLevel level, Collection<BlockPos> positions) {
		return force || isOutOfView(level, positions);
	}

	private static void logStack(ServerLevel level, TraceLedger.Kind kind, BlockPos pos, @Nullable BlockPos to, ItemStack stack,
			int slot, int toSlot, String cause) {
		MinecraftServer server = level.getServer();
		TraceLedger.get(server).add(new TraceLedger.Entry(kind, cause, GameClock.day(server), GlobalPos.of(level.dimension(), pos.immutable()),
				Optional.ofNullable(to).map(BlockPos::immutable), Optional.empty(), Optional.empty(), Optional.of(stack), slot, toSlot));
		A1016_02.LOGGER.debug("[a1016] trace {} at {} ({})", kind, pos, cause);
	}
}
