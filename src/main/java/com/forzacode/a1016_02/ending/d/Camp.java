package com.forzacode.a1016_02.ending.d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Step 1's danger (DESIGN.md: "creepers moved into the tents at night, silent"): at night, while the player is near
 * the Abandoned Camp whose secret chest holds F28, creepers that are already out there are moved into the camp's tents
 * (out of view where they are and where they go) and silenced. None is ever spawned. Deniable: creepers wander in the
 * dark. The clue: a creeper standing inside a tent that made no sound.
 */
public final class Camp {
	/** Per night, in memory. */
	private static long night = -1;
	private static int movedTonight;
	private static long next;

	private Camp() {
	}

	/** The map's camp: where lore put F28 (the camp's secret chest). */
	public static Optional<GlobalPos> camp(MinecraftServer server) {
		return Optional.ofNullable(HerobrineState.get(server).fragmentsPlaced().get("F28"));
	}

	/** Each check of step 1: at night, near the camp, at most {@code creepersPerNight}. */
	static void danger(ServerPlayer player, View view, EndingDConfig cfg) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		Optional<GlobalPos> camp = camp(server);
		long now = server.getTickCount();
		if (camp.isEmpty() || !camp.get().dimension().equals(level.dimension()) || !level.isDarkOutside() || now < next
				|| camp.get().pos().distToCenterSqr(player.position()) > (double) cfg.campWatchRadius * cfg.campWatchRadius) {
			return;
		}
		long today = GameClock.day(server);
		if (today != night) {
			night = today;
			movedTonight = 0;
		}
		if (movedTonight >= cfg.creepersPerNight) {
			return;
		}
		next = now + EndingDConfig.ticks(cfg.creeperCooldownSeconds);
		if (creepersIntoTents(level, camp.get().pos(), view, cfg, level.getRandom())) {
			movedTonight++;
		}
	}

	/**
	 * Moves one existing creeper near the camp into one of its tents, through {@code MobTamper} (out of view at both
	 * ends), and silences it. True if one was moved.
	 */
	public static boolean creepersIntoTents(ServerLevel level, BlockPos camp, View view, EndingDConfig cfg, RandomSource random) {
		List<BlockPos> tents = tentSpots(level, camp, cfg.campRadius);
		if (tents.isEmpty()) {
			return false;
		}
		MobTamper mobs = Services.mobs();
		AABB search = new AABB(camp).inflate(cfg.creeperSearchRadius);
		List<Creeper> creepers = new ArrayList<>(level.getEntitiesOfClass(Creeper.class, search, c -> c.isAlive() && !mobs.isTampered(c)));
		creepers.sort(Comparator.comparingDouble(c -> c.distanceToSqr(Vec3.atCenterOf(camp))));
		int ticks = (int) EndingDConfig.ticks(cfg.creeperSilenceSeconds);
		for (Creeper creeper : creepers) {
			if (!view.outOfView(level, creeper.getBoundingBox())) {
				continue;
			}
			List<BlockPos> spots = new ArrayList<>(tents);
			java.util.Collections.shuffle(spots, new java.util.Random(random.nextLong()));
			for (BlockPos spot : spots) {
				AABB box = creeper.getDimensions(creeper.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(spot));
				if (!level.noCollision(creeper, box) || !view.outOfView(level, box)) {
					continue;
				}
				if (mobs.moveOutOfView(creeper, spot)) {
					mobs.silence(creeper, ticks);
					A1016_02.LOGGER.info("[a1016] ending d: a creeper stands in the camp's tent at {}", spot.toShortString());
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Floor cells inside the camp's tents: two cells of air on a sturdy floor, under a roof (the first block above,
	 * within five, is something built: not leaves or a trunk, so the shade of a tree is not a tent).
	 */
	static List<BlockPos> tentSpots(ServerLevel level, BlockPos camp, int radius) {
		List<BlockPos> spots = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(camp.offset(-radius, -4, -radius), camp.offset(radius, 4, radius))) {
			if (!level.isLoaded(p) || !level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) {
				continue;
			}
			BlockPos below = p.below();
			if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
				continue;
			}
			for (int up = 2; up <= 5; up++) {
				BlockState roof = level.getBlockState(p.above(up));
				if (!roof.isAir()) {
					if (!roof.is(BlockTags.LEAVES) && !roof.is(BlockTags.LOGS)) {
						spots.add(p.immutable());
					}
					break;
				}
			}
		}
		return spots;
	}

	static void clear() {
		night = -1;
		movedTonight = 0;
		next = 0;
	}
}
