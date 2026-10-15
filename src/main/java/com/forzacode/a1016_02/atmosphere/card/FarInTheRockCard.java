package com.forzacode.a1016_02.atmosphere.card;

import java.util.Set;
import java.util.function.BiPredicate;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Gates;
import com.forzacode.a1016_02.atmosphere.WorldScan;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Far in the rock (D-070's Alone stage): underground, with no other participant within {@code rockSoundAloneRadius}
 * (24), one vanilla sound of the rock around, a stone or deepslate hit or break, or a gravel slide, 18 to 28 blocks
 * off, below or to the side, from inside solid rock ({@link WorldScan#rockSpot}), faint. Nothing changes in the
 * world. The sound is the block's own ({@code SoundType}), so it is what that rock would sound like; it plays to
 * everyone in hearing range ({@link SoundOut}, D-060). The false positive is the vanilla cave sound.
 */
public final class FarInTheRockCard extends AtmosphereCard {
	public static final String ID = "far_in_the_rock";

	/** Whether a position is underground. A game test swaps in its own, as {@link #sounds}. */
	BiPredicate<Level, BlockPos> underground = WorldScan::underground;

	public FarInTheRockCard() {
		super(ID, Tier.AMBIENT, Stage.ALONE, Set.of(), Set.of(CardTag.SOUND), true);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return Gates.rockSound(underground.test(world, player.blockPosition()), aloneHere(player, cfg().rockSoundAloneRadius));
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
		RandomSource random = ctx.random();
		BlockPos spot = WorldScan.rockSpot(level, player.blockPosition(), cfg.rockSoundMinDistance, cfg.rockSoundMaxDistance, cfg.rockSoundMaxAbove,
				cfg.rockSoundMaxBelow, random);
		if (spot == null) {
			return FireResult.NO_SPOT;
		}
		BlockState state = level.getBlockState(spot);
		SoundType type = state.getSoundType();
		SoundEvent sound = soundOf(state, random.nextDouble() < cfg.rockSoundHitShare);
		double pitch = cfg.rockSoundPitchMin + random.nextDouble() * Math.max(0.0, cfg.rockSoundPitchMax - cfg.rockSoundPitchMin);
		sounds.play(level, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.BLOCKS, Vec3.atCenterOf(spot), (float) cfg.rockSoundVolume,
				(float) (pitch * type.getPitch()));
		return FireResult.FIRED;
	}

	/** The rock's own sound: gravel slides (its break), stone and deepslate are a hit or a break. */
	static SoundEvent soundOf(BlockState state, boolean hit) {
		SoundType type = state.getSoundType();
		if (state.is(Blocks.GRAVEL)) {
			return type.getBreakSound();
		}
		return hit ? type.getHitSound() : type.getBreakSound();
	}
}
