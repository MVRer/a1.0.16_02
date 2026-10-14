package com.forzacode.a1016_02.lore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

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
 * close for a moment, holding F28's map, and having F12's disc. Any participant's read counts (D-058), never a
 * spectator's. Each calls {@link com.forzacode.a1016_02.core.FragmentService#markRead}, which records the fragment
 * once and fires {@code FRAGMENT_READ} for the reader who read it first: two players reading it in one tick read it
 * once ("8+ fragments read" counts fragments, not readers). Look time is kept per player by UUID and forgotten when
 * they stop taking part.
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
					// Opened books show their current text (the list's lines, F10's last line) before vanilla opens them.
					LiveBooks.refreshForReading(stack, serverPlayer);
					markRead(serverPlayer, FragmentItems.fragmentId(stack));
				}
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer
					&& level.getBlockEntity(hit.getBlockPos()) instanceof LecternBlockEntity lectern && lectern.hasBook()) {
				if (LiveBooks.refreshForReading(lectern.getBook(), serverPlayer)) {
					lectern.setChanged();
				}
				markRead(serverPlayer, FragmentItems.fragmentId(lectern.getBook()));
			}
			return InteractionResult.PASS;
		});
	}

	/** A read by a participant (never a spectator); the fragment service records it once. */
	private static void markRead(ServerPlayer player, java.util.Optional<String> id) {
		if (id.isPresent() && Services.watch().isParticipant(player)) {
			Services.fragments().markRead(player, id.get());
		}
	}

	void clear() {
		dwell.clear();
	}

	/** A player stopped taking part (PARTICIPATION): their look time goes. */
	void forget(UUID player) {
		dwell.remove(player);
	}

	void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (now % LOOK_CHECK_TICKS != 0) {
			return;
		}
		// A participant on the death screen still takes part, but reads nothing until they are back.
		List<ServerPlayer> readers = Services.watch().participants(server).stream().filter(ServerPlayer::isAlive).toList();
		look(readers, HerobrineState.get(server), LoreData.get(server), now % INVENTORY_CHECK_TICKS == 0, LoreConfig.get(),
				Services.fragments()::markRead);
	}

	/**
	 * One look check for these participants (live, or a test's own): each one's look time at each unread target
	 * grows, and whoever has looked long enough reads it through {@code reader}. Two players finishing the same
	 * target in one check both call {@code reader}, which records the fragment once ({@link FragmentServiceImpl#markRead}).
	 */
	void look(List<ServerPlayer> players, HerobrineState state, LoreData data, boolean inventories, LoreConfig config,
			BiConsumer<ServerPlayer, String> reader) {
		Map<GlobalPos, String> targets = new HashMap<>();
		data.readTargets().forEach((id, list) -> {
			if (!state.fragmentsRead().contains(id)) {
				list.forEach(pos -> targets.put(pos, id));
			}
		});
		long dwellTicks = Math.max(LOOK_CHECK_TICKS, ModConfig.realTicks(config.readDwellSeconds));
		for (ServerPlayer player : players) {
			if (inventories) {
				checkInventory(player, state.fragmentsRead(), reader);
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
					reader.accept(player, target.getValue());
				}
			}
		}
	}

	/** F12's disc counts as read once carried; F28's map once held in a hand. */
	private static void checkInventory(ServerPlayer player, Set<String> read, BiConsumer<ServerPlayer, String> reader) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || stack.is(Items.WRITTEN_BOOK) || stack.is(Items.FILLED_MAP)) {
				continue;
			}
			FragmentItems.fragmentId(stack).filter(id -> !read.contains(id)).filter(id -> !id.equals("F11"))
					.ifPresent(id -> reader.accept(player, id));
		}
		for (ItemStack held : List.of(player.getMainHandItem(), player.getOffhandItem())) {
			if (held.is(Items.FILLED_MAP)) {
				FragmentItems.fragmentId(held).filter(id -> !read.contains(id)).ifPresent(id -> reader.accept(player, id));
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
