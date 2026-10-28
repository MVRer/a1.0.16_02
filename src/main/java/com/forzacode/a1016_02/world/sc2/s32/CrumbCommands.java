package com.forzacode.a1016_02.world.sc2.s32;

import java.util.Locale;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.world.sc2.s32.CrumbData.Crumb;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Line;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /a1016 sc 32} (op level 2, through the shared {@code /a1016} root), answering only the op who ran it: the
 * nearest line built in this world (its place's kind number, how far its nearest crumb is, how many of its crumbs are
 * built and how many the op has seen), the world's count, and what the two rules did last; {@code sc 32 a} runs the two
 * rules' checks now (their conditions kept).
 */
final class CrumbCommands {
	private CrumbCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(Commands.literal("32").executes(CrumbCommands::nearest)
				.then(Commands.literal("a").executes(CrumbCommands::now)))));
	}

	private static int nearest(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		CrumbData data = CrumbData.get(server);
		BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
		Crumb best = null;
		double bestD = Double.MAX_VALUE;
		for (String key : data.lineKeys()) {
			for (Crumb c : data.line(key).values()) {
				double d = c.at().distSqr(at);
				if (d < bestD) {
					bestD = d;
					best = c;
				}
			}
		}
		if (best == null) {
			say(ctx, "32: none built yet (" + (S32.config() == null ? "no config" : "new land gets them") + ")");
			return 0;
		}
		Line line = CrumbRules.line(best.line());
		String kind = line == null ? "?" : line.kind().substring(1);
		int built = data.line(best.line()).size();
		int seen = 0;
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player != null) {
			for (Crumb c : data.line(best.line()).values()) {
				seen += data.hasSeen(player.getUUID(), c.id()) ? 1 : 0;
			}
		}
		say(ctx, String.format(Locale.ROOT, "32: nearest line to %s, its nearest point %.0f off (%d %d %d), %d built, %d seen by you; %d in the world", kind,
				Math.sqrt(bestD), best.at().getX(), best.at().getY(), best.at().getZ(), built, seen, data.size()));
		say(ctx, "32: " + CrumbRules.LIVE.last() + (player == null ? "" : "; " + CrumbRules.LIVE.watching(player.getUUID())));
		return 1;
	}

	private static int now(CommandContext<CommandSourceStack> ctx) {
		say(ctx, "32 a: " + CrumbRules.LIVE.run(ctx.getSource().getServer(), true));
		return 1;
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] " + line), false);
	}
}
