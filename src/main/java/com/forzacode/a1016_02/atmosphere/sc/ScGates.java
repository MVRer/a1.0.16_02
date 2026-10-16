package com.forzacode.a1016_02.atmosphere.sc;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * The common gates of atmosphere's part of the layer (docs/SELF_CREATED.md, "Common gates"): the switch is on
 * ({@link SelfCreated#enabled}), the story goes on (never once it is quiet for good, on path C, after
 * {@code ending:ended} or {@code ending:d_complete}, during {@code ending:last_minute}, nor while
 * {@code director:obeyed_after_stop} holds Ending A's false peace), the overworld only, and never within
 * {@link #NO_COMBAT_SECONDS} of the target's own combat. Pure forms for the tests; live forms read the world's state.
 */
public final class ScGates {
	/** While any of these is set nothing of the layer happens here. */
	public static final List<String> STOP_FLAGS = List.of("director:silence_forever", "ending:path=C", "ending:ended", "ending:d_complete",
			TraceService.LAST_MINUTE_FLAG, "director:obeyed_after_stop");
	/** Never within this long of the target's own combat. */
	public static final double NO_COMBAT_SECONDS = 30.0;

	private ScGates() {
	}

	/** Pure: the switch is on and none of {@link #STOP_FLAGS} is set. */
	public static boolean storyAllows(boolean enabled, Predicate<String> hasFlag) {
		return why(enabled, hasFlag).isEmpty();
	}

	/** Pure: why not ({@link #storyAllows}), in a few words, or empty. */
	public static Optional<String> why(boolean enabled, Predicate<String> hasFlag) {
		if (!enabled) {
			return Optional.of("the layer is switched off");
		}
		for (String flag : STOP_FLAGS) {
			if (hasFlag.test(flag)) {
				return Optional.of("the story holds none (" + flag + ")");
			}
		}
		return Optional.empty();
	}

	/** The live world: the switch and the story's flags. */
	public static boolean storyAllows(MinecraftServer server) {
		return why(server).isEmpty();
	}

	/** The live world: why not, or empty. */
	public static Optional<String> why(MinecraftServer server) {
		HerobrineState state = HerobrineState.get(server);
		return why(SelfCreated.enabled(), state::hasFlag);
	}

	/** The layer's world: the overworld. */
	public static boolean overworld(Level level) {
		return level.dimension() == Level.OVERWORLD;
	}

	/** No combat of the target's own within {@link #NO_COMBAT_SECONDS}. */
	public static boolean noCombat(long ticksSinceCombat) {
		return ticksSinceCombat >= AtmosphereConfig.ticks(NO_COMBAT_SECONDS);
	}
}
