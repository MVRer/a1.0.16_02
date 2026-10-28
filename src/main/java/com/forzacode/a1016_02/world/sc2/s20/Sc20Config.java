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
	public static final int CURRENT = 4;

	public int version = 0;
	/** The lowest stage whose new land may hold one (0: from the world's creation, D-108). */
	@SerializedName("a")
	public int fromStage = 0;
	/** No start nearer than this to (0, 0) or to the world spawn (blocks; 900 in version 1, R10-3). */
	@SerializedName("b")
	public int minOriginDistance = 416;
	/** The square's side (blocks; whole chunks, 32 to 128). */
	@SerializedName("c")
	public int side = 96;
	/** 20b: a player this near the little building in the middle (blocks). */
	@SerializedName("d")
	public int faceRadius = 32;
	/** 20b: how long they all face it (seconds). */
	@SerializedName("e")
	public double faceSeconds = 20;
	/** The widest square tried first where the land allows (blocks; whole chunks, up to 192; round 3, R3-3). */
	@SerializedName("f")
	public int sideMax = 192;
	/** 20b: the dark reaches one more ring of the floor's chunks in from its edge this often (seconds; version 4, FIX-R3-B: 4 s a chunk before). */
	@SerializedName("g")
	public double darkRingSeconds = 7;
	/** 20b: at most this many of the floor's lights go out a visit (version 4: every light but the chunks a player keeps, about 4 000 on a 192; 400 before). */
	@SerializedName("h")
	public int darkPerVisit = 6000;
	/** 20b: the glimpse in the doorway once the dark has reached this many rings in (version 4; 4 chunks before). */
	@SerializedName("i")
	public int glimpseAfterRings = 2;

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

	/** The widest side as built: whole chunks, even (the square's middle is a chunk corner), from 4 to 12 (64 to 192). */
	public int sideMaxBlocks() {
		return Math.clamp(Math.round(sideMax / 32.0F), 2, 6) * 32;
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
		if (version >= 1 && version < 3) {
			// 2 to 3 (round 3, D-108, D-109): the places from the world's creation, asleep until Traces, and
			// nearer the world spawn, where still version 2's.
			Sc20Config d = new Sc20Config();
			fromStage = ModConfig.Upgrades.keepOrNew(fromStage, 1, d.fromStage);
			minOriginDistance = ModConfig.Upgrades.keepOrNew(minOriginDistance, 700, d.minOriginDistance);
		}
		if (version >= 1 && version < 4) {
			// 3 to 4 (FIX-R3-B, R3A-3): 20b's dark comes in a ring at a time, the glimpse after the second ring, every
			// light of the floor but a player's own chunks, where still version 3's.
			Sc20Config d = new Sc20Config();
			darkRingSeconds = ModConfig.Upgrades.keepOrNew(darkRingSeconds, 4.0, d.darkRingSeconds);
			darkPerVisit = ModConfig.Upgrades.keepOrNew(darkPerVisit, 400, d.darkPerVisit);
			glimpseAfterRings = ModConfig.Upgrades.keepOrNew(glimpseAfterRings, 4, d.glimpseAfterRings);
		}
		version = CURRENT;
		return true;
	}
}
