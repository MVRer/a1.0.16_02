package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import com.forzacode.a1016_02.A1016_02;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.DropChances;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Every block, item or light change "he" makes goes through here, plus the out-of-view check (D-012, D-027).
 * Every edit returns false and changes nothing if anything it would change is in view of any player, or if a
 * {@link TraceVeto} objects to any position it would change. Edits are silent: no drops, particles or sounds,
 * containers never spill, and neighbours that would break (a torch on a removed block, the other door half) are
 * removed silently too and written to the ledger. Removals, moves, conversions, sign edits and stack changes go to
 * the {@link TraceLedger} so Ending D can undo them; {@link #leave} and {@link #leaveStack} place things "left by
 * others" and are never undone. The only edits allowed in view are {@link #figureDig} and {@link #figureFill}
 * (D-030). Server thread only.
 */
public final class TraceService {
	/**
	 * Where a player looks from.
	 *
	 * @param eye          eye position
	 * @param look         unit look vector
	 * @param body         the player's bounding box, for the "within 3 blocks" rule (its bottom is the feet)
	 * @param viewDistance how far they can see, in blocks
	 */
	public record Viewer(Vec3 eye, Vec3 look, AABB body, double viewDistance) {
		public static Viewer of(Player player, int viewDistanceChunks) {
			return new Viewer(player.getEyePosition(), player.getViewVector(1.0F), player.getBoundingBox(), (viewDistanceChunks + 1) * 16.0);
		}
	}

	/**
	 * D-027: a viewer looking up at least this far (degrees above the horizon) does not see a block strictly below
	 * their feet that lies outside the cone, even within the 3-block rule ("the blocks under you go").
	 */
	public static final double UNDER_FEET_LOOK_UP_DEGREES = 30.0;
	/** {@link #editSign} may change a waxed sign only for this cause prefix (lore placing F30). */
	public static final String WAXED_SIGN_CAUSE = "lore:left/F30";
	/** {@link #restoreBlock} puts a block back at most this many blocks (on every axis) from where it was. */
	public static final int RESTORE_REACH = 2;

	/** Half the diagonal of a block: how far a block's corners reach from its center. */
	private static final double BLOCK_RADIUS = 0.87;
	private static final double LOOK_UP_MIN_Y = Math.sin(Math.toRadians(UNDER_FEET_LOOK_UP_DEGREES)) - 1.0E-9;
	private static final List<TraceVeto> VETOES = new CopyOnWriteArrayList<>();

	private final boolean force;
	/** Tests: these viewpoints instead of the level's players. */
	private final @Nullable List<Viewer> fixedViewers;
	private @Nullable TraceService forced;

	TraceService(boolean force) {
		this(force, null);
	}

	private TraceService(boolean force, @Nullable List<Viewer> fixedViewers) {
		this.force = force;
		this.fixedViewers = fixedViewers == null ? null : List.copyOf(fixedViewers);
	}

	/** Tests and debug only: the same service without the view check. Edits are still silent, vetoed and ledgered. */
	public TraceService forced() {
		if (force) {
			return this;
		}
		if (forced == null) {
			forced = new TraceService(true, fixedViewers);
		}
		return forced;
	}

	/** Tests only: the same service, but the view check uses these viewpoints instead of the level's players. */
	TraceService watchedBy(List<Viewer> viewers) {
		return new TraceService(force, viewers);
	}

	// --- vetoes ---

	/** Registers a veto asked about every position every edit would change (see {@link TraceVeto}). Any thread. */
	public void addVeto(TraceVeto veto) {
		VETOES.add(Objects.requireNonNull(veto));
	}

	/** Removes a veto (tests). */
	public void removeVeto(TraceVeto veto) {
		VETOES.remove(veto);
	}

	/** True if any registered veto objects to any of these positions. */
	static boolean vetoed(ServerLevel level, Collection<BlockPos> positions) {
		if (VETOES.isEmpty()) {
			return false;
		}
		for (BlockPos pos : positions) {
			for (TraceVeto veto : VETOES) {
				if (veto.vetoes(level, pos)) {
					return true;
				}
			}
		}
		return false;
	}

	// --- out-of-view check ---

	public boolean isOutOfView(ServerLevel level, BlockPos pos) {
		return isOutOfView(level, new AABB(pos));
	}

	/**
	 * False if any player in the level is within {@code viewNearBlocks} (3) of the box, or has line of sight to it
	 * inside a {@code viewConeDegrees} (160°) cone within their view distance. Only opaque full blocks block sight;
	 * leaves, glass, ice and the like are see-through. Unloaded points count as unseen. The near rule does not
	 * count a box strictly below a player's feet while they look up 30° or more and it is outside the cone (D-027).
	 */
	public boolean isOutOfView(ServerLevel level, AABB box) {
		Pacing pacing = ModConfig.pacing();
		return isOutOfView(level, box, viewersFor(level), pacing.viewNearBlocks, pacing.viewConeDegrees);
	}

	/**
	 * Like {@link #isOutOfView(ServerLevel, AABB)} for a set of blocks, checking each one that can be seen at all
	 * (it has a neighbour that is not an opaque full block). This is what every edit uses.
	 */
	public boolean isOutOfView(ServerLevel level, Collection<BlockPos> positions) {
		Pacing pacing = ModConfig.pacing();
		return positionsOutOfView(level, positions, viewersFor(level), pacing.viewNearBlocks, pacing.viewConeDegrees);
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

	private List<Viewer> viewersFor(ServerLevel level) {
		return fixedViewers != null ? fixedViewers : viewers(level);
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
					if (reach.intersects(pos) && !hiddenUnderFeet(viewer, new AABB(pos), halfCone)) {
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

	/** True if this viewer is near the box (D-027 aside) or can see any sample point of it. */
	public static boolean sees(Level level, Viewer viewer, AABB box, double nearBlocks, double coneDegrees) {
		double halfCone = Math.toRadians(coneDegrees / 2.0);
		if (viewer.body().inflate(nearBlocks).intersects(box) && !hiddenUnderFeet(viewer, box, halfCone)) {
			return true;
		}
		double cosHalfCone = Math.cos(halfCone);
		for (Vec3 point : samplePoints(box)) {
			if (pointInView(viewer, point, cosHalfCone) && level.isLoaded(BlockPos.containing(point)) && lineOfSight(level, viewer.eye(), point, box)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * D-027: the box lies strictly below the viewer's feet, the viewer looks up at least
	 * {@link #UNDER_FEET_LOOK_UP_DEGREES}, and no part of the box is inside the cone. Such a box is exempt from the
	 * near rule (line of sight still counts, and cannot reach it outside the cone).
	 */
	public static boolean hiddenUnderFeet(Viewer viewer, AABB box, double halfConeRadians) {
		if (box.maxY > viewer.body().minY + 1.0E-6 || viewer.look().y < LOOK_UP_MIN_Y) {
			return false;
		}
		Vec3 toCenter = box.getCenter().subtract(viewer.eye());
		double dist = toCenter.length();
		double radius = 0.5 * Math.sqrt(box.getXsize() * box.getXsize() + box.getYsize() * box.getYsize() + box.getZsize() * box.getZsize());
		if (dist <= radius) {
			return false;
		}
		double angle = Math.acos(Mth.clamp(toCenter.dot(viewer.look()) / dist, -1.0, 1.0));
		return angle - Math.asin(radius / dist) > halfConeRadians;
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

	/**
	 * Removes a block and lets what rests on it fall by the game's rules: the sand, red sand or gravel column on top
	 * of it drops and lands as blocks, or the stalactite (pointed dripstone) hanging under it drops and shatters,
	 * hurting what it lands on. The removed block, every falling block, every cell they fall through and where they
	 * land must be out of view, or nothing happens. The removed block (and broken dependents) is ledgered; the fallen
	 * blocks are not (the game moved them). Refused if a falling block would break into an item (landing in a torch
	 * or on a slab), if other falling blocks (concrete powder, anvils, suspicious sand) are involved, or if both a
	 * column and a stalactite rest on the block. With nothing resting on it, this is {@link #remove}.
	 */
	public boolean removeLettingFall(ServerLevel level, BlockPos pos, String cause) {
		if (level.getBlockState(pos).isAir()) {
			return false;
		}
		TraceFall fall = TraceFall.plan(level, pos.immutable());
		if (fall == null) {
			return false;
		}
		TraceEdit edit = plan(level, cause, List.of(new TraceBatch.Remove(pos.immutable())), fall);
		if (edit == null || !allowed(level, edit.checkedPositions())) {
			return false;
		}
		edit.apply();
		return true;
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
		return leave(level, pos, state, null, cause);
	}

	/**
	 * {@link #leave(ServerLevel, BlockPos, BlockState, String)} with block entity contents: {@code blockEntityData}
	 * is {@code BlockEntity#saveCustomOnly} data (a chest's {@code Items}, a sign's {@code front_text}) loaded into
	 * the new block and sent to clients. False if there is data but the block has no block entity.
	 */
	public boolean leave(ServerLevel level, BlockPos pos, BlockState state, @Nullable CompoundTag blockEntityData, String cause) {
		return execute(level, cause, List.of(new TraceBatch.Leave(pos.immutable(), state, blockEntityData == null ? null : blockEntityData.copy())));
	}

	/**
	 * Puts back a block removed earlier: {@code entry} is a REMOVE entry still in the ledger, {@code toPos} its old
	 * spot or one at most {@link #RESTORE_REACH} blocks away on every axis (a torch put back one block off). The
	 * target must be replaceable and the block must survive there; its block entity data comes back too.
	 * View-checked like every edit. The entry is closed (same spot) or rewritten in place as a MOVE from the old
	 * spot (another spot), so Ending D's undo never puts it back twice.
	 */
	public boolean restoreBlock(ServerLevel level, TraceLedger.Entry entry, BlockPos toPos) {
		TraceLedger ledger = TraceLedger.get(level.getServer());
		BlockPos origin = entry.pos().pos();
		if (entry.kind() != TraceLedger.Kind.REMOVE || entry.state().isEmpty() || !entry.pos().dimension().equals(level.dimension())
				|| reach(origin, toPos) > RESTORE_REACH || !ledger.entries().contains(entry)) {
			return false;
		}
		BlockPos to = toPos.immutable();
		TraceEdit edit = plan(level, entry.cause(), List.of(new TraceBatch.Restore(to, entry.state().get(), entry.blockEntity().map(CompoundTag::copy).orElse(null))), null);
		if (edit == null || !allowed(level, edit.checkedPositions())) {
			return false;
		}
		edit.apply();
		if (to.equals(origin)) {
			ledger.remove(entry);
		} else {
			ledger.replace(entry, asMove(entry, to));
		}
		return true;
	}

	/**
	 * Replaces a sign's text out of view. {@code null} or empty lists blank that side (the "blank sign" event); up to
	 * 4 lines, the rest are blank; colour and glow stay. Silent, vetoed, and ledgered as BLOCK_ENTITY with the old
	 * text so Ending D can put it back. False if there is no sign, more than 4 lines, or the sign is waxed and the
	 * cause does not start with {@link #WAXED_SIGN_CAUSE}.
	 */
	public boolean editSign(ServerLevel level, BlockPos pos, @Nullable List<Component> frontLines, @Nullable List<Component> backLines, String cause) {
		if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign) || frontLines != null && frontLines.size() > SignText.LINES
				|| backLines != null && backLines.size() > SignText.LINES || sign.isWaxed() && !cause.startsWith(WAXED_SIGN_CAUSE)) {
			return false;
		}
		List<BlockPos> at = List.of(pos.immutable());
		if (vetoed(level, at) || !allowed(level, at)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		CompoundTag before = sign.saveCustomOnly(level.registryAccess());
		sign.setText(withLines(sign.getText(SignTextSlot.FRONT), frontLines), SignTextSlot.FRONT);
		sign.setText(withLines(sign.getText(SignTextSlot.BACK), backLines), SignTextSlot.BACK);
		sign.setChanged();
		level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
		MinecraftServer server = level.getServer();
		TraceLedger.get(server).add(new TraceLedger.Entry(TraceLedger.Kind.BLOCK_ENTITY, cause, GameClock.day(server), GlobalPos.of(level.dimension(), pos.immutable()),
				Optional.empty(), Optional.of(state), Optional.of(before), Optional.empty(), -1, -1));
		A1016_02.LOGGER.debug("[a1016] trace sign text at {} ({})", pos, cause);
		return true;
	}

	/**
	 * ONLY for the figure entity's own dig-under exit (D-030). Every other caller must use the view-checked methods.
	 * Removes one block even in view, silently (no drops, particles or sound), ledgered like {@link #remove}, with
	 * the same dependent, hanging-entity, falling-block and veto safety. Refuses air, fluids (and waterlogged blocks or
	 * water beside the hole), unbreakable blocks (bedrock), block entities and containers, and blocks a player placed
	 * ({@code PlayerWatch.wasPlacedByPlayer}), for the block and for every dependent it would take with it.
	 */
	public boolean figureDig(ServerLevel level, BlockPos pos, String cause) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0.0F || state.hasBlockEntity()
				|| level.getBlockEntity(pos) != null) {
			return false;
		}
		TraceEdit edit = plan(level, cause, List.of(new TraceBatch.Remove(pos.immutable())), null);
		if (edit == null) {
			return false;
		}
		PlayerWatch watch = Services.watch();
		for (BlockPos changed : edit.changedPositions()) {
			if (level.getBlockEntity(changed) != null || watch.wasPlacedByPlayer(level, changed) || fluidBeside(level, changed)) {
				return false;
			}
		}
		edit.apply();
		return true;
	}

	/**
	 * ONLY for the figure entity's own dig-under exit (D-030). Every other caller must use the view-checked methods.
	 * Covers the hole over him, even in view: puts {@code state} at {@code pos} (replaceable, no block entity, and
	 * it must survive there), but only as one of the blocks {@link #figureDig} removed under the same {@code cause}
	 * that is still missing (the newest that matches the block), so it is a move, not a creation. That ledger entry
	 * is rewritten in place as a MOVE from where the block was dug to {@code pos}. Silent and vetoed.
	 */
	public boolean figureFill(ServerLevel level, BlockPos pos, BlockState state, String cause) {
		TraceLedger ledger = TraceLedger.get(level.getServer());
		List<TraceLedger.Entry> entries = ledger.entries();
		TraceLedger.Entry source = null;
		for (int i = entries.size() - 1; i >= 0 && source == null; i--) {
			TraceLedger.Entry e = entries.get(i);
			if (e.kind() == TraceLedger.Kind.REMOVE && e.cause().equals(cause) && e.pos().dimension().equals(level.dimension())
					&& e.state().filter(s -> s.is(state.getBlock())).isPresent()) {
				source = e;
			}
		}
		if (source == null) {
			return false;
		}
		BlockPos to = pos.immutable();
		TraceEdit edit = plan(level, cause, List.of(new TraceBatch.Restore(to, state, null)), null);
		if (edit == null) {
			return false;
		}
		edit.apply();
		ledger.replace(source, asMove(source, to));
		return true;
	}

	// --- container edits ---

	/**
	 * Takes up to {@code count} items out of a container slot. The removed stack is kept in the ledger.
	 * False if {@code count <= 0} or the slot is empty or missing.
	 */
	public boolean removeStack(ServerLevel level, BlockPos pos, int slot, int count, String cause) {
		if (count <= 0 || !(level.getBlockEntity(pos) instanceof Container container) || slot < 0 || slot >= container.getContainerSize()
				|| container.getItem(slot).isEmpty() || !allowedAndNotVetoed(level, List.of(pos))) {
			return false;
		}
		ItemStack removed = container.removeItem(slot, count);
		container.setChanged();
		if (removed.isEmpty()) {
			return false;
		}
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
		int targetSlot = emptySlotFor(target, stack);
		if (targetSlot < 0 || !allowedAndNotVetoed(level, List.of(from, to))) {
			return false;
		}
		target.setItem(targetSlot, stack.copy());
		source.setItem(slot, ItemStack.EMPTY);
		source.setChanged();
		target.setChanged();
		logStack(level, TraceLedger.Kind.MOVE_STACK, from, to, stack.copy(), slot, targetSlot, cause);
		return true;
	}

	/**
	 * Puts a stack "left by others" into the first empty slot of a container, out of view. Not ledgered, never
	 * undone. False if the stack is empty, there is no container or no empty slot takes it.
	 */
	public boolean leaveStack(ServerLevel level, BlockPos containerPos, ItemStack stack, String cause) {
		if (stack.isEmpty() || !(level.getBlockEntity(containerPos) instanceof Container target)) {
			return false;
		}
		int slot = emptySlotFor(target, stack);
		if (slot < 0 || !allowedAndNotVetoed(level, List.of(containerPos))) {
			return false;
		}
		target.setItem(slot, stack.copy());
		target.setChanged();
		A1016_02.LOGGER.debug("[a1016] trace left a stack at {} ({})", containerPos, cause);
		return true;
	}

	/**
	 * Moves a stack he took earlier ({@code entry}: a REMOVE_STACK entry still in the ledger) into the first empty
	 * slot of the container at {@code toPos} in this level (the network chest), out of view, and closes the entry:
	 * the items are back in the world, so Ending D has nothing left to return. False if the entry is not an open
	 * REMOVE_STACK with items, or there is no container or room.
	 */
	public boolean restoreStack(ServerLevel level, TraceLedger.Entry entry, BlockPos toPos) {
		TraceLedger ledger = TraceLedger.get(level.getServer());
		if (entry.kind() != TraceLedger.Kind.REMOVE_STACK || entry.stack().filter(s -> !s.isEmpty()).isEmpty() || !ledger.entries().contains(entry)
				|| !(level.getBlockEntity(toPos) instanceof Container target)) {
			return false;
		}
		ItemStack stack = entry.stack().get();
		int slot = emptySlotFor(target, stack);
		if (slot < 0 || !allowedAndNotVetoed(level, List.of(toPos))) {
			return false;
		}
		target.setItem(slot, stack.copy());
		target.setChanged();
		ledger.remove(entry);
		A1016_02.LOGGER.debug("[a1016] trace restored a stack from {} to {}", entry.pos(), toPos);
		return true;
	}

	/**
	 * Puts a stack he took from a container earlier into an existing mob's empty equipment slot ("the zombie has
	 * your sword"). {@code entry} is a REMOVE_STACK entry (the ledger holds the stack) or a MOVE_STACK entry (the
	 * stack is taken back out of the slot it was moved to, which must still hold exactly that stack, in this level).
	 * The mob must be alive, in this level, out of view, with that slot empty; the container it leaves must be out of
	 * view and not vetoed. The mob drops it exactly as it was taken, undamaged, however it dies (the game's
	 * guaranteed drop chance, above 1.0) and no longer despawns; stacks with a curse of vanishing are refused, since
	 * they would vanish with the mob. Silent (no equip
	 * sound). Never creates items: the entry is rewritten in place as EQUIP (taken from the original container, now
	 * worn by the mob) under {@code cause}, so Ending D never returns it twice.
	 */
	public boolean equipFromLedger(ServerLevel level, TraceLedger.Entry entry, Mob mob, EquipmentSlot slot, String cause) {
		TraceLedger ledger = TraceLedger.get(level.getServer());
		ItemStack wanted = entry.stack().orElse(ItemStack.EMPTY);
		// A curse of vanishing would make it vanish with the mob instead of dropping: never equip those.
		if (wanted.isEmpty() || EnchantmentHelper.has(wanted, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP) || mob.level() != level || !mob.isAlive() || mob.isRemoved() || !mob.getItemBySlot(slot).isEmpty()
				|| !ledger.entries().contains(entry) || !(force || isOutOfView(level, mob.getBoundingBox()))) {
			return false;
		}
		ItemStack stack;
		if (entry.kind() == TraceLedger.Kind.REMOVE_STACK) {
			stack = wanted.copy();
		} else if (entry.kind() == TraceLedger.Kind.MOVE_STACK && entry.to().isPresent() && entry.pos().dimension().equals(level.dimension())) {
			BlockPos at = entry.to().get();
			int toSlot = entry.toSlot();
			if (!(level.getBlockEntity(at) instanceof Container container) || toSlot < 0 || toSlot >= container.getContainerSize()
					|| !ItemStack.matches(container.getItem(toSlot), wanted) || !allowedAndNotVetoed(level, List.of(at))) {
				return false;
			}
			stack = container.getItem(toSlot).copy();
			container.setItem(toSlot, ItemStack.EMPTY);
			container.setChanged();
		} else {
			return false;
		}
		boolean silent = mob.isSilent();
		mob.setSilent(true);
		mob.setItemSlot(slot, stack);
		mob.setSilent(silent);
		// Guaranteed drop: above 1.0 the game "preserves" the item, so it drops whatever kills the mob, never with
		// random damage, exactly as it was taken (a plain 1.0 drops only on a player kill, and damaged). The clue.
		mob.setDropChance(slot, DropChances.PRESERVE_ITEM_DROP_CHANCE);
		mob.setPersistenceRequired();
		ledger.replace(entry, new TraceLedger.Entry(TraceLedger.Kind.EQUIP, cause, entry.day(), entry.pos(), Optional.of(mob.blockPosition()),
				Optional.empty(), Optional.empty(), Optional.of(stack.copy()), entry.slot(), slot.ordinal(), Optional.of(mob.getUUID())));
		A1016_02.LOGGER.debug("[a1016] trace equipped {} on {} ({})", stack, mob, cause);
		return true;
	}

	/** A large edit checked and applied as one. See {@link TraceBatch}. */
	public TraceBatch batch(ServerLevel level, String cause) {
		return new TraceBatch(this, level, cause);
	}

	public static TraceLedger ledger(MinecraftServer server) {
		return TraceLedger.get(server);
	}

	// --- internals ---

	/** Plans the ops and their dependents, checks vetoes and every affected block, then applies. All or nothing. */
	boolean execute(ServerLevel level, String cause, List<TraceBatch.Op> ops) {
		TraceEdit edit = plan(level, cause, ops, null);
		if (edit == null || !allowed(level, edit.checkedPositions())) {
			return false;
		}
		edit.apply();
		return true;
	}

	/** A planned edit that passed every safety rule and veto (not the view check), or null. */
	private static @Nullable TraceEdit plan(ServerLevel level, String cause, List<TraceBatch.Op> ops, @Nullable TraceFall fall) {
		TraceEdit edit = new TraceEdit(level, cause);
		if (fall != null) {
			edit.letFall(fall);
		}
		for (TraceBatch.Op op : ops) {
			if (!edit.add(op)) {
				return null;
			}
		}
		if (edit.isEmpty() || !edit.expand() || edit.touchesAttachedEntity() || vetoed(level, edit.checkedPositions())) {
			return null;
		}
		return edit;
	}

	private boolean allowed(ServerLevel level, Collection<BlockPos> positions) {
		return force || isOutOfView(level, positions);
	}

	private boolean allowedAndNotVetoed(ServerLevel level, Collection<BlockPos> positions) {
		return !vetoed(level, positions) && allowed(level, positions);
	}

	private static int emptySlotFor(Container target, ItemStack stack) {
		for (int i = 0; i < target.getContainerSize(); i++) {
			if (target.getItem(i).isEmpty() && target.canPlaceItem(i, stack)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean fluidBeside(ServerLevel level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			if (dir != Direction.DOWN && !level.getFluidState(pos.relative(dir)).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static int reach(BlockPos a, BlockPos b) {
		return Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
	}

	/** A REMOVE entry rewritten as the move it became: from where it was taken to where it is now. */
	private static TraceLedger.Entry asMove(TraceLedger.Entry removed, BlockPos to) {
		return new TraceLedger.Entry(TraceLedger.Kind.MOVE, removed.cause(), removed.day(), removed.pos(), Optional.of(to), removed.state(),
				removed.blockEntity(), Optional.empty(), -1, -1);
	}

	private static SignText withLines(SignText old, @Nullable List<Component> lines) {
		List<Component> messages = new ArrayList<>(SignText.LINES);
		for (int i = 0; i < SignText.LINES; i++) {
			Component line = lines != null && i < lines.size() ? lines.get(i) : null;
			messages.add(line == null ? Component.empty() : line);
		}
		return new SignText(messages, messages, old.getColor(), old.hasGlowingText());
	}

	private static void logStack(ServerLevel level, TraceLedger.Kind kind, BlockPos pos, @Nullable BlockPos to, ItemStack stack,
			int slot, int toSlot, String cause) {
		MinecraftServer server = level.getServer();
		TraceLedger.get(server).add(new TraceLedger.Entry(kind, cause, GameClock.day(server), GlobalPos.of(level.dimension(), pos.immutable()),
				Optional.ofNullable(to).map(BlockPos::immutable), Optional.empty(), Optional.empty(), Optional.of(stack), slot, toSlot));
		A1016_02.LOGGER.debug("[a1016] trace {} at {} ({})", kind, pos, cause);
	}
}
