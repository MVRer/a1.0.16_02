package com.forzacode.a1016_02.dig;

import java.util.Optional;

import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import org.jspecify.annotations.Nullable;

/**
 * "Under you", always running from the first nights in a base in Traces (not a deck card). Each in-game night
 * credits {@link DigConfig#networkBlocksPerNight} steps to the network under {@code PlayerWatch.base}; the steps
 * are carved while the base is loaded and the edits are out of view, a few at a time.
 */
public final class UnderYou {
	/** Steps carved per check, so a long catch-up never lands in one tick. */
	static final int STEPS_PER_CHECK = 4;

	private UnderYou() {
	}

	/** The night index: night N starts at dusk of day N (sleeping or a timewarp still counts the night). */
	public static long nightIndex(MinecraftServer server) {
		long time = Math.floorMod(server.overworld().getOverworldClockTime(), GameClock.TICKS_PER_DAY);
		long day = GameClock.day(server);
		return time >= 13000L ? day : day - 1;
	}

	/** Background tick (every few seconds) with the subject online. */
	static void tick(MinecraftServer server, ServerPlayer player, DigData data, RandomSource random) {
		if (!HerobrineState.get(server).stage().atLeast(Stage.TRACES)) {
			return;
		}
		Optional<GlobalPos> base = Services.watch().base(player);
		if (base.isEmpty()) {
			return;
		}
		ServerLevel level = server.getLevel(base.get().dimension());
		if (level == null) {
			return;
		}
		long night = nightIndex(server);
		DigConfig config = DigConfig.get();
		Network net = active(data, base.get(), night, config, false);
		if (net == null || !level.isLoaded(net.base)) {
			return;
		}
		int before = net.nights;
		net.creditNights(night, config);
		boolean changed = net.nights != before | NetworkGrower.refreshBed(level, net, base.get().pos());
		NetworkGrower.Ctx ctx = ctx(level, net, data, player.level() == level ? player.blockPosition() : null, night, random);
		if (net.nights != before && !net.anchors.isEmpty()) {
			NetworkGrower.refreshTargets(ctx);
		}
		// Also with no budget left: a chest may be waiting for its chunk to load.
		growNow(ctx, data, STEPS_PER_CHECK, false);
		if (changed) {
			data.setDirty();
		}
	}

	/**
	 * The network for this base: the current one if the base has not moved far, else a new one once the base has
	 * stood for {@link DigConfig#networkStartAfterNights} nights (or right away with {@code force}).
	 */
	static @Nullable Network active(DigData data, GlobalPos base, long night, DigConfig config, boolean force) {
		Optional<Network> current = data.network();
		if (current.isPresent() && near(current.get().dimension, current.get().base, base, config.networkBaseMoveDistance)) {
			return current.get();
		}
		if (data.baseSeen == null || !near(data.baseSeen.dimension(), data.baseSeen.pos(), base, config.networkBaseMoveDistance)) {
			data.baseSeen = base;
			data.baseSeenNight = night;
			data.setDirty();
		}
		if (!force && night - data.baseSeenNight < config.networkStartAfterNights) {
			return null;
		}
		Network net = new Network(base.dimension(), base.pos());
		data.addNetwork(net);
		return net;
	}

	static NetworkGrower.Ctx ctx(ServerLevel level, Network net, DigData data, @Nullable BlockPos playerPos, long night, RandomSource random) {
		return new NetworkGrower.Ctx(level, net, data.explored(net.dimension), DigConfig.get(), ModConfig.pacing().digBelow, random, Services.traces(),
				night, playerPos);
	}

	/** Spends the budget (founding at most once a night unless forced), fetches the chest and records the site. */
	static int growNow(NetworkGrower.Ctx ctx, DigData data, int maxSteps, boolean force) {
		Network net = ctx.net();
		if (net.anchors.isEmpty() && net.foundTriedNight == ctx.night() && !force) {
			return 0;
		}
		int carved = NetworkGrower.grow(ctx, maxSteps);
		if (net.anchors.isEmpty()) {
			net.foundTriedNight = ctx.night();
		}
		long now = ctx.level().getServer().getTickCount();
		if (net.alcove != null && net.chest == null && (net.chestTriedNight != ctx.night() || force || now >= net.chestRetryTick)) {
			net.chestTriedNight = ctx.night();
			if (NetworkChest.tryFetch(ctx)) {
				data.setDirty();
			}
		}
		if (net.underBaseSite < 0 && !net.anchors.isEmpty() && net.nights >= ctx.config().networkChestAfterNights + 3) {
			NetworkGrower.recordUnderBase(ctx, net.anchors.getFirst());
			data.setDirty();
		}
		if (carved > 0) {
			data.changed();
		}
		return carved;
	}

	private static boolean near(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos pos, GlobalPos other, int distance) {
		return dimension.equals(other.dimension()) && pos.distSqr(other.pos()) <= (double) distance * distance;
	}
}
