package com.forzacode.a1016_02.core;

/**
 * World profile tempo. Scales every stage time by {@link #paceFactor()}: 0.6 / 1.0 / 1.4 with the default
 * {@code Pacing.tempoShift} of 0.4.
 */
public enum Tempo {
	EARLY(-1),
	SLOW_BURN(0),
	VERY_LATE(1);

	private final int direction;

	Tempo(int direction) {
		this.direction = direction;
	}

	/** -1, 0 or +1: which way this tempo shifts the stage times. */
	public int direction() {
		return direction;
	}

	/** Multiplier for stage times, from config ({@link Pacing#paceFactor(Tempo)}). */
	public double paceFactor() {
		return ModConfig.pacing().paceFactor(this);
	}
}
