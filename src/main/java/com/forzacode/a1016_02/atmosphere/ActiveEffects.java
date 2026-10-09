package com.forzacode.a1016_02.atmosphere;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.forzacode.a1016_02.core.ClientEffects;

import net.minecraft.server.level.ServerPlayer;

/**
 * Sends the transient client effects (fog surge, silence, compass drift) and remembers them per player, so a player
 * who relogs while one is running gets the rest of it. Persistent effects (music off, dusk fog) are core's
 * {@code Sync}. In memory only. Server thread only.
 */
public final class ActiveEffects {
	private static final class Active {
		long surgeStart = Long.MIN_VALUE;
		ClientEffects.FogSurge surge;
		long silenceStart = Long.MIN_VALUE;
		ClientEffects.Silence silence;
		long compassStart = Long.MIN_VALUE;
		CompassDriftPayload compass;
	}

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();

	private ActiveEffects() {
	}

	public static void fogSurge(ServerPlayer player, float strength, int rampTicks, int holdTicks, int fadeTicks) {
		ClientEffects.fogSurge(player, strength, rampTicks, holdTicks, fadeTicks);
		Active active = active(player);
		active.surge = new ClientEffects.FogSurge(strength, rampTicks, holdTicks, fadeTicks);
		active.surgeStart = now(player);
	}

	public static void silence(ServerPlayer player, int ticks, int fadeTicks) {
		ClientEffects.silence(player, ticks, fadeTicks);
		Active active = active(player);
		active.silence = new ClientEffects.Silence(ticks, fadeTicks);
		active.silenceStart = now(player);
	}

	public static void compassDrift(ServerPlayer player, int x, int z, int ticks, int settleBlocks) {
		CompassDriftPayload payload = new CompassDriftPayload(x, z, ticks, settleBlocks);
		CompassDriftPayload.send(player, payload);
		Active active = active(player);
		active.compass = ticks > 0 ? payload : null;
		active.compassStart = now(player);
	}

	public static boolean compassActive(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		return active != null && active.compass != null && now(player) - active.compassStart < active.compass.ticks();
	}

	/** Re-sends whatever is still running. Called on join, after core's {@code Sync}. */
	static void onJoin(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		if (active == null) {
			return;
		}
		long now = now(player);
		if (active.surge != null) {
			ClientEffects.FogSurge s = active.surge;
			long elapsed = now - active.surgeStart;
			long holdLeft = s.rampTicks() + s.holdTicks() - elapsed;
			if (holdLeft > 0) {
				ClientEffects.fogSurge(player, s.strength(), Math.min(10, s.rampTicks()), (int) holdLeft, s.fadeTicks());
			}
		}
		if (active.silence != null) {
			ClientEffects.Silence s = active.silence;
			long left = s.ticks() - (now - active.silenceStart);
			if (left > 0) {
				ClientEffects.silence(player, (int) left, s.fadeTicks());
			}
		}
		if (active.compass != null) {
			CompassDriftPayload c = active.compass;
			long left = c.ticks() - (now - active.compassStart);
			if (left > 0) {
				CompassDriftPayload.send(player, new CompassDriftPayload(c.x(), c.z(), (int) left, c.settleBlocks()));
			}
		}
	}

	static void clear() {
		ACTIVE.clear();
	}

	private static Active active(ServerPlayer player) {
		return ACTIVE.computeIfAbsent(player.getUUID(), k -> new Active());
	}

	private static long now(ServerPlayer player) {
		return player.level().getServer().getTickCount();
	}
}
