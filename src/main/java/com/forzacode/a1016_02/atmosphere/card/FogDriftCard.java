package com.forzacode.a1016_02.atmosphere.card;

import java.util.Set;

import com.forzacode.a1016_02.atmosphere.ActiveEffects;
import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Gates;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Fog drift: the fog closes in sharply for 3 to 8 seconds, then eases back. Never in combat, rarer by day. */
public final class FogDriftCard extends AtmosphereCard {
	public static final String ID = "fog_drift";

	public FogDriftCard() {
		super(ID, Tier.AMBIENT, Stage.ALONE, Set.of(), Set.of(CardTag.FOG), false);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return !Gates.quietForGood(world.getServer())
				&& Gates.fogDrift(Services.watch().ticksSinceCombat(player), AtmosphereConfig.ticks(cfg().fogDriftNoCombatSeconds));
	}

	@Override
	public FireResult fire(FireContext ctx) {
		AtmosphereConfig cfg = cfg();
		if (!ctx.forced() && ctx.level().isBrightOutside() && ctx.random().nextDouble() >= cfg.fogDriftDaylightChance) {
			return FireResult.SKIPPED;
		}
		float strength = (float) (cfg.fogDriftStrengthMin + ctx.random().nextDouble() * (cfg.fogDriftStrengthMax - cfg.fogDriftStrengthMin));
		int hold = (int) ModConfig.pacing().fogDrift().pick(ctx.random());
		return ActiveEffects.fogSurge(ctx.player(), strength, cfg.fogDriftRampTicks, hold, cfg.fogDriftFadeTicks) ? FireResult.FIRED : FireResult.SKIPPED;
	}
}
