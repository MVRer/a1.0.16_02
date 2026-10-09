package com.forzacode.a1016_02.ending;

import java.util.Optional;

import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything the ending asks of other workstreams, in one place: the figure, the accident planner, the house copy,
 * lore's ending hooks, the dusk fog, and core's trace, mob and watch services. {@link #LIVE} calls the real ones;
 * game tests record the calls instead. Every world change still goes through {@link TraceService} and every mob
 * change through {@link MobTamper}; nothing here spawns a mob or touches the player.
 */
public interface EndingPorts {
	/** Ending A: fires {@code sighting_last_one} for the player through the card's own gates (never forced). */
	FireResult lastSighting(ServerPlayer player);

	/** True if the figure is out right now. */
	boolean figureOut(MinecraftServer server);

	/** Arms one trap of this kind for the player (around them), through the accident planner. */
	boolean armTrap(ServerPlayer player, String trapId);

	/** True if any trap is armed. */
	boolean trapArmed();

	void disarmTraps();

	boolean copyExists(MinecraftServer server);

	/** Asks for the copy elsewhere to be finished ({@code HouseCopyApi.finish}). */
	boolean finishCopy(MinecraftServer server);

	boolean copyFinished(MinecraftServer server);

	/** The middle of the copy, once its site is picked. */
	Optional<GlobalPos> copySite(MinecraftServer server);

	/** True while the "Stop." sign (F03) exists. */
	boolean stopSignExists(MinecraftServer server);

	boolean moveStopSignToCross(MinecraftServer server, GlobalPos cross);

	/** The bottom of the cross built for the marked death at {@code death}, once it stands. */
	Optional<GlobalPos> findCross(MinecraftServer server, GlobalPos death);

	/** F10 gains its last line. */
	boolean finishF10(MinecraftServer server);

	/** F20 under F10 (Ending B only). */
	boolean placeF20(MinecraftServer server);

	/** The telling count ({@code LoreApi.tellingCount}). */
	int tellingCount(MinecraftServer server);

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
