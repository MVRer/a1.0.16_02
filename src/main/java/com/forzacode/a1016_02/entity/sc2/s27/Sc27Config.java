package com.forzacode.a1016_02.entity.sc2.s27;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc27} (version 1, keys {@code a} to {@code i}), entity's (docs/SELF_CREATED_2.md, 27): the fit, the spot,
 * the copy's life on the viewer's client and the caps. The server's numbers rule: every number the copy lives by on the
 * client comes in its payload ({@link Sc27Payload}), never from the client's own config.
 */
public final class Sc27Config implements ModConfig.Versioned {
	public static final String SECTION = "sc27";
	public static final int CURRENT = 1;

	public int version = 0;
	/** The other player counts as away at least this far off in the viewer's level (blocks, 3D). */
	@SerializedName("a")
	public double farDistance = 160.0;
	/** ...or at least this far under the surface over them (blocks, from their eyes), with no sky at their eyes. */
	@SerializedName("b")
	public double undergroundDepth = 12.0;
	/** The copy stands no nearer the viewer than this (blocks, horizontal)... */
	@SerializedName("c")
	public double distanceMin = 36.0;
	/** ...and no farther than this (blocks, horizontal; and never past {@link Sc27Rules#NAME_REACH} from the eyes). */
	@SerializedName("d")
	public double distanceMax = 64.0;
	/** The copy's life on the viewer's client, seconds. */
	@SerializedName("e")
	public double lifeSeconds = 25.0;
	/** Looked at this long in all (near the middle of the screen), seconds, it turns and walks off. */
	@SerializedName("f")
	public double lookSeconds = 3.0;
	/** The viewer this near (blocks): it is gone the first moment it is out of their view, else it walks off. */
	@SerializedName("g")
	public double nearLimit = 28.0;
	/** At most once in this many in-game days per viewer. */
	@SerializedName("h")
	public double daysApart = 3.0;
	/** At most this many in the world. */
	@SerializedName("i")
	public int maxPerWorld = 4;

	@Override
	public boolean upgrade() {
		if (version >= CURRENT) {
			return false;
		}
		version = CURRENT;
		return true;
	}

	public static Sc27Config get() {
		return ModConfig.section(SECTION, Sc27Config.class, Sc27Config::new);
	}

	/** {@link #lifeSeconds} in ticks (real ticks: the client counts its own). */
	public int lifeTicks() {
		return (int) Math.max(20L, Math.min(20L * 600, ModConfig.realTicks(Math.max(1.0, lifeSeconds))));
	}

	/** {@link #lookSeconds} in ticks. */
	public int lookTicks() {
		return (int) Math.max(1L, Math.min(20L * 600, ModConfig.realTicks(Math.max(0.05, lookSeconds))));
	}
}
