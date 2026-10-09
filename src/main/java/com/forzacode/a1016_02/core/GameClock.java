package com.forzacode.a1016_02.core;

import net.minecraft.server.MinecraftServer;

/**
 * The one clock everyone shares (D-007). Play time counts server ticks while the subject is online (the
 * integrated server pauses with the menu, so paused time does not count), plus timewarp.
 */
public final class GameClock {
	public static final long TICKS_PER_DAY = 24000L;

	private GameClock() {
	}

	/** Real play ticks so far, plus timewarp. */
	public static long playTicks(MinecraftServer server) {
		return HerobrineState.get(server).playTicks();
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

	/** Advances the clock by whole in-game days: adds the days and one day's worth of play ticks per day. */
	public static void warp(MinecraftServer server, int days) {
		HerobrineState state = HerobrineState.get(server);
		state.addWarpDays(days);
		state.addPlayTicks(days * TICKS_PER_DAY);
	}

	/** Called every server tick by core. */
	static void tick(MinecraftServer server, boolean subjectOnline) {
		if (subjectOnline) {
			HerobrineState.get(server).addPlayTicks(1);
		}
	}
}
