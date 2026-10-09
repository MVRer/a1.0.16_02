package com.forzacode.a1016_02.ending.d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ending D's chain, step by step (DESIGN.md "Ending D"). Each check looks at the subject: has the current step been
 * done, and its danger. Steps only count in order; what the player did earlier (logs cut, signs written, torches
 * placed) counts once its step comes. Server thread only.
 */
public final class Chain {
	private static long nextStairTry;
	private static long nextF25;
	private static long nextLoss;
	private static Stair.Attempt lastStair = new Stair.Attempt(Stair.Status.WAITING_FOR_LORE, "not started (waits for F28 to be read)");

	private Chain() {
	}

	static void check(MinecraftServer server, EndingDState data, EndingDConfig cfg) {
		Optional<ServerPlayer> subject = Services.watch().subject(server);
		if (subject.isEmpty()) {
			return;
		}
		HerobrineState state = HerobrineState.get(server);
		background(server, data, cfg, state, server.getTickCount());
		check(server, data, cfg, subject.get(), state.fragmentsRead()::contains, View.TRACES);
	}

	/**
	 * One check of the current step for this player. {@code read} says which fragments were read; {@code view} is
	 * the out-of-view question the dangers ask first (core asks again on every edit).
	 */
	public static void check(MinecraftServer server, EndingDState data, EndingDConfig cfg, ServerPlayer player, Predicate<String> read, View view) {
		long now = server.getTickCount();
		Cairn.watch(player, cfg);
		Step step = data.step();
		switch (step) {
			case MAP -> {
				if (read.test("F28") && inGrove(player)) {
					advance(server, data, step);
				}
			}
			case GROVE -> {
				Grove.dangers(player, view, cfg);
				if (read.test("F30") && data.groveLogs() >= cfg.groveLogsMin) {
					advance(server, data, step);
				}
			}
			case TAKE_BACK -> {
				if (view == View.TRACES) {
					// Accident's own planner, with its own view rules (not in tests with fixed viewpoints).
					Cairn.danger(player, data, cfg);
				}
				if (Cairn.carriedAway(player, data, cfg)) {
					advance(server, data, step);
				}
			}
			case UNDER_SEED -> {
				Optional<StairPlan> plan = data.stair().filter(StairPlan::complete);
				if (plan.isEmpty() || !plan.get().dimension().equals(player.level().dimension())) {
					return;
				}
				if (!data.has(EndingDState.FLOODED) && Stair.inShaftBelow(player, plan.get(), cfg.floodDepthBlocks)
						&& Stair.flood(player, plan.get(), view)) {
					data.set(EndingDState.FLOODED, true);
				}
				if (plan.get().inChamber(player.blockPosition())) {
					advance(server, data, step);
				}
			}
			case TORCHES, SENTENCE, CROSS -> chamber(server, data, cfg, player, step, now, view);
			case LAST_MINUTE, AFTERWARD -> {
			}
		}
	}

	/** The stair, built early once F28 is read; then F25 offered to lore. */
	private static void background(MinecraftServer server, EndingDState data, EndingDConfig cfg, HerobrineState state, long now) {
		boolean complete = data.stair().map(StairPlan::complete).orElse(false);
		if (!complete && state.fragmentsRead().contains("F28") && now >= nextStairTry) {
			lastStair = Stair.build(server, data, Services.traces(), cfg);
			nextStairTry = now + (lastStair.status() == Stair.Status.BUILT_SEGMENT ? 20 : EndingDConfig.ticks(cfg.stairRetrySeconds));
		}
		if (complete && now >= nextF25 && !state.fragmentsPlaced().containsKey("F25")) {
			nextF25 = now + EndingDConfig.ticks(cfg.stairRetrySeconds);
			data.stair().ifPresent(plan -> Stair.offerF25(server, plan));
		}
	}

	/** Steps 5 to 7: down in the chamber. */
	private static void chamber(MinecraftServer server, EndingDState data, EndingDConfig cfg, ServerPlayer player, Step step, long now, View view) {
		Optional<StairPlan> found = data.stair().filter(StairPlan::complete);
		if (found.isEmpty() || !found.get().dimension().equals(player.level().dimension())) {
			return;
		}
		StairPlan plan = found.get();
		ServerLevel level = player.level();
		boolean down = plan.inChamber(player.blockPosition()) || Stair.inShaftBelow(player, plan, 0);
		if (!down && player.blockPosition().distSqr(plan.twin()) > 48 * 48) {
			return;
		}
		List<Chamber.Written> signs = Chamber.signs(level, plan);
		Chamber.watchNaming(player, data, signs);
		Chamber.namedFall(player, data, view);
		if (down && now >= nextLoss) {
			double seconds = step == Step.CROSS ? cfg.stairLossFastSeconds : cfg.stairLossSeconds;
			nextLoss = now + EndingDConfig.ticks(seconds);
			Stair.loseBlock(player, data, plan, view);
		}
		switch (step) {
			case TORCHES -> {
				if (Chamber.torches(level, plan) == cfg.torchesRequired) {
					advance(server, data, step);
				}
			}
			case SENTENCE -> {
				Optional<String> sentence = Chamber.sentence();
				if (sentence.isPresent() && Chamber.finished(signs, plan, cfg, sentence.get()).isPresent()) {
					advance(server, data, step);
				}
			}
			case CROSS -> {
				if (Chamber.cross(level, plan, data).isPresent()) {
					A1016_02.LOGGER.info("[a1016] ending d: the last plank is on his cross");
					LastMinute.start(server, data, false, player.blockPosition());
				}
			}
			default -> {
			}
		}
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
		Optional<ServerPlayer> player = Services.watch().subject(server);
		boolean f28 = state.fragmentsRead().contains("F28");
		lines.add("stair: " + data.stair().map(p -> p.complete() ? "built at " + p.twin().toShortString() + " (" + p.stairs().size() + " steps, top "
				+ p.yTop() + ")" : "building, down to " + (p.builtTo() == Integer.MAX_VALUE ? "nothing yet" : p.builtTo())).orElse(lastStair.detail())
				+ (data.stair().isPresent() && !data.stair().get().complete() ? " / " + lastStair.detail() : ""));
		switch (step) {
			case MAP -> {
				lines.add("missing: " + (f28 ? "" : "F28 read (the map); ") + "reach the untouched grove at "
						+ Grove.center(server).map(c -> c.pos().toShortString()).orElse("(not chosen yet)")
						+ player.map(p -> inGrove(p) ? " (in it now)" : "").orElse(""));
			}
			case GROVE -> {
				boolean f30 = state.fragmentsRead().contains("F30");
				lines.add("missing: " + (f30 ? "" : "F30 read on the oldest trunk; ") + "poplar logs cut in the grove " + data.groveLogs() + "/"
						+ cfg.groveLogsMin);
			}
			case TAKE_BACK -> {
				Optional<GlobalPos> cairn = Cairn.center(server);
				String carry = player.map(p -> Marks.carries(p, Marks.FIRST_BLOCK) ? "carrying it" : "not carrying it").orElse("offline");
				String dist = player.flatMap(p -> cairn.filter(c -> c.dimension().equals(p.level().dimension()))
						.map(c -> String.format(Locale.ROOT, "%.0f/%d blocks from the cairn", Math.sqrt(c.pos().distToCenterSqr(p.position())),
								cfg.carryAwayBlocks))).orElse("");
				lines.add("cairn: " + cairn.map(c -> c.pos().toShortString()).orElse("F13 not placed") + "; first block out cleanly: "
						+ data.has(EndingDState.FIRST_TAKEN) + " (offering this visit: " + Cairn.offering() + "); " + carry + ", " + dist
						+ "; lure armed: " + data.has(EndingDState.CAIRN_ARMED));
			}
			case UNDER_SEED -> lines.add("missing: dig through the seed pyramid's floor and go down to the chamber; flooded: " + data.has(EndingDState.FLOODED));
			case TORCHES, SENTENCE, CROSS -> {
				data.stair().filter(StairPlan::complete).ifPresent(plan -> {
					ServerLevel level = server.getLevel(plan.dimension());
					if (level == null) {
						return;
					}
					lines.add("torches in the chamber: " + Chamber.torches(level, plan) + "/" + cfg.torchesRequired + "; stair blocks left above: "
							+ Stair.standing(level, plan) + "/" + plan.stairs().stream().filter(s -> s.getY() >= plan.chamberCeil()).count());
					Optional<String> sentence = Chamber.sentence();
					lines.add("sentence under the twin: " + (sentence.isPresent()
							&& Chamber.finished(Chamber.signs(level, plan), plan, cfg, sentence.get()).isPresent() ? "written" : "missing")
							+ "; named him there: " + data.has(EndingDState.NAMED));
					long firsts = data.tracked().stream().filter(t -> t.mark().equals(Marks.FIRST_BLOCK)).count();
					long planks = data.tracked().stream().filter(t -> t.mark().equals(Marks.GROVE_WOOD)).count();
					lines.add("cross: " + (Chamber.cross(level, plan, data).isPresent() ? "standing" : "missing") + " (first block placed "
							+ firsts + "x, grove planks placed " + planks + "x)");
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
