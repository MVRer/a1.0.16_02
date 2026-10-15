package com.forzacode.a1016_02.ending;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * What the group does that the commit rules read (D-058, D-064: whatever an ending asks of "you" is asked of the group
 * that plays, and anyone who takes part counts): tellings and naming, fragment reads, every fragment anyone held, who
 * holds it now and which went into lava or fire, the group's house taken apart, staring into the fog, visits near his
 * traces. Event handlers record into {@link EndingState}; {@link #facts} reads one {@link EndingFacts}. Who counts is the
 * caller's check ({@link EndingApi#takesPart}). Server thread only.
 */
public final class EndingWatch {
	/** The group's own blocks near the home that someone is breaking right now (before the break completes). */
	private static final Set<GlobalPos> BREAKING = new HashSet<>();

	private EndingWatch() {
	}

	static void clear() {
		BREAKING.clear();
	}

	// --- events ---

	/** TELLING (lore), by someone who takes part: a telling, and whether it names him. */
	static void onTelling(boolean namesHim, EndingState data, long now) {
		data.recordTelling(now, namesHim);
	}

	/** FRAGMENT_READ (lore), by someone who takes part: the first read of a fragment (later reads come from {@link EndingReads}). */
	static void onFragmentRead(EndingState data, long now) {
		data.setLastReadAt(now);
	}

	/** An item entity was destroyed by damage. A fragment someone who takes part threw into lava or fire counts for Ending C. */
	public static void onItemDestroyed(ItemEntity entity, ServerLevel level, DamageSource source) {
		if (source.is(DamageTypeTags.IS_FIRE) && !fragmentIds(entity.getItem()).isEmpty()) {
			onItemDestroyed(entity, source, EndingState.get(level.getServer()), EndingApi::takesPart);
		}
	}

	/**
	 * True if this destroyed item counted: fragments (the item itself, or nested in a shulker box or bundle, whose
	 * contents fall into the same fire), burned in lava or fire, thrown by someone {@code takesPart} accepts.
	 */
	static boolean onItemDestroyed(ItemEntity entity, DamageSource source, EndingState data, Predicate<ServerPlayer> takesPart) {
		Entity owner = entity.getOwner();
		if (!(owner instanceof ServerPlayer thrower) || !takesPart.test(thrower) || !source.is(DamageTypeTags.IS_FIRE)) {
			return false;
		}
		Set<String> ids = fragmentIds(entity.getItem());
		if (ids.isEmpty()) {
			return false;
		}
		ids.forEach(data::addBurned);
		data.log("C: " + String.join(", ", ids) + " went into the " + (source.is(DamageTypes.LAVA) ? "lava" : "fire") + " ("
				+ data.burnedIds().size() + " of " + data.everHeld().size() + " held are gone)");
		return true;
	}

	/** A pickup by someone who takes part: every fragment in it (nested too) counts as held, and they keep it. */
	public static void onPickedUp(ServerPlayer player, ItemStack stack) {
		if (EndingApi.takesPart(player)) {
			Set<String> ids = fragmentIds(stack);
			if (!ids.isEmpty()) {
				EndingState data = EndingState.get(player.level().getServer());
				ids.forEach(id -> data.addHeld(id, player.getUUID()));
			}
		}
	}

	/** Every fragment the player carries now counts as held, and they keep it (things taken out of chests are caught here). */
	static void sampleHeld(ServerPlayer player, EndingState data) {
		FragmentHoldings.carried(player).forEach(id -> data.addHeld(id, player.getUUID()));
	}

	private static Set<String> fragmentIds(ItemStack stack) {
		Set<String> ids = new TreeSet<>();
		FragmentHoldings.collect(stack, ids);
		return ids;
	}

	/** Before a block break: remembers it if it is one of the players' own blocks around the group's home. */
	static boolean beforeBreak(Level level, ServerPlayer player, BlockPos pos) {
		if (!(level instanceof ServerLevel server) || !EndingApi.takesPart(player)) {
			return true;
		}
		EndingState data = EndingState.get(server.getServer());
		Optional<GlobalPos> home = data.home();
		int r = EndingConfig.get().cHouseRadius;
		if (home.isPresent() && home.get().dimension().equals(level.dimension()) && near(home.get().pos(), pos, r)
				&& Services.watch().wasPlacedByPlayer(server, pos)) {
			BREAKING.add(GlobalPos.of(level.dimension(), pos.immutable()));
		}
		return true;
	}

	/** After a block break: one of the group's own blocks around the home is gone by one of their own hands. */
	static void afterBreak(Level level, BlockPos pos) {
		if (level instanceof ServerLevel server && BREAKING.remove(GlobalPos.of(level.dimension(), pos))) {
			EndingState.get(server.getServer()).addOwnBroken();
		}
	}

	static void canceledBreak(Level level, BlockPos pos) {
		BREAKING.remove(GlobalPos.of(level.dimension(), pos));
	}

	// --- periodic sampling ---

	/**
	 * Notes "Stop." the first time it is seen, keeps the group's home and the most blocks ever seen around it, what each
	 * one in it carries, and anyone's fog staring. Called with each check; {@code players} are the ones in it now.
	 */
	static void sample(MinecraftServer server, List<ServerPlayer> players, HerobrineState state, EndingState data, EndingConfig cfg, PlayerWatch watch,
			long now) {
		if (state.stopFired() && data.stopSeenAt() == EndingState.NEVER) {
			data.setStopSeenAt(now);
			data.log("\"Stop.\" seen");
		}
		sampleHome(server, players, state, data, cfg, watch);
		for (ServerPlayer player : players) {
			if (!player.isAlive()) {
				continue;
			}
			sampleHeld(player, data);
			if (staringIntoFog(player, cfg, watch)) {
				if (data.lastFogStareAt() == EndingState.NEVER || now - data.lastFogStareAt() >= GameClock.TICKS_PER_DAY / 24) {
					data.log("C: staring into the fog (" + player.getName().getString() + ")");
				}
				data.setLastFogStareAt(now);
			}
		}
	}

	/**
	 * The group's home, "your house" for C and (without a house copy) for B: the base of the home's owner
	 * ({@code ending:home}, D-061: chosen once, among the participants who have a base, and never flips). While the
	 * owner is online with a base, a base of theirs far from the old home with more blocks around it is a new home.
	 * While the owner is away (or has no base now), the home is never theirs to hold (D-064): with no home yet their
	 * first block stands in, and the base of anyone in it that is far from the home with more blocks around it is the
	 * new home, so a friend who played ten minutes at spawn never keeps the group's house there.
	 */
	static void sampleHome(MinecraftServer server, List<ServerPlayer> players, HerobrineState state, EndingState data, EndingConfig cfg,
			PlayerWatch watch) {
		Optional<UUID> owner = watch.ownerFor(server, EndingApi.HOME_OWNER, uuid -> watch.participant(server, uuid).flatMap(watch::base).isPresent());
		Optional<GlobalPos> base = owner.flatMap(uuid -> watch.participant(server, uuid)).flatMap(watch::base);
		if (base.isPresent()) {
			sampleHome(server, base, data, cfg, watch);
			return;
		}
		if (data.home().isEmpty()) {
			sampleHome(server, owner.map(uuid -> state.firstBlocks(uuid).block()).map(PlacedBlock::pos), data, cfg, watch);
		}
		for (ServerPlayer player : players) {
			sampleHome(server, watch.base(player), data, cfg, watch);
		}
		if (players.isEmpty()) {
			sampleHome(server, Optional.empty(), data, cfg, watch);
		}
	}

	/** The home is this base; a base far from the old home with more blocks around it is a new home. */
	static void sampleHome(MinecraftServer server, Optional<GlobalPos> base, EndingState data, EndingConfig cfg, PlayerWatch watch) {
		Optional<GlobalPos> home = data.home();
		if (base.isPresent()) {
			ServerLevel level = server.getLevel(base.get().dimension());
			if (level != null && level.isLoaded(base.get().pos())) {
				if (home.isEmpty()) {
					data.setHome(base.get());
				} else if (!home.get().dimension().equals(base.get().dimension()) || !near(home.get().pos(), base.get().pos(), cfg.cHouseRadius * 2)) {
					int there = houseBlocks(level, base.get().pos(), cfg, watch);
					if (there > data.housePeak()) {
						data.setHome(base.get());
					}
				}
			}
		}
		home = data.home();
		ServerLevel homeLevel = home.map(h -> server.getLevel(h.dimension())).orElse(null);
		if (homeLevel != null && homeLevel.isLoaded(home.get().pos())) {
			int left = houseBlocks(homeLevel, home.get().pos(), cfg, watch);
			if (left > data.housePeak()) {
				data.setHousePeak(left);
			}
		}
	}

	/** Player-placed blocks still standing around {@code home}. */
	static int houseBlocks(ServerLevel level, BlockPos home, EndingConfig cfg, PlayerWatch watch) {
		return watch.placedNear(level, home, cfg.cHouseRadius, s -> !s.isAir()).size();
	}

	/** Still, outdoors, looking level, at dusk or night or in rain, for {@code fogStareSeconds}. */
	static boolean staringIntoFog(ServerPlayer player, EndingConfig cfg, PlayerWatch watch) {
		ServerLevel level = player.level();
		if (level.dimension() != Level.OVERWORLD || player.isSpectator() || player.isSleeping()) {
			return false;
		}
		if (watch.stillTicks(player) < ModConfig.realTicks(cfg.fogStareSeconds) || Math.abs(player.getXRot()) > cfg.fogStarePitch) {
			return false;
		}
		if (!level.canSeeSky(BlockPos.containing(player.getEyePosition()))) {
			return false;
		}
		long time = Math.floorMod(level.getServer().overworld().getOverworldClockTime(), GameClock.TICKS_PER_DAY);
		return level.isRaining() || time >= cfg.fogStareFrom && time <= cfg.fogStareTo;
	}

	// --- facts ---

	/**
	 * Everything the rules read, now. {@code players} are the ones in it ({@link EndingApi#players}): a fragment counts
	 * as held if any of them carries one or it lies in a container at the home, and only fragments whose keeper is among
	 * them must still burn ({@link EndingState#unburned(Predicate, UUID)}). Marked deaths are the most any one player
	 * has of their own (D-063).
	 */
	static EndingFacts facts(MinecraftServer server, List<ServerPlayer> players, HerobrineState state, EndingState data, EndingConfig cfg,
			EndingPorts ports, long now) {
		int left = 0;
		Optional<GlobalPos> home = data.home();
		ServerLevel homeLevel = home.map(h -> server.getLevel(h.dimension())).orElse(null);
		if (home.isPresent()) {
			if (homeLevel != null && homeLevel.isLoaded(home.get().pos())) {
				left = houseBlocks(homeLevel, home.get().pos(), cfg, ports.watch());
			} else {
				left = data.housePeak();
			}
		}
		boolean holds = players.stream().anyMatch(p -> !FragmentHoldings.carried(p).isEmpty());
		if (!holds && home.isPresent() && homeLevel != null) {
			BlockPos at = home.get().pos();
			int r = cfg.cBaseContainerRadius;
			// The base's containers can only be read while its chunks are loaded; unread counts as still held.
			holds = !HouseScan.loaded(homeLevel, at.offset(-r, 0, -r), at.offset(r, 0, r))
					|| !FragmentHoldings.stored(homeLevel, at, r).isEmpty();
		}
		Set<UUID> playing = new HashSet<>();
		players.forEach(p -> playing.add(p.getUUID()));
		UUID oldSubject = state.players().isEmpty() ? null : state.players().getFirst().uuid();
		int unburned = data.unburned(playing::contains, oldSubject).size();
		return new EndingFacts(state.stage(), state.stopFired(), state.tellingStarted(), now, data.stopSeenAt(), data.lastTellingAt(),
				data.lastNamedAt(), data.lastReadAt(), lastTraceVisit(server, cfg, ports.watch()), data.lastFogStareAt(), state.tellingCount(),
				data.tellingsSinceStop(), state.attention(), mostOwnMarkedDeaths(state), data.fragmentsBurned(), unburned, holds,
				data.housePeak(), left, data.ownBroken(), state.hasFlag(DirectorHooks.OBEYED_AFTER_STOP));
	}

	/** The most marked deaths any one known player has of their own (the third-death rule counts each player's, D-063). */
	static int mostOwnMarkedDeaths(HerobrineState state) {
		int most = 0;
		for (HerobrineState.Subject player : state.players()) {
			most = Math.max(most, state.markedDeaths(player.uuid()).size());
		}
		return most;
	}

	/**
	 * The entity's STARED_AT_HIM ({@code FigureApi.STARED}): someone who takes part stared him down at the fog edge,
	 * which is staring into the fog for Ending C, whatever the guess in {@link #staringIntoFog} saw. True if it counted.
	 */
	static boolean onStared(ServerPlayer player, EndingState data, long now, Predicate<ServerPlayer> takesPart) {
		if (!takesPart.test(player)) {
			return false;
		}
		data.setLastFogStareAt(now);
		data.log("C: " + player.getName().getString() + " stared at him in the fog");
		return true;
	}

	/** The last in-game day a participant was near one of his traces (the configured site types), or -1. */
	static long lastTraceVisit(MinecraftServer server, EndingConfig cfg, PlayerWatch watch) {
		Set<SiteType> types = EnumSet.noneOf(SiteType.class);
		for (String name : cfg.traceSiteTypes) {
			try {
				types.add(SiteType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException | NullPointerException e) {
				A1016_02.LOGGER.warn("[a1016] ending: unknown site type '{}' in traceSiteTypes", name);
			}
		}
		int radius = Math.max(0, cfg.traceVisitRadiusChunks);
		long last = -1;
		for (SiteRegistry.Site site : Services.sites().all()) {
			if (!types.contains(site.type())) {
				continue;
			}
			ServerLevel level = server.getLevel(site.dimension());
			if (level == null) {
				continue;
			}
			ChunkPos center = ChunkPos.containing(site.pos());
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					last = Math.max(last, watch.lastVisitDay(level, new ChunkPos(center.x() + dx, center.z() + dz)));
				}
			}
		}
		return last;
	}

	private static boolean near(BlockPos a, BlockPos b, int r) {
		return Math.abs(a.getX() - b.getX()) <= r && Math.abs(a.getY() - b.getY()) <= r && Math.abs(a.getZ() - b.getZ()) <= r;
	}
}
