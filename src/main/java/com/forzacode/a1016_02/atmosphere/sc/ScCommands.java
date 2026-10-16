package com.forzacode.a1016_02.atmosphere.sc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.atmosphere.card.ComingHome;
import com.forzacode.a1016_02.atmosphere.card.NameGoneCard;
import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Services;
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
 * Atmosphere's numbers under {@code /a1016 sc} (D-080: the same {@code sc} literal every workstream adds to; Brigadier
 * merges the children). Every line goes only to whoever ran the command, in plain words.
 * <ul>
 * <li>{@code sc 04}: the memory, the night set now, tonight's answer, the next eligible night; {@code sc 04 tonight
 * [force]} sets tonight (or now, at night; {@code force} skips the fog and the moon's phase); {@code sc 04 clear}.</li>
 * <li>{@code sc 05 [player]}: the arm, the time away and each card's answer; {@code sc 05 arm [b] [fake] [player]}
 * fires {@code sc_05} (or {@code sc_05b}) through the director, forced: it skips the time away, the band and the view
 * rules stay (walk away and come back).</li>
 * <li>{@code sc 06 [player]}; {@code sc 06 arm [fake] [anytime]}: within two minutes, the first moment it fits (anytime:
 * night and fog skipped) it fires through the director; then walk.</li>
 * <li>{@code sc 07 [player]}: the world's caps and every named mob near a known base with why it is refused (fire it
 * with {@code /a1016 fire sc_07}).</li>
 * </ul>
 * Without a player: whoever runs it, else (the console) the first participant (D-062.9).
 */
public final class ScCommands {
	private ScCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc")
				.then(Commands.literal("04")
						.executes(ScCommands::status04)
						.then(Commands.literal("tonight")
								.executes(ctx -> tonight(ctx, false))
								.then(Commands.literal("force").executes(ctx -> tonight(ctx, true))))
						.then(Commands.literal("clear").executes(ScCommands::clear04)))
				.then(withPlayer(Commands.literal("05"), ScCommands::status05)
						.then(arm05(Commands.literal("arm"), false, false)
								.then(arm05(Commands.literal("fake"), false, true))
								.then(arm05(Commands.literal("b"), true, false)
										.then(arm05(Commands.literal("fake"), true, true)))))
				.then(withPlayer(Commands.literal("06"), ScCommands::status06)
						.then(Commands.literal("arm")
								.executes(ctx -> arm06(ctx, false, false))
								.then(Commands.literal("anytime").executes(ctx -> arm06(ctx, false, true)))
								.then(Commands.literal("fake")
										.executes(ctx -> arm06(ctx, true, false))
										.then(Commands.literal("anytime").executes(ctx -> arm06(ctx, true, true))))))
				.then(withPlayer(Commands.literal("07"), ScCommands::status07))));
	}

	@FunctionalInterface
	private interface ForPlayer {
		int run(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> withPlayer(LiteralArgumentBuilder<CommandSourceStack> literal, ForPlayer action) {
		return literal.executes(ctx -> run(ctx, action, null))
				.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> run(ctx, action, EntityArgument.getPlayer(ctx, "player"))));
	}

	private static <T extends ArgumentBuilder<CommandSourceStack, T>> T arm05(T node, boolean b, boolean fake) {
		return node.executes(ctx -> run(ctx, (c, p) -> arm05(c, p, b, fake), null))
				.then(Commands.argument("player", EntityArgument.player())
						.executes(ctx -> run(ctx, (c, p) -> arm05(c, p, b, fake), EntityArgument.getPlayer(ctx, "player"))));
	}

	private static int run(CommandContext<CommandSourceStack> ctx, ForPlayer action, @Nullable ServerPlayer named) throws CommandSyntaxException {
		Optional<ServerPlayer> player = named != null ? Optional.of(named) : target(ctx.getSource());
		if (player.isEmpty()) {
			return fail(ctx, "nobody takes part");
		}
		return action.run(ctx, player.get());
	}

	/** Whoever runs it, else (the console) the first participant. */
	private static Optional<ServerPlayer> target(CommandSourceStack source) {
		ServerPlayer self = source.getPlayer();
		if (self != null) {
			return Optional.of(self);
		}
		List<ServerPlayer> participants = Services.watch().participants(source.getServer());
		return participants.isEmpty() ? Optional.empty() : Optional.of(participants.getFirst());
	}

	private static @Nullable UUID op(CommandSourceStack source) {
		ServerPlayer self = source.getPlayer();
		return self == null ? null : self.getUUID();
	}

	// --- 04 ---

	private static int status04(CommandContext<CommandSourceStack> ctx) {
		EmptyNight.status(ctx.getSource().getServer()).forEach(line -> ok(ctx, line));
		return 1;
	}

	private static int tonight(CommandContext<CommandSourceStack> ctx, boolean force) {
		MinecraftServer server = ctx.getSource().getServer();
		Optional<String> why = EmptyNight.setTonight(server, force);
		if (why.isPresent()) {
			return fail(ctx, "sc 04: not set: " + why.get());
		}
		ok(ctx, "sc 04: set for tonight");
		return status04(ctx);
	}

	private static int clear04(CommandContext<CommandSourceStack> ctx) {
		EmptyNight.clearNight(ctx.getSource().getServer());
		return ok(ctx, "sc 04: cleared");
	}

	// --- 05 ---

	private static int status05(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		return ok(ctx, "sc 05 for " + player.getPlainTextName() + ": " + ComingHome.LIVE.describe(player));
	}

	private static int arm05(CommandContext<CommandSourceStack> ctx, ServerPlayer player, boolean b, boolean fake) {
		MinecraftServer server = ctx.getSource().getServer();
		String id = b ? ComingHome.WAITING_ID : ComingHome.STEPS_ID;
		FireResult result = Services.director().fire(server, player, id, fake);
		if (result != FireResult.FIRED) {
			return fail(ctx, "sc 05: " + id + " did not arm (" + result + "): " + ComingHome.LIVE.describe(player));
		}
		ComingHome.LIVE.tellTo(player.getUUID(), op(ctx.getSource()));
		return ok(ctx, "sc 05: " + id + (fake ? " (fake)" : "") + " armed for " + player.getPlainTextName() + "; walk 20 blocks away from home and come back");
	}

	// --- 06 ---

	private static int status06(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		return ok(ctx, "sc 06 for " + player.getPlainTextName() + ": " + InStep.LIVE.describe(player));
	}

	private static int arm06(CommandContext<CommandSourceStack> ctx, boolean fake, boolean anytime) throws CommandSyntaxException {
		return run(ctx, (c, player) -> {
			InStep.LIVE.arm(player, fake, anytime, op(c.getSource()));
			return ok(c, "sc 06: armed for " + player.getPlainTextName() + (fake ? " (fake)" : "") + (anytime ? " (anytime)" : "")
					+ " for two minutes; walk");
		}, null);
	}

	// --- 07 ---

	private static int status07(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		Optional<NameGoneCard> card = CardRegistry.get(NameGoneCard.ID).filter(NameGoneCard.class::isInstance).map(NameGoneCard.class::cast);
		if (card.isEmpty()) {
			return fail(ctx, "sc 07: not registered");
		}
		ok(ctx, "sc 07 for " + player.getPlainTextName() + ":");
		card.get().describe(player).forEach(line -> ok(ctx, "  " + line));
		return 1;
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendFailure(Component.literal(line));
		return 0;
	}
}
