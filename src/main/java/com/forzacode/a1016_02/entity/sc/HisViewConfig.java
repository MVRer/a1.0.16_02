package com.forzacode.a1016_02.entity.sc;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/** {@code sections.sc03} (version 2), entity's (docs/SELF_CREATED.md, 03). */
public final class HisViewConfig implements ModConfig.Versioned {
	public static final String SECTION = "sc03";
	public static final int CURRENT = 2;
	/** Vanilla's world list draws only a 64 x 64 {@code icon.png} ({@code FaviconTexture}). */
	public static final int VANILLA_ICON_SIZE = 64;

	public int version = 0;
	/** From this stage on ({@code Stage.level()}: 2 is Proximity). */
	@SerializedName(value = "a", alternate = "fromStage")
	public int fromStage = 2;
	/** The figure within this many blocks (horizontal) of the host's base... */
	@SerializedName(value = "b", alternate = "maxBaseDistance")
	public double maxBaseDistance = 96.0;
	/** ...and the host within this many of it. */
	@SerializedName(value = "c", alternate = "hostBaseDistance")
	public double hostBaseDistance = 48.0;
	/** He has stood still this long on the client before the frame, seconds. */
	@SerializedName(value = "d", alternate = "standSeconds")
	public double standSeconds = 1.0;
	/** The picture's side in pixels: vanilla's 64 (any other value reads as 64, the only size the world list shows). */
	@SerializedName(value = "e", alternate = "iconSize")
	public int iconSize = VANILLA_ICON_SIZE;

	@Override
	public boolean upgrade() {
		if (version >= CURRENT) {
			return false;
		}
		// 1 to 2 (D-080): the keys are opaque letters; a value under an old key is read into its field (its
		// @SerializedName alternate) and only the new key is written back.
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
