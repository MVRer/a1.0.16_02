package com.forzacode.a1016_02.ending;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything the ending asks of other workstreams, in one place: the figure, the accident planner, the director's
 * pacing, the house copy, lore's ending hooks, the dusk fog, and core's trace, mob and watch services. {@link #LIVE}
 * calls the real ones; game tests record the calls instead. Every world change still goes through
 * {@link TraceService} and every mob change through {@link MobTamper}; nothing here spawns a mob or touches the player.
 */
public interface EndingPorts {
	/** Ending A: fires {@code sighting_last_one} for the player through the card's own gates (never forced). */
	FireResult lastSighting(ServerPlayer player);

	/** True if the figure is out right now. */
	boolean figureOut(MinecraftServer server);

	/**
	 * Arms one trap of this kind for the player (around them), through the accident planner: one trap per player (the
	 * planner refuses while one of theirs is armed), never on ground another player's trap holds.
	 */
	boolean armTrap(ServerPlayer player, String trapId);

	/**
	 * Arms one trap of this kind for the player only if every spot the planner could pick for it lies inside
	 * {@code bounds} (the planner tries its nearest candidates in order, so all of those it may try must be inside).
	 * False otherwise, or while one of that player's own traps is armed.
	 */
	boolean armTrapInside(ServerPlayer player, String trapId, Bounds bounds);

	/** True if any trap is armed for anyone (Ending A's one accident for the world waits on it). */
	boolean trapArmed();

	/** True if this player's own trap is armed (accident keeps one per player). */
	boolean trapArmed(MinecraftServer server, UUID player);

	/** Every armed trap, its kind and spot, whoever's it is, in the order they were armed. */
	List<EndingState.Trap> armedTraps(MinecraftServer server);

	/** This player's armed trap, if any. */
	Optional<EndingState.Trap> armedTrap(MinecraftServer server, UUID player);

	void disarmTraps();

	/** Play ticks since the player joined this session (the join grace). */
	long ticksSinceJoin(ServerPlayer player);

	/** Play ticks since the director last fired an ACCIDENT card ({@link Long#MAX_VALUE} if never). */
	long ticksSinceDirectorAccident(MinecraftServer server);

	/** True during the director's quiet. */
	boolean directorQuiet(MinecraftServer server);

	boolean copyExists(MinecraftServer server);

	/** Asks for the copy elsewhere to be finished ({@code HouseCopyApi.finish}). */
	boolean finishCopy(MinecraftServer server);

	/** Cancels a finish asked for with {@link #finishCopy} ({@code HouseCopyApi.cancelFinish}): B left or the story ended. */
	default boolean cancelCopyFinish(MinecraftServer server) {
		return false;
	}

	boolean copyFinished(MinecraftServer server);

	/** The middle of the copy, once its site is picked. */
	Optional<GlobalPos> copySite(MinecraftServer server);

	/** The box around every block moved into the copy (its shell, and so its inside), once it has any. */
	Optional<Bounds> copyBounds(MinecraftServer server);

	/** True while the "Stop." sign (F03) exists. */
	boolean stopSignExists(MinecraftServer server);

	boolean moveStopSignToCross(MinecraftServer server, GlobalPos cross);

	/** The bottom of the cross built for the marked death at {@code death}, once it stands. */
	Optional<GlobalPos> findCross(MinecraftServer server, GlobalPos death);

	/** F10 gains one "* removed [PLAYER NAME]" line for this player, whose line the ending finished. */
	boolean finishF10(MinecraftServer server, UUID player);

	/** F20 under F10 (Ending B only). */
	boolean placeF20(MinecraftServer server);

	/** Stores and sends the dusk fog level. */
	void setDuskFog(MinecraftServer server, float level);

	/** Atmosphere's dusk fog for a stage (to undo Ending C's clear fog when another path takes over). */
	float stageDuskFog(Stage stage);

	TraceService traces();

	MobTamper mobs();

	PlayerWatch watch();

	/** The real services. */
	EndingPorts LIVE = new LivePorts();
}
