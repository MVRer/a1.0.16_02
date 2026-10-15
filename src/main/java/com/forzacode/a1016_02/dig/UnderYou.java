package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
 * "Under you", always running from the first nights in a base in Traces (not a deck card). With friends (D-061) there
 * is one network per distinct base: a base within {@link DigConfig#networkShareDistance} of a network's base shares
 * that network (friends living in one house get one network under it), and a base farther from every network gets
 * its own once its player has been at home there and it has stood for {@link DigConfig#networkStartAfterNights}
 * nights, while fewer than {@link DigConfig#networkMaxPerWorld} networks are still under someone's base (a base
 * everyone moved away from, such as a night's camp bed, no longer counts, so a new base always gets one, as in v1.0).
 * A respawn point a player never came home to never starts one. Each in-game night credits
 * {@link DigConfig#networkBlocksPerNight} steps to a network while one of its players takes part (its own players'
 * nights; nights missed while they were away catch up, at most {@link DigConfig#networkMaxCatchUpNights}), once per
 * network however many of them are online, so a shared base never grows faster. The steps are carved while the base
 * is loaded and out of view of every player, a few at a time. The shaft stops under the bed of the network's founder.
 * Players are looked up fresh each check (D-062.1).
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

	/** Background tick (every few seconds): the network under each participant's base, each network once. */
	static void tick(MinecraftServer server, Bases bases, RandomSource random) {
		HerobrineState state = bases.state();
		if (!state.stage().atLeast(Stage.TRACES)) {
			return;
		}
		DigData data = bases.data();
		data.adoptLegacy(state.players().stream().findFirst().map(HerobrineState.Subject::uuid));
		long night = nightIndex(server);
		DigConfig config = DigConfig.get();
		Set<Network> done = Collections.newSetFromMap(new IdentityHashMap<>());
		for (ServerPlayer player : bases.participants()) {
			bases.remember(player);
			Optional<GlobalPos> base = bases.watch().base(player);
			if (base.isEmpty()) {
				continue;
			}
			ServerLevel level = server.getLevel(base.get().dimension());
			if (level == null) {
				continue;
			}
			see(data, player.getUUID(), base.get(), night, atHome(player, base.get(), config), config);
			Network net = active(data, player.getUUID(), base.get(), night, config, false);
			if (net == null || !level.isLoaded(net.base)) {
				continue;
			}
			// The shaft follows one bed only, its founder's, so two friends' beds never flip it back and forth.
			if (net.founder().map(player.getUUID()::equals).orElse(false) && NetworkGrower.refreshBed(level, net, base.get().pos())) {
				data.setDirty();
			}
			if (!done.add(net)) {
				continue; // a friend's turn already grew it this check
			}
			int before = net.nights;
			net.creditNights(night, config);
			boolean changed = net.nights != before;
			NetworkGrower.Ctx ctx = ctx(level, net, data, positions(bases, level, player), night, random);
			if (net.nights != before && !net.anchors.isEmpty()) {
				NetworkGrower.refreshTargets(ctx);
			}
			// Also with no budget left: a chest may be waiting for its chunk to load.
			growNow(ctx, data, STEPS_PER_CHECK, false);
			if (changed) {
				data.setDirty();
			}
		}
	}

	/** Every player in this level ({@code first} first if they are here): chest sources keep away from all of them. */
	static List<BlockPos> positions(Bases bases, ServerLevel level, @Nullable ServerPlayer first) {
		List<BlockPos> positions = new ArrayList<>();
		if (first != null && first.level() == level) {
			positions.add(first.blockPosition());
		}
		for (ServerPlayer player : level.players()) {
			if (first == null || !player.getUUID().equals(first.getUUID())) {
				positions.add(player.blockPosition());
			}
		}
		return positions;
	}

	/** At home: in the base's dimension, horizontally within {@link DigConfig#homeRadius} of it and not far above or below. */
	static boolean atHome(ServerPlayer player, GlobalPos base, DigConfig config) {
		BlockPos pos = player.blockPosition();
		return player.level().dimension().equals(base.dimension()) && DigTicker.horizontalDistSqr(pos, base.pos()) <= (long) config.homeRadius * config.homeRadius
				&& Math.abs(pos.getY() - base.pos().getY()) <= 12;
	}

	/**
	 * Remembers the base a participant has now: a base new to dig (or moved farther than
	 * {@link DigConfig#networkBaseMoveDistance}) is seen from tonight on; once they are at home there, they played there.
	 */
	static void see(DigData data, UUID player, GlobalPos base, long night, boolean atHome, DigConfig config) {
		DigData.BaseSeen seen = data.baseSeen.get(player);
		if (seen == null || !near(seen.base(), base, config.networkBaseMoveDistance)) {
			data.baseSeen.put(player, new DigData.BaseSeen(base, night, atHome));
			data.setDirty();
		} else if (atHome && !seen.playedThere()) {
			data.baseSeen.put(player, new DigData.BaseSeen(seen.base(), seen.night(), true));
			data.setDirty();
		}
	}

	/**
	 * The network for this player's base: the one whose base is within {@link DigConfig#networkShareDistance} (shared,
	 * the player becomes one of its owners), else a new one once they played at this base and it has stood for
	 * {@link DigConfig#networkStartAfterNights} nights, while fewer than {@link DigConfig#networkMaxPerWorld} networks
	 * are {@linkplain #inUse in use} ({@code force}, debug: right away and past the cap). Null while it waits.
	 */
	static @Nullable Network active(DigData data, UUID player, GlobalPos base, long night, DigConfig config, boolean force) {
		Network shared = data.networkFor(base.dimension(), base.pos(), config.networkShareDistance);
		if (shared != null) {
			if (shared.owners.add(player)) {
				data.setDirty();
			}
			return shared;
		}
		if (!force) {
			DigData.BaseSeen seen = data.baseSeen.get(player);
			if (seen == null || !near(seen.base(), base, config.networkBaseMoveDistance) || !seen.playedThere()
					|| night - seen.night() < config.networkStartAfterNights || inUse(data, config) >= config.networkMaxPerWorld) {
				return null;
			}
		}
		Network net = new Network(base.dimension(), base.pos());
		net.owners.add(player);
		data.addNetwork(net);
		return net;
	}

	/**
	 * How many networks count toward {@link DigConfig#networkMaxPerWorld}: those still under some owner's base, the
	 * base dig last saw that owner with ({@link DigData#baseSeen}, kept while they are away) within
	 * {@link DigConfig#networkShareDistance} of the network's. A network every owner moved away from (a camp bed slept
	 * in for a night) no longer counts; it stays, and is theirs again if one of them comes back. One with no owners yet
	 * (a save from before multiplayer), or an owner dig never saw a base for, counts.
	 */
	static int inUse(DigData data, DigConfig config) {
		int count = 0;
		for (Network net : data.networks) {
			if (inUse(data, net, config)) {
				count++;
			}
		}
		return count;
	}

	static boolean inUse(DigData data, Network net, DigConfig config) {
		if (net.owners.isEmpty()) {
			return true;
		}
		GlobalPos under = GlobalPos.of(net.dimension, net.base);
		for (UUID owner : net.owners) {
			DigData.BaseSeen seen = data.baseSeen.get(owner);
			if (seen == null || near(seen.base(), under, config.networkShareDistance)) {
				return true;
			}
		}
		return false;
	}

	static NetworkGrower.Ctx ctx(ServerLevel level, Network net, DigData data, List<BlockPos> players, long night, RandomSource random) {
		return new NetworkGrower.Ctx(level, net, data.explored(net.dimension), DigConfig.get(), ModConfig.pacing().digBelow, random, Services.traces(),
				night, players);
	}

	/**
	 * Spends the budget (founding at most once a night unless forced), fetches the chest, moves the night's few
	 * ledgered stacks into it and records the site.
	 */
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
		if (net.chest != null && NetworkChest.restoreLedgered(ctx) > 0) {
			data.setDirty();
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

	private static boolean near(GlobalPos a, GlobalPos b, int distance) {
		return a.dimension().equals(b.dimension()) && a.pos().distSqr(b.pos()) <= (double) distance * distance;
	}
}
