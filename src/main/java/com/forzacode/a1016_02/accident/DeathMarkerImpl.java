package com.forzacode.a1016_02.accident;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.DeathMarker;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.MarkedDeath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * The real {@link DeathMarker}. A marked death is written to {@link HerobrineState} at once as the dead player's own
 * (D-063: their cross, their cause on their line), with its list word, and fires {@link HerobrineEvents#MARKED_DEATH}
 * with that player; its cross waits until the spot is out of view (the dead player, a spectator in hardcore, the
 * respawned player or a friend may still be watching) and is then built from blocks taken around the spot. Two deaths
 * in one tick each get their own cross: crosses are built one after the other, and none takes from or touches a cross
 * already standing. Ordinary deaths never reach here.
 */
public final class DeathMarkerImpl implements DeathMarker {
	private final Function<MinecraftServer, AccidentData> data;
	private final ViewGate view;
	private final Function<MinecraftServer, HerobrineState> state;

	public DeathMarkerImpl(Function<MinecraftServer, AccidentData> data, ViewGate view) {
		this(data, view, HerobrineState::get);
	}

	/** A marker over its own story state (tests). */
	public DeathMarkerImpl(Function<MinecraftServer, AccidentData> data, ViewGate view, Function<MinecraftServer, HerobrineState> state) {
		this.data = data;
		this.view = view;
		this.state = state;
	}

	@Override
	public void mark(ServerPlayer player, String cause, BlockPos pos) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		AccidentConfig cfg = AccidentConfig.get();
		GlobalPos at = GlobalPos.of(level.dimension(), pos.immutable());
		long day = GameClock.day(server);
		UUID victim = player.getUUID();
		state.apply(server).addMarkedDeath(victim, new MarkedDeath(cause, at, day));
		AccidentData.PendingCross cross = new AccidentData.PendingCross(at, cause, CrossBuilder.height(level.getRandom(), cfg), 0, Optional.of(victim));
		AccidentData d = data.apply(server);
		d.addCross(cross);
		String name = player.getName().getString();
		d.log("day " + day + ": marked death of " + name + " (" + cause + ") at " + Candidate.at(pos));
		A1016_02.LOGGER.info("[a1016] marked death of {} '{}' at {} on day {}", name, cause, pos, day);
		HerobrineEvents.MARKED_DEATH.invoker().onMarkedDeath(player, cause, pos);
		build(server, cross);
	}

	/**
	 * Debug preview: builds the cross for {@code cause} at {@code pos} (or queues it until the spot is out of view)
	 * without recording a marked death or firing {@link HerobrineEvents#MARKED_DEATH}. True if it stands now.
	 */
	public boolean preview(ServerPlayer player, String cause, BlockPos pos) {
		int height = CrossBuilder.height(player.level().getRandom(), AccidentConfig.get());
		AccidentData.PendingCross cross = new AccidentData.PendingCross(GlobalPos.of(player.level().dimension(), pos.immutable()), cause, height, 0);
		data.apply(player.level().getServer()).addCross(cross);
		return build(player.level().getServer(), cross);
	}

	/** The line the list would get for this death: the dead player's own. */
	public static String listLine(ServerPlayer player, String cause, long day) {
		return player.getName().getString() + " - " + cause + " (day " + day + ")";
	}

	@Override
	public int count(MinecraftServer server) {
		return state.apply(server).markedDeaths().size();
	}

	@Override
	public int count(MinecraftServer server, UUID player) {
		return state.apply(server).markedDeaths(player).size();
	}

	@Override
	public Optional<GlobalPos> lastCrossPos(MinecraftServer server) {
		return data.apply(server).lastCross();
	}

	/** The cross built for the marked death at exactly {@code death}, whoever's it was and however many came since. */
	@Override
	public Optional<GlobalPos> crossFor(MinecraftServer server, GlobalPos death) {
		return data.apply(server).crossFor(death);
	}

	/** Tries every waiting cross, oldest first (each sees the ones built before it). */
	public void tick(MinecraftServer server) {
		for (AccidentData.PendingCross cross : List.copyOf(data.apply(server).crosses())) {
			build(server, cross);
		}
	}

	/** Builds one waiting cross if its spot can be planned and is out of view. True when it stands. */
	public boolean build(MinecraftServer server, AccidentData.PendingCross cross) {
		AccidentData d = data.apply(server);
		ServerLevel level = server.getLevel(cross.pos().dimension());
		if (level == null) {
			d.replaceCross(cross, null);
			return false;
		}
		BlockPos pos = cross.pos().pos();
		if (!level.isLoaded(pos)) {
			if (!level.players().isEmpty()) {
				return false;
			}
			// Nobody is in this level: load the spot (it existed when they died) and build unseen.
			ChunkPos chunk = ChunkPos.containing(pos);
			level.getChunk(chunk.x(), chunk.z());
		}
		CrossBuilder.Plan plan = CrossBuilder.plan(level, pos, cross.height(), AccidentConfig.get(), d.crossCells(level.dimension())).orElse(null);
		if (plan == null) {
			AccidentData.PendingCross next = cross.retried();
			if (next.attempts() >= AccidentConfig.get().crossMaxPlanFailures()) {
				d.log("cross at " + Candidate.at(pos) + " given up: nowhere to stand it");
				d.replaceCross(cross, null);
			} else {
				d.replaceCross(cross, next);
			}
			return false;
		}
		if (!TraceOp.apply(level, view, "accident:cross", plan.ops())) {
			return false;
		}
		d.replaceCross(cross, null);
		GlobalPos base = GlobalPos.of(level.dimension(), plan.base().immutable());
		// A debug preview is not a marked death: it is remembered (so no later cross takes it apart) but stands for nobody.
		boolean marked = state.apply(server).markedDeaths().stream()
				.anyMatch(m -> m.pos().equals(cross.pos()) && cross.player().map(p -> p.equals(m.player())).orElse(true));
		d.addBuilt(new AccidentData.BuiltCross(cross.pos(), base, Set.copyOf(plan.cells()), cross.player(), marked));
		if (marked) {
			d.setLastCross(base, cross.pos());
		}
		d.log("cross (" + cross.cause() + ") stands at " + Candidate.at(plan.base()) + ", " + plan.height() + " tall");
		A1016_02.LOGGER.info("[a1016] cross for '{}' built at {}, {} tall (asked {})", cross.cause(), plan.base(), plan.height(), cross.height());
		return true;
	}
}
