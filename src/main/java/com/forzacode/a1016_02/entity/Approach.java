package com.forzacode.a1016_02.entity;

/**
 * How far one player has closed on him since he was first seen. Fed the horizontal distance every tick.
 *
 * <p>Distance only counts in runs of at least {@code minStep} toward him: a single step, shuffling on the spot or
 * stepping forward and back never adds up. Backing off moves the reference out again without taking anything back,
 * so walking in, retreating and walking in again counts both walks. Strafing past him only grows the distance and
 * turning does not change it, so neither counts.
 */
public final class Approach {
	private double reference = Double.NaN;
	private double closed;

	/** Takes the current distance; returns the distance closed so far. */
	public double update(double distance, double minStep) {
		if (Double.isNaN(reference) || distance > reference) {
			reference = distance;
		} else if (reference - distance >= minStep) {
			closed += reference - distance;
			reference = distance;
		}
		return closed;
	}

	public double closed() {
		return closed;
	}
}
