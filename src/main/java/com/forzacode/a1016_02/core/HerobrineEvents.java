package com.forzacode.a1016_02.core;

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
