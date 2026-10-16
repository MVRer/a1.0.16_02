package com.forzacode.a1016_02.dig.sc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * What dig keeps for the layer (docs/SELF_CREATED.md, 12 and 13), inside {@code dig.dat} under {@code sc}: each
 * player's arm of 12 by UUID (the bed, until when on the day clock, and whether a debug arm skips the shaft), how many
 * refusals the world had, and how many times 13 closed a way back. Written only when there is something to keep; a
 * {@code dig.dat} without it loads with none, and v1.3 skips the key. Mark the {@code DigData} dirty after a change.
 */
public final class DigScMemory {
	/** An arm of 12: the bed it is for, the day ticks it ends at, and whether it skips the shaft (debug). */
	public record RestArm(GlobalPos bed, long untilDayTicks, boolean noShaft) {
	}

	final Map<UUID, RestArm> restArms = new LinkedHashMap<>();
	int restRefusals;
	int wayBacks;

	public Map<UUID, RestArm> restArms() {
		return Collections.unmodifiableMap(restArms);
	}

	public Optional<RestArm> restArm(UUID player) {
		return Optional.ofNullable(restArms.get(player));
	}

	public void setRestArm(UUID player, RestArm arm) {
		restArms.put(player, arm);
	}

	public boolean dropRestArm(UUID player) {
		return restArms.remove(player) != null;
	}

	public int restRefusals() {
		return restRefusals;
	}

	public void countRestRefusal() {
		restRefusals++;
	}

	public int wayBacks() {
		return wayBacks;
	}

	public void countWayBack() {
		wayBacks++;
	}

	/** Debug: forgets the counts and arms. */
	public void reset() {
		restArms.clear();
		restRefusals = 0;
		wayBacks = 0;
	}

	public boolean isEmpty() {
		return restArms.isEmpty() && restRefusals == 0 && wayBacks == 0;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		ListTag arms = new ListTag();
		restArms.forEach((player, arm) -> {
			CompoundTag a = new CompoundTag();
			a.putString("player", player.toString());
			a.putString("dim", arm.bed().dimension().identifier().toString());
			a.putLong("bed", arm.bed().pos().asLong());
			a.putLong("until", arm.untilDayTicks());
			a.putBoolean("noShaft", arm.noShaft());
			arms.add(a);
		});
		tag.put("arms12", arms);
		tag.putInt("refusals12", restRefusals);
		tag.putInt("done13", wayBacks);
		return tag;
	}

	/** Reads what {@link #save} wrote, leniently (an arm that cannot be read is dropped). */
	public void load(CompoundTag tag) {
		restArms.clear();
		ListTag arms = tag.getListOrEmpty("arms12");
		for (int i = 0; i < arms.size(); i++) {
			CompoundTag a = arms.getCompoundOrEmpty(i);
			try {
				UUID player = UUID.fromString(a.getStringOr("player", ""));
				Identifier dim = Identifier.tryParse(a.getStringOr("dim", "minecraft:overworld"));
				if (dim == null || !a.contains("bed")) {
					continue;
				}
				ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dim);
				restArms.put(player, new RestArm(GlobalPos.of(key, BlockPos.of(a.getLongOr("bed", 0L))), a.getLongOr("until", 0L),
						a.getBooleanOr("noShaft", false)));
			} catch (IllegalArgumentException ignored) {
				// a broken uuid: that arm is gone
			}
		}
		restRefusals = Math.max(0, tag.getIntOr("refusals12", 0));
		wayBacks = Math.max(0, tag.getIntOr("done13", 0));
	}
}
