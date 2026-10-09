package com.forzacode.a1016_02.director;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Attention;
import com.forzacode.a1016_02.core.AttentionTrigger;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
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
 * The attention triggers the director owns (D-017): sleeping, low render distance, daylight in open areas,
 * disc 13 underground and stopping it, obeying after "Stop.", and avoiding his traces. Only the subject counts.
 * Server thread only.
 */
public final class DirectorTriggers {
	/** Jukeboxes whose underground disc 13 counted, so stopping it mid-track can count too. */
	private static final Map<GlobalPos, Long> DISC_13_PLAYING = new HashMap<>();

	private DirectorTriggers() {
	}

	static void clear() {
		DISC_13_PLAYING.clear();
	}

	/** Runs with the director tick (every {@code directorTickSeconds} of play). */
	static void periodic(MinecraftServer server, ServerPlayer subject, DirectorRules rules, DirectorBrain.Clock clock) {
		DirectorData data = DirectorData.get(server);
		DirectorMemory m = data.memory();
		DirectorConfig config = DirectorConfig.get();
		Pacing pacing = ModConfig.pacing();
		lowRenderDistance(server, subject, rules, m, config, pacing);
		daylightOpenAreas(server, subject, rules, m, config);
		obeyedAfterStop(server, m, clock, pacing);
		avoidedTraces(server, m, clock, config);
		data.setDirty();
	}

	// --- LOW_RENDER_DISTANCE: the original sighting was on a slow PC with a tiny render distance ---

	private static void lowRenderDistance(MinecraftServer server, ServerPlayer subject, DirectorRules rules, DirectorMemory m,
			DirectorConfig config, Pacing pacing) {
		if (subject.requestedViewDistance() > pacing.lowRenderDistanceChunks) {
			return;
		}
		m.lowRenderAccum += rules.tickInterval;
		long period = Math.max(1, ModConfig.realTicks(config.lowRenderTriggerMinutes * 60));
		if (m.lowRenderAccum >= period) {
			m.lowRenderAccum -= period;
			Attention.trigger(server, AttentionTrigger.LOW_RENDER_DISTANCE);
		}
	}

	// --- DAYLIGHT_OPEN_AREAS: no fog for him to stand in ---

	private static void daylightOpenAreas(MinecraftServer server, ServerPlayer subject, DirectorRules rules, DirectorMemory m,
			DirectorConfig config) {
		ServerLevel level = subject.level();
		if (level.dimension() != Level.OVERWORLD || !level.isBrightOutside() || level.isRaining() || !isOpen(level, subject, config)) {
			return;
		}
		m.daylightAccum += rules.tickInterval;
		long period = Math.max(1, ModConfig.realTicks(config.daylightTriggerMinutes * 60));
		if (m.daylightAccum >= period) {
			m.daylightAccum -= period;
			Attention.trigger(server, AttentionTrigger.DAYLIGHT_OPEN_AREAS);
		}
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

	private static void obeyedAfterStop(MinecraftServer server, DirectorMemory m, DirectorBrain.Clock clock, Pacing pacing) {
		if (obeyRule(HerobrineState.get(server), m, clock.day(), pacing.obeyDays)) {
			Attention.trigger(server, AttentionTrigger.OBEYED_AFTER_STOP);
		}
	}

	/**
	 * OBEYED_AFTER_STOP's rule: after "Stop.", {@code obeyDays} in-game days with no telling (since "Stop." was seen or
	 * the last telling). True the moment it fires (once until the next telling). Keeps
	 * {@link DirectorFlags#OBEYED_AFTER_STOP} set exactly while it has fired and no telling came since, so the endings
	 * read the same rule.
	 */
	static boolean obeyRule(HerobrineState state, DirectorMemory m, long day, int obeyDays) {
		boolean fires = false;
		if (!state.stopFired()) {
			m.stopSeenDay = -1;
			m.obeyCounted = false;
		} else {
			if (m.stopSeenDay < 0) {
				m.stopSeenDay = day;
			}
			long since = day - Math.max(m.stopSeenDay, m.lastTellingDay);
			if (!m.obeyCounted && since >= obeyDays) {
				m.obeyCounted = true;
				fires = true;
			}
		}
		if (state.hasFlag(DirectorFlags.OBEYED_AFTER_STOP) != m.obeyCounted) {
			state.setFlag(DirectorFlags.OBEYED_AFTER_STOP, m.obeyCounted);
		}
		return fires;
	}

	// --- AVOIDED_TRACES: no visit near his tunnels and pyramids for several days ---

	private static void avoidedTraces(MinecraftServer server, DirectorMemory m, DirectorBrain.Clock clock, DirectorConfig config) {
		long day = clock.day();
		if (day == m.lastAvoidCheckDay || !HerobrineState.get(server).stage().atLeast(Stage.TRACES)) {
			return;
		}
		m.lastAvoidCheckDay = day;
		long lastVisit = lastTraceVisit(server, config);
		// Never having been near one is not avoiding. Each visit can count once.
		if (lastVisit >= 0 && day - lastVisit >= config.avoidDays && m.avoidCountedVisitDay != lastVisit) {
			m.avoidCountedVisitDay = lastVisit;
			Attention.trigger(server, AttentionTrigger.AVOIDED_TRACES);
		}
	}

	/** The last in-game day any player was near a trace site of the configured types, or -1. */
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
		if (!Services.watch().isSubject(player)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		DirectorData data = DirectorData.get(server);
		DirectorMemory m = data.memory();
		long day = GameClock.day(server);
		if (DirectorConfig.get().sleptOncePerDay && m.lastSleptDay == day) {
			return;
		}
		m.lastSleptDay = day;
		data.setDirty();
		Attention.trigger(server, AttentionTrigger.SLEPT);
	}

	// --- DISC_13_UNDERGROUND / STOPPED_DISC_13 (jukebox mixin) ---

	/** A jukebox started a song. Counts disc 13 below {@code disc13BelowY} with the subject in earshot. */
	public static void onJukeboxPlay(ServerLevel level, BlockPos pos, Holder<JukeboxSong> song) {
		if (!song.is(JukeboxSongs.THIRTEEN) || pos.getY() >= ModConfig.pacing().disc13BelowY) {
			return;
		}
		MinecraftServer server = level.getServer();
		Optional<ServerPlayer> subject = Services.watch().subject(server);
		DirectorConfig config = DirectorConfig.get();
		double radius = config.disc13HearRadius;
		if (subject.isEmpty() || subject.get().level() != level || subject.get().distanceToSqr(Vec3.atCenterOf(pos)) > radius * radius) {
			return;
		}
		DirectorData data = DirectorData.get(server);
		DirectorMemory m = data.memory();
		long now = GameClock.playTicks(server);
		if (now - m.lastDisc13Play < ModConfig.realTicks(config.disc13CooldownMinutes * 60)) {
			return;
		}
		m.lastDisc13Play = now;
		data.setDirty();
		DISC_13_PLAYING.put(GlobalPos.of(level.dimension(), pos.immutable()), now);
		Attention.trigger(server, AttentionTrigger.DISC_13_UNDERGROUND);
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
