package com.forzacode.a1016_02.lore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Detects reads: opening a fragment book (or one on a lectern), looking at a fragment sign (or F13's cairn) up
 * close for a moment, holding F28's map, and having F12's disc. Each calls {@link
 * com.forzacode.a1016_02.core.FragmentService#markRead}, which fires {@code FRAGMENT_READ} the first time.
 */
final class ReadWatcher {
	private static final int LOOK_CHECK_TICKS = 5;
	private static final int INVENTORY_CHECK_TICKS = 20;

	/** Ticks each player has been looking at each read target. */
	private final Map<UUID, Map<GlobalPos, Integer>> dwell = new HashMap<>();

	void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
				ItemStack stack = player.getItemInHand(hand);
				if (stack.is(Items.WRITTEN_BOOK) || stack.is(Items.FILLED_MAP)) {
					FragmentItems.fragmentId(stack).ifPresent(id -> Services.fragments().markRead(serverPlayer, id));
				}
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer
					&& level.getBlockEntity(hit.getBlockPos()) instanceof LecternBlockEntity lectern && lectern.hasBook()) {
				FragmentItems.fragmentId(lectern.getBook()).ifPresent(id -> Services.fragments().markRead(serverPlayer, id));
			}
			return InteractionResult.PASS;
		});
	}

	void clear() {
		dwell.clear();
	}

	void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (now % LOOK_CHECK_TICKS != 0) {
			return;
		}
		HerobrineState state = HerobrineState.get(server);
		Map<GlobalPos, String> targets = new HashMap<>();
		LoreData.get(server).readTargets().forEach((id, list) -> {
			if (!state.fragmentsRead().contains(id)) {
				list.forEach(pos -> targets.put(pos, id));
			}
		});
		LoreConfig config = LoreConfig.get();
		long dwellTicks = Math.max(LOOK_CHECK_TICKS, ModConfig.realTicks(config.readDwellSeconds));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (now % INVENTORY_CHECK_TICKS == 0) {
				checkInventory(player, state.fragmentsRead());
			}
			Map<GlobalPos, Integer> mine = dwell.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
			mine.keySet().retainAll(targets.keySet());
			for (Map.Entry<GlobalPos, String> target : targets.entrySet()) {
				GlobalPos pos = target.getKey();
				if (!pos.dimension().equals(player.level().dimension()) || !looksAt(player, pos.pos(), config)) {
					mine.remove(pos);
					continue;
				}
				int looked = mine.merge(pos, LOOK_CHECK_TICKS, Integer::sum);
				if (looked >= dwellTicks) {
					mine.remove(pos);
					Services.fragments().markRead(player, target.getValue());
				}
			}
		}
	}

	/** F12's disc counts as read once carried; F28's map once held in a hand. */
	private static void checkInventory(ServerPlayer player, Set<String> read) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || stack.is(Items.WRITTEN_BOOK) || stack.is(Items.FILLED_MAP)) {
				continue;
			}
			FragmentItems.fragmentId(stack).filter(id -> !read.contains(id)).filter(id -> !id.equals("F11"))
					.ifPresent(id -> Services.fragments().markRead(player, id));
		}
		for (ItemStack held : List.of(player.getMainHandItem(), player.getOffhandItem())) {
			if (held.is(Items.FILLED_MAP)) {
				FragmentItems.fragmentId(held).filter(id -> !read.contains(id)).ifPresent(id -> Services.fragments().markRead(player, id));
			}
		}
	}

	/** Close, facing it within the cone, with a clear line to it; the block must still be there. */
	static boolean looksAt(ServerPlayer player, BlockPos pos, LoreConfig config) {
		ServerLevel level = player.level();
		if (!level.isLoaded(pos) || level.getBlockState(pos).isAir()) {
			return false;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 center = Vec3.atCenterOf(pos);
		Vec3 to = center.subtract(eye);
		double distance = to.length();
		if (distance > config.readDistance) {
			return false;
		}
		if (distance > 0.5 && to.normalize().dot(player.getLookAngle()) < Math.cos(Math.toRadians(config.readConeDegrees / 2.0))) {
			return false;
		}
		BlockHitResult hit = level.clip(new ClipContext(eye, center, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}
}
