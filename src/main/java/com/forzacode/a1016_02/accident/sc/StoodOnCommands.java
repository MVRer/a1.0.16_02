package com.forzacode.a1016_02.accident.sc;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Services;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * {@code /a1016 sc 15 [player] | sc 15 arm [player]}: the arm, the gates and why each kept stand may not go now; the
 * arm fires {@code sc_15} through the director, forced (its arm needs no passes), and the op is told when it happens or
 * runs out. The player is the one named, else whoever runs it, else the first participant. Lines go to the op only.
 */
public final class StoodOnCommands {
	private static final String PLAYER = "player";

	private StoodOnCommands() {
	}

	public static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(Commands.literal("15")
				.executes(ctx -> show(ctx, null))
				.then(Commands.literal("arm").executes(ctx -> arm(ctx, null))
						.then(Commands.argument(PLAYER, EntityArgument.player()).executes(ctx -> arm(ctx, EntityArgument.getPlayer(ctx, PLAYER)))))
				.then(Commands.argument(PLAYER, EntityArgument.player()).executes(ctx -> show(ctx, EntityArgument.getPlayer(ctx, PLAYER)))))));
	}

	private static Optional<ServerPlayer> target(CommandSourceStack source, @Nullable ServerPlayer named) {
		if (named != null) {
			return Optional.of(named);
		}
		if (source.getPlayer() != null) {
			return Optional.of(source.getPlayer());
		}
		List<ServerPlayer> participants = Services.watch().participants(source.getServer());
		return participants.isEmpty() ? Optional.empty() : Optional.of(participants.getFirst());
	}

	private static int show(CommandContext<CommandSourceStack> ctx, @Nullable ServerPlayer named) {
		CommandSourceStack source = ctx.getSource();
		Optional<ServerPlayer> target = target(source, named);
		if (target.isEmpty()) {
			source.sendFailure(Component.literal("sc 15: nobody takes part right now; name a player"));
			return 0;
		}
		StoodOn.describe(target.get(), StoodOn.Env.live(source.getServer())).forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
		return 1;
	}

	private static int arm(CommandContext<CommandSourceStack> ctx, @Nullable ServerPlayer named) {
		CommandSourceStack source = ctx.getSource();
		Optional<ServerPlayer> target = target(source, named);
		if (target.isEmpty()) {
			source.sendFailure(Component.literal("sc 15: nobody takes part right now; name a player"));
			return 0;
		}
		ServerPlayer player = target.get();
		StoodOn.disarm(player.getUUID());
		FireResult result = Services.director().fire(source.getServer(), player, StoodOn.CARD_ID, false);
		if (result != FireResult.FIRED) {
			String why = StoodOn.gate(player, StoodOn.Env.live(source.getServer()));
			source.sendFailure(Component.literal("sc 15: not armed (" + result + ")" + (why == null ? "" : ": " + why)));
			return 0;
		}
		ServerPlayer op = source.getPlayer();
		StoodOn.tellOp(player.getUUID(), op == null ? null : op.getUUID());
		source.sendSuccess(() -> Component.literal("sc 15: " + player.getName().getString() + " is armed for "
				+ StoodOn.Env.live(source.getServer()).config().armMinutes + " minutes of play: walk over natural ground with a low "
				+ "cave pocket under it (2 or 3 from your feet to its floor, no lava or water near), then keep going"), false);
		return 1;
	}
}
