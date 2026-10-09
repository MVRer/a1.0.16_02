package com.forzacode.a1016_02.debug;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CommandHooks;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Playtest tooling: {@code /a1016 debug playthrough <hours> [seed]} (every tempo, logs in
 * {@code logs/a1016_playthrough_<tempo>.log}) and, in a development environment only,
 * {@code /a1016 debug overlay on|off}.
 */
final class PlaytestCommands {
	private PlaytestCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> {
			LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug")
					.then(Commands.literal("playthrough")
							.then(Commands.argument("hours", IntegerArgumentType.integer(1, Playthrough.MAX_HOURS))
									.executes(ctx -> playthrough(ctx, Playthrough.DEFAULT_SEED))
									.then(Commands.argument("seed", LongArgumentType.longArg())
											.executes(ctx -> playthrough(ctx, LongArgumentType.getLong(ctx, "seed"))))));
			if (DebugOverlay.available()) {
				debug.then(Commands.literal("overlay")
						.executes(PlaytestCommands::overlayStatus)
						.then(Commands.literal("on").executes(ctx -> overlay(ctx, true)))
						.then(Commands.literal("off").executes(ctx -> overlay(ctx, false))));
			}
			root.then(debug);
		});
	}

	private static int playthrough(CommandContext<CommandSourceStack> ctx, long seed) {
		MinecraftServer server = ctx.getSource().getServer();
		int hours = IntegerArgumentType.getInteger(ctx, "hours");
		List<Playthrough.Report> reports = Playthrough.runAll(hours, seed);
		Path dir = server.getServerDirectory().resolve("logs");
		String where;
		try {
			Playthrough.write(dir, reports);
			where = "logs/a1016_playthrough_<tempo>.log";
		} catch (IOException e) {
			A1016_02.LOGGER.error("[a1016] could not write the playthrough logs to {}", dir, e);
			where = "(logs not written: " + e.getMessage() + ")";
		}
		boolean passed = reports.stream().allMatch(Playthrough.Report::passed);
		send(ctx, Fmt.f("[a1016] playthrough %d h, seed %d, every tempo (dry run, nothing fired): %s; full logs in %s", hours, seed,
				passed ? "PASS" : "FAIL", where));
		for (Playthrough.Report report : reports) {
			send(ctx, report.summary());
			A1016_02.LOGGER.info("[a1016] playthrough {}", report.summary());
			List<String> failures = report.failures();
			for (int i = 0; i < Math.min(3, failures.size()); i++) {
				send(ctx, "  " + failures.get(i));
			}
			if (failures.size() > 3) {
				send(ctx, "  ... " + (failures.size() - 3) + " more in the log");
			}
		}
		return passed ? 1 : 0;
	}

	private static int overlay(CommandContext<CommandSourceStack> ctx, boolean on) {
		DebugOverlay.setEnabled(ctx.getSource().getServer(), on);
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] debug overlay " + (DebugOverlay.enabled() ? "on (op players, once per second)" : "off")),
				true);
		return 1;
	}

	private static int overlayStatus(CommandContext<CommandSourceStack> ctx) {
		send(ctx, "[a1016] debug overlay is " + (DebugOverlay.enabled() ? "on" : "off") + "; /a1016 debug overlay on|off");
		return 1;
	}

	private static void send(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
	}
}
