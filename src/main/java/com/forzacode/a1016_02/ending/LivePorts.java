package com.forzacode.a1016_02.ending;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.accident.AccidentInit;
import com.forzacode.a1016_02.accident.AccidentPlannerImpl;
import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.TrapKind;
import com.forzacode.a1016_02.accident.Traps;
import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.core.TrapType;
import com.forzacode.a1016_02.entity.FigureApi;
import com.forzacode.a1016_02.lore.LoreApi;
import com.forzacode.a1016_02.world.HouseCopyApi;
import com.forzacode.a1016_02.world.sig.HouseCopyState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/** {@link EndingPorts} on the real services. Server thread only. */
final class LivePorts implements EndingPorts {
	/** The figure's Ending A card ({@code entity.Variant.LAST_ONE}). */
	static final String LAST_SIGHTING_CARD = "sighting_last_one";

	@Override
	public FireResult lastSighting(ServerPlayer player) {
		Optional<EventCard> card = CardRegistry.get(LAST_SIGHTING_CARD);
		if (card.isEmpty() || !card.get().contextFits(player, player.level())) {
			return FireResult.SKIPPED;
		}
		return card.get().fire(new FireContext(player, player.level(), false, false, RandomSource.create()));
	}

	@Override
	public boolean figureOut(MinecraftServer server) {
		return FigureApi.anyOut(server);
	}

	@Override
	public boolean armTrap(ServerPlayer player, String trapId) {
		return Services.accidents().arm(player, new TrapType(trapId));
	}

	@Override
	public boolean armTrapInside(ServerPlayer player, String trapId, Bounds bounds) {
		MinecraftServer server = player.level().getServer();
		AccidentPlannerImpl planner = AccidentInit.planner();
		Optional<TrapKind> kind = Traps.byId(trapId);
		if (!player.level().dimension().equals(bounds.dimension()) || kind.isEmpty() || kind.get().blocked() != null || !planner.canArm(server)) {
			return false;
		}
		// The planner looks around the middle of the copy and sets the first of its nearest spots that works: every
		// one it may try must be inside.
		BlockPos center = bounds.box().getCenter();
		List<Candidate> candidates = planner.candidates(player, kind.get(), center);
		int tries = Math.min(candidates.size(), AccidentConfig.get().maxSetupTries);
		if (tries == 0) {
			return false;
		}
		for (Candidate candidate : candidates.subList(0, tries)) {
			if (!bounds.contains(candidate.dimension != null ? candidate.dimension : player.level().dimension(), candidate.pos)) {
				return false;
			}
		}
		if (!Services.accidents().arm(player, new TrapType(trapId), center)) {
			return false;
		}
		Optional<ArmedTrap> armed = planner.armedTrap(server);
		if (armed.isEmpty() || !bounds.contains(armed.get().dimension(), armed.get().pos())) {
			planner.disarm(server, "not inside the copy");
			return false;
		}
		return true;
	}

	@Override
	public boolean trapArmed() {
		return Services.accidents().armed().isPresent();
	}

	@Override
	public Optional<EndingState.Trap> armedTrap(MinecraftServer server) {
		return AccidentInit.planner().armedTrap(server).map(t -> new EndingState.Trap(t.type(), GlobalPos.of(t.dimension(), t.pos())));
	}

	@Override
	public long ticksSinceJoin(ServerPlayer player) {
		return Services.watch().ticksSinceJoin(player);
	}

	@Override
	public long ticksSinceDirectorAccident(MinecraftServer server) {
		return Services.director().ticksSinceTag(server, CardTag.ACCIDENT);
	}

	@Override
	public boolean directorQuiet(MinecraftServer server) {
		return Services.director().inQuiet(server);
	}

	@Override
	public void disarmTraps() {
		Services.accidents().disarm();
	}

	@Override
	public boolean copyExists(MinecraftServer server) {
		return HouseCopyApi.exists(server);
	}

	@Override
	public boolean finishCopy(MinecraftServer server) {
		return HouseCopyApi.finish(server);
	}

	@Override
	public boolean cancelCopyFinish(MinecraftServer server) {
		return HouseCopyApi.cancelFinish(server);
	}

	@Override
	public boolean copyFinished(MinecraftServer server) {
		return HouseCopyApi.finished(server);
	}

	@Override
	public Optional<GlobalPos> copySite(MinecraftServer server) {
		return HouseCopyApi.site(server);
	}

	@Override
	public Optional<Bounds> copyBounds(MinecraftServer server) {
		Optional<GlobalPos> site = HouseCopyApi.site(server);
		if (site.isEmpty()) {
			return Optional.empty();
		}
		return Bounds.around(site.get().dimension(), HouseCopyApi.moved(server).stream().map(HouseCopyState.Moved::to).toList());
	}

	@Override
	public boolean stopSignExists(MinecraftServer server) {
		return LoreApi.stopSign(server).isPresent();
	}

	@Override
	public boolean moveStopSignToCross(MinecraftServer server, GlobalPos cross) {
		return LoreApi.moveStopSignToCross(server, cross);
	}

	@Override
	public Optional<GlobalPos> findCross(MinecraftServer server, GlobalPos death) {
		// Accident knows which marked death its newest cross stands for: no search radius of our own.
		return Services.deaths().crossFor(server, death);
	}

	@Override
	public boolean finishF10(MinecraftServer server) {
		return LoreApi.finishF10(server);
	}

	@Override
	public boolean placeF20(MinecraftServer server) {
		return LoreApi.placeF20(server);
	}

	@Override
	public void setDuskFog(MinecraftServer server, float level) {
		ClientEffects.setDuskFog(server, level);
	}

	@Override
	public float stageDuskFog(Stage stage) {
		return AtmosphereConfig.get().duskFogFor(stage);
	}

	@Override
	public TraceService traces() {
		return Services.traces();
	}

	@Override
	public MobTamper mobs() {
		return Services.mobs();
	}

	@Override
	public PlayerWatch watch() {
		return Services.watch();
	}
}
