package com.forzacode.a1016_02.ending;

import java.util.Set;
import java.util.TreeSet;

import com.forzacode.a1016_02.lore.FragmentItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Where fragment items are (lore marks them with a custom data marker): in a stack, nested in shulker boxes and
 * bundles, in a player's inventory and ender chest, or in containers and lecterns around a place. Read-only.
 */
public final class FragmentHoldings {
	/** How deep nested containers are opened (a shulker box in a bundle in a shulker box...). */
	static final int MAX_DEPTH = 4;

	private FragmentHoldings() {
	}

	/** Fragment ids in this stack and everything nested in it. */
	public static void collect(ItemStack stack, Set<String> into) {
		collect(stack, into, 0);
	}

	private static void collect(ItemStack stack, Set<String> into, int depth) {
		if (stack.isEmpty()) {
			return;
		}
		FragmentItems.fragmentId(stack).ifPresent(into::add);
		if (depth >= MAX_DEPTH) {
			return;
		}
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents != null) {
			contents.nonEmptyItemCopyStream().forEach(inner -> collect(inner, into, depth + 1));
		}
		BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
		if (bundle != null) {
			bundle.itemCopies().forEach(inner -> collect(inner, into, depth + 1));
		}
	}

	/** Fragment ids in a container, nested ones included. */
	public static void collect(Container container, Set<String> into) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			collect(container.getItem(slot), into);
		}
	}

	/** Fragment ids the player carries: inventory (with nested shulker boxes and bundles) and ender chest. */
	public static Set<String> carried(ServerPlayer player) {
		Set<String> found = new TreeSet<>();
		collect(player.getInventory(), found);
		collect(player.getEnderChestInventory(), found);
		collect(player.containerMenu.getCarried(), found);
		return found;
	}

	/**
	 * Fragment ids in containers and lecterns within {@code radius} (cube) of {@code center}, nested ones included.
	 * Only loaded chunks are read.
	 */
	public static Set<String> stored(ServerLevel level, BlockPos center, int radius) {
		Set<String> found = new TreeSet<>();
		ChunkPos min = ChunkPos.containing(center.offset(-radius, 0, -radius));
		ChunkPos max = ChunkPos.containing(center.offset(radius, 0, radius));
		for (int cx = min.x(); cx <= max.x(); cx++) {
			for (int cz = min.z(); cz <= max.z(); cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				LevelChunk chunk = level.getChunk(cx, cz);
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					BlockPos pos = be.getBlockPos();
					if (Math.abs(pos.getX() - center.getX()) > radius || Math.abs(pos.getY() - center.getY()) > radius
							|| Math.abs(pos.getZ() - center.getZ()) > radius) {
						continue;
					}
					if (be instanceof LecternBlockEntity lectern) {
						collect(lectern.getBook(), found);
					} else if (be instanceof Container container) {
						collect(container, found);
					}
				}
			}
		}
		return found;
	}
}
