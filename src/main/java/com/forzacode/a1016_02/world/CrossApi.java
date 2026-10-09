package com.forzacode.a1016_02.world;

import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;

/**
 * The world's crosses, for the other workstreams. His crosses (worldgen crosses on hills, peaks and tunnel mouths,
 * the row of crosses) are CROSS sites whose size is the cross's height. A glass memorial (D-051) was left by others,
 * the people on the list, and is not one of his traces (D-041): its CROSS site carries its height negated, so
 * {@code size} stays a harmless reach of 0 for code that reads it as an extent. Any thread.
 */
public final class CrossApi {
	private CrossApi() {
	}

	/** True if the site is a glass memorial cross (left by others, never his). */
	public static boolean isGlassMemorial(SiteRegistry.Site site) {
		return site.type() == SiteType.CROSS && isGlassSize(site.size());
	}

	/** The height of the cross a CROSS site stands for. */
	public static int height(SiteRegistry.Site site) {
		return Math.abs(site.size());
	}

	/** The size a CROSS site of this height is recorded with. */
	public static int siteSize(int height, boolean glass) {
		return glass ? -Math.abs(height) : Math.abs(height);
	}

	/** True if a CROSS site of this size is a glass memorial. */
	public static boolean isGlassSize(int size) {
		return size < 0;
	}
}
