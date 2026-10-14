package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * {@code /a1016 accident status [player] | candidates [trap [player]] | arm <trap> [player] | disarm [player|all] |
 * stolen | mark <cause> [player] | mark <cause> record [player]}. D-062.9: each acts on the player who runs it, or on
 * the named one; from the console, on the first participant. Arming skips the session cap but never one trap per
 * player, the spacing from other traps or the out-of-view rule. {@code stolen} lists the group's weapon and armor stacks
 * he took that could turn up on a zombie. {@code mark} only previews the cross unless told to {@code record}, because a
 * recorded marked death is that player's and counts toward Ending B.
 */
final class AccidentCommands {
	/** How far behind the player {@code mark} puts the cross, so it can be built out of view and then looked at. */
	private static final double MARK_BEHIND = 7;
	private static final SimpleCommandExceptionType UNKNOWN_TRAP = new SimpleCommandExceptionType(Component.literal("unknown trap"));
	private static final SimpleCommandExceptionType UNKNOWN_CAUSE = new SimpleCommandExceptionType(
			Component.literal("unknown cause; use one of " + String.join(", ", DeathCauses.KNOWN)));
	private static final SimpleCommandExceptionType NOBODY = new SimpleCommandExceptionType(Component.literal("nobody takes part right now"));

	private AccidentCommands() {
	}

	static void register(AccidentPlannerImpl planner, DeathMarkerImpl marker) {
		CommandHooks.register((root, context) -> root.then(Commands.literal("accident")
				.then(Commands.literal("status")
						.executes(ctx -> send(ctx, status(planner, ctx.getSource().getServer(), targetOrNull(ctx))))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> send(ctx, status(planner, ctx.getSource().getServer(), EntityArgument.getPlayer(ctx, "player"))))))
				.then(Commands.literal("candidates")
						.executes(ctx -> send(ctx, candidates(planner, target(ctx, false), null)))
						.then(Commands.argument("trap", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Traps.ALL.stream().map(TrapKind::id), builder))
								.executes(ctx -> send(ctx, candidates(planner, target(ctx, false), trap(ctx))))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> send(ctx, candidates(planner, target(ctx, true), trap(ctx)))))))
				.then(Commands.literal("arm")
						.then(Commands.argument("trap", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Traps.ALL.stream().map(TrapKind::id), builder))
								.executes(ctx -> arm(ctx, planner, target(ctx, false)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> arm(ctx, planner, target(ctx, true))))))
				.then(Commands.literal("disarm")
						.executes(ctx -> disarm(ctx, planner, target(ctx, false)))
						.then(Commands.literal("all").executes(ctx -> {
							MinecraftServer server = ctx.getSource().getServer();
							int n = planner.armedTraps(server).size();
							planner.disarmAll(server, "disarmed by command");
							return send(ctx, List.of("[a1016] accident: disarmed " + n + " trap" + (n == 1 ? "" : "s")));
						}))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> disarm(ctx, planner, target(ctx, true)))))
				.then(Commands.literal("stolen").executes(ctx -> send(ctx, stolen(ctx.getSource().getServer()))))
				.then(Commands.literal("mark")
						.then(Commands.argument("cause", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(DeathCauses.KNOWN, builder))
								.executes(ctx -> mark(ctx, marker, target(ctx, false), false))
								.then(Commands.literal("record")
										.executes(ctx -> mark(ctx, marker, target(ctx, false), true))
										.then(Commands.argument("player", EntityArgument.player())
												.executes(ctx -> mark(ctx, marker, target(ctx, true), true))))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> mark(ctx, marker, target(ctx, true), false)))))));
	}

	/**
	 * D-062.9: the named player, else the player running the command, else (the console) the first participant in join
	 * order.
	 */
	static ServerPlayer target(CommandContext<CommandSourceStack> ctx, boolean named) throws CommandSyntaxException {
		if (named) {
			return EntityArgument.getPlayer(ctx, "player");
		}
		ServerPlayer self = targetOrNull(ctx);
		if (self == null) {
			throw NOBODY.create();
		}
		return self;
	}

	/** The player running the command, else the first participant, else null. */
	static @Nullable ServerPlayer targetOrNull(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer self = ctx.getSource().getPlayer();
		if (self != null) {
			return self;
		}
		return Services.watch().participants(ctx.getSource().getServer()).stream().findFirst().orElse(null);
	}

	private static TrapKind trap(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String id = StringArgumentType.getString(ctx, "trap");
		return Traps.byId(id).orElseThrow(UNKNOWN_TRAP::create);
	}

	/** Every armed trap with its player; then {@code player}'s own session, routes and cairn, if there is one. */
	static List<String> status(AccidentPlannerImpl planner, MinecraftServer server, @Nullable ServerPlayer player) {
		AccidentData d = planner.data(server);
		AccidentConfig cfg = AccidentConfig.get();
		long now = GameClock.playTicks(server);
		List<String> lines = new ArrayList<>();
		Map<UUID, ArmedTrap> traps = d.traps();
		lines.add(traps.isEmpty() ? "[a1016] accident: nothing armed" : "[a1016] accident: " + traps.size() + " armed (one per player)");
		traps.forEach((owner, t) -> {
			lines.add("  " + planner.name(server, owner) + ": " + t.type() + " " + t.phase().name().toLowerCase(Locale.ROOT) + " at " + Candidate.at(t.pos()) + " in "
					+ t.dimension().identifier().getPath() + ", " + minutes(t.until() - now) + " left");
			lines.add("    clue: " + t.clue());
			t.worn().ifPresent(w -> lines.add("    worn: " + StolenGear.describe(w.stack()) + " in the " + w.slot().getName() + " slot"));
		});
		for (ArmedTrap t : d.restoring()) {
			lines.add("  dark corner torches still out at " + Candidate.at(t.pos()));
		}
		lines.add("  spacing " + cfg.trapSpacingBlocks + " blocks, MobTamper " + (Services.mobs() instanceof MobTamper.Stub ? "stub" : "real"));
		if (player != null) {
			UUID id = player.getUUID();
			String who = player.getName().getString();
			lines.add("  " + who + ": session arms " + planner.sessionArms(id) + "/" + cfg.maxTrapsPerSession + ", marked deaths " + Services.deaths().count(server, id));
			int tunnel = 0;
			int ladder = 0;
			int placed = 0;
			int water = 0;
			RouteBook routes = d.routes(id);
			List<RouteBook.Spot> spots = routes.near(player.level().dimension(), player.blockPosition(), cfg.scanRadius, Integer.MAX_VALUE);
			for (RouteBook.Spot spot : spots) {
				tunnel += spot.point().has(RouteBook.UNDER) ? 1 : 0;
				ladder += spot.point().has(RouteBook.LADDER) ? 1 : 0;
				placed += spot.point().has(RouteBook.ON_PLACED) ? 1 : 0;
				water += spot.point().has(RouteBook.WATER) ? 1 : 0;
			}
			lines.add("  " + who + "'s routes here: " + spots.size() + " cells (" + tunnel + " underground, " + ladder + " ladder, " + placed + " on placed blocks, "
					+ water + " water), " + routes.size(player.level().dimension()) + " in this dimension");
			AccidentData.PlayerData mine = d.personal(id);
			lines.add("  " + who + "'s cairn: " + mine.cairn().map(c -> Candidate.at(c.pos())).orElse("none") + ", visits " + mine.cairnVisits()
					+ (mine.atCairn ? " (there now)" : ""));
		}
		lines.add("  crosses waiting " + d.crosses().size() + ", standing " + d.built().size() + ", marked deaths " + Services.deaths().count(server) + " in all");
		List<String> history = d.history();
		for (String line : history.subList(Math.max(0, history.size() - 5), history.size())) {
			lines.add("  - " + line);
		}
		return lines;
	}

	/**
	 * The group's weapon and armor stacks he took that could come back on a zombie (from a container a player placed,
	 * still where he left them, not cursed), the ones picked first on top, and when each is ready ("days later").
	 */
	static List<String> stolen(MinecraftServer server) {
		AccidentConfig cfg = AccidentConfig.get();
		long day = GameClock.day(server);
		List<StolenGear.Stolen> all = StolenGear.all(server);
		List<StolenGear.Stolen> back = new ArrayList<>(all.stream().filter(StolenGear.Stolen::canComeBack).toList());
		back.sort(Comparator.comparingInt((StolenGear.Stolen st) -> -StolenGear.score(st.stack())).thenComparingLong(st -> st.entry().day()));
		List<String> lines = new ArrayList<>();
		lines.add("[a1016] accident: " + back.size() + " stolen weapon and armor stacks could come back (" + all.size() + " in the ledger, day " + day + ")");
		for (StolenGear.Stolen st : back) {
			GlobalPos from = st.entry().pos();
			String where = st.held() ? "he holds it" : "in the chest at " + st.entry().to().map(Candidate::at).orElse("?");
			String when = st.oldEnough(day, cfg) ? "ready" : "ready on day " + (st.entry().day() + cfg.swordMinDaysAfterTaken);
			lines.add("  - " + StolenGear.describe(st.stack()) + ": taken on day " + st.entry().day() + " from " + Candidate.at(from.pos()) + " in "
					+ from.dimension().identifier().getPath() + ", " + where + ", " + when);
		}
		long notTheirs = all.stream().filter(st -> !st.fromPlayer()).count();
		long moved = all.stream().filter(st -> st.fromPlayer() && !st.stillThere()).count();
		long cursed = all.stream().filter(st -> st.fromPlayer() && st.stillThere() && st.vanishes()).count();
		if (notTheirs + moved + cursed > 0) {
			lines.add("  not counted: " + notTheirs + " not from a player's containers, " + moved + " no longer where he left them (or not loaded), " + cursed
					+ " would vanish (curse of vanishing)");
		}
		return lines;
	}

	static List<String> candidates(AccidentPlannerImpl planner, ServerPlayer player, @Nullable TrapKind only) {
		List<String> lines = new ArrayList<>();
		lines.add("[a1016] accident candidates for " + player.getName().getString() + " near " + Candidate.at(player.blockPosition())
				+ " (clear of other players' traps / all):");
		for (TrapKind kind : only != null ? List.of(only) : Traps.ALL) {
			List<Candidate> all = kind.candidates(planner.context(player));
			List<Candidate> found = planner.candidates(player, kind);
			planner.remember(player.getUUID(), kind, kind.blocked() != null ? 0 : found.size());
			String blocked = kind.blocked();
			String head = "  " + kind.id() + (kind.live() ? " (live)" : "") + ": " + found.size() + (found.size() != all.size() ? "/" + all.size() : "")
					+ (blocked != null ? " [" + blocked + "]" : "");
			if (found.isEmpty()) {
				lines.add(head);
				continue;
			}
			Candidate first = found.get(0);
			lines.add(head + ", nearest " + Candidate.at(first.pos) + " (" + Math.round(Math.sqrt(first.pos.distSqr(player.blockPosition()))) + " m)");
			if (only != null) {
				for (Candidate c : found.subList(0, Math.min(5, found.size()))) {
					lines.add("    " + Candidate.at(c.pos) + ": " + c.clue);
				}
			}
		}
		return lines;
	}

	private static int arm(CommandContext<CommandSourceStack> ctx, AccidentPlannerImpl planner, ServerPlayer player) throws CommandSyntaxException {
		TrapKind kind = trap(ctx);
		AccidentPlannerImpl.ArmResult result = planner.arm(player, kind, true);
		List<String> lines = new ArrayList<>();
		String who = player.getName().getString();
		if (result.armed() && result.candidate() != null) {
			Candidate c = result.candidate();
			lines.add("[a1016] accident: " + kind.id() + " for " + who + " " + result.message() + " at " + Candidate.at(c.pos) + " ("
					+ Math.round(Math.sqrt(c.pos.distSqr(player.blockPosition()))) + " m away)");
			lines.add("  clue: " + c.clue);
			if (kind.blocked() != null) {
				lines.add("  note: " + kind.blocked());
			}
		} else {
			lines.add("[a1016] accident: " + kind.id() + " for " + who + " not armed: " + result.message());
		}
		return send(ctx, lines);
	}

	private static int disarm(CommandContext<CommandSourceStack> ctx, AccidentPlannerImpl planner, ServerPlayer player) {
		MinecraftServer server = ctx.getSource().getServer();
		String was = planner.armedTrap(server, player.getUUID()).map(ArmedTrap::type).orElse(null);
		planner.disarm(server, player.getUUID(), "disarmed by command");
		String who = player.getName().getString();
		return send(ctx, List.of(was == null ? "[a1016] accident: nothing was armed for " + who : "[a1016] accident: disarmed " + was + " (" + who + "'s)"));
	}

	/**
	 * Builds a cross 7 blocks behind the player. By default a preview: nothing is recorded and no event fires. With
	 * {@code record} it is a real marked death of that player's (state, event, their Ending B count).
	 */
	private static int mark(CommandContext<CommandSourceStack> ctx, DeathMarkerImpl marker, ServerPlayer player, boolean record) throws CommandSyntaxException {
		String cause = StringArgumentType.getString(ctx, "cause");
		if (!DeathCauses.KNOWN.contains(cause)) {
			throw UNKNOWN_CAUSE.create();
		}
		MinecraftServer server = ctx.getSource().getServer();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
		BlockPos spot = BlockPos.containing(player.position().subtract(flat.scale(MARK_BEHIND)));
		List<String> lines = new ArrayList<>();
		String line = DeathMarkerImpl.listLine(player, cause, GameClock.day(server));
		String who = player.getName().getString();
		boolean built;
		if (record) {
			int before = waitingCrosses(server);
			marker.mark(player, cause, spot);
			built = waitingCrosses(server) <= before;
			lines.add("[a1016] accident: RECORDED a marked death of " + who + " (" + cause + ") at " + Candidate.at(spot) + ". It fired MARKED_DEATH and counts toward"
					+ " Ending B (" + who + " " + Services.deaths().count(server, player.getUUID()) + ", everyone " + Services.deaths().count(server) + ", rule "
					+ ModConfig.pacing().endingBMarkedDeaths + ").");
			lines.add("  list line added: " + line);
		} else {
			built = marker.preview(player, cause, spot);
			lines.add("[a1016] accident: preview only, nothing recorded and no event fired. Add 'record' to make it count (it counts toward Ending B).");
			lines.add("  list line it would add: " + line);
		}
		lines.add(built ? "  the cross stands at " + Candidate.at(spot) + ", behind " + who + ": turn around"
				: "  the cross waits until " + Candidate.at(spot) + " is out of view: look away or walk off, then come back");
		return send(ctx, lines);
	}

	/** Crosses still waiting. */
	private static int waitingCrosses(MinecraftServer server) {
		return AccidentInit.planner().data(server).crosses().size();
	}

	private static String minutes(long ticks) {
		if (ticks > 20L * 60 * 60 * 24 * 365) {
			return "until put back";
		}
		return Math.max(0, ticks / 1200) + "m";
	}

	private static int send(CommandContext<CommandSourceStack> ctx, List<String> lines) {
		for (String line : lines) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}
}
