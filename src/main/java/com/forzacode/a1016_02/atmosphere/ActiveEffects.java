package com.forzacode.a1016_02.atmosphere;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.GameClock;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends the transient client effects (fog surge, silence, compass drift) and remembers them, so a player who relogs
 * while one is running gets the rest of it. Silence and compass drift are kept in {@link AtmosphereData} in play
 * ticks, so they also carry on after a singleplayer world reload; a fog surge (seconds long) only in memory.
 * Persistent effects (music off, dusk fog) are core's {@code Sync}. Server thread only.
 */
public final class ActiveEffects {
	private record Surge(long start, ClientEffects.FogSurge effect) {
	}

	private static final Map<UUID, Surge> SURGES = new HashMap<>();

	private ActiveEffects() {
	}

	/** Sends a fog surge (and remembers it for a relog). False, and nothing sent, once the world is quiet for good. */
	public static boolean fogSurge(ServerPlayer player, float strength, int rampTicks, int holdTicks, int fadeTicks) {
		if (Gates.quietForGood(player.level().getServer())) {
			SURGES.remove(player.getUUID());
			return false;
		}
		ClientEffects.fogSurge(player, strength, rampTicks, holdTicks, fadeTicks);
		SURGES.put(player.getUUID(), new Surge(player.level().getServer().getTickCount(), new ClientEffects.FogSurge(strength, rampTicks, holdTicks, fadeTicks)));
		return true;
	}

	public static void silence(ServerPlayer player, int ticks, int fadeTicks) {
		ClientEffects.silence(player, ticks, fadeTicks);
		MinecraftServer server = player.level().getServer();
		AtmosphereData.get(server).setSilence(player.getUUID(), GameClock.playTicks(server), ticks, fadeTicks);
	}

	/** Sends a compass drift (and remembers it). False, and nothing sent, once the world is quiet for good. */
	public static boolean compassDrift(ServerPlayer player, int x, int z, int ticks, int settleBlocks) {
		MinecraftServer server = player.level().getServer();
		if (Gates.quietForGood(server)) {
			return false;
		}
		CompassDriftPayload.send(player, new CompassDriftPayload(x, z, Math.max(0, ticks), settleBlocks));
		AtmosphereData.get(server).setCompass(player.getUUID(), GameClock.playTicks(server), Math.max(0, ticks), x, z, settleBlocks);
		return true;
	}

	public static boolean compassActive(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		return AtmosphereData.get(server).transientOf(player.getUUID()).map(t -> t.compassLeft(GameClock.playTicks(server)) > 0).orElse(false);
	}

	/** Re-sends whatever is still running. Called on join, after core's {@code Sync}. */
	static void onJoin(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Surge surge = SURGES.remove(player.getUUID());
		if (surge != null) {
			ClientEffects.FogSurge s = surge.effect();
			long holdLeft = s.rampTicks() + s.holdTicks() - (server.getTickCount() - surge.start());
			if (holdLeft > 0) {
				fogSurge(player, s.strength(), Math.min(10, s.rampTicks()), (int) holdLeft, s.fadeTicks());
			}
		}
		Optional<AtmosphereData.Transient> active = AtmosphereData.get(server).transientOf(player.getUUID());
		if (active.isEmpty()) {
			return;
		}
		AtmosphereData.Transient t = active.get();
		long now = GameClock.playTicks(server);
		long silenceLeft = t.silenceLeft(now);
		if (silenceLeft > 0) {
			ClientEffects.silence(player, (int) silenceLeft, t.silenceFade());
		}
		long compassLeft = t.compassLeft(now);
		if (compassLeft > 0 && !Gates.quietForGood(server)) {
			CompassDriftPayload.send(player, new CompassDriftPayload(t.compassX(), t.compassZ(), (int) compassLeft, t.compassSettle()));
		}
	}

	static void clear() {
		SURGES.clear();
	}
}
