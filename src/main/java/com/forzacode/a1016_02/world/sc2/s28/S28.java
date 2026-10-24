package com.forzacode.a1016_02.world.sc2.s28;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.world.sc.WorldSc;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * Feature 28 of the self-created layer's second wave (docs/SELF_CREATED_2.md, 28), world's part: the card
 * ({@link S28Card}, rules in {@link Moves}), its section ({@link Sc28Config}), the visit tracker ({@link Visitors}, every
 * 5 s), the switch's give-back (every move through the ledger, tried again every {@value #RETRY_TICKS} ticks while the
 * layer stays off and something waits) and {@code /a1016 sc 28 [player] | force [player] | fake [player] | reset}.
 */
public final class S28 {
	/** While the layer is off and a move waits to be given back (in view, not loaded), it is tried again this often. */
	public static final int RETRY_TICKS = 600;
	private static final String PLAYER = "player";

	static final S28Card CARD = new S28Card();
	private static boolean pending;

	private S28() {
	}

	/** Called once from its workstream's second-wave init. */
	public static void init() {
		Sc28Config.get();
		Director.register(CARD);
		ServerTickEvents.END_SERVER_TICK.register(S28::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			pending = false;
			CARD.forget();
		});
		SelfCreated.onSwitchedOff("sc28", S28::giveBack);
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").then(withPlayer(Commands.literal("28"), S28::describe)
				.then(withPlayer(Commands.literal("force"), (ctx, player) -> fire(ctx, player, false)))
				.then(withPlayer(Commands.literal("fake"), (ctx, player) -> fire(ctx, player, true)))
				.then(Commands.literal("reset").executes(S28::reset)))));
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % Visitors.EVERY_TICKS == 37) {
			Visitors.look(S28Env.live(server));
		}
		if (pending && now % RETRY_TICKS == 41) {
			if (SelfCreated.enabled()) {
				pending = false;
			} else {
				giveBack(server);
			}
		}
	}

	/** The switch: every move given back through the ledger where it can be now; what waits is tried again while off. */
	static void giveBack(MinecraftServer server) {
		WorldSc.Back back = WorldSc.giveBackAll(server, Moves.CAUSE);
		pending = back.waiting() > 0;
	}

	/** True while something of 28 waits to be given back (debug and tests). */
	public static boolean pending() {
		return pending;
	}

	// --- commands ---

	@FunctionalInterface
	private interface PlayerCommand {
		int run(CommandContext<CommandSourceStack> ctx, ServerPlayer player);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> withPlayer(LiteralArgumentBuilder<CommandSourceStack> node, PlayerCommand command) {
		return node.executes(ctx -> {
			ServerPlayer player = self(ctx);
			return player == null ? 0 : command.run(ctx, player);
		}).then(Commands.argument(PLAYER, EntityArgument.player()).executes(ctx -> command.run(ctx, EntityArgument.getPlayer(ctx, PLAYER))));
	}

	private static @Nullable ServerPlayer self(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer self = ctx.getSource().getPlayer();
		if (self != null) {
			return self;
		}
		List<ServerPlayer> all = Services.watch().participants(ctx.getSource().getServer());
		if (all.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[a1016] nobody takes part right now"));
			return null;
		}
		return all.getFirst();
	}

	private static int describe(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		for (String line : lines(S28Env.live(ctx.getSource().getServer()), player)) {
			say(ctx, line);
		}
		return 1;
	}

	/** Debug lines: the caps, the way, each visit kept, each base, the plan now and the fake's. */
	public static List<String> lines(S28Env env, ServerPlayer player) {
		List<String> lines = new ArrayList<>();
		S28Data data = env.data();
		MinecraftServer server = env.server();
		lines.add(String.format(java.util.Locale.ROOT, "28: %d of %d, last on day %d (today %d, %d apart)%s", data.count(), env.config().maxPerWorld,
				data.lastDay(), GameClock.day(server), env.config().minDaysApart, data.lastFrom() == null ? "" : ", last from " + name(env, data.lastFrom())
						+ " to " + name(env, data.lastTo())));
		long now = GameClock.dayTicks(server);
		for (Map.Entry<S28Data.Pair, Long> visit : data.visits().entrySet()) {
			lines.add(String.format(java.util.Locale.ROOT, "28: %s came by %s's base %.1f days ago", name(env, visit.getKey().visitor()),
					name(env, visit.getKey().host()), (now - visit.getValue()) / 24000.0));
		}
		ServerLevel overworld = server.overworld();
		for (ServerPlayer p : env.bases().participants()) {
			Optional<BlockPos> base = Moves.base(env, p.getUUID(), overworld);
			lines.add("28: " + p.getPlainTextName() + (base.isEmpty() ? ": no base in the overworld"
					: ": base " + base.get().toShortString() + ", " + Moves.boxes(env, overworld, base.get()).size() + " container(s) loaded, "
							+ (Moves.away(env, p, base.get(), overworld) ? "away" : "home")));
		}
		Moves.Plan plan = Moves.plan(env, player, false, false, player.getRandom());
		lines.add("28 for " + player.getPlainTextName() + ": " + (plan.found() ? "fits now (" + plan.reason() + ")" : plan.reason()));
		Moves.Plan forced = Moves.plan(env, player, true, false, player.getRandom());
		lines.add("28 forced: " + (forced.found() ? "would go (" + forced.reason() + ")" : forced.reason()));
		Moves.Plan fake = Moves.plan(env, player, true, true, player.getRandom());
		lines.add("28 fake: " + (fake.found() ? "would go (" + fake.reason() + ")" : fake.reason()));
		return lines;
	}

	private static String name(S28Env env, @Nullable UUID uuid) {
		if (uuid == null) {
			return "nobody";
		}
		return env.state().player(uuid).map(s -> s.name()).orElse(uuid.toString().substring(0, 8));
	}

	private static int fire(CommandContext<CommandSourceStack> ctx, ServerPlayer player, boolean fake) {
		MinecraftServer server = ctx.getSource().getServer();
		FireResult result = Services.director().fire(server, player, Moves.ID, fake);
		if (result == FireResult.FIRED) {
			say(ctx, "28: fired" + (fake ? " (fake)" : "") + " for " + player.getPlainTextName());
			return 1;
		}
		Moves.Plan plan = Moves.plan(S28Env.live(server), player, true, fake, player.getRandom());
		ctx.getSource().sendFailure(Component.literal("[a1016] 28: " + result + ": " + plan.reason()));
		return 0;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) {
		S28Data.get(ctx.getSource().getServer()).reset();
		say(ctx, "28: the count, the day and the way are forgotten (what moved stays moved)");
		return 1;
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal("[a1016] " + line), false);
	}
}
