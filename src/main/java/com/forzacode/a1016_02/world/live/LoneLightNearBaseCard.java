package com.forzacode.a1016_02.world.live;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.world.WorldConfig;
import com.forzacode.a1016_02.world.WorldSites;
import com.forzacode.a1016_02.world.gen.Builds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * "Lone light near base" (MAJOR, Proximity): one light where nothing should be, a short walk from the player's
 * base: a redstone torch deep in a dark cave, a single glowstone block on the surface, or a torch on a lone block
 * in the water. Left by others ({@code leave}), out of view, recorded as a LONE_LIGHT site.
 */
public final class LoneLightNearBaseCard implements EventCard {
	public static final String ID = "lone_light_near_base";
	public static final String CAUSE = "world:lone_light_near_base";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Tier tier() {
		return Tier.MAJOR;
	}

	@Override
	public Stage earliestStage() {
		return Stage.PROXIMITY;
	}

	@Override
	public Set<Habit> habits() {
		return Set.of(Habit.WATCHER, Habit.MOURNER);
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.LIGHT);
	}

	@Override
	public boolean hasFake() {
		return false;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return Services.watch().base(player).map(base -> base.dimension().equals(world.dimension())).orElse(false);
	}

	@Override
	public FireResult fire(FireContext ctx) {
		Optional<GlobalPos> base = Services.watch().base(ctx.player());
		if (base.isEmpty() || !base.get().dimension().equals(ctx.level().dimension())) {
			return FireResult.SKIPPED;
		}
		BlockPos placed = placeNear(ctx.level(), base.get().pos(), ctx.random());
		return placed != null ? FireResult.FIRED : FireResult.NO_SPOT;
	}

	/** Leaves one light near {@code base}, out of view. Returns where, or null. */
	public static @Nullable BlockPos placeNear(ServerLevel level, BlockPos base, RandomSource random) {
		WorldConfig config = WorldConfig.get();
		LiveTerrain terrain = new LiveTerrain(level);
		List<Builds.LightKind> kinds = new ArrayList<>(List.of(Builds.LightKind.CAVE_TORCH, Builds.LightKind.GLOWSTONE, Builds.LightKind.OCEAN_TORCH));
		Util.shuffle(kinds, random);
		if (level.isDarkOutside()) {
			kinds.remove(Builds.LightKind.GLOWSTONE);
			kinds.addFirst(Builds.LightKind.GLOWSTONE); // seen from the base at night
		}
		for (Builds.LightKind kind : kinds) {
			for (int attempt = 0; attempt < 24; attempt++) {
				double angle = random.nextDouble() * Math.PI * 2;
				int d = Mth.nextInt(random, config.baseLightMinDistance, config.baseLightMaxDistance);
				int x = base.getX() + Mth.floor(Math.cos(angle) * d);
				int z = base.getZ() + Mth.floor(Math.sin(angle) * d);
				if (!terrain.loaded(x, z)) {
					continue;
				}
				BlockPos light = tryPlace(level, terrain, kind, x, z);
				if (light != null) {
					WorldSites.record(SiteType.LONE_LIGHT, level.dimension(), light, 1, null);
					A1016_02.LOGGER.info("[a1016] world: lone light ({}) near base at {}", kind, light.toShortString());
					return light;
				}
			}
		}
		return null;
	}

	private static @Nullable BlockPos tryPlace(ServerLevel level, LiveTerrain terrain, Builds.LightKind kind, int x, int z) {
		int ground = terrain.ground(x, z);
		switch (kind) {
			case GLOWSTONE -> {
				BlockPos pos = new BlockPos(x, ground + 1, z);
				if (terrain.wet(x, z) || Services.watch().wasPlacedByPlayer(level, pos.below())) {
					return null;
				}
				return Services.traces().leave(level, pos, Blocks.GLOWSTONE.defaultBlockState(), CAUSE) ? pos : null;
			}
			case CAVE_TORCH -> {
				BlockPos floor = LivePlacer.darkCaveFloor(level, x, z, ground);
				if (floor == null) {
					return null;
				}
				BlockPos pos = floor.above();
				BlockState torch = Blocks.REDSTONE_TORCH.defaultBlockState();
				return torch.canSurvive(level, pos) && Services.traces().leave(level, pos, torch, CAUSE) ? pos : null;
			}
			case OCEAN_TORCH -> {
				int surface = terrain.surface(x, z);
				if (surface - ground < 2) {
					return null;
				}
				BlockPos block = new BlockPos(x, surface, z);
				TraceBatch batch = Services.traces().batch(level, CAUSE).leave(block, Blocks.COBBLESTONE.defaultBlockState())
						.leave(block.above(), Blocks.TORCH.defaultBlockState());
				return batch.commit() ? block.above() : null;
			}
		}
		return null;
	}
}
