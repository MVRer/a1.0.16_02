package com.forzacode.a1016_02.core;

import java.util.UUID;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Cross-workstream events. All fire on the server thread. */
public final class HerobrineEvents {
	private HerobrineEvents() {
	}

	/** Fired by {@link HerobrineState#setStage}. */
	public static final Event<StageChanged> STAGE_CHANGED = EventFactory.createArrayBacked(StageChanged.class,
			listeners -> (server, oldStage, newStage) -> {
				for (StageChanged listener : listeners) {
					listener.onStageChanged(server, oldStage, newStage);
				}
			});

	/** Fired by lore when the player writes about him. {@code namesHim} is true if the text names him. */
	public static final Event<Telling> TELLING = EventFactory.createArrayBacked(Telling.class,
			listeners -> (player, text, pos, namesHim) -> {
				for (Telling listener : listeners) {
					listener.onTelling(player, text, pos, namesHim);
				}
			});

	/** Fired by lore the first time a fragment is read. */
	public static final Event<FragmentRead> FRAGMENT_READ = EventFactory.createArrayBacked(FragmentRead.class,
			listeners -> (player, id) -> {
				for (FragmentRead listener : listeners) {
					listener.onFragmentRead(player, id);
				}
			});

	/** Fired by accident's {@link DeathMarker} when a death is marked. */
	public static final Event<MarkedDeathEvent> MARKED_DEATH = EventFactory.createArrayBacked(MarkedDeathEvent.class,
			listeners -> (player, cause, pos) -> {
				for (MarkedDeathEvent listener : listeners) {
					listener.onMarkedDeath(player, cause, pos);
				}
			});

	/** Fired by the director after a card returns {@link FireResult#FIRED}. */
	public static final Event<CardFired> CARD_FIRED = EventFactory.createArrayBacked(CardFired.class,
			listeners -> (player, cardId, fake) -> {
				for (CardFired listener : listeners) {
					listener.onCardFired(player, cardId, fake);
				}
			});

	/**
	 * Fired by core once per change of a player's participation (D-058): {@code true} when they start (they joined
	 * as a non-spectator, or left spectator mode), {@code false} when they stop (they disconnected while a
	 * participant, or became a spectator). Never twice in a row with the same value for one player; nothing for a
	 * spectator who joins and leaves; nothing on server stop; nothing for a death or an ordinary respawn (a hardcore
	 * respawn as a spectator is a stop). Starts fire at the end of the tick, from core's tick; a disconnect fires
	 * from Fabric's DISCONNECT, while the player is still listed. Core's own bookkeeping is done first: on {@code true}
	 * the player is a known player ({@link HerobrineState#player}) and fully online, so
	 * {@link PlayerWatch#participant(MinecraftServer, UUID)} finds them; on {@code false} their play clock has
	 * stopped. Use it for sessions instead of Fabric's JOIN and DISCONNECT.
	 */
	public static final Event<Participation> PARTICIPATION = EventFactory.createArrayBacked(Participation.class,
			listeners -> (server, player, participating) -> {
				for (Participation listener : listeners) {
					listener.onParticipation(server, player, participating);
				}
			});

	@FunctionalInterface
	public interface Participation {
		void onParticipation(MinecraftServer server, UUID player, boolean participating);
	}

	@FunctionalInterface
	public interface StageChanged {
		void onStageChanged(MinecraftServer server, Stage oldStage, Stage newStage);
	}

	@FunctionalInterface
	public interface Telling {
		void onTelling(ServerPlayer player, String text, BlockPos pos, boolean namesHim);
	}

	@FunctionalInterface
	public interface FragmentRead {
		void onFragmentRead(ServerPlayer player, String id);
	}

	@FunctionalInterface
	public interface MarkedDeathEvent {
		void onMarkedDeath(ServerPlayer player, String cause, BlockPos pos);
	}

	@FunctionalInterface
	public interface CardFired {
		void onCardFired(ServerPlayer player, String cardId, boolean fake);
	}
}
