package com.forzacode.a1016_02.lore.sc2.s30;

import java.util.List;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * Feature 30 of the self-created layer's second wave (docs/SELF_CREATED_2.md, 30), lore's part: the card
 * ({@link S30Card}, rules in {@link Maps}), its section ({@link Sc30Config}), the waiting places' passes (every
 * {@value Maps#DRAW_EVERY_TICKS} ticks while one waits), the switch (every pixel written back where vanilla has not drawn
 * since), {@code /a1016 sc 30 [player] | force [player] | b [player] | reset} and the dev-only exhibit {@code 30}.
 */
public final class S30 {
	private static final String PLAYER = "player";
	/** While the layer is off and a map in sight still waits to be given back, it is tried again this often (ticks). */
	public static final int RETRY_TICKS = 600;
	static final S30Card CARD = new S30Card();
	private static boolean pending;

	private S30() {
	}

	/** Called once from its workstream's second-wave init. */
	public static void init() {
		Sc30Config.get();
		Director.register(CARD);
		ServerTickEvents.END_SERVER_TICK.register(S30::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CARD.forget();
			pending = false;
		});
		SelfCreated.onSwitchedOff("sc30", S30::giveBack);
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(withPlayer(Commands.literal("30"), S30::describe)
				.then(withPlayer(Commands.literal("force"), S30::force))
				.then(withPlayer(Commands.literal("b"), S30::second))
				.then(Commands.literal("reset").executes(S30::reset)))));
		S30Gallery.register();
	}

	/** The switch: every map's pixels written back where it can be now (out of sight); what waits is tried again while off. */
	static void giveBack(MinecraftServer server) {
		S30Data data = S30Data.get(server);
		Maps.restore(server, data);
		pending = !data.edits().isEmpty();
	}

	private static void tick(MinecraftServer server) {
		if (pending && server.getTickCount() % RETRY_TICKS == 53) {
			if (SelfCreated.enabled()) {
				pending = false;
			} else {
				giveBack(server);
			}
		}
		if (server.getTickCount() % Maps.DRAW_EVERY_TICKS == 59) {
			S30Data data = S30Data.get(server);
			if (!data.pending().isEmpty()) {
				Maps.drawAll(Maps.Env.live(server));
			}
		}
	}

	@FunctionalInterface
	private interface PlayerCommand {
		int run(CommandContext<CommandSourceStack> ctx, ServerPlayer player);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> withPlayer(LiteralArgumentBuilder<CommandSourceStack> node, PlayerCommand command) {
		return node.executes(ctx -> {
			ServerPlayer player = self(ctx);
			return player == null ? 0 : command.run(ctx, player);
		}).then(Commands.argument(PLAYER, EntityArgument.player()).executes(ctx -> command.run(ctx, EntityArgument.getPlayer(ctx, PLAYER))));
	}

	private static @Nullable ServerPlayer self(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer self = ctx.getSource().getPlayer();
		if (self != null) {
			return self;
		}
		List<ServerPlayer> all = Services.watch().participants(ctx.getSource().getServer());
		if (all.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] nobody takes part right now"));
			return null;
		}
		return all.getFirst();
	}

	private static int describe(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		for (String line : Maps.describe(Maps.Env.live(ctx.getSource().getServer()), player)) {
			say(ctx, line);
		}
		return 1;
	}

	private static int force(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		MinecraftServer server = ctx.getSource().getServer();
		FireResult result = Services.director().fire(server, player, Maps.ID, false);
		if (result == FireResult.FIRED) {
			say(ctx, "30: fired for " + player.getPlainTextName() + " (put the map in a hand to see it)");
			return 1;
		}
		ctx.getSource().sendFailure(Component.literal("[a1016] 30: " + result + ": " + Maps.refusal(Maps.Env.live(server), player, true).orElse("no map may change now")));
		return 0;
	}

	private static int second(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		Maps.Outcome outcome = Maps.forceDraw(Maps.Env.live(ctx.getSource().getServer()), player);
		if (outcome.result() == FireResult.FIRED) {
			say(ctx, "30 b: " + outcome.reason() + ", " + outcome.pixels() + " pixels now, the rest as its chunks load");
			return 1;
		}
		ctx.getSource().sendFailure(Component.literal("[a1016] 30 b: " + outcome.reason()));
		return 0;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		int back = Maps.restore(server, S30Data.get(server));
		S30Data.get(server).reset();
		say(ctx, "30: forgotten, " + back + " pixels written back");
		return 1;
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] " + line), false);
	}
}
