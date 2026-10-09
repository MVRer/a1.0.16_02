package com.forzacode.a1016_02.atmosphere;

/**
 * The dusk fog level the client draws. The first level after joining applies at once, so a player who joins at night
 * finds the fog already there (no fade-in from zero). Every later change (a stage change, an ending,
 * {@code /a1016 atmosphere fog dusk}) eases there over {@code duskLevelChangeSeconds} with a smoothstep, starting from
 * wherever the fog is at that moment, so a change in the middle of another one never jumps.
 *
 * <p>Alongside the level it eases the haze presence, {@code level / duskHazeFullLevel} (at most 1), which scales the
 * close-in haze ({@link Curves#applyDusk}): every stage level has all of it, and fog that arrives from level 0 or
 * leaves for it brings or takes its haze over the same time instead of switching it at once.
 *
 * <p>Pure (no client classes) so the game tests can check it. Times are in client ticks, any origin.
 */
public final class DuskLevel {
	private boolean known;
	private double fromLevel;
	private double toLevel;
	private double fromHaze;
	private double toHaze;
	private double start;
	private double length;

	/**
	 * A new dusk level from the server (the {@code DuskFog} or join {@code Sync} payload).
	 *
	 * @param level         the new level, 0 to 1
	 * @param hazeFullLevel the level from which the close-in haze is whole
	 * @param now           client ticks now
	 * @param lengthTicks   how long a change takes (the first level after joining applies at once)
	 */
	public void set(double level, double hazeFullLevel, double now, double lengthTicks) {
		double target = Curves.clamp01(level);
		double targetHaze = hazeFullLevel > 0.0 ? Curves.clamp01(target / hazeFullLevel) : target > 0.0 ? 1.0 : 0.0;
		if (!known) {
			known = true;
			fromLevel = target;
			toLevel = target;
			fromHaze = targetHaze;
			toHaze = targetHaze;
			start = now;
			length = 0.0;
			return;
		}
		if (target == toLevel && targetHaze == toHaze) {
			// The same level again (a re-send): a change still easing keeps going.
			return;
		}
		fromLevel = level(now);
		fromHaze = haze(now);
		toLevel = target;
		toHaze = targetHaze;
		start = now;
		length = Math.max(0.0, lengthTicks);
	}

	/** The level to draw now, 0 to 1. */
	public double level(double now) {
		double p = progress(now);
		return p >= 1.0 ? toLevel : Curves.lerp(p, fromLevel, toLevel);
	}

	/** The haze presence to draw now, 0 to 1. */
	public double haze(double now) {
		double p = progress(now);
		return p >= 1.0 ? toHaze : Curves.lerp(p, fromHaze, toHaze);
	}

	/** The level it is easing to (or at). */
	public double target() {
		return toLevel;
	}

	/** False until the first level after joining. */
	public boolean known() {
		return known;
	}

	/** Back to no fog, and the next level applies at once (disconnect). */
	public void reset() {
		known = false;
		fromLevel = 0.0;
		toLevel = 0.0;
		fromHaze = 0.0;
		toHaze = 0.0;
		start = 0.0;
		length = 0.0;
	}

	private double progress(double now) {
		if (length <= 0.0) {
			return 1.0;
		}
		return Curves.smooth((now - start) / length);
	}
}
