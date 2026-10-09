package com.forzacode.a1016_02.director;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CommandHooks;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /a1016 director} (state, decks, history), {@code director tick} (one decision now, gates obeyed),
 * {@code director quiet clear}, {@code director sim <hours> [synthetic] [telling <atHour>]} (dry run, log in
 * {@code logs/a1016_director_sim.log}; {@code telling} has the subject name him that many hours in, so the run
 * reaches Telling) and {@code director timewarp <days>} (timewarp with its summary).
 */
final class DirectorCommands {
	static final String SIM_LOG = "a1016_director_sim.log";

	private DirectorCommands() {
	}

	static void register(DirectorImpl director) {
		CommandHooks.register((root, context) -> root.then(Commands.literal("director")
				.executes(ctx -> send(ctx, DirectorDebug.fullLines(director, ctx.getSource().getServer())))
				.then(Commands.literal("tick").executes(ctx -> {
					String outcome = director.decideNow(ctx.getSource().getServer());
					return send(ctx, List.of("[a1016] director tick: " + outcome));
				}))
				.then(Commands.literal("quiet").then(Commands.literal("clear").executes(ctx -> {
					director.clearQuiet(ctx.getSource().getServer());
					return send(ctx, List.of("[a1016] director: quiet cleared"));
				})))
				.then(Commands.literal("sim")
						.then(Commands.argument("hours", IntegerArgumentType.integer(1, 1000))
								.executes(ctx -> sim(ctx, director, false, null))
								.then(telling(director, false))
								.then(Commands.literal("synthetic").executes(ctx -> sim(ctx, director, true, null))
										.then(telling(director, true)))))
				.then(Commands.literal("timewarp")
						.then(Commands.argument("days", IntegerArgumentType.integer(1, 3650)).executes(ctx -> {
							int days = IntegerArgumentType.getInteger(ctx, "days");
							DirectorSim.Result result = director.timewarpReport(ctx.getSource().getServer(), days);
							send(ctx, List.of("[a1016] timewarp +" + days + "d (dry run, nothing fired):"));
							return send(ctx, result.summary());
						})))));
	}

	/** {@code telling <atHour>}: the subject names him that many hours into the run. */
	private static LiteralArgumentBuilder<CommandSourceStack> telling(DirectorImpl director, boolean synthetic) {
		return Commands.literal("telling").then(Commands.argument("atHour", DoubleArgumentType.doubleArg(0, 1000))
				.executes(ctx -> sim(ctx, director, synthetic, DoubleArgumentType.getDouble(ctx, "atHour"))));
	}

	private static int sim(CommandContext<CommandSourceStack> ctx, DirectorImpl director, boolean synthetic, Double tellingAtHour) {
		MinecraftServer server = ctx.getSource().getServer();
		int hours = IntegerArgumentType.getInteger(ctx, "hours");
		DirectorSim.Result result = director.simulate(server, hours, synthetic, tellingAtHour);
		Path path = server.getServerDirectory().resolve("logs").resolve(SIM_LOG);
		String where;
		try {
			Files.createDirectories(path.getParent());
			Files.write(path, result.log());
			where = "logs/" + SIM_LOG;
		} catch (IOException e) {
			A1016_02.LOGGER.error("[a1016] could not write {}", path, e);
			where = "(log not written: " + e.getMessage() + ")";
		}
		String named = result.params.tellingAtHour < 0 ? "" : String.format(Locale.ROOT, ", named him at %.1fh", result.params.tellingAtHour);
		send(ctx, List.of("[a1016] director sim " + hours + "h" + (synthetic ? " (synthetic deck)" : "") + named + ", full log " + where));
		return send(ctx, result.summary());
	}

	private static int send(CommandContext<CommandSourceStack> ctx, List<String> lines) {
		for (String line : lines) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}
}
