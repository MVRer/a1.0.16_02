package com.forzacode.a1016_02.entity;

/**
 * The pure rules of the fog report (D-035), kept free of client classes so the game tests can check them: when the
 * client sends, what the server accepts, and which fog end the spawn band uses.
 */
public final class FogReport {
	/** The client sends at least this often (half a second)... */
	public static final int HEARTBEAT_TICKS = 10;
	/** ...and at once when the fog end moved by more than this many blocks. */
	public static final float CHANGE_BLOCKS = 2.0F;
	/** Reports outside this range are not fog ends. */
	public static final double MIN_BLOCKS = 1.0;
	public static final double MAX_BLOCKS = 4096.0;

	private FogReport() {
	}

	/** Where the fog is complete: the nearer of the environmental and the render-distance fog end. NaN if neither is usable. */
	public static float visibleEnd(float environmentalEnd, float renderDistanceEnd) {
		float a = usable(environmentalEnd) ? environmentalEnd : Float.NaN;
		float b = usable(renderDistanceEnd) ? renderDistanceEnd : Float.NaN;
		if (Float.isNaN(a)) {
			return b;
		}
		return Float.isNaN(b) ? a : Math.min(a, b);
	}

	private static boolean usable(float value) {
		return Float.isFinite(value) && value > 0.0F;
	}

	/**
	 * Client: send now? Always the first time, then every {@link #HEARTBEAT_TICKS}, and at once on a change of more than
	 * {@link #CHANGE_BLOCKS}. Never a value that is not a fog end.
	 *
	 * @param lastSent       last value sent, NaN if none since joining
	 * @param ticksSinceSent client ticks since it was sent
	 */
	public static boolean shouldSend(float lastSent, float now, int ticksSinceSent) {
		if (!usable(now)) {
			return false;
		}
		return Float.isNaN(lastSent) || ticksSinceSent >= HEARTBEAT_TICKS || Math.abs(now - lastSent) > CHANGE_BLOCKS;
	}

	/** Server: the reported value as a fog end in blocks, or NaN if it is not one. */
	public static double accept(float blocks) {
		if (!Float.isFinite(blocks) || blocks < MIN_BLOCKS) {
			return Double.NaN;
		}
		return Math.min(blocks, MAX_BLOCKS);
	}

	/**
	 * Server: the client's report as the band's fog end while it is fresh, never past the render distance the server
	 * allows. NaN when there is none yet, or it is too old or from an earlier server run: the band then falls back to
	 * the server's estimate ({@link FogEdge}).
	 *
	 * @param reported    the latest report, NaN if none
	 * @param ageTicks    server ticks since it arrived
	 * @param maxAgeTicks older than this is stale
	 * @param renderLimit the render-distance fog end the server allows this player
	 */
	public static double fresh(double reported, long ageTicks, long maxAgeTicks, double renderLimit) {
		if (Double.isNaN(reported) || ageTicks < 0 || ageTicks > maxAgeTicks) {
			return Double.NaN;
		}
		return Math.min(reported, renderLimit);
	}
}
