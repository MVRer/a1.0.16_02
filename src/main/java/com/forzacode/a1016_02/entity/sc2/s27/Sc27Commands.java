package com.forzacode.a1016_02.entity.sc2.s27;

import java.util.Optional;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.Services;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * 27's debug number under {@code /a1016 sc} (D-080, D-103: numbers and plain verbs; the lines say what happened in plain
 * words, only to the op who ran them): {@code 27} (the showings, the caps, each participant's fit now), {@code 27 arm
 * [player]} (the next fitting moment within 10 minutes, the caps aside, fired through the director), {@code 27 reset} (the
 * caps forgotten) and {@code 27 clear} (every showing ends). A command that acts on a player takes the named one, else
 * whoever runs it, else (the console) the first participant (D-062.9).
 */
final class Sc27Commands {
	private Sc27Commands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(Commands.literal("27").executes(Sc27Commands::state)
				.then(Commands.literal("arm").executes(ctx -> arm(ctx, target(ctx.getSource())))
						.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> arm(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("reset").executes(Sc27Commands::reset))
				.then(Commands.literal("clear").executes(Sc27Commands::clear)))));
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] " + line), false);
	}

	private static int state(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		Sc27.LIVE.lines(server).forEach(line -> say(ctx, line));
		for (ServerPlayer player : Services.watch().participants(server)) {
			Optional<String> why = Sc27.LIVE.fit(player);
			String away = Sc27.LIVE.awayOthers(player).stream().map(p -> p.getName().getString()).reduce((a, b) -> a + ", " + b).orElse("nobody");
			say(ctx, "  " + player.getName().getString() + ": " + why.map(w -> "would not fit: " + w).orElse("fits now") + " (away: " + away + ")");
		}
		return 1;
	}

	private static int arm(CommandContext<CommandSourceStack> ctx, @Nullable ServerPlayer player) {
		if (player == null) {
			say(ctx, "27: nobody to arm");
			return 0;
		}
		ServerPlayer asking = ctx.getSource().getPlayer();
		Sc27.LIVE.arm(player, asking != null ? asking.getUUID() : null);
		say(ctx, "27: armed for " + player.getName().getString() + " for 10 min: outdoors at night, dusk or in fog, nobody within 48, someone else 160+"
				+ " off, deep under the ground 72+ off or in another dimension; now: " + Sc27.LIVE.fit(player, false).orElse("fits"));
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) {
		Sc27Data.get(ctx.getSource().getServer()).reset();
		say(ctx, "27: the caps are forgotten");
		return 1;
	}

	private static int clear(CommandContext<CommandSourceStack> ctx) {
		Sc27.LIVE.endAll(ctx.getSource().getServer());
		say(ctx, "27: every showing ends");
		return 1;
	}

	private static @Nullable ServerPlayer target(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player != null ? player : Services.watch().participants(source.getServer()).stream().findFirst().orElse(null);
	}
}
