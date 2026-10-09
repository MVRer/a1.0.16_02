package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.world.gen.ScarContext;
import com.forzacode.a1016_02.world.gen.ScarPlan;
import com.forzacode.a1016_02.world.gen.ScarPlanner;
import com.forzacode.a1016_02.world.live.LivePlacer;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Debug commands for the world workstream (op level 2):
 * {@code /a1016 world sites [type] | locate <scar> | place <scar> | newscar now}.
 */
final class WorldCommands {
	private static final int SITE_LINES = 12;
	private static final int LOCATE_RADIUS = 4000;

	private WorldCommands() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("world")
				.then(Commands.literal("sites")
						.executes(ctx -> sites(ctx, null))
						.then(Commands.argument("type", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(SiteType.values()).map(t -> t.name().toLowerCase(Locale.ROOT)), b))
								.executes(ctx -> sites(ctx, StringArgumentType.getString(ctx, "type")))))
				.then(Commands.literal("locate")
						.then(Commands.argument("scar", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(ScarKind.values()).map(ScarKind::id), b))
								.executes(WorldCommands::locate)))
				.then(Commands.literal("place")
						.then(Commands.argument("scar", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(LivePlacer.names(), b))
								.executes(WorldCommands::place)))
				.then(Commands.literal("newscar")
						.then(Commands.literal("now").executes(WorldCommands::newScarNow)))));
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String line) {
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
	}

	private static int sites(CommandContext<CommandSourceStack> ctx, String typeName) {
		SiteType type = null;
		if (typeName != null) {
			try {
				type = SiteType.valueOf(typeName.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				ctx.getSource().sendFailure(Component.literal("[a1016] unknown site type " + typeName));
				return 0;
			}
		}
		ServerLevel level = ctx.getSource().getLevel();
		Vec3 from = ctx.getSource().getPosition();
		SiteType filter = type;
		List<SiteRegistry.Site> sites = new ArrayList<>(Services.sites().all().stream()
				.filter(s -> s.dimension().equals(level.dimension()) && (filter == null || s.type() == filter))
				.sorted(Comparator.comparingDouble(s -> horizontal(from, s.pos())))
				.toList());
		say(ctx, "[a1016] " + sites.size() + " site(s)" + (filter == null ? "" : " of " + filter) + " in " + level.dimension().identifier());
		for (SiteRegistry.Site site : sites.subList(0, Math.min(SITE_LINES, sites.size()))) {
			say(ctx, String.format(Locale.ROOT, "#%d %s %s d=%d size=%d%s", site.id(), site.type(), site.pos().toShortString(),
					Math.round(horizontal(from, site.pos())), site.size(), site.claimedBy().map(id -> " claimed by " + id).orElse("")));
		}
		return sites.size();
	}

	private static int locate(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "scar");
		Optional<ScarKind> kind = ScarKind.byId(name);
		ScarContext context = ScarContext.current();
		ScarPlanner planner = context == null ? null : context.planner(ctx.getSource().getLevel());
		if (kind.isEmpty() || planner == null) {
			ctx.getSource().sendFailure(Component.literal(kind.isEmpty() ? "[a1016] unknown scar " + name : "[a1016] scars only grow in the overworld"));
			return 0;
		}
		Vec3 from = ctx.getSource().getPosition();
		List<ScarPlan> plans = planner.plansNear(kind.get(), (int) from.x, (int) from.z, LOCATE_RADIUS);
		say(ctx, String.format(Locale.ROOT, "[a1016] %d planned %s within %d (habits=%s density=%s, weight %.1f)", plans.size(), kind.get().id(),
				LOCATE_RADIUS, context.habits(), context.density(), planner.weight(kind.get())));
		for (ScarPlan plan : plans.subList(0, Math.min(5, plans.size()))) {
			boolean core = kind.get() == ScarKind.OCEAN_PYRAMID && plan.anchor().equals(planner.corePyramid().orElse(null));
			say(ctx, String.format(Locale.ROOT, "%s %s d=%d size=%d%s", plan.kind().id(), plan.anchor().toShortString(),
					Math.round(horizontal(from, plan.anchor())), plan.size(), core ? " (core pocket)" : ""));
		}
		BlockPos origin = planner.origin();
		say(ctx, "spawn search origin " + origin.getX() + " " + origin.getZ());
		return plans.size();
	}

	private static int place(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "scar");
		LivePlacer.Result result = LivePlacer.place(ctx.getSource(), name);
		if (result.ok()) {
			say(ctx, "[a1016] " + result.message());
			return 1;
		}
		ctx.getSource().sendFailure(Component.literal("[a1016] " + result.message()));
		return 0;
	}

	private static int newScarNow(CommandContext<CommandSourceStack> ctx) {
		NewScarPlacer.Outcome outcome = NewScarPlacer.forceNow(ctx.getSource().getServer());
		if (outcome.placed()) {
			say(ctx, "[a1016] " + outcome.message());
			return 1;
		}
		ctx.getSource().sendFailure(Component.literal("[a1016] " + outcome.message()));
		return 0;
	}

	private static double horizontal(Vec3 from, BlockPos pos) {
		double dx = pos.getX() + 0.5 - from.x;
		double dz = pos.getZ() + 0.5 - from.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
