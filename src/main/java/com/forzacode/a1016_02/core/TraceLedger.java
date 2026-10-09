package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Everything {@link TraceService} removed, moved or changed, oldest first, so Ending D can undo it (newest first).
 * Ending D skips causes starting with {@code lore:left/}: what others left stays. Stored as
 * {@code data/a1016_02/traces.dat}.
 */
public final class TraceLedger extends SavedData {
	/**
	 * What an entry undoes. REMOVE, MOVE and CONVERT keep the block (and block entity data) from before; MOVE also the
	 * destination ({@code to}). REMOVE_STACK and MOVE_STACK keep the stack and slots. BLOCK_ENTITY is block entity
	 * data replaced in place (sign text): {@code blockEntity} is the data from before. EQUIP is a stack taken from a
	 * container ({@code pos}, {@code slot}) now worn by a mob ({@code entity}, equipment slot ordinal in
	 * {@code toSlot}, where it was in {@code to}); if the mob died the item dropped and there is nothing to undo.
	 */
	public enum Kind { REMOVE, MOVE, CONVERT, REMOVE_STACK, MOVE_STACK, BLOCK_ENTITY, EQUIP }

	/**
	 * One edit.
	 *
	 * @param pos         where it happened (the source for moves)
	 * @param to          the destination of a move, same dimension
	 * @param state       the block before the edit (the moved block for MOVE)
	 * @param blockEntity the block entity data before the edit
	 * @param stack       the removed or moved item stack
	 * @param slot        the source slot of a stack edit, or -1
	 * @param toSlot      the destination slot of a stack move (the equipment slot ordinal for EQUIP), or -1
	 * @param entity      the mob that wears the stack (EQUIP)
	 */
	public record Entry(Kind kind, String cause, long day, GlobalPos pos, Optional<BlockPos> to, Optional<BlockState> state,
			Optional<CompoundTag> blockEntity, Optional<ItemStack> stack, int slot, int toSlot, Optional<UUID> entity) {
		/** An entry that involves no entity. */
		public Entry(Kind kind, String cause, long day, GlobalPos pos, Optional<BlockPos> to, Optional<BlockState> state,
				Optional<CompoundTag> blockEntity, Optional<ItemStack> stack, int slot, int toSlot) {
			this(kind, cause, day, pos, to, state, blockEntity, stack, slot, toSlot, Optional.empty());
		}

		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				CoreCodecs.enumCodec(Kind.class).fieldOf("kind").forGetter(Entry::kind),
				Codec.STRING.fieldOf("cause").forGetter(Entry::cause),
				Codec.LONG.fieldOf("day").forGetter(Entry::day),
				GlobalPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
				BlockPos.CODEC.optionalFieldOf("to").forGetter(Entry::to),
				BlockState.CODEC.optionalFieldOf("state").forGetter(Entry::state),
				CompoundTag.CODEC.optionalFieldOf("blockEntity").forGetter(Entry::blockEntity),
				ItemStack.CODEC.optionalFieldOf("stack").forGetter(Entry::stack),
				Codec.INT.optionalFieldOf("slot", -1).forGetter(Entry::slot),
				Codec.INT.optionalFieldOf("toSlot", -1).forGetter(Entry::toSlot),
				UUIDUtil.CODEC.optionalFieldOf("entity").forGetter(Entry::entity)
		).apply(i, Entry::new));
	}

	public static final Codec<TraceLedger> CODEC = Entry.CODEC.listOf().fieldOf("entries").codec()
			.xmap(TraceLedger::new, ledger -> ledger.entries);
	public static final SavedDataType<TraceLedger> TYPE = new SavedDataType<>(A1016_02.id("traces"), TraceLedger::new, CODEC, null);

	private final List<Entry> entries = new ArrayList<>();

	public TraceLedger() {
	}

	private TraceLedger(List<Entry> entries) {
		this.entries.addAll(entries);
	}

	public static TraceLedger get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Oldest first. Unmodifiable. */
	public List<Entry> entries() {
		return Collections.unmodifiableList(entries);
	}

	/** Adds an entry. An empty item stack cannot be saved, so it is dropped from the entry. */
	void add(Entry entry) {
		if (entry.stack().filter(ItemStack::isEmpty).isPresent()) {
			entry = new Entry(entry.kind(), entry.cause(), entry.day(), entry.pos(), entry.to(), entry.state(), entry.blockEntity(),
					Optional.empty(), entry.slot(), entry.toSlot(), entry.entity());
		}
		entries.add(entry);
		setDirty();
	}

	/** Replaces an entry in place (keeps the order), for an edit that was partly undone. False if it is not here. */
	boolean replace(Entry old, Entry updated) {
		int index = entries.indexOf(old);
		if (index < 0) {
			return false;
		}
		entries.set(index, updated);
		setDirty();
		return true;
	}

	/** Drops an entry once it has been undone. */
	public boolean remove(Entry entry) {
		boolean removed = entries.remove(entry);
		if (removed) {
			setDirty();
		}
		return removed;
	}
}
