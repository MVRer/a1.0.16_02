package com.forzacode.a1016_02.world.sc;

import java.util.List;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.FogPlace;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The world part's debug commands under {@code /a1016 sc} (op level 2; the other workstreams add their numbers to
 * the same literal). Each answers only the op who ran it, in plain words:
 * <ul>
 *   <li>{@code sc 08 [player]}: the base, its bed, its marks, each placed block that could turn and why it does not;</li>
 *   <li>{@code sc 09 [player]}: the base, the door, the marks; {@code sc 09 b [player]}: the tracks now, round the
 *   house (forced: the gaps and the target's distance skipped, the view and the caps kept);</li>
 *   <li>{@code sc 10}: what waits; {@code sc 10 here}: as if he stood, seen, on the block you look at (prints there and
 *   along a way out from you, and the fog place; the prints wait until nobody sees them);</li>
 *   <li>{@code sc 11 [player]}: what was left and the cave spots; {@code sc 11 here [player]}: from the cave spot
 *   nearest you (else where you stand), the visit rule skipped, the view and every other rule kept;
 *   {@code sc 11 reset}: forgets what was left (it stays in the world).</li>
 * </ul>
 * {@code /a1016 fire sc_08}, {@code sc_09 [fake]} and {@code sc_11} fire the cards through the director.
 */
final class ScCommands {
	private static final String PLAYER = "player";
	/** How far {@code sc 10 here} looks. */
	private static final double LOOK_REACH = 96.0;

	private ScCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc")
				.then(withPlayer(Commands.literal("08"), (ctx, player) -> lines(ctx, Turned.describe(env(ctx), player))))
				.then(withPlayer(Commands.literal("09"), (ctx, player) -> lines(ctx, Tracks.describe(env(ctx), player)))
						.then(withPlayer(Commands.literal("b"), ScCommands::circle)))
				.then(Commands.literal("10").executes(ScCommands::pending)
						.then(Commands.literal("here").executes(ScCommands::here)))
				.then(withPlayer(Commands.literal("11"), (ctx, player) -> lines(ctx, TorchTrail.describe(env(ctx), player)))
						.then(withPlayer(Commands.literal("here"), ScCommands::trailHere))
						.then(Commands.literal("reset").executes(ScCommands::trailReset)))));
	}

	@FunctionalInterface
	private interface PlayerCommand {
		int run(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException;
	}

	/** {@code node} for whoever runs it (else the first participant), and {@code node <player>} for that player. */
	private static LiteralArgumentBuilder<CommandSourceStack> withPlayer(LiteralArgumentBuilder<CommandSourceStack> node, PlayerCommand command) {
		return node.executes(ctx -> {
			ServerPlayer player = target(ctx);
			return player == null ? 0 : command.run(ctx, player);
		}).then(Commands.argument(PLAYER, EntityArgument.player()).executes(ctx -> command.run(ctx, EntityArgument.getPlayer(ctx, PLAYER))));
	}

	/** The player running it, else (the console) the first participant (D-062.9); null with a message if nobody. */
	private static @Nullable ServerPlayer target(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer self = ctx.getSource().getPlayer();
		if (self != null) {
			return self;
		}
		ServerPlayer first = Services.watch().participants(ctx.getSource().getServer()).stream().findFirst().orElse(null);
		if (first == null) {
			ctx.getSource().sendFailure(Component.literal("[a1016] nobody takes part right now"));
		}
		return first;
	}

	private static ScEnv env(CommandContext<CommandSourceStack> ctx) {
		return ScEnv.live(ctx.getSource().getServer());
	}

	private static int lines(CommandContext<CommandSourceStack> ctx, List<String> lines) {
		for (String line : lines) {
			say(ctx, line);
		}
		return lines.size();
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] " + line), false);
	}

	// --- 09 ---

	private static int circle(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		ScEnv env = env(ctx);
		Tracks.Outcome outcome = Tracks.walk(env, player, env.state().stage(), player.getRandom(), false, true, true);
		say(ctx, "09 circle: " + outcome.result() + ": " + outcome.reason());
		return outcome.result() == com.forzacode.a1016_02.core.FireResult.FIRED ? 1 : 0;
	}

	// --- 10 ---

	private static int pending(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		List<WhereHeStood.Pending> waiting = WhereHeStood.LIVE.pending();
		say(ctx, "10: " + waiting.size() + " waiting to be out of view");
		for (WhereHeStood.Pending p : waiting) {
			long left = Math.max(0, p.deadline() - server.getTickCount());
			say(ctx, "10: at " + p.clear().toShortString() + ", " + p.run().size() + " along the way, " + (left / 20) + " s left");
		}
		long now = server.overworld().getDefaultClockTime();
		List<FogPlace> places = HerobrineState.get(server).effects().fogPlaces();
		say(ctx, "10: fog places " + places.size());
		for (FogPlace place : places) {
			say(ctx, String.format(java.util.Locale.ROOT, "10: fog at %s, radius %.0f, level %.2f, %.1f days left%s", BlockPos.containing(place.center()).toShortString(),
					place.radius(), place.level(), Math.max(0, place.untilClock() - now) / 24000.0, place.fadingBy(now) ? " (fading)" : ""));
		}
		return waiting.size();
	}

	private static int here(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		HitResult hit = player.pick(LOOK_REACH, 1.0F, false);
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
			ctx.getSource().sendFailure(Component.literal("[a1016] 10: look at a block within " + (int) LOOK_REACH));
			return 0;
		}
		ServerLevel level = player.level();
		Vec3 stoodAt = Vec3.atBottomCenterOf(block.getBlockPos().above());
		Vec3 eye = player.position();
		// He faced you, and went away from you.
		float yaw = (float) (Mth.atan2(eye.z - stoodAt.z, eye.x - stoodAt.x) * Mth.RAD_TO_DEG) - 90.0F;
		List<Vec3> path = WhereHeStood.pathAway(stoodAt, yaw + 180.0F, 8);
		WhereHeStood.Config config = WhereHeStood.config();
		String did = WhereHeStood.figureLeft(env(ctx), WhereHeStood.LIVE, level, stoodAt, yaw, path, "close", player.getUUID(), true,
				WhereHeStood.Rolls.ALL, player.getRandom(), config);
		say(ctx, "10 here: " + did + " (prints wait until nobody sees them: look away)");
		return 1;
	}

	// --- 11 ---

	private static int trailHere(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		ScEnv env = env(ctx);
		BlockPos start = TorchTrail.nearestSpot(env, player.level(), player.blockPosition());
		TorchTrail.Result result = TorchTrail.leaveFor(env, player, List.of(start), null, TorchTrail.config(), player.getRandom(), true);
		say(ctx, "11 here (from " + start.toShortString() + "): " + (result.plan() != null ? "left: " : "nothing: ") + result.reason());
		return result.plan() != null ? 1 : 0;
	}

	private static int trailReset(CommandContext<CommandSourceStack> ctx) {
		env(ctx).marks().forgetTrails();
		say(ctx, "11: forgotten (what was left stays in the world)");
		return 1;
	}
}
