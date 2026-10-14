package com.forzacode.a1016_02.director;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Attention;
import com.forzacode.a1016_02.core.AttentionTrigger;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.Stage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongs;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The attention triggers the director owns (D-017), for any participant. Attention is one value for the world
 * (D-058), so a trigger counts once per occasion, not once per witness, unless it is about one player's own setup:
 * <ul>
 * <li>SLEPT: per occasion, one per night however many sleep (with {@code sleptOncePerDay} off, one per sleep that
 * nobody else was already in).</li>
 * <li>LOW_RENDER_DISTANCE: per player (their own view distance, a personal record, D-061), at most once per session.</li>
 * <li>DAYLIGHT_OPEN_AREAS: per occasion, one world tally that runs while anyone plays in open, clear daylight.</li>
 * <li>DISC_13_UNDERGROUND and STOPPED_DISC_13: per occasion, one per jukebox with anyone in earshot.</li>
 * <li>OBEYED_AFTER_STOP: per occasion, the group's obeying (D-064): anyone's telling starts the wait over.</li>
 * <li>AVOIDED_TRACES: per occasion, from the shared footprint: each visit by anyone counts once.</li>
 * </ul>
 * The per-occasion memory is in {@link WorldMemory}, the per-player one in {@link PacingMemory}. Server thread only.
 */
public final class DirectorTriggers {
	/** Jukeboxes whose underground disc 13 counted, so stopping it mid-track can count too. */
	private static final Map<GlobalPos, Long> DISC_13_PLAYING = new HashMap<>();

	private DirectorTriggers() {
	}

	static void clear() {
		DISC_13_PLAYING.clear();
	}

	/** Runs with the world's director step: the triggers that count once per occasion. */
	static void periodicWorld(MinecraftServer server, List<ServerPlayer> participants, DirectorRules rules, DirectorBrain.Clock clock) {
		DirectorData data = DirectorData.get(server);
		WorldMemory w = data.memory().world();
		DirectorConfig config = DirectorConfig.get();
		Pacing pacing = ModConfig.pacing();
		boolean daylight = false;
		for (ServerPlayer player : participants) {
			daylight |= player.isAlive() && inOpenDaylight(player, config);
		}
		long period = Math.max(1, ModConfig.realTicks(config.daylightTriggerMinutes * 60));
		if (daylightRule(w, daylight, rules.tickInterval, period)) {
			Attention.trigger(server, AttentionTrigger.DAYLIGHT_OPEN_AREAS);
		}
		obeyedAfterStop(server, w, clock, pacing);
		avoidedTraces(server, w, clock, config);
		data.setDirty();
	}

	/** Runs with one player's own director tick: the triggers that are theirs. */
	static void periodicFor(MinecraftServer server, ServerPlayer player, PacingMemory m, DirectorRules rules) {
		DirectorConfig config = DirectorConfig.get();
		boolean low = player.requestedViewDistance() <= ModConfig.pacing().lowRenderDistanceChunks;
		long period = Math.max(1, ModConfig.realTicks(config.lowRenderTriggerMinutes * 60));
		if (lowRenderRule(m, low, rules.tickInterval, period)) {
			Attention.trigger(server, AttentionTrigger.LOW_RENDER_DISTANCE);
		}
	}

	// --- LOW_RENDER_DISTANCE: the original sighting was on a slow PC with a tiny render distance ---

	/**
	 * LOW_RENDER_DISTANCE's rule for one player: {@code period} of play at a low view distance in a session counts once,
	 * and then not again until their next session (the brain clears the tally when a session starts).
	 */
	static boolean lowRenderRule(PacingMemory m, boolean low, long step, long period) {
		if (!low || m.lowRenderCounted) {
			return false;
		}
		m.lowRenderAccum += step;
		if (m.lowRenderAccum < period) {
			return false;
		}
		m.lowRenderCounted = true;
		return true;
	}

	// --- DAYLIGHT_OPEN_AREAS: no fog for him to stand in ---

	/** One world tally: {@code period} of world play with anyone in open daylight counts once, then the tally restarts. */
	static boolean daylightRule(WorldMemory w, boolean anyoneInOpenDaylight, long step, long period) {
		if (!anyoneInOpenDaylight) {
			return false;
		}
		w.daylightAccum += step;
		if (w.daylightAccum < period) {
			return false;
		}
		w.daylightAccum -= period;
		return true;
	}

	/** In the overworld, in clear daylight, in the open (see {@link #isOpen}), in their own level. */
	static boolean inOpenDaylight(ServerPlayer player, DirectorConfig config) {
		ServerLevel level = player.level();
		return level.dimension() == Level.OVERWORLD && level.isBrightOutside() && !level.isRaining() && isOpen(level, player, config);
	}

	/** Open: nothing above the player's eyes, here and at most of eight points around (trees and roofs count). */
	static boolean isOpen(ServerLevel level, ServerPlayer player, DirectorConfig config) {
		int eyeY = (int) Math.floor(player.getEyeY());
		int x = player.getBlockX();
		int z = player.getBlockZ();
		if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) > eyeY) {
			return false;
		}
		int r = Math.max(1, config.daylightOpenRadius);
		int open = 1;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				int sx = x + dx * r;
				int sz = z + dz * r;
				if (level.hasChunk(sx >> 4, sz >> 4) && level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz) <= eyeY + 1) {
					open++;
				}
			}
		}
		return open >= Math.min(9, config.daylightOpenMinSamples + 1);
	}

	// --- OBEYED_AFTER_STOP: writing nothing for obeyDays in-game days after "Stop." ---

	private static void obeyedAfterStop(MinecraftServer server, WorldMemory w, DirectorBrain.Clock clock, Pacing pacing) {
		if (obeyRule(HerobrineState.get(server), w, clock.day(), pacing.obeyDays)) {
			Attention.trigger(server, AttentionTrigger.OBEYED_AFTER_STOP);
		}
	}

	/**
	 * OBEYED_AFTER_STOP's rule: after "Stop.", {@code obeyDays} in-game days with no telling from anyone (since "Stop."
	 * was seen or the last telling). True the moment it fires (once until the next telling). Keeps
	 * {@link DirectorFlags#OBEYED_AFTER_STOP} set exactly while it has fired and no telling came since, so the endings
	 * read the same rule.
	 */
	static boolean obeyRule(HerobrineState state, WorldMemory w, long day, int obeyDays) {
		boolean fires = false;
		if (!state.stopFired()) {
			w.stopSeenDay = -1;
			w.obeyCounted = false;
		} else {
			if (w.stopSeenDay < 0) {
				w.stopSeenDay = day;
			}
			long since = day - Math.max(w.stopSeenDay, w.lastTellingDay);
			if (!w.obeyCounted && since >= obeyDays) {
				w.obeyCounted = true;
				fires = true;
			}
		}
		if (state.hasFlag(DirectorFlags.OBEYED_AFTER_STOP) != w.obeyCounted) {
			state.setFlag(DirectorFlags.OBEYED_AFTER_STOP, w.obeyCounted);
		}
		return fires;
	}

	/**
	 * Someone told, whoever it was (D-064: the group obeys only while nobody in it tells): with the world's wait
	 * started over ({@code DirectorBrain.onTelling}), {@link DirectorFlags#OBEYED_AFTER_STOP} goes at once, not at the
	 * next world step, so Ending A never reads an obeying that a friend's telling already broke.
	 */
	static void obeyBroken(HerobrineState state) {
		if (state.hasFlag(DirectorFlags.OBEYED_AFTER_STOP)) {
			state.setFlag(DirectorFlags.OBEYED_AFTER_STOP, false);
		}
	}

	// --- AVOIDED_TRACES: no visit near his tunnels and pyramids for several days ---

	private static void avoidedTraces(MinecraftServer server, WorldMemory w, DirectorBrain.Clock clock, DirectorConfig config) {
		long day = clock.day();
		if (day == w.lastAvoidCheckDay || !HerobrineState.get(server).stage().atLeast(Stage.TRACES)) {
			return;
		}
		w.lastAvoidCheckDay = day;
		long lastVisit = lastTraceVisit(server, config);
		// Never having been near one is not avoiding. Each visit can count once.
		if (lastVisit >= 0 && day - lastVisit >= config.avoidDays && w.avoidCountedVisitDay != lastVisit) {
			w.avoidCountedVisitDay = lastVisit;
			Attention.trigger(server, AttentionTrigger.AVOIDED_TRACES);
		}
	}

	/** The last in-game day any participant was near a trace site of the configured types, or -1. */
	static long lastTraceVisit(MinecraftServer server, DirectorConfig config) {
		Set<SiteType> types = EnumSet.noneOf(SiteType.class);
		for (String name : config.avoidSiteTypes) {
			try {
				types.add(SiteType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException | NullPointerException e) {
				A1016_02.LOGGER.warn("[a1016] director: unknown site type '{}' in avoidSiteTypes", name);
			}
		}
		int radius = Math.max(0, config.avoidVisitRadiusChunks);
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
					last = Math.max(last, Services.watch().lastVisitDay(level, new ChunkPos(center.x() + dx, center.z() + dz)));
				}
			}
		}
		return last;
	}

	// --- SLEPT: he works while you aren't watching ---

	static void onStartSleeping(ServerPlayer player) {
		PlayerWatch watch = Services.watch();
		if (!watch.isParticipant(player)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		boolean someoneElseAsleep = false;
		for (ServerPlayer other : watch.participants(server)) {
			someoneElseAsleep |= !other.getUUID().equals(player.getUUID()) && watch.isSleeping(other);
		}
		DirectorData data = DirectorData.get(server);
		if (sleptRule(data.memory().world(), GameClock.day(server), someoneElseAsleep, DirectorConfig.get().sleptOncePerDay)) {
			data.setDirty();
			Attention.trigger(server, AttentionTrigger.SLEPT);
		}
	}

	/**
	 * SLEPT's rule, once per occasion: a participant lay down. Someone else already asleep makes it the same night's
	 * sleep; with {@code oncePerDay} (the default) a night that already counted never counts again, whoever sleeps.
	 */
	static boolean sleptRule(WorldMemory w, long day, boolean someoneElseAsleep, boolean oncePerDay) {
		if (someoneElseAsleep || oncePerDay && w.lastSleptDay == day) {
			return false;
		}
		w.lastSleptDay = day;
		return true;
	}

	// --- DISC_13_UNDERGROUND / STOPPED_DISC_13 (jukebox mixin) ---

	/** A jukebox started a song. Counts disc 13 below {@code disc13BelowY} with any alive participant in earshot, once. */
	public static void onJukeboxPlay(ServerLevel level, BlockPos pos, Holder<JukeboxSong> song) {
		if (!song.is(JukeboxSongs.THIRTEEN) || pos.getY() >= ModConfig.pacing().disc13BelowY) {
			return;
		}
		MinecraftServer server = level.getServer();
		DirectorConfig config = DirectorConfig.get();
		boolean heard = anyoneInEarshot(Services.watch().participants(server), level, pos, config.disc13HearRadius);
		DirectorData data = DirectorData.get(server);
		long now = GameClock.playTicks(server);
		if (!disc13Rule(data.memory().world(), now, heard, ModConfig.realTicks(config.disc13CooldownMinutes * 60))) {
			return;
		}
		data.setDirty();
		DISC_13_PLAYING.put(GlobalPos.of(level.dimension(), pos.immutable()), now);
		Attention.trigger(server, AttentionTrigger.DISC_13_UNDERGROUND);
	}

	/** True if any alive one of these participants is in this level within {@code radius} blocks of the jukebox. */
	static boolean anyoneInEarshot(List<ServerPlayer> participants, ServerLevel level, BlockPos pos, double radius) {
		Vec3 center = Vec3.atCenterOf(pos);
		for (ServerPlayer player : participants) {
			if (player.isAlive() && player.level() == level && player.distanceToSqr(center) <= radius * radius) {
				return true;
			}
		}
		return false;
	}

	/**
	 * DISC_13_UNDERGROUND's rule, once per occasion: one jukebox start counts once however many hear it (the caller
	 * asks only whether anyone does), and none counts within {@code cooldown} of world play after the last one.
	 */
	static boolean disc13Rule(WorldMemory w, long worldNow, boolean anyoneInEarshot, long cooldown) {
		if (!anyoneInEarshot || worldNow - w.lastDisc13Play < cooldown) {
			return false;
		}
		w.lastDisc13Play = worldNow;
		return true;
	}

	/** A jukebox stopped. Counts only a disc 13 that counted when it started and is stopped before its end. */
	public static void onJukeboxStop(ServerLevel level, BlockPos pos, Holder<JukeboxSong> song, long ticksSinceStart) {
		if (DISC_13_PLAYING.remove(GlobalPos.of(level.dimension(), pos.immutable())) == null) {
			return;
		}
		if (song == null || !song.is(JukeboxSongs.THIRTEEN) || song.value().hasFinished(ticksSinceStart)) {
			return;
		}
		Attention.trigger(level.getServer(), AttentionTrigger.STOPPED_DISC_13);
	}
}
