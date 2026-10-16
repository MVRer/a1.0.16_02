package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One player's tunnel ladder (D-072), kept in {@link DigData} by UUID: the highest rung they heard, the play tick
 * (their own clock) of their last ladder event, of their last rung 2 and of their last rung 3, the in-game day of
 * their last rung 3, and the tunnel runs already used (a run starting within {@code ladderUsedRunRadius} of one of
 * them never fires again). {@link Long#MIN_VALUE} means never.
 */
final class LadderMemory {
	/** The newest used runs kept per player; older ones are dropped. */
	static final int USED_RUNS_KEPT = 64;

	int rung;
	long lastEvent = Long.MIN_VALUE;
	long lastComing = Long.MIN_VALUE;
	long lastGlimpse = Long.MIN_VALUE;
	long glimpseDay = Long.MIN_VALUE;
	/** Where each used run starts (its far end behind the player), oldest first. */
	final List<GlobalPos> usedRuns = new ArrayList<>();

	/** Remembers a used run (its start), dropping the oldest past {@link #USED_RUNS_KEPT}. */
	void use(ResourceKey<Level> dimension, BlockPos start) {
		usedRuns.add(GlobalPos.of(dimension, start.immutable()));
		while (usedRuns.size() > USED_RUNS_KEPT) {
			usedRuns.removeFirst();
		}
	}

	/** True if a used run in this dimension starts within {@code radius} blocks of {@code start}. */
	boolean used(ResourceKey<Level> dimension, BlockPos start, int radius) {
		for (GlobalPos used : usedRuns) {
			if (used.dimension().equals(dimension) && used.pos().distSqr(start) <= (double) radius * radius) {
				return true;
			}
		}
		return false;
	}

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("rung", rung);
		tag.putLong("lastEvent", lastEvent);
		tag.putLong("lastComing", lastComing);
		tag.putLong("lastGlimpse", lastGlimpse);
		tag.putLong("glimpseDay", glimpseDay);
		ListTag used = new ListTag();
		for (GlobalPos pos : usedRuns) {
			CompoundTag u = new CompoundTag();
			u.putString("dimension", pos.dimension().identifier().toString());
			u.putLong("pos", pos.pos().asLong());
			used.add(u);
		}
		tag.put("usedRuns", used);
		return tag;
	}

	static LadderMemory load(CompoundTag tag) {
		LadderMemory memory = new LadderMemory();
		memory.rung = tag.getIntOr("rung", 0);
		memory.lastEvent = tag.getLongOr("lastEvent", Long.MIN_VALUE);
		memory.lastComing = tag.getLongOr("lastComing", Long.MIN_VALUE);
		memory.lastGlimpse = tag.getLongOr("lastGlimpse", Long.MIN_VALUE);
		memory.glimpseDay = tag.getLongOr("glimpseDay", Long.MIN_VALUE);
		ListTag used = tag.getListOrEmpty("usedRuns");
		for (int i = 0; i < used.size(); i++) {
			CompoundTag u = used.getCompoundOrEmpty(i);
			memory.usedRuns.add(GlobalPos.of(DigData.dimension(u.getStringOr("dimension", "minecraft:overworld")), BlockPos.of(u.getLongOr("pos", 0L))));
		}
		return memory;
	}
}
