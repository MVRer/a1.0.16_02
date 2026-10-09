package com.forzacode.a1016_02.atmosphere.card;

import java.util.Set;

import com.forzacode.a1016_02.atmosphere.ActiveEffects;
import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Silence: ambient sound, weather and music cut out for about a minute, then come back gradually. */
public final class SilenceCard extends AtmosphereCard {
	public static final String ID = "silence";

	public SilenceCard() {
		super(ID, Tier.MINOR, Stage.TRACES, Set.of(), Set.of(CardTag.SOUND), false);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return !inCombat(player, AtmosphereConfig.ticks(cfg().fogDriftNoCombatSeconds));
	}

	@Override
	public FireResult fire(FireContext ctx) {
		int ticks = (int) ModConfig.pacing().silenceTicks();
		ActiveEffects.silence(ctx.player(), ticks, AtmosphereConfig.ticks(cfg().silenceFadeSeconds));
		return FireResult.FIRED;
	}
}
