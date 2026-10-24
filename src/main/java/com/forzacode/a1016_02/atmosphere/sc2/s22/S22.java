package com.forzacode.a1016_02.atmosphere.sc2.s22;

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
 * Feature 22 of the self-created layer's second wave (docs/SELF_CREATED_2.md), atmosphere's part: the card
 * ({@link ProcessionCard}), its section ({@link Sc22Config}), its file's tick ({@link Processions}), the switch's revert
 * (every animal let go at once), {@code /a1016 sc 22 [force]} and the gallery exhibit {@code 22} (development only).
 */
public final class S22 {
	static final ProcessionCard CARD = new ProcessionCard();
	/** While off, what is still to give back is tried again this often (ticks). */
	static final int GIVE_BACK_RETRY = 600;
	private static boolean giveBackPending;

	private S22() {
	}

	/** Called once from its workstream's second-wave init. */
	public static void init() {
		Sc22Config.get();
		Director.register(CARD);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Processions.LIVE.tick(server);
			if (giveBackPending && !SelfCreated.enabled() && server.getTickCount() % GIVE_BACK_RETRY == 0) {
				giveBackPending = Herds.giveBack(server, ProcessionCard.CAUSE) > 0;
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Processions.LIVE.clear();
			giveBackPending = false;
		});
		// The animals it took go back where they stood (R13-7), retried while off.
		SelfCreated.onSwitchedOff("sc22", server -> {
			Processions.LIVE.clear();
			giveBackPending = Herds.giveBack(server, ProcessionCard.CAUSE) > 0;
		});
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(Commands.literal("22")
				.executes(S22::status)
				.then(Commands.literal("force").executes(S22::force)))));
		if (Gallery.active()) {
			Gallery.register(new Gallery.Exhibit("22", "22", S22Gallery::place));
		}
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = player(ctx);
		if (player == null) {
			return fail(ctx, "nobody takes part");
		}
		ok(ctx, "sc 22 for " + player.getPlainTextName() + ": " + CARD.refusal(player, false).orElse("fits now"));
		Processions.LIVE.describe().forEach(line -> ok(ctx, "  " + line));
		return 1;
	}

	private static int force(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = player(ctx);
		if (player == null) {
			return fail(ctx, "nobody takes part");
		}
		FireResult result = Services.director().fire(ctx.getSource().getServer(), player, ProcessionCard.ID, false);
		if (result != FireResult.FIRED) {
			return fail(ctx, "sc 22: did not fire (" + result + "): " + CARD.refusal(player, true).orElse("the moment passed"));
		}
		return ok(ctx, "sc 22: fired; " + String.join("; ", Processions.LIVE.describe()));
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
