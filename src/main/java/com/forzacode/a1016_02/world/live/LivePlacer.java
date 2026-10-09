package com.forzacode.a1016_02.world.live;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.world.ScarKind;
import com.forzacode.a1016_02.world.WorldConfig;
import com.forzacode.a1016_02.world.WorldSites;
import com.forzacode.a1016_02.world.gen.Blueprint;
import com.forzacode.a1016_02.world.gen.Builds;
import com.forzacode.a1016_02.world.gen.Carves;
import com.forzacode.a1016_02.world.gen.Hash;
import com.forzacode.a1016_02.world.gen.Terrain;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * {@code /a1016 world place <scar>}: builds one scar near the player but out of view, through
 * {@link com.forzacode.a1016_02.core.TraceService}, so it can be seen without exploring. Builds and lights are
 * "left" ({@code leave}); what is in the way is removed. The site is recorded like a generated one.
 */
public final class LivePlacer {
	public static final String CAUSE = "world:place";

	/** What happened. */
	public record Result(boolean ok, String message) {
	}

	/** A scar ready to commit: its blueprint (or area edits) and its site. */
	private record Placement(@Nullable Blueprint blueprint, @Nullable List<NewScarPlacer.Edit> edits, SiteType site, BlockPos sitePos, int size,
			@Nullable BoundingBox interior) {
	}

	private static final String[] NAMES = {"dead_mountain", "bare_forest", "cut", "tunnel", "stair", "abandoned_build", "ruined_hut",
		"panic_tower", "emptied_house", "cross", "lone_light", "lone_light_cave", "lone_light_ocean", "ocean_pyramid"};

	private LivePlacer() {
	}

	public static List<String> names() {
		return List.of(NAMES);
	}

	public static Result place(CommandSourceStack source, String name) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return new Result(false, "place needs a player");
		}
		if (!names().contains(name)) {
			return new Result(false, "unknown scar " + name + "; one of " + String.join(", ", NAMES));
		}
		ServerLevel level = player.level();
		LiveTerrain terrain = new LiveTerrain(level);
		WorldConfig config = WorldConfig.get();
		long seed = Hash.of(level.getGameTime(), name.hashCode());
		int tried = 0;
		int inView = 0;
		for (BlockPos candidate : candidates(player, config)) {
			if (!terrain.loaded(candidate.getX(), candidate.getZ())) {
				continue;
			}
			Placement placement = plan(name, level, terrain, candidate.getX(), candidate.getZ(), Hash.of(seed, tried));
			tried++;
			if (placement == null) {
				continue;
			}
			if (!commit(level, placement, name)) {
				inView++;
				continue;
			}
			WorldSites.record(placement.site(), level.dimension(), placement.sitePos(), placement.size(), placement.interior());
			WorldSites.drain(level.getServer());
			double d = Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(placement.sitePos())));
			A1016_02.LOGGER.info("[a1016] world: placed {} at {}", name, placement.sitePos().toShortString());
			return new Result(true, String.format(Locale.ROOT, "placed %s at %s (%.0f blocks away), site %s", name, placement.sitePos().toShortString(), d,
					placement.site()));
		}
		return new Result(false, "no out-of-view spot for " + name + " (" + tried + " fitting spots, " + inView + " in view); turn around or move");
	}

	/** Spots between the configured distances, behind the player first. */
	private static List<BlockPos> candidates(ServerPlayer player, WorldConfig config) {
		List<BlockPos> spots = new ArrayList<>();
		float yaw = player.getYRot();
		int[] offsets = {180, 150, 210, 120, 240, 90, 270, 60, 300, 30, 330, 0};
		for (int d = config.placeMinDistance; d <= config.placeMaxDistance; d += 8) {
			for (int offset : offsets) {
				double angle = Math.toRadians(yaw + offset);
				int x = Mth.floor(player.getX() - Math.sin(angle) * d);
				int z = Mth.floor(player.getZ() + Math.cos(angle) * d);
				spots.add(new BlockPos(x, 0, z));
			}
		}
		return spots;
	}

	private static boolean commit(ServerLevel level, Placement placement, String name) {
		if (placement.edits() != null) {
			return !placement.edits().isEmpty() && NewScarPlacer.commit(level, placement.edits(), CAUSE + "/" + name);
		}
		TraceBatch batch = Services.traces().batch(level, CAUSE + "/" + name);
		placement.blueprint().queue(level, batch);
		return batch.size() > 0 && batch.commit();
	}

	private static @Nullable Placement plan(String name, ServerLevel level, LiveTerrain terrain, int x, int z, long h) {
		int ground = terrain.ground(x, z);
		boolean wet = terrain.wet(x, z);
		Holder<Biome> biome = terrain.surfaceBiome(x, z);
		Direction facing = Direction.from2DDataValue(Hash.below(h, 4));
		Builds.Wood wood = Builds.Wood.of(biome);
		switch (name) {
			case "dead_mountain", "bare_forest" -> {
				if (wet) {
					return null;
				}
				boolean dead = name.equals("dead_mountain");
				BlockPos center = new BlockPos(x, ground, z);
				List<NewScarPlacer.Edit> edits = dead ? NewScarPlacer.collectDead(level, center, 16, ground - 6, chunk -> true)
						: NewScarPlacer.collectBare(level, center, 16, chunk -> true);
				if (edits.isEmpty() || edits.size() > WorldConfig.get().newScarMaxBlocks) {
					return null;
				}
				BlockPos site = dead ? center.above() : NewScarPlacer.nearestTrunk(level, center, 16);
				return new Placement(null, edits, dead ? SiteType.DEAD_MOUNTAIN : SiteType.BARE_GROVE, site, 16, null);
			}
			case "cut", "tunnel" -> {
				Direction axis = Hash.unit(h ^ 3) < 0.5 ? Direction.EAST : Direction.SOUTH;
				for (Direction a : new Direction[] {axis, axis.getClockWise()}) {
					Carves.Carve carve = name.equals("cut") ? Carves.cut(terrain, x, z, a, 5, 3, 40)
							: Carves.tunnel(terrain, x, z, a, Math.max(terrain.seaLevel() + 2, ground - 8), 60);
					if (carve != null) {
						return new Placement(carve.blueprint(), null, SiteType.CUT, carve.site(), carve.size(), null);
					}
				}
				return null;
			}
			case "stair" -> {
				for (int n = 0; n < 4; n++) {
					Carves.Carve carve = Carves.stair(terrain, x, z, Direction.from2DDataValue((facing.get2DDataValue() + n) % 4), Carves.PathCheck.NO_FLUID);
					if (carve != null) {
						return new Placement(carve.blueprint(), null, SiteType.STAIR_BOTTOM, carve.site(), 1, null);
					}
				}
				return null;
			}
			case "abandoned_build", "ruined_hut", "emptied_house" -> {
				if (wet || !flat(terrain, x, z, name.equals("abandoned_build") ? 3 : 4, ground, 3)) {
					return null;
				}
				Builds.Build build = switch (name) {
					case "abandoned_build" -> Builds.abandonedBuild(x, ground, z, facing, wood, h);
					case "ruined_hut" -> Builds.ruinedHut(x, ground, z, facing, h);
					default -> Builds.emptiedHouse(x, ground, z, facing, wood, h);
				};
				SiteType type = switch (name) {
					case "abandoned_build" -> SiteType.ABANDONED_BUILD;
					case "ruined_hut" -> SiteType.RUINED_HUT;
					default -> SiteType.EMPTIED_HOUSE;
				};
				return new Placement(build.blueprint(), null, type, build.site(), build.size(), build.interior());
			}
			case "panic_tower" -> {
				return wet ? null : of(Builds.panicTower(x, ground, z, h), SiteType.PANIC_TOWER);
			}
			case "cross" -> {
				return wet ? null : of(Builds.cross(x, ground, z, facing, wood, h), SiteType.CROSS);
			}
			case "lone_light" -> {
				return wet ? null : of(Builds.lonelight(Builds.LightKind.GLOWSTONE, x, ground, z), SiteType.LONE_LIGHT);
			}
			case "lone_light_ocean" -> {
				int surface = terrain.surface(x, z);
				return surface - ground < 2 ? null : of(Builds.lonelight(Builds.LightKind.OCEAN_TORCH, x, surface, z), SiteType.LONE_LIGHT);
			}
			case "lone_light_cave" -> {
				BlockPos floor = darkCaveFloor(level, x, z, ground);
				return floor == null ? null : of(Builds.lonelight(Builds.LightKind.CAVE_TORCH, x, floor.getY(), z), SiteType.LONE_LIGHT);
			}
			case "ocean_pyramid" -> {
				int water = terrain.surface(x, z) - ground;
				if (water < 2) {
					return null;
				}
				int size = Math.clamp(water + 1, 3, 6);
				for (int[] c : new int[][] {{-(size - 1), -(size - 1)}, {size - 1, size - 1}, {-(size - 1), size - 1}, {size - 1, -(size - 1)}}) {
					if (!terrain.wet(x + c[0], z + c[1])) {
						return null;
					}
				}
				Builds.Build build = Builds.pyramid(x, ground, z, size);
				return of(build, SiteType.OCEAN_PYRAMID);
			}
			default -> {
				return null;
			}
		}
	}

	private static Placement of(Builds.Build build, SiteType type) {
		return new Placement(build.blueprint(), null, type, build.site(), build.size(), null);
	}

	private static boolean flat(Terrain terrain, int x, int z, int half, int floor, int tolerance) {
		for (int[] c : new int[][] {{-half, -half}, {half, -half}, {-half, half}, {half, half}}) {
			if (terrain.wet(x + c[0], z + c[1]) || Math.abs(terrain.ground(x + c[0], z + c[1]) - floor) > tolerance) {
				return false;
			}
		}
		return true;
	}

	/** A cave floor under (x, z) with two dark air blocks above it, or null. */
	public static @Nullable BlockPos darkCaveFloor(ServerLevel level, int x, int z, int ground) {
		for (int y = ground - 8; y > level.getMinY() + 8; y--) {
			BlockPos feet = new BlockPos(x, y, z);
			if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir() && level.getBlockState(feet.below()).isFaceSturdy(level,
					feet.below(), Direction.UP) && level.getBrightness(LightLayer.BLOCK, feet) == 0 && level.getBrightness(LightLayer.SKY, feet) == 0) {
				return feet.below();
			}
		}
		return null;
	}

	/** For tests and the cards: the kind a debug name builds. */
	public static @Nullable ScarKind kindOf(String name) {
		return ScarKind.byId(name.startsWith("lone_light") ? "lone_light" : name).orElse(null);
	}

	static boolean inLoadedChunk(ServerLevel level, BlockPos pos) {
		return level.hasChunk(ChunkPos.containing(pos).x(), ChunkPos.containing(pos).z());
	}
}
