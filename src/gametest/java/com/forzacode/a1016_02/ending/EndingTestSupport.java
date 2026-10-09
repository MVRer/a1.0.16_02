package com.forzacode.a1016_02.ending;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;

import org.jspecify.annotations.Nullable;

/**
 * Test support: {@link Ports} records what the ending asked of other workstreams (and answers as told), and
 * {@link Run} is one ending run with its own {@link HerobrineState}, {@link EndingState}, config, engine and a forced
 * clock, so tests never share the world's state. World edits use core's forced trace service (the test level has no
 * real players) and mobs go through the real MobTamper.
 */
final class EndingTestSupport {
	private EndingTestSupport() {
	}

	/** Records every call; answers from its fields. */
	static final class Ports implements EndingPorts {
		FireResult sightingResult = FireResult.FIRED;
		int sightings;
		boolean figureOut;
		/** Trap ids that arm when asked. */
		final Set<String> armable = new HashSet<>();
		final List<String> armed = new ArrayList<>();
		boolean trapArmed;
		/** The armed trap as the planner would report it. */
		EndingState.@Nullable Trap armedRef;
		/** armTrapInside: true if every spot the planner could pick is inside the bounds asked for. */
		boolean spotsInside = true;
		int disarms;
		long sinceJoin = Long.MAX_VALUE / 2;
		long sinceDirectorAccident = Long.MAX_VALUE / 2;
		boolean directorQuiet;
		@Nullable Bounds copyBounds;
		boolean copyExists;
		boolean copyFinished;
		int copyFinishes;
		@Nullable GlobalPos copySite;
		boolean stopSign = true;
		boolean signMoves = true;
		final List<GlobalPos> signMovedTo = new ArrayList<>();
		@Nullable GlobalPos cross;
		int f10Finished;
		boolean f20Ready;
		int f20Calls;
		final List<Float> duskFog = new ArrayList<>();

		@Override
		public FireResult lastSighting(ServerPlayer player) {
			sightings++;
			return sightingResult;
		}

		@Override
		public boolean figureOut(MinecraftServer server) {
			return figureOut;
		}

		@Override
		public boolean armTrap(ServerPlayer player, String trapId) {
			if (trapArmed || !armable.contains(trapId)) {
				return false;
			}
			armed.add(trapId);
			trapArmed = true;
			armedRef = new EndingState.Trap(trapId, GlobalPos.of(player.level().dimension(), player.blockPosition()));
			return true;
		}

		@Override
		public boolean armTrapInside(ServerPlayer player, String trapId, Bounds bounds) {
			if (trapArmed || !armable.contains(trapId) || !spotsInside) {
				return false;
			}
			armed.add(trapId);
			trapArmed = true;
			armedRef = new EndingState.Trap(trapId, GlobalPos.of(bounds.dimension(), bounds.box().getCenter()));
			return true;
		}

		@Override
		public Optional<EndingState.Trap> armedTrap(MinecraftServer server) {
			return trapArmed ? Optional.ofNullable(armedRef) : Optional.empty();
		}

		@Override
		public long ticksSinceJoin(ServerPlayer player) {
			return sinceJoin;
		}

		@Override
		public long ticksSinceDirectorAccident(MinecraftServer server) {
			return sinceDirectorAccident;
		}

		@Override
		public boolean directorQuiet(MinecraftServer server) {
			return directorQuiet;
		}

		@Override
		public boolean trapArmed() {
			return trapArmed;
		}

		@Override
		public void disarmTraps() {
			disarms++;
			trapArmed = false;
			armedRef = null;
		}

		@Override
		public boolean copyExists(MinecraftServer server) {
			return copyExists;
		}

		@Override
		public boolean finishCopy(MinecraftServer server) {
			copyFinishes++;
			return copyExists;
		}

		@Override
		public boolean copyFinished(MinecraftServer server) {
			return copyFinished;
		}

		@Override
		public Optional<GlobalPos> copySite(MinecraftServer server) {
			return Optional.ofNullable(copySite);
		}

		@Override
		public Optional<Bounds> copyBounds(MinecraftServer server) {
			return Optional.ofNullable(copyBounds);
		}

		@Override
		public boolean stopSignExists(MinecraftServer server) {
			return stopSign;
		}

		@Override
		public boolean moveStopSignToCross(MinecraftServer server, GlobalPos to) {
			if (!signMoves) {
				return false;
			}
			signMovedTo.add(to);
			return true;
		}

		@Override
		public Optional<GlobalPos> findCross(MinecraftServer server, GlobalPos death) {
			return Optional.ofNullable(cross);
		}

		@Override
		public boolean finishF10(MinecraftServer server) {
			f10Finished++;
			return true;
		}

		@Override
		public boolean placeF20(MinecraftServer server) {
			f20Calls++;
			return f20Ready;
		}

		@Override
		public void setDuskFog(MinecraftServer server, float level) {
			duskFog.add(level);
		}

		@Override
		public float stageDuskFog(Stage stage) {
			return 0.7F;
		}

		@Override
		public TraceService traces() {
			return Services.traces().forced();
		}

		@Override
		public MobTamper mobs() {
			return Services.mobs();
		}

		@Override
		public PlayerWatch watch() {
			return Services.watch();
		}
	}

	/** One run: private states, recording ports, a mock subject (not in the level) and a clock the test moves. */
	static final class Run {
		final GameTestHelper helper;
		final MinecraftServer server;
		final HerobrineState state = new HerobrineState();
		final EndingState data = new EndingState();
		final EndingConfig cfg = new EndingConfig();
		final Ports ports = new Ports();
		final EndingEngine engine = new EndingEngine(ports);
		final ServerPlayer player;
		final RandomSource random = RandomSource.create(42L);
		/** {@code GameClock.dayTicks}: starts at day 10, noon. */
		long now = 10 * GameClock.TICKS_PER_DAY + 6000;
		/** {@code GameClock.playTicks}: 20 hours in. */
		long play = 20L * 72000;

		Run(GameTestHelper helper) {
			this.helper = helper;
			this.server = helper.getLevel().getServer();
			this.player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
			state.setStage(server, Stage.TELLING);
		}

		EndingEngine.Ctx ctx() {
			return new EndingEngine.Ctx(server, player, state, data, cfg, now, play, random, false);
		}

		long today() {
			return Math.floorDiv(now, GameClock.TICKS_PER_DAY);
		}

		/** In-game days pass, and as much real play (a day is 20 real minutes). */
		void days(double days) {
			now += Math.round(days * GameClock.TICKS_PER_DAY);
			play += Math.round(days * GameClock.TICKS_PER_DAY);
		}

		/** Real play time passes (the in-game clock with it). */
		void minutes(double minutes) {
			long ticks = Math.round(minutes * 1200);
			now += ticks;
			play += ticks;
		}

		/** Facts where nothing commits: Stage 3, nothing done. */
		EndingFacts quiet() {
			return new EndingFacts(state.stage(), state.stopFired(), state.tellingStarted(), now, data.stopSeenAt(), data.lastTellingAt(),
					data.lastNamedAt(), data.lastReadAt(), -1, data.lastFogStareAt(), state.tellingCount(), data.tellingsSinceStop(), state.attention(),
					state.markedDeaths().size(), data.fragmentsBurned(), data.unburned().size(), false, data.housePeak(), data.housePeak(),
					data.ownBroken());
		}

		void tick() {
			engine.tick(ctx(), quiet());
		}

		void commit(EndingPath path) {
			engine.commit(ctx(), path, "test");
		}

		<E extends Enum<E>> E beat(Class<E> type, EndingPath path) {
			return EndingBeats.of(type, data.progress(path));
		}

		GlobalPos at(int x, int y, int z) {
			return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new net.minecraft.core.BlockPos(x, y, z)));
		}
	}

	/** A facts record with every field named, for the rule tests. */
	static EndingFacts facts(Stage stage, boolean stopFired, long now, long stopSeenAt, long lastTellingAt, long lastNamedAt, long lastReadAt,
			long lastTraceVisitDay, long lastFogStareAt, int tellingCount, int tellingsSinceStop, double attention, int markedDeaths, int burned,
			int unburned, boolean holds, int peak, int left, int broken) {
		return new EndingFacts(stage, stopFired, stage.atLeast(Stage.TELLING), now, stopSeenAt, lastTellingAt, lastNamedAt, lastReadAt,
				lastTraceVisitDay, lastFogStareAt, tellingCount, tellingsSinceStop, attention, markedDeaths, burned, unburned, holds, peak, left,
				broken);
	}
}
