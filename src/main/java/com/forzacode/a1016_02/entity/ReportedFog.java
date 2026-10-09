package com.forzacode.a1016_02.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.level.ServerPlayer;

/**
 * The latest fog end each player's client reported ({@link FogEndPayload}, D-035), with the server tick it arrived.
 * Forgotten when the player leaves or the server stops. Written and read on the server thread.
 */
public final class ReportedFog {
	/** A report, NaN blocks if none. */
	public record Entry(double blocks, int tick) {
	}

	private static final Map<UUID, Entry> LATEST = new ConcurrentHashMap<>();

	private ReportedFog() {
	}

	static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LATEST.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LATEST.clear());
	}

	/** Keeps the report if it is a fog end ({@link FogReport#accept}). */
	static void report(ServerPlayer player, float blocks) {
		double accepted = FogReport.accept(blocks);
		if (!Double.isNaN(accepted)) {
			LATEST.put(player.getUUID(), new Entry(accepted, player.level().getServer().getTickCount()));
		}
	}

	/** The latest report as it arrived, or null. */
	public static Entry latest(ServerPlayer player) {
		return LATEST.get(player.getUUID());
	}

	/** Server ticks since the latest report, or -1 if none. */
	public static long ageTicks(ServerPlayer player) {
		Entry entry = LATEST.get(player.getUUID());
		return entry == null ? -1 : player.level().getServer().getTickCount() - (long) entry.tick();
	}

	/**
	 * The reported fog end the band may use for this player ({@link FogReport#fresh}), or NaN to fall back to the
	 * server's estimate.
	 */
	public static double fresh(ServerPlayer player, double renderLimit) {
		Entry entry = LATEST.get(player.getUUID());
		if (entry == null) {
			return Double.NaN;
		}
		// Plain ticks, not ModConfig.realTicks: this matches the client's send rate, which devFastMode does not speed up.
		long maxAge = Math.round(EntityConfig.get().fogReportMaxAgeSeconds * 20.0);
		return FogReport.fresh(entry.blocks(), ageTicks(player), maxAge, renderLimit);
	}

	/** Tests: sets or (with NaN) forgets a player's report as if it arrived now. */
	static void set(ServerPlayer player, double blocks) {
		if (Double.isNaN(blocks)) {
			LATEST.remove(player.getUUID());
		} else {
			LATEST.put(player.getUUID(), new Entry(blocks, player.level().getServer().getTickCount()));
		}
	}
}
