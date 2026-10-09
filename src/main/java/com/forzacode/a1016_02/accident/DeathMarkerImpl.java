package com.forzacode.a1016_02.accident;

import java.util.List;
import java.util.Optional;
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
 * The real {@link DeathMarker}. A marked death is written to {@link HerobrineState} with its list word at once and
 * fires {@link HerobrineEvents#MARKED_DEATH}; its cross waits until the spot is out of view (the dead player, a
 * spectator in hardcore, or the respawned player may still be watching) and is then built from blocks taken around
 * the spot. Ordinary deaths never reach here.
 */
public final class DeathMarkerImpl implements DeathMarker {
	private final Function<MinecraftServer, AccidentData> data;
	private final ViewGate view;

	public DeathMarkerImpl(Function<MinecraftServer, AccidentData> data, ViewGate view) {
		this.data = data;
		this.view = view;
	}

	@Override
	public void mark(ServerPlayer player, String cause, BlockPos pos) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		AccidentConfig cfg = AccidentConfig.get();
		GlobalPos at = GlobalPos.of(level.dimension(), pos.immutable());
		long day = GameClock.day(server);
		HerobrineState.get(server).addMarkedDeath(new MarkedDeath(cause, at, day));
		int span = Math.max(0, cfg.crossMaxHeight - cfg.crossMinHeight);
		int height = cfg.crossMinHeight + (span == 0 ? 0 : level.getRandom().nextInt(span + 1));
		AccidentData.PendingCross cross = new AccidentData.PendingCross(at, cause, height, 0);
		AccidentData d = data.apply(server);
		d.addCross(cross);
		d.log("day " + day + ": marked death (" + cause + ") at " + Candidate.at(pos));
		A1016_02.LOGGER.info("[a1016] marked death '{}' at {} on day {}", cause, pos, day);
		HerobrineEvents.MARKED_DEATH.invoker().onMarkedDeath(player, cause, pos);
		build(server, cross);
	}

	/**
	 * Debug preview: builds the cross for {@code cause} at {@code pos} (or queues it until the spot is out of view)
	 * without recording a marked death or firing {@link HerobrineEvents#MARKED_DEATH}. True if it stands now.
	 */
	public boolean preview(ServerPlayer player, String cause, BlockPos pos) {
		AccidentConfig cfg = AccidentConfig.get();
		int span = Math.max(0, cfg.crossMaxHeight - cfg.crossMinHeight);
		int height = cfg.crossMinHeight + (span == 0 ? 0 : player.level().getRandom().nextInt(span + 1));
		AccidentData.PendingCross cross = new AccidentData.PendingCross(GlobalPos.of(player.level().dimension(), pos.immutable()), cause, height, 0);
		data.apply(player.level().getServer()).addCross(cross);
		return build(player.level().getServer(), cross);
	}

	/** The line the list would get for this death. */
	public static String listLine(ServerPlayer player, String cause, long day) {
		return player.getName().getString() + " - " + cause + " (day " + day + ")";
	}

	@Override
	public int count(MinecraftServer server) {
		return HerobrineState.get(server).markedDeaths().size();
	}

	@Override
	public Optional<GlobalPos> lastCrossPos(MinecraftServer server) {
		return data.apply(server).lastCross();
	}

	@Override
	public Optional<GlobalPos> crossFor(MinecraftServer server, GlobalPos death) {
		AccidentData d = data.apply(server);
		return d.lastCrossDeath().filter(death::equals).flatMap(at -> d.lastCross());
	}

	/** Tries every waiting cross. */
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
		CrossBuilder.Plan plan = CrossBuilder.plan(level, pos, cross.height(), AccidentConfig.get()).orElse(null);
		if (plan == null) {
			AccidentData.PendingCross next = new AccidentData.PendingCross(cross.pos(), cross.cause(), cross.height(), cross.attempts() + 1);
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
		if (HerobrineState.get(server).markedDeaths().stream().anyMatch(m -> m.pos().equals(cross.pos()))) {
			d.setLastCross(GlobalPos.of(level.dimension(), plan.base().immutable()), cross.pos()); // a debug preview is not a marked death
		}
		d.log("cross (" + cross.cause() + ") stands at " + Candidate.at(plan.base()));
		A1016_02.LOGGER.info("[a1016] cross for '{}' built at {}", cross.cause(), plan.base());
		return true;
	}
}
