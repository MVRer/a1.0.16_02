package com.forzacode.a1016_02.atmosphere.sc2.s23;

import java.util.List;

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
 * Feature 23 of the self-created layer's second wave (docs/SELF_CREATED_2.md), atmosphere's part: the card
 * ({@link HeldBreathCard}), its section ({@link Sc23Config}), the tick that lets every held mob go together
 * ({@link HeldBreath}), the switch's revert (everything held let go at once), {@code /a1016 sc 23 [force|fake]} and the
 * gallery exhibit {@code 23} (development only).
 */
public final class S23 {
	static final HeldBreathCard CARD = new HeldBreathCard();

	private S23() {
	}

	/** Called once from its workstream's second-wave init. */
	public static void init() {
		Sc23Config.get();
		Director.register(CARD);
		ServerTickEvents.END_SERVER_TICK.register(HeldBreath.LIVE::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HeldBreath.LIVE.clear());
		SelfCreated.onSwitchedOff("sc23", server -> HeldBreath.LIVE.clear());
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(Commands.literal("23")
				.executes(S23::status)
				.then(Commands.literal("force").executes(ctx -> fire(ctx, false)))
				.then(Commands.literal("fake").executes(ctx -> fire(ctx, true))))));
		if (Gallery.active()) {
			Gallery.register(new Gallery.Exhibit("23", "23", S23Gallery::place));
		}
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = player(ctx);
		if (player == null) {
			return fail(ctx, "nobody takes part");
		}
		HeldBreath.Bearing bearing = CARD.bearing(player);
		ok(ctx, "sc 23 for " + player.getPlainTextName() + ": " + CARD.refusal(player, false).orElse("fits now"));
		ok(ctx, String.format(java.util.Locale.ROOT, "  toward %.0f %.0f (%s), %d held now", bearing.toward().x, bearing.toward().z,
				bearing.from().name().toLowerCase(java.util.Locale.ROOT), HeldBreath.LIVE.breaths().size()));
		return 1;
	}

	private static int fire(CommandContext<CommandSourceStack> ctx, boolean fake) {
		ServerPlayer player = player(ctx);
		if (player == null) {
			return fail(ctx, "nobody takes part");
		}
		FireResult result = Services.director().fire(ctx.getSource().getServer(), player, HeldBreathCard.ID, fake);
		if (result != FireResult.FIRED) {
			return fail(ctx, "sc 23: did not fire (" + result + "): " + CARD.refusal(player, true).orElse("nothing near to hold"));
		}
		return ok(ctx, "sc 23: fired" + (fake ? " (fake)" : ""));
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
