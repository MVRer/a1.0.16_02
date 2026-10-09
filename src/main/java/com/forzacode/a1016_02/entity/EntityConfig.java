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
 * Shared rules (300-block spacing, one per day) come from {@code Pacing}; the minimum distance is {@link #minDistance}
 * here (D-035 replaced the 24-block floor).
 * Real-time values are in seconds and go through {@link ModConfig#realTicks(double)}.
 */
public final class EntityConfig {
	private static final String SECTION = "entity";
	/** Same settings as {@code ModConfig}'s writer. */
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	// --- where he stands (see FogEdge; D-035) ---
	/**
	 * Every variant but the close one stands this share of the visible fog end away: the fog end the client reports
	 * (dusk fog, surges and vanilla fog included), else the server's estimate. Tunable live.
	 */
	public double normalFractionMin = 0.55;
	public double normalFractionMax = 0.75;
	/** The close variant's share of the visible fog end, then clamped to {@link #closeMinDistance}..{@link #closeMaxDistance}. Tunable live. */
	public double closeFractionMin = 0.35;
	public double closeFractionMax = 0.50;
	/** The close variant's clamp in blocks. Tunable live. */
	public double closeMinDistance = 16;
	public double closeMaxDistance = 28;
	/** Never closer than this (horizontal, and from any player's eyes to any part of him). Tunable live, never under {@link #MIN_DISTANCE_FLOOR}. */
	public double minDistance = 12;
	/** A client fog report older than this is ignored and the server's estimate is used. */
	public double fogReportMaxAgeSeconds = 5;
	/** He stands at least this far inside the visible fog end. */
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

	// --- behaviour (the tunable ones can be set live with /a1016 entity tune) ---
	/** Half-angle of the cone around the crosshair that counts as looking at him. */
	public double stareConeDegrees = 8;
	/** Looking straight at him this long (in total, see HimEntity) ends the sighting. Tunable live. */
	public double stareSeconds = 3;
	/**
	 * Closing this many blocks on him, counted since he was first seen ({@link Approach}), ends the sighting; at most
	 * {@link #approachSpawnFraction} of the distance he appeared at. Tunable live.
	 */
	public double approachBlocks = 10;
	public double approachSpawnFraction = 0.3;
	/** Closing less than this at a time is a step, not an approach, and does not count. */
	public double approachStepBlocks = 1.5;
	/**
	 * Coming this close (horizontal) ends the sighting at once, before {@link #minSeenSeconds} too; at most
	 * {@link #fleeSpawnFraction} of the distance he appeared at, so a close one does not flee at once. Tunable live.
	 */
	public double fleeDistance = 18;
	public double fleeSpawnFraction = 0.6;
	/** Once first seen, nothing but {@link #fleeDistance} ends the sighting (or removes him) before this. Tunable live. */
	public double minSeenSeconds = 3;
	/** He stares back this long before he turns away. Tunable live. */
	public double stareBackSeconds = 2;
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
	/** Movement speed modifiers (on a 0.25 base speed) of the slow walk and the walk. */
	public double slowWalkSpeed = 0.7;
	public double walkSpeed = 0.9;
	/**
	 * The run (D-036), in blocks per second on the ground: never slower than {@link #baseRunSpeed}, and
	 * {@link #outrunFactor} times the chasing player's speed, up to {@link #maxRunSpeed} (sprint-jumping is about 7.1).
	 * Tunable live.
	 */
	public double baseRunSpeed = 5.8;
	public double outrunFactor = 1.1;
	public double maxRunSpeed = 9.0;
	/** A walking figure breaks into the run when a player closes on him faster than this (blocks per second). Tunable live. */
	public double closeInFastSpeed = 3.5;

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

	/** Section version, so changed defaults reach files written before the change ({@link #migrate}). */
	public int version = 0;
	private static final int CURRENT_VERSION = 3;
	/** {@link #minDistance} is never taken under this. */
	public static final double MIN_DISTANCE_FLOOR = 8.0;

	public static EntityConfig get() {
		return ModConfig.section(SECTION, EntityConfig.class, EntityConfig::new);
	}

	/**
	 * Brings an older section up to date and saves it. Version 2 (playtest): approach 10 blocks (was 6), stare cone 8
	 * degrees (was 6), stare back 2 s (was 1). Version 3 (D-035): the close clamp is 16 to 28 blocks (was 24 to 36).
	 * Values that only appeared now take their defaults by themselves.
	 */
	public void migrate() {
		if (upgrade()) {
			save();
		}
	}

	/** {@link #migrate} without saving. Returns true if anything changed. */
	boolean upgrade() {
		if (version >= CURRENT_VERSION) {
			return false;
		}
		if (version < 2) {
			EntityConfig defaults = new EntityConfig();
			approachBlocks = defaults.approachBlocks;
			stareConeDegrees = defaults.stareConeDegrees;
			stareBackSeconds = defaults.stareBackSeconds;
		}
		if (version < 3) {
			EntityConfig defaults = new EntityConfig();
			closeMinDistance = defaults.closeMinDistance;
			closeMaxDistance = defaults.closeMaxDistance;
		}
		version = CURRENT_VERSION;
		return true;
	}

	/** {@link #minDistance}, never under {@link #MIN_DISTANCE_FLOOR} (a bad value in the file included). */
	public double minDistance() {
		double min = minDistance;
		return Double.isNaN(min) ? MIN_DISTANCE_FLOOR : Math.max(MIN_DISTANCE_FLOOR, min);
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
