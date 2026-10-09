package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.TraceService;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/** One-off 2x2 tunnels for cards and the debug command: a lone tunnel through stone, and the tunnel into a mine. */
public final class CardTunnels {
	public static final String CAUSE_PLAIN = "dig:tunnel";
	public static final String CAUSE_INTO_MINE = "dig:tunnel_into_mine";

	/** A carved card tunnel and its TUNNEL_END site. */
	public record Carved(List<BlockPos> anchors, Direction dir, BlockPos opening, int siteId) {
		public BlockPos end() {
			return anchors.getLast();
		}
	}

	private CardTunnels() {
	}

	/**
	 * Plans a tunnel from {@code start}: straight runs with rare turns, each anchor checked against {@code rules}.
	 * Returns the valid prefix (empty if {@code start} itself fails).
	 */
	static List<BlockPos> plan(ServerLevel level, BlockPos start, Direction dir, int length, Tunnels.Rules rules, DigConfig config,
			RandomSource random, boolean turns) {
		List<BlockPos> anchors = new ArrayList<>();
		LongOpenHashSet existing = new LongOpenHashSet();
		BlockPos current = start;
		Direction heading = dir;
		int run = run(config, random);
		for (int i = 0; i < length; i++) {
			BlockPos next = i == 0 ? start : current.relative(heading);
			if (i > 0 && turns && run <= 0 && random.nextBoolean()) {
				Direction turn = random.nextBoolean() ? heading.getClockWise() : heading.getCounterClockWise();
				if (Tunnels.check(level, current.relative(turn), existing, rules) == null) {
					heading = turn;
					next = current.relative(turn);
					run = run(config, random);
				}
			}
			if (Tunnels.check(level, next, existing, rules) != null) {
				break;
			}
			anchors.add(next);
			existing.addAll(Tunnels.cells(List.of(next)));
			current = next;
			run--;
		}
		return anchors;
	}

	/** A lone 2x2 tunnel through stone 20 to 40 blocks from {@code near}, below it, carved out of view. */
	public static @Nullable Carved plain(ServerLevel level, BlockPos near, DigConfig config, int clearance, RandomSource random, TraceService traces) {
		Tunnels.Rules rules = new Tunnels.Rules(clearance, 0, false, false, pos -> false, null);
		for (int tries = 0; tries < 48; tries++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double r = 20 + random.nextDouble() * 20;
			BlockPos start = new BlockPos(near.getX() + (int) Math.round(Math.cos(angle) * r), near.getY() - 6 - random.nextInt(12),
					near.getZ() + (int) Math.round(Math.sin(angle) * r));
			Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			List<BlockPos> anchors = plan(level, start, dir, config.demoTunnelLength, rules, config, random, true);
			if (anchors.size() < Math.max(4, config.demoTunnelLength / 2)) {
				continue;
			}
			if (Tunnels.carve(level, traces, anchors, new LongOpenHashSet(), CAUSE_PLAIN)) {
				int site = Services.sites().record(SiteType.TUNNEL_END, level.dimension(), anchors.getLast(), anchors.size()).id();
				return new Carved(anchors, dir, start, site);
			}
		}
		return null;
	}

	/**
	 * "Tunnel into your mine": finds a 2-high tunnel the player dug, at least {@code intoMineMinDistance} from
	 * {@code playerPos}, and bores a 2x2 tunnel through its wall that leads straight away and ends in stone. Only the
	 * breakthrough touches the player's space; the rest keeps the card clearance and never opens into a cave.
	 */
	public static @Nullable Carved intoMine(ServerLevel level, BlockPos playerPos, DigConfig config, int clearance, RandomSource random,
			TraceService traces) {
		PlayerWatch watch = Services.watch();
		List<BlockPos> dug = new ArrayList<>();
		long minSqr = (long) config.intoMineMinDistance * config.intoMineMinDistance;
		for (BlockPos pos : watch.dugNear(level, playerPos, 128)) {
			if (pos.distSqr(playerPos) >= minSqr && level.isLoaded(pos) && watch.wasDugByPlayer(level, pos.above())) {
				dug.add(pos);
			}
		}
		NetworkGrower.shuffle(dug, random);
		int tried = 0;
		for (BlockPos floor : dug) {
			if (tried++ > 200) {
				break;
			}
			if (!isAirPair(level, floor)) {
				continue;
			}
			List<Direction> dirs = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
			NetworkGrower.shuffle(dirs, random);
			for (Direction dir : dirs) {
				Carved carved = tryBreakThrough(level, floor, dir, config, clearance, random, traces);
				if (carved != null) {
					return carved;
				}
			}
		}
		return null;
	}

	/** Breaks through the wall at {@code floor + dir}, if the mine runs sideways there and solid stone lies beyond. */
	static @Nullable Carved tryBreakThrough(ServerLevel level, BlockPos floor, Direction dir, DigConfig config, int clearance, RandomSource random,
			TraceService traces) {
		BlockPos wall = floor.relative(dir);
		if (!wallBlock(level, wall) || !wallBlock(level, wall.above())) {
			return null;
		}
		for (Direction side : List.of(dir.getClockWise(), dir.getCounterClockWise())) {
			BlockPos sideFloor = floor.relative(side);
			if (!isAirPair(level, sideFloor) || !wallBlock(level, wall.relative(side)) || !wallBlock(level, wall.relative(side).above())) {
				continue;
			}
			// The cube covering {wall, wall+side} x {y, y+1} x {wall, wall+dir}.
			BlockPos a = wall;
			BlockPos b = wall.relative(side).relative(dir).above();
			BlockPos anchor = new BlockPos(Math.min(a.getX(), b.getX()), floor.getY(), Math.min(a.getZ(), b.getZ()));
			int along = dir.getStepX() * floor.getX() + dir.getStepZ() * floor.getZ();
			// The player's side of the wall plane is the space this card is allowed to open into.
			Predicate<BlockPos> playerSide = pos -> dir.getStepX() * pos.getX() + dir.getStepZ() * pos.getZ() <= along;
			Tunnels.Rules rules = new Tunnels.Rules(clearance, 0, true, false, playerSide, null);
			int length = config.intoMineLengthMin + random.nextInt(Math.max(1, config.intoMineLengthMax - config.intoMineLengthMin + 1));
			List<BlockPos> anchors = plan(level, anchor, dir, length, rules, config, random, false);
			if (anchors.size() < Math.min(8, config.intoMineLengthMin)) {
				continue;
			}
			if (Tunnels.carve(level, traces, anchors, new LongOpenHashSet(), CAUSE_INTO_MINE)) {
				int site = Services.sites().record(SiteType.TUNNEL_END, level.dimension(), anchors.getLast(), anchors.size()).id();
				return new Carved(anchors, dir, wall, site);
			}
			return null;
		}
		return null;
	}

	private static boolean isAirPair(ServerLevel level, BlockPos floor) {
		return level.isLoaded(floor) && level.getBlockState(floor).isAir() && level.getBlockState(floor.above()).isAir()
				&& Services.watch().wasDugByPlayer(level, floor);
	}

	private static boolean wallBlock(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return Tunnels.carvable(state) && !Services.watch().wasPlacedByPlayer(level, pos);
	}

	private static int run(DigConfig config, RandomSource random) {
		return config.tunnelRunMin + random.nextInt(Math.max(1, config.tunnelRunMax - config.tunnelRunMin + 1));
	}
}
