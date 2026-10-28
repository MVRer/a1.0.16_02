package com.forzacode.a1016_02.world.sc2.s17;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.core.Attention;
import com.forzacode.a1016_02.core.Figure;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Visits;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the archive's rules read and write (docs/SELF_CREATED_2.md, feature 17): the live world's ({@link #live}) or a
 * test's own, so every rule runs the same in a game test with its own players, view and data.
 *
 * @param answers      the kit's answers (caps, fair-danger checks, retries out of view)
 * @param traces       every edit that is not an answer's (17a's records, 17c's move)
 * @param view         out of everyone's view
 * @param inside       who is inside a place now
 * @param people       every participant online (spectators left out by the rules)
 * @param data         the kit's saved data (visits, counts)
 * @param day          today's in-game day
 * @param now          the server's tick count
 * @param figure       the glimpse (31b)
 * @param config       {@code sc17}
 * @param watch        what players placed
 * @param known        the story's known players
 * @param quiet        in their director quiet
 * @param playTicks    their own play time
 * @param sink         what a done answer does that is not the kit's (17c): the event and the tension
 * @param days         the day books' days (R3-B's books; a test's own)
 */
record ArchiveEnv(Answers answers, TraceService traces, Answers.View view, Function<Remains.Place, List<ServerPlayer>> inside,
		Supplier<List<ServerPlayer>> people, Supplier<Sc2Data> data, LongSupplier day, LongSupplier now, Figure figure, Supplier<Sc17Config> config,
		PlayerWatch watch, Supplier<List<HerobrineState.Subject>> known, Predicate<UUID> quiet, ToLongFunction<UUID> playTicks, Answers.Sink sink,
		DayBooks.Days days) {

	/** Round 2's env (the books' own days). */
	ArchiveEnv(Answers answers, TraceService traces, Answers.View view, Function<Remains.Place, List<ServerPlayer>> inside,
			Supplier<List<ServerPlayer>> people, Supplier<Sc2Data> data, LongSupplier day, LongSupplier now, Figure figure, Supplier<Sc17Config> config,
			PlayerWatch watch, Supplier<List<HerobrineState.Subject>> known, Predicate<UUID> quiet, ToLongFunction<UUID> playTicks, Answers.Sink sink) {
		this(answers, traces, view, inside, people, data, day, now, figure, config, watch, known, quiet, playTicks, sink, DayBooks.Days.LIVE);
	}

	/** The live world's. */
	static ArchiveEnv live(MinecraftServer server) {
		TraceService traces = Services.traces();
		return new ArchiveEnv(Answers.LIVE, traces, traces::isOutOfView, place -> Visits.LIVE.inside(server, place),
				() -> Services.watch().participants(server), RemainsImpl.LIVE::data, () -> GameClock.day(server), server::getTickCount,
				Services.figure(), Sc17Config::get, Services.watch(), () -> HerobrineState.get(server).players(),
				uuid -> Services.director().inQuiet(server, uuid), uuid -> GameClock.playTicks(server, uuid), (level, place, answer, player) -> {
					HerobrineEvents.REMAINS_ANSWERED.invoker().onAnswered(level, place, answer, player.getUUID());
					Attention.raiseTension(server, player.getUUID(), Answers.TENSION, "s2 " + answer);
				});
	}

	/** True if this player may be answered at all now: not in their quiet, past their first hour. */
	boolean answerable(ServerPlayer player) {
		return !quiet.test(player.getUUID()) && playTicks.applyAsLong(player.getUUID()) >= Answers.FIRST_HOUR_TICKS;
	}
}
