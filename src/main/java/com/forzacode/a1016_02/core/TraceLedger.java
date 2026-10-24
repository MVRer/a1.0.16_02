package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
 * with none. A CONVERT whose block was changed since, in what the conversion changed, by anything but
 * {@link TraceService} (a player, redstone, a piston) is marked so ({@link #changedSince}); the marks are stored under
 * {@value #CHANGED_KEY} by their place in the order, a key v1.3 skips.
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
	 * destination ({@code to}), CONVERT also the block it left ({@code after}, under a key v1.3 skips). REMOVE_STACK and MOVE_STACK keep the stack and slots. BLOCK_ENTITY is block entity
	 * data replaced in place (sign text): {@code blockEntity} is the data from before. EQUIP is a stack taken from a
	 * container ({@code pos}, {@code slot}) now worn by a mob ({@code entity}, equipment slot ordinal in
	 * {@code toSlot}, where it was in {@code to}); if the mob died the item dropped and there is nothing to undo. NAME
	 * (D-086) is a mob's custom name taken ({@link #recordName}): {@code entity} is the mob, {@code pos} its block when
	 * the name was taken, {@code blockEntity} holds {@code {name: <component>}} (and {@code visible: true} if the name
	 * showed without being looked at). MOVE_MOB (the self-created layer, R13-7) is a mob taken from where it stood
	 * ({@link #recordMobMove}): {@code entity} is the mob, {@code pos} where it stood, {@code to} where it was first put.
	 */
	public enum Kind { REMOVE, MOVE, CONVERT, REMOVE_STACK, MOVE_STACK, BLOCK_ENTITY, EQUIP, NAME, MOVE_MOB }

	/** Where NAME entries are stored in {@code traces.dat}, apart from the others (v1.3 skips the key). */
	public static final String NAMES_KEY = "names";
	/** Where MOVE_MOB entries are stored in {@code traces.dat}, apart from the others (v1.3 and older layer jars skip the key). */
	public static final String MOBS_KEY = "mobs";
	/** Where the marks of the CONVERT entries changed since are stored in {@code traces.dat}: their indexes (v1.3 skips the key). */
	public static final String CHANGED_KEY = "changed";
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
	 * @param after       the block a CONVERT left (empty for every other kind, and for a CONVERT ledgered before it
	 *                    was kept): its undo takes it back only while the block still holds what the conversion changed
	 *                    (the block, and each property it set) and nothing but core changed that since
	 */
	public record Entry(Kind kind, String cause, long day, GlobalPos pos, Optional<BlockPos> to, Optional<BlockState> state,
			Optional<CompoundTag> blockEntity, Optional<ItemStack> stack, int slot, int toSlot, Optional<UUID> entity, Optional<BlockState> after) {
		/** An entry without the block a conversion left. */
		public Entry(Kind kind, String cause, long day, GlobalPos pos, Optional<BlockPos> to, Optional<BlockState> state,
				Optional<CompoundTag> blockEntity, Optional<ItemStack> stack, int slot, int toSlot, Optional<UUID> entity) {
			this(kind, cause, day, pos, to, state, blockEntity, stack, slot, toSlot, entity, Optional.empty());
		}

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
				UUIDUtil.CODEC.optionalFieldOf("entity").forGetter(Entry::entity),
				// A key v1.3 does not read (it skips it); an entry from before loads without it.
				BlockState.CODEC.lenientOptionalFieldOf("after").forGetter(Entry::after)
		).apply(i, Entry::new));
	}

	/** A NAME (or MOVE_MOB) entry and its index in the whole ledger, stored under {@value #NAMES_KEY} (or {@value #MOBS_KEY}). */
	private record Placed(int at, Entry entry) {
		static final Codec<Placed> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("at").forGetter(Placed::at),
				Entry.CODEC.fieldOf("entry").forGetter(Placed::entry)
		).apply(i, Placed::new));
	}

	public static final Codec<TraceLedger> CODEC = RecordCodecBuilder.create(i -> i.group(
			Entry.CODEC.listOf().fieldOf("entries").forGetter(TraceLedger::unnamed),
			Placed.CODEC.listOf().lenientOptionalFieldOf(NAMES_KEY, List.of()).forGetter(TraceLedger::names),
			Codec.INT.listOf().lenientOptionalFieldOf(CHANGED_KEY, List.of()).forGetter(TraceLedger::changedIndexes),
			Placed.CODEC.listOf().lenientOptionalFieldOf(MOBS_KEY, List.of()).forGetter(TraceLedger::mobs)
	).apply(i, TraceLedger::merged));
	public static final SavedDataType<TraceLedger> TYPE = new SavedDataType<>(A1016_02.id("traces"), TraceLedger::new, CODEC, null);

	/**
	 * The ledger whose CONVERT entries are watched now ({@link #blockChanged}): the running server's, while it has any.
	 * Read on every block change in a server level.
	 */
	private static volatile @Nullable TraceLedger watching;

	private final List<Entry> entries = new ArrayList<>();
	/** The CONVERT entries whose block was changed since (by identity). */
	private final Set<Entry> changed = Collections.newSetFromMap(new IdentityHashMap<>());
	/** The CONVERT entries with {@code after} not changed since, by level and position: the ones a block change can mark. */
	private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<List<Entry>>> watched = new HashMap<>();
	/** The server this ledger was last read for ({@link #get}), so the hook never marks one server's entries from another. */
	private @Nullable MinecraftServer server;

	public TraceLedger() {
	}

	private TraceLedger(List<Entry> entries) {
		this.entries.addAll(entries);
		this.entries.forEach(this::watch);
	}

	/**
	 * The codec's: the other entries in their order, each NAME and MOVE_MOB entry put back at its index (ascending,
	 * clamped), then the marks of the CONVERT entries changed since, by index (an index that names no CONVERT is skipped).
	 */
	private static TraceLedger merged(List<Entry> unnamed, List<Placed> names, List<Integer> changedAt, List<Placed> mobs) {
		List<Entry> all = new ArrayList<>(unnamed);
		List<Placed> sorted = new ArrayList<>();
		names.stream().filter(p -> p.entry().kind() == Kind.NAME).forEach(sorted::add);
		mobs.stream().filter(p -> p.entry().kind() == Kind.MOVE_MOB).forEach(sorted::add);
		sorted.sort(Comparator.comparingInt(Placed::at));
		for (Placed placed : sorted) {
			all.add(Math.clamp(placed.at(), 0, all.size()), placed.entry());
		}
		TraceLedger ledger = new TraceLedger(all);
		for (int at : changedAt) {
			if (at >= 0 && at < all.size() && all.get(at).kind() == Kind.CONVERT) {
				ledger.markChanged(all.get(at));
			}
		}
		return ledger;
	}

	/** What is stored under {@value #CHANGED_KEY}: the index of each CONVERT entry changed since, ascending. */
	private List<Integer> changedIndexes() {
		List<Integer> at = new ArrayList<>();
		for (int i = 0; i < entries.size(); i++) {
			if (changed.contains(entries.get(i))) {
				at.add(i);
			}
		}
		return at;
	}

	/** What is stored under {@code entries}: every entry but NAME and MOVE_MOB, in order (what v1.3 can read). */
	private List<Entry> unnamed() {
		return entries.stream().filter(e -> e.kind() != Kind.NAME && e.kind() != Kind.MOVE_MOB).toList();
	}

	/** What is stored under {@value #NAMES_KEY}: each NAME entry with its index in the whole ledger. */
	private List<Placed> names() {
		return placed(Kind.NAME);
	}

	/** What is stored under {@value #MOBS_KEY}: each MOVE_MOB entry with its index in the whole ledger. */
	private List<Placed> mobs() {
		return placed(Kind.MOVE_MOB);
	}

	private List<Placed> placed(Kind kind) {
		List<Placed> found = new ArrayList<>();
		for (int at = 0; at < entries.size(); at++) {
			if (entries.get(at).kind() == kind) {
				found.add(new Placed(at, entries.get(at)));
			}
		}
		return found;
	}

	public static TraceLedger get(MinecraftServer server) {
		TraceLedger ledger = server.getDataStorage().computeIfAbsent(TYPE);
		if (ledger.server != server) {
			ledger.server = server;
			ledger.rewatch();
		}
		return ledger;
	}

	/** Core: the server stopped, so no ledger is watched until the next one is read. */
	static void stopWatching() {
		watching = null;
	}

	/** Oldest first. Unmodifiable. */
	public List<Entry> entries() {
		return Collections.unmodifiableList(entries);
	}

	/** Adds an entry and returns it as stored. An empty item stack cannot be saved, so it is dropped from the entry. */
	Entry add(Entry entry) {
		if (entry.stack().filter(ItemStack::isEmpty).isPresent()) {
			entry = new Entry(entry.kind(), entry.cause(), entry.day(), entry.pos(), entry.to(), entry.state(), entry.blockEntity(),
					Optional.empty(), entry.slot(), entry.toSlot(), entry.entity(), entry.after());
		}
		entries.add(entry);
		watch(entry);
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

	/**
	 * Records a mob as taken from where it stood (kind MOVE_MOB, the self-created layer's animals; R13-7) under
	 * {@code cause}: {@code pos} is {@code from} (where it stood before anything moved it), {@code to} where it was first
	 * put, {@code entity} its UUID, the day today. Record it once per mob and scene, when it is first moved, so the one
	 * entry keeps the spot it came from. Fires {@link HerobrineEvents#TRACED} (not placed). {@link TraceService#undo}
	 * puts the mob back there (out of view), so the switch and Ending D give it back. Server thread.
	 */
	public static Entry recordMobMove(ServerLevel level, Mob mob, BlockPos from, BlockPos to, String cause) {
		Objects.requireNonNull(cause, "cause");
		MinecraftServer server = level.getServer();
		Entry entry = get(server).add(new Entry(Kind.MOVE_MOB, cause, GameClock.day(server), GlobalPos.of(level.dimension(), from.immutable()),
				Optional.of(to.immutable()), Optional.empty(), Optional.empty(), Optional.empty(), -1, -1, Optional.of(mob.getUUID())));
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
		int index = indexOf(old);
		if (index < 0) {
			return false;
		}
		Entry was = entries.get(index);
		boolean wasChanged = changed.remove(was);
		unwatch(was);
		entries.set(index, updated);
		if (wasChanged && updated.kind() == Kind.CONVERT) {
			changed.add(updated);
		} else {
			watch(updated);
		}
		setDirty();
		return true;
	}

	/** Drops an entry once it has been undone. */
	public boolean remove(Entry entry) {
		int index = indexOf(entry);
		if (index < 0) {
			return false;
		}
		Entry was = entries.remove(index);
		changed.remove(was);
		unwatch(was);
		setDirty();
		return true;
	}

	/** This very entry's index, else the first equal one's, else -1. */
	private int indexOf(Entry entry) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i) == entry) {
				return i;
			}
		}
		return entries.indexOf(entry);
	}

	// --- conversions changed since ---

	/**
	 * True if this CONVERT entry's block was changed since the conversion, in what the conversion changed (its block,
	 * or a property it set), by anything but {@link TraceService}: a player who closed the door it opened, turned the
	 * block again, or broke it and placed it again; redstone; a piston. The block may be just what the conversion left
	 * again (the door opened again by hand): it is the player's all the same. Vanilla's own changes of what the
	 * conversion never set (a furnace lit, a hive's honey, a lectern's book, water in a chest) do not count. False for
	 * any other entry, and for one not in this ledger.
	 */
	public boolean changedSince(Entry entry) {
		int index = indexOf(entry);
		return index >= 0 && changed.contains(entries.get(index));
	}

	/**
	 * Core's {@code Level.setBlock} hook (nothing else calls it): the block at {@code pos} in this server level changed.
	 * If it no longer holds what the newest watched conversion there changed ({@link TraceUndo#holds}), every watched
	 * conversion there is marked changed since; never for one of {@link TraceService}'s own edits. One field read while
	 * nothing is watched. Server thread.
	 */
	public static void blockChanged(ServerLevel level, BlockPos pos) {
		TraceLedger ledger = watching;
		if (ledger == null || ledger.server != level.getServer() || TraceEdit.applying()) {
			return;
		}
		Long2ObjectOpenHashMap<List<Entry>> here = ledger.watched.get(level.dimension());
		List<Entry> at = here == null ? null : here.get(pos.asLong());
		if (at == null) {
			return;
		}
		// The newest conversion there is the one the block shows now (core's later edits of it are not watched).
		Entry newest = at.getLast();
		BlockState now = level.getBlockState(pos);
		if (TraceUndo.holds(newest.state().orElseThrow(), newest.after().orElseThrow(), now)) {
			return;
		}
		for (Entry entry : List.copyOf(at)) {
			ledger.markChanged(entry);
			A1016_02.LOGGER.debug("[a1016] trace '{}' at {}: changed since ({})", entry.cause(), entry.pos().pos(), now);
		}
	}

	private void markChanged(Entry entry) {
		if (changed.add(entry)) {
			unwatch(entry);
			setDirty();
		}
	}

	private static boolean watchable(Entry entry) {
		return entry.kind() == Kind.CONVERT && entry.after().isPresent() && entry.state().isPresent();
	}

	private void watch(Entry entry) {
		if (!watchable(entry) || changed.contains(entry)) {
			return;
		}
		watched.computeIfAbsent(entry.pos().dimension(), k -> new Long2ObjectOpenHashMap<>())
				.computeIfAbsent(entry.pos().pos().asLong(), k -> new ArrayList<>(1)).add(entry);
		rewatch();
	}

	private void unwatch(Entry entry) {
		if (!watchable(entry)) {
			return;
		}
		Long2ObjectOpenHashMap<List<Entry>> here = watched.get(entry.pos().dimension());
		if (here == null) {
			return;
		}
		long key = entry.pos().pos().asLong();
		List<Entry> at = here.get(key);
		if (at != null && at.removeIf(e -> e == entry) && at.isEmpty()) {
			here.remove(key);
			if (here.isEmpty()) {
				watched.remove(entry.pos().dimension());
			}
		}
		rewatch();
	}

	/** This ledger is the watched one while it was read for a running server and has anything to watch. */
	private void rewatch() {
		if (server == null) {
			return;
		}
		if (!watched.isEmpty()) {
			watching = this;
		} else if (watching == this) {
			watching = null;
		}
	}
}
