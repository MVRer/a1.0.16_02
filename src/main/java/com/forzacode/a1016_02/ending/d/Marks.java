package com.forzacode.a1016_02.ending.d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Where the player's things came from. Two kinds of item are followed: poplar wood cut in the untouched grove (the
 * logs, and the planks made from them) and the first block taken out of the cairn. The item carries a mark (custom
 * data); a placed block cannot, so where a marked block is placed is remembered in {@link EndingDState#tracked()},
 * and breaking it marks its drop again. Nothing here changes the world: it only follows what the player does.
 */
public final class Marks {
	/** The custom data key. */
	public static final String KEY = "a1016_02:ending_d";
	public static final String GROVE_WOOD = "grove_wood";
	public static final String FIRST_BLOCK = "first_block";

	/** A drop that should get a mark when it appears (this tick or the next). */
	private record Pending(GlobalPos pos, Item item, String mark, long until) {
	}

	/** A use of a marked block item: where the block may have been placed (checked next tick). */
	private record Placing(GlobalPos[] candidates, Item item, String mark, long at) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();
	private static final List<Placing> PLACING = new ArrayList<>();
	private static final long DROP_WINDOW = 4;

	private Marks() {
	}

	// --- the mark on a stack ---

	public static Optional<String> mark(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return Optional.empty();
		}
		CompoundTag tag = data.copyTag();
		return tag.getString(KEY);
	}

	public static boolean has(ItemStack stack, String mark) {
		return mark(stack).filter(mark::equals).isPresent();
	}

	public static void set(ItemStack stack, String mark) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(KEY, mark));
	}

	/** Any stack with this mark in the player's inventory (or on the cursor). */
	public static boolean carries(ServerPlayer player, String mark) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (has(inventory.getItem(i), mark)) {
				return true;
			}
		}
		return has(player.containerMenu.getCarried(), mark);
	}

	// --- drops ---

	/** A block of this item broken at {@code pos}: its drop gets {@code mark}. */
	static void expectDrop(ServerLevel level, BlockPos pos, Item item, String mark) {
		if (item == Items.AIR) {
			return;
		}
		PENDING.add(new Pending(GlobalPos.of(level.dimension(), pos.immutable()), item, mark, level.getServer().getTickCount() + DROP_WINDOW));
	}

	/** An item entity was added to a level: mark it if it is an expected drop. */
	static void onItemAdded(ServerLevel level, ItemEntity entity) {
		if (PENDING.isEmpty()) {
			return;
		}
		ItemStack stack = entity.getItem();
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
			Pending p = it.next();
			if (p.pos().dimension().equals(level.dimension()) && stack.is(p.item())
					&& entity.position().distanceToSqr(Vec3.atCenterOf(p.pos().pos())) <= 2.25) {
				set(stack, p.mark());
				entity.setItem(stack);
				it.remove();
				return;
			}
		}
	}

	// --- placing ---

	/** A player used a block item on a block: if it is marked, remember where it may land. Server side. */
	static void onUseBlock(ServerPlayer player, ItemStack held, BlockHitResult hit) {
		Optional<String> mark = mark(held);
		if (mark.isEmpty() || !(held.getItem() instanceof BlockItem)) {
			return;
		}
		ServerLevel level = player.level();
		BlockPos clicked = hit.getBlockPos();
		GlobalPos[] candidates = {GlobalPos.of(level.dimension(), clicked.immutable()),
				GlobalPos.of(level.dimension(), clicked.relative(hit.getDirection()).immutable())};
		PLACING.add(new Placing(candidates, held.getItem(), mark.get(), level.getServer().getTickCount()));
	}

	/** Every tick: drops expire, and uses of marked items become tracked blocks once they are placed. */
	static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		PENDING.removeIf(p -> p.until() < now);
		if (PLACING.isEmpty()) {
			return;
		}
		EndingDState state = EndingDState.get(server);
		for (Iterator<Placing> it = PLACING.iterator(); it.hasNext();) {
			Placing p = it.next();
			if (p.at() >= now) {
				continue;
			}
			it.remove();
			for (GlobalPos candidate : p.candidates()) {
				ServerLevel level = server.getLevel(candidate.dimension());
				if (level == null || state.trackedAt(candidate).isPresent()) {
					continue;
				}
				BlockState placed = level.getBlockState(candidate.pos());
				if (placed.getBlock().asItem() == p.item() && Services.watch().wasPlacedByPlayer(level, candidate.pos())) {
					state.track(candidate, p.mark());
					break;
				}
			}
		}
	}

	/**
	 * A player is breaking a block. A tracked block marks its drop again (and is no longer tracked); a poplar log in
	 * the untouched grove marks its drop as grove wood.
	 */
	static void onBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		EndingDState data = EndingDState.get(level.getServer());
		GlobalPos at = GlobalPos.of(level.dimension(), pos.immutable());
		Optional<String> tracked = data.trackedAt(at);
		if (tracked.isPresent()) {
			data.untrack(at);
			expectDrop(level, pos, state.getBlock().asItem(), tracked.get());
		}
	}

	/** Copies the grove mark onto a crafting result made from grove wood (called by the crafting mixin). */
	public static ItemStack onCraft(Container grid, ItemStack result) {
		if (result.isEmpty() || !result.is(Items.POPLAR_PLANKS) || has(result, GROVE_WOOD)) {
			return result;
		}
		for (int i = 0; i < grid.getContainerSize(); i++) {
			if (has(grid.getItem(i), GROVE_WOOD)) {
				ItemStack marked = result.copy();
				set(marked, GROVE_WOOD);
				return marked;
			}
		}
		return result;
	}

	/** True if a marked drop is waiting (tests). */
	static boolean expecting(@Nullable Item item) {
		return PENDING.stream().anyMatch(p -> item == null || p.item() == item);
	}

	static void clear() {
		PENDING.clear();
		PLACING.clear();
	}
}
