package com.forzacode.a1016_02.atmosphere.card;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Tasks;
import com.forzacode.a1016_02.atmosphere.WorldScan;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlayerAction;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Chest opens: the open and close sound of a chest within {@code Pacing.chestRadius} of the player, out of
 * everyone's sight. Everyone in hearing range hears it. The contents are unchanged, or (real version only,
 * sometimes) one stack is gone through {@code TraceService.removeStack}. Which chest (D-076): the container the player
 * opened most recently, if it is one of those chests (in range, out of sight); otherwise one of the player's own chests
 * there, else any of them.
 */
public final class ChestOpensCard extends AtmosphereCard {
	public static final String ID = "chest_opens";

	public ChestOpensCard() {
		// From Traces (D-070; v1.1: Proximity).
		super(ID, Tier.MINOR, Stage.TRACES, Set.of(Habit.COLLECTOR, Habit.VISITOR), Set.of(CardTag.SOUND, CardTag.ITEM), true);
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
			if (outOfView.test(level, new AABB(pos))) {
				candidates.add(pos);
			}
		}
		if (candidates.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		// D-076: the container the player opened last, if it is one of these (a chest in range, out of sight).
		BlockPos chest = lastContainer(player, level).filter(candidates::contains).orElse(null);
		if (chest == null) {
			// Otherwise as before: the player's own chests first.
			List<BlockPos> own = candidates.stream().filter(pos -> watch.get().wasPlacedByPlayer(level, pos)).toList();
			List<BlockPos> pool = own.isEmpty() ? candidates : own;
			chest = pool.get(ctx.random().nextInt(pool.size()));
		}
		AtmosphereConfig cfg = cfg();
		RandomSource random = ctx.random();
		Vec3 at = Vec3.atCenterOf(chest);
		// The chest is in the world: everyone in hearing range hears it open and close (D-060), whoever it fired for.
		SoundOut out = sounds;
		out.play(level, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.CHEST_OPEN), SoundSource.BLOCKS, at, 0.5F, random.nextFloat() * 0.1F + 0.9F);
		int closeDelay = cfg.chestCloseMinTicks + random.nextInt(Math.max(1, cfg.chestCloseMaxTicks - cfg.chestCloseMinTicks + 1));
		float closePitch = random.nextFloat() * 0.1F + 0.9F;
		Tasks.later(closeDelay, () -> out.play(level, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.CHEST_CLOSE), SoundSource.BLOCKS, at, 0.5F, closePitch));
		if (!ctx.fake() && random.nextDouble() < cfg.chestStackChance) {
			takeOneStack(level, chest, random);
		}
		return FireResult.FIRED;
	}

	/**
	 * The container the player opened most recently ({@code PlayerWatch.recentActions}, D-076), if it was in this level:
	 * the clicked half of a double chest. Empty if they opened none this session, or the last one was elsewhere.
	 */
	Optional<BlockPos> lastContainer(ServerPlayer player, ServerLevel level) {
		for (PlayerAction action : watch.get().recentActions(player)) {
			if (action instanceof PlayerAction.ContainerOpen open) {
				return open.dimension().equals(level.dimension()) ? Optional.of(open.pos()) : Optional.empty();
			}
		}
		return Optional.empty();
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
