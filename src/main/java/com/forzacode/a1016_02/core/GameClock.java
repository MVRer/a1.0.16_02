package com.forzacode.a1016_02.core;

import java.util.Collection;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The clocks (D-007, D-059). The world clock ({@link #playTicks(MinecraftServer)}) counts server ticks while at
 * least one participant is online; stages move on it, so there is one stage for everyone. Each known player also
 * has a clock of their own ({@link #playTicks(MinecraftServer, UUID)}) that counts only while they take part; the
 * director measures that player's pacing on it. The integrated server pauses with the menu, so paused time does
 * not count. Timewarp adds to every clock.
 */
public final class GameClock {
	public static final long TICKS_PER_DAY = 24000L;

	private GameClock() {
	}

	/** The world clock: real play ticks while at least one participant was online, plus timewarp. */
	public static long playTicks(MinecraftServer server) {
		return HerobrineState.get(server).playTicks();
	}

	/** That player's own play ticks: ticks while they were a participant, plus timewarp since they first took part. */
	public static long playTicks(MinecraftServer server, ServerPlayer player) {
		return playTicks(server, player.getUUID());
	}

	/** That player's own play ticks; 0 for someone who was never a participant. */
	public static long playTicks(MinecraftServer server, UUID player) {
		return HerobrineState.get(server).playTicks(player);
	}

	/** In-game day: overworld day time / 24000 (sleeping moves it forward), plus timewarp days. Day 0 is the first. */
	public static long day(MinecraftServer server) {
		return server.overworld().getOverworldClockTime() / TICKS_PER_DAY + HerobrineState.get(server).warpDays();
	}

	/**
	 * In-game time in ticks: {@link #day} times 24000 plus the overworld time of day (0 is sunrise), so it counts
	 * timewarp days and sleeping. Use it for "in-game days since" math.
	 */
	public static long dayTicks(MinecraftServer server) {
		return day(server) * TICKS_PER_DAY + Math.floorMod(server.overworld().getOverworldClockTime(), TICKS_PER_DAY);
	}

	/**
	 * Advances time by whole in-game days: adds the days, and one day's worth of play ticks per day to the world
	 * clock and to every known player's clock.
	 */
	public static void warp(MinecraftServer server, int days) {
		warp(HerobrineState.get(server), days);
	}

	static void warp(HerobrineState state, int days) {
		state.addWarpDays(days);
		state.addPlayTicks(days * TICKS_PER_DAY);
		state.addPlayTicksToAll(days * TICKS_PER_DAY);
	}

	/** One server tick with these participants online: the world clock and each of their own clocks move by 1. */
	static void advance(HerobrineState state, Collection<UUID> participants) {
		if (participants.isEmpty()) {
			return;
		}
		state.addPlayTicks(1);
		for (UUID player : participants) {
			state.addPlayTicks(player, 1);
		}
	}
}
