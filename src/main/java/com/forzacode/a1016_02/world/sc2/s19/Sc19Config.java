package com.forzacode.a1016_02.world.sc2.s19;

import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc19} (docs/SELF_CREATED_2.md, 19): where the four segments of feature 19 lie and how they are cut.
 * Version 1, keys opaque from the start (D-103: no old keys). Read on the server thread; worldgen reads it through the
 * kit's snapshot (the kind {@code s19} is registered for that). A later default change adds a step to {@link #upgrade}
 * with {@code ModConfig.Upgrades.keepOrNew} and raises {@link #CURRENT}.
 */
public final class Sc19Config implements ModConfig.Versioned, Sc2Kind.Gates {
	public static final String SECTION = "sc19";
	public static final int CURRENT = 2;

	public int version = 0;
	/** The lowest stage whose new land may hold it (0: from the world's creation, D-108). */
	@SerializedName("a")
	public int fromStage = 0;
	/** A segment begins at least this far from the origin along its half-axis (blocks). */
	@SerializedName("b")
	public int startMin = 512;
	/** ... and up to this much farther (a hash of the world's seed, blocks). */
	@SerializedName("c")
	public int startSpread = 256;
	/** A segment's length: at least this ... */
	@SerializedName("d")
	public int lengthMin = 512;
	/** ... and at most this (blocks). */
	@SerializedName("e")
	public int lengthMax = 1024;
	/** One opening at the foot every this many blocks (0: none). */
	@SerializedName("f")
	public int holeEvery = 48;
	/** One cut this wide from top to bottom per segment (0: none). */
	@SerializedName("g")
	public int gapWidth = 9;

	public static Sc19Config get() {
		return ModConfig.section(SECTION, Sc19Config.class, Sc19Config::new);
	}

	@Override
	public int fromStage() {
		return fromStage;
	}

	/** The kit's gate number: a segment never begins nearer the origin than {@link #startMin}. */
	@Override
	public int minOriginDistance() {
		return startMin;
	}

	@Override
	public boolean upgrade() {
		if (version >= CURRENT) {
			return false;
		}
		if (version >= 1 && version < 2) {
			// 1 to 2 (round 3, D-108, D-109): from the world's creation, nearer and longer, where still version 1's.
			Sc19Config d = new Sc19Config();
			fromStage = ModConfig.Upgrades.keepOrNew(fromStage, 1, d.fromStage);
			startMin = ModConfig.Upgrades.keepOrNew(startMin, 1024, d.startMin);
			lengthMin = ModConfig.Upgrades.keepOrNew(lengthMin, 256, d.lengthMin);
			lengthMax = ModConfig.Upgrades.keepOrNew(lengthMax, 768, d.lengthMax);
		}
		version = CURRENT;
		return true;
	}
}
