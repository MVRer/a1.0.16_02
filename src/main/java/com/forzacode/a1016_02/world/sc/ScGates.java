package com.forzacode.a1016_02.world.sc;

import java.util.Collection;
import java.util.List;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.SelfCreated;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The common gates of the self-created layer's world features ({@code docs/SELF_CREATED.md}, "Common gates"), as
 * small functions: the switch, the overworld, nothing once the story is quiet for good, over or in its last minute,
 * nothing during Ending A's false peace, never within 30 s of the target's combat. Server thread.
 */
public final class ScGates {
	/**
	 * Flags under which nothing of the layer happens: quiet for good ({@code director:silence_forever}, path C), the
	 * story over ({@code ending:ended}, {@code ending:d_complete}), Ending D's last minute and Ending A's false peace
	 * ({@code director:obeyed_after_stop}).
	 */
	public static final List<String> QUIET_FLAGS = List.of("director:silence_forever", "ending:path=C", "ending:ended", "ending:d_complete",
			"ending:last_minute", "director:obeyed_after_stop");
	/** The layer's common combat gate: never within this many seconds of the target's own combat. */
	public static final int NO_COMBAT_SECONDS = 30;

	private ScGates() {
	}

	/** Why nothing of the layer may happen in this world now, or null: the switch, then the story's flags. */
	public static @Nullable String worldRefusal(HerobrineState state) {
		if (!SelfCreated.enabled()) {
			return "the layer is switched off";
		}
		for (String flag : QUIET_FLAGS) {
			if (state.hasFlag(flag)) {
				return "the story is quiet (" + flag + ")";
			}
		}
		return null;
	}

	/** True if the layer may do something in this world now ({@link #worldRefusal} is null). */
	public static boolean allowsWorld(HerobrineState state) {
		return worldRefusal(state) == null;
	}

	/**
	 * Why nothing may happen for this player now, or null: {@link #worldRefusal}, then the overworld, then (unless
	 * {@code forced}) their combat within {@link #NO_COMBAT_SECONDS}.
	 */
	public static @Nullable String targetRefusal(HerobrineState state, PlayerWatch watch, ServerPlayer player, ServerLevel level, boolean forced) {
		String world = worldRefusal(state);
		if (world != null) {
			return world;
		}
		if (level.dimension() != Level.OVERWORLD) {
			return "not the overworld";
		}
		return forced ? null : combatRefusal(watch.ticksSinceCombat(player));
	}

	/** Why the target's own combat holds it back, or null: less than {@link #NO_COMBAT_SECONDS} since it. */
	public static @Nullable String combatRefusal(long ticksSinceCombat) {
		return ticksSinceCombat < NO_COMBAT_SECONDS * 20L ? "in combat (" + NO_COMBAT_SECONDS + " s)" : null;
	}

	/** {@code people} without {@code player} (by UUID). */
	public static List<ServerPlayer> without(List<ServerPlayer> people, ServerPlayer player) {
		return people.stream().filter(p -> !p.getUUID().equals(player.getUUID())).toList();
	}

	/** The first of {@code people} within {@code radius} blocks (3D, from their feet) of any of {@code cells}, or null. */
	public static @Nullable ServerPlayer within(List<ServerPlayer> people, Collection<BlockPos> cells, double radius) {
		double limit = radius * radius;
		for (ServerPlayer player : people) {
			Vec3 at = player.position();
			for (BlockPos cell : cells) {
				if (at.distanceToSqr(Vec3.atCenterOf(cell)) < limit) {
					return player;
				}
			}
		}
		return null;
	}

	/** Horizontal distance squared between a player's feet and a cell's center. */
	public static double horizontalSqr(ServerPlayer player, BlockPos cell) {
		double dx = player.getX() - (cell.getX() + 0.5);
		double dz = player.getZ() - (cell.getZ() + 0.5);
		return dx * dx + dz * dz;
	}

	/** A player's name for a debug line. */
	public static String name(ServerPlayer player) {
		return player.getName().getString();
	}
}
