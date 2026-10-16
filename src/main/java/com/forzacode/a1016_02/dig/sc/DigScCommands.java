package com.forzacode.a1016_02.dig.sc;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.dig.DigData;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * {@code /a1016 sc 12 [player] | sc 12 arm [force] [player] | sc 13 [player] | sc 13 arm [player]}. The player is
 * the one named, else whoever runs it, else (the console) the first participant. Lines go to the op who ran it only, in
 * plain words. 12's arm skips the card's fit (its {@code force} the shaft too); the bed's rules stay vanilla's (survival,
 * night or thunder). 13's arm fires {@code sc_13} through the director, forced, at the first point that fits within
 * {@link WayBack#ARM_TICKS}.
 */
public final class DigScCommands {
	private static final String PLAYER = "player";

	private DigScCommands() {
	}

	public static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc")
				.then(Commands.literal("12").executes(ctx -> show12(ctx, null))
						.then(Commands.literal("arm").executes(ctx -> arm12(ctx, false, null))
								.then(Commands.literal("force").executes(ctx -> arm12(ctx, true, null))
										.then(Commands.argument(PLAYER, EntityArgument.player()).executes(ctx -> arm12(ctx, true, named(ctx)))))
								.then(Commands.argument(PLAYER, EntityArgument.player()).executes(ctx -> arm12(ctx, false, named(ctx)))))
						.then(Commands.argument(PLAYER, EntityArgument.player()).executes(ctx -> show12(ctx, named(ctx)))))
				.then(Commands.literal("13").executes(ctx -> show13(ctx, null))
						.then(Commands.literal("arm").executes(ctx -> arm13(ctx, null))
								.then(Commands.argument(PLAYER, EntityArgument.player()).executes(ctx -> arm13(ctx, named(ctx)))))
						.then(Commands.argument(PLAYER, EntityArgument.player()).executes(ctx -> show13(ctx, named(ctx)))))));
	}

	private static ServerPlayer named(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return EntityArgument.getPlayer(ctx, PLAYER);
	}

	/** The named player, else whoever runs it, else the first participant. */
	private static Optional<ServerPlayer> target(CommandSourceStack source, @Nullable ServerPlayer named) {
		if (named != null) {
			return Optional.of(named);
		}
		if (source.getPlayer() != null) {
			return Optional.of(source.getPlayer());
		}
		List<ServerPlayer> participants = Services.watch().participants(source.getServer());
		return participants.isEmpty() ? Optional.empty() : Optional.of(participants.getFirst());
	}

	private static void say(CommandSourceStack source, String line) {
		source.sendSuccess(() -> Component.literal(line), false);
	}

	private static int nobody(CommandSourceStack source) {
		source.sendFailure(Component.literal("sc: nobody takes part right now; name a player"));
		return 0;
	}

	private static int show12(CommandContext<CommandSourceStack> ctx, @Nullable ServerPlayer named) {
		CommandSourceStack source = ctx.getSource();
		Optional<ServerPlayer> target = target(source, named);
		if (target.isEmpty()) {
			return nobody(source);
		}
		ServerPlayer player = target.get();
		MinecraftServer server = source.getServer();
		RestRefusal.Env env = RestRefusal.Env.live(server);
		DigScMemory sc = env.data().sc();
		String name = player.getName().getString();
		Optional<DigScMemory.RestArm> arm = sc.restArm(player.getUUID());
		long day = GameClock.dayTicks(server);
		say(source, arm.map(a -> String.format(Locale.ROOT, "sc 12: %s is armed for the bed at %s, %.1f in-game days left%s", name,
				a.bed().pos().toShortString(), (a.untilDayTicks() - day) / 24000.0, a.noShaft() ? " (debug: no shaft needed)" : ""))
				.orElseGet(() -> {
					String why = RestRefusal.why(player, env, false);
					return "sc 12: " + name + " is not armed; the card " + (why == null ? "fits now" : "does not fit: " + why);
				}));
		Optional<GlobalPos> bed = RestRefusal.respawnBed(player);
		say(source, bed.map(b -> "sc 12: their bed at " + b.pos().toShortString() + ": " + RestRefusal.shaftUnder(env.data(), b, env.config())
				.flatMap(net -> net.shaftTop().map(top -> "the shaft is done, its top at " + top.toShortString()))
				.orElse("no finished shaft under it")).orElse("sc 12: no respawn bed in the overworld"));
		say(source, String.format(Locale.ROOT, "sc 12: refusals in this world %d of %d, beds near a refusal still refused: %d", sc.restRefusals(),
				env.config().maxPerWorld, env.windows().all().stream().filter(w -> w.untilTick() > server.getTickCount()).count()));
		return 1;
	}

	private static int arm12(CommandContext<CommandSourceStack> ctx, boolean noShaft, @Nullable ServerPlayer named) {
		CommandSourceStack source = ctx.getSource();
		Optional<ServerPlayer> target = target(source, named);
		if (target.isEmpty()) {
			return nobody(source);
		}
		ServerPlayer player = target.get();
		RestRefusal.Env env = RestRefusal.Env.live(source.getServer());
		env.data().sc().dropRestArm(player.getUUID());
		String why = RestRefusal.why(player, env, noShaft);
		if (why != null) {
			source.sendFailure(Component.literal("sc 12: not armed: " + why));
			return 0;
		}
		DigScMemory.RestArm arm = RestRefusal.arm(player, env, noShaft);
		say(source, String.format(Locale.ROOT, "sc 12: %s is armed for the bed at %s for %d in-game nights: their next sleep there in survival, "
				+ "at night, is refused once", player.getName().getString(), arm.bed().pos().toShortString(), env.config().armNights));
		return 1;
	}

	private static int show13(CommandContext<CommandSourceStack> ctx, @Nullable ServerPlayer named) {
		CommandSourceStack source = ctx.getSource();
		Optional<ServerPlayer> target = target(source, named);
		if (target.isEmpty()) {
			return nobody(source);
		}
		ServerPlayer player = target.get();
		MinecraftServer server = source.getServer();
		WayBack.Env env = WayBack.Env.live(server);
		say(source, String.format(Locale.ROOT, "sc 13: %s: trail %d samples, done in this world %d of %d%s", player.getName().getString(),
				WayBack.trail(player.getUUID(), player.level().dimension()).size(), DigData.get(server).sc().wayBacks(), env.config().maxPerWorld,
				WayBack.armed(player.getUUID()) ? ", armed" : ""));
		say(source, "sc 13: " + WayBack.plan(player, env).summary());
		return 1;
	}

	private static int arm13(CommandContext<CommandSourceStack> ctx, @Nullable ServerPlayer named) {
		CommandSourceStack source = ctx.getSource();
		Optional<ServerPlayer> target = target(source, named);
		if (target.isEmpty()) {
			return nobody(source);
		}
		ServerPlayer player = target.get();
		ServerPlayer op = source.getPlayer();
		WayBack.arm(player, source.getServer().getTickCount(), op == null ? null : op.getUUID());
		say(source, "sc 13: " + player.getName().getString() + " is armed for 10 minutes: the first point that fits closes (walk 40 blocks into a "
				+ "1-wide natural passage you lit, alone, with a pickaxe)");
		return 1;
	}
}
