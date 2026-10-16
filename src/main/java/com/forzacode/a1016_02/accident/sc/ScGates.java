package com.forzacode.a1016_02.accident.sc;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.TraceService;

import org.jspecify.annotations.Nullable;

/**
 * The layer's common gates (docs/SELF_CREATED.md, "Features"), for accident's part of it: nothing once the story is
 * quiet for good ({@code director:silence_forever} or {@code ending:path=C}), after {@code ending:ended} or
 * {@code ending:d_complete}, during {@code ending:last_minute}, or while {@code director:obeyed_after_stop} holds
 * Ending A's false peace; never within {@link #NO_COMBAT_SECONDS} of the player's combat.
 */
public final class ScGates {
	/** No combat of the player's within this long. */
	public static final double NO_COMBAT_SECONDS = 30;

	private ScGates() {
	}

	/** Why the story allows nothing of the layer now, or null. */
	public static @Nullable String storyBlock(HerobrineState state) {
		if (state.hasFlag("director:silence_forever") || state.hasFlag("ending:path=C")) {
			return "the story is quiet for good";
		}
		if (state.hasFlag("ending:ended") || state.hasFlag("ending:d_complete")) {
			return "the story is over";
		}
		if (state.hasFlag(TraceService.LAST_MINUTE_FLAG)) {
			return "the last minute";
		}
		if (state.hasFlag("director:obeyed_after_stop")) {
			return "the quiet after Stop.";
		}
		return null;
	}

	/** True if the player's last combat is at least {@link #NO_COMBAT_SECONDS} ago. */
	public static boolean noCombat(long ticksSinceCombat) {
		return ticksSinceCombat >= ModConfig.realTicks(NO_COMBAT_SECONDS);
	}
}
