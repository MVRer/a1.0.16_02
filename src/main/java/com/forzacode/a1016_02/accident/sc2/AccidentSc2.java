package com.forzacode.a1016_02.accident.sc2;

import com.forzacode.a1016_02.accident.sc2.s25.S25;
import com.forzacode.a1016_02.accident.sc2.s26.S26;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.level.ServerPlayer;

/**
 * The self-created layer's second wave (docs/SELF_CREATED_2.md), accident's part: one line per feature, so tasks running
 * at the same time never touch the same file. Called once from accident's init. Also the one tick, death and stop hook
 * of the scenes both trap kinds run ({@link Hunts}).
 */
public final class AccidentSc2 {
	private AccidentSc2() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Hunts::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof ServerPlayer)) {
				Hunts.onMobDied(entity);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Hunts.clear());
		S25.init();
		S26.init();
	}
}
