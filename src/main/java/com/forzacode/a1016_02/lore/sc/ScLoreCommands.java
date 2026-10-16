package com.forzacode.a1016_02.lore.sc;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.FireResult;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Lore's debug number under {@code /a1016 sc} (D-080: numbers only; the lines say what happened in plain words, only to the
 * op who ran them): {@code 14 [arm a|b [force] | c | reset]} ((a), (b) and (c) of 14; {@code force} skips the hours
 * away and the night). After an arm, quit to the title and open Singleplayer.
 */
final class ScLoreCommands {
	private ScLoreCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc")
				.then(Commands.literal("14").executes(ScLoreCommands::state)
						.then(Commands.literal("arm")
								.then(Commands.literal("a").executes(ctx -> armLast(ctx, false))
										.then(Commands.literal("force").executes(ctx -> armLast(ctx, true))))
								.then(Commands.literal("b").executes(ctx -> armVersion(ctx, false))
										.then(Commands.literal("force").executes(ctx -> armVersion(ctx, true)))))
						.then(Commands.literal("c").executes(ScLoreCommands::seed))
						.then(Commands.literal("reset").executes(ScLoreCommands::reset)))));
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] " + line), false);
	}

	private static int state(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		say(ctx, TheRecord.LIVE.line(server));
		if (server.isDedicatedServer()) {
			say(ctx, "  (a) and (b) need an integrated server: there is no world list here");
		}
		return 1;
	}

	private static int armLast(CommandContext<CommandSourceStack> ctx, boolean nogap) {
		FireResult result = TheRecord.LIVE.armLast(ctx.getSource().getServer(), nogap);
		say(ctx, result == FireResult.FIRED ? "14: the world list's last played time is armed" + (nogap ? " (no 6 hours needed)" : " (after 6 hours away)")
				+ "; quit to the title and open Singleplayer" : "14: not armed (" + ScGates.storyRefusal(ctx.getSource().getServer()).orElse("refused") + ")");
		return result == FireResult.FIRED ? 1 : 0;
	}

	private static int armVersion(CommandContext<CommandSourceStack> ctx, boolean nogap) {
		FireResult result = TheRecord.LIVE.armVersion(ctx.getSource().getServer(), nogap);
		say(ctx, result == FireResult.FIRED ? "14: the world list's version is armed (with vanilla's backup question); quit to the title and open"
				+ " Singleplayer" : "14: not armed (" + ScGates.storyRefusal(ctx.getSource().getServer()).orElse("refused") + ")");
		return result == FireResult.FIRED ? 1 : 0;
	}

	private static int seed(CommandContext<CommandSourceStack> ctx) {
		TheRecord.LIVE.forceSeed();
		say(ctx, "14: the next /seed a player runs answers the other seed, once" + (RecordData.get(ctx.getSource().getServer()).seedIsDone()
				? " (already done in this world: reset first)" : ""));
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) {
		TheRecord.LIVE.reset(ctx.getSource().getServer());
		say(ctx, "14: every beat and the seed forgotten (the statistics stay)");
		return 1;
	}
}
