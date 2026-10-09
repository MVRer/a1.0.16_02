package com.forzacode.a1016_02.ending.d;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.forzacode.a1016_02.core.TraceLedger;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * {@code /a1016 ending d ...}: {@code status} (the step and what is missing), {@code step <n>} (jump the chain, for
 * testing; 4 and up build the stair now if it is not there, 8 starts the last minute for real), {@code lastminute}
 * (a cosmetic preview of the last minute where the player stands: no flags, no clock, no figure, nothing given back;
 * only the silence, the fog and the music), {@code undo status}, {@code kit}
 * (marked items for steps 5 to 7: a first block and grove planks, debug only).
 */
public final class EndingDCommands {
	private EndingDCommands() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {
		root.then(Commands.literal("ending").then(Commands.literal("d")
				.then(Commands.literal("status").executes(EndingDCommands::status))
				.then(Commands.literal("step").then(Commands.argument("n", IntegerArgumentType.integer(1, 9)).executes(EndingDCommands::step)))
				.then(Commands.literal("lastminute").executes(EndingDCommands::lastMinute))
				.then(Commands.literal("undo").then(Commands.literal("status").executes(EndingDCommands::undoStatus)))
				.then(Commands.literal("kit").executes(EndingDCommands::kit))));
	}

	private static void say(CommandSourceStack source, String line) {
		source.sendSuccess(() -> Component.literal(line), false);
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		for (String line : Chain.status(server, EndingDState.get(server), EndingDConfig.get())) {
			say(ctx.getSource(), line);
		}
		return 1;
	}

	private static int step(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		EndingDState data = EndingDState.get(server);
		EndingDConfig cfg = EndingDConfig.get();
		Step step = Step.byNumber(IntegerArgumentType.getInteger(ctx, "n")).orElseThrow();
		if (step.atLeast(Step.UNDER_SEED) && !data.stair().map(StairPlan::complete).orElse(false)) {
			Stair.Attempt attempt = Chain.buildNow(server, data, cfg);
			say(ctx.getSource(), "stair: " + attempt.detail());
		}
		if (step.atLeast(Step.TAKE_BACK.next())) {
			data.set(EndingDState.FIRST_TAKEN, true);
		}
		LastMinute.reset();
		if (step == Step.LAST_MINUTE) {
			Optional<ServerPlayer> player = Optional.ofNullable(ctx.getSource().getPlayer());
			LastMinute.start(server, data, false, player.map(ServerPlayer::blockPosition).orElse(Chain.twinOr(data, server.overworld().getRespawnData().pos())));
		} else {
			data.setStep(step);
		}
		say(ctx.getSource(), "Ending D is at step " + step);
		return 1;
	}

	private static int lastMinute(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			say(ctx.getSource(), "run it as a player");
			return 0;
		}
		LastMinute.start(ctx.getSource().getServer(), EndingDState.get(ctx.getSource().getServer()), true, player.blockPosition());
		say(ctx.getSource(), "the last minute (preview) plays here");
		return 1;
	}

	private static int undoStatus(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		EndingDState data = EndingDState.get(server);
		say(ctx.getSource(), "undo: " + (data.step() == Step.AFTERWARD ? Afterward.describe(data) : "waits for the afterward (step " + data.step() + ")"));
		Map<TraceLedger.Kind, Integer> open = new EnumMap<>(TraceLedger.Kind.class);
		Map<String, Integer> stays = new TreeMap<>();
		List<TraceLedger.Entry> entries = TraceLedger.get(server).entries();
		for (TraceLedger.Entry entry : entries) {
			Optional<String> skip = Undo.skipReason(server, entry);
			if (skip.isPresent()) {
				stays.merge(skip.get(), 1, Integer::sum);
			} else {
				open.merge(entry.kind(), 1, Integer::sum);
			}
		}
		say(ctx.getSource(), String.format(Locale.ROOT, "ledger: %d entries; to undo: %s; stay: %s", entries.size(), open, stays));
		return 1;
	}

	private static int kit(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			return 0;
		}
		ItemStack first = new ItemStack(Items.CRAFTING_TABLE);
		Marks.set(first, Marks.FIRST_BLOCK);
		ItemStack planks = new ItemStack(Items.POPLAR_PLANKS, 4);
		Marks.set(planks, Marks.GROVE_WOOD);
		player.getInventory().add(first);
		player.getInventory().add(planks);
		player.getInventory().add(new ItemStack(Items.TORCH, 6));
		player.getInventory().add(new ItemStack(Items.OAK_SIGN, 2));
		say(ctx.getSource(), "kit: a marked first block, grove planks, six torches, two signs");
		return 1;
	}
}
