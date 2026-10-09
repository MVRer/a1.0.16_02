package com.forzacode.a1016_02.world.sig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.SignatureData;
import com.forzacode.a1016_02.world.WorldConfig;
import com.forzacode.a1016_02.world.WorldData;
import com.forzacode.a1016_02.world.live.NewScarPlacer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * The lone redstone torch (D-033): one redstone torch "left by others" deep in a cave the subject explored and has
 * left (no visit for {@code loneTorchAwayDays} in-game days), or at the dead end of a recorded TUNNEL_END. Never
 * within {@code loneTorchBaseRadius} of the base, at most {@code loneTorchMax} per world, at least
 * {@code loneTorchMinDays} in-game days apart (debug included). The caves are the spots the subject stood in
 * underground ({@link #trackCaves}). Server thread only.
 */
public final class LoneTorch {
	public static final String ID = "lone_redstone_torch";
	public static final String CAUSE = "world:lone_redstone_torch";
	/** Cave spots closer than this are one spot. */
	private static final int CAVE_MERGE = 12;
	/** Tunnel ends this far from the subject are considered. */
	private static final int TUNNEL_SEARCH = 512;
	/** A torch at a tunnel end stands this close to its site. */
	private static final int TUNNEL_REACH = 2;

	/** Where a torch may go: a remembered cave spot or a tunnel end. */
	public record Candidate(BlockPos pos, int reach, boolean tunnel) {
	}

	private LoneTorch() {
	}

	// --- caps (D-033) ---

	/** Why no torch may be placed today, or null: three already, or the last one is less than seven days old. */
	public static @Nullable String capRefusal(List<SignatureData.Spot> torches, long today, WorldConfig config) {
		if (torches.size() >= config.loneTorchMax) {
			return torches.size() + " lone redstone torches already (at most " + config.loneTorchMax + " per world)";
		}
		if (!torches.isEmpty() && today - torches.getLast().day() < config.loneTorchMinDays) {
			return "the last lone redstone torch is from day " + torches.getLast().day() + " (at least " + config.loneTorchMinDays + " days apart)";
		}
		return null;
	}

	// --- where ---

	/**
	 * The candidates now: cave spots the subject stood in at least {@code loneTorchAwayDays} ago in chunks nobody
	 * visited since, and tunnel ends in chunks not visited for as long (or never); none within the base radius.
	 * Shuffled.
	 */
	public static List<Candidate> candidates(ServerLevel level, List<SignatureData.Spot> caves, List<SiteRegistry.Site> tunnelEnds, @Nullable BlockPos base,
			long today, WorldConfig config, NewScarPlacer.VisitLookup visits, RandomSource random) {
		List<Candidate> found = new ArrayList<>();
		for (SignatureData.Spot spot : caves) {
			BlockPos pos = spot.pos().pos();
			long visit = visits.lastVisitDay(level, ChunkPos.containing(pos));
			if (!spot.pos().dimension().equals(level.dimension()) || today - spot.day() < config.loneTorchAwayDays
					|| visit >= 0 && today - visit < config.loneTorchAwayDays || nearBase(pos, base, config)) {
				continue;
			}
			found.add(new Candidate(pos, config.loneTorchSearch, false));
		}
		for (SiteRegistry.Site site : tunnelEnds) {
			long visit = visits.lastVisitDay(level, ChunkPos.containing(site.pos()));
			if (!site.dimension().equals(level.dimension()) || visit >= 0 && today - visit < config.loneTorchAwayDays || nearBase(site.pos(), base, config)) {
				continue;
			}
			found.add(new Candidate(site.pos(), TUNNEL_REACH, true));
		}
		Util.shuffle(found, random);
		return found;
	}

	static boolean nearBase(BlockPos pos, @Nullable BlockPos base, WorldConfig config) {
		if (base == null) {
			return false;
		}
		double dx = pos.getX() - base.getX();
		double dz = pos.getZ() - base.getZ();
		return dx * dx + dz * dz < (double) config.loneTorchBaseRadius * config.loneTorchBaseRadius;
	}

	/**
	 * Leaves one redstone torch on dark cave floor within {@code reach} of {@code near} (no block or sky light, at
	 * least {@code loneTorchMinDepth} under the surface, on natural ground, outside the base radius), out of view.
	 * Returns where, or null.
	 */
	public static @Nullable BlockPos placeNear(ServerLevel level, BlockPos near, int reach, @Nullable BlockPos base, WorldConfig config,
			TraceService traces) {
		BlockState torch = Blocks.REDSTONE_TORCH.defaultBlockState();
		List<BlockPos> spots = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(near.offset(-reach, -Math.min(reach, 4), -reach), near.offset(reach, Math.min(reach, 4), reach))) {
			spots.add(pos.immutable());
		}
		spots.sort((a, b) -> Double.compare(a.distSqr(near), b.distSqr(near)));
		for (BlockPos pos : spots) {
			if (!level.isLoaded(pos) || !darkFloor(level, pos, config) || nearBase(pos, base, config) || !torch.canSurvive(level, pos)) {
				continue;
			}
			if (traces.leave(level, pos, torch, CAUSE)) {
				return pos;
			}
		}
		return null;
	}

	/** An empty cell on natural sturdy ground, without block light or sky light, deep enough under the surface. */
	static boolean darkFloor(ServerLevel level, BlockPos pos, WorldConfig config) {
		BlockState at = level.getBlockState(pos);
		BlockPos below = pos.below();
		BlockState ground = level.getBlockState(below);
		return at.isAir() && ground.isFaceSturdy(level, below, Direction.UP) && ground.getFluidState().isEmpty()
				&& !Services.watch().wasPlacedByPlayer(level, below) && level.getBrightness(LightLayer.BLOCK, pos) == 0
				&& level.getBrightness(LightLayer.SKY, pos) == 0
				&& level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - pos.getY() >= config.loneTorchMinDepth;
	}

	// --- live ---

	/**
	 * The card's work: within the caps, places the torch at a loaded candidate. If only unloaded candidates exist,
	 * one is asked to load and this returns empty (the director keeps the card; next time it is there).
	 */
	public static Optional<BlockPos> place(ServerPlayer player, RandomSource random) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = player.level();
		WorldConfig config = WorldConfig.get();
		SignatureData data = WorldData.get(server).signatures();
		long today = GameClock.day(server);
		if (capRefusal(data.torches(), today, config) != null || level.dimension() != Level.OVERWORLD) {
			return Optional.empty();
		}
		BlockPos base = Services.watch().base(player).filter(b -> b.dimension().equals(level.dimension())).map(GlobalPos::pos).orElse(null);
		List<SiteRegistry.Site> tunnels = Services.sites().find(SiteType.TUNNEL_END, GlobalPos.of(level.dimension(), player.blockPosition()), TUNNEL_SEARCH);
		List<Candidate> candidates = candidates(level, data.caveSpots(), tunnels, base, today, config, Services.watch()::lastVisitDay, random);
		Candidate unloaded = null;
		for (Candidate c : candidates) {
			List<ChunkPos> chunks = ChunkLoads.around(new BoundingBox(c.pos()).inflatedBy(c.reach()), 0);
			if (!ChunkLoads.ready(level, chunks)) {
				unloaded = unloaded == null ? c : unloaded;
				continue;
			}
			BlockPos placed = placeNear(level, c.pos(), c.reach(), base, config, Services.traces());
			if (placed != null) {
				data.addTorch(new SignatureData.Spot(GlobalPos.of(level.dimension(), placed), today));
				SiteSink.LIVE.record(SiteType.LONE_LIGHT, level.dimension(), placed, 1);
				A1016_02.LOGGER.info("[a1016] world: lone redstone torch at {} ({})", placed.toShortString(), c.tunnel() ? "tunnel end" : "cave");
				return Optional.of(placed);
			}
		}
		if (unloaded != null) {
			ChunkLoads.request(level, ChunkLoads.around(new BoundingBox(unloaded.pos())
					.inflatedBy(unloaded.reach()), 0));
		}
		return Optional.empty();
	}

	/** Called every few seconds: remembers where the subject stands deep underground (the caves they explore). */
	public static void trackCaves(MinecraftServer server) {
		Optional<ServerPlayer> subject = Services.watch().subject(server);
		if (subject.isEmpty() || subject.get().level().dimension() != Level.OVERWORLD) {
			return;
		}
		ServerPlayer player = subject.get();
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		WorldConfig config = WorldConfig.get();
		if (level.getBrightness(LightLayer.SKY, feet) > 0
				|| level.getHeight(Heightmap.Types.WORLD_SURFACE, feet.getX(), feet.getZ()) - feet.getY() < config.loneTorchMinDepth) {
			return;
		}
		WorldData.get(server).signatures().visitCave(GlobalPos.of(level.dimension(), feet), GameClock.day(server), CAVE_MERGE, config.caveSpotsMax);
	}

	/** Debug: one line about the torches. */
	public static String status(MinecraftServer server) {
		SignatureData data = WorldData.get(server).signatures();
		StringBuilder line = new StringBuilder("lone redstone torches: " + data.torches().size() + " of " + WorldConfig.get().loneTorchMax);
		for (SignatureData.Spot torch : data.torches()) {
			line.append(", ").append(torch.pos().pos().toShortString()).append(" (day ").append(torch.day()).append(')');
		}
		line.append("; cave spots remembered: ").append(data.caveSpots().size());
		return line.toString();
	}
}
