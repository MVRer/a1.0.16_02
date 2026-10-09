package com.forzacode.a1016_02.ending;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /a1016 ending status | path <A|B|C|NONE> | step}. {@code path} commits a path now whatever its rules say
 * (NONE goes back to no path and Stage 3, and lets an ended story run again); {@code step} runs the current beat
 * with its waits skipped (edits use the forced trace service) and moves on one beat. Mobs are still only moved out
 * of view, and nothing is spawned.
 */
final class EndingCommands {
	private EndingCommands() {
	}

	static void register(EndingEngine engine) {
		CommandHooks.register((root, context) -> root.then(Commands.literal("ending")
				.then(Commands.literal("status").executes(ctx -> send(ctx, status(engine, ctx.getSource().getServer()))))
				.then(Commands.literal("path")
						.then(Commands.argument("path", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Stream.of("A", "B", "C", "NONE"), builder))
								.executes(ctx -> path(ctx, engine))))
				.then(Commands.literal("step").executes(ctx -> send(ctx, engine.step(EndingAbcInit.ctx(ctx.getSource().getServer(), true)))))));
	}

	private static int path(CommandContext<CommandSourceStack> ctx, EndingEngine engine) {
		String text = StringArgumentType.getString(ctx, "path");
		EndingPath path = EndingPath.parse(text).orElse(null);
		if (path == null || path == EndingPath.D) {
			return send(ctx, List.of("[a1016] ending: use A, B, C or NONE (D is committed by ending.d)"));
		}
		MinecraftServer server = ctx.getSource().getServer();
		EndingState data = EndingState.get(server);
		if (data.ended()) {
			data.clearEnded();
			HerobrineState.get(server).setFlag(EndingEngine.ENDED_FLAG, false);
		}
		engine.commit(EndingAbcInit.ctx(server, true), path, "debug");
		return send(ctx, status(engine, server));
	}

	static List<String> status(EndingEngine engine, MinecraftServer server) {
		EndingState data = EndingState.get(server);
		HerobrineState state = HerobrineState.get(server);
		EndingConfig cfg = EndingConfig.get();
		EndingAbcInit.Snapshot snap = EndingAbcInit.snapshot(server);
		EndingFacts f = snap.facts();
		List<String> lines = new ArrayList<>();
		EndingPath path = data.path();
		lines.add(String.format(Locale.ROOT, "[a1016] ending: path %s, beat %s%s, stage %s%s", path, EndingBeats.name(path, data.progress(path)),
				path == EndingPath.NONE ? "" : String.format(Locale.ROOT, " (%.1f days in, %.1f in this beat; %s)", snap.ctx().daysSince(data.pathSince()),
						snap.ctx().daysSince(data.beatSince()), data.reason()),
				state.stage(), data.ended() ? ", the story has ended" : ""));
		lines.add("  director flags: silence " + DirectorFlags.silence(state).map(d -> d == DirectorFlags.FOREVER ? "forever" : "until day " + d)
				.orElse("off") + ", pace " + DirectorFlags.pace(state).map(x -> String.format(Locale.ROOT, "x%.2f", x)).orElse("x1")
				+ ", last sighting " + (state.hasFlag(EndingEngine.LAST_SIGHTING_FLAG) ? "allowed" : "off")
				+ (state.hasFlag(EndingEngine.LAST_SIGHTING_SEEN_FLAG) ? " (seen)" : ""));
		lines.add(String.format(Locale.ROOT, "  watch: Stop. %s, tellings %d (%d after Stop.), named %s, read %s, traces %s, fog stare %s, marked deaths %d",
				f.stopSeenAt() == EndingState.NEVER ? "not yet" : ago(f, f.stopSeenAt()), f.tellingCount(), f.tellingsSinceStop(), ago(f, f.lastNamedAt()),
				ago(f, f.lastReadAt()), f.lastTraceVisitDay() < 0 ? "never" : String.format(Locale.ROOT, "%.0f days ago", f.daysSinceTraceVisit()),
				ago(f, f.lastFogStareAt()), f.markedDeaths()));
		lines.add(String.format(Locale.ROOT, "  C's work: %d fragments burned, holds one: %s, house %d left of %d, %d broken by them, home %s",
				f.fragmentsBurned(), f.holdsFragment() ? "yes" : "no", f.houseLeft(), f.housePeak(), f.ownBroken(),
				data.home().map(h -> h.pos().toShortString()).orElse("-")));
		if (path == EndingPath.NONE && !data.ended()) {
			int obey = ModConfig.pacing().obeyDays;
			lines.add("  A: " + EndingRules.aWhy(f, cfg, obey).orElse("would commit now"));
			lines.add("  B: " + EndingRules.bWhy(f, cfg).orElse("would commit now"));
			lines.add("  C: " + EndingRules.cWhy(f, cfg).orElse("would commit now"));
		}
		if (path == EndingPath.B) {
			lines.add("  B: house " + data.house().map(h -> h.pos().toShortString()).orElse("-") + ", copy "
					+ (engine.ports().copyExists(server) ? (engine.ports().copyFinished(server) ? "finished" : "growing") : "none") + ", waiting in the doorway "
					+ data.waiters().size() + ", final trap " + (data.finalArmed() ? "armed" : "not armed") + ", F20 " + (data.f20Placed() ? "placed" : "not yet"));
		}
		List<String> log = data.log();
		for (String line : log.subList(Math.max(0, log.size() - 6), log.size())) {
			lines.add("  - " + line);
		}
		return lines;
	}

	private static String ago(EndingFacts f, long at) {
		return at == EndingState.NEVER ? "never" : String.format(Locale.ROOT, "%.1f days ago", f.daysSince(at));
	}

	private static int send(CommandContext<CommandSourceStack> ctx, List<String> lines) {
		for (String line : lines) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}
}
