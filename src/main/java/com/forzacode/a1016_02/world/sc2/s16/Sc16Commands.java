package com.forzacode.a1016_02.world.sc2.s16;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.world.sc2.kit.RaysPlacement;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import org.jspecify.annotations.Nullable;

/**
 * {@code /a1016 sc 16} (op level 2, through the shared {@code /a1016} root), answering only the op who ran it:
 * <ul>
 *   <li>{@code sc 16}: the nearest known one (distance, bearing, size and form, visited, answers spent) and your own visit;</li>
 *   <li>{@code sc 16 a}: the nearest slots of the rays from where you stand (whether one stands there is decided when its
 *   land is first made, from Traces on);</li>
 *   <li>{@code sc 16 arm}: if you are inside one, its timers run out for you now (16b's time inside, 16c's next light);
 *   every other rule holds.</li>
 * </ul>
 */
final class Sc16Commands {
	/** How far {@code sc 16} looks for a known one. */
	static final int REACH = 30_000;
	/** How many slots {@code sc 16 a} lists. */
	static final int SLOTS = 6;
	static final ResourceKey<StructureSet> SET = ResourceKey.create(Registries.STRUCTURE_SET, A1016_02.id("world/s16"));

	private Sc16Commands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(Commands.literal("16").executes(Sc16Commands::nearest)
				.then(Commands.literal("a").executes(Sc16Commands::slots))
				.then(Commands.literal("arm").executes(Sc16Commands::arm)))));
	}

	private static int nearest(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		ServerLevel level = player != null ? player.level() : ctx.getSource().getServer().overworld();
		BlockPos at = player != null ? player.blockPosition() : BlockPos.containing(ctx.getSource().getPosition());
		Optional<Remains.Place> found = Services.remains().nearest(level, at, EnumSet.of(Remains.Kind.PYRAMID), REACH);
		if (found.isEmpty()) {
			say(ctx, "16: none known within " + REACH);
			return 0;
		}
		Remains.Place place = found.get();
		BlockPos apex = place.apex().orElse(place.anchor());
		int variant = place.variant();
		int size = PyramidShape.size(variant);
		say(ctx, String.format(Locale.ROOT, "16: %d blocks, bearing %d, apex %s, base %d, %s, %s, %s, way in %s", (int) place.distanceTo(at),
				bearing(at, apex), apex.toShortString(), PyramidShape.BASES[size], PyramidShape.sea(variant) ? "sea" : "land",
				PyramidShape.sealed(variant) || PyramidShape.sea(variant) ? "closed" : "open", PyramidShape.light(variant) ? "light" : "dark",
				place.entranceFacing().getSerializedName()));
		Sc2Data data = RemainsImpl.LIVE.data();
		long today = GameClock.day(ctx.getSource().getServer());
		Sc2Data.Spent close = data.spent(place, "16b");
		Sc2Data.Spent dark = data.spent(place, "16c");
		say(ctx, String.format(Locale.ROOT, "16: visited by you %s; 16b today %d, ever %d; 16c today %d, ever %d",
				player != null && data.visited(player.getUUID(), place) ? "yes" : "no", close.on(today), close.ever(), dark.on(today), dark.ever()));
		if (player != null) {
			String visit = PyramidAnswers.LIVE.describe(player.getUUID(), ctx.getSource().getServer().getTickCount());
			if (visit != null) {
				say(ctx, "16: " + visit);
			}
		}
		return 1;
	}

	private static int slots(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		RaysPlacement rays = rays(server);
		if (rays == null) {
			say(ctx, "16: the set's placement is not on the rays");
			return 0;
		}
		BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
		List<RaysPlacement.Slot> slots = new ArrayList<>(rays.slots(server.getWorldGenSettings().options().seed(), rays.slots()));
		slots.sort(Comparator.comparingDouble(slot -> flat(at, slot.pos())));
		for (RaysPlacement.Slot slot : slots.subList(0, Math.min(SLOTS, slots.size()))) {
			BlockPos pos = new BlockPos(slot.chunk().getMiddleBlockX(), at.getY(), slot.chunk().getMiddleBlockZ());
			say(ctx, String.format(Locale.ROOT, "16 a: ray %d slot %d at %d %d: %d blocks, bearing %d", slot.ray(), slot.k(), pos.getX(), pos.getZ(),
					(int) Math.sqrt(flat(at, pos)), bearing(at, pos)));
		}
		return slots.size();
	}

	private static int arm(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendFailure(Component.literal("[a1016] 16 arm: run it as a player inside one"));
			return 0;
		}
		boolean armed = PyramidAnswers.LIVE.arm(player, ctx.getSource().getServer().getTickCount());
		say(ctx, armed ? "16 arm: its timers ran out for you; every other rule holds" : "16 arm: you are not inside one");
		return armed ? 1 : 0;
	}

	/** The rays of the set's placement, or null. */
	static @Nullable RaysPlacement rays(MinecraftServer server) {
		return server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).get(SET)
				.map(holder -> holder.value().placement() instanceof RaysPlacement rays ? rays : null).orElse(null);
	}

	/** Degrees clockwise from north, 0 to 359. */
	static int bearing(BlockPos from, BlockPos to) {
		double degrees = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
		return (int) Math.round((degrees + 360.0) % 360.0) % 360;
	}

	private static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] " + line), false);
	}
}
