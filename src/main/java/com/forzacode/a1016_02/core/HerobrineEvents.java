package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;


import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

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
	 * respawn as a spectator is a stop). Always on the server thread (R12-4). Starts fire at the end of the tick,
	 * from core's tick; a disconnect fires while the player is still listed: from Fabric's DISCONNECT when the server
	 * makes it, and when the client does (a quit, a crash, a timeout: Fabric runs DISCONNECT on the network thread then)
	 * on the server thread's next turn or as vanilla takes the player off the list, whichever comes first (core's
	 * {@code PlayerList.remove} hook). Core's own bookkeeping is done first: on {@code true}
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

	/** {@link #FIGURE_LEFT}'s path holds at most this many samples (the first ones, oldest first). */
	public static final int FIGURE_PATH_MAX = 24;

	/**
	 * Fired by entity once for each figure that is gone (its {@code HimEntity.gone}), whichever way it went, after it
	 * is gone. {@code stoodAt} is where his feet stood still (his spawn feet, or where he stopped before he left) and
	 * {@code yaw} where he faced there; {@code path} is his feet on his way out, sampled every 2 blocks, oldest first,
	 * empty if he vanished or went under; {@code variant} the variant's id ({@code "ridge"}, {@code "close"}, ...);
	 * {@code appearedFor} the player he appeared for (null if nobody); {@code seen} whether anyone ever saw him (his
	 * {@code everSeen}). The invoker hands every listener the same unmodifiable copy of the path, cut to the first
	 * {@link #FIGURE_PATH_MAX}, and a listener that throws is logged and never stops the others or the figure's
	 * removal. Firing it exactly once per figure is the firer's part. Listeners must be cheap.
	 */
	public static final Event<FigureLeft> FIGURE_LEFT = EventFactory.createArrayBacked(FigureLeft.class,
			listeners -> (level, stoodAt, yaw, path, variant, appearedFor, seen) -> {
				List<Vec3> samples = figurePath(path);
				for (FigureLeft listener : listeners) {
					try {
						listener.onFigureLeft(level, stoodAt, yaw, samples, variant, appearedFor, seen);
					} catch (RuntimeException e) {
						ScLog.error("[a1016] sc: a listener failed (f)", e);
					}
				}
			});

	/**
	 * Fired by core after {@link TraceLedger} records an entry: every entry {@link TraceService}'s edits write
	 * (removals, moves, conversions, broken dependents, sign text, stack edits; each entry of a committed batch once,
	 * after the whole batch is applied and ledgered) and {@link TraceLedger#recordName}'s. Never for {@code leave} or
	 * {@code leaveStack} (nothing is ledgered), never for an entry rewritten in place (a give-back that became a move, a
	 * stack now worn by a mob), and never for the reverse edits of {@link TraceService#undo}, which are closed at once
	 * (only what such an undo leaves ledgered, its broken neighbours, fires, after it). {@code placedByPlayer} is
	 * {@link PlayerWatch#wasPlacedByPlayer} at the entry's position read before the edit (the container's for a stack
	 * edit; false for NAME). A listener that throws is logged and never stops the others or the edit. Listeners must be
	 * cheap: this runs inside every edit.
	 */
	public static final Event<Traced> TRACED = EventFactory.createArrayBacked(Traced.class,
			listeners -> (level, entry, placedByPlayer) -> {
				for (Traced listener : listeners) {
					try {
						listener.onTraced(level, entry, placedByPlayer);
					} catch (RuntimeException e) {
						ScLog.error("[a1016] sc: a listener failed (t)", e);
					}
				}
			});

	/**
	 * Fired by world (docs/SELF_CREATED_2.md, SC2-0b) when a participant is first seen inside a known place's interior
	 * ({@link Remains#containing}), at most once per visit: its scan looks at every participant once a second, so a step
	 * in and out between two looks is no visit. Never for a spectator, never while the layer is switched off. A listener
	 * that throws is logged and never stops the others. Listeners must be cheap.
	 */
	public static final Event<RemainsVisit> REMAINS_ENTERED = EventFactory.createArrayBacked(RemainsVisit.class,
			listeners -> (level, place, player) -> {
				for (RemainsVisit listener : listeners) {
					try {
						listener.onVisit(level, place, player);
					} catch (RuntimeException e) {
						ScLog.error("[a1016] s2: a listener failed (v)", e);
					}
				}
			});

	/**
	 * Fired by world once a visit ends: the participant is seen outside that interior (or inside another one, which
	 * then enters), changed level, became a spectator or stopped taking part (fired while they are still listed). Every
	 * {@link #REMAINS_ENTERED} gets one, but on a server stop. {@code level} is the place's. Listeners must be cheap.
	 */
	public static final Event<RemainsVisit> REMAINS_LEFT = EventFactory.createArrayBacked(RemainsVisit.class,
			listeners -> (level, place, player) -> {
				for (RemainsVisit listener : listeners) {
					try {
						listener.onVisit(level, place, player);
					} catch (RuntimeException e) {
						ScLog.error("[a1016] s2: a listener failed (v)", e);
					}
				}
			});

	/**
	 * Fired by world after one of his places answered a visit (its answer's edits were applied and ledgered), once for
	 * each player it happened to (everyone inside then). {@code answer} is the answer's id ({@code "16b"}, {@code "17b"},
	 * ...). A listener that throws is logged and never stops the others. Listeners must be cheap.
	 */
	public static final Event<RemainsAnswered> REMAINS_ANSWERED = EventFactory.createArrayBacked(RemainsAnswered.class,
			listeners -> (level, place, answer, player) -> {
				for (RemainsAnswered listener : listeners) {
					try {
						listener.onAnswered(level, place, answer, player);
					} catch (RuntimeException e) {
						ScLog.error("[a1016] s2: a listener failed (a)", e);
					}
				}
			});

	/** {@link #FIGURE_LEFT}'s path as listeners get it: unmodifiable, no nulls, at most the first {@link #FIGURE_PATH_MAX}. */
	static List<Vec3> figurePath(@Nullable List<Vec3> path) {
		if (path == null || path.isEmpty()) {
			return List.of();
		}
		List<Vec3> samples = new ArrayList<>(Math.min(path.size(), FIGURE_PATH_MAX));
		for (Vec3 sample : path) {
			if (samples.size() >= FIGURE_PATH_MAX) {
				break;
			}
			if (sample != null) {
				samples.add(sample);
			}
		}
		return Collections.unmodifiableList(samples);
	}

	@FunctionalInterface
	public interface FigureLeft {
		void onFigureLeft(ServerLevel level, Vec3 stoodAt, float yaw, List<Vec3> path, String variant, @Nullable UUID appearedFor, boolean seen);
	}

	@FunctionalInterface
	public interface Traced {
		void onTraced(ServerLevel level, TraceLedger.Entry entry, boolean placedByPlayer);
	}

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

	@FunctionalInterface
	public interface RemainsVisit {
		void onVisit(ServerLevel level, Remains.Place place, ServerPlayer player);
	}

	@FunctionalInterface
	public interface RemainsAnswered {
		void onAnswered(ServerLevel level, Remains.Place place, String answer, UUID player);
	}
}
