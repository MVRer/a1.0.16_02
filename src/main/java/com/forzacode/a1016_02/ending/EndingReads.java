package com.forzacode.a1016_02.ending;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.lore.FragmentItems;
import com.forzacode.a1016_02.lore.LoreConfig;
import com.forzacode.a1016_02.lore.LoreData;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Every read of a fragment by the subject, not only the first (lore's {@code FRAGMENT_READ} fires once per
 * fragment): opening a fragment book or map, a fragment book on a lectern, holding a fragment map, and looking at a
 * fragment's sign (lore's read targets) up close for a moment, with lore's own distance, cone and dwell. Each one
 * moves {@link EndingState#lastReadAt}, which Ending A's "no more reading" reads. Only observes; changes nothing.
 */
public final class EndingReads {
	private static final int LOOK_CHECK_TICKS = 10;

	/** Ticks the subject has been looking at each read target. */
	private static final Map<GlobalPos, Integer> DWELL = new HashMap<>();

	private EndingReads() {
	}

	static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
				read(serverPlayer, player.getItemInHand(hand));
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer
					&& level.getBlockEntity(hit.getBlockPos()) instanceof LecternBlockEntity lectern && lectern.hasBook()) {
				read(serverPlayer, lectern.getBook());
			}
			return InteractionResult.PASS;
		});
	}

	static void clear() {
		DWELL.clear();
	}

	/** Opening this stack: a fragment book or map is read. */
	static void read(ServerPlayer player, ItemStack stack) {
		Optional<String> id = readable(stack);
		if (id.isPresent() && Services.watch().isSubject(player)) {
			noteRead(EndingState.get(player.level().getServer()), id.get(), EndingAbcInit.now(player.level().getServer()));
		}
	}

	/** The fragment id of a stack that is read by opening or holding it (a book or a map). */
	static Optional<String> readable(ItemStack stack) {
		if (!stack.is(Items.WRITTEN_BOOK) && !stack.is(Items.WRITABLE_BOOK) && !stack.is(Items.FILLED_MAP)) {
			return Optional.empty();
		}
		return FragmentItems.fragmentId(stack);
	}

	/** A read: A's quiet clock starts again. */
	static void noteRead(EndingState data, String id, long now) {
		boolean fresh = data.lastReadAt() == EndingState.NEVER || now - data.lastReadAt() >= 1000;
		data.setLastReadAt(now);
		if (fresh) {
			data.log("read " + id);
		}
	}

	/** Every server tick: the subject holding a fragment map, or looking at a fragment sign long enough. */
	static void tick(MinecraftServer server) {
		if (server.getTickCount() % LOOK_CHECK_TICKS != 0) {
			return;
		}
		ServerPlayer player = Services.watch().subject(server).orElse(null);
		if (player == null || !player.isAlive() || player.isSpectator()) {
			DWELL.clear();
			return;
		}
		EndingState data = EndingState.get(server);
		long now = EndingAbcInit.now(server);
		for (ItemStack held : List.of(player.getMainHandItem(), player.getOffhandItem())) {
			if (held.is(Items.FILLED_MAP)) {
				FragmentItems.fragmentId(held).ifPresent(id -> noteRead(data, id, now));
			}
		}
		LoreConfig config = LoreConfig.get();
		long dwellTicks = Math.max(LOOK_CHECK_TICKS, ModConfig.realTicks(config.readDwellSeconds));
		Map<GlobalPos, String> targets = new HashMap<>();
		LoreData.get(server).readTargets().forEach((id, list) -> list.forEach(pos -> targets.put(pos, id)));
		DWELL.keySet().retainAll(targets.keySet());
		for (Map.Entry<GlobalPos, String> target : targets.entrySet()) {
			GlobalPos pos = target.getKey();
			if (!pos.dimension().equals(player.level().dimension()) || !looksAt(player, pos.pos(), config.readDistance, config.readConeDegrees)) {
				DWELL.remove(pos);
				continue;
			}
			if (DWELL.merge(pos, LOOK_CHECK_TICKS, Integer::sum) >= dwellTicks) {
				DWELL.remove(pos);
				noteRead(data, target.getValue(), now);
			}
		}
	}

	/** Close, facing it within the cone, with a clear line to it; the block must still be there. */
	static boolean looksAt(ServerPlayer player, BlockPos pos, double distanceMax, double coneDegrees) {
		ServerLevel level = player.level();
		if (!level.isLoaded(pos) || level.getBlockState(pos).isAir()) {
			return false;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 center = Vec3.atCenterOf(pos);
		Vec3 to = center.subtract(eye);
		double distance = to.length();
		if (distance > distanceMax) {
			return false;
		}
		if (distance > 0.5 && to.normalize().dot(player.getLookAngle()) < Math.cos(Math.toRadians(coneDegrees / 2.0))) {
			return false;
		}
		BlockHitResult hit = level.clip(new ClipContext(eye, center, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}
}
