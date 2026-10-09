package com.forzacode.a1016_02.ending;

import java.util.Optional;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.core.CardRegistry;
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
	public boolean trapArmed() {
		return Services.accidents().armed().isPresent();
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
	public boolean copyFinished(MinecraftServer server) {
		return HouseCopyApi.finished(server);
	}

	@Override
	public Optional<GlobalPos> copySite(MinecraftServer server) {
		return HouseCopyApi.site(server);
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
		return CrossFinder.find(TraceService.ledger(server).entries(), death, CrossFinder.SEARCH_RADIUS);
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
	public int tellingCount(MinecraftServer server) {
		return LoreApi.tellingCount(server);
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
