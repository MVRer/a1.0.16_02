package com.forzacode.a1016_02.world.live;

import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.WorldData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * "Light on the mountain" (MINOR, Traces): at night, one torch on a far hilltop that wasn't there, at least
 * {@code Pacing.mountainLightMinDistance} away. It is placed while the player looks the other way, somewhere they
 * will see when they turn. Next day, or once they come closer than that distance, it is gone without a trace
 * (taken out of view, and its ledger entry dropped: it undoes our own torch).
 */
public final class LightOnMountainCard implements EventCard {
	public static final String ID = "light_on_mountain";
	public static final String CAUSE = "world:light_on_mountain";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Tier tier() {
		return Tier.MINOR;
	}

	@Override
	public Stage earliestStage() {
		return Stage.TRACES;
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
		return world.dimension() == Level.OVERWORLD && world.isDarkOutside() && WorldData.get(world.getServer()).light().isEmpty()
				&& world.canSeeSky(BlockPos.containing(player.getEyePosition()));
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerLevel level = ctx.level();
		WorldData data = WorldData.get(level.getServer());
		if (level.dimension() != Level.OVERWORLD || data.light().isPresent()) {
			return FireResult.SKIPPED;
		}
		BlockPos spot = findHilltop(level, ctx.player(), ctx.random());
		if (spot == null || !Services.traces().leave(level, spot, Blocks.TORCH.defaultBlockState(), CAUSE)) {
			return FireResult.NO_SPOT;
		}
		data.setLight(new WorldData.Light(GlobalPos.of(level.dimension(), spot), GameClock.day(level.getServer())));
		A1016_02.LOGGER.info("[a1016] world: light on the mountain at {}", spot.toShortString());
		return FireResult.FIRED;
	}

	/**
	 * A hilltop behind the player (outside the view cone), between the minimum distance and the edge of view, with a
	 * clear line from the player's eyes, so it is there when they turn around.
	 */
	static @Nullable BlockPos findHilltop(ServerLevel level, ServerPlayer player, RandomSource random) {
		int min = ModConfig.pacing().mountainLightMinDistance + 8;
		int max = Math.min(level.getServer().getPlayerList().getViewDistance() * 16 - 16, 240);
		if (max <= min) {
			return null;
		}
		Vec3 eye = player.getEyePosition();
		double halfCone = ModConfig.pacing().viewConeDegrees / 2.0;
		for (int attempt = 0; attempt < 96; attempt++) {
			double rel = halfCone + 15 + random.nextDouble() * (360 - 2 * (halfCone + 15));
			double angle = Math.toRadians(player.getYRot() + rel);
			int d = Mth.nextInt(random, min, max);
			int x = Mth.floor(eye.x - Math.sin(angle) * d);
			int z = Mth.floor(eye.z + Math.cos(angle) * d);
			if (!level.hasChunk(x >> 4, z >> 4)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos spot = new BlockPos(x, y, z);
			if (y < eye.y - 6 || !standsOn(level, spot) || !isHilltop(level, x, y - 1, z) || !visibleFrom(level, eye, spot)) {
				continue;
			}
			return spot;
		}
		return null;
	}

	private static boolean standsOn(ServerLevel level, BlockPos spot) {
		BlockState below = level.getBlockState(spot.below());
		BlockState at = level.getBlockState(spot);
		return (at.isAir() || at.canBeReplaced() && at.getFluidState().isEmpty()) && below.isFaceSturdy(level, spot.below(), Direction.UP)
				&& below.getFluidState().isEmpty() && !below.is(net.minecraft.tags.BlockTags.LEAVES) && Blocks.TORCH.defaultBlockState().canSurvive(level, spot);
	}

	private static boolean isHilltop(ServerLevel level, int x, int y, int z) {
		for (int i = 0; i < 8; i++) {
			double a = Math.PI / 4 * i;
			int nx = x + (int) Math.round(Math.cos(a) * 10);
			int nz = z + (int) Math.round(Math.sin(a) * 10);
			if (!level.hasChunk(nx >> 4, nz >> 4) || level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz) - 1 > y - 2) {
				return false;
			}
		}
		return true;
	}

	/** True if nothing opaque stands between the eyes and the spot. */
	static boolean visibleFrom(ServerLevel level, Vec3 eye, BlockPos spot) {
		Vec3 target = Vec3.atCenterOf(spot);
		Vec3 look = target.subtract(eye).normalize();
		TraceService.Viewer viewer = new TraceService.Viewer(eye, look, new AABB(eye, eye), eye.distanceTo(target) + 4);
		return !TraceService.isOutOfView(level, new AABB(spot), List.of(viewer), 0, 10);
	}

	/** Takes the light away next day, or once the subject is closer than the minimum distance. Server thread. */
	public static void tick(MinecraftServer server) {
		WorldData data = WorldData.get(server);
		WorldData.Light light = data.light().orElse(null);
		if (light == null) {
			return;
		}
		ServerLevel level = server.getLevel(light.pos().dimension());
		if (level == null) {
			data.setLight(null);
			return;
		}
		BlockPos pos = light.pos().pos();
		boolean loaded = level.hasChunkAt(pos);
		boolean nextDay = GameClock.day(server) > light.day() || !level.isDarkOutside();
		boolean close = Services.watch().subject(server).filter(p -> p.level() == level)
				.map(p -> horizontal(p.position(), pos) < ModConfig.pacing().mountainLightMinDistance).orElse(false);
		if (!nextDay && !close) {
			return;
		}
		if (loaded && !level.getBlockState(pos).is(Blocks.TORCH)) {
			data.setLight(null); // someone took it
			return;
		}
		if (Services.traces().remove(level, pos, CAUSE)) {
			TraceLedger ledger = TraceLedger.get(server);
			for (TraceLedger.Entry entry : List.copyOf(ledger.entries())) {
				if (entry.cause().startsWith(CAUSE) && entry.pos().equals(light.pos())) {
					ledger.remove(entry);
				}
			}
			data.setLight(null);
			A1016_02.LOGGER.info("[a1016] world: the light on the mountain is gone");
		}
	}

	private static double horizontal(Vec3 from, BlockPos pos) {
		double dx = pos.getX() + 0.5 - from.x;
		double dz = pos.getZ() + 0.5 - from.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
