package com.forzacode.a1016_02.world.sc2.s16.deep;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.s16.PyramidAnswers;
import com.forzacode.a1016_02.world.sc2.s16.PyramidShape;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.Doorway;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.House;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /a1016 sc 33} (op level 2, through the shared {@code /a1016} root), answering only the op who ran it:
 * <ul>
 *   <li>{@code sc 33}: the nearest pyramid's depths (the levels built, the doors' states, the answers spent) and your visit;</li>
 *   <li>{@code sc 33 here}: where you stand in them (the level, the house);</li>
 *   <li>{@code sc 33 arm}: your timers run out now (33b's wait, the warren's time, the time past the iron door, the next
 *   door, the next light); every other rule holds.</li>
 * </ul>
 */
final class Sc33Commands {
	static final int REACH = 30_000;

	private Sc33Commands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(Commands.literal("33").executes(Sc33Commands::nearest)
				.then(Commands.literal("here").executes(Sc33Commands::here))
				.then(Commands.literal("arm").executes(Sc33Commands::arm)))));
	}

	private static Optional<Found> found(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		ServerLevel level = player != null ? player.level() : ctx.getSource().getServer().overworld();
		BlockPos at = player != null ? player.blockPosition() : BlockPos.containing(ctx.getSource().getPosition());
		Optional<Remains.Place> place = Services.remains().nearest(level, at, EnumSet.of(Remains.Kind.PYRAMID), REACH);
		if (place.isEmpty()) {
			return Optional.empty();
		}
		PyramidShape shape = PyramidAnswers.shapeOf(level, place.get());
		return shape == null ? Optional.empty() : Optional.of(new Found(level, place.get(), shape, at));
	}

	private record Found(ServerLevel level, Remains.Place place, PyramidShape shape, BlockPos at) {
	}

	private static int nearest(CommandContext<CommandSourceStack> ctx) {
		Optional<Found> f = found(ctx);
		if (f.isEmpty()) {
			say(ctx, "33: none known within " + REACH);
			return 0;
		}
		Found found = f.get();
		Depths depths = found.shape().depths();
		if (!depths.present()) {
			say(ctx, String.format(Locale.ROOT, "33: the nearest (%d blocks, version %d) has none", (int) found.place().distanceTo(found.at()),
					found.shape().version()));
			return 1;
		}
		DepthsPlan plan = depths.plan();
		int open = 0;
		int shut = 0;
		int gone = 0;
		RoomGraph graph = new RoomGraph(depths);
		for (Doorway d : plan.doorways()) {
			if (!d.way().door() || !found.level().isLoaded(depths.cells(d).get(0))) {
				continue;
			}
			if (!graph.hasDoor(found.level(), d)) {
				gone++;
			} else if (graph.open(found.level(), d)) {
				open++;
			} else {
				shut++;
			}
		}
		BlockPos door = depths.cells(plan.doorway(DepthsPlan.Way.FRONT)).get(0);
		say(ctx, String.format(Locale.ROOT, "33: %d blocks, levels %s, grid b %s, grid c %s, length %d, first at %s", (int) found.place().distanceTo(found.at()),
				plan.levels(), java.util.Arrays.toString(plan.roomsGrid()), java.util.Arrays.toString(plan.warrenGrid()), plan.hisTunnelLength(),
				door.toShortString()));
		Sc2Data data = RemainsImpl.LIVE.data();
		long today = GameClock.day(ctx.getSource().getServer());
		StringBuilder spent = new StringBuilder();
		for (String answer : new String[] {DepthsAnswers.A_LURE, DepthsAnswers.A_ROOMS, DepthsAnswers.A_DARK, DepthsAnswers.A_OPEN, DepthsAnswers.A_SHUT}) {
			Sc2Data.Spent s = data.spent(found.place(), answer);
			spent.append(' ').append(answer).append(' ').append(s.on(today)).append('/').append(s.ever());
		}
		say(ctx, "33: loaded " + shut + " shut, " + open + " open, " + gone + " gone; answers today/ever:" + spent);
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player != null) {
			String visit = DepthsAnswers.LIVE.describe(player.getUUID());
			if (visit != null) {
				say(ctx, "33: " + visit);
			}
		}
		return 1;
	}

	private static int here(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		Optional<Found> f = found(ctx);
		if (player == null || f.isEmpty() || !f.get().shape().depths().present()) {
			say(ctx, "33 here: not by one");
			return 0;
		}
		Depths depths = f.get().shape().depths();
		BlockPos at = player.blockPosition();
		House h = depths.houseOf(at.getX(), at.getY(), at.getZ());
		say(ctx, String.format(Locale.ROOT, "33 here: %s, level %s, house %s", DepthsAnswers.zone(f.get().shape(), player.position()),
				depths.levelOf(at.getX(), at.getY(), at.getZ()), h == null ? "-" : h.level() + " " + h.i() + " " + h.j()));
		return 1;
	}

	private static int arm(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendFailure(Component.literal("[a1016] 33 arm: run it as a player inside one"));
			return 0;
		}
		boolean armed = DepthsAnswers.LIVE.arm(player, ctx.getSource().getServer().getTickCount());
		say(ctx, armed ? "33 arm: your timers ran out; every other rule holds" : "33 arm: you are not inside one");
		return armed ? 1 : 0;
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] " + line), false);
	}
}
