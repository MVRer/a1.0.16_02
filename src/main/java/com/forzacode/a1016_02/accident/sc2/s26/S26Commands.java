package com.forzacode.a1016_02.accident.sc2.s26;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.accident.AccidentInit;
import com.forzacode.a1016_02.accident.AccidentPlannerImpl;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.accident.Traps;
import com.forzacode.a1016_02.accident.sc2.HuntCommands;
import com.forzacode.a1016_02.accident.sc2.HuntData;

import net.minecraft.server.level.ServerPlayer;

/** {@code /a1016 sc 26 [player] | arm [force] [player] | reset | clear} (see {@link HuntCommands}). */
final class S26Commands {
	private S26Commands() {
	}

	static void register() {
		HuntCommands.register(new HuntCommands.Kind("26", Traps.SC26, S26Commands::status, S26Commands::arm, server -> HuntData.get(server).reset(CallTrap.ID)));
	}

	/** Why it would or would not start for this player now, and with how many. */
	static List<String> status(ServerPlayer player, boolean forced) {
		CallTrap.Env env = CallTrap.env(player).forced(forced);
		AccidentPlannerImpl planner = AccidentInit.planner();
		TrapContext ctx = planner.context(player);
		List<String> lines = new ArrayList<>();
		String why = CallTrap.refusal(player, ctx, env);
		int near = CallTrap.callees(player, ctx, env.cfg()).size();
		lines.add("sc 26 for " + player.getName().getString() + ": " + (why == null ? "may start" : "not now: " + why) + "; " + near + " near (needs "
				+ env.cfg().minMobs + ", takes up to " + env.cfg().maxMobs + ")");
		lines.add("last day " + env.data().lastDay(CallTrap.ID, player.getUUID()) + ", world count " + env.data().count(CallTrap.ID) + " of "
				+ env.cfg().maxPerWorld + "; trap armed: " + planner.armedTrap(player.level().getServer(), player.getUUID()).map(t -> t.type()).orElse("none"));
		return lines;
	}

	static AccidentPlannerImpl.ArmResult arm(AccidentPlannerImpl planner, ServerPlayer player, boolean forced) {
		if (!forced) {
			return planner.arm(player, Traps.SC26, true);
		}
		CallTrap.use(player.getUUID(), CallTrap.env(player).forced(true));
		try {
			return planner.arm(player, Traps.SC26, true);
		} finally {
			CallTrap.use(player.getUUID(), null);
		}
	}
}
