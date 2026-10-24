package com.forzacode.a1016_02.world.sc2.s18;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
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
 * {@code /a1016 sc 18} (op level 2, through the shared {@code /a1016} root), answering only the op who ran it:
 * <ul>
 *   <li>{@code sc 18}: the nearest known one (distance, bearing, its mouth, the floor's height, how tall, whether it has a
 *   high opening, visited, 18b spent) and your own visit;</li>
 *   <li>{@code sc 18 arm}: if you are inside one, 18b's time runs out for you now; every other rule holds.</li>
 * </ul>
 */
final class Sc18Commands {
	/** How far {@code sc 18} looks for a known one. */
	static final int REACH = 30_000;

	private Sc18Commands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(Commands.literal("18").executes(Sc18Commands::nearest)
				.then(Commands.literal("arm").executes(Sc18Commands::arm)))));
	}

	private static int nearest(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		ServerLevel level = player != null ? player.level() : ctx.getSource().getServer().overworld();
		BlockPos at = player != null ? player.blockPosition() : BlockPos.containing(ctx.getSource().getPosition());
		Optional<Remains.Place> found = Services.remains().nearest(level, at, EnumSet.of(Remains.Kind.HOLLOW), REACH);
		if (found.isEmpty()) {
			say(ctx, "18: none known within " + REACH);
			return 0;
		}
		Remains.Place place = found.get();
		Sc2Data.Known known = RemainsImpl.LIVE.record(place).orElse(null);
		int floor = known == null ? place.anchor().getY() - 1 : known.extra().getIntOr("y", place.anchor().getY() - 1);
		int tallest = place.box().maxY() - 4 - floor;
		say(ctx, String.format(Locale.ROOT, "18: %d blocks, bearing %d, mouth %s facing %s, base %d, up to %d, %s", (int) place.distanceTo(at),
				bearing(at, place.anchor()), place.entrance().toShortString(), place.entranceFacing().getSerializedName(), floor, tallest,
				HollowShape.window(place.variant()) ? "an opening wanted" : "no opening"));
		Sc2Data data = RemainsImpl.LIVE.data();
		long today = GameClock.day(ctx.getSource().getServer());
		Sc2Data.Spent close = data.spent(place, "18b");
		say(ctx, String.format(Locale.ROOT, "18: visited by you %s; 18b today %d, ever %d", player != null && data.visited(player.getUUID(), place) ? "yes" : "no",
				close.on(today), close.ever()));
		if (player != null) {
			String visit = HollowAnswers.LIVE.describe(player.getUUID(), ctx.getSource().getServer().getTickCount());
			if (visit != null) {
				say(ctx, "18: " + visit);
			}
		}
		return 1;
	}

	private static int arm(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendFailure(Component.literal("[a1016] 18 arm: run it as a player inside one"));
			return 0;
		}
		boolean armed = HollowAnswers.LIVE.arm(player, ctx.getSource().getServer().getTickCount());
		say(ctx, armed ? "18 arm: its time ran out for you; every other rule holds" : "18 arm: you are not inside one");
		return armed ? 1 : 0;
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
