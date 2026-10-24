package com.forzacode.a1016_02.world.sc2.s20;

import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc20} (docs/SELF_CREATED_2.md, 20): the numbers of the remains kind {@code s20} and of 20b. Version 1,
 * keys opaque from the start (D-103: no old keys). How often one is tried lives in the structure set's JSON. Read on the
 * server thread; worldgen reads it through the kit's snapshot. A later default change adds a step to {@link #upgrade}
 * with {@code ModConfig.Upgrades.keepOrNew} and raises {@link #CURRENT}.
 */
public final class Sc20Config implements ModConfig.Versioned, Sc2Kind.Gates {
	public static final String SECTION = "sc20";
	public static final int CURRENT = 2;

	public int version = 0;
	/** The lowest stage whose new land may hold one (1, Traces). */
	@SerializedName("a")
	public int fromStage = 1;
	/** No start nearer than this to (0, 0) or to the world spawn (blocks; 900 in version 1, R10-3). */
	@SerializedName("b")
	public int minOriginDistance = 700;
	/** The square's side (blocks; whole chunks, 32 to 128). */
	@SerializedName("c")
	public int side = 96;
	/** 20b: a player this near the little building in the middle (blocks). */
	@SerializedName("d")
	public int faceRadius = 32;
	/** 20b: how long they all face it (seconds). */
	@SerializedName("e")
	public double faceSeconds = 20;

	public static Sc20Config get() {
		return ModConfig.section(SECTION, Sc20Config.class, Sc20Config::new);
	}

	@Override
	public int fromStage() {
		return fromStage;
	}

	@Override
	public int minOriginDistance() {
		return minOriginDistance;
	}

	/** The side as built: whole chunks from 2 to 8 (32 to 128 blocks). */
	public int sideBlocks() {
		return Math.clamp(Math.round(side / 16.0F), 2, 8) * 16;
	}

	@Override
	public boolean upgrade() {
		if (version >= CURRENT) {
			return false;
		}
		if (version >= 1 && version < 2) {
			// 1 to 2 (review R10-3): nearer the origin, where still version 1's.
			minOriginDistance = ModConfig.Upgrades.keepOrNew(minOriginDistance, 900, new Sc20Config().minOriginDistance);
		}
		version = CURRENT;
		return true;
	}
}
