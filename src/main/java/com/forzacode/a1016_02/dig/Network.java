package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/**
 * One "Under you" network: the 2x2 corridors under a base, the shaft under the bed and the dead end with the chest.
 * Pure state (plus NBT); {@link NetworkGrower} does the growing.
 */
public final class Network {
	/** A corridor end that keeps digging. */
	static final class Head {
		BlockPos pos;
		Direction dir;
		int run;
		/** A column (x, z) it is heading for, or null. */
		@Nullable BlockPos target;
		/** Heading for the foot of the shaft. */
		boolean toShaft;

		Head(BlockPos pos, Direction dir, int run) {
			this.pos = pos;
			this.dir = dir;
			this.run = run;
		}
	}

	final ResourceKey<Level> dimension;
	final BlockPos base;
	/** Corridor anchor height (cells are this and the block above). */
	int depth;
	final List<BlockPos> anchors = new ArrayList<>();
	final LongOpenHashSet anchorSet = new LongOpenHashSet();
	final LongOpenHashSet cells = new LongOpenHashSet();
	final LongOpenHashSet shaftAnchors = new LongOpenHashSet();
	final List<Head> heads = new ArrayList<>();
	final List<BlockPos> targets = new ArrayList<>();
	int nextHead;
	@Nullable BlockPos bedHead;
	@Nullable BlockPos bedFoot;
	/** Where the corridor meets the shaft (an anchor), once reached. */
	@Nullable BlockPos shaftFoot;
	boolean shaftDone;
	/** The night the shaft last got stuck; it retries the next night. */
	long shaftStuckNight = Long.MIN_VALUE;
	/** The floor cell at the end of the dead end where the chest goes. */
	@Nullable BlockPos alcove;
	@Nullable BlockPos chest;
	long chestTriedNight = Long.MIN_VALUE;
	/** The last night index credited, or MIN_VALUE before the first. */
	long lastNight = Long.MIN_VALUE;
	int nights;
	int budget;
	int underBaseSite = -1;
	int shaftSite = -1;
	/** Stacks moved into the chest, and stacks taken while there was no chest (they stay in the ledger). */
	int stacksMoved;
	int stacksLedgered;
	/** Not saved: founding is tried once per night. */
	long foundTriedNight = Long.MIN_VALUE;

	Network(ResourceKey<Level> dimension, BlockPos base) {
		this.dimension = dimension;
		this.base = base.immutable();
	}

	public ResourceKey<Level> dimension() {
		return dimension;
	}

	public BlockPos base() {
		return base;
	}

	public int depth() {
		return depth;
	}

	public List<BlockPos> anchors() {
		return List.copyOf(anchors);
	}

	/** Every air cell of the network (packed positions). Do not modify. */
	public LongOpenHashSet cells() {
		return cells;
	}

	public boolean isShaftCell(BlockPos cell) {
		for (int dx = 0; dx <= 1; dx++) {
			for (int dy = 0; dy <= 1; dy++) {
				for (int dz = 0; dz <= 1; dz++) {
					if (shaftAnchors.contains(cell.offset(-dx, -dy, -dz).asLong())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public Optional<BlockPos> chest() {
		return Optional.ofNullable(chest);
	}

	public Optional<BlockPos> alcove() {
		return Optional.ofNullable(alcove);
	}

	public boolean shaftDone() {
		return shaftDone;
	}

	public Optional<BlockPos> bed() {
		return Optional.ofNullable(bedHead);
	}

	public int nights() {
		return nights;
	}

	public int budget() {
		return budget;
	}

	/** The topmost cell of the current shaft, once the corridor has reached its foot. */
	public Optional<BlockPos> shaftTop() {
		if (shaftFoot == null) {
			return Optional.empty();
		}
		BlockPos top = shaftFoot;
		for (long packed : shaftAnchors) {
			BlockPos anchor = BlockPos.of(packed);
			if (anchor.getX() == shaftFoot.getX() && anchor.getZ() == shaftFoot.getZ() && anchor.getY() > top.getY()) {
				top = anchor;
			}
		}
		return Optional.of(top.above());
	}

	void addAnchor(BlockPos anchor, boolean shaft) {
		BlockPos a = anchor.immutable();
		if (anchorSet.add(a.asLong())) {
			anchors.add(a);
			for (BlockPos cell : Tunnels.cube(a)) {
				cells.add(cell.asLong());
			}
		}
		if (shaft) {
			shaftAnchors.add(a.asLong());
		}
	}

	/** Credits the nights that passed since the last credit (the first call credits one). */
	void creditNights(long nightIndex, DigConfig config) {
		if (lastNight == Long.MIN_VALUE) {
			lastNight = nightIndex;
			nights++;
			budget = Math.min(budget + config.networkBlocksPerNight, Math.max(config.networkMaxBudget, config.networkBlocksPerNight));
			return;
		}
		long owed = nightIndex - lastNight;
		if (owed <= 0) {
			return;
		}
		int credited = (int) Math.min(owed, Math.max(1, config.networkMaxCatchUpNights));
		lastNight = nightIndex;
		nights += credited;
		budget = Math.min(budget + credited * config.networkBlocksPerNight, Math.max(config.networkMaxBudget, config.networkBlocksPerNight));
	}

	/** Min and max corner of every cell, or empty. */
	public Optional<BlockPos[]> bounds() {
		if (cells.isEmpty()) {
			return Optional.empty();
		}
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (long packed : cells) {
			int x = BlockPos.getX(packed), y = BlockPos.getY(packed), z = BlockPos.getZ(packed);
			minX = Math.min(minX, x);
			minY = Math.min(minY, y);
			minZ = Math.min(minZ, z);
			maxX = Math.max(maxX, x);
			maxY = Math.max(maxY, y);
			maxZ = Math.max(maxZ, z);
		}
		return Optional.of(new BlockPos[] {new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ)});
	}

	// --- NBT ---

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("dimension", dimension.identifier().toString());
		tag.putLong("base", base.asLong());
		tag.putInt("depth", depth);
		tag.putLongArray("anchors", anchors.stream().mapToLong(BlockPos::asLong).toArray());
		tag.putLongArray("shaft", shaftAnchors.toLongArray());
		ListTag headList = new ListTag();
		for (Head head : heads) {
			CompoundTag h = new CompoundTag();
			h.putLong("pos", head.pos.asLong());
			h.putInt("dir", head.dir.get3DDataValue());
			h.putInt("run", head.run);
			if (head.target != null) {
				h.putLong("target", head.target.asLong());
			}
			h.putBoolean("toShaft", head.toShaft);
			headList.add(h);
		}
		tag.put("heads", headList);
		tag.putLongArray("targets", targets.stream().mapToLong(BlockPos::asLong).toArray());
		putPos(tag, "bedHead", bedHead);
		putPos(tag, "bedFoot", bedFoot);
		putPos(tag, "shaftFoot", shaftFoot);
		tag.putBoolean("shaftDone", shaftDone);
		tag.putLong("shaftStuckNight", shaftStuckNight);
		putPos(tag, "alcove", alcove);
		putPos(tag, "chest", chest);
		tag.putLong("chestTriedNight", chestTriedNight);
		tag.putLong("lastNight", lastNight);
		tag.putInt("nights", nights);
		tag.putInt("budget", budget);
		tag.putInt("underBaseSite", underBaseSite);
		tag.putInt("shaftSite", shaftSite);
		tag.putInt("stacksMoved", stacksMoved);
		tag.putInt("stacksLedgered", stacksLedgered);
		return tag;
	}

	static Network load(CompoundTag tag) {
		ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(tag.getStringOr("dimension", "minecraft:overworld")));
		Network net = new Network(dimension, BlockPos.of(tag.getLongOr("base", 0L)));
		net.depth = tag.getIntOr("depth", 0);
		LongOpenHashSet shaft = new LongOpenHashSet(tag.getLongArray("shaft").orElse(new long[0]));
		for (long packed : tag.getLongArray("anchors").orElse(new long[0])) {
			net.addAnchor(BlockPos.of(packed), shaft.contains(packed));
		}
		ListTag headList = tag.getListOrEmpty("heads");
		for (int i = 0; i < headList.size(); i++) {
			CompoundTag h = headList.getCompoundOrEmpty(i);
			Head head = new Head(BlockPos.of(h.getLongOr("pos", 0L)), Direction.from3DDataValue(h.getIntOr("dir", 2)), h.getIntOr("run", 0));
			head.target = h.getLong("target").map(BlockPos::of).orElse(null);
			head.toShaft = h.getBooleanOr("toShaft", false);
			net.heads.add(head);
		}
		for (long packed : tag.getLongArray("targets").orElse(new long[0])) {
			net.targets.add(BlockPos.of(packed));
		}
		net.bedHead = getPos(tag, "bedHead");
		net.bedFoot = getPos(tag, "bedFoot");
		net.shaftFoot = getPos(tag, "shaftFoot");
		net.shaftDone = tag.getBooleanOr("shaftDone", false);
		net.shaftStuckNight = tag.getLongOr("shaftStuckNight", Long.MIN_VALUE);
		net.alcove = getPos(tag, "alcove");
		net.chest = getPos(tag, "chest");
		net.chestTriedNight = tag.getLongOr("chestTriedNight", Long.MIN_VALUE);
		net.lastNight = tag.getLongOr("lastNight", Long.MIN_VALUE);
		net.nights = tag.getIntOr("nights", 0);
		net.budget = tag.getIntOr("budget", 0);
		net.underBaseSite = tag.getIntOr("underBaseSite", -1);
		net.shaftSite = tag.getIntOr("shaftSite", -1);
		net.stacksMoved = tag.getIntOr("stacksMoved", 0);
		net.stacksLedgered = tag.getIntOr("stacksLedgered", 0);
		return net;
	}

	static void putPos(CompoundTag tag, String key, @Nullable BlockPos pos) {
		if (pos != null) {
			tag.putLong(key, pos.asLong());
		}
	}

	static @Nullable BlockPos getPos(CompoundTag tag, String key) {
		return tag.getLong(key).map(BlockPos::of).orElse(null);
	}
}
