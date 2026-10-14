package com.forzacode.a1016_02.ending.d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.ending.EndingApi;
import com.forzacode.a1016_02.lore.LoreApi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ending D's chain, step by step (DESIGN.md "Ending D"). Each check looks at everyone in it (D-064: any participant
 * can do a step, progress is shared): has the current step been done by any of them, and each one's danger. Steps only
 * count in order, and one check moves the chain at most one step, so two players doing the same step in one tick
 * commit it once; what anyone did earlier (logs cut, signs written, torches placed) counts once its step comes.
 * Nothing here keeps a player across checks (D-062.1). Server thread only.
 */
public final class Chain {
	private static long nextStairTry;
	private static long nextF25;
	private static long nextLoss;
	private static Stair.Attempt lastStair = new Stair.Attempt(Stair.Status.WAITING_FOR_LORE, "not started (waits for F28 to be read)");

	private Chain() {
	}

	static void check(MinecraftServer server, EndingDState data, EndingDConfig cfg) {
		List<ServerPlayer> players = EndingApi.players(server);
		if (players.isEmpty()) {
			return;
		}
		HerobrineState state = HerobrineState.get(server);
		background(server, data, cfg, state, server.getTickCount());
		check(server, data, cfg, players, state.fragmentsRead()::contains, View.TRACES, deadFriends(server, state));
		publish(state, data, GameClock.day(server));
	}

	/**
	 * The shared flags lore's late clues read (D-066 M7): {@code ending:d_step=<n>} and {@code ending:d_step_day=<day>}
	 * (the world clock's in-game day the chain got to step n), updated the check it advances. Step 1's clock starts on
	 * the day F23 is first read: nothing is published before that. An old save in the middle of the chain starts its
	 * step's clock on the first check. True if the flags changed.
	 */
	public static boolean publish(HerobrineState state, EndingDState data, long today) {
		Step step = data.step();
		if (step == Step.MAP && !state.fragmentsRead().contains("F23")) {
			return false;
		}
		String stepFlag = EndingApi.D_STEP_FLAG + step.number();
		if (state.hasFlag(stepFlag) && state.flags().stream().anyMatch(f -> f.startsWith(EndingApi.D_STEP_DAY_FLAG))) {
			return false;
		}
		for (String flag : List.copyOf(state.flags())) {
			if (flag.startsWith(EndingApi.D_STEP_FLAG) || flag.startsWith(EndingApi.D_STEP_DAY_FLAG)) {
				state.setFlag(flag, false);
			}
		}
		state.setFlag(stepFlag, true);
		state.setFlag(EndingApi.D_STEP_DAY_FLAG + today, true);
		return true;
	}

	/**
	 * Known players whose line a hardcore marked death finished (D-063): the group may light one more torch for each of
	 * them in step 5.
	 */
	static int deadFriends(MinecraftServer server, HerobrineState state) {
		int count = 0;
		for (UUID player : EndingApi.finishedLines(server)) {
			if (state.player(player).isPresent()) {
				count++;
			}
		}
		return count;
	}

	/** One check of the current step for this one player (tests), with no dead friends. */
	public static void check(MinecraftServer server, EndingDState data, EndingDConfig cfg, ServerPlayer player, Predicate<String> read, View view) {
		check(server, data, cfg, List.of(player), read, view, 0);
	}

	/**
	 * One check of the current step for everyone in it ({@code players}, fresh this tick). {@code read} says which
	 * fragments were read (the world's: one reader unlocks it for all, D-058); {@code view} is the out-of-view question
	 * the dangers ask first (core asks again on every edit); {@code deadFriends} is how many friends' lines are finished
	 * (step 5).
	 */
	public static void check(MinecraftServer server, EndingDState data, EndingDConfig cfg, List<ServerPlayer> players, Predicate<String> read, View view,
			int deadFriends) {
		if (players.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		Cairn.watch(players, data, cfg);
		Step step = data.step();
		switch (step) {
			case MAP -> {
				Camp.danger(players, view, cfg);
				if (read.test("F28") && players.stream().anyMatch(Chain::inGrove)) {
					advance(server, data, step);
				}
			}
			case GROVE -> {
				Grove.dangers(players, view, cfg);
				if (read.test("F30") && data.groveLogs() >= cfg.groveLogsMin) {
					advance(server, data, step);
				}
			}
			case TAKE_BACK -> {
				if (view == View.TRACES) {
					// Accident's own planner, with its own view rules (not in tests with fixed viewpoints).
					Cairn.danger(players, data, cfg);
				}
				if (Cairn.away(server, players, data, cfg)) {
					advance(server, data, step);
				}
			}
			case UNDER_SEED -> {
				Optional<StairPlan> plan = data.stair().filter(StairPlan::complete);
				if (plan.isEmpty()) {
					return;
				}
				boolean down = false;
				for (ServerPlayer player : players) {
					if (!plan.get().dimension().equals(player.level().dimension())) {
						continue;
					}
					if (!data.has(EndingDState.FLOODED) && Stair.inShaftBelow(player, plan.get(), cfg.floodDepthBlocks)
							&& Stair.flood(player, plan.get(), view)) {
						data.set(EndingDState.FLOODED, true);
					}
					down |= plan.get().inChamber(player.blockPosition());
				}
				if (down) {
					advance(server, data, step);
				}
			}
			case TORCHES, SENTENCE, CROSS -> chamber(server, data, cfg, players, step, now, view, deadFriends);
			case LAST_MINUTE, AFTERWARD -> {
			}
		}
	}

	/** The stair, built early once F28 is read; then F25 offered to lore. */
	private static void background(MinecraftServer server, EndingDState data, EndingDConfig cfg, HerobrineState state, long now) {
		boolean complete = data.stair().map(StairPlan::complete).orElse(false);
		if (!complete && state.fragmentsRead().contains("F28") && now >= nextStairTry) {
			lastStair = Stair.build(server, data, Services.traces(), cfg);
			nextStairTry = now + EndingDConfig.ticks(lastStair.status() == Stair.Status.BUILT_SEGMENT ? cfg.stairSegmentSeconds : cfg.stairRetrySeconds);
		}
		if (complete && now >= nextF25 && !state.fragmentsPlaced().containsKey("F25")) {
			nextF25 = now + EndingDConfig.ticks(cfg.stairRetrySeconds);
			data.stair().ifPresent(plan -> Stair.offerF25(server, plan));
		}
		if (complete) {
			// The stair is finished: lore learns the chamber (F35's plates and F36's note go in it, out of view, once each;
			// a sign written there is not near his traces). Cheap and idempotent, so an old save's finished stair counts too.
			data.stair().ifPresent(plan -> LoreApi.chamberBuilt(server, plan.dimension(), plan.chamberBox()));
		}
	}

	/**
	 * Steps 5 to 7: down in the chamber (in the stair's own dimension, whoever is elsewhere). The room is read once per
	 * check; each one down there or near it has their own danger (the fall after naming); the stair loses a block while
	 * anyone is down.
	 */
	private static void chamber(MinecraftServer server, EndingDState data, EndingDConfig cfg, List<ServerPlayer> players, Step step, long now, View view,
			int deadFriends) {
		Optional<StairPlan> found = data.stair().filter(StairPlan::complete);
		ServerLevel level = found.map(p -> server.getLevel(p.dimension())).orElse(null);
		if (found.isEmpty() || level == null) {
			return;
		}
		StairPlan plan = found.get();
		List<ServerPlayer> near = new ArrayList<>();
		boolean anyDown = false;
		for (ServerPlayer player : players) {
			if (!plan.dimension().equals(player.level().dimension())) {
				continue;
			}
			boolean down = plan.inChamber(player.blockPosition()) || Stair.inShaftBelow(player, plan, 0);
			if (down || player.blockPosition().distSqr(plan.twin()) <= 48 * 48) {
				near.add(player);
				anyDown |= down;
			}
		}
		if (near.isEmpty()) {
			return;
		}
		List<Chamber.Written> signs = Chamber.signs(level, plan);
		Chamber.watchNaming(server, data, signs);
		for (ServerPlayer player : near) {
			Chamber.namedFall(player, data, view);
		}
		if (anyDown && now >= nextLoss) {
			double seconds = step == Step.CROSS ? cfg.stairLossFastSeconds : cfg.stairLossSeconds;
			nextLoss = now + EndingDConfig.ticks(seconds);
			Stair.loseBlock(level, data, plan, view);
		}
		switch (step) {
			case TORCHES -> {
				if (torchesCount(Chamber.torches(level, plan), cfg, deadFriends)) {
					advance(server, data, step);
				}
			}
			case SENTENCE -> {
				List<String> sentences = Chamber.sentences();
				if (Chamber.finished(signs, plan, cfg, sentences).isPresent()) {
					advance(server, data, step);
				}
			}
			case CROSS -> {
				if (Chamber.cross(level, plan, data).isPresent()) {
					A1016_02.LOGGER.info("[a1016] ending d: the last plank is on his cross");
					// Once for the world: start() moves the step on at once, so no one else starts it again.
					LastMinute.start(server, data, false, near.getFirst().blockPosition());
				}
			}
			default -> {
			}
		}
	}

	/**
	 * Step 5: exactly {@code torchesRequired} torches (one per dead list member), or exactly that many plus one for each
	 * friend whose line was finished (a group may light one for a dead friend). Any other count does nothing.
	 */
	public static boolean torchesCount(int torches, EndingDConfig cfg, int deadFriends) {
		return torches == cfg.torchesRequired || deadFriends > 0 && torches == cfg.torchesRequired + deadFriends;
	}

	static boolean inGrove(ServerPlayer player) {
		return Grove.contains(player.level().getServer(), GlobalPos.of(player.level().dimension(), player.blockPosition()));
	}

	static void advance(MinecraftServer server, EndingDState data, Step from) {
		if (data.step() != from) {
			return;
		}
		data.setStep(from.next());
		A1016_02.LOGGER.info("[a1016] ending d: step {} done on day {}, now {}", from, GameClock.day(server), from.next());
	}

	/** The status lines: the current step and what is missing. */
	static List<String> status(MinecraftServer server, EndingDState data, EndingDConfig cfg) {
		List<String> lines = new ArrayList<>();
		HerobrineState state = HerobrineState.get(server);
		Step step = data.step();
		lines.add("Ending D, step " + step + (state.hasFlag(EndingDInit.COMPLETE_FLAG) ? ", complete on day " + data.completeDay() : ""));
		List<ServerPlayer> players = EndingApi.players(server);
		lines.add("in it: " + (players.isEmpty() ? "nobody" : players.stream().map(p -> p.getName().getString()).collect(Collectors.joining(", ")))
				+ "; first blocks owner: " + state.owner(EndingApi.FIRST_BLOCKS_OWNER).flatMap(state::player).map(HerobrineState.Subject::name)
						.orElse("not chosen"));
		boolean f28 = state.fragmentsRead().contains("F28");
		lines.add("stair: " + data.stair().map(p -> p.complete() ? "built at " + p.twin().toShortString() + " (" + p.stairs().size() + " steps, top "
				+ p.yTop() + ")" : "building, down to " + (p.builtTo() == Integer.MAX_VALUE ? "nothing yet" : p.builtTo())).orElse(lastStair.detail())
				+ (data.stair().isPresent() && !data.stair().get().complete() ? " / " + lastStair.detail() : ""));
		switch (step) {
			case MAP -> {
				lines.add("missing: " + (f28 ? "" : "F28 read (the map); ") + "reach the untouched grove at "
						+ Grove.center(server).map(c -> c.pos().toShortString()).orElse("(not chosen yet)")
						+ (players.stream().anyMatch(Chain::inGrove) ? " (someone is in it now)" : ""));
			}
			case GROVE -> {
				boolean f30 = state.fragmentsRead().contains("F30");
				lines.add("missing: " + (f30 ? "" : "F30 read on the oldest trunk; ") + "poplar logs cut in the grove " + data.groveLogs() + "/"
						+ cfg.groveLogsMin);
			}
			case TAKE_BACK -> {
				Optional<GlobalPos> cairn = Cairn.center(server);
				List<String> carrying = new ArrayList<>();
				for (ServerPlayer p : players) {
					if (Marks.carries(p, Marks.FIRST_BLOCK)) {
						String dist = cairn.filter(c -> c.dimension().equals(p.level().dimension()))
								.map(c -> String.format(Locale.ROOT, " %.0f/%d blocks from the cairn", Math.sqrt(c.pos().distToCenterSqr(p.position())),
										cfg.carryAwayBlocks)).orElse(" in another dimension");
						carrying.add(p.getName().getString() + dist);
					}
				}
				long placed = data.tracked().stream().filter(t -> t.mark().equals(Marks.FIRST_BLOCK)).count();
				lines.add("cairn: " + cairn.map(c -> c.pos().toShortString()).orElse("F13 not placed") + "; first block out cleanly: "
						+ data.has(EndingDState.FIRST_TAKEN) + " (at the cairn: " + data.has(EndingDState.FIRST_NEAR) + ", offering this visit: "
						+ Cairn.offering() + "); carried by " + (carrying.isEmpty() ? "nobody" : String.join(", ", carrying)) + "; placed " + placed
						+ "x; lure armed: " + data.has(EndingDState.CAIRN_ARMED));
			}
			case UNDER_SEED -> lines.add("missing: dig through the seed pyramid's floor and go down to the chamber; flooded: " + data.has(EndingDState.FLOODED));
			case TORCHES, SENTENCE, CROSS -> {
				data.stair().filter(StairPlan::complete).ifPresent(plan -> {
					ServerLevel level = server.getLevel(plan.dimension());
					if (level == null) {
						return;
					}
					int dead = deadFriends(server, state);
					lines.add("torches in the chamber: " + Chamber.torches(level, plan) + "/" + cfg.torchesRequired
							+ (dead > 0 ? " or " + (cfg.torchesRequired + dead) : "") + "; stair blocks left above: "
							+ Stair.standing(level, plan) + "/" + plan.stairs().stream().filter(s -> s.getY() >= plan.chamberCeil()).count());
					List<String> sentences = Chamber.sentences();
					lines.add("sentence under the twin: " + (Chamber.finished(Chamber.signs(level, plan), plan, cfg, sentences).isPresent() ? "written"
							: "missing") + "; named him there: " + data.has(EndingDState.NAMED));
					long firsts = data.tracked().stream().filter(t -> t.mark().equals(Marks.FIRST_BLOCK)).count();
					long wood = data.tracked().stream().filter(t -> t.mark().equals(Marks.GROVE_WOOD)).count();
					lines.add("cross: " + (Chamber.cross(level, plan, data).isPresent() ? "standing" : "missing") + " (first block placed "
							+ firsts + "x, grove wood placed " + wood + "x)");
				});
			}
			case LAST_MINUTE -> lines.add("last minute: " + LastMinute.describe());
			case AFTERWARD -> lines.add("afterward: " + Afterward.describe(data));
		}
		return lines;
	}

	static void clear() {
		nextStairTry = 0;
		nextF25 = 0;
		nextLoss = 0;
		lastStair = new Stair.Attempt(Stair.Status.WAITING_FOR_LORE, "not started (waits for F28 to be read)");
	}

	/** Debug: builds the whole stair now, out of view or not. */
	static Stair.Attempt buildNow(MinecraftServer server, EndingDState data, EndingDConfig cfg) {
		Stair.Attempt attempt = lastStair;
		for (int i = 0; i < 128; i++) {
			attempt = Stair.build(server, data, Services.traces().forced(), cfg);
			if (attempt.status() != Stair.Status.BUILT_SEGMENT && attempt.status() != Stair.Status.IN_VIEW) {
				break;
			}
		}
		lastStair = attempt;
		return attempt;
	}

	static BlockPos twinOr(EndingDState data, BlockPos fallback) {
		return data.stair().map(StairPlan::twin).orElse(fallback);
	}
}
