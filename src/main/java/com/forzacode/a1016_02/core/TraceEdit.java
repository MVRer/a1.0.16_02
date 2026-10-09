package com.forzacode.a1016_02.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

import org.jspecify.annotations.Nullable;

/**
 * One planned trace edit: the requested changes, the neighbours that would break because of them (removed
 * silently, written to the ledger) and the neighbours that would change shape. {@link TraceService} plans,
 * checks vetoes and the view on {@link #checkedPositions()}, then {@link #apply()}s. With {@link #letFall} the
 * blocks of a {@link TraceFall} are left to fall by the game's rules: they count as empty while planning what
 * breaks, the neighbours of their landing cells are checked too, and the fall is started last.
 *
 * <p>Lore's {@code TraceEditMixin} shadows {@code level} and {@code changes} and hooks the return of
 * {@link #expand()}: keep those names until lore moves to {@link TraceVeto}.
 */
final class TraceEdit {
	/** Client updates only: no neighbour updates (so nothing pops off and drops), no drops, no container spill. */
	static final int SILENT_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
			| Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
	/** More broken neighbours than this and the edit is refused. */
	static final int MAX_DEPENDENTS = 64;

	private record Record(TraceLedger.Kind kind, BlockPos pos, @Nullable BlockPos to, BlockState state, @Nullable CompoundTag blockEntity, String cause) {
	}

	private final ServerLevel level;
	private final String cause;
	/** Everything that will be set, in order; also the overlay the planning reads through. */
	private final Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
	/** Block entity data loaded into a moved or placed block right after it is set. */
	private final Map<BlockPos, CompoundTag> moveInto = new HashMap<>();
	private final Set<BlockPos> reshaped = new LinkedHashSet<>();
	private final List<Record> records = new ArrayList<>();
	private TraceFall fall = TraceFall.NONE;

	TraceEdit(ServerLevel level, String cause) {
		this.level = level;
		this.cause = cause;
	}

	// --- planning ---

	/** Adds one requested edit. False if it is impossible (nothing to move, target not replaceable). */
	boolean add(TraceBatch.Op op) {
		return switch (op) {
			case TraceBatch.Remove(BlockPos pos) -> {
				BlockState old = current(pos);
				if (!old.isAir()) {
					records.add(new Record(TraceLedger.Kind.REMOVE, pos, null, old, blockEntity(pos), cause));
					changes.put(pos, old.getFluidState().createLegacyBlock());
				}
				yield true;
			}
			case TraceBatch.Move(BlockPos from, BlockPos to) -> {
				BlockState state = current(from);
				if (state.isAir() || !current(to).canBeReplaced() || hasBlockEntity(to)) {
					yield false;
				}
				CompoundTag data = blockEntity(from);
				records.add(new Record(TraceLedger.Kind.MOVE, from, to, state, data, cause));
				changes.put(from, state.getFluidState().createLegacyBlock());
				changes.put(to, state);
				if (data != null) {
					moveInto.put(to, data);
				}
				yield true;
			}
			case TraceBatch.Convert(BlockPos pos, BlockState state) -> {
				BlockState old = current(pos);
				if (old != state) {
					records.add(new Record(TraceLedger.Kind.CONVERT, pos, null, old, blockEntity(pos), cause));
					changes.put(pos, state);
				}
				yield true;
			}
			case TraceBatch.Leave(BlockPos pos, BlockState state, CompoundTag data) -> place(pos, state, data);
			case TraceBatch.Restore(BlockPos pos, BlockState state, CompoundTag data) -> state.canSurvive(level, pos) && place(pos, state, data);
		};
	}

	/** Places a block (and its block entity data) into a replaceable spot without a block entity. Not recorded. */
	private boolean place(BlockPos pos, BlockState state, @Nullable CompoundTag data) {
		if (!current(pos).canBeReplaced() || hasBlockEntity(pos) || data != null && !state.hasBlockEntity()) {
			return false;
		}
		changes.put(pos, state);
		if (data != null) {
			moveInto.put(pos, data);
		}
		return true;
	}

	/** Lets this planned fall happen after the edit instead of refusing it. Call before {@link #expand()}. */
	void letFall(TraceFall planned) {
		fall = planned;
	}

	boolean isEmpty() {
		return changes.isEmpty();
	}

	/** The blocks this edit sets itself: the requested ones and the broken dependents. */
	Set<BlockPos> changedPositions() {
		return changes.keySet();
	}

	/**
	 * Finds the neighbours that survive now but would not after the edit (torches, signs, plants, rails, door
	 * halves...), cascading, and the ones that would only change shape. False (refuse) if more than
	 * {@link #MAX_DEPENDENTS} would break or a falling block would lose its support (unless it is part of the
	 * planned fall).
	 */
	boolean expand() {
		// The world while things fall: the edits, plus every falling cell empty.
		Map<BlockPos, BlockState> planned = new LinkedHashMap<>(changes);
		for (BlockPos pos : fall.falling()) {
			planned.putIfAbsent(pos, level.getBlockState(pos).getFluidState().createLegacyBlock());
		}
		LevelReader after = overlay(level, planned);
		ScheduledTickAccess noTicks = noTicks(level);
		Deque<BlockPos> queue = new ArrayDeque<>(planned.keySet());
		int dependents = 0;
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			BlockState now = planned.get(pos);
			for (Direction dir : Direction.values()) {
				BlockPos n = pos.relative(dir);
				if (planned.containsKey(n) || !level.isLoaded(n)) {
					continue;
				}
				BlockState state = level.getBlockState(n);
				if (state.isAir()) {
					continue;
				}
				if (dir == Direction.UP && fallsWhenFree(state) && FallingBlock.isFree(now)) {
					return false;
				}
				BlockState shaped = state.updateShape(after, noTicks, n, dir.getOpposite(), pos, now, level.getRandom());
				boolean breaks = shaped.isAir() || state.canSurvive(level, n) && !state.canSurvive(after, n);
				if (breaks) {
					if (++dependents > MAX_DEPENDENTS) {
						return false;
					}
					records.add(new Record(TraceLedger.Kind.REMOVE, n, null, state, blockEntity(n), cause + "/dependent"));
					BlockState empty = state.getFluidState().createLegacyBlock();
					changes.put(n, empty);
					planned.put(n, empty);
					queue.add(n);
				} else if (shaped != state || !state.getFluidState().isEmpty()) {
					reshaped.add(n);
				}
			}
		}
		reshaped.removeAll(planned.keySet());
		return fall.landing().isEmpty() || expandLanding(planned, noTicks);
	}

	/**
	 * Sand and gravel coming to rest: refuses if a neighbour of a landing cell would break (the game would drop it)
	 * and view-checks the neighbours that would change shape.
	 */
	private boolean expandLanding(Map<BlockPos, BlockState> planned, ScheduledTickAccess noTicks) {
		Map<BlockPos, BlockState> settled = new LinkedHashMap<>(planned);
		settled.putAll(fall.landing());
		LevelReader done = overlay(level, settled);
		for (Map.Entry<BlockPos, BlockState> landing : fall.landing().entrySet()) {
			for (Direction dir : Direction.values()) {
				BlockPos n = landing.getKey().relative(dir);
				if (settled.containsKey(n) || !level.isLoaded(n)) {
					continue;
				}
				BlockState state = level.getBlockState(n);
				if (state.isAir()) {
					continue;
				}
				BlockState shaped = state.updateShape(done, noTicks, n, dir.getOpposite(), landing.getKey(), landing.getValue(), level.getRandom());
				if (shaped.isAir() || state.canSurvive(level, n) && !state.canSurvive(done, n)) {
					return false;
				}
				if (shaped != state || !state.getFluidState().isEmpty()) {
					reshaped.add(n);
				}
			}
		}
		reshaped.removeAll(settled.keySet());
		return true;
	}

	/** Blocks the game drops when the block under them goes (sand, gravel, suspicious blocks); not stalagmites, which break. */
	private static boolean fallsWhenFree(BlockState state) {
		return state.getBlock() instanceof Fallable && !(state.getBlock() instanceof SpeleothemBlock);
	}

	/**
	 * True if an item frame, painting or leash knot touches a block this edit changes (or that falls or receives a
	 * falling block). Those pop off with a drop and a sound a few seconds later, so the edit is refused.
	 */
	boolean touchesAttachedEntity() {
		Set<BlockPos> touched = new LinkedHashSet<>(changes.keySet());
		touched.addAll(fall.falling());
		touched.addAll(fall.landing().keySet());
		AABB bounds = null;
		for (BlockPos pos : touched) {
			bounds = bounds == null ? new AABB(pos) : bounds.minmax(new AABB(pos));
		}
		if (bounds == null) {
			return false;
		}
		for (BlockAttachedEntity entity : level.getEntitiesOfClass(BlockAttachedEntity.class, bounds.inflate(1.0))) {
			for (BlockPos pos : touched) {
				if (entity.getBoundingBox().intersects(new AABB(pos).inflate(0.1))) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Every block whose look changes: edits, broken dependents and reshaped neighbours, plus for a fall every cell
	 * that falls, that a falling block passes through and where it comes to rest.
	 */
	Set<BlockPos> checkedPositions() {
		Set<BlockPos> all = new LinkedHashSet<>(changes.keySet());
		all.addAll(reshaped);
		all.addAll(fall.falling());
		all.addAll(fall.path());
		all.addAll(fall.landing().keySet());
		return all;
	}

	// --- applying ---

	void apply() {
		changes.forEach((pos, state) -> level.setBlock(pos, state, SILENT_FLAGS));
		moveInto.forEach((pos, data) -> {
			BlockEntity moved = level.getBlockEntity(pos);
			if (moved != null) {
				moved.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data));
				moved.setChanged();
				// Clients need what renders (sign text, a lectern's book), so the block entity is sent again.
				BlockState state = level.getBlockState(pos);
				level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
			}
		});
		// Let untouched neighbours adapt their shape (fences, panes, fluids start to flow), still without drops.
		Set<BlockPos> neighbours = new LinkedHashSet<>();
		for (BlockPos pos : changes.keySet()) {
			for (Direction dir : Direction.values()) {
				BlockPos n = pos.relative(dir);
				if (!changes.containsKey(n) && level.isLoaded(n)) {
					neighbours.add(n);
				}
			}
		}
		for (BlockPos n : neighbours) {
			BlockState old = level.getBlockState(n);
			BlockState updated = old.isAir() ? old : Block.updateFromNeighbourShapes(old, level, n);
			if (updated != old && !updated.isAir()) {
				level.setBlock(n, updated, SILENT_FLAGS);
			}
		}
		TraceLedger ledger = TraceLedger.get(level.getServer());
		long day = GameClock.day(level.getServer());
		for (Record r : records) {
			ledger.add(new TraceLedger.Entry(r.kind(), r.cause(), day, GlobalPos.of(level.dimension(), r.pos()), Optional.ofNullable(r.to()),
					Optional.of(r.state()), Optional.ofNullable(r.blockEntity()), Optional.empty(), -1, -1));
		}
		// The game does the falling: the lowest falling block ticks, finds nothing under it and drops; each block
		// above follows when the one under it leaves.
		BlockPos start = fall.start();
		Block startBlock = fall.startBlock();
		if (start != null && startBlock != null && level.getBlockState(start).is(startBlock)) {
			level.scheduleTick(start, startBlock, TraceFall.START_DELAY);
		}
		A1016_02.LOGGER.debug("[a1016] trace '{}': {} changes, {} records, {} falling", cause, changes.size(), records.size(), fall.falling().size());
	}

	// --- helpers ---

	private BlockState current(BlockPos pos) {
		BlockState planned = changes.get(pos);
		return planned != null ? planned : level.getBlockState(pos);
	}

	private boolean hasBlockEntity(BlockPos pos) {
		return !changes.containsKey(pos) && level.getBlockEntity(pos) != null;
	}

	private @Nullable CompoundTag blockEntity(BlockPos pos) {
		if (changes.containsKey(pos)) {
			return null;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		return blockEntity == null ? null : blockEntity.saveCustomOnly(level.registryAccess());
	}

	/** The level as it would look after {@code changes}: block and fluid reads at changed positions come from the map. */
	static LevelReader overlay(ServerLevel level, Map<BlockPos, BlockState> changes) {
		InvocationHandler handler = (proxy, method, args) -> {
			if (method.getDeclaringClass() == Object.class) {
				return switch (method.getName()) {
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> "TraceEdit.overlay";
				};
			}
			if (args != null && args.length == 1 && args[0] instanceof BlockPos pos && changes.containsKey(pos)) {
				switch (method.getName()) {
					case "getBlockState":
						return changes.get(pos);
					case "getFluidState":
						return changes.get(pos).getFluidState();
					case "getBlockEntity":
						return null;
					default:
						break;
				}
			}
			if (method.isDefault()) {
				return InvocationHandler.invokeDefault(proxy, method, args);
			}
			return method.invoke(level, args);
		};
		return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[] {LevelReader.class}, handler);
	}

	/** Tick access that schedules nothing, so planning has no side effects. */
	private static ScheduledTickAccess noTicks(ServerLevel level) {
		return new ScheduledTickAccess() {
			@Override
			public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int tickDelay, TickPriority priority) {
				return level.createTick(pos, type, tickDelay, priority);
			}

			@Override
			public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int tickDelay) {
				return level.createTick(pos, type, tickDelay);
			}

			@Override
			public LevelTickAccess<Block> getBlockTicks() {
				return BlackholeTickAccess.emptyLevelList();
			}

			@Override
			public LevelTickAccess<Fluid> getFluidTicks() {
				return BlackholeTickAccess.emptyLevelList();
			}
		};
	}
}
