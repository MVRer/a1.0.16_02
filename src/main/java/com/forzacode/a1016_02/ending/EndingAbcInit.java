package com.forzacode.a1016_02.ending;

import java.util.List;

import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.entity.FigureApi;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import org.jspecify.annotations.Nullable;

/**
 * Endings A, B and C at runtime: the check every {@code checkSeconds}, the event hooks (telling, fragment reads,
 * marked deaths, the group's own blocks), the "Missing first block" card and {@code /a1016 ending}. Anyone who takes
 * part counts (D-058, D-064: one story, one ending per world); the check runs while at least one participant is
 * online, and the beats act on whoever is in it at that moment ({@link EndingApi#players}).
 */
public final class EndingAbcInit {
	private static final EndingEngine ENGINE = new EndingEngine(EndingPorts.LIVE);
	private static final RandomSource RANDOM = RandomSource.create();

	/** One live moment: the context and the facts the rules read. */
	record Snapshot(EndingEngine.Ctx ctx, EndingFacts facts) {
	}

	private EndingAbcInit() {
	}

	public static EndingEngine engine() {
		return ENGINE;
	}

	static void init() {
		Director.register(new MissingFirstBlockCard());
		EndingCommands.register(ENGINE);
		EndingReads.register();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> EndingConfig.get());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			EndingWatch.clear();
			EndingReads.clear();
			ENGINE.reset();
		});
		ServerTickEvents.END_SERVER_TICK.register(EndingAbcInit::tick);
		ServerTickEvents.END_SERVER_TICK.register(EndingReads::tick);
		// Sessions are per player (D-062.4): whatever the ending keeps per session for someone ends with their part.
		HerobrineEvents.PARTICIPATION.register((server, player, participating) -> {
			if (!participating) {
				EndingReads.forget(player);
			}
		});

		HerobrineEvents.TELLING.register((player, text, pos, namesHim) -> {
			if (!EndingApi.takesPart(player)) {
				return;
			}
			MinecraftServer server = player.level().getServer();
			EndingWatch.onTelling(namesHim, EndingState.get(server), now(server));
			ENGINE.onTelling(ctx(server, false), namesHim);
		});
		FigureApi.STARED.register((player, him) -> {
			MinecraftServer server = player.level().getServer();
			EndingWatch.onStared(player, EndingState.get(server), now(server), EndingApi::takesPart);
		});
		HerobrineEvents.FRAGMENT_READ.register((player, id) -> {
			if (EndingApi.takesPart(player)) {
				EndingWatch.onFragmentRead(EndingState.get(player.level().getServer()), now(player.level().getServer()));
			}
		});
		// Whoever's death it is (it is recorded as theirs before the event): every death not handled yet, once.
		HerobrineEvents.MARKED_DEATH.register((player, cause, pos) -> ENGINE.onMarkedDeath(ctx(player.level().getServer(), false)));

		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				!(player instanceof ServerPlayer serverPlayer) || EndingWatch.beforeBreak(level, serverPlayer, pos));
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> EndingWatch.afterBreak(level, pos));
		PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, blockEntity) -> EndingWatch.canceledBreak(level, pos));
	}

	/** {@code GameClock.dayTicks}: the ending's clock. */
	static long now(MinecraftServer server) {
		return GameClock.dayTicks(server);
	}

	/** A live context: who is in it now (fresh), the shared state, the ending's state and the config. */
	static EndingEngine.Ctx ctx(MinecraftServer server, boolean force) {
		return ctx(server, force, null);
	}

	/**
	 * A live context. A debug command acts on {@code actor} (the player running it, or the one it names, D-062.9);
	 * otherwise on whoever is in it, in join order (from the console: the first participant first).
	 */
	static EndingEngine.Ctx ctx(MinecraftServer server, boolean force, @Nullable ServerPlayer actor) {
		List<ServerPlayer> players = actor != null ? List.of(actor) : EndingApi.players(server);
		return new EndingEngine.Ctx(server, players, HerobrineState.get(server), EndingState.get(server), EndingConfig.get(), now(server),
				GameClock.playTicks(server), RANDOM, force, server.isHardcore());
	}

	/** The live context and facts right now (status). */
	static Snapshot snapshot(MinecraftServer server) {
		EndingEngine.Ctx c = ctx(server, false);
		return new Snapshot(c, EndingWatch.facts(server, c.players(), c.state(), c.data(), c.cfg(), ENGINE.ports(), c.now()));
	}

	private static void tick(MinecraftServer server) {
		EndingConfig cfg = EndingConfig.get();
		if (server.getTickCount() % cfg.checkTicks() != 0 || Services.watch().participants(server).isEmpty()) {
			return;
		}
		EndingEngine.Ctx c = ctx(server, false);
		EndingWatch.sample(server, c.players(), c.state(), c.data(), cfg, ENGINE.ports().watch(), c.now());
		ENGINE.tick(c, EndingWatch.facts(server, c.players(), c.state(), c.data(), cfg, ENGINE.ports(), c.now()));
	}
}
