package com.forzacode.a1016_02.ending;

import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import org.jspecify.annotations.Nullable;

/**
 * Endings A, B and C at runtime: the check every {@code checkSeconds}, the event hooks (telling, fragment reads,
 * marked deaths, the player's own blocks), the "Missing first block" card and {@code /a1016 ending}. Only the
 * subject counts, and nothing runs while the subject is offline.
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

		HerobrineEvents.TELLING.register((player, text, pos, namesHim) -> {
			if (!Services.watch().isSubject(player)) {
				return;
			}
			MinecraftServer server = player.level().getServer();
			EndingWatch.onTelling(player, namesHim, EndingState.get(server), now(server));
			ENGINE.onTelling(ctx(server, false), namesHim);
		});
		HerobrineEvents.FRAGMENT_READ.register((player, id) -> EndingWatch.onFragmentRead(player, EndingState.get(player.level().getServer()),
				now(player.level().getServer())));
		HerobrineEvents.MARKED_DEATH.register((player, cause, pos) -> {
			if (!Services.watch().isSubject(player)) {
				return;
			}
			MinecraftServer server = player.level().getServer();
			int count = HerobrineState.get(server).markedDeaths().size();
			ENGINE.onMarkedDeath(ctx(server, false), GlobalPos.of(player.level().dimension(), pos.immutable()), count, server.isHardcore());
		});

		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				!(player instanceof ServerPlayer serverPlayer) || EndingWatch.beforeBreak(level, serverPlayer, pos));
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> EndingWatch.afterBreak(level, pos));
		PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, blockEntity) -> EndingWatch.canceledBreak(level, pos));
	}

	/** {@code GameClock.dayTicks}: the ending's clock. */
	static long now(MinecraftServer server) {
		return GameClock.dayTicks(server);
	}

	/** A live context: the subject (or null), the shared state, the ending's state and the config. */
	static EndingEngine.Ctx ctx(MinecraftServer server, boolean force) {
		@Nullable ServerPlayer player = Services.watch().subject(server).orElse(null);
		return new EndingEngine.Ctx(server, player, HerobrineState.get(server), EndingState.get(server), EndingConfig.get(), now(server),
				GameClock.playTicks(server), RANDOM, force);
	}

	/** The live context and facts right now (status). */
	static Snapshot snapshot(MinecraftServer server) {
		EndingEngine.Ctx c = ctx(server, false);
		return new Snapshot(c, EndingWatch.facts(server, c.player(), c.state(), c.data(), c.cfg(), ENGINE.ports(), c.now()));
	}

	private static void tick(MinecraftServer server) {
		EndingConfig cfg = EndingConfig.get();
		if (server.getTickCount() % cfg.checkTicks() != 0) {
			return;
		}
		ServerPlayer player = Services.watch().subject(server).orElse(null);
		if (player == null) {
			return;
		}
		EndingEngine.Ctx c = ctx(server, false);
		EndingWatch.sample(player, c.state(), c.data(), cfg, ENGINE.ports().watch(), c.now());
		ENGINE.tick(c, EndingWatch.facts(server, player, c.state(), c.data(), cfg, ENGINE.ports(), c.now()));
	}
}
