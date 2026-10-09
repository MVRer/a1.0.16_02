package com.forzacode.a1016_02.entity;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The entity workstream's tunables, stored under {@code sections.entity} in {@code config/a1016_02.json}.
 * Shared rules (24-block minimum, 300-block spacing, one per day, the 2 s stare) come from {@code Pacing}.
 * Real-time values are in seconds and go through {@link ModConfig#realTicks(double)}.
 */
public final class EntityConfig {
	private static final String SECTION = "entity";
	/** Same settings as {@code ModConfig}'s writer. */
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	// --- the fog edge ---
	/** At dusk fog level 1 the spawn limit is pulled in to {@code 1 - duskFogPull} of the render limit. */
	public double duskFogPull = 0.6;
	/** Depth of the spawn band just inside the limit, as a fraction of the limit. */
	public double fogBandFraction = 0.10;
	public double fogBandMinBlocks = 6;
	/** The ridge, the trunk, the shore and a known place need the right terrain, so their band is this deep. */
	public double terrainBandFraction = 0.25;
	/** He stands at least this far inside the limit. */
	public double edgeMarginBlocks = 2;
	/** Hard cap on the spawn distance, whatever the render distance. */
	public double maxSpawnDistance = 192;
	/** Directions tried around the player when looking for a spot. */
	public int spotSamples = 48;

	// --- gates ---
	/** Never near the base: neither the player nor his spot may be within this many blocks of it. */
	public int baseRadius = 64;
	public double combatCooldownSeconds = 30;
	/** Clear daylight is this time-of-day window (0 = sunrise), unless it rains. No sightings in it. */
	public int clearDayFrom = 0;
	public int clearDayTo = 11800;
	/** Dusk and dawn windows (the ridge needs one of them, or rain). */
	public int duskFrom = 11800;
	public int duskTo = 14000;
	public int dawnFrom = 22500;
	public int dawnTo = 24000;
	/** Night window (the light needs it). */
	public int nightFrom = 13000;
	public int nightTo = 23000;
	/** Chance that a drawn sighting goes ahead in Alone ("a tiny chance") and in Telling ("almost none"). */
	public double aloneChance = 0.3;
	public double tellingChance = 0.2;

	// --- behaviour ---
	/** Half-angle of the cone around the crosshair that counts as looking at him. */
	public double stareConeDegrees = 6;
	/** Walking this many blocks toward him ends the sighting. */
	public double approachBlocks = 6;
	public double stareBackSeconds = 1.0;
	public double riseSeconds = 0.75;
	/** Once seen, he is gone after being out of view this long. */
	public double goneAfterUnseenSeconds = 1.0;
	/** The cow is meant to be read as a cow: it may be glanced at and looked back at for a while. */
	public double cowGoneAfterUnseenSeconds = 20;
	/** If nobody sees him, he despawns after this long. */
	public double unseenLifetimeSeconds = 150;
	/** After this long he leaves even if nothing happened. */
	public double maxLifetimeSeconds = 300;
	/** Trunk variant: how long he tries to slip behind the tree before he just leaves. */
	public double hideMaxSeconds = 10;
	/** Movement speed modifiers (on a 0.25 base speed). */
	public double slowWalkSpeed = 0.7;
	public double walkSpeed = 0.9;
	public double runSpeed = 1.45;

	// --- variant spots ---
	/** The ridge: his feet at least this far above the player's eyes, so the sky is behind him. */
	public double ridgeMinRise = 2;
	/** The light: block light at his feet within this range is the edge of the glow. */
	public int lightEdgeMin = 3;
	public int lightEdgeMax = 7;
	public int lightSearchRadius = 9;
	/** Across the water: share of the line between you and him that has to be water. */
	public double waterFractionMin = 0.55;
	/** Close: a block you placed or dug within this radius makes a place you know. */
	public int closeKnownRadius = 10;

	// --- fakes ---
	public double fakeHoldSeconds = 30;

	// --- eyes (client look only; set live with /a1016 entity eyes, read by the renderer every frame) ---
	/** FLAT, BRIGHT or GLOW, see {@link EyeStyle}. Volatile: the server thread sets it, the render thread reads it. */
	public volatile EyeStyle eyeStyle = EyeStyle.BRIGHT;
	/** BRIGHT and GLOW: the eye pixels take fog at {@code 1 - this} strength. 0 = fogged like the body, 1 = never fogged. */
	public volatile double eyeFogResistance = 0.5;

	public static EntityConfig get() {
		return ModConfig.section(SECTION, EntityConfig.class, EntityConfig::new);
	}

	/** The eye style, BRIGHT if the file holds an unknown value. */
	public EyeStyle eyeStyle() {
		EyeStyle style = eyeStyle;
		return style != null ? style : EyeStyle.BRIGHT;
	}

	/** {@link #eyeFogResistance} clamped to 0..1. */
	public double eyeFogResistance() {
		double resistance = eyeFogResistance;
		return Double.isNaN(resistance) ? 0.5 : Math.clamp(resistance, 0.0, 1.0);
	}

	/**
	 * Writes this section back to {@code config/a1016_02.json}, in memory and on disk. Only {@code sections.entity}
	 * changes in the file; everything else is kept as it is there. {@code ModConfig} has no public save, so this
	 * takes the same lock and writes the same way.
	 */
	public void save() {
		synchronized (ModConfig.class) {
			JsonElement tree = GSON.toJsonTree(this);
			ModConfig.get().sections.add(SECTION, tree);
			Path path = FabricLoader.getInstance().getConfigDir().resolve(A1016_02.MOD_ID + ".json");
			JsonObject root = null;
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					JsonElement read = JsonParser.parseReader(reader);
					root = read.isJsonObject() ? read.getAsJsonObject() : null;
				} catch (IOException | JsonParseException e) {
					A1016_02.LOGGER.warn("[a1016] could not read {}, rewriting it from memory", path, e);
				}
			}
			if (root == null) {
				root = GSON.toJsonTree(ModConfig.get()).getAsJsonObject();
			}
			JsonObject sections = root.has("sections") && root.get("sections").isJsonObject() ? root.getAsJsonObject("sections") : new JsonObject();
			sections.add(SECTION, tree);
			root.add("sections", sections);
			try {
				Files.createDirectories(path.getParent());
				try (Writer writer = Files.newBufferedWriter(path)) {
					GSON.toJson(root, writer);
				}
			} catch (IOException e) {
				A1016_02.LOGGER.error("[a1016] could not write {}", path, e);
			}
		}
	}
}
