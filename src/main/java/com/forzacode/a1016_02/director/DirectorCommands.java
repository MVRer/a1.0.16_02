package com.forzacode.a1016_02.director;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CommandHooks;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /a1016 director} (state, decks, history), {@code director tick} (one decision now, gates obeyed),
 * {@code director quiet clear}, {@code director sim <hours> [synthetic]} (dry run, log in
 * {@code logs/a1016_director_sim.log}) and {@code director timewarp <days>} (timewarp with its summary).
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
								.executes(ctx -> sim(ctx, director, false))
								.then(Commands.literal("synthetic").executes(ctx -> sim(ctx, director, true)))))
				.then(Commands.literal("timewarp")
						.then(Commands.argument("days", IntegerArgumentType.integer(1, 3650)).executes(ctx -> {
							int days = IntegerArgumentType.getInteger(ctx, "days");
							DirectorSim.Result result = director.timewarpReport(ctx.getSource().getServer(), days);
							send(ctx, List.of("[a1016] timewarp +" + days + "d (dry run, nothing fired):"));
							return send(ctx, result.summary());
						})))));
	}

	private static int sim(CommandContext<CommandSourceStack> ctx, DirectorImpl director, boolean synthetic) {
		MinecraftServer server = ctx.getSource().getServer();
		int hours = IntegerArgumentType.getInteger(ctx, "hours");
		DirectorSim.Result result = director.simulate(server, hours, synthetic);
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
		send(ctx, List.of("[a1016] director sim " + hours + "h" + (synthetic ? " (synthetic deck)" : "") + ", full log " + where));
		return send(ctx, result.summary());
	}

	private static int send(CommandContext<CommandSourceStack> ctx, List<String> lines) {
		for (String line : lines) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}
}
