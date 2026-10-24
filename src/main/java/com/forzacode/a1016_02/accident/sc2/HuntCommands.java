package com.forzacode.a1016_02.accident.sc2;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import com.forzacode.a1016_02.accident.AccidentInit;
import com.forzacode.a1016_02.accident.AccidentPlannerImpl;
import com.forzacode.a1016_02.accident.TrapKind;
import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.Services;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * {@code /a1016 sc <NN> [player] | arm [force] [player] | reset | clear} for the second wave's trap kinds
 * (docs/SELF_CREATED_2.md, 25 and 26; D-080, D-103: only the number, the plain verbs and a player under it). The bare
 * number prints why it would or would not start for the player and what its scene is doing; {@code arm} arms it through
 * the planner now (no session cap; {@code force} relaxes the time, the base and the pacing, never the way out, the
 * health floor, the alone rule or the tells); {@code reset} forgets the days and the world's count; {@code clear} ends
 * every running scene of it at once. The player is the one named, else whoever runs it, else the first participant.
 * Lines go to the op who ran it only.
 */
public final class HuntCommands {
	private static final String PLAYER = "player";

	/** A kind's part: its trap, its status lines, how to arm it (forced or not), and how to forget its pacing. */
	public record Kind(String number, TrapKind trap, BiFunction<ServerPlayer, Boolean, List<String>> status, Arming arming, Consumer<MinecraftServer> reset) {
	}

	/** Arms the kind for this player through the planner, forced or not, and returns what happened. */
	@FunctionalInterface
	public interface Arming {
		AccidentPlannerImpl.ArmResult arm(AccidentPlannerImpl planner, ServerPlayer player, boolean forced);
	}

	private HuntCommands() {
	}

	public static void register(Kind kind) {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(tree(kind))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> tree(Kind kind) {
		return Commands.literal(kind.number())
				.executes(ctx -> show(ctx, kind, null))
				.then(Commands.argument(PLAYER, EntityArgument.player()).executes(ctx -> show(ctx, kind, EntityArgument.getPlayer(ctx, PLAYER))))
				.then(Commands.literal("arm")
						.executes(ctx -> arm(ctx, kind, null, false))
						.then(Commands.literal("force")
								.executes(ctx -> arm(ctx, kind, null, true))
								.then(Commands.argument(PLAYER, EntityArgument.player()).executes(ctx -> arm(ctx, kind, EntityArgument.getPlayer(ctx, PLAYER), true))))
						.then(Commands.argument(PLAYER, EntityArgument.player()).executes(ctx -> arm(ctx, kind, EntityArgument.getPlayer(ctx, PLAYER), false))))
				.then(Commands.literal("reset").executes(ctx -> {
					kind.reset().accept(ctx.getSource().getServer());
					ctx.getSource().sendSuccess(() -> Component.literal("sc " + kind.number() + ": the days and the count are forgotten"), false);
					return 1;
				}))
				.then(Commands.literal("clear").executes(ctx -> {
					int n = (int) Hunts.running().stream().filter(s -> s.kind().equals(kind.trap().id())).count();
					Hunts.endAll(kind.trap().id(), "cleared");
					ctx.getSource().sendSuccess(() -> Component.literal("sc " + kind.number() + ": ended " + n), false);
					return 1;
				}));
	}

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

	private static int show(CommandContext<CommandSourceStack> ctx, Kind kind, @Nullable ServerPlayer named) {
		CommandSourceStack source = ctx.getSource();
		Optional<ServerPlayer> target = target(source, named);
		List<String> lines = new ArrayList<>();
		if (target.isEmpty()) {
			lines.add("sc " + kind.number() + ": nobody takes part right now; name a player");
		} else {
			lines.addAll(kind.status().apply(target.get(), false));
		}
		long now = source.getServer().getTickCount();
		for (HuntScene scene : Hunts.running()) {
			if (scene.kind().equals(kind.trap().id())) {
				lines.addAll(scene.describe(now));
			}
		}
		lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
		return 1;
	}

	private static int arm(CommandContext<CommandSourceStack> ctx, Kind kind, @Nullable ServerPlayer named, boolean forced) {
		CommandSourceStack source = ctx.getSource();
		Optional<ServerPlayer> target = target(source, named);
		if (target.isEmpty()) {
			source.sendFailure(Component.literal("sc " + kind.number() + ": nobody takes part right now; name a player"));
			return 0;
		}
		ServerPlayer player = target.get();
		AccidentPlannerImpl.ArmResult result = kind.arming().arm(AccidentInit.planner(), player, forced);
		if (!result.armed()) {
			source.sendFailure(Component.literal("sc " + kind.number() + ": not armed (" + result.status() + "): " + result.message()));
			kind.status().apply(player, forced).forEach(line -> source.sendFailure(Component.literal(line)));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("sc " + kind.number() + ": armed for " + player.getName().getString() + (forced ? " (forced)" : "")), false);
		return 1;
	}
}
