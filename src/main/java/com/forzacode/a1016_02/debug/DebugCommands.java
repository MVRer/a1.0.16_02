package com.forzacode.a1016_02.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
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
import com.forzacode.a1016_02.director.CommandTarget;
import com.forzacode.a1016_02.director.DirectorImpl;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * Core debug commands: {@code /a1016 state | stage <n> | fire <cardId> [fake] [player] | timewarp <days> | profile
 * reroll}. {@code fire} acts on the named player, else on whoever runs it, else (the console) on the first participant
 * (D-062.9). {@code state} shows the shared story, then each known player's own records and each participant's pacing.
 */
final class DebugCommands {
	static final int MAX_PLAYER_LINES = 16;

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
								.executes(ctx -> fire(ctx, false, null))
								.then(Commands.literal("fake").executes(ctx -> fire(ctx, true, null))
										.then(Commands.argument("player", EntityArgument.player())
												.executes(ctx -> fire(ctx, true, EntityArgument.getPlayer(ctx, "player")))))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> fire(ctx, false, EntityArgument.getPlayer(ctx, "player"))))))
				.then(Commands.literal("timewarp")
						.then(Commands.argument("days", IntegerArgumentType.integer(1, 3650)).executes(DebugCommands::timewarp)))
				.then(Commands.literal("profile")
						.then(Commands.literal("reroll").executes(DebugCommands::reroll))));
	}

	private static int state(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		HerobrineState state = HerobrineState.get(server);
		List<String> lines = new ArrayList<>();
		lines.add(Fmt.f("[a1016] stage=%s attention=%.1f (shared by everyone)", state.stage(), state.attention()));
		lines.add(profileLine(state.profile()));
		lines.add("fragments (" + state.profile().fragments().size() + "): " + String.join(" ", state.profile().fragments()));
		long worldTicks = GameClock.playTicks(server);
		lines.add(Fmt.f("world play=%dh%02dm day=%d, %d taking part", worldTicks / 72000, worldTicks / 1200 % 60, GameClock.day(server),
				Services.watch().participants(server).size()));
		lines.addAll(playerLines(server, state));
		lines.add(Fmt.f("stopFired=%s listRead=%s tellingStarted=%s read=%s placed=%d markedDeaths=%d",
				state.stopFired(), state.listRead(), state.tellingStarted(), state.fragmentsRead(), state.fragmentsPlaced().size(), state.markedDeaths().size()));
		lines.add(Fmt.f("effects: musicOff=%s duskFog=%.2f flags=%s", state.effects().musicOff(), state.effects().duskFogLevel(), state.flags()));
		lines.add("sites=" + Services.sites().all().size() + " ledger=" + TraceLedger.get(server).entries().size() + " cards=" + CardRegistry.ids().size()
				+ (ModConfig.get().devFastMode ? " devFastMode=ON /" + ModConfig.get().devFastDivisor : ""));
		lines.addAll(Services.director().debugLines(server));
		for (String line : lines) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}

	/**
	 * One line per known player (the first {@link #MAX_PLAYER_LINES}): taking part or not, their own play time, tension,
	 * marked deaths and first blocks.
	 */
	static List<String> playerLines(MinecraftServer server, HerobrineState state) {
		List<String> lines = new ArrayList<>();
		List<HerobrineState.Subject> players = state.players();
		if (players.isEmpty()) {
			lines.add("players: none yet");
		}
		for (HerobrineState.Subject player : players.subList(0, Math.min(players.size(), MAX_PLAYER_LINES))) {
			String status = Services.watch().participant(server, player.uuid()).isPresent() ? "taking part"
					: server.getPlayerList().getPlayer(player.uuid()) != null ? "online, not taking part" : "offline";
			long ticks = GameClock.playTicks(server, player.uuid());
			HerobrineState.FirstBlocks first = state.firstBlocks(player.uuid());
			lines.add(Fmt.f("player %s (%s): play=%dh%02dm tension=%.1f markedDeaths=%d first: block=%s table=%s chest=%s", player.name(), status,
					ticks / 72000, ticks / 1200 % 60, state.tension(player.uuid()), state.markedDeaths(player.uuid()).size(), describe(first.block()),
					describe(first.craftingTable()), describe(first.chest())));
		}
		if (players.size() > MAX_PLAYER_LINES) {
			lines.add("players: " + (players.size() - MAX_PLAYER_LINES) + " more known");
		}
		if (!state.owners().isEmpty()) {
			StringJoiner owners = new StringJoiner(", ");
			state.owners().forEach((key, uuid) -> owners.add(key + "=" + state.player(uuid).map(HerobrineState.Subject::name).orElse(uuid.toString())));
			lines.add("owners: " + owners);
		}
		return lines;
	}

	private static int stage(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		Stage stage = Stage.byLevel(IntegerArgumentType.getInteger(ctx, "n"));
		HerobrineState.get(server).setStage(server, stage);
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] stage set to " + stage), true);
		return 1;
	}

	private static int fire(CommandContext<CommandSourceStack> ctx, boolean fake, @Nullable ServerPlayer named) {
		MinecraftServer server = ctx.getSource().getServer();
		String cardId = StringArgumentType.getString(ctx, "cardId");
		if (CardRegistry.get(cardId).isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] unknown card " + cardId));
			return 0;
		}
		ServerPlayer target = CommandTarget.player(ctx.getSource(), named).orElse(null);
		String problem = CommandTarget.problem(target);
		if (problem != null) {
			ctx.getSource().sendFailure(Component.literal("[a1016] fire: " + problem));
			return 0;
		}
		FireResult result = Services.director().fire(server, target, cardId, fake);
		String who = target.getName().getString();
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] fire " + cardId + (fake ? " (fake)" : "") + " for " + who + " -> " + result), true);
		return result == FireResult.FIRED ? 1 : 0;
	}

	private static int timewarp(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		int days = IntegerArgumentType.getInteger(ctx, "days");
		// Every clock moves; the summary's dry run plays the pacing of whoever runs it (D-062.9).
		List<String> summary = Services.director() instanceof DirectorImpl director
				? director.timewarp(server, days, CommandTarget.pacing(ctx.getSource(), null).orElse(null))
				: Services.director().timewarp(server, days);
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
		return Fmt.f("profile: habits=%s density=%s tempo=%s (x%.1f) signature=%s",
				profile.habits(), profile.density(), profile.tempo(), profile.tempo().paceFactor(), profile.signature());
	}

	private static String describe(PlacedBlock block) {
		return block == null ? "-" : block.state().getBlock().getName().getString() + "@" + block.pos().pos().toShortString();
	}
}
