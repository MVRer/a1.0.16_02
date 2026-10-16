package com.forzacode.a1016_02.core;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
	 * Moves time forward by whole in-game days (see {@link GameClock#warp}) and applies what time alone does.
	 *
	 * @return summary lines for the command output (what the skipped time would have held); never empty
	 */
	List<String> timewarp(MinecraftServer server, int days);

	/**
	 * The world's answer: ticks of the world clock ({@link GameClock#playTicks(MinecraftServer)}) since a card with
	 * this tag fired for anyone, or {@link Long#MAX_VALUE} if none ever did. For what the whole story waits on (the
	 * endings' "no accident lately"). A gap that is one player's own pacing (D-059) asks
	 * {@link #ticksSinceTag(MinecraftServer, UUID, CardTag)}.
	 */
	long ticksSinceTag(MinecraftServer server, CardTag tag);

	/**
	 * That player's answer (D-059, D-060.3): ticks of their own clock ({@link GameClock#playTicks(MinecraftServer, UUID)})
	 * since a card with this tag fired for them or was felt by them (it fired for someone close by), or
	 * {@link Long#MAX_VALUE} if none ever did, or for someone the director never paced. Default: the world's answer.
	 */
	default long ticksSinceTag(MinecraftServer server, UUID player, CardTag tag) {
		return ticksSinceTag(server, tag);
	}

	/**
	 * True while any participant is in a forced quiet of their own (D-059): the conservative answer for one flag.
	 * Who is in quiet is per player: {@link #inQuiet(MinecraftServer, UUID)}.
	 */
	boolean inQuiet(MinecraftServer server);

	/** True while this player is in a forced quiet of their own. Default: the world's answer. */
	default boolean inQuiet(MinecraftServer server, UUID player) {
		return inQuiet(server);
	}

	/** Lines for {@code /a1016 state}. */
	List<String> debugLines(MinecraftServer server);

	/**
	 * A blind moment's recheck (D-077): {@code player}'s client has had a counting screen open
	 * ({@link PlayerWatch#blindTicks} &gt; 0). Core calls it {@link PlayerWatch#BLIND_FIRST_RECHECK_TICKS} (30) ticks after
	 * the moment starts and every {@link PlayerWatch#BLIND_RECHECK_EVERY_TICKS} (20) while it lasts, at most
	 * {@link PlayerWatch#BLIND_MAX_RECHECKS} (10) times, on the server thread, from the end of the server tick before
	 * {@link #tick}. For that player only: fire their held or parked card if its moment fits now ({@code contextFits}) and
	 * every gate the normal decision uses passes, {@link CardTag#BLIND} cards first; never roll or draw. Default: nothing
	 * (the stub); the director implements it.
	 */
	default void recheck(MinecraftServer server, ServerPlayer player) {
	}

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
