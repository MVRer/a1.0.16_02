package com.forzacode.a1016_02.ending.d;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.jspecify.annotations.Nullable;

/**
 * After the last minute, for good: the fog stays gone and the music on; every removal in the ledger is undone, the
 * newest first (the oldest last), a few entries every couple of ticks, each out of view (blocks go back to the house,
 * the copy falls apart into it, the network under the house fills in, stacks go back to their chests); then every
 * bare grove grows its leaves back. What the skip rules keep ({@link Undo#skipReason}) stays.
 */
public final class Afterward {
	private static int cursor = -1;
	private static int passDone;
	private static int passWaiting;
	private static int passBlocked;
	private static int passSkipped;
	private static long restUntil;
	private static long nextRegrow;
	/** Far entries waiting for their chunk, grouped by chunk (each cluster holds its chunk loaded while it is worked). */
	private static @Nullable ChunkClusters clusters;

	/** Cost cadence: how often the afterward's lasting effects (no fog, music on) are checked again (never divided). */
	private static final int KEEP_EFFECTS_TICKS = 100;

	private Afterward() {
	}

	static void tick(MinecraftServer server, EndingDState data, EndingDConfig cfg) {
		long now = server.getTickCount();
		if (now % KEEP_EFFECTS_TICKS == 0) {
			keepEffects(server);
		}
		if (!data.undoFinished()) {
			tickClusters(server, data, cfg, Services.traces(), ChunkClusters.Chunks.of(server));
			if (now >= restUntil && now % Math.max(1, cfg.undoIntervalTicks) == 0) {
				step(server, data, cfg, Services.traces(), Math.max(1, cfg.undoPerTick));
			}
		}
		// Groves regrow once the first pass has given back the leaves he took (crowns never cover a waiting leaf).
		if (data.undoPasses() > 0 && now >= nextRegrow) {
			nextRegrow = now + EndingDConfig.ticks(cfg.regrowIntervalSeconds);
			regrow(server, data, Services.traces());
		}
	}

	/** The fog is gone for good and the music stays on. */
	static void keepEffects(MinecraftServer server) {
		HerobrineState.Effects effects = HerobrineState.get(server).effects();
		if (effects.duskFogLevel() > 0.0F) {
			ClientEffects.setDuskFog(server, 0.0F);
		}
		if (effects.musicOff()) {
			ClientEffects.setMusicOff(server, false);
		}
	}

	/**
	 * Up to {@code budget} entries, walking the ledger from the newest down. A full pass that undid nothing and has
	 * nothing waiting ends the undo; a pass that still has work rests a while and starts again from the newest.
	 */
	public static void step(MinecraftServer server, EndingDState data, EndingDConfig cfg, TraceService traces, int budget) {
		step(server, data, cfg, traces, budget, entry -> true);
	}

	/** {@link #step} over the entries {@code scope} accepts (tests keep to their own). */
	public static void step(MinecraftServer server, EndingDState data, EndingDConfig cfg, TraceService traces, int budget,
			Predicate<TraceLedger.Entry> scope) {
		TraceLedger ledger = TraceLedger.get(server);
		List<TraceLedger.Entry> entries = ledger.entries();
		if (cursor < 0) {
			// A new pass, from the newest entry.
			cursor = entries.size() - 1;
		}
		while (budget-- > 0 && cursor >= 0) {
			if (cursor >= entries.size()) {
				cursor = entries.size() - 1;
				if (cursor < 0) {
					break;
				}
			}
			TraceLedger.Entry entry = entries.get(cursor);
			if (!scope.test(entry)) {
				cursor--;
				budget++;
				continue;
			}
			ChunkClusters waiting = clusters(cfg);
			Undo.Result result = waiting.contains(entry) ? Undo.Result.UNLOADED : Undo.undo(server, entry, traces);
			switch (result) {
				case DONE -> {
					passDone++;
					data.addUndone(1);
				}
				case WAIT -> passWaiting++;
				case UNLOADED -> {
					// Its chunk's cluster loads the chunk and works through everything in it while it is held.
					waiting.add(entry, server.getTickCount());
					passWaiting++;
				}
				case BLOCKED -> passBlocked++;
				case SKIP -> passSkipped++;
			}
			cursor--;
			entries = ledger.entries();
		}
		if (cursor < 0) {
			endPass(server, data, cfg);
			cursor = -1;
		}
	}

	private static void endPass(MinecraftServer server, EndingDState data, EndingDConfig cfg) {
		data.addUndoPass();
		A1016_02.LOGGER.info("[a1016] ending d: undo pass {}: {} undone, {} waiting, {} cannot be, {} stay", data.undoPasses(), passDone, passWaiting,
				passBlocked, passSkipped);
		boolean finished = passDone == 0 && passWaiting == 0 && clusters(cfg).isEmpty();
		if (finished) {
			data.setUndoFinished(true);
		} else if (passDone == 0) {
			restUntil = server.getTickCount() + EndingDConfig.ticks(cfg.undoRestSeconds);
		}
		passDone = passWaiting = passBlocked = passSkipped = 0;
	}

	private static ChunkClusters clusters(EndingDConfig cfg) {
		if (clusters == null) {
			clusters = new ChunkClusters(Math.max(1, cfg.undoMaxClusters));
		}
		return clusters;
	}

	/**
	 * The far clusters, every tick: a few new chunks are loaded, held ones are renewed before their ticket runs out,
	 * and the entries of every loaded cluster are tried while it is held. What they undo counts for the pass.
	 */
	static ChunkClusters.Tick tickClusters(MinecraftServer server, EndingDState data, EndingDConfig cfg, TraceService traces, ChunkClusters.Chunks chunks) {
		ChunkClusters waiting = clusters(cfg);
		if (waiting.isEmpty()) {
			return new ChunkClusters.Tick(0, 0, 0);
		}
		ChunkClusters.Tick tick = waiting.tick(server.getTickCount(), Math.max(1, cfg.chunkLoadsPerTick), ChunkClusters.TICKET.timeout() / 2,
				EndingDConfig.ticks(cfg.undoClusterTimeoutSeconds), Math.max(1, cfg.undoPerTick), chunks, entry -> Undo.undo(server, entry, traces));
		if (tick.undone() > 0) {
			passDone += tick.undone();
			data.addUndone(tick.undone());
		}
		return tick;
	}

	/** One bare grove at a time (loaded chunks only), one crown per call. */
	static void regrow(MinecraftServer server, EndingDState data, TraceService traces) {
		for (SiteRegistry.Site site : Services.sites().all()) {
			if (site.type() != com.forzacode.a1016_02.core.SiteType.BARE_GROVE || data.regrown().contains(site.id())) {
				continue;
			}
			ServerLevel level = server.getLevel(site.dimension());
			if (level == null || level.getChunkSource().getChunkNow(site.pos().getX() >> 4, site.pos().getZ() >> 4) == null) {
				continue;
			}
			List<Regrow.Crown> crowns = Regrow.crowns(level, site.pos(), Math.max(6, site.size()));
			if (crowns.isEmpty()) {
				data.addRegrown(site.id());
				continue;
			}
			for (Regrow.Crown crown : crowns) {
				if (Regrow.grow(level, crown, traces)) {
					return;
				}
			}
			return;
		}
	}

	static String describe(EndingDState data) {
		ChunkClusters waiting = clusters;
		return String.format(Locale.ROOT, "%s, %d undone in %d passes, %d groves regrown, %d far chunks waiting (%d entries)",
				data.undoFinished() ? "finished" : "running", data.undone(), data.undoPasses(), data.regrown().size(),
				waiting == null ? 0 : waiting.size(), waiting == null ? 0 : waiting.entries());
	}

	public static void clear() {
		clusters = null;
		cursor = -1;
		passDone = passWaiting = passBlocked = passSkipped = 0;
		restUntil = 0;
		nextRegrow = 0;
	}
}
