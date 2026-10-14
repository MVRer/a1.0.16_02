package com.forzacode.a1016_02.ending.d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.lore.UntouchedGrove;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Step 2, the untouched grove (lore's chosen Dappled Forest, protected in {@code ProtectedAreas}): the poplar logs
 * anyone in the group cuts there, and its two dangers. While someone climbs, the leaf under their feet goes (only while
 * they look up, D-027). Existing spiders are moved into the canopy and held there, motionless. Both through core, out of
 * view, for whoever is there.
 */
public final class Grove {
	public static final String LEAF_CAUSE = "ending:d/grove_leaves";

	/** The group's visit, in memory: it ends once all of them are far from the grove. */
	private static boolean inGrove;
	private static int spidersThisVisit;
	private static long nextLeaf;
	private static long nextSpider;

	private Grove() {
	}

	public static Optional<GlobalPos> center(MinecraftServer server) {
		return UntouchedGrove.center(server);
	}

	public static boolean contains(MinecraftServer server, GlobalPos pos) {
		return UntouchedGrove.contains(server, pos);
	}

	/** True if this block is inside the untouched grove's protected area and no player placed it there. */
	public static boolean grewInGrove(ServerLevel level, BlockPos pos) {
		boolean inside = Services.protectedAreas().get(UntouchedGrove.AREA_ID)
				.filter(area -> area.dimension().equals(level.dimension()) && area.box().isInside(pos)).isPresent();
		return inside && !Services.watch().wasPlacedByPlayer(level, pos);
	}

	public static boolean isPoplarLog(BlockState state) {
		return state.is(Blocks.POPLAR_LOG) || state.is(Blocks.POPLAR_WOOD);
	}

	/**
	 * A player is breaking a block: a poplar log in the grove cut by anyone who takes part counts, and its drop is
	 * marked as grove wood. True if it counted.
	 */
	static boolean onBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, EndingDState data, Predicate<ServerPlayer> takesPart) {
		return takesPart.test(player) && cutLog(level, data, pos, state);
	}

	/**
	 * Someone cut this block: a poplar log that grew in the untouched grove (inside its protected area, and not placed
	 * by a player: a log carried in does not count) counts, and its drop is grove wood. True if it counted.
	 */
	public static boolean cutLog(ServerLevel level, EndingDState data, BlockPos pos, BlockState state) {
		if (!isPoplarLog(state) || !grewInGrove(level, pos)) {
			return false;
		}
		data.addGroveLog();
		Marks.expectDrop(level, pos, state.getBlock().asItem(), Marks.GROVE_WOOD);
		return true;
	}

	/**
	 * Step 2's dangers, for whoever of the group is in the grove: the leaf under a climber (any of them, at most one per
	 * cooldown), and spiders into the canopy (a budget per group visit, near one of them).
	 */
	static void dangers(List<ServerPlayer> players, View view, EndingDConfig cfg) {
		if (players.isEmpty()) {
			return;
		}
		MinecraftServer server = players.getFirst().level().getServer();
		List<ServerPlayer> inside = new ArrayList<>();
		boolean allFar = true;
		Optional<GlobalPos> center = center(server);
		for (ServerPlayer player : players) {
			GlobalPos at = GlobalPos.of(player.level().dimension(), player.blockPosition());
			if (contains(server, at)) {
				inside.add(player);
			}
			if (center.isPresent() && center.get().dimension().equals(at.dimension())
					&& center.get().pos().distSqr(at.pos()) <= 4.0 * UntouchedGrove.RADIUS * UntouchedGrove.RADIUS) {
				allFar = false;
			}
		}
		if (inside.isEmpty()) {
			if (inGrove && allFar) {
				inGrove = false;
				spidersThisVisit = 0;
			}
			return;
		}
		inGrove = true;
		long now = server.getTickCount();
		if (now >= nextLeaf) {
			for (ServerPlayer player : inside) {
				if (leafUnderClimber(player, view, cfg)) {
					nextLeaf = now + EndingDConfig.ticks(cfg.leafDropCooldownSeconds);
					break;
				}
			}
		}
		if (now >= nextSpider && spidersThisVisit < cfg.spidersPerVisit) {
			nextSpider = now + EndingDConfig.ticks(cfg.spiderCooldownSeconds);
			for (ServerPlayer player : inside) {
				if (spiderIntoCanopy(player, view, cfg, player.level().getRandom())) {
					spidersThisVisit++;
					break;
				}
			}
		}
	}

	/**
	 * The leaf under a climber: the player stands on leaves at least {@code leafDropMinHeight} above the ground, and
	 * the leaf goes the moment it is out of view (in practice: while they look up, D-027). One clue: a hole in the
	 * canopy exactly where they stood.
	 */
	public static boolean leafUnderClimber(ServerPlayer player, View view, EndingDConfig cfg) {
		ServerLevel level = player.level();
		if (!player.onGround()) {
			return false;
		}
		BlockPos under = BlockPos.containing(player.getX(), player.getBoundingBox().minY - 0.01, player.getZ());
		BlockState state = level.getBlockState(under);
		if (!state.is(BlockTags.LEAVES) || Services.watch().wasPlacedByPlayer(level, under) || heightAboveGround(level, under) < cfg.leafDropMinHeight) {
			return false;
		}
		return view.outOfView(level, under) && Services.traces().remove(level, under, LEAF_CAUSE);
	}

	/** Air (or leaves, logs, plants) between this block and the ground under it. */
	static int heightAboveGround(ServerLevel level, BlockPos pos) {
		int height = 0;
		BlockPos cursor = pos.below();
		while (cursor.getY() > level.getMinY() && height < 64) {
			BlockState state = level.getBlockState(cursor);
			if (!state.isAir() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && !state.canBeReplaced()) {
				return height;
			}
			height++;
			cursor = cursor.below();
		}
		return height;
	}

	/**
	 * An existing spider near the player is moved up into the grove's canopy, out of view (where it is and where it
	 * goes), and held there motionless and silent. Spiders climb trees: deniable. The clue: it never moves.
	 */
	public static boolean spiderIntoCanopy(ServerPlayer player, View view, EndingDConfig cfg, RandomSource random) {
		ServerLevel level = player.level();
		MobTamper mobs = Services.mobs();
		AABB search = player.getBoundingBox().inflate(cfg.spiderSearchRadius);
		List<Spider> spiders = new ArrayList<>(level.getEntitiesOfClass(Spider.class, search, s -> s.isAlive() && !mobs.isTampered(s)));
		if (spiders.isEmpty()) {
			return false;
		}
		spiders.sort(Comparator.comparingDouble(s -> s.distanceToSqr(player)));
		List<BlockPos> spots = canopySpots(level, player.blockPosition(), 6, 20, random);
		int ticks = (int) EndingDConfig.ticks(cfg.spiderFreezeSeconds);
		for (Spider spider : spiders) {
			if (!view.outOfView(level, spider.getBoundingBox())) {
				continue;
			}
			for (BlockPos spot : spots) {
				AABB box = spider.getDimensions(spider.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(spot));
				if (!level.noCollision(spider, box) || !view.outOfView(level, box)) {
					continue;
				}
				if (mobs.moveOutOfView(spider, spot)) {
					mobs.freeze(spider, ticks);
					mobs.silence(spider, ticks);
					return true;
				}
			}
		}
		return false;
	}

	/** Tops of the canopy around {@code center}: an air cell resting on leaves, between {@code min} and {@code max} away. */
	static List<BlockPos> canopySpots(ServerLevel level, BlockPos center, int min, int max, RandomSource random) {
		List<BlockPos> spots = new ArrayList<>();
		for (int tries = 0; tries < 96 && spots.size() < 12; tries++) {
			int dx = random.nextIntBetweenInclusive(-max, max);
			int dz = random.nextIntBetweenInclusive(-max, max);
			int d2 = dx * dx + dz * dz;
			if (d2 < min * min || d2 > max * max) {
				continue;
			}
			for (int dy = 12; dy >= -4; dy--) {
				BlockPos pos = center.offset(dx, dy, dz);
				if (level.isLoaded(pos) && level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(BlockTags.LEAVES)) {
					spots.add(pos);
					break;
				}
			}
		}
		return spots;
	}

	static void clear() {
		inGrove = false;
		spidersThisVisit = 0;
		nextLeaf = 0;
		nextSpider = 0;
	}
}
