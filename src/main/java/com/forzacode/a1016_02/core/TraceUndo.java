package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * {@link TraceService#undo}: one ledger entry taken back exactly once, through the service's own edits (so it is
 * view-checked, vetoed and silent like every edit). REMOVE and REMOVE_STACK through {@code restoreBlock} and
 * {@code restoreStack} (which close the entry); MOVE, CONVERT, MOVE_STACK and sign text by the reverse edit, after
 * which both the old entry and the one the reverse edit wrote are dropped (its broken neighbours,
 * {@code <cause>/dependent}, stay ledgered). Nothing is created: a moved block goes back only if it is still where it
 * was moved, a stack only if it is still in its slot. Nothing is loaded: an entry whose chunk is not loaded waits.
 */
final class TraceUndo {
	private TraceUndo() {
	}

	static TraceService.UndoResult undo(TraceService traces, ServerLevel level, TraceLedger.Entry entry, String cause) {
		TraceLedger ledger = TraceLedger.get(level.getServer());
		if (!ledger.entries().contains(entry) || !entry.pos().dimension().equals(level.dimension()) || entry.kind() == TraceLedger.Kind.EQUIP) {
			// Already undone, never ledgered, another level, or worn by a mob (the mob drops it).
			return TraceService.UndoResult.BLOCKED;
		}
		BlockPos at = entry.pos().pos();
		if (!loaded(level, at) || entry.to().isPresent() && !loaded(level, entry.to().get())) {
			return TraceService.UndoResult.UNLOADED;
		}
		return switch (entry.kind()) {
			case REMOVE -> remove(traces, level, entry);
			case MOVE -> move(traces, level, ledger, entry, cause);
			case CONVERT -> convert(traces, level, ledger, entry, cause);
			case BLOCK_ENTITY -> signText(traces, level, ledger, entry, cause);
			case REMOVE_STACK -> level.getBlockEntity(at) instanceof Container ? result(traces.restoreStack(level, entry, at)) : TraceService.UndoResult.BLOCKED;
			case MOVE_STACK -> moveStack(traces, level, ledger, entry, cause);
			case EQUIP -> TraceService.UndoResult.BLOCKED;
		};
	}

	private static TraceService.UndoResult result(boolean done) {
		return done ? TraceService.UndoResult.DONE : TraceService.UndoResult.IN_VIEW;
	}

	static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	private static TraceService.UndoResult remove(TraceService traces, ServerLevel level, TraceLedger.Entry entry) {
		BlockPos at = entry.pos().pos();
		BlockState state = entry.state().orElse(null);
		if (state == null) {
			return TraceService.UndoResult.BLOCKED;
		}
		BlockState now = level.getBlockState(at);
		if (!now.canBeReplaced() || level.getBlockEntity(at) != null || !state.canSurvive(level, at)) {
			// Built over, or a torch whose wall is not back yet (it can come back once the wall is).
			return TraceService.UndoResult.BLOCKED;
		}
		return result(traces.restoreBlock(level, entry, at));
	}

	private static TraceService.UndoResult move(TraceService traces, ServerLevel level, TraceLedger ledger, TraceLedger.Entry entry, String cause) {
		BlockPos from = entry.pos().pos();
		BlockPos to = entry.to().orElse(null);
		BlockState state = entry.state().orElse(null);
		if (to == null || state == null || !level.getBlockState(to).is(state.getBlock())) {
			// Not there any more (taken, or moved on and undone already): nothing to bring back.
			return TraceService.UndoResult.BLOCKED;
		}
		BlockState home = level.getBlockState(from);
		if (!home.canBeReplaced() || level.getBlockEntity(from) != null) {
			return TraceService.UndoResult.BLOCKED;
		}
		int before = ledger.entries().size();
		if (!traces.move(level, to, from, cause)) {
			return TraceService.UndoResult.IN_VIEW;
		}
		close(ledger, entry, before, cause);
		return TraceService.UndoResult.DONE;
	}

	private static TraceService.UndoResult convert(TraceService traces, ServerLevel level, TraceLedger ledger, TraceLedger.Entry entry, String cause) {
		BlockPos at = entry.pos().pos();
		BlockState old = entry.state().orElse(null);
		if (old == null) {
			return TraceService.UndoResult.BLOCKED;
		}
		BlockState now = level.getBlockState(at);
		if (now == old) {
			ledger.remove(entry);
			return TraceService.UndoResult.DONE;
		}
		PlayerWatch watch = Services.watch();
		if (watch.wasPlacedByPlayer(level, at) || now.isAir() && watch.wasDugByPlayer(level, at) || level.getBlockEntity(at) != null && !old.hasBlockEntity()) {
			// The player has built or dug there since: theirs now.
			return TraceService.UndoResult.BLOCKED;
		}
		int before = ledger.entries().size();
		if (!traces.convert(level, at, old, cause)) {
			return TraceService.UndoResult.IN_VIEW;
		}
		close(ledger, entry, before, cause);
		return TraceService.UndoResult.DONE;
	}

	private static TraceService.UndoResult signText(TraceService traces, ServerLevel level, TraceLedger ledger, TraceLedger.Entry entry, String cause) {
		BlockPos at = entry.pos().pos();
		if (!(level.getBlockEntity(at) instanceof SignBlockEntity sign) || sign.isWaxed() || entry.blockEntity().isEmpty()) {
			return TraceService.UndoResult.BLOCKED;
		}
		CompoundTag old = entry.blockEntity().get();
		RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		int before = ledger.entries().size();
		if (!traces.editSign(level, at, lines(old.get("front_text"), ops), lines(old.get("back_text"), ops), cause)) {
			return TraceService.UndoResult.IN_VIEW;
		}
		close(ledger, entry, before, cause);
		return TraceService.UndoResult.DONE;
	}

	private static List<Component> lines(@Nullable Tag tag, RegistryOps<Tag> ops) {
		if (tag == null) {
			return List.of();
		}
		return SignText.CODEC.parse(ops, tag).result().map(text -> new ArrayList<>(text.getMessages(false))).<List<Component>>map(l -> l).orElse(List.of());
	}

	private static TraceService.UndoResult moveStack(TraceService traces, ServerLevel level, TraceLedger ledger, TraceLedger.Entry entry, String cause) {
		BlockPos from = entry.pos().pos();
		BlockPos to = entry.to().orElse(null);
		ItemStack wanted = entry.stack().orElse(ItemStack.EMPTY);
		if (to == null || wanted.isEmpty() || !(level.getBlockEntity(to) instanceof Container target) || !(level.getBlockEntity(from) instanceof Container)) {
			return TraceService.UndoResult.BLOCKED;
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
			return TraceService.UndoResult.BLOCKED;
		}
		int before = ledger.entries().size();
		if (!traces.moveStack(level, to, slot, from, cause)) {
			return TraceService.UndoResult.IN_VIEW;
		}
		close(ledger, entry, before, cause);
		return TraceService.UndoResult.DONE;
	}

	/** Closes the undone entry and drops what the reverse edit itself wrote (its broken neighbours stay). */
	private static void close(TraceLedger ledger, TraceLedger.Entry entry, int before, String cause) {
		List<TraceLedger.Entry> all = ledger.entries();
		List<TraceLedger.Entry> written = new ArrayList<>();
		for (int i = before; i < all.size(); i++) {
			if (all.get(i).cause().equals(cause)) {
				written.add(all.get(i));
			}
		}
		written.forEach(ledger::remove);
		ledger.remove(entry);
	}
}
