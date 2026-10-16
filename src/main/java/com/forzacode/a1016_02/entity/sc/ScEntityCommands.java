package com.forzacode.a1016_02.entity.sc;

import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.entity.EntityData;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * Entity's debug numbers under {@code /a1016 sc} (D-080: numbers only; the lines say what happened in plain words, only to
 * the op who ran them): {@code 02 [arm [player] [minutes]]} and {@code 03 [arm | reset]}. A command that acts on a player
 * takes the named one, else whoever runs it, else (the console) the first participant (D-062.9).
 */
final class ScEntityCommands {
	private ScEntityCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc")
				.then(Commands.literal("02").executes(ScEntityCommands::state02)
						.then(Commands.literal("arm").executes(ctx -> arm02(ctx, target(ctx.getSource()), -1.0))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> arm02(ctx, EntityArgument.getPlayer(ctx, "player"), -1.0))
										.then(Commands.argument("minutes", DoubleArgumentType.doubleArg(0.05, 600.0))
												.executes(ctx -> arm02(ctx, EntityArgument.getPlayer(ctx, "player"), DoubleArgumentType.getDouble(ctx, "minutes")))))))
				.then(Commands.literal("03").executes(ScEntityCommands::state03)
						.then(Commands.literal("arm").executes(ScEntityCommands::arm03))
						.then(Commands.literal("reset").executes(ScEntityCommands::reset03)))));
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] " + line), false);
	}

	/** {@code 02}: who is armed, and each participant's fit now. */
	private static int state02(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		say(ctx, Nameless.LIVE.line(server));
		for (ServerPlayer player : Services.watch().participants(server)) {
			Optional<String> why = Nameless.LIVE.fit(player);
			say(ctx, "  " + player.getName().getString() + ": " + why.map(w -> "would not fit: " + w).orElse("fits now"));
		}
		return 1;
	}

	/** {@code 02 arm [player] [minutes]}: arms that player's client now (the fit skipped, the client's rules kept). */
	private static int arm02(CommandContext<CommandSourceStack> ctx, @Nullable ServerPlayer player, double minutes) {
		if (player == null) {
			say(ctx, "02: nobody to arm");
			return 0;
		}
		NamelessConfig config = NamelessConfig.get();
		long ticks = minutes > 0.0 ? Math.max(1L, Math.round(minutes * 1200.0)) : config.armTicks();
		FireResult result = Nameless.LIVE.arm(player, ticks, true);
		if (result == FireResult.FIRED) {
			say(ctx, String.format(Locale.ROOT, "02: %s's client is armed for %.1f min: a friend %.0f to %.0f blocks off, standing still %.1f s past"
					+ " half the fog, shows no name (%.0f s in all)", player.getName().getString(), ticks / 1200.0, config.minDistance, config.maxDistance,
					config.stillSeconds, config.hideSecondsTotal));
			return 1;
		}
		say(ctx, "02: not armed (" + ScGates.storyRefusal(ctx.getSource().getServer()).orElse("their client cannot take it") + ")");
		return 0;
	}

	/** {@code 03}: done or not, the arm, and why the last sighting did not arm. */
	private static int state03(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		say(ctx, HisView.LIVE.line(server));
		HisView.LIVE.lastRefusal().ifPresent(why -> say(ctx, "  the last sighting did not arm: " + why));
		return 1;
	}

	/** {@code 03 arm}: the next sighting skips the stage and the distances. */
	private static int arm03(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		if (EntityData.get(server).sc03Done()) {
			say(ctx, "03: already done in this world (reset it first)");
			return 0;
		}
		HisView.LIVE.forceNext();
		say(ctx, "03: the next sighting set up for the host, at night or in fog, arms the picture (stage and distances skipped)");
		return 1;
	}

	/** {@code 03 reset}: the old picture back, and the world forgets it. */
	private static int reset03(CommandContext<CommandSourceStack> ctx) {
		String restored = HisView.LIVE.reset(ctx.getSource().getServer());
		say(ctx, "03: forgotten (the old picture: " + restored + ")");
		return 1;
	}

	private static @Nullable ServerPlayer target(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player != null ? player : Services.watch().participants(source.getServer()).stream().findFirst().orElse(null);
	}
}
