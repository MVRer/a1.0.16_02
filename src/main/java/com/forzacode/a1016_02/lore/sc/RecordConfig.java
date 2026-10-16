package com.forzacode.a1016_02.lore.sc;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc14} (version 2), lore's (docs/SELF_CREATED.md, 14). The client's world list reads its own copy (in
 * singleplayer the same file).
 */
public final class RecordConfig implements ModConfig.Versioned {
	public static final String SECTION = "sc14";
	public static final int CURRENT = 2;
	static final LocalTime DEFAULT_NIGHT_FROM = LocalTime.of(1, 0);
	static final LocalTime DEFAULT_NIGHT_TO = LocalTime.of(4, 30);

	public int version = 0;
	/** (a) shows only after at least this many real hours away. */
	@SerializedName(value = "a", alternate = "gapHours")
	public double gapHours = 6.0;
	/** (a) prefers a local time from... */
	@SerializedName(value = "b", alternate = "nightFrom")
	public String nightFrom = "01:00";
	/** ...to (HH:mm). */
	@SerializedName(value = "c", alternate = "nightTo")
	public String nightTo = "04:30";
	/** (b) from this stage on ({@code Stage.level()}: 3 is Telling)... */
	@SerializedName(value = "d", alternate = "versionFromStage")
	public int versionFromStage = 3;
	/** ...at least this many in-game days after (a) was shown. */
	@SerializedName(value = "e", alternate = "versionAfterDays")
	public double versionAfterDays = 2.0;
	/** (c) from this stage on (2 is Proximity). */
	@SerializedName(value = "f", alternate = "seedFromStage")
	public int seedFromStage = 2;
	/** (d) on or off. */
	@SerializedName(value = "g", alternate = "stats")
	public boolean stats = true;
	/** (d) the credited player's base (else the player) within this many blocks (horizontal). */
	@SerializedName(value = "h", alternate = "statsRadius")
	public double statsRadius = 64.0;

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

	public static RecordConfig get() {
		return ModConfig.section(SECTION, RecordConfig.class, RecordConfig::new);
	}

	/** {@link #nightFrom}, or 01:00 if it cannot be read. */
	public LocalTime nightFromTime() {
		return time(nightFrom, DEFAULT_NIGHT_FROM, "b");
	}

	/** {@link #nightTo}, or 04:30 if it cannot be read. */
	public LocalTime nightToTime() {
		return time(nightTo, DEFAULT_NIGHT_TO, "c");
	}

	/** {@link #gapHours} in milliseconds. */
	public long gapMillis() {
		return Math.round(Math.max(0.0, gapHours) * 3_600_000.0);
	}

	private static LocalTime time(String text, LocalTime fallback, String key) {
		if (text == null) {
			return fallback;
		}
		try {
			return LocalTime.parse(text.trim());
		} catch (DateTimeParseException e) {
			A1016_02.LOGGER.warn("[a1016] sections.{}.{} is not a time (HH:mm): '{}'", SECTION, key, text);
			return fallback;
		}
	}
}
