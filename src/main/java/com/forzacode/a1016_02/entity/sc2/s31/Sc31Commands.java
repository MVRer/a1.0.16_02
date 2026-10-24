package com.forzacode.a1016_02.entity.sc2.s31;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 31's debug number under {@code /a1016 sc} (D-080, D-103): {@code 31} fires {@code sc_31} now for whoever runs it, through
 * the director, forced (the gates skipped, the spot's rules kept: a top in reach, out of everyone's view); {@code 31 fake}
 * its fake; {@code 31 here} what it would do now and why not; {@code 31 c} and {@code 31 c here} the same for
 * {@code sc_31c}; {@code 31 b} says where 31b comes from (17b's dark, no card). Lines in plain words, to the op only.
 */
final class Sc31Commands {
	private Sc31Commands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(Commands.literal("31").executes(ctx -> fire(ctx, Apex.CARD_ID, false))
				.then(Commands.literal("fake").executes(ctx -> fire(ctx, Apex.CARD_ID, true)))
				.then(Commands.literal("here").executes(Sc31Commands::here))
				.then(Commands.literal("b").executes(ctx -> {
					say(ctx, "31b: no card; it comes with 17b, at the far end of the dark (sc 17 arm inside one)");
					return 1;
				}))
				.then(Commands.literal("c").executes(ctx -> fire(ctx, InsideTrunks.CARD_ID, false))
						.then(Commands.literal("here").executes(Sc31Commands::hereC))))));
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] " + line), false);
	}

	private static int fire(CommandContext<CommandSourceStack> ctx, String card, boolean fake) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			say(ctx, "31: run it as a player");
			return 0;
		}
		FireResult result = Services.director().fire(ctx.getSource().getServer(), player, card, fake);
		Optional<String> why = card.equals(Apex.CARD_ID) ? Apex.fit(player) : InsideTrunks.fit(player);
		say(ctx, (card.equals(Apex.CARD_ID) ? "31" : "31c") + (fake ? " fake" : "") + ": " + result + (result == FireResult.FIRED ? "" : " (now: "
				+ why.orElse("fits") + ")"));
		return result == FireResult.FIRED ? 1 : 0;
	}

	private static int here(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			say(ctx, "31: run it as a player");
			return 0;
		}
		ServerLevel level = player.level();
		Sc31Config config = Sc31Config.get();
		Optional<Remains.Place> nearest = Services.remains().known(level).stream().filter(p -> p.kind() == Remains.Kind.PYRAMID && p.apex().isPresent())
				.min(Comparator.comparingDouble(p -> p.apex().get().distSqr(player.blockPosition())));
		say(ctx, nearest.map(p -> String.format(Locale.ROOT, "31: the nearest top is %.0f off (%.0f to %.0f), at %s", Math.sqrt(p.apex().get().distSqr(
				player.blockPosition())), config.apexMin, config.apexMax, p.apex().get().toShortString())).orElse("31: no top known in this level"));
		say(ctx, "31: " + Apex.fit(player).map(w -> "would not fit: " + w).orElse("fits now"));
		return 1;
	}

	private static int hereC(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			say(ctx, "31c: run it as a player");
			return 0;
		}
		say(ctx, "31c: " + InsideTrunks.fit(player).map(w -> "would not fit: " + w).orElse("fits now"));
		return 1;
	}
}
