package com.forzacode.a1016_02.entity.sc;

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
 * The layer's common gates for entity's parts (docs/SELF_CREATED.md, "Common gates"): the switch first, then the overworld
 * only, nothing once the story is quiet for good ({@code director:silence_forever}, {@code ending:path=C}), after
 * {@code ending:ended} or {@code ending:d_complete}, during {@code ending:last_minute}, or while
 * {@code director:obeyed_after_stop} is set (Ending A's false peace is real quiet), and never within
 * {@link #COMBAT_SECONDS} of the target's combat.
 */
public final class ScGates {
	/** The story flags that hold every part back while one is set. */
	public static final List<String> STORY_FLAGS = List.of("director:silence_forever", "ending:path=C", "ending:ended", "ending:d_complete",
			"ending:last_minute", "director:obeyed_after_stop");
	/** Never within this long of the target's own combat. */
	public static final double COMBAT_SECONDS = 30.0;

	private ScGates() {
	}

	/** The first story flag set now, or empty ({@link #STORY_FLAGS}). */
	public static Optional<String> storyFlag(Predicate<String> hasFlag) {
		for (String flag : STORY_FLAGS) {
			if (hasFlag.test(flag)) {
				return Optional.of(flag);
			}
		}
		return Optional.empty();
	}

	/**
	 * Why the common gates hold a part back, or empty (pure): the layer off, not the overworld, a story flag
	 * ({@link #storyFlag}), or combat within {@link #COMBAT_SECONDS} ({@code combatTicks}).
	 */
	public static Optional<String> refusal(boolean enabled, boolean overworld, Predicate<String> hasFlag, long ticksSinceCombat, long combatTicks) {
		if (!enabled) {
			return Optional.of("the layer is off");
		}
		if (!overworld) {
			return Optional.of("not the overworld");
		}
		Optional<String> flag = storyFlag(hasFlag);
		if (flag.isPresent()) {
			return Optional.of("the story holds it (" + flag.get() + ")");
		}
		if (ticksSinceCombat < combatTicks) {
			return Optional.of("combat " + ticksSinceCombat / 20 + " s ago");
		}
		return Optional.empty();
	}

	/** The common gates for this player now (live). */
	public static Optional<String> refusal(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		return refusal(SelfCreated.enabled(), player.level().dimension() == Level.OVERWORLD, HerobrineState.get(server)::hasFlag,
				Services.watch().ticksSinceCombat(player), combatTicks());
	}

	/** The story gates for the world now (live): the switch and the story flags, no player. */
	public static Optional<String> storyRefusal(MinecraftServer server) {
		if (!SelfCreated.enabled()) {
			return Optional.of("the layer is off");
		}
		return storyFlag(HerobrineState.get(server)::hasFlag).map(flag -> "the story holds it (" + flag + ")");
	}

	/** {@link #COMBAT_SECONDS} in ticks. */
	public static long combatTicks() {
		return ModConfig.realTicks(COMBAT_SECONDS);
	}
}
