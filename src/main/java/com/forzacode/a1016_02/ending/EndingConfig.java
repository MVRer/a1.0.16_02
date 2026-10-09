package com.forzacode.a1016_02.ending;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Stage;

/**
 * The ending workstream's tunables, stored under {@code sections.ending} in {@code config/a1016_02.json}. Day counts
 * are in-game days ({@code GameClock}); real-time values go through {@link ModConfig#realTicks}.
 */
public final class EndingConfig {
	// --- cadence and commits ---
	/** How often (real seconds of server time) the ending checks its paths and runs a beat. */
	public double checkSeconds = 10;
	/** No path commits on its own before this stage (the third-death rule and debug always can). */
	public String commitMinStage = "TELLING";
	/** After A or B finish (normal mode) and after any hardcore marked death, the director stays silent for good. */
	public boolean silenceAfterEnd = true;
	/** Trap scans are not cheap: the ending tries to arm (or fire the last sighting) at most once per this many real seconds. */
	public double armRetrySeconds = 60;

	// --- what counts as his traces for "chasing traces" and "returning to a scar" (A and C) ---
	public List<String> traceSiteTypes = new ArrayList<>(List.of("TUNNEL_END", "OCEAN_PYRAMID", "CUT", "BARE_GROVE", "DEAD_MOUNTAIN"));
	/** Chunk radius around a trace site that counts as a visit. */
	public int traceVisitRadiusChunks = 1;

	// --- Ending A: "Stop." ---
	/** No fragment read and no trace visited for this many days (on top of pacing.obeyDays since "Stop." or the last telling). */
	public int aQuietDays = 3;
	/** Give up waiting for the last sighting after this many days and go on to the quiet. */
	public double aSightingMaxDays = 3;
	/** Fire the last sighting again (if he was never seen) at most this many times. */
	public int aSightingTries = 3;
	/** Trap ids tried in order for the one ordinary accident (centered on the player, so their own mine route). */
	public List<String> aTraps = new ArrayList<>(List.of("lava_floor", "lava_wall"));
	/** If the trap's window ran out without a death, arm again after this many days. */
	public double aRearmDays = 1;
	/** Give up moving the "Stop." sign to the cross after this many days. */
	public double aSignMaxDays = 5;

	// --- Ending B: "Removed" ---
	/** Tellings after "Stop." that commit B. */
	public int bTellingsAfterStop = 3;
	/** Total tellings (LoreApi.tellingCount) that commit B, "Stop." or not. */
	public int bTellingCount = 12;
	/** Attention (0 to 100) that commits B in Stage 3 or later. */
	public double bAttention = 95;
	/** {@code director:pace_multiplier} while B runs (above 1: accidents closer together). */
	public double bPaceMultiplier = 1.6;
	/** Days of escalation before the house is emptied. */
	public double bEscalateDays = 2;
	/** Home accidents: when the player is this close to their base, the ending arms a trap there itself. */
	public int bHomeRadius = 48;
	/**
	 * At most one home accident per this much real play time (persisted, so a rejoin does not reset it). Never under
	 * {@code pacing.majorGapMinutes} (D-045's floor), never in the join grace, never within that floor of the
	 * director's own last accident, never during the director's quiet.
	 */
	public double bHomeArmMinutes = 90;
	public List<String> bHomeTraps = new ArrayList<>(List.of("dark_corner", "house_fire", "gravel_ceiling", "lava_floor", "missing_rung",
			"moved_mob"));
	/** The house: furnishings the player placed within this many blocks of the base, under a roof. */
	public int houseRadius = 8;
	/** How far up the roof check looks. */
	public int houseRoofScan = 8;
	/** Try emptying the house as one batch for this many days, then take what is out of view a piece at a time. */
	public double bEmptyBatchDays = 1;
	/** Give up emptying (and go on) after this many days. */
	public double bEmptyMaxDays = 4;
	/** Wait this long for the copy elsewhere to finish. */
	public double bCopyMaxDays = 4;
	/** Mobs that wait in the doorway. */
	public int bDoorwayMobs = 3;
	/** Existing mobs are taken from within this many blocks of the house. */
	public int bDoorwayMobRadius = 48;
	/** Mob types that may wait in the doorway (never spawned, only moved). */
	public List<String> bDoorwayMobTypes = new ArrayList<>(List.of("minecraft:zombie", "minecraft:husk", "minecraft:skeleton", "minecraft:stray",
			"minecraft:spider", "minecraft:creeper"));
	/** They wait (frozen, silent, facing the door) until the player comes within this many blocks; then they are released. */
	public double bDoorwayReleaseBlocks = 6;
	/** They wait at most this many days. */
	public double bDoorwayHoldDays = 2;
	/** Go on to the final death after this many days even if no mob could be moved. */
	public double bDoorwayMaxDays = 2;
	/** The final trap is armed when the player is this close to the copy (or to the house if there is no copy). */
	public int bFinalRadius = 12;
	public List<String> bFinalTraps = new ArrayList<>(List.of("dark_corner", "moved_mob", "house_fire", "gravel_ceiling", "lava_floor",
			"falling_dripstone"));
	/** Give up placing F20 after this many days of trying. */
	public double bRecordMaxDays = 5;

	// --- Ending C: "For the record" ---
	/** No naming, no scar visit and no fog staring for this many days. */
	public int cQuietDays = 3;
	/**
	 * Every fragment they ever held must have gone into lava or fire, none may be held anywhere (inventory, nested
	 * shulker boxes and bundles, ender chest, containers around the base), and at least this many were burned.
	 */
	public int cMinFragmentsBurned = 1;
	/** Containers (nested ones too) within this many blocks of the base count as "held". */
	public int cBaseContainerRadius = 24;
	/** The house counts as taken apart once the player broke this share of the most blocks they ever had around the base. */
	public double cHouseTakenFraction = 0.75;
	/** A house needs at least this many player-placed blocks. */
	public int cMinHouseBlocks = 12;
	/** Radius (cube) around the base for the player's own blocks. */
	public int cHouseRadius = 12;
	/** Fog staring: standing still outdoors this long, looking level, at dusk, at night or in rain. */
	public double fogStareSeconds = 20;
	/** "Looking level": pitch within this many degrees of the horizon. */
	public double fogStarePitch = 20;
	/** The fog is out (time of day, 0 is sunrise) from dusk to just before dawn; rain counts at any time. */
	public int fogStareFrom = 11500;
	public int fogStareTo = 23500;

	public static EndingConfig get() {
		return ModConfig.section("ending", EndingConfig.class, EndingConfig::new);
	}

	public long checkTicks() {
		return Math.max(1, ModConfig.realTicks(checkSeconds));
	}

	public Stage minStage() {
		try {
			return Stage.valueOf(commitMinStage.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException | NullPointerException e) {
			return Stage.TELLING;
		}
	}
}
