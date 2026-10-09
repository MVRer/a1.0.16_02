package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * {@code /a1016 entity spawn <variant> | clear | info | goesunder | eyes [<flat|bright|glow> [fogResistance]] | tune [<key> <value>]}.
 * {@code goesunder} makes the figure that is out end the sighting by going under (D-030) now, if the ground lets him.
 * Debug only (op level 2, like the whole /a1016 tree). {@code eyes} and {@code tune} save to the config file.
 */
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
				.then(Commands.literal("goesunder").executes(EntityCommands::goesUnder))
				.then(Commands.literal("info").executes(EntityCommands::info))
				.then(Commands.literal("eyes").executes(EntityCommands::eyes)
						.then(Commands.argument("style", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(EyeStyle.values()).map(EyeStyle::shortName), builder))
								.executes(ctx -> setEyes(ctx, null))
								.then(Commands.argument("fogResistance", DoubleArgumentType.doubleArg(0.0, 1.0))
										.executes(ctx -> setEyes(ctx, DoubleArgumentType.getDouble(ctx, "fogResistance"))))))
				.then(Commands.literal("tune").executes(EntityCommands::tunings)
						.then(Commands.argument("key", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(EntityTuning.KEYS.stream().map(EntityTuning.Key::name), builder))
								.then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes(EntityCommands::tune))))));
	}

	/** Bare {@code tune}: prints every live-tunable value. */
	private static int tunings(CommandContext<CommandSourceStack> ctx) {
		String line = "[a1016] entity tune: " + EntityTuning.describe(EntityConfig.get());
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	/** Sets one value and saves the config. The figure reads it on his next tick. */
	private static int tune(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "key");
		double value = DoubleArgumentType.getDouble(ctx, "value");
		Optional<EntityTuning.Key> key = EntityTuning.byName(name);
		if (key.isEmpty()) {
			String keys = EntityTuning.KEYS.stream().map(EntityTuning.Key::name).reduce((a, b) -> a + ", " + b).orElse("");
			ctx.getSource().sendFailure(Component.literal("[a1016] unknown key " + name + " (" + keys + ")"));
			return 0;
		}
		EntityTuning.Key k = key.get();
		if (!k.accepts(value)) {
			ctx.getSource().sendFailure(Component.literal(String.format(Locale.ROOT, "[a1016] %s must be %s to %s", k.name(), EntityTuning.format(k.min()),
					EntityTuning.format(k.max()))));
			return 0;
		}
		EntityConfig config = EntityConfig.get();
		k.set().accept(config, value);
		config.save();
		String line = "[a1016] entity tune " + k.name() + " -> " + EntityTuning.format(value) + " (saved)";
		ctx.getSource().sendSuccess(() -> Component.literal(line), true);
		return 1;
	}

	/** Bare {@code eyes}: prints the current style. */
	private static int eyes(CommandContext<CommandSourceStack> ctx) {
		String line = "[a1016] entity eyes: " + describeEyes(EntityConfig.get());
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	/**
	 * Sets the style (and the fog resistance if given) and saves the config. In singleplayer the renderer reads the
	 * same config instance every frame, so the change shows on the next frame without a restart.
	 */
	private static int setEyes(CommandContext<CommandSourceStack> ctx, Double fogResistance) {
		String name = StringArgumentType.getString(ctx, "style");
		Optional<EyeStyle> style = EyeStyle.byName(name);
		if (style.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] unknown eye style " + name + " (flat, bright, glow)"));
			return 0;
		}
		EntityConfig config = EntityConfig.get();
		config.eyeStyle = style.get();
		if (fogResistance != null) {
			config.eyeFogResistance = fogResistance;
		}
		config.save();
		String line = "[a1016] entity eyes -> " + describeEyes(config) + " (saved)";
		ctx.getSource().sendSuccess(() -> Component.literal(line), true);
		return 1;
	}

	private static String describeEyes(EntityConfig config) {
		return String.format(Locale.ROOT, "%s, fog resistance %.2f", config.eyeStyle().shortName(), config.eyeFogResistance());
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
		FogEdge edge = FogEdge.of(player, variant.get() == Variant.CLOSE);
		FigureApi.Spawned spawned = FigureApi.spawnAtFogEdge(player, variant.get(), RandomSource.create(), true);
		String where = spawned.figure() == null ? ""
				: String.format(Locale.ROOT, " at %s, %d blocks out", spawned.figure().blockPosition().toShortString(),
						Math.round(SpotFinder.horizontal(player.position(), spawned.figure().position())));
		String line = String.format(Locale.ROOT, "[a1016] entity spawn %s -> %s%s (band %.1f..%.1f of fog end %.1f, %s%s)", variant.get().shortName(),
				spawned.result(), where, edge.inner(), edge.outer(), edge.limit(), source(edge), edge.seeable() ? "" : ", fog too thick to see him");
		ctx.getSource().sendSuccess(() -> Component.literal(line), true);
		return spawned.figure() != null ? 1 : 0;
	}

	/** The figure that is out goes under now (D-030): digs down, covers the hole over himself, gone once out of view. */
	private static int goesUnder(CommandContext<CommandSourceStack> ctx) {
		List<HimEntity> out = FigureApi.active(ctx.getSource().getServer());
		if (out.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] entity goesunder: no figure is out (spawn one with /a1016 entity spawn <variant>)"));
			return 0;
		}
		int started = 0;
		for (HimEntity him : out) {
			Optional<String> refusal = him.forceGoUnder();
			GoUnder dig = him.goUnder();
			if (refusal.isEmpty() && dig != null) {
				started++;
				String line = String.format(Locale.ROOT, "[a1016] entity goesunder -> %s digs down at %s, %d blocks deep", him.variant().shortName(),
						dig.plan().top().toShortString(), dig.plan().depth());
				ctx.getSource().sendSuccess(() -> Component.literal(line), true);
			} else {
				String why = refusal.orElse("no dig");
				ctx.getSource().sendFailure(Component.literal("[a1016] entity goesunder -> " + him.variant().shortName() + " at "
						+ him.blockPosition().toShortString() + " can't: " + why));
			}
		}
		return started;
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
		EntityConfig config = EntityConfig.get();
		for (HimEntity him : out) {
			String dist = player == null ? "?" : String.format(Locale.ROOT, "%.1f", SpotFinder.horizontal(player.position(), him.position()));
			String closed = player == null ? "?" : String.format(Locale.ROOT, "%.1f", him.closedBy(player.getUUID()));
			lines.add(String.format(Locale.ROOT, " %s %s at %s (%s blocks) spawnedAt=%.1f flee=%.1f approach=%.1f age=%ds seenFor=%ds unseen=%d stare=%d"
					+ " closed=%s triggered=%s fled=%s low=%s speed=%.1f b/s outrun=%s",
					him.variant().shortName(), him.phase(), him.blockPosition().toShortString(), dist, him.spawnDistance(), him.fleeDistance(config),
					him.approachBlocks(config), him.age() / 20, him.seenFor() < 0 ? -1 : him.seenFor() / 20, him.unseenTicks(), him.stareTicks(), closed,
					him.triggered(), him.fled(), him.isLow(), him.moveSpeed(), him.outrunning()));
			GoUnder dig = him.goUnder();
			if (dig != null) {
				lines.add(String.format(Locale.ROOT, "  under: %s at %s, %d deep, dug %d, covered %d of %d", dig.status(), dig.plan().top().toShortString(),
						dig.plan().depth(), dig.dugCount(), dig.filledCount(), GoUnder.COVER));
			}
			if (him.rushed()) {
				lines.add("  rushed: " + (him.rush() != null ? "passing" + (him.rush().passed() ? " (passed)" : "") : "done"));
			}
		}
		FigureApi.LastSpawn lastSpawn = FigureApi.lastSpawn();
		if (lastSpawn != null) {
			lines.add(String.format(Locale.ROOT, "last spawn: %s at %.1f blocks (band %.1f..%.1f of fog end %.1f, %s)", lastSpawn.variant().shortName(),
					lastSpawn.distance(), lastSpawn.edge().inner(), lastSpawn.edge().outer(), lastSpawn.edge().limit(), source(lastSpawn.edge())));
		}
		EntityData data = EntityData.get(server);
		String last = data.lastPos() == null ? "-" : data.lastPos().pos().toShortString();
		lines.add(String.format(Locale.ROOT, "record: last=%s@%s day=%d (x%d) today=%d sightings=%d fakes=%d stared=%d",
				data.lastVariant().isEmpty() ? "-" : data.lastVariant(), last, data.lastDay(), data.countOnLastDay(), GameClock.day(server),
				data.sightings(), data.fakes(), data.stared()));
		if (player != null) {
			HerobrineState state = HerobrineState.get(server);
			FogEdge edge = FogEdge.of(player, false);
			FogEdge close = FogEdge.of(player, true);
			ReportedFog.Entry report = ReportedFog.latest(player);
			String reported = report == null ? "none yet"
					: String.format(Locale.ROOT, "%.1f (%.1fs ago)", report.blocks(), ReportedFog.ageTicks(player) / 20.0);
			lines.add(String.format(Locale.ROOT, "fog: view=%d chunks, render end=%.1f, client fog end=%s, server estimate=%.1f (duskFog %.2f), using %.1f (%s)",
					edge.chunks(), edge.renderLimit(), reported, edge.estimate(), state.effects().duskFogLevel(), edge.limit(), source(edge)));
			lines.add(String.format(Locale.ROOT, "band %.1f..%.1f, close %.1f..%.1f, minDistance=%.1f%s, time=%d base=%s", edge.inner(), edge.outer(),
					close.inner(), close.outer(), config.minDistance(), edge.seeable() ? "" : " (fog too thick to see him)",
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

	/** Where the band's fog end came from. */
	private static String source(FogEdge edge) {
		return edge.fromClient() ? "client" : "server estimate";
	}

	/** The command's player, otherwise the subject. */
	private static ServerPlayer target(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		return player != null ? player : Services.watch().subject(ctx.getSource().getServer()).orElse(null);
	}
}
