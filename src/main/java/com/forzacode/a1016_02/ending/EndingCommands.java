package com.forzacode.a1016_02.ending;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * {@code /a1016 ending status | path <A|B|C|NONE> | step [player]}. {@code path} commits a path now whatever its rules
 * say (NONE goes back to no path and Stage 3, and lets an ended story run again with every line open again);
 * {@code step} runs the current beat with its waits skipped (edits use the forced trace service) and moves on one beat,
 * acting on the player who runs it or the one named (D-062.9; from the console, on whoever is in it, the first
 * participant first). Mobs are still only moved out of view, and nothing is spawned.
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
				.then(Commands.literal("step")
						.executes(ctx -> step(ctx, engine, ctx.getSource().getPlayer()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> step(ctx, engine, EntityArgument.getPlayer(ctx, "player")))))));
	}

	private static int step(CommandContext<CommandSourceStack> ctx, EndingEngine engine, @Nullable ServerPlayer actor) {
		return send(ctx, engine.step(EndingAbcInit.ctx(ctx.getSource().getServer(), true, actor)));
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
		if (path == EndingPath.NONE) {
			HerobrineState state = HerobrineState.get(server);
			data.finishedLines().forEach(uuid -> state.setFlag(EndingApi.LINE_FINISHED_FLAG + uuid, false));
			data.reopenLines();
		}
		engine.commit(EndingAbcInit.ctx(server, true, ctx.getSource().getPlayer()), path, "debug");
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
		lines.add("  in it: " + names(snap.ctx().players().stream().map(ServerPlayer::getUUID).toList(), state) + "; lines finished: "
				+ names(List.copyOf(data.finishedLines()), state) + "; home owner " + owner(state, EndingApi.HOME_OWNER) + ", house copy owner "
				+ owner(state, EndingApi.HOUSE_COPY_OWNER) + ", first blocks owner " + owner(state, EndingApi.FIRST_BLOCKS_OWNER));
		lines.add("  director flags: silence " + DirectorHooks.silence(state).map(d -> d == DirectorHooks.FOREVER ? "forever" : "until day " + d)
				.orElse("off") + ", pace " + DirectorHooks.pace(state).map(x -> String.format(Locale.ROOT, "x%.2f", x)).orElse("x1")
				+ ", last sighting " + (state.hasFlag(EndingEngine.LAST_SIGHTING_FLAG) ? "allowed" : "off")
				+ (state.hasFlag(EndingEngine.LAST_SIGHTING_SEEN_FLAG) ? " (seen)" : ""));
		lines.add(String.format(Locale.ROOT,
				"  watch: Stop. %s, tellings %d (%d after Stop.), named %s, read %s, traces %s, fog stare %s, marked deaths %d (most of one player's own)",
				f.stopSeenAt() == EndingState.NEVER ? "not yet" : ago(f, f.stopSeenAt()), f.tellingCount(), f.tellingsSinceStop(), ago(f, f.lastNamedAt()),
				ago(f, f.lastReadAt()), f.lastTraceVisitDay() < 0 ? "never" : String.format(Locale.ROOT, "%.0f days ago", f.daysSinceTraceVisit()),
				ago(f, f.lastFogStareAt()), f.markedDeaths()));
		lines.add(String.format(Locale.ROOT,
				"  C's work: held %s, burned %s, unburned and kept by someone playing %d, held now: %s, house %d left of %d, %d broken by the group, home %s",
				data.everHeld(), data.burnedIds(), f.fragmentsUnburned(), f.holdsFragment() ? "yes" : "no", f.houseLeft(), f.housePeak(), f.ownBroken(),
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
					+ data.waiters().size() + ", final trap " + data.finalTrap().map(t -> t.type() + " at " + t.pos().pos().toShortString()).orElse("not armed")
					+ ", copy box " + engine.finalBounds(snap.ctx()).map(b -> b.box().toString()).orElse("-") + ", F20 " + (data.f20Placed() ? "placed" : "not yet"));
		}
		List<String> log = data.log();
		for (String line : log.subList(Math.max(0, log.size() - 6), log.size())) {
			lines.add("  - " + line);
		}
		return lines;
	}

	private static String names(List<UUID> players, HerobrineState state) {
		return players.isEmpty() ? "nobody" : players.stream().map(uuid -> state.player(uuid).map(HerobrineState.Subject::name).orElse(uuid.toString()))
				.collect(Collectors.joining(", "));
	}

	private static String owner(HerobrineState state, String key) {
		Optional<UUID> owner = state.owner(key);
		return owner.map(uuid -> state.player(uuid).map(HerobrineState.Subject::name).orElse(uuid.toString())).orElse("not chosen");
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
