package com.forzacode.a1016_02.atmosphere.sc2.s24;

import java.util.List;

import com.forzacode.a1016_02.atmosphere.sc2.s21.Herds;
import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Feature 24 of the self-created layer's second wave (docs/SELF_CREATED_2.md), atmosphere's part: the card
 * ({@link FollowerCard}), its section ({@link Sc24Config}), the tick ({@link Follows}), the switch's revert (let go at
 * once), {@code /a1016 sc 24 [force]} and the gallery exhibit {@code 24} (development only).
 */
public final class S24 {
	static final FollowerCard CARD = new FollowerCard();
	/** While off, what is still to give back is tried again this often (ticks). */
	static final int GIVE_BACK_RETRY = 600;
	private static boolean giveBackPending;

	private S24() {
	}

	/** Called once from its workstream's second-wave init. */
	public static void init() {
		Sc24Config.get();
		Director.register(CARD);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Follows.LIVE.tick(server);
			if (giveBackPending && !SelfCreated.enabled() && server.getTickCount() % GIVE_BACK_RETRY == 0) {
				giveBackPending = Herds.giveBack(server, Follows.CAUSE) > 0;
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Follows.LIVE.clear();
			giveBackPending = false;
		});
		// The animal it took goes back where it stood (R13-7), retried while off.
		SelfCreated.onSwitchedOff("sc24", server -> {
			Follows.LIVE.clear();
			giveBackPending = Herds.giveBack(server, Follows.CAUSE) > 0;
		});
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(Commands.literal("24")
				.executes(S24::status)
				.then(Commands.literal("force").executes(S24::force)))));
		if (Gallery.active()) {
			Gallery.register(new Gallery.Exhibit("24", "24", S24Gallery::place));
		}
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = player(ctx);
		if (player == null) {
			return fail(ctx, "nobody takes part");
		}
		ok(ctx, "sc 24 for " + player.getPlainTextName() + ": " + CARD.refusal(player, false).orElse("fits now"));
		Follows.LIVE.describe().forEach(line -> ok(ctx, "  " + line));
		return 1;
	}

	private static int force(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = player(ctx);
		if (player == null) {
			return fail(ctx, "nobody takes part");
		}
		FireResult result = Services.director().fire(ctx.getSource().getServer(), player, FollowerCard.ID, false);
		if (result != FireResult.FIRED) {
			return fail(ctx, "sc 24: did not fire (" + result + "): " + CARD.refusal(player, true).orElse("the moment passed"));
		}
		return ok(ctx, "sc 24: fired; " + String.join("; ", Follows.LIVE.describe()));
	}

	private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer self = ctx.getSource().getPlayer();
		if (self != null) {
			return self;
		}
		List<ServerPlayer> all = Services.watch().participants(ctx.getSource().getServer());
		return all.isEmpty() ? null : all.getFirst();
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
