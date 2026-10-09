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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

import org.jspecify.annotations.Nullable;

/**
 * One planned trace edit: the requested changes, the neighbours that would break because of them (removed
 * silently, written to the ledger) and the neighbours that would change shape. {@link TraceService} plans,
 * checks the view on {@link #checkedPositions()}, then {@link #apply()}s.
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
	private final Map<BlockPos, CompoundTag> moveInto = new HashMap<>();
	private final Set<BlockPos> reshaped = new LinkedHashSet<>();
	private final List<Record> records = new ArrayList<>();

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
			case TraceBatch.Leave(BlockPos pos, BlockState state) -> {
				if (!current(pos).canBeReplaced() || hasBlockEntity(pos)) {
					yield false;
				}
				changes.put(pos, state);
				yield true;
			}
		};
	}

	boolean isEmpty() {
		return changes.isEmpty();
	}

	/**
	 * Finds the neighbours that survive now but would not after the edit (torches, signs, plants, rails, door
	 * halves...), cascading, and the ones that would only change shape. False (refuse) if more than
	 * {@link #MAX_DEPENDENTS} would break or a falling block would lose its support.
	 */
	boolean expand() {
		LevelReader after = overlay(level, changes);
		ScheduledTickAccess noTicks = noTicks(level);
		Deque<BlockPos> queue = new ArrayDeque<>(changes.keySet());
		int dependents = 0;
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			BlockState now = changes.get(pos);
			for (Direction dir : Direction.values()) {
				BlockPos n = pos.relative(dir);
				if (changes.containsKey(n) || !level.isLoaded(n)) {
					continue;
				}
				BlockState state = level.getBlockState(n);
				if (state.isAir()) {
					continue;
				}
				if (dir == Direction.UP && state.getBlock() instanceof FallingBlock && FallingBlock.isFree(now)) {
					return false;
				}
				BlockState shaped = state.updateShape(after, noTicks, n, dir.getOpposite(), pos, now, level.getRandom());
				boolean breaks = shaped.isAir() || state.canSurvive(level, n) && !state.canSurvive(after, n);
				if (breaks) {
					if (++dependents > MAX_DEPENDENTS) {
						return false;
					}
					records.add(new Record(TraceLedger.Kind.REMOVE, n, null, state, blockEntity(n), cause + "/dependent"));
					changes.put(n, state.getFluidState().createLegacyBlock());
					queue.add(n);
				} else if (shaped != state || !state.getFluidState().isEmpty()) {
					reshaped.add(n);
				}
			}
		}
		reshaped.removeAll(changes.keySet());
		return true;
	}

	/** Every block whose look changes: edits, broken dependents and reshaped neighbours. */
	Set<BlockPos> checkedPositions() {
		Set<BlockPos> all = new LinkedHashSet<>(changes.keySet());
		all.addAll(reshaped);
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
		A1016_02.LOGGER.debug("[a1016] trace '{}': {} changes, {} records", cause, changes.size(), records.size());
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
