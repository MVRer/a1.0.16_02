package com.forzacode.a1016_02.ending;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
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

import org.jspecify.annotations.Nullable;

/**
 * What the player does that the commit rules read: tellings and naming, fragment reads, every fragment they held and
 * which of them went into lava or fire, their own house taken apart, staring into the fog, visits near his traces. Only the subject counts.
 * Event handlers record into {@link EndingState}; {@link #facts} reads one {@link EndingFacts}. Server thread only.
 */
public final class EndingWatch {
	/** Own blocks near the home the subject is breaking right now (before the break completes). */
	private static final Set<GlobalPos> BREAKING = new HashSet<>();

	private EndingWatch() {
	}

	static void clear() {
		BREAKING.clear();
	}

	// --- events ---

	/** TELLING (lore): a telling, and whether it names him. */
	static void onTelling(ServerPlayer player, boolean namesHim, EndingState data, long now) {
		if (!Services.watch().isSubject(player)) {
			return;
		}
		data.recordTelling(now, namesHim);
	}

	/** FRAGMENT_READ (lore): the first read of a fragment (later reads come from {@link EndingReads}). */
	static void onFragmentRead(ServerPlayer player, EndingState data, long now) {
		if (Services.watch().isSubject(player)) {
			data.setLastReadAt(now);
		}
	}

	/** An item entity was destroyed by damage. A fragment the subject threw into lava or fire counts for Ending C. */
	public static void onItemDestroyed(ItemEntity entity, ServerLevel level, DamageSource source) {
		if (source.is(DamageTypeTags.IS_FIRE) && !fragmentIds(entity.getItem()).isEmpty()) {
			onItemDestroyed(entity, source, EndingState.get(level.getServer()), Services.watch()::isSubject);
		}
	}

	/**
	 * True if this destroyed item counted: fragments (the item itself, or nested in a shulker box or bundle, whose
	 * contents fall into the same fire), burned in lava or fire, thrown by the subject.
	 */
	static boolean onItemDestroyed(ItemEntity entity, DamageSource source, EndingState data, Predicate<ServerPlayer> subject) {
		Entity owner = entity.getOwner();
		if (!(owner instanceof ServerPlayer thrower) || !subject.test(thrower) || !source.is(DamageTypeTags.IS_FIRE)) {
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

	/** A pickup by the subject: every fragment in it (nested too) counts as held. */
	public static void onPickedUp(ServerPlayer player, ItemStack stack) {
		if (Services.watch().isSubject(player)) {
			Set<String> ids = fragmentIds(stack);
			if (!ids.isEmpty()) {
				EndingState data = EndingState.get(player.level().getServer());
				ids.forEach(data::addHeld);
			}
		}
	}

	/** Every fragment the player carries now counts as held (things taken out of chests are caught here). */
	static void sampleHeld(ServerPlayer player, EndingState data) {
		FragmentHoldings.carried(player).forEach(data::addHeld);
	}

	private static Set<String> fragmentIds(ItemStack stack) {
		Set<String> ids = new TreeSet<>();
		FragmentHoldings.collect(stack, ids);
		return ids;
	}

	/** Before a block break: remembers it if it is one of the subject's own blocks around their home. */
	static boolean beforeBreak(Level level, ServerPlayer player, BlockPos pos) {
		if (!(level instanceof ServerLevel server) || !Services.watch().isSubject(player)) {
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

	/** After a block break: one of their own blocks around their home is gone by their own hand. */
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
	 * Notes "Stop." the first time it is seen, keeps the home and the most blocks ever seen around it, and notes
	 * fog staring. Called with each check.
	 */
	static void sample(@Nullable ServerPlayer player, HerobrineState state, EndingState data, EndingConfig cfg, PlayerWatch watch, long now) {
		if (state.stopFired() && data.stopSeenAt() == EndingState.NEVER) {
			data.setStopSeenAt(now);
			data.log("\"Stop.\" seen");
		}
		if (player == null || !player.isAlive()) {
			return;
		}
		sampleHome(player, data, cfg, watch);
		sampleHeld(player, data);
		if (staringIntoFog(player, cfg, watch)) {
			if (data.lastFogStareAt() == EndingState.NEVER || now - data.lastFogStareAt() >= GameClock.TICKS_PER_DAY / 24) {
				data.log("C: staring into the fog");
			}
			data.setLastFogStareAt(now);
		}
	}

	/** The home is the base; a base far from the old home with more blocks around it is a new home. */
	static void sampleHome(ServerPlayer player, EndingState data, EndingConfig cfg, PlayerWatch watch) {
		Optional<GlobalPos> base = watch.base(player);
		if (base.isEmpty()) {
			return;
		}
		Optional<GlobalPos> home = data.home();
		ServerLevel level = player.level().getServer().getLevel(base.get().dimension());
		if (level == null || !level.isLoaded(base.get().pos())) {
			return;
		}
		if (home.isEmpty()) {
			data.setHome(base.get());
			home = data.home();
		} else if (!home.get().dimension().equals(base.get().dimension()) || !near(home.get().pos(), base.get().pos(), cfg.cHouseRadius * 2)) {
			int there = houseBlocks(level, base.get().pos(), cfg, watch);
			if (there > data.housePeak()) {
				data.setHome(base.get());
				home = data.home();
			}
		}
		ServerLevel homeLevel = player.level().getServer().getLevel(home.get().dimension());
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

	/** Everything the rules read, now. */
	static EndingFacts facts(MinecraftServer server, @Nullable ServerPlayer player, HerobrineState state, EndingState data, EndingConfig cfg,
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
		boolean holds = player != null && !FragmentHoldings.carried(player).isEmpty();
		if (!holds && home.isPresent() && homeLevel != null) {
			BlockPos at = home.get().pos();
			int r = cfg.cBaseContainerRadius;
			// The base's containers can only be read while its chunks are loaded; unread counts as still held.
			holds = !homeLevel.hasChunksAt(at.offset(-r, 0, -r), at.offset(r, 0, r))
					|| !FragmentHoldings.stored(homeLevel, at, r).isEmpty();
		}
		return new EndingFacts(state.stage(), state.stopFired(), state.tellingStarted(), now, data.stopSeenAt(), data.lastTellingAt(),
				data.lastNamedAt(), data.lastReadAt(), lastTraceVisit(server, cfg, ports.watch()), data.lastFogStareAt(), state.tellingCount(),
				data.tellingsSinceStop(), state.attention(), state.markedDeaths().size(), data.fragmentsBurned(), data.unburned().size(), holds,
				data.housePeak(), left, data.ownBroken());
	}

	/** The last in-game day a player was near one of his traces (the configured site types), or -1. */
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
