package com.forzacode.a1016_02.world.sc2.s20;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /a1016 sc 20} (op level 2, through the shared {@code /a1016} root), answering only the op who ran it:
 * <ul>
 *   <li>{@code sc 20}: the nearest known one (distance, bearing, side, floor height, visited, 20b spent);</li>
 *   <li>{@code sc 20 arm}: if you are on one, 20b now, its caps lifted (the switch, the story, quiet and first hour hold).</li>
 * </ul>
 */
final class FloorCommands {
	/** How far {@code sc 20} looks for a known one. */
	static final int REACH = 30_000;

	private FloorCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(Commands.literal("20").executes(FloorCommands::nearest)
				.then(Commands.literal("arm").executes(FloorCommands::arm)))));
	}

	private static int nearest(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		ServerLevel level = player != null ? player.level() : ctx.getSource().getServer().overworld();
		BlockPos at = player != null ? player.blockPosition() : BlockPos.containing(ctx.getSource().getPosition());
		Optional<Remains.Place> found = Services.remains().nearest(level, at, EnumSet.of(Remains.Kind.FLOOR), REACH);
		if (found.isEmpty()) {
			say(ctx, "20: none known within " + REACH);
			return 0;
		}
		Remains.Place place = found.get();
		BlockPos middle = place.anchor();
		Sc2Data data = RemainsImpl.LIVE.data();
		long today = GameClock.day(ctx.getSource().getServer());
		Sc2Data.Spent face = data.spent(place, FloorAnswers.FACE);
		say(ctx, String.format(Locale.ROOT, "20: %d blocks, bearing %d, middle %s, side %d, floor %d, way in %s; visited by you %s; 20b today %d, ever %d",
				(int) place.distanceTo(at), bearing(at, middle), middle.toShortString(), FloorShape.sideOf(place.variant()), middle.getY() - 1,
				place.entranceFacing().getSerializedName(), player != null && data.visited(player.getUUID(), place) ? "yes" : "no", face.on(today),
				face.ever()));
		return 1;
	}

	private static int arm(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendFailure(Component.literal("[a1016] 20 arm: run it as a player on one"));
			return 0;
		}
		Optional<Remains.Place> place = Services.remains().containing(player.level(), player.blockPosition());
		if (place.isEmpty() || place.get().kind() != Remains.Kind.FLOOR) {
			say(ctx, "20 arm: you are not on one");
			return 0;
		}
		Answers.Result result = FloorAnswers.LIVE.ask(player.level(), place.get(), true);
		say(ctx, "20 arm: " + result.status().name().toLowerCase(Locale.ROOT) + " (" + result.reason() + ")");
		return result.status() == Answers.Status.REFUSED ? 0 : 1;
	}

	/** Degrees clockwise from north, 0 to 359. */
	static int bearing(BlockPos from, BlockPos to) {
		double degrees = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
		return (int) Math.round((degrees + 360.0) % 360.0) % 360;
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] " + line), false);
	}
}
