package com.forzacode.a1016_02.director;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CommandHooks;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * {@code /a1016 director [player]} (the world part, then that player's state, blind moments' rechecks, decks, history),
 * {@code director tick [player]} (one decision now for them, gates obeyed), {@code director quiet clear [player]},
 * {@code director sim <hours> [synthetic] [telling <atHour>] [player]} (dry run from their pacing, log in
 * {@code logs/a1016_director_sim.log}; {@code telling} has them name him that many hours in, so the run reaches
 * Telling) and {@code director timewarp <days> [player]} (timewarp, with the summary from their pacing). Without a
 * player each acts on whoever runs it; from the console, on the first participant (D-062.9).
 */
final class DirectorCommands {
	static final String SIM_LOG = "a1016_director_sim.log";
	private static final String PLAYER = "player";

	/** A command body that gets the named player, or null when none was named. */
	@FunctionalInterface
	private interface Body {
		int run(CommandContext<CommandSourceStack> ctx, @Nullable ServerPlayer named) throws CommandSyntaxException;
	}

	private DirectorCommands() {
	}

	static void register(DirectorImpl director) {
		CommandHooks.register((root, context) -> root.then(withPlayer(Commands.literal("director"), (ctx, named) -> info(ctx, director, named))
				.then(withPlayer(Commands.literal("tick"), (ctx, named) -> tick(ctx, director, named)))
				.then(Commands.literal("quiet").then(withPlayer(Commands.literal("clear"), (ctx, named) -> clearQuiet(ctx, director, named))))
				.then(Commands.literal("sim")
						.then(withPlayer(Commands.argument("hours", IntegerArgumentType.integer(1, 1000)), (ctx, named) -> sim(ctx, director, false, null, named))
								.then(telling(director, false))
								.then(withPlayer(Commands.literal("synthetic"), (ctx, named) -> sim(ctx, director, true, null, named))
										.then(telling(director, true)))))
				.then(Commands.literal("timewarp")
						.then(withPlayer(Commands.argument("days", IntegerArgumentType.integer(1, 3650)), (ctx, named) -> timewarp(ctx, director, named))))));
	}

	/** Runs {@code body} as it is, and with a trailing player argument. */
	private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withPlayer(T node, Body body) {
		return node.executes(ctx -> body.run(ctx, null))
				.then(Commands.argument(PLAYER, EntityArgument.player()).executes(ctx -> body.run(ctx, EntityArgument.getPlayer(ctx, PLAYER))));
	}

	/** {@code telling <atHour> [player]}: they name him that many hours into the run. */
	private static LiteralArgumentBuilder<CommandSourceStack> telling(DirectorImpl director, boolean synthetic) {
		return Commands.literal("telling").then(withPlayer(Commands.argument("atHour", DoubleArgumentType.doubleArg(0, 1000)),
				(ctx, named) -> sim(ctx, director, synthetic, DoubleArgumentType.getDouble(ctx, "atHour"), named)));
	}

	private static int info(CommandContext<CommandSourceStack> ctx, DirectorImpl director, @Nullable ServerPlayer named) {
		MinecraftServer server = ctx.getSource().getServer();
		Optional<UUID> who = CommandTarget.pacing(ctx.getSource(), named);
		if (who.isEmpty()) {
			send(ctx, DirectorDebug.worldLines(server, director.rules(server)));
			return send(ctx, List.of("director: nobody has taken part yet"));
		}
		return send(ctx, DirectorDebug.fullLines(director, server, who.get()));
	}

	private static int tick(CommandContext<CommandSourceStack> ctx, DirectorImpl director, @Nullable ServerPlayer named) {
		ServerPlayer player = CommandTarget.player(ctx.getSource(), named).orElse(null);
		String problem = CommandTarget.problem(player);
		if (problem != null) {
			ctx.getSource().sendFailure(Component.literal("[a1016] director tick: " + problem));
			return 0;
		}
		String outcome = director.decideNow(ctx.getSource().getServer(), player);
		return send(ctx, List.of("[a1016] director tick for " + player.getName().getString() + ": " + outcome));
	}

	private static int clearQuiet(CommandContext<CommandSourceStack> ctx, DirectorImpl director, @Nullable ServerPlayer named) {
		MinecraftServer server = ctx.getSource().getServer();
		Optional<UUID> who = CommandTarget.pacing(ctx.getSource(), named);
		if (who.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] director: nobody has taken part yet"));
			return 0;
		}
		director.clearQuiet(server, who.get());
		return send(ctx, List.of("[a1016] director: quiet cleared for " + DirectorImpl.name(server, who.get())));
	}

	private static int timewarp(CommandContext<CommandSourceStack> ctx, DirectorImpl director, @Nullable ServerPlayer named) {
		MinecraftServer server = ctx.getSource().getServer();
		int days = IntegerArgumentType.getInteger(ctx, "days");
		UUID who = CommandTarget.pacing(ctx.getSource(), named).orElse(null);
		DirectorSim.Result result = director.timewarpReport(server, days, who);
		send(ctx, List.of("[a1016] timewarp +" + days + "d (dry run of " + (who == null ? "a fresh player" : DirectorImpl.name(server, who))
				+ "'s pacing, nothing fired; every clock moved):"));
		return send(ctx, result.summary());
	}

	private static int sim(CommandContext<CommandSourceStack> ctx, DirectorImpl director, boolean synthetic, Double tellingAtHour,
			@Nullable ServerPlayer named) {
		MinecraftServer server = ctx.getSource().getServer();
		int hours = IntegerArgumentType.getInteger(ctx, "hours");
		UUID who = CommandTarget.pacing(ctx.getSource(), named).orElse(DirectorSim.SOLO);
		DirectorSim.Result result = director.simulate(server, who, hours, synthetic, tellingAtHour);
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
		String naming = result.params.tellingAtHour < 0 ? "" : String.format(Locale.ROOT, ", named him at %.1fh", result.params.tellingAtHour);
		String whose = who.equals(DirectorSim.SOLO) ? "a fresh player" : DirectorImpl.name(server, who);
		send(ctx, List.of("[a1016] director sim " + hours + "h of " + whose + "'s pacing" + (synthetic ? " (synthetic deck)" : "") + naming
				+ ", full log " + where));
		return send(ctx, result.summary());
	}

	private static int send(CommandContext<CommandSourceStack> ctx, List<String> lines) {
		for (String line : lines) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}
}
