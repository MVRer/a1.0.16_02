package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/** {@code /a1016 entity spawn <variant> | clear | info}. Debug only (op level 2, like the whole /a1016 tree). */
final class EntityCommands {
	private EntityCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("entity")
				.then(Commands.literal("spawn")
						.then(Commands.argument("variant", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Variant.values()).map(Variant::shortName), builder))
								.executes(EntityCommands::spawn)))
				.then(Commands.literal("clear").executes(EntityCommands::clear))
				.then(Commands.literal("info").executes(EntityCommands::info))));
	}

	/** Skips the gates, never the fog edge or the out-of-view rule. */
	private static int spawn(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "variant");
		Optional<Variant> variant = Variant.byName(name);
		if (variant.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] unknown variant " + name));
			return 0;
		}
		ServerPlayer player = target(ctx);
		if (player == null) {
			ctx.getSource().sendFailure(Component.literal("[a1016] no player to spawn him for"));
			return 0;
		}
		FogEdge edge = FogEdge.of(player, false);
		FigureApi.Spawned spawned = FigureApi.spawnAtFogEdge(player, variant.get(), RandomSource.create(), true);
		String where = spawned.figure() == null ? ""
				: String.format(" at %s, %d blocks out", spawned.figure().blockPosition().toShortString(),
						Math.round(SpotFinder.horizontal(player.position(), spawned.figure().position())));
		String line = String.format("[a1016] entity spawn %s -> %s%s (band %d..%d)", variant.get().shortName(), spawned.result(), where,
				Math.round(edge.inner()), Math.round(edge.outer()));
		ctx.getSource().sendSuccess(() -> Component.literal(line), true);
		return spawned.figure() != null ? 1 : 0;
	}

	private static int clear(CommandContext<CommandSourceStack> ctx) {
		int removed = FigureApi.clear(ctx.getSource().getServer());
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] entity clear -> removed " + removed), true);
		return removed;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		List<String> lines = new ArrayList<>();
		ServerPlayer player = target(ctx);
		List<HimEntity> out = FigureApi.active(server);
		lines.add("[a1016] entity: " + out.size() + " out");
		for (HimEntity him : out) {
			String dist = player == null ? "?" : String.valueOf(Math.round(SpotFinder.horizontal(player.position(), him.position())));
			lines.add(String.format(" %s %s at %s (%s blocks) age=%ds seen=%s unseen=%d stare=%d triggered=%s low=%s",
					him.variant().shortName(), him.phase(), him.blockPosition().toShortString(), dist, him.age() / 20, him.everSeen(),
					him.unseenTicks(), him.stareTicks(), him.triggered(), him.isLow()));
		}
		EntityData data = EntityData.get(server);
		String last = data.lastPos() == null ? "-" : data.lastPos().pos().toShortString();
		lines.add(String.format("record: last=%s@%s day=%d (x%d) today=%d sightings=%d fakes=%d stared=%d",
				data.lastVariant().isEmpty() ? "-" : data.lastVariant(), last, data.lastDay(), data.countOnLastDay(), GameClock.day(server),
				data.sightings(), data.fakes(), data.stared()));
		if (player != null) {
			HerobrineState state = HerobrineState.get(server);
			FogEdge edge = FogEdge.of(player, false);
			lines.add(String.format("fog edge: view=%d chunks limit=%d (duskFog %.2f) band %d..%d time=%d base=%s",
					edge.chunks(), Math.round(edge.limit()), state.effects().duskFogLevel(), Math.round(edge.inner()), Math.round(edge.outer()),
					SightingGates.timeOfDay(server), Services.watch().base(player).map(b -> b.pos().toShortString()).orElse("-")));
			StringBuilder gates = new StringBuilder("gates:");
			for (Variant variant : Variant.values()) {
				gates.append(' ').append(variant.shortName()).append('=')
						.append(SightingGates.check(player, player.level(), variant).orElse("ok"));
			}
			lines.add(gates.toString());
		}
		for (String line : lines) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return out.size();
	}

	/** The command's player, otherwise the subject. */
	private static ServerPlayer target(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		return player != null ? player : Services.watch().subject(ctx.getSource().getServer()).orElse(null);
	}
}
