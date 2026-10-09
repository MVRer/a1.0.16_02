package com.forzacode.a1016_02.entity;

import java.util.Locale;
import java.util.Optional;

/**
 * How the figure's eyes are drawn. Client look only; nothing about spawning, behaviour or sound depends on it.
 * Set live with {@code /a1016 entity eyes <style> [fogResistance]}, stored as {@link EntityConfig#eyeStyle}.
 */
public enum EyeStyle {
	/** Painted into the skin and lit like the rest of it, so they go dark with him at night. */
	FLAT,
	/**
	 * Unshaded full white that ignores darkness: no light emitted, no bloom, no particles. They take fog at
	 * {@code 1 - eyeFogResistance} strength, so at night at the fog edge two pale points outlast the body.
	 */
	BRIGHT,
	/** BRIGHT plus an emissive spill around the eyes (vanilla eyes layer, spider and enderman style). */
	GLOW;

	public String shortName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Optional<EyeStyle> byName(String name) {
		for (EyeStyle style : values()) {
			if (style.shortName().equalsIgnoreCase(name)) {
				return Optional.of(style);
			}
		}
		return Optional.empty();
	}
}
