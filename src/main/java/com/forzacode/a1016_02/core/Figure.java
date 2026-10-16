package com.forzacode.a1016_02.core;

import java.util.Optional;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The figure, for the one sighting another workstream sets up (D-073): the glimpse at the top of the tunnel ladder
 * (dig's third rung, D-072). Underground the dark stands in for the fog: he stands where the caller says, past the last
 * torch, and is gone before the player looks straight at him, the one figure that vanishes in view. Not a director card
 * of its own: only this contract spawns it. The entity workstream installs the real one ({@link Services#installFigure});
 * the stub never shows him. Server thread.
 */
public interface Figure {
	/**
	 * Whether a glimpse could appear for {@code player} with his feet at {@code feet} right now, by the figure's own
	 * rules (he is not out anywhere, the spot is loaded, dark and has room for him, it is out of every player's view and
	 * far enough from everyone). Cheap and without side effects: the ladder asks it before its steps start. The same
	 * answer as {@code glimpseRefusal(player, feet).isEmpty()}.
	 */
	boolean canGlimpse(ServerPlayer player, Vec3 feet);

	/**
	 * Why a glimpse could not appear there now (empty: it could, exactly when {@link #canGlimpse} is true), in a few
	 * words for a debug line ("too lit (light 6)", "in someone's view: Alex"). Cheap and without side effects.
	 * Default: {@link #canGlimpse}, with {@code "refused"} for every no.
	 */
	default Optional<String> glimpseRefusal(ServerPlayer player, Vec3 feet) {
		return canGlimpse(player, feet) ? Optional.empty() : Optional.of("refused");
	}

	/**
	 * {@link #glimpseRefusal} with the light at the spot left aside: whether he could stand there once the caller has
	 * made it dark (the ladder takes the players' own lights behind the player first, D-072). The caller checks the dark
	 * again before {@link #glimpse}, which holds the spot to every rule. Default: {@link #glimpseRefusal} (a figure that
	 * cannot tell refuses a lit spot as lit).
	 */
	default Optional<String> glimpseRefusalOnceDark(ServerPlayer player, Vec3 feet) {
		return glimpseRefusal(player, feet);
	}

	/**
	 * Spawns the glimpse for {@code player} at {@code feet}, facing {@code yaw} (degrees, the entity's yaw), if
	 * {@link #canGlimpse} still holds: {@link FireResult#FIRED} when he stands there, {@link FireResult#NO_SPOT} when the
	 * spot no longer works (or he is out), {@link FireResult#SKIPPED} when this player cannot have one.
	 */
	FireResult glimpse(ServerPlayer player, Vec3 feet, float yaw);

	/** Default: never a glimpse ({@code false}, {@link FireResult#NO_SPOT}). */
	final class Stub implements Figure {
		@Override
		public boolean canGlimpse(ServerPlayer player, Vec3 feet) {
			return false;
		}

		@Override
		public FireResult glimpse(ServerPlayer player, Vec3 feet, float yaw) {
			return FireResult.NO_SPOT;
		}
	}
}
