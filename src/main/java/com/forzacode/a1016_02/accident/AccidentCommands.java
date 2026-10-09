package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.List;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /a1016 accident status | candidates [trap] | arm <trap> | disarm | mark <cause> [record]}. Arming skips the
 * session cap but never the one-at-a-time or out-of-view rules. {@code mark} only previews the cross unless told to
 * {@code record}, because a recorded marked death counts toward Ending B.
 */
final class AccidentCommands {
	/** How far behind the player {@code mark} puts the cross, so it can be built out of view and then looked at. */
	private static final double MARK_BEHIND = 7;
	private static final SimpleCommandExceptionType UNKNOWN_TRAP = new SimpleCommandExceptionType(Component.literal("unknown trap"));
	private static final SimpleCommandExceptionType UNKNOWN_CAUSE = new SimpleCommandExceptionType(
			Component.literal("unknown cause; use one of " + String.join(", ", DeathCauses.KNOWN)));

	private AccidentCommands() {
	}

	static void register(AccidentPlannerImpl planner, DeathMarkerImpl marker) {
		CommandHooks.register((root, context) -> root.then(Commands.literal("accident")
				.then(Commands.literal("status").executes(ctx -> send(ctx, status(planner, ctx.getSource().getServer(), ctx.getSource().getPlayer()))))
				.then(Commands.literal("candidates")
						.executes(ctx -> send(ctx, candidates(planner, ctx.getSource().getPlayerOrException(), null)))
						.then(Commands.argument("trap", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Traps.ALL.stream().map(TrapKind::id), builder))
								.executes(ctx -> send(ctx, candidates(planner, ctx.getSource().getPlayerOrException(), trap(ctx))))))
				.then(Commands.literal("arm")
						.then(Commands.argument("trap", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Traps.ALL.stream().map(TrapKind::id), builder))
								.executes(ctx -> arm(ctx, planner))))
				.then(Commands.literal("disarm").executes(ctx -> {
					MinecraftServer server = ctx.getSource().getServer();
					String was = planner.armedTrap(server).map(ArmedTrap::type).orElse(null);
					planner.disarm(server, "disarmed by command");
					return send(ctx, List.of(was == null ? "[a1016] accident: nothing was armed" : "[a1016] accident: disarmed " + was));
				}))
				.then(Commands.literal("mark")
						.then(Commands.argument("cause", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(DeathCauses.KNOWN, builder))
								.executes(ctx -> mark(ctx, marker, false))
								.then(Commands.literal("record").executes(ctx -> mark(ctx, marker, true)))))));
	}

	private static TrapKind trap(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String id = StringArgumentType.getString(ctx, "trap");
		return Traps.byId(id).orElseThrow(UNKNOWN_TRAP::create);
	}

	static List<String> status(AccidentPlannerImpl planner, MinecraftServer server, ServerPlayer player) {
		AccidentData d = planner.data(server);
		AccidentConfig cfg = AccidentConfig.get();
		long now = GameClock.playTicks(server);
		List<String> lines = new ArrayList<>();
		lines.add(d.armed().map(t -> "[a1016] accident: " + t.type() + " " + t.phase().name().toLowerCase() + " at " + Candidate.at(t.pos()) + " in "
				+ t.dimension().identifier().getPath() + ", " + minutes(t.until() - now) + " left").orElse("[a1016] accident: nothing armed"));
		d.armed().ifPresent(t -> lines.add("  clue: " + t.clue()));
		d.restoring().ifPresent(t -> lines.add("  dark corner torches still out at " + Candidate.at(t.pos())));
		lines.add("  session arms " + planner.sessionArms() + "/" + cfg.maxTrapsPerSession + ", MobTamper " + (Services.mobs() instanceof MobTamper.Stub ? "stub" : "real")
				+ ", falling opt-in " + (CoreGaps.FALLING_OPT_IN ? "yes" : "no (gravel ceiling and dripstone never spring)"));
		if (player != null) {
			int tunnel = 0;
			int ladder = 0;
			int placed = 0;
			int water = 0;
			List<RouteBook.Spot> spots = d.routes().near(player.level().dimension(), player.blockPosition(), cfg.scanRadius, Integer.MAX_VALUE);
			for (RouteBook.Spot spot : spots) {
				tunnel += spot.point().has(RouteBook.UNDER) ? 1 : 0;
				ladder += spot.point().has(RouteBook.LADDER) ? 1 : 0;
				placed += spot.point().has(RouteBook.ON_PLACED) ? 1 : 0;
				water += spot.point().has(RouteBook.WATER) ? 1 : 0;
			}
			lines.add("  routes here: " + spots.size() + " cells (" + tunnel + " underground, " + ladder + " ladder, " + placed + " on placed blocks, " + water
					+ " water), " + d.routes().size(player.level().dimension()) + " in this dimension");
		}
		lines.add("  cairn: " + d.cairn().map(c -> Candidate.at(c.pos())).orElse("none") + ", visits " + d.cairnVisits() + (d.atCairn ? " (there now)" : "")
				+ "; grove leaves " + Math.max(0, d.lure.groveLeaves));
		lines.add("  crosses waiting " + d.crosses().size() + ", marked deaths " + Services.deaths().count(server));
		List<String> history = d.history();
		for (String line : history.subList(Math.max(0, history.size() - 5), history.size())) {
			lines.add("  - " + line);
		}
		return lines;
	}

	static List<String> candidates(AccidentPlannerImpl planner, ServerPlayer player, TrapKind only) {
		List<String> lines = new ArrayList<>();
		lines.add("[a1016] accident candidates near " + Candidate.at(player.blockPosition()) + ":");
		for (TrapKind kind : only != null ? List.of(only) : Traps.ALL) {
			List<Candidate> found = planner.candidates(player, kind);
			planner.remember(kind, kind.blocked() != null ? 0 : found.size());
			String blocked = kind.blocked();
			String head = "  " + kind.id() + (kind.live() ? " (live)" : "") + ": " + found.size() + (blocked != null ? " [" + blocked + "]" : "");
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

	private static int arm(CommandContext<CommandSourceStack> ctx, AccidentPlannerImpl planner) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TrapKind kind = trap(ctx);
		AccidentPlannerImpl.ArmResult result = planner.arm(player, kind, true);
		List<String> lines = new ArrayList<>();
		if (result.armed() && result.candidate() != null) {
			Candidate c = result.candidate();
			lines.add("[a1016] accident: " + kind.id() + " " + result.message() + " at " + Candidate.at(c.pos) + " ("
					+ Math.round(Math.sqrt(c.pos.distSqr(player.blockPosition()))) + " m away)");
			lines.add("  clue: " + c.clue);
			if (kind.blocked() != null) {
				lines.add("  note: " + kind.blocked());
			}
		} else {
			lines.add("[a1016] accident: " + kind.id() + " not armed: " + result.message());
		}
		return send(ctx, lines);
	}

	/**
	 * Builds a cross 7 blocks behind the player. By default a preview: nothing is recorded and no event fires. With
	 * {@code record} it is a real marked death (state, event, Ending B count).
	 */
	private static int mark(CommandContext<CommandSourceStack> ctx, DeathMarkerImpl marker, boolean record) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
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
		boolean built;
		if (record) {
			int before = waitingCrosses(server);
			marker.mark(player, cause, spot);
			built = waitingCrosses(server) <= before;
			lines.add("[a1016] accident: RECORDED a marked death (" + cause + ") at " + Candidate.at(spot) + ". It fired MARKED_DEATH and counts toward Ending B ("
					+ Services.deaths().count(server) + "/" + ModConfig.pacing().endingBMarkedDeaths + ").");
			lines.add("  list line added: " + line);
		} else {
			built = marker.preview(player, cause, spot);
			lines.add("[a1016] accident: preview only, nothing recorded and no event fired. Add 'record' to make it count (it counts toward Ending B).");
			lines.add("  list line it would add: " + line);
		}
		lines.add(built ? "  the cross stands at " + Candidate.at(spot) + ", behind you: turn around"
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
