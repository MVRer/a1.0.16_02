package com.forzacode.a1016_02.dig;

import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Sound-only dig cards: "Mining that moves" and the "Under you" home cues. Nothing in the world changes. */
final class SoundCards {
	private SoundCards() {
	}

	/** The sounds of the block at {@code pos}, or stone if it makes none worth hearing (air, plants). */
	static SoundType soundAt(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isSolidRender() ? state.getSoundType() : SoundType.STONE;
	}

	/** Two hits and a break: someone mining one block. */
	static void mineOnce(ServerPlayer player, long delay, Vec3 pos, SoundType type, float volume, float pitch) {
		DigTicker ticker = DigTicker.INSTANCE;
		ticker.schedule(player, delay, type.getHitSound(), SoundSource.BLOCKS, pos, volume * 0.6F, pitch * 0.5F);
		ticker.schedule(player, delay + 4, type.getHitSound(), SoundSource.BLOCKS, pos, volume * 0.6F, pitch * 0.5F);
		ticker.schedule(player, delay + 8, type.getBreakSound(), SoundSource.BLOCKS, pos, volume, pitch * 0.8F);
	}

	/** A line of block-break sounds that comes through the stone and stops right under the player. Max once per world per stage. */
	static final class MiningThatMoves extends DigCard {
		static final String ID = "mining_that_moves";

		MiningThatMoves() {
			super(ID, Tier.MAJOR, Stage.PROXIMITY, Set.of(Habit.CARVER, Habit.WATCHER), Set.of(CardTag.SOUND, CardTag.DIG), true);
		}

		static String onceKey(ServerLevel level) {
			return ID + "@" + HerobrineState.get(level.getServer()).stage().name();
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			return !data(world).hasOnce(onceKey(world)) && player.onGround()
					&& Services.watch().ticksSinceCombat(player) > ModConfig.realTicks(DigConfig.get().miningNoCombatSeconds)
					&& (DigTicker.underground(world, player.blockPosition()) || world.isDarkOutside());
		}

		@Override
		public FireResult fire(FireContext ctx) {
			ServerLevel level = ctx.level();
			ServerPlayer player = ctx.player();
			DigConfig config = DigConfig.get();
			RandomSource random = ctx.random();
			Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			BlockPos feet = player.blockPosition();
			BlockPos start = feet.relative(dir, config.miningStartDistance).below(6);
			BlockPos end = feet.below(3);
			long stepTicks = Math.max(10, ModConfig.realTicks(config.miningStepSeconds));
			if (ctx.fake()) {
				// Something far off, a few knocks, and it never comes closer.
				for (int i = 0; i < 3; i++) {
					mineOnce(player, i * stepTicks, Vec3.atCenterOf(start), soundAt(level, start), 1.4F, 0.9F + random.nextFloat() * 0.1F);
				}
				return FireResult.FIRED;
			}
			if (!ctx.forced() && data(level).hasOnce(onceKey(level))) {
				return FireResult.SKIPPED;
			}
			int steps = Math.max(2, config.miningSteps);
			for (int i = 0; i < steps; i++) {
				double t = i / (double) (steps - 1);
				Vec3 pos = Vec3.atCenterOf(start).lerp(Vec3.atCenterOf(end), t);
				BlockPos block = BlockPos.containing(pos);
				float volume = (float) Mth.clamp(pos.distanceTo(player.getEyePosition()) / 14.0, 0.6, 2.0);
				mineOnce(player, i * stepTicks, pos, soundAt(level, block), volume, 0.9F + random.nextFloat() * 0.1F);
			}
			data(level).once(onceKey(level));
			return FireResult.FIRED;
		}
	}

	/** A faint block-break through the floor at night, from the network under the base. */
	static final class UnderYouSound extends DigCard {
		static final String ID = "under_you_sound";

		UnderYouSound() {
			super(ID, Tier.MINOR, Stage.TRACES, Set.of(Habit.CARVER), Set.of(CardTag.SOUND, CardTag.DIG), true);
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			return world.isDarkOutside() && atHome(player) && networkCellBelow(player).isPresent();
		}

		@Override
		public FireResult fire(FireContext ctx) {
			Optional<BlockPos> cell = networkCellBelow(ctx.player());
			if (cell.isEmpty()) {
				return FireResult.NO_SPOT;
			}
			Vec3 pos = Vec3.atCenterOf(cell.get());
			float pitch = 0.8F + ctx.random().nextFloat() * 0.15F;
			if (ctx.fake()) {
				DigTicker.INSTANCE.schedule(ctx.player(), 0, SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, pos, 0.7F, pitch);
			} else {
				mineOnce(ctx.player(), 0, pos, soundAt(ctx.level(), cell.get().below()), DigConfig.get().underYouSoundVolume, pitch);
			}
			return FireResult.FIRED;
		}
	}

	/** A footstep that isn't the player's, under the floor. */
	static final class UnderYouFootstep extends DigCard {
		static final String ID = "under_you_footstep";

		UnderYouFootstep() {
			super(ID, Tier.MINOR, Stage.TRACES, Set.of(Habit.VISITOR), Set.of(CardTag.SOUND), true);
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			return atHome(player) && Services.watch().stillTicks(player) > ModConfig.realTicks(DigConfig.get().underYouStepStillSeconds)
					&& networkCellBelow(player).isPresent();
		}

		@Override
		public FireResult fire(FireContext ctx) {
			ServerPlayer player = ctx.player();
			ServerLevel level = ctx.level();
			float volume = DigConfig.get().underYouStepVolume;
			if (ctx.fake()) {
				// Just their own step, late: the floor they stand on.
				BlockPos under = player.blockPosition().below();
				DigTicker.INSTANCE.schedule(player, 8, soundAt(level, under).getStepSound(), SoundSource.PLAYERS, Vec3.atCenterOf(player.blockPosition()),
						volume, 1.0F);
				return FireResult.FIRED;
			}
			Optional<BlockPos> cell = networkCellBelow(player);
			Optional<Network> net = data(level).network();
			if (cell.isEmpty() || net.isEmpty()) {
				return FireResult.NO_SPOT;
			}
			// Walk along the corridor floor from the nearest cell.
			BlockPos floor = cell.get();
			while (net.get().cells.contains(floor.below().asLong())) {
				floor = floor.below();
			}
			Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(ctx.random());
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (net.get().cells.contains(floor.relative(d).asLong())) {
					dir = d;
					break;
				}
			}
			BlockPos step = floor;
			for (int i = 0; i < 3; i++) {
				DigTicker.INSTANCE.schedule(player, i * 9L, soundAt(level, step.below()).getStepSound(), SoundSource.BLOCKS, Vec3.atBottomCenterOf(step),
						volume, 0.9F + ctx.random().nextFloat() * 0.1F);
				if (net.get().cells.contains(step.relative(dir).asLong())) {
					step = step.relative(dir);
				}
			}
			return FireResult.FIRED;
		}
	}
}
