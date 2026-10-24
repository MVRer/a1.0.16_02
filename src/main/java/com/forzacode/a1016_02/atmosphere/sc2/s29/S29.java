package com.forzacode.a1016_02.atmosphere.sc2.s29;

import java.util.List;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Services;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * Feature 29 of the self-created layer's second wave (docs/SELF_CREATED_2.md, 29), atmosphere's part: the card
 * ({@link S29Card}), its section ({@link Sc29Config}) and {@code /a1016 sc 29 [player] | force [player]}. Only sounds:
 * nothing to give back when switched off (a replay running then plays out its few seconds, as every echo does; no new
 * one starts).
 */
public final class S29 {
	private static final String PLAYER = "player";
	static final S29Card CARD = new S29Card();

	private S29() {
	}

	/** Called once from its workstream's second-wave init. */
	public static void init() {
		Sc29Config.get();
		Director.register(CARD);
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(withPlayer(Commands.literal("29"), S29::status)
				.then(withPlayer(Commands.literal("force"), S29::force)))));
	}

	@FunctionalInterface
	private interface PlayerCommand {
		int run(CommandContext<CommandSourceStack> ctx, ServerPlayer player);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> withPlayer(LiteralArgumentBuilder<CommandSourceStack> node, PlayerCommand command) {
		return node.executes(ctx -> {
			ServerPlayer player = self(ctx);
			return player == null ? 0 : command.run(ctx, player);
		}).then(Commands.argument(PLAYER, EntityArgument.player()).executes(ctx -> command.run(ctx, EntityArgument.getPlayer(ctx, PLAYER))));
	}

	private static @Nullable ServerPlayer self(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer self = ctx.getSource().getPlayer();
		if (self != null) {
			return self;
		}
		List<ServerPlayer> all = Services.watch().participants(ctx.getSource().getServer());
		if (all.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] nobody takes part right now"));
			return null;
		}
		return all.getFirst();
	}

	private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		say(ctx, "29 for " + player.getPlainTextName() + ": " + CARD.refusal(player, false).orElse("fits now"));
		say(ctx, "29 forced: " + CARD.refusal(player, true).orElse("would fit"));
		for (S29Card.Pick pick : CARD.picks(player, true)) {
			say(ctx, String.format(java.util.Locale.ROOT, "29: %s, %.0f away, a run of %d, %d s ago", pick.friend().getPlainTextName(),
					pick.friend().distanceTo(player), pick.burst().breaks().size(), (ctx.getSource().getServer().getTickCount() - pick.burst().tick()) / 20));
		}
		return 1;
	}

	private static int force(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		FireResult result = Services.director().fire(ctx.getSource().getServer(), player, S29Card.ID, false);
		if (result == FireResult.FIRED) {
			say(ctx, "29: fired for " + player.getPlainTextName());
			return 1;
		}
		ctx.getSource().sendFailure(Component.literal("[a1016] 29: " + result + ": " + CARD.refusal(player, true).orElse("no spot in the rock (out of view, heard by nobody else)")));
		return 0;
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] " + line), false);
	}
}
