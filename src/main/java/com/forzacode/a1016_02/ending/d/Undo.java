package com.forzacode.a1016_02.ending.d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * Undoing one {@link TraceLedger} entry, for the afterward. Every undo goes through core and is checked against the
 * view like any edit: REMOVE and REMOVE_STACK through {@code restoreBlock}/{@code restoreStack} (which close the
 * entry), MOVE, CONVERT, MOVE_STACK and sign text by the reverse edit, after which both the old entry and the one the
 * reverse edit wrote are dropped. So an entry is undone at most once and nothing is ever put back twice. Nothing is
 * created: a moved block goes back only if it is still where it was moved, a stack only if it is still in its slot.
 *
 * <p>What stays: whatever others left ({@code lore:left/}), the team's stair, still burning's cleared ground
 * ({@code world:still_burning} REMOVE entries), the fragments' own edits ({@code lore:his/F..}) and anything within
 * {@value #FRAGMENT_REACH} blocks of a placed fragment, the crosses, and stacks worn by mobs.
 */
public final class Undo {
	/** The cause of the reverse edits (dropped from the ledger right after; their broken neighbours are kept). */
	public static final String CAUSE = "ending:d/undo";
	/** Entries this close (on every axis) to a placed fragment stay: the fragments need their places. */
	public static final int FRAGMENT_REACH = 2;

	public enum Result {
		/** Undone (the entry is gone from the ledger). */
		DONE,
		/** Never undone (a skip rule). */
		SKIP,
		/** Not now: in view. Try again. */
		WAIT,
		/** Its chunk is not loaded: it waits in its chunk's cluster ({@link ChunkClusters}), which loads it. */
		UNLOADED,
		/** Cannot be undone as things stand (the block was taken or built over). Tried again next pass. */
		BLOCKED
	}

	private Undo() {
	}

	/** Why this entry is never undone, or empty if it should be. */
	public static Optional<String> skipReason(MinecraftServer server, TraceLedger.Entry entry) {
		String cause = entry.cause();
		if (cause.startsWith("lore:left/")) {
			// What others left, the team's stair to bedrock included ({@link Stair#CAUSE}).
			return Optional.of("left by others");
		}
		if (cause.startsWith(CAUSE)) {
			return Optional.of("the undo's own");
		}
		if (cause.startsWith("world:still_burning") && entry.kind() == TraceLedger.Kind.REMOVE) {
			return Optional.of("still burning's ground");
		}
		if (cause.startsWith("lore:his/F")) {
			return Optional.of("a fragment's own");
		}
		if (cause.startsWith("accident:cross") || cause.startsWith("world:cross_row")) {
			return Optional.of("the crosses stay");
		}
		if (entry.kind() == TraceLedger.Kind.EQUIP) {
			return Optional.of("worn by a mob");
		}
		if (nearFragment(HerobrineState.get(server).fragmentsPlaced(), entry)) {
			return Optional.of("a fragment needs it");
		}
		return Optional.empty();
	}

	static boolean nearFragment(Map<String, GlobalPos> placed, TraceLedger.Entry entry) {
		for (GlobalPos fragment : placed.values()) {
			if (!fragment.dimension().equals(entry.pos().dimension())) {
				continue;
			}
			if (reach(fragment.pos(), entry.pos().pos()) <= FRAGMENT_REACH || entry.to().map(to -> reach(fragment.pos(), to) <= FRAGMENT_REACH).orElse(false)) {
				return true;
			}
		}
		return false;
	}

	private static int reach(BlockPos a, BlockPos b) {
		return Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
	}

	/**
	 * Undoes one entry if it can now. {@code traces} is core's service ({@code forced()} only in tests and debug).
	 * An entry whose chunk (or whose move's other end) is not loaded is {@link Result#UNLOADED}: nothing is loaded here.
	 */
	public static Result undo(MinecraftServer server, TraceLedger.Entry entry, TraceService traces) {
		if (skipReason(server, entry).isPresent()) {
			return Result.SKIP;
		}
		TraceLedger ledger = TraceLedger.get(server);
		if (!ledger.entries().contains(entry)) {
			// Already undone (or never ledgered): nothing left to put back.
			return Result.BLOCKED;
		}
		ServerLevel level = server.getLevel(entry.pos().dimension());
		if (level == null) {
			return Result.BLOCKED;
		}
		BlockPos at = entry.pos().pos();
		if (!loaded(level, at) || entry.to().isPresent() && !loaded(level, entry.to().get())) {
			return Result.UNLOADED;
		}
		return switch (entry.kind()) {
			case REMOVE -> remove(level, entry, traces);
			case MOVE -> move(level, ledger, entry, traces);
			case CONVERT -> convert(level, ledger, entry, traces);
			case BLOCK_ENTITY -> signText(level, ledger, entry, traces);
			case REMOVE_STACK -> {
				if (!(level.getBlockEntity(at) instanceof Container)) {
					yield Result.BLOCKED;
				}
				yield traces.restoreStack(level, entry, at) ? Result.DONE : Result.WAIT;
			}
			case MOVE_STACK -> moveStack(level, ledger, entry, traces);
			case EQUIP -> Result.SKIP;
		};
	}

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	private static Result remove(ServerLevel level, TraceLedger.Entry entry, TraceService traces) {
		BlockPos at = entry.pos().pos();
		BlockState state = entry.state().orElse(null);
		if (state == null) {
			return Result.BLOCKED;
		}
		BlockState now = level.getBlockState(at);
		if (!now.canBeReplaced() || level.getBlockEntity(at) != null) {
			return Result.BLOCKED;
		}
		if (!state.canSurvive(level, at)) {
			// A torch whose wall is not back yet: its wall comes back later in the pass.
			return Result.BLOCKED;
		}
		return traces.restoreBlock(level, entry, at) ? Result.DONE : Result.WAIT;
	}

	private static Result move(ServerLevel level, TraceLedger ledger, TraceLedger.Entry entry, TraceService traces) {
		BlockPos from = entry.pos().pos();
		BlockPos to = entry.to().orElse(null);
		BlockState state = entry.state().orElse(null);
		if (to == null || state == null) {
			return Result.BLOCKED;
		}
		if (!level.getBlockState(to).is(state.getBlock())) {
			// Not there any more (taken, or moved on and undone already): nothing to bring back.
			return Result.BLOCKED;
		}
		BlockState home = level.getBlockState(from);
		if (!home.canBeReplaced() || level.getBlockEntity(from) != null) {
			return Result.BLOCKED;
		}
		int before = ledger.entries().size();
		if (!traces.move(level, to, from, CAUSE)) {
			return Result.WAIT;
		}
		dropNew(ledger, before);
		ledger.remove(entry);
		return Result.DONE;
	}

	private static Result convert(ServerLevel level, TraceLedger ledger, TraceLedger.Entry entry, TraceService traces) {
		BlockPos at = entry.pos().pos();
		BlockState old = entry.state().orElse(null);
		if (old == null) {
			return Result.BLOCKED;
		}
		BlockState now = level.getBlockState(at);
		if (now == old) {
			ledger.remove(entry);
			return Result.DONE;
		}
		PlayerWatch watch = Services.watch();
		if (watch.wasPlacedByPlayer(level, at) || now.isAir() && watch.wasDugByPlayer(level, at) || level.getBlockEntity(at) != null && !old.hasBlockEntity()) {
			return Result.BLOCKED;
		}
		int before = ledger.entries().size();
		if (!traces.convert(level, at, old, CAUSE)) {
			return Result.WAIT;
		}
		dropNew(ledger, before);
		ledger.remove(entry);
		return Result.DONE;
	}

	private static Result signText(ServerLevel level, TraceLedger ledger, TraceLedger.Entry entry, TraceService traces) {
		BlockPos at = entry.pos().pos();
		if (!(level.getBlockEntity(at) instanceof SignBlockEntity sign) || sign.isWaxed() || entry.blockEntity().isEmpty()) {
			return Result.BLOCKED;
		}
		CompoundTag old = entry.blockEntity().get();
		RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		List<Component> front = lines(old.get("front_text"), ops);
		List<Component> back = lines(old.get("back_text"), ops);
		int before = ledger.entries().size();
		if (!traces.editSign(level, at, front, back, CAUSE)) {
			return Result.WAIT;
		}
		dropNew(ledger, before);
		ledger.remove(entry);
		return Result.DONE;
	}

	private static List<Component> lines(@Nullable Tag tag, RegistryOps<Tag> ops) {
		if (tag == null) {
			return List.of();
		}
		return SignText.CODEC.parse(ops, tag).result().map(text -> new ArrayList<>(text.getMessages(false))).<List<Component>>map(l -> l).orElse(List.of());
	}

	private static Result moveStack(ServerLevel level, TraceLedger ledger, TraceLedger.Entry entry, TraceService traces) {
		BlockPos from = entry.pos().pos();
		BlockPos to = entry.to().orElse(null);
		ItemStack wanted = entry.stack().orElse(ItemStack.EMPTY);
		if (to == null || wanted.isEmpty() || !(level.getBlockEntity(to) instanceof Container target) || !(level.getBlockEntity(from) instanceof Container)) {
			return Result.BLOCKED;
		}
		int slot = -1;
		if (entry.toSlot() >= 0 && entry.toSlot() < target.getContainerSize() && ItemStack.matches(target.getItem(entry.toSlot()), wanted)) {
			slot = entry.toSlot();
		} else {
			for (int i = 0; i < target.getContainerSize(); i++) {
				if (ItemStack.matches(target.getItem(i), wanted)) {
					slot = i;
					break;
				}
			}
		}
		if (slot < 0) {
			return Result.BLOCKED;
		}
		int before = ledger.entries().size();
		if (!traces.moveStack(level, to, slot, from, CAUSE)) {
			return Result.WAIT;
		}
		dropNew(ledger, before);
		ledger.remove(entry);
		return Result.DONE;
	}

	/** Drops the entries the reverse edit itself wrote (its broken neighbours, {@code <cause>/dependent}, stay). */
	private static void dropNew(TraceLedger ledger, int before) {
		List<TraceLedger.Entry> all = ledger.entries();
		List<TraceLedger.Entry> written = new ArrayList<>();
		for (int i = before; i < all.size(); i++) {
			if (all.get(i).cause().equals(CAUSE)) {
				written.add(all.get(i));
			}
		}
		written.forEach(ledger::remove);
	}
}
