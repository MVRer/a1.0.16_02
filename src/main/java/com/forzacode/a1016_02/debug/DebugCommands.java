package com.forzacode.a1016_02.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.WorldProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Core debug commands: {@code /a1016 state | stage <n> | fire <cardId> [fake] | timewarp <days> | profile reroll}. */
final class DebugCommands {
	private DebugCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root
				.then(Commands.literal("state").executes(DebugCommands::state))
				.then(Commands.literal("stage")
						.then(Commands.argument("n", IntegerArgumentType.integer(0, Stage.values().length - 1)).executes(DebugCommands::stage)))
				.then(Commands.literal("fire")
						.then(Commands.argument("cardId", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(CardRegistry.ids(), builder))
								.executes(ctx -> fire(ctx, false))
								.then(Commands.literal("fake").executes(ctx -> fire(ctx, true)))))
				.then(Commands.literal("timewarp")
						.then(Commands.argument("days", IntegerArgumentType.integer(1, 3650)).executes(DebugCommands::timewarp)))
				.then(Commands.literal("profile")
						.then(Commands.literal("reroll").executes(DebugCommands::reroll))));
	}

	private static int state(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		HerobrineState state = HerobrineState.get(server);
		List<String> lines = new ArrayList<>();
		lines.add(String.format("[a1016] stage=%s attention=%.1f tension=%.1f", state.stage(), state.attention(), state.tension()));
		lines.add(profileLine(state.profile()));
		lines.add("fragments (" + state.profile().fragments().size() + "): " + String.join(" ", state.profile().fragments()));
		String subject = state.subject().map(s -> s.name() + (Services.watch().subject(server).isPresent() ? " (online)" : " (offline)")).orElse("none yet");
		long playTicks = GameClock.playTicks(server);
		lines.add(String.format("subject=%s play=%dh%02dm day=%d", subject, playTicks / 72000, playTicks / 1200 % 60, GameClock.day(server)));
		lines.add(String.format("stopFired=%s listRead=%s tellingStarted=%s read=%s placed=%d markedDeaths=%d",
				state.stopFired(), state.listRead(), state.tellingStarted(), state.fragmentsRead(), state.fragmentsPlaced().size(), state.markedDeaths().size()));
		HerobrineState.FirstBlocks first = state.firstBlocks();
		lines.add("first: block=" + describe(first.block()) + " table=" + describe(first.craftingTable()) + " chest=" + describe(first.chest()));
		lines.add(String.format("effects: musicOff=%s duskFog=%.2f flags=%s", state.effects().musicOff(), state.effects().duskFogLevel(), state.flags()));
		lines.add("sites=" + Services.sites().all().size() + " ledger=" + TraceLedger.get(server).entries().size() + " cards=" + CardRegistry.ids().size()
				+ (ModConfig.get().devFastMode ? " devFastMode=ON /" + ModConfig.get().devFastDivisor : ""));
		lines.addAll(Services.director().debugLines(server));
		for (String line : lines) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}

	private static int stage(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		Stage stage = Stage.byLevel(IntegerArgumentType.getInteger(ctx, "n"));
		HerobrineState.get(server).setStage(server, stage);
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] stage set to " + stage), true);
		return 1;
	}

	private static int fire(CommandContext<CommandSourceStack> ctx, boolean fake) {
		MinecraftServer server = ctx.getSource().getServer();
		String cardId = StringArgumentType.getString(ctx, "cardId");
		if (CardRegistry.get(cardId).isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] unknown card " + cardId));
			return 0;
		}
		Optional<ServerPlayer> subject = Services.watch().subject(server);
		if (subject.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] the subject is not online"));
			return 0;
		}
		FireResult result = Services.director().fire(server, cardId, fake);
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] fire " + cardId + (fake ? " (fake)" : "") + " -> " + result), true);
		return result == FireResult.FIRED ? 1 : 0;
	}

	private static int timewarp(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		int days = IntegerArgumentType.getInteger(ctx, "days");
		List<String> summary = Services.director().timewarp(server, days);
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] timewarp +" + days + "d -> day " + GameClock.day(server)), true);
		for (String line : summary) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}

	private static int reroll(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		HerobrineState state = HerobrineState.get(server);
		state.reroll(server.getWorldGenSettings().options().seed(), ThreadLocalRandom.current().nextLong());
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] new " + profileLine(state.profile())), true);
		return 1;
	}

	private static String profileLine(WorldProfile profile) {
		return String.format("profile: habits=%s density=%s tempo=%s (x%.1f) signature=%s",
				profile.habits(), profile.density(), profile.tempo(), profile.tempo().paceFactor(), profile.signature());
	}

	private static String describe(PlacedBlock block) {
		return block == null ? "-" : block.state().getBlock().getName().getString() + "@" + block.pos().pos().toShortString();
	}
}
