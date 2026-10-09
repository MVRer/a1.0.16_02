package com.forzacode.a1016_02.atmosphere.card;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Tasks;
import com.forzacode.a1016_02.atmosphere.WorldScan;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.phys.Vec3;

/**
 * Chest opens: the open and close sound of a chest within {@code Pacing.chestRadius}, out of sight. The contents are
 * unchanged, or (real version only, sometimes) one stack is gone through {@code TraceService.removeStack}.
 */
public final class ChestOpensCard extends AtmosphereCard {
	public static final String ID = "chest_opens";

	public ChestOpensCard() {
		super(ID, Tier.MINOR, Stage.PROXIMITY, Set.of(Habit.COLLECTOR, Habit.VISITOR), Set.of(CardTag.SOUND, CardTag.ITEM), true);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return !WorldScan.chestsNear(world, player.blockPosition(), ModConfig.pacing().chestRadius).isEmpty();
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerLevel level = ctx.level();
		ServerPlayer player = ctx.player();
		List<BlockPos> candidates = new ArrayList<>();
		for (BlockPos pos : WorldScan.chestsNear(level, player.blockPosition(), ModConfig.pacing().chestRadius)) {
			if (Services.traces().isOutOfView(level, pos)) {
				candidates.add(pos);
			}
		}
		if (candidates.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		// Prefer the player's own chests.
		List<BlockPos> own = candidates.stream().filter(pos -> Services.watch().wasPlacedByPlayer(level, pos)).toList();
		List<BlockPos> pool = own.isEmpty() ? candidates : own;
		BlockPos chest = pool.get(ctx.random().nextInt(pool.size()));
		AtmosphereConfig cfg = cfg();
		RandomSource random = ctx.random();
		Vec3 at = Vec3.atCenterOf(chest);
		SoundCues.playTo(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.CHEST_OPEN), SoundSource.BLOCKS, at, 0.5F,
				random.nextFloat() * 0.1F + 0.9F);
		int closeDelay = cfg.chestCloseMinTicks + random.nextInt(Math.max(1, cfg.chestCloseMaxTicks - cfg.chestCloseMinTicks + 1));
		float closePitch = random.nextFloat() * 0.1F + 0.9F;
		UUID uuid = player.getUUID();
		MinecraftServer server = level.getServer();
		Tasks.later(closeDelay, () -> {
			ServerPlayer online = server.getPlayerList().getPlayer(uuid);
			if (online != null && online.level() == level) {
				SoundCues.playTo(online, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.CHEST_CLOSE), SoundSource.BLOCKS, at, 0.5F, closePitch);
			}
		});
		if (!ctx.fake() && random.nextDouble() < cfg.chestStackChance) {
			takeOneStack(level, chest, random);
		}
		return FireResult.FIRED;
	}

	/** One whole stack from a random non-empty slot. False if the chest is empty or the edit was refused. */
	boolean takeOneStack(ServerLevel level, BlockPos chest, RandomSource random) {
		if (!(level.getBlockEntity(chest) instanceof Container container)) {
			return false;
		}
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (!container.getItem(i).isEmpty()) {
				slots.add(i);
			}
		}
		if (slots.isEmpty()) {
			return false;
		}
		int slot = slots.get(random.nextInt(slots.size()));
		return Services.traces().removeStack(level, chest, slot, container.getItem(slot).getCount(), cause());
	}
}
