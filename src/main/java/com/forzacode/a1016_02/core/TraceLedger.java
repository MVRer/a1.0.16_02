package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * Everything {@link TraceService} removed, moved or changed, oldest first, so Ending D can undo it (newest first).
 * Causes are {@code <workstream>:<what>}. A cause containing {@value #LEFT_BY_OTHERS} ({@code lore:left/F07},
 * {@code lore:left/ending-d/stair}, {@code world:left/...}) is something others left, never his trace: lore does not
 * count it as his ({@link #isLeftByOthers}) and Ending D never undoes it. Stored as {@code data/a1016_02/traces.dat}:
 * every entry but NAME under {@code entries}, as v1.3 wrote them, and NAME entries apart under {@value #NAMES_KEY}
 * with their place in the order, so v1.3 still reads a file written now (it skips the names) and an old file loads
 * with none.
 */
public final class TraceLedger extends SavedData {
	/** The cause segment that marks what others left (the people before the player), in any workstream. */
	public static final String LEFT_BY_OTHERS = ":left/";

	/** True if this cause is something others left ({@value #LEFT_BY_OTHERS} anywhere in it), not his trace. */
	public static boolean isLeftByOthers(String cause) {
		return cause.contains(LEFT_BY_OTHERS);
	}

	/**
	 * What an entry undoes. REMOVE, MOVE and CONVERT keep the block (and block entity data) from before; MOVE also the
	 * destination ({@code to}). REMOVE_STACK and MOVE_STACK keep the stack and slots. BLOCK_ENTITY is block entity
	 * data replaced in place (sign text): {@code blockEntity} is the data from before. EQUIP is a stack taken from a
	 * container ({@code pos}, {@code slot}) now worn by a mob ({@code entity}, equipment slot ordinal in
	 * {@code toSlot}, where it was in {@code to}); if the mob died the item dropped and there is nothing to undo. NAME
	 * (D-086) is a mob's custom name taken ({@link #recordName}): {@code entity} is the mob, {@code pos} its block when
	 * the name was taken, {@code blockEntity} holds {@code {name: <component>}} (and {@code visible: true} if the name
	 * showed without being looked at).
	 */
	public enum Kind { REMOVE, MOVE, CONVERT, REMOVE_STACK, MOVE_STACK, BLOCK_ENTITY, EQUIP, NAME }

	/** Where NAME entries are stored in {@code traces.dat}, apart from the others (v1.3 skips the key). */
	public static final String NAMES_KEY = "names";
	/** The name's key in a NAME entry's {@code blockEntity}. */
	public static final String NAME_KEY = "name";
	/** Set (true) in a NAME entry's {@code blockEntity} when the name showed without being looked at. */
	public static final String NAME_VISIBLE_KEY = "visible";

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

	/** A NAME entry and its index in the whole ledger, stored under {@value #NAMES_KEY}. */
	private record Placed(int at, Entry entry) {
		static final Codec<Placed> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("at").forGetter(Placed::at),
				Entry.CODEC.fieldOf("entry").forGetter(Placed::entry)
		).apply(i, Placed::new));
	}

	public static final Codec<TraceLedger> CODEC = RecordCodecBuilder.create(i -> i.group(
			Entry.CODEC.listOf().fieldOf("entries").forGetter(TraceLedger::unnamed),
			Placed.CODEC.listOf().lenientOptionalFieldOf(NAMES_KEY, List.of()).forGetter(TraceLedger::names)
	).apply(i, TraceLedger::merged));
	public static final SavedDataType<TraceLedger> TYPE = new SavedDataType<>(A1016_02.id("traces"), TraceLedger::new, CODEC, null);

	private final List<Entry> entries = new ArrayList<>();

	public TraceLedger() {
	}

	private TraceLedger(List<Entry> entries) {
		this.entries.addAll(entries);
	}

	/** The codec's: the other entries in their order, each NAME entry put back at its index (ascending, clamped). */
	private static TraceLedger merged(List<Entry> unnamed, List<Placed> names) {
		List<Entry> all = new ArrayList<>(unnamed);
		List<Placed> sorted = new ArrayList<>(names);
		sorted.sort(Comparator.comparingInt(Placed::at));
		for (Placed placed : sorted) {
			if (placed.entry().kind() == Kind.NAME) {
				all.add(Math.clamp(placed.at(), 0, all.size()), placed.entry());
			}
		}
		return new TraceLedger(all);
	}

	/** What is stored under {@code entries}: every entry but NAME, in order. */
	private List<Entry> unnamed() {
		return entries.stream().filter(e -> e.kind() != Kind.NAME).toList();
	}

	/** What is stored under {@value #NAMES_KEY}: each NAME entry with its index in the whole ledger. */
	private List<Placed> names() {
		List<Placed> names = new ArrayList<>();
		for (int at = 0; at < entries.size(); at++) {
			if (entries.get(at).kind() == Kind.NAME) {
				names.add(new Placed(at, entries.get(at)));
			}
		}
		return names;
	}

	public static TraceLedger get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Oldest first. Unmodifiable. */
	public List<Entry> entries() {
		return Collections.unmodifiableList(entries);
	}

	/** Adds an entry and returns it as stored. An empty item stack cannot be saved, so it is dropped from the entry. */
	Entry add(Entry entry) {
		if (entry.stack().filter(ItemStack::isEmpty).isPresent()) {
			entry = new Entry(entry.kind(), entry.cause(), entry.day(), entry.pos(), entry.to(), entry.state(), entry.blockEntity(),
					Optional.empty(), entry.slot(), entry.toSlot(), entry.entity());
		}
		entries.add(entry);
		setDirty();
		return entry;
	}

	/**
	 * Records a mob's custom name as taken (D-086, kind NAME) under {@code cause}, for {@code MobTamper.unname}: its
	 * position is the mob's block now, {@code entity} its UUID, {@code blockEntity} {@code {name: oldName}} (and
	 * {@code visible: true} if the mob shows its name now without being looked at), the day today. Fires
	 * {@link HerobrineEvents#TRACED} (not placed). Call it before the name is cleared: it returns null and records
	 * nothing if the name cannot be stored, and then the name must stay. {@link TraceService#undo} gives it back.
	 * Server thread.
	 *
	 * @return the entry as stored, or null if nothing was recorded
	 */
	public static @Nullable Entry recordName(ServerLevel level, Mob mob, Component oldName, String cause) {
		Objects.requireNonNull(oldName, "oldName");
		Objects.requireNonNull(cause, "cause");
		Optional<Tag> name = ComponentSerialization.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), oldName).result();
		if (name.isEmpty()) {
			A1016_02.LOGGER.warn("[a1016] trace: a name could not be stored ({}), nothing recorded", cause);
			return null;
		}
		CompoundTag data = new CompoundTag();
		data.put(NAME_KEY, name.get());
		if (mob.isCustomNameVisible()) {
			data.putBoolean(NAME_VISIBLE_KEY, true);
		}
		MinecraftServer server = level.getServer();
		Entry entry = get(server).add(new Entry(Kind.NAME, cause, GameClock.day(server), GlobalPos.of(level.dimension(), mob.blockPosition().immutable()),
				Optional.empty(), Optional.empty(), Optional.of(data), Optional.empty(), -1, -1, Optional.of(mob.getUUID())));
		A1016_02.LOGGER.debug("[a1016] trace name of {} ({})", mob, cause);
		TraceService.traced(level, entry, false);
		return entry;
	}

	/** The name a NAME entry holds, read with this level's registries; empty if it is not a NAME entry or cannot be read. */
	public static Optional<Component> storedName(ServerLevel level, Entry entry) {
		if (entry.kind() != Kind.NAME) {
			return Optional.empty();
		}
		Tag tag = entry.blockEntity().map(data -> data.get(NAME_KEY)).orElse(null);
		if (tag == null) {
			return Optional.empty();
		}
		return ComponentSerialization.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).result();
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
