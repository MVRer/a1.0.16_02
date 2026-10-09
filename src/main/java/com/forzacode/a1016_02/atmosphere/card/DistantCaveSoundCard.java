package com.forzacode.a1016_02.atmosphere.card;

import java.util.Set;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Gates;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Distant cave sound: the game's own cave ambience, timed for when the player is alone and still. A pure false positive. */
public final class DistantCaveSoundCard extends AtmosphereCard {
	public static final String ID = "distant_cave_sound";

	public DistantCaveSoundCard() {
		super(ID, Tier.AMBIENT, Stage.ALONE, Set.of(), Set.of(CardTag.SOUND), true);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		AtmosphereConfig cfg = cfg();
		return Gates.caveSound(alone(player, cfg.caveSoundAloneRadius), Services.watch().stillTicks(player), AtmosphereConfig.ticks(cfg.caveSoundStillSeconds));
	}

	@Override
	public FireResult fire(FireContext ctx) {
		play(ctx.player(), ctx.random());
		return FireResult.FIRED;
	}

	/** The vanilla cave sound somewhere around the player, a little below. Also the mining card's false positive. */
	static void play(ServerPlayer player, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		double dist = 9.0 + random.nextDouble() * 6.0;
		Vec3 pos = player.position().add(Math.cos(angle) * dist, -2.0 - random.nextDouble() * 5.0, Math.sin(angle) * dist);
		SoundCues.playTo(player, SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, pos, 1.3F, 0.8F + random.nextFloat() * 0.4F);
	}
}
