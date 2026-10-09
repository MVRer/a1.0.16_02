package com.forzacode.a1016_02.lore;

import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Block entity contents of what others left, as the {@code saveCustomOnly} data {@code TraceService.leave} takes:
 * a chest's or furnace's items, a sign's text (and wax), a jukebox's disc. Built without a level, so nothing in the
 * world is touched until the trace edit places the block with it.
 */
final class BlockEntityData {
	private BlockEntityData() {
	}

	/** {@code Items} for a container of {@code size} slots, these stacks in the first slots. */
	static CompoundTag items(HolderLookup.Provider registries, int size, List<ItemStack> contents) {
		NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
		for (int slot = 0; slot < contents.size() && slot < size; slot++) {
			items.set(slot, contents.get(slot).copy());
		}
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		ContainerHelper.saveAllItems(output, items);
		return output.buildResult();
	}

	/** A sign's front text (the back blank), waxed or not. */
	static CompoundTag sign(HolderLookup.Provider registries, SignText front, boolean waxed) {
		CompoundTag tag = new CompoundTag();
		var ops = registries.createSerializationContext(NbtOps.INSTANCE);
		tag.put("front_text", SignText.CODEC.encodeStart(ops, front).getOrThrow());
		tag.put("back_text", SignText.CODEC.encodeStart(ops, SignText.EMPTY).getOrThrow());
		tag.putBoolean("is_waxed", waxed);
		return tag;
	}

	/**
	 * A jukebox holding {@code disc}, not playing: without {@code ticks_since_song_started} the disc is set without
	 * starting the song (no sound, notes or events).
	 */
	static CompoundTag jukebox(HolderLookup.Provider registries, ItemStack disc) {
		CompoundTag tag = new CompoundTag();
		tag.put("RecordItem", ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), disc).getOrThrow());
		return tag;
	}
}
