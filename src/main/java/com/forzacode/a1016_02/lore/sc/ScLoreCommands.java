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
 * op who ran them): {@code 14 [arm last|version [nogap] | seed | reset]}. After an arm, quit to the title and open
 * Singleplayer.
 */
final class ScLoreCommands {
	private ScLoreCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc")
				.then(Commands.literal("14").executes(ScLoreCommands::state)
						.then(Commands.literal("arm")
								.then(Commands.literal("last").executes(ctx -> armLast(ctx, false))
										.then(Commands.literal("nogap").executes(ctx -> armLast(ctx, true))))
								.then(Commands.literal("version").executes(ctx -> armVersion(ctx, false))
										.then(Commands.literal("nogap").executes(ctx -> armVersion(ctx, true)))))
						.then(Commands.literal("seed").executes(ScLoreCommands::seed))
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
