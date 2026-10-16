package com.forzacode.a1016_02.entity.sc;

import com.forzacode.a1016_02.core.ModConfig;

/** {@code sections.sc03} (version 1), entity's (docs/SELF_CREATED.md, 03). */
public final class HisViewConfig implements ModConfig.Versioned {
	public static final String SECTION = "sc03";
	public static final int CURRENT = 1;
	/** Vanilla's world list draws only a 64 x 64 {@code icon.png} ({@code FaviconTexture}). */
	public static final int VANILLA_ICON_SIZE = 64;

	public int version = 0;
	/** From this stage on ({@code Stage.level()}: 2 is Proximity). */
	public int fromStage = 2;
	/** The figure within this many blocks (horizontal) of the host's base... */
	public double maxBaseDistance = 96.0;
	/** ...and the host within this many of it. */
	public double hostBaseDistance = 48.0;
	/** He has stood still this long on the client before the frame, seconds. */
	public double standSeconds = 1.0;
	/** The picture's side in pixels: vanilla's 64 (any other value reads as 64, the only size the world list shows). */
	public int iconSize = VANILLA_ICON_SIZE;

	@Override
	public boolean upgrade() {
		if (version >= CURRENT) {
			return false;
		}
		version = CURRENT;
		return true;
	}

	public static HisViewConfig get() {
		return ModConfig.section(SECTION, HisViewConfig.class, HisViewConfig::new);
	}

	/** {@link #standSeconds} in ticks. */
	public long standTicks() {
		return ModConfig.realTicks(Math.max(0.0, standSeconds));
	}

	/** The side the picture is written at: always vanilla's 64 (the world list rejects any other). */
	public int iconSize() {
		return VANILLA_ICON_SIZE;
	}
}
