package com.forzacode.a1016_02.lore.sc;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The layer's common gates for lore's parts (docs/SELF_CREATED.md, "Common gates"): the switch, the overworld, the story
 * flags ({@code director:silence_forever}, {@code ending:path=C}, {@code ending:ended}, {@code ending:d_complete},
 * {@code ending:last_minute}, {@code director:obeyed_after_stop}) and 30 s of combat.
 */
final class ScGates {
	static final List<String> STORY_FLAGS = List.of("director:silence_forever", "ending:path=C", "ending:ended", "ending:d_complete",
			"ending:last_minute", "director:obeyed_after_stop");
	static final double COMBAT_SECONDS = 30.0;

	private ScGates() {
	}

	static Optional<String> storyFlag(Predicate<String> hasFlag) {
		for (String flag : STORY_FLAGS) {
			if (hasFlag.test(flag)) {
				return Optional.of(flag);
			}
		}
		return Optional.empty();
	}

	/** The switch and the story flags for the world now. */
	static Optional<String> storyRefusal(MinecraftServer server) {
		if (!SelfCreated.enabled()) {
			return Optional.of("the layer is off");
		}
		return storyFlag(HerobrineState.get(server)::hasFlag).map(flag -> "the story holds it (" + flag + ")");
	}

	/** The common gates for this player now: the story's, then the overworld and their combat. */
	static Optional<String> refusal(ServerPlayer player) {
		Optional<String> story = storyRefusal(player.level().getServer());
		if (story.isPresent()) {
			return story;
		}
		if (player.level().dimension() != Level.OVERWORLD) {
			return Optional.of("not the overworld");
		}
		long since = Services.watch().ticksSinceCombat(player);
		if (since < ModConfig.realTicks(COMBAT_SECONDS)) {
			return Optional.of("combat " + since / 20 + " s ago");
		}
		return Optional.empty();
	}
}
