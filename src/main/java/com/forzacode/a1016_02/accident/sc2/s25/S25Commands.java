package com.forzacode.a1016_02.accident.sc2.s25;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.accident.AccidentInit;
import com.forzacode.a1016_02.accident.AccidentPlannerImpl;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.accident.Traps;
import com.forzacode.a1016_02.accident.sc2.HuntCommands;
import com.forzacode.a1016_02.accident.sc2.HuntData;

import net.minecraft.server.level.ServerPlayer;

/** {@code /a1016 sc 25 [player] | arm [force] [player] | reset | clear} (see {@link HuntCommands}). */
final class S25Commands {
	private S25Commands() {
	}

	static void register() {
		HuntCommands.register(new HuntCommands.Kind("25", Traps.SC25, S25Commands::status, S25Commands::arm, server -> HuntData.get(server).reset(PackTrap.ID)));
	}

	/** Why it would or would not start for this player now, and with how many. */
	static List<String> status(ServerPlayer player, boolean forced) {
		PackTrap.Env env = PackTrap.env(player).forced(forced);
		AccidentPlannerImpl planner = AccidentInit.planner();
		TrapContext ctx = planner.context(player);
		List<String> lines = new ArrayList<>();
		String why = PackTrap.refusal(player, ctx, env);
		int wolves = PackTrap.wolves(player, ctx, env.cfg()).size();
		lines.add("sc 25 for " + player.getName().getString() + ": " + (why == null ? "may start" : "not now: " + why) + "; " + wolves + " near out of view (needs "
				+ Math.max(3, env.cfg().minWolves) + ", takes " + PackTrap.size(player, env.cfg()) + ")");
		lines.add("today's roll " + String.format("%.2f", PackTrap.roll(env, player.getUUID())) + " against " + String.format("%.2f", PackTrap.chance(env, player.getUUID()))
				+ "; last day " + env.data().lastDay(PackTrap.ID, player.getUUID()) + ", world count " + env.data().count(PackTrap.ID) + " of " + env.cfg().maxPerWorld
				+ "; trap armed: " + planner.armedTrap(player.level().getServer(), player.getUUID()).map(t -> t.type()).orElse("none"));
		return lines;
	}

	static AccidentPlannerImpl.ArmResult arm(AccidentPlannerImpl planner, ServerPlayer player, boolean forced) {
		if (!forced) {
			return planner.arm(player, Traps.SC25, true);
		}
		PackTrap.use(player.getUUID(), PackTrap.env(player).forced(true));
		try {
			return planner.arm(player, Traps.SC25, true);
		} finally {
			PackTrap.use(player.getUUID(), null);
		}
	}
}
