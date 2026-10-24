package com.forzacode.a1016_02.world.sc2.s17;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Feature 17's debug commands under {@code /a1016 sc 17} (op, as every {@code sc} command; plain words to the op only):
 * {@code sc 17} (the nearest known one: how far and which way, its size, visited, what its wall holds, its answers, which
 * way in is open, the lights' state), {@code sc 17 arm} (inside one: 17b starts now if you are deep enough, and 17a, 17d
 * and 17e look now; every rule kept), {@code sc 17 b} (17c's move now at the nearest, out of view and within its caps).
 */
final class ArchiveCommands {
	private ArchiveCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(Commands.literal("17").executes(ArchiveCommands::where)
				.then(Commands.literal("arm").executes(ArchiveCommands::arm))
				.then(Commands.literal("b").executes(ArchiveCommands::door)))));
	}

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

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] " + line), false);
	}

	private static Optional<Remains.Place> nearest(ServerPlayer player) {
		return Services.remains().nearest(player.level(), player.blockPosition(), Set.of(Remains.Kind.ARCHIVE), 30_000_000);
	}

	private static int where(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = target(ctx);
		if (player == null) {
			return 0;
		}
		for (String line : describe(ctx.getSource().getServer(), player)) {
			say(ctx, line);
		}
		return 1;
	}

	static List<String> describe(MinecraftServer server, ServerPlayer player) {
		List<String> lines = new ArrayList<>();
		Optional<Remains.Place> found = nearest(player);
		if (found.isEmpty()) {
			lines.add("17: none known in this dimension");
			return lines;
		}
		Remains.Place place = found.get();
		Sc2Data data = RemainsImpl.LIVE.data();
		ArchivePlan plan = ArchiveRules.LIVE.plan(data, place);
		ServerLevel level = server.getLevel(place.dimension());
		Vec3 at = Vec3.atCenterOf(place.entrance());
		double dx = at.x - player.getX();
		double dz = at.z - player.getZ();
		lines.add(String.format(java.util.Locale.ROOT, "17: way in %.0f off (dx %.0f, dz %.0f) at %s, facing %s", Math.sqrt(dx * dx + dz * dz), dx, dz,
				place.entrance().toShortString(), place.entranceFacing().getName()));
		if (plan == null || level == null) {
			lines.add("17: its plan is not known");
			return lines;
		}
		lines.add("17: " + (plan.longHall ? "long" : "short") + ", floor y " + plan.floorY() + ", cells " + plan.cols + " x " + plan.rows + ", visited "
				+ (data.visited(player.getUUID(), place) ? "yes" : "no"));
		lines.add("17: on the wall: " + (level.isLoaded(plan.lectern()) ? Records.titles(level, plan) : "(not loaded)"));
		lines.add("17: answers b " + data.spent(place, ArchiveRules.DARK).ever() + ", c " + data.spent(place, OtherDoor.ANSWER).ever() + ", d "
				+ data.spent(place, ArchiveRules.MARKER).ever() + ", e " + data.spent(place, ArchiveRules.BOOK).ever());
		lines.add("17: open way " + (level.isLoaded(plan.entrance()) ? OtherDoor.openWay(level, plan) : "(not loaded)"));
		ArchiveRules.Visit visit = ArchiveRules.LIVE.visit(place);
		if (visit != null) {
			lines.add("17: inside now; lights out " + (visit.darkStarted ? visit.dark.size() + " units, " + visit.lightsGone + " lights" : "not started")
					+ (visit.darkWhy != null ? " (" + visit.darkWhy + ")" : "") + "; moved " + visit.markersMoved);
		}
		return lines;
	}

	private static int arm(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = target(ctx);
		if (player == null) {
			return 0;
		}
		String why = ArchiveRules.LIVE.arm(ctx.getSource().getServer(), player);
		say(ctx, "17 arm: " + (why == null ? "looked now" : why));
		for (String line : describe(ctx.getSource().getServer(), player)) {
			say(ctx, line);
		}
		return why == null ? 1 : 0;
	}

	private static int door(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = target(ctx);
		if (player == null) {
			return 0;
		}
		Optional<Remains.Place> found = nearest(player);
		MinecraftServer server = ctx.getSource().getServer();
		Sc2Data data = RemainsImpl.LIVE.data();
		if (found.isEmpty()) {
			say(ctx, "17 b: none known");
			return 0;
		}
		ArchivePlan plan = ArchiveRules.LIVE.plan(data, found.get());
		ServerLevel level = server.getLevel(found.get().dimension());
		if (plan == null || level == null) {
			say(ctx, "17 b: its plan is not known");
			return 0;
		}
		String why = ArchiveRules.LIVE.door(ArchiveRules.LIVE.env(server), level, found.get(), plan, data, true);
		say(ctx, "17 b: " + (why == null ? "moved" : why));
		return why == null ? 1 : 0;
	}
}
