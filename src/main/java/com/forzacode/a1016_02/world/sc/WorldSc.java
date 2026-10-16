package com.forzacode.a1016_02.world.sc;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The self-created layer's world part (08, 09, 10, 11; {@code docs/SELF_CREATED.md}), called from world's init: its
 * cards, the figure-left listener, the queue's tick, the debug commands ({@code /a1016 sc 08..11}) and what each one
 * puts back when the layer is switched off. While the layer is off, what 08, 09 and 10 could not give back yet (in view,
 * or not loaded then) is tried again every {@value #RETRY_TICKS} ticks until nothing waits (review R7).
 */
public final class WorldSc {
	/** While the layer is off, what waits to be given back is tried again this often (ticks). */
	public static final int RETRY_TICKS = 600;
	/** The most passes one give-back makes (a pass after one that gave something back retries what was blocked). */
	private static final int MAX_PASSES = 8;

	/** A give-back's count: what went back, and what waits (in view, or not loaded now) to be tried again. */
	public record Back(int done, int waiting) {
	}

	/** Each part's give-back by its revert id. */
	private static final Map<String, Function<MinecraftServer, Back>> GIVE_BACKS = new LinkedHashMap<>();
	/** The parts with something still waiting to be given back while the layer is off. */
	private static final Set<String> PENDING = new LinkedHashSet<>();

	static {
		GIVE_BACKS.put("sc08", server -> turnBackAll(server, e -> e.cause().equals(Turned.CAUSE)));
		GIVE_BACKS.put("sc09", server -> giveBackAll(server, Tracks.CAUSE));
		GIVE_BACKS.put("sc10", server -> giveBackAll(server, WhereHeStood.CAUSE));
	}

	private WorldSc() {
	}

	public static void init() {
		Director.register(new Turned.Card());
		Director.register(new Tracks.Card());
		Director.register(new TorchTrail.Card());
		HerobrineEvents.FIGURE_LEFT.register(WhereHeStood::onFigureLeft);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % WhereHeStood.TRY_EVERY_TICKS == 13 && !WhereHeStood.LIVE.pending().isEmpty()) {
				WhereHeStood.LIVE.tick(ScEnv.live(server), server.getTickCount());
			}
			if (!PENDING.isEmpty() && server.getTickCount() % RETRY_TICKS == 17) {
				retry(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WhereHeStood.LIVE.clear();
			PENDING.clear();
		});
		ScCommands.register();

		// Switched off: what is his from these parts is given back where it can be now (loaded, out of view; never a
		// chunk loaded); what waits is tried again while the layer stays off, the rest stays a trace like any other
		// (Ending D still gives it back; grass grows over prints).
		SelfCreated.onSwitchedOff("sc08", server -> giveBackPart(server, "sc08"));
		SelfCreated.onSwitchedOff("sc09", server -> giveBackPart(server, "sc09"));
		SelfCreated.onSwitchedOff("sc10", server -> {
			WhereHeStood.LIVE.clear();
			giveBackPart(server, "sc10");
			// Its fog places are core's: core's own revert clears them.
		});
		// 11 is left by others (D-088): never undone, and nothing of it is metadata or UI.
		SelfCreated.onSwitchedOff("sc11", server -> {
		});
	}

	/** One part's give-back now; it stays pending while something of it waits. */
	private static void giveBackPart(MinecraftServer server, String id) {
		Back back = GIVE_BACKS.get(id).apply(server);
		if (back.waiting() > 0) {
			PENDING.add(id);
		} else {
			PENDING.remove(id);
		}
	}

	/** Every {@value #RETRY_TICKS} ticks while something waits: tried again while the layer is off, forgotten once it is on. */
	static void retry(MinecraftServer server) {
		if (SelfCreated.enabled()) {
			PENDING.clear();
			return;
		}
		for (String id : List.copyOf(PENDING)) {
			giveBackPart(server, id);
		}
	}

	/** Whether something of 08, 09 or 10 waits to be given back (debug and tests). */
	public static boolean pending() {
		return !PENDING.isEmpty();
	}

	/**
	 * 08's give-back: turns back, newest first, every turned block of these entries (CONVERT) that is loaded and out of
	 * view, then both the turn's entry and the reverse one leave the ledger, so nothing of it is left to give back and
	 * running this again does nothing more. An entry that keeps the block the turn left goes through core's undo (SC-5:
	 * only while the block is still exactly that; anything else there now is the player's); one from before core kept it
	 * is turned back while it is still that block with only its facing changed. Returns how many were turned back.
	 */
	public static int turnBack(MinecraftServer server, Predicate<TraceLedger.Entry> which) {
		return turnBackAll(server, which).done();
	}

	/** {@link #turnBack}, with how many wait: in view, or not loaded now. */
	public static Back turnBackAll(MinecraftServer server, Predicate<TraceLedger.Entry> which) {
		TraceLedger ledger = TraceLedger.get(server);
		List<TraceLedger.Entry> mine = ledger.entries().stream().filter(e -> e.kind() == TraceLedger.Kind.CONVERT && which.test(e)).toList();
		String back = Turned.CAUSE + "/back";
		int done = 0;
		int waiting = 0;
		for (int i = mine.size() - 1; i >= 0; i--) {
			TraceLedger.Entry entry = mine.get(i);
			ServerLevel level = server.getLevel(entry.pos().dimension());
			BlockPos pos = entry.pos().pos();
			BlockState old = entry.state().orElse(null);
			if (level == null || old == null) {
				continue;
			}
			if (!level.isLoaded(pos)) {
				waiting++;
				continue;
			}
			if (entry.after().isPresent()) {
				TraceService.UndoResult result = Services.traces().undo(level, entry, back);
				if (result == TraceService.UndoResult.DONE) {
					done++;
				} else if (result != TraceService.UndoResult.BLOCKED) {
					waiting++;
				}
				continue;
			}
			BlockState now = level.getBlockState(pos);
			if (now == old) {
				ledger.remove(entry);
				done++;
				continue;
			}
			if (!Turned.onlyFacingDiffers(now, old)) {
				continue;
			}
			int before = ledger.entries().size();
			if (!Services.traces().convert(level, pos, old, back)) {
				waiting++;
				continue;
			}
			List<TraceLedger.Entry> written = ledger.entries();
			for (int j = written.size() - 1; j >= before; j--) {
				if (written.get(j).cause().equals(back)) {
					ledger.remove(written.get(j));
				}
			}
			ledger.remove(entry);
			done++;
		}
		return new Back(done, waiting);
	}

	/**
	 * Gives back, newest first, every ledger entry with this cause (or one of its dependents) whose block is loaded now,
	 * through {@link TraceService#undo} (out of view only, exactly once: an entry undone leaves the ledger, so running
	 * this again does nothing more). A pass that gave something back while something else was blocked is followed by
	 * another (review R7: a tall plant's upper half stands only once its lower half is back). Returns how many were given
	 * back.
	 */
	public static int giveBack(MinecraftServer server, String cause) {
		return giveBackAll(server, cause).done();
	}

	/** {@link #giveBack}, with how many wait: in view, or not loaded now. */
	public static Back giveBackAll(MinecraftServer server, String cause) {
		TraceLedger ledger = TraceLedger.get(server);
		int done = 0;
		int waiting = 0;
		for (int pass = 0; pass < MAX_PASSES; pass++) {
			List<TraceLedger.Entry> mine = ledger.entries().stream()
					.filter(e -> e.cause().equals(cause) || e.cause().startsWith(cause + "/")).toList();
			int given = 0;
			int blocked = 0;
			waiting = 0;
			for (int i = mine.size() - 1; i >= 0; i--) {
				TraceLedger.Entry entry = mine.get(i);
				ServerLevel level = server.getLevel(entry.pos().dimension());
				if (level == null) {
					continue;
				}
				if (!level.isLoaded(entry.pos().pos())) {
					waiting++;
					continue;
				}
				switch (Services.traces().undo(level, entry)) {
					case DONE -> given++;
					case BLOCKED -> blocked++;
					case IN_VIEW, UNLOADED -> waiting++;
				}
			}
			done += given;
			if (given == 0 || blocked == 0) {
				break;
			}
		}
		return new Back(done, waiting);
	}
}
