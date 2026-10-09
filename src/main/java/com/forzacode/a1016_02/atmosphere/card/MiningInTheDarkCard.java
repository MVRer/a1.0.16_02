package com.forzacode.a1016_02.atmosphere.card;

import java.util.Set;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.AtmosphereData;
import com.forzacode.a1016_02.atmosphere.Gates;
import com.forzacode.a1016_02.atmosphere.WorldScan;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Mining in the dark: at night, in bed or still for a while, one block-break sound close by. Nothing is broken.
 * At most once per night, and never within the gap after another sound card. The false positive is a vanilla cave
 * sound.
 */
public final class MiningInTheDarkCard extends AtmosphereCard {
	public static final String ID = "mining_in_the_dark";

	public MiningInTheDarkCard() {
		super(ID, Tier.MINOR, Stage.PROXIMITY, Set.of(Habit.CARVER), Set.of(CardTag.SOUND), true);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		MinecraftServer server = world.getServer();
		Pacing pacing = ModConfig.pacing();
		return Gates.miningInTheDark(Gates.isNight(world), Services.watch().isSleeping(player), Services.watch().stillTicks(player),
				pacing.miningDarkStillTicks(), Services.director().ticksSinceTag(server, CardTag.SOUND), pacing.miningDarkSoundGapTicks(),
				AtmosphereData.get(server).miningOn(GameClock.day(server)), pacing.miningDarkPerNight);
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerPlayer player = ctx.player();
		if (ctx.fake()) {
			DistantCaveSoundCard.play(player, ctx.random());
			return FireResult.FIRED;
		}
		AtmosphereConfig cfg = cfg();
		ServerLevel level = ctx.level();
		BlockPos spot = WorldScan.miningSpot(level, player.blockPosition(), cfg.miningMinDistance, cfg.miningMaxDistance, ctx.random());
		if (spot == null) {
			return FireResult.NO_SPOT;
		}
		BlockState state = level.getBlockState(spot);
		SoundType sound = state.getSoundType();
		SoundCues.playTo(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound.getBreakSound()), SoundSource.BLOCKS, Vec3.atCenterOf(spot),
				(sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		MinecraftServer server = level.getServer();
		AtmosphereData.get(server).recordMining(GameClock.day(server));
		return FireResult.FIRED;
	}
}
