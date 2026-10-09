package com.forzacode.a1016_02.entity;

import net.minecraft.core.GlobalPos;

import org.jspecify.annotations.Nullable;

/** The pure sighting rules, kept free of world access so the game tests can check them directly. */
public final class SightingRules {
	private SightingRules() {
	}

	/** Never two in one in-game day: false if {@code today} already has {@code maxPerDay} sightings. */
	public static boolean dayAllows(long lastDay, int countOnLastDay, long today, int maxPerDay) {
		return lastDay != today || countOnLastDay < maxPerDay;
	}

	/** Never the same variant twice in a row. */
	public static boolean variantAllows(String lastVariant, String variant) {
		return !variant.equals(lastVariant);
	}

	/** At least {@code spacing} blocks (horizontal) from the last sighting. Another dimension is always far enough. */
	public static boolean farEnough(@Nullable GlobalPos last, GlobalPos pos, int spacing) {
		if (last == null || !last.dimension().equals(pos.dimension())) {
			return true;
		}
		long dx = last.pos().getX() - pos.pos().getX();
		long dz = last.pos().getZ() - pos.pos().getZ();
		return dx * dx + dz * dz >= (long) spacing * spacing;
	}

	/** True if {@code timeOfDay} (0..23999) lies in {@code [from, to)}; a window may wrap past midnight. */
	public static boolean inWindow(long timeOfDay, int from, int to) {
		long t = Math.floorMod(timeOfDay, 24000L);
		return from <= to ? t >= from && t < to : t >= from || t < to;
	}

	/** Clear daylight: inside the day window and not raining. Never a sighting then. */
	public static boolean clearDaylight(long timeOfDay, boolean raining, EntityConfig config) {
		return !raining && inWindow(timeOfDay, config.clearDayFrom, config.clearDayTo);
	}

	/** Dusk or dawn, the ridge's sky. Rain counts too: a grey sky works as well as a dusk one. */
	public static boolean duskSky(long timeOfDay, boolean raining, EntityConfig config) {
		return raining || inWindow(timeOfDay, config.duskFrom, config.duskTo) || inWindow(timeOfDay, config.dawnFrom, config.dawnTo);
	}

	public static boolean night(long timeOfDay, EntityConfig config) {
		return inWindow(timeOfDay, config.nightFrom, config.nightTo);
	}
}
