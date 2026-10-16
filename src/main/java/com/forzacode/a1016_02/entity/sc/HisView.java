package com.forzacode.a1016_02.entity.sc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.entity.EntityData;
import com.forzacode.a1016_02.entity.HimEntity;
import com.forzacode.a1016_02.entity.SightingGates;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/**
 * 03 on the server (D-081): a surface sighting set up for the host of an integrated server, near their base at night or in
 * fog, arms the host's client with that figure ({@link HisViewPayload}); the client renders one frame from his eyes,
 * never presented, and writes it as the world's {@code icon.png}, keeping the old picture once as
 * {@link HisViewRules#BEFORE} ({@code entity.client.HisViewClient}), then reports ({@link HisViewTakenPayload}): the world's
 * flag is set ({@link EntityData#setSc03Done}), once per world. Ending D's afterward ({@code ending:d_complete}), the
 * layer's switch turned off and the debug reset put the old picture back ({@link #restore}, once). Arms live in memory: a
 * sighting that comes to nothing arms the next one. Server thread only. {@link #LIVE} is the game's; tests make their own
 * with their own {@link Hooks}.
 */
public final class HisView {
	public static final String ID = "sc03";
	/** The opaque name its log lines use. */
	static final String LOG_ID = "sc_03";
	/** Ending D's afterward is looked for this often (ticks). */
	public static final int TICK_EVERY = 100;
	static final String D_COMPLETE_FLAG = "ending:d_complete";

	/** What 03 reads and sends, so tests can stand in for the live world. */
	public interface Hooks {
		/** An integrated server (singleplayer or a LAN host): the host has a world list. */
		boolean integrated(MinecraftServer server);

		/** This player is the integrated server's host (its singleplayer owner). */
		boolean isHost(ServerPlayer player);

		EntityData data(MinecraftServer server);

		Stage stage(MinecraftServer server);

		/** The common gates for the host ({@link ScGates#refusal(ServerPlayer)}). */
		Optional<String> commonRefusal(ServerPlayer host);

		/** The host's base in the overworld, or null. */
		@Nullable BlockPos base(ServerPlayer host);

		/** The overworld's time of day (0 to 23999). */
		long timeOfDay(MinecraftServer server);

		/** The world's fog spell counts now. */
		boolean fogCounts(MinecraftServer server);

		boolean send(ServerPlayer player, HisViewPayload payload);

		Optional<ServerPlayer> online(MinecraftServer server, UUID player);

		/** The world's own folder ({@link SelfCreated#worldFolder}). */
		Path worldFolder(MinecraftServer server);

		/** Ending D's afterward ({@code ending:d_complete}). */
		boolean dComplete(MinecraftServer server);
	}

	static final Hooks LIVE_HOOKS = new Hooks() {
		@Override
		public boolean integrated(MinecraftServer server) {
			return !server.isDedicatedServer() && server.isSingleplayer();
		}

		@Override
		public boolean isHost(ServerPlayer player) {
			return player.level().getServer().isSingleplayerOwner(player.nameAndId());
		}

		@Override
		public EntityData data(MinecraftServer server) {
			return EntityData.get(server);
		}

		@Override
		public Stage stage(MinecraftServer server) {
			return HerobrineState.get(server).stage();
		}

		@Override
		public Optional<String> commonRefusal(ServerPlayer host) {
			return ScGates.refusal(host);
		}

		@Override
		public @Nullable BlockPos base(ServerPlayer host) {
			Optional<GlobalPos> base = Services.watch().base(host);
			return base.filter(b -> b.dimension() == Level.OVERWORLD).map(GlobalPos::pos).orElse(null);
		}

		@Override
		public long timeOfDay(MinecraftServer server) {
			return SightingGates.timeOfDay(server);
		}

		@Override
		public boolean fogCounts(MinecraftServer server) {
			return SightingGates.fogWeather(server);
		}

		@Override
		public boolean send(ServerPlayer player, HisViewPayload payload) {
			if (!ServerPlayNetworking.canSend(player, HisViewPayload.TYPE)) {
				return false;
			}
			ServerPlayNetworking.send(player, payload);
			return true;
		}

		@Override
		public Optional<ServerPlayer> online(MinecraftServer server, UUID player) {
			return Optional.ofNullable(server.getPlayerList().getPlayer(player));
		}

		@Override
		public Path worldFolder(MinecraftServer server) {
			return SelfCreated.worldFolder(server);
		}

		@Override
		public boolean dComplete(MinecraftServer server) {
			return HerobrineState.get(server).hasFlag(D_COMPLETE_FLAG);
		}
	};

	/** The game's 03. */
	public static final HisView LIVE = new HisView(LIVE_HOOKS);

	private final Hooks hooks;
	/** Debug: the next sighting skips the stage and the distances. */
	private boolean forceNext;
	/** The host armed last and the figure they were armed with (0: none). */
	private @Nullable UUID armedFor;
	private int armedFigure;
	private @Nullable String lastRefusal;

	public HisView(Hooks hooks) {
		this.hooks = hooks;
	}

	/** Entity: a figure's first tick. A sighting set up for the host arms the host's client if it may ({@link #refusal}). */
	public void onAppeared(ServerLevel level, HimEntity him) {
		UUID appearedFor = him.appearedFor();
		if (appearedFor == null || !SelfCreated.enabled()) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (!hooks.integrated(server)) {
			return; // cheap: never on a dedicated server or the test server
		}
		Optional<ServerPlayer> host = hooks.online(server, appearedFor);
		if (host.isEmpty()) {
			return;
		}
		Optional<String> why = refusal(host.get(), level, him);
		lastRefusal = why.orElse(null);
		if (why.isEmpty()) {
			arm(host.get(), him.getId());
		}
	}

	/** Why this figure does not arm this player's client now ({@link ScGates}, then {@link HisViewRules#armRefusal}), or empty. */
	public Optional<String> refusal(ServerPlayer host, ServerLevel level, HimEntity him) {
		MinecraftServer server = level.getServer();
		Optional<String> common = hooks.commonRefusal(host);
		if (common.isPresent()) {
			return common;
		}
		return HisViewRules.armRefusal(hooks.integrated(server), hooks.isHost(host), hooks.data(server).sc03Done(), hooks.stage(server), him.variant(),
				level.dimension() == Level.OVERWORLD, hooks.base(host), him.position(), host.position(), hooks.timeOfDay(server), hooks.fogCounts(server),
				HisViewConfig.get(), forceNext);
	}

	/** Arms the host's client with this figure (its entity id); false if their client cannot take it. */
	public boolean arm(ServerPlayer host, int figureId) {
		if (!hooks.send(host, new HisViewPayload(figureId))) {
			return false;
		}
		armedFor = host.getUUID();
		armedFigure = figureId;
		forceNext = false;
		A1016_02.LOGGER.info("[a1016] {}: armed{}", LOG_ID, figureId == 0 ? " (none)" : "");
		return true;
	}

	/**
	 * The host's client wrote the picture ({@link HisViewTakenPayload}). Only from the player armed last, the host: the
	 * world's flag is set (once per world). Anything else is ignored. True if it was recorded now.
	 */
	public boolean report(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		EntityData data = hooks.data(server);
		if (armedFor == null || !armedFor.equals(player.getUUID()) || !hooks.isHost(player) || data.sc03Done()) {
			return false;
		}
		data.setSc03Done(true);
		armedFor = null;
		armedFigure = 0;
		A1016_02.LOGGER.info("[a1016] {}: done", LOG_ID);
		return true;
	}

	/** Debug ({@code /a1016 sc 03 arm}): the next sighting skips the stage and the distances. */
	public void forceNext() {
		forceNext = true;
	}

	public boolean forcing() {
		return forceNext;
	}

	/** The figure the host was armed with last (0: none this session). */
	public int armedFigure() {
		return armedFigure;
	}

	/** Why the last sighting set up for the host did not arm (empty if it did, or none came). */
	public Optional<String> lastRefusal() {
		return Optional.ofNullable(lastRefusal);
	}

	/**
	 * Puts the picture from before back as the world's {@code icon.png} ({@link SelfCreated#writeInWorld}), once: only after
	 * the picture was taken, and only while it was not put back yet. Returns what happened, in a few words. Never deletes
	 * anything; a copy that cannot be read leaves the picture as it is.
	 */
	public String restore(MinecraftServer server) {
		EntityData data = hooks.data(server);
		if (!data.sc03Done()) {
			return "not taken";
		}
		if (data.sc03Restored()) {
			return "already put back";
		}
		Path folder = hooks.worldFolder(server);
		Optional<Path> before = SelfCreated.inWorld(folder, HisViewRules.BEFORE);
		if (before.isEmpty() || !Files.isRegularFile(before.get())) {
			return "no picture from before";
		}
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(before.get());
		} catch (IOException e) {
			A1016_02.LOGGER.warn("[a1016] {}: could not read the file from before", LOG_ID, e);
			return "the picture from before could not be read";
		}
		if (!SelfCreated.writeInWorld(folder, HisViewRules.ICON, bytes)) {
			return "the picture could not be written";
		}
		data.setSc03Restored(true);
		A1016_02.LOGGER.info("[a1016] {}: put back", LOG_ID);
		return "put back";
	}

	/**
	 * The host's files (any thread; {@code entity.client.HisViewClient} after its grab): the world's current {@code icon.png}
	 * copied to {@link HisViewRules#BEFORE} only if that copy does not exist yet, then {@code png} written as {@code icon.png};
	 * each whole or not at all, inside the world's folder only ({@link SelfCreated#writeInWorld}). Nothing is written without
	 * an {@code icon.png} to keep (so putting it back never needs a deletion). Empty when done, else why not.
	 */
	public static Optional<String> writePicture(Path folder, byte[] png) {
		Optional<Path> icon = SelfCreated.inWorld(folder, HisViewRules.ICON);
		Optional<Path> before = SelfCreated.inWorld(folder, HisViewRules.BEFORE);
		if (icon.isEmpty() || before.isEmpty() || !Files.isRegularFile(icon.get())) {
			return Optional.of("the world has no picture yet");
		}
		if (!Files.exists(before.get())) {
			byte[] old;
			try {
				old = Files.readAllBytes(icon.get());
			} catch (IOException e) {
				return Optional.of("the picture could not be read");
			}
			if (!SelfCreated.writeInWorld(folder, HisViewRules.BEFORE, old)) {
				return Optional.of("the picture could not be kept");
			}
		}
		if (!SelfCreated.writeInWorld(folder, HisViewRules.ICON, png)) {
			return Optional.of("the picture could not be written");
		}
		return Optional.empty();
	}

	/** Every server tick; every {@link #TICK_EVERY} ticks, Ending D's afterward puts the old picture back (once). */
	public void tick(MinecraftServer server) {
		if (server.getTickCount() % TICK_EVERY == 0) {
			afterward(server);
		}
	}

	/** Ending D's afterward ({@code ending:d_complete}) puts the old picture back, once. True if it did now. */
	public boolean afterward(MinecraftServer server) {
		EntityData data = hooks.data(server);
		return data.sc03Done() && !data.sc03Restored() && hooks.dComplete(server) && "put back".equals(restore(server));
	}

	/** Debug ({@code /a1016 sc 03 reset}): the old picture back, then the world forgets it, so it can happen again. */
	public String reset(MinecraftServer server) {
		String restored = restore(server);
		hooks.data(server).setSc03Done(false);
		forceNext = false;
		armedFor = null;
		armedFigure = 0;
		lastRefusal = null;
		return restored;
	}

	/** Forgets the arm (server stop). */
	public void clear() {
		forceNext = false;
		armedFor = null;
		armedFigure = 0;
		lastRefusal = null;
	}

	/** One line for debug. */
	public String line(MinecraftServer server) {
		EntityData data = hooks.data(server);
		return "03: " + (data.sc03Done() ? "done" + (data.sc03Restored() ? ", put back" : "") : armedFigure != 0 ? "armed" : "waiting")
				+ (forceNext ? ", next sighting forced" : "");
	}
}
