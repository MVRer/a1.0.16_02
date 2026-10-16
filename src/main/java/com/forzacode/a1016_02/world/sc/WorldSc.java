package com.forzacode.a1016_02.world.sc;

import java.util.List;
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
 * puts back when the layer is switched off.
 */
public final class WorldSc {
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
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WhereHeStood.LIVE.clear());
		ScCommands.register();

		// Switched off: what is his from these parts is given back where it can be now (loaded, out of view; never a
		// chunk loaded), the rest stays a trace like any other (Ending D still gives it back; grass grows over prints).
		SelfCreated.onSwitchedOff("sc08", server -> turnBack(server, e -> e.cause().equals(Turned.CAUSE)));
		SelfCreated.onSwitchedOff("sc09", server -> giveBack(server, Tracks.CAUSE));
		SelfCreated.onSwitchedOff("sc10", server -> {
			WhereHeStood.LIVE.clear();
			giveBack(server, WhereHeStood.CAUSE);
			// Its fog places are core's: core's own revert clears them.
		});
		// 11 is left by others (D-088): never undone, and nothing of it is metadata or UI.
		SelfCreated.onSwitchedOff("sc11", server -> {
		});
	}

	/**
	 * 08's give-back: turns back, newest first, every turned block of these entries (CONVERT) that is loaded, out of view
	 * and still that block with only its facing changed (anything else there now is the player's), by the reverse
	 * conversion; then both the turn's entry and the reverse one leave the ledger, so nothing of it is left to give back
	 * and running this again does nothing more. (Core's undo leaves a block a player placed alone, as built since, and
	 * every turned block is one a player placed.) Returns how many were turned back.
	 */
	public static int turnBack(MinecraftServer server, Predicate<TraceLedger.Entry> which) {
		TraceLedger ledger = TraceLedger.get(server);
		List<TraceLedger.Entry> mine = ledger.entries().stream().filter(e -> e.kind() == TraceLedger.Kind.CONVERT && which.test(e)).toList();
		String back = Turned.CAUSE + "/back";
		int done = 0;
		for (int i = mine.size() - 1; i >= 0; i--) {
			TraceLedger.Entry entry = mine.get(i);
			ServerLevel level = server.getLevel(entry.pos().dimension());
			BlockPos pos = entry.pos().pos();
			BlockState old = entry.state().orElse(null);
			if (level == null || old == null || !level.isLoaded(pos)) {
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
		return done;
	}

	/**
	 * Gives back, newest first, every ledger entry with this cause (or one of its dependents) whose block is loaded now,
	 * through {@link TraceService#undo} (out of view only, exactly once: an entry undone leaves the ledger, so running
	 * this again does nothing more). Returns how many were given back.
	 */
	public static int giveBack(MinecraftServer server, String cause) {
		List<TraceLedger.Entry> mine = TraceLedger.get(server).entries().stream()
				.filter(e -> e.cause().equals(cause) || e.cause().startsWith(cause + "/")).toList();
		int done = 0;
		for (int i = mine.size() - 1; i >= 0; i--) {
			TraceLedger.Entry entry = mine.get(i);
			ServerLevel level = server.getLevel(entry.pos().dimension());
			if (level == null || !level.isLoaded(entry.pos().pos())) {
				continue;
			}
			if (Services.traces().undo(level, entry) == TraceService.UndoResult.DONE) {
				done++;
			}
		}
		return done;
	}
}
