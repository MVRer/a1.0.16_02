package com.forzacode.a1016_02.core;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Decides what happens and when: tick loop, decks, gates, tension, quiet, pity, fakes, and stages by time.
 * The director workstream installs the real one with {@link Services#installDirector(Director)}.
 */
public interface Director {
	/** Registers a card. Works before the director is installed. */
	static void register(EventCard card) {
		CardRegistry.register(card);
	}

	/** Called every server tick; the implementation picks its own cadence ({@link Pacing#directorTickTicks()}). */
	void tick(MinecraftServer server);

	/**
	 * Debug: fires a card for that player now. Skips gates and pacing, never the out-of-view rule.
	 * Returns {@link FireResult#SKIPPED} if the card is unknown or the player is not
	 * {@linkplain PlayerWatch#isTargetable targetable} (not a participant, or dead).
	 */
	FireResult fire(MinecraftServer server, ServerPlayer player, String cardId, boolean fake);

	/**
	 * Debug: fires a card for the first participant (in join order) now. {@link FireResult#SKIPPED} if nobody takes part.
	 *
	 * @deprecated fire for a player: {@link #fire(MinecraftServer, ServerPlayer, String, boolean)}. Removed in MP-8.
	 */
	@Deprecated(forRemoval = true)
	default FireResult fire(MinecraftServer server, String cardId, boolean fake) {
		List<ServerPlayer> participants = Services.watch().participants(server);
		return participants.isEmpty() ? FireResult.SKIPPED : fire(server, participants.getFirst(), cardId, fake);
	}

	/**
	 * Moves time forward by whole in-game days (see {@link GameClock#warp}) and applies what time alone does.
	 *
	 * @return summary lines for the command output (what the skipped time would have held); never empty
	 */
	List<String> timewarp(MinecraftServer server, int days);

	/** Play ticks since a card with this tag fired, or {@link Long#MAX_VALUE} if none ever did. */
	long ticksSinceTag(MinecraftServer server, CardTag tag);

	/** True during a forced quiet period. */
	boolean inQuiet(MinecraftServer server);

	/** Lines for {@code /a1016 state}. */
	List<String> debugLines(MinecraftServer server);

	/** The default until the director workstream installs its own: fires cards directly, nothing on its own. */
	final class Stub implements Director {
		private final Map<CardTag, Long> lastFired = new EnumMap<>(CardTag.class);

		@Override
		public void tick(MinecraftServer server) {
		}

		@Override
		public FireResult fire(MinecraftServer server, ServerPlayer player, String cardId, boolean fake) {
			Optional<EventCard> card = CardRegistry.get(cardId);
			if (card.isEmpty() || !Services.watch().isTargetable(player)) {
				return FireResult.SKIPPED;
			}
			boolean asFake = fake && card.get().hasFake();
			FireResult result = card.get().fire(new FireContext(player, player.level(), asFake, true, RandomSource.create()));
			if (result == FireResult.FIRED) {
				long now = GameClock.playTicks(server);
				card.get().tags().forEach(tag -> lastFired.put(tag, now));
				HerobrineEvents.CARD_FIRED.invoker().onCardFired(player, cardId, asFake);
			}
			return result;
		}

		@Override
		public List<String> timewarp(MinecraftServer server, int days) {
			GameClock.warp(server, days);
			return List.of("director: stub, only the clock moved (day " + GameClock.day(server) + ")");
		}

		@Override
		public long ticksSinceTag(MinecraftServer server, CardTag tag) {
			Long at = lastFired.get(tag);
			return at == null ? Long.MAX_VALUE : GameClock.playTicks(server) - at;
		}

		@Override
		public boolean inQuiet(MinecraftServer server) {
			return false;
		}

		@Override
		public List<String> debugLines(MinecraftServer server) {
			return List.of("director: stub (no automatic events)");
		}
	}
}
