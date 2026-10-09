package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import org.jspecify.annotations.Nullable;

/**
 * Every block, item or light change "he" makes goes through here, plus the out-of-view check (D-012).
 * Every edit returns false and changes nothing if the target is in view of any player. Removals, moves,
 * conversions and stack changes are written to the {@link TraceLedger} so Ending D can undo them; {@link #leave}
 * places things "left by others" and is never undone. Server thread only.
 */
public final class TraceService {
	/** Silent edit: no drops, particles or sound, and containers keep their items in the ledger instead of spilling. */
	public static final int EDIT_FLAGS = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;

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

	private final boolean force;
	private @Nullable TraceService forced;

	TraceService(boolean force) {
		this.force = force;
	}

	/** Tests and debug only: the same service without the view check. Edits are still written to the ledger. */
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
	 * inside a {@code viewConeDegrees} (160°) cone within their view distance.
	 */
	public boolean isOutOfView(ServerLevel level, AABB box) {
		Pacing pacing = ModConfig.pacing();
		return isOutOfView(level, box, viewers(level), pacing.viewNearBlocks, pacing.viewConeDegrees);
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

	/** True if this viewer is near the box or can see any sample point of it. */
	public static boolean sees(Level level, Viewer viewer, AABB box, double nearBlocks, double coneDegrees) {
		if (viewer.body().inflate(nearBlocks).intersects(box)) {
			return true;
		}
		double cosHalfCone = Math.cos(Math.toRadians(coneDegrees / 2.0));
		double maxDistSqr = viewer.viewDistance() * viewer.viewDistance();
		for (Vec3 point : samplePoints(box)) {
			Vec3 toPoint = point.subtract(viewer.eye());
			double distSqr = toPoint.lengthSqr();
			if (distSqr > maxDistSqr) {
				continue;
			}
			if (distSqr > 1.0E-6 && toPoint.normalize().dot(viewer.look()) < cosHalfCone) {
				continue;
			}
			BlockHitResult hit = level.clip(new ClipContext(viewer.eye(), point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
			if (hit.getType() == HitResult.Type.MISS || box.intersects(new AABB(hit.getBlockPos()).deflate(1.0E-3))) {
				return true;
			}
		}
		return false;
	}

	/** A grid of points through the box, slightly inset, about every 4 blocks (3 to 6 per axis). */
	static List<Vec3> samplePoints(AABB box) {
		AABB inner = box.deflate(Math.min(0.05, Math.min(box.getXsize(), Math.min(box.getYsize(), box.getZsize())) / 4.0));
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

	private static int samples(double length) {
		return Mth.clamp((int) Math.ceil(length / 4.0) + 2, 3, 6);
	}

	private boolean allowed(ServerLevel level, AABB box) {
		return force || isOutOfView(level, box);
	}

	// --- block edits ---

	/** Removes a block silently (leaves its fluid if it was waterlogged). */
	public boolean remove(ServerLevel level, BlockPos pos, String cause) {
		return !level.getBlockState(pos).isAir() && allowed(level, new AABB(pos)) && applyRemove(level, pos, cause);
	}

	/** Moves a block (and its block entity data) to an empty or replaceable spot. Both spots must be out of view. */
	public boolean move(ServerLevel level, BlockPos from, BlockPos to, String cause) {
		return canMove(level, from, to) && allowed(level, new AABB(from)) && allowed(level, new AABB(to)) && applyMove(level, from, to, cause);
	}

	/** Swaps a block for another (for example grass to dirt). */
	public boolean convert(ServerLevel level, BlockPos pos, BlockState newState, String cause) {
		return allowed(level, new AABB(pos)) && applyConvert(level, pos, newState, cause);
	}

	/** Places something "left by others" (fragments, builds, lights). Not written to the ledger, never undone. */
	public boolean leave(ServerLevel level, BlockPos pos, BlockState state, String cause) {
		return allowed(level, new AABB(pos)) && applyLeave(level, pos, state, cause);
	}

	/** Takes up to {@code count} items out of a container slot. The removed stack is kept in the ledger. */
	public boolean removeStack(ServerLevel level, BlockPos pos, int slot, int count, String cause) {
		if (!(level.getBlockEntity(pos) instanceof Container container) || slot < 0 || slot >= container.getContainerSize()
				|| container.getItem(slot).isEmpty() || !allowed(level, new AABB(pos))) {
			return false;
		}
		ItemStack removed = container.removeItem(slot, count);
		container.setChanged();
		log(level, TraceLedger.Kind.REMOVE_STACK, pos, null, null, null, removed, slot, -1, cause);
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
		if (targetSlot < 0 || !allowed(level, new AABB(from)) || !allowed(level, new AABB(to))) {
			return false;
		}
		target.setItem(targetSlot, stack.copy());
		source.setItem(slot, ItemStack.EMPTY);
		source.setChanged();
		target.setChanged();
		log(level, TraceLedger.Kind.MOVE_STACK, from, to, null, null, stack.copy(), slot, targetSlot, cause);
		return true;
	}

	/** A large edit with one view check on its whole bounding box. */
	public TraceBatch batch(ServerLevel level, String cause) {
		return new TraceBatch(this, level, cause);
	}

	/** Edits {@code allowed} decides for a whole batch. */
	boolean allowedBatch(ServerLevel level, AABB box) {
		return allowed(level, box);
	}

	public static TraceLedger ledger(MinecraftServer server) {
		return TraceLedger.get(server);
	}

	// --- unchecked edits (used after the view check) ---

	boolean applyRemove(ServerLevel level, BlockPos pos, String cause) {
		BlockState old = level.getBlockState(pos);
		CompoundTag blockEntity = saveBlockEntity(level, pos);
		level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), EDIT_FLAGS);
		log(level, TraceLedger.Kind.REMOVE, pos, null, old, blockEntity, null, -1, -1, cause);
		return true;
	}

	static boolean canMove(ServerLevel level, BlockPos from, BlockPos to) {
		return !level.getBlockState(from).isAir() && level.getBlockState(to).canBeReplaced();
	}

	boolean applyMove(ServerLevel level, BlockPos from, BlockPos to, String cause) {
		BlockState state = level.getBlockState(from);
		CompoundTag blockEntity = saveBlockEntity(level, from);
		level.setBlock(from, level.getFluidState(from).createLegacyBlock(), EDIT_FLAGS);
		level.setBlock(to, state, EDIT_FLAGS);
		if (blockEntity != null && level.getBlockEntity(to) instanceof BlockEntity moved) {
			moved.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), blockEntity));
			moved.setChanged();
		}
		log(level, TraceLedger.Kind.MOVE, from, to, state, blockEntity, null, -1, -1, cause);
		return true;
	}

	boolean applyConvert(ServerLevel level, BlockPos pos, BlockState newState, String cause) {
		BlockState old = level.getBlockState(pos);
		if (old == newState) {
			return false;
		}
		CompoundTag blockEntity = saveBlockEntity(level, pos);
		level.setBlock(pos, newState, EDIT_FLAGS);
		log(level, TraceLedger.Kind.CONVERT, pos, null, old, blockEntity, null, -1, -1, cause);
		return true;
	}

	boolean applyLeave(ServerLevel level, BlockPos pos, BlockState state, String cause) {
		level.setBlock(pos, state, Block.UPDATE_ALL);
		A1016_02.LOGGER.debug("[a1016] trace leave {} at {} ({})", state, pos, cause);
		return true;
	}

	private static @Nullable CompoundTag saveBlockEntity(ServerLevel level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		return blockEntity == null ? null : blockEntity.saveCustomOnly(level.registryAccess());
	}

	private static void log(ServerLevel level, TraceLedger.Kind kind, BlockPos pos, @Nullable BlockPos to, @Nullable BlockState state,
			@Nullable CompoundTag blockEntity, @Nullable ItemStack stack, int slot, int toSlot, String cause) {
		MinecraftServer server = level.getServer();
		TraceLedger.get(server).add(new TraceLedger.Entry(kind, cause, GameClock.day(server), GlobalPos.of(level.dimension(), pos.immutable()),
				Optional.ofNullable(to).map(BlockPos::immutable), Optional.ofNullable(state), Optional.ofNullable(blockEntity),
				Optional.ofNullable(stack), slot, toSlot));
		A1016_02.LOGGER.debug("[a1016] trace {} at {} ({})", kind, pos, cause);
	}
}
