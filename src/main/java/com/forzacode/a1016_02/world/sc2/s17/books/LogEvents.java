package com.forzacode.a1016_02.world.sc2.s17.books;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.PlayerAction;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

/**
 * What feeds the {@link DayLog} (registered once by {@link Books#init}): once a second the day's rollover, its weather,
 * moon and fog, who is online and out at night, and the doors and chests a participant used at another known player's
 * home (from {@link PlayerWatch#recentActions}); deaths (vanilla's and the marks), what he took
 * ({@link HerobrineEvents#TRACED} for what a player placed, near the nearest participant), the places entered, and the
 * statistics kept at logout and at each rollover. Only while the layer is switched on; server thread; never logs.
 */
final class LogEvents {
	/** Ticks between two looks. */
	static final int EVERY = 20;
	/** How near a door or chest is to another's home to be theirs (blocks, horizontally). */
	static final int HOME_REACH = 8;
	/** How near a participant is to what he took to be the one it was taken from. */
	static final int TAKEN_REACH = 64;

	private static boolean registered;

	private LogEvents() {
	}

	static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY == 0 && SelfCreated.enabled()) {
				tick(server);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && SelfCreated.enabled()) {
				String cause = source.typeHolder().unwrapKey().map(k -> k.identifier().getPath()).orElse("generic");
				death(player, cause, false);
			}
		});
		HerobrineEvents.MARKED_DEATH.register((player, cause, pos) -> {
			if (SelfCreated.enabled()) {
				death(player, cause, true);
			}
		});
		HerobrineEvents.TRACED.register(LogEvents::traced);
		HerobrineEvents.REMAINS_ENTERED.register((level, place, player) -> {
			if (SelfCreated.enabled()) {
				MinecraftServer server = level.getServer();
				DayLog log = open(server);
				log.entered(index(server, player.getUUID()), player.getUUID(), place.kind().number());
			}
		});
		HerobrineEvents.PARTICIPATION.register((server, uuid, participating) -> {
			if (!participating && SelfCreated.enabled()) {
				Services.watch().participant(server, uuid).ifPresent(player -> DayLog.get(server).snapshot(uuid, snapshot(player)));
			}
		});
	}

	/** The log with today open (the day rolled over if it changed, the stage kept at the old day's end). */
	static DayLog open(MinecraftServer server) {
		DayLog log = DayLog.get(server);
		int day = (int) GameClock.day(server) + 1;
		if (log.current() != day) {
			boolean rolled = log.current() != 0;
			log.open(day, HerobrineState.get(server).stage().ordinal());
			if (rolled) {
				Services.watch().participants(server).forEach(p -> log.snapshot(p.getUUID(), snapshot(p)));
			}
		}
		return log;
	}

	static void tick(MinecraftServer server) {
		DayLog log = open(server);
		HerobrineState state = HerobrineState.get(server);
		ServerLevel overworld = server.overworld();
		long clock = overworld.getOverworldClockTime();
		boolean night = night(clock);
		int rank = overworld.isThundering() ? DayLog.THUNDER : overworld.isRaining() ? DayLog.RAIN : DayLog.CLEAR;
		Map<Integer, GlobalPos> homes = null;
		for (ServerPlayer player : Services.watch().participants(server)) {
			int index = index(state, player.getUUID());
			if (index < 0) {
				continue;
			}
			log.online(index);
			boolean outside = player.level() == overworld;
			if (outside && rank == DayLog.RAIN && snowAt(overworld, player.blockPosition())) {
				rank = DayLog.SNOW;
			}
			if (night && outside && !player.isSleeping() && overworld.canSeeSkyFromBelowWater(player.blockPosition())) {
				log.nightOut(index);
			}
			if (homes == null) {
				homes = homes(server, state);
			}
			scan(log, player, index, homes);
		}
		log.weather(rank);
		if (night) {
			log.moon((int) Math.floorMod(clock / GameClock.TICKS_PER_DAY, 8L));
			HerobrineState.Effects effects = state.effects();
			if (effects.fogSpell().isPresent() || effects.duskFogLevel() > 0.2F) {
				log.fog();
			}
		}
	}

	/** Doors and chests this participant used at another's home since the last look. */
	private static void scan(DayLog log, ServerPlayer player, int index, Map<Integer, GlobalPos> homes) {
		List<PlayerAction> actions = Services.watch().recentActions(player);
		long seen = log.scanned.getOrDefault(player.getUUID(), Long.MIN_VALUE);
		long newest = seen;
		for (PlayerAction action : actions) {
			ResourceKey<Level> dimension;
			BlockPos pos;
			long tick;
			Block block;
			switch (action) {
				case PlayerAction.DoorUse door -> {
					dimension = door.dimension();
					pos = door.pos();
					tick = door.tick();
					block = door.state().getBlock();
				}
				case PlayerAction.ContainerOpen box -> {
					dimension = box.dimension();
					pos = box.pos();
					tick = box.tick();
					block = box.state().getBlock();
				}
				default -> {
					continue;
				}
			}
			newest = Math.max(newest, tick);
			if (tick <= seen) {
				continue;
			}
			for (Map.Entry<Integer, GlobalPos> home : homes.entrySet()) {
				GlobalPos at = home.getValue();
				if (home.getKey() != index && at.dimension() == dimension && Math.abs(at.pos().getX() - pos.getX()) <= HOME_REACH
						&& Math.abs(at.pos().getZ() - pos.getZ()) <= HOME_REACH && Math.abs(at.pos().getY() - pos.getY()) <= HOME_REACH) {
					log.visit(index, home.getKey(), Facts.spaced(BuiltInRegistries.BLOCK.getKey(block).getPath()));
				}
			}
		}
		log.scanned.put(player.getUUID(), newest);
	}

	/** Every known player's home (the first {@link DayLog#PLAYERS}): an online one's watch base, else their first block. */
	private static Map<Integer, GlobalPos> homes(MinecraftServer server, HerobrineState state) {
		Map<Integer, GlobalPos> homes = new HashMap<>();
		List<HerobrineState.Subject> players = state.players();
		PlayerWatch watch = Services.watch();
		for (int i = 0; i < players.size() && i < DayLog.PLAYERS; i++) {
			UUID uuid = players.get(i).uuid();
			GlobalPos home = watch.participant(server, uuid).flatMap(watch::base).orElse(null);
			if (home == null) {
				PlacedBlock first = state.firstBlocks(uuid).block();
				home = first == null ? null : first.pos();
			}
			if (home != null) {
				homes.put(i, home);
			}
		}
		return homes;
	}

	private static void death(ServerPlayer player, String cause, boolean marked) {
		MinecraftServer server = player.level().getServer();
		DayLog log = open(server);
		log.death(index(server, player.getUUID()), cause, marked, night(server.overworld().getOverworldClockTime()), server.getTickCount());
	}

	private static void traced(ServerLevel level, TraceLedger.Entry entry, boolean placedByPlayer) {
		if (!placedByPlayer || !SelfCreated.enabled()) {
			return;
		}
		String word;
		boolean item;
		switch (entry.kind()) {
			case REMOVE, MOVE, CONVERT -> {
				if (entry.state().isEmpty()) {
					return;
				}
				word = Facts.word(entry.state().get());
				item = false;
			}
			case REMOVE_STACK, MOVE_STACK -> {
				if (entry.stack().isEmpty() || entry.stack().get().isEmpty()) {
					return;
				}
				word = Facts.spaced(BuiltInRegistries.ITEM.getKey(entry.stack().get().getItem()).getPath());
				item = true;
			}
			default -> {
				return;
			}
		}
		MinecraftServer server = level.getServer();
		BlockPos at = entry.pos().pos();
		ServerPlayer nearest = null;
		double best = (double) TAKEN_REACH * TAKEN_REACH;
		for (ServerPlayer player : Services.watch().participants(server)) {
			double d = player.level() == level ? player.blockPosition().distSqr(at) : Double.MAX_VALUE;
			if (d <= best) {
				best = d;
				nearest = player;
			}
		}
		open(server).took(nearest == null ? -1 : index(server, nearest.getUUID()), word, item);
	}

	/** A player's statistics now (vanilla's counters). */
	static DayLog.Snapshot snapshot(ServerPlayer player) {
		ServerStatsCounter stats = player.getStats();
		Block top = null;
		int most = 0;
		for (Block block : BuiltInRegistries.BLOCK) {
			int n = stats.getValue(Stats.BLOCK_MINED, block);
			if (n > most) {
				most = n;
				top = block;
			}
		}
		return new DayLog.Snapshot(top == null ? "" : Facts.spaced(BuiltInRegistries.BLOCK.getKey(top).getPath()), most,
				stats.getValue(Stats.CUSTOM, Stats.WALK_ONE_CM) / 100, stats.getValue(Stats.CUSTOM, Stats.JUMP), stats.getValue(Stats.CUSTOM, Stats.MOB_KILLS),
				stats.getValue(Stats.CUSTOM, Stats.SLEEP_IN_BED), stats.getValue(Stats.CUSTOM, Stats.DEATHS),
				stats.getValue(Stats.CUSTOM, Stats.TIME_SINCE_DEATH));
	}

	static boolean night(long clock) {
		long time = Math.floorMod(clock, GameClock.TICKS_PER_DAY);
		return time >= 13000 && time < 23000;
	}

	private static boolean snowAt(ServerLevel level, BlockPos pos) {
		return level.getBiome(pos).value().getPrecipitationAt(pos, 63) == Biome.Precipitation.SNOW;
	}

	private static int index(MinecraftServer server, UUID uuid) {
		return index(HerobrineState.get(server), uuid);
	}

	private static int index(HerobrineState state, UUID uuid) {
		List<HerobrineState.Subject> players = state.players();
		for (int i = 0; i < players.size() && i < DayLog.PLAYERS; i++) {
			if (players.get(i).uuid().equals(uuid)) {
				return i;
			}
		}
		return -1;
	}
}
