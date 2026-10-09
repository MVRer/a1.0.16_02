package com.forzacode.a1016_02.ending.d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Groves grow back once the pass has ended. Leaves he took are in the ledger and come back through the undo (each
 * exactly once). Old bare groves were made bare when their chunks generated, so nothing in the ledger holds their
 * leaves: those trunks get a crown again (red and orange poplar leaves for poplars, each tree's own leaves otherwise),
 * placed as the world healing ({@value #CAUSE}, never ledgered), out of view, one tree per batch.
 */
public final class Regrow {
	public static final String CAUSE = "ending:d/regrow";

	/** One bare tree and the crown it gets back. */
	public record Crown(BlockPos top, Map<BlockPos, BlockState> leaves) {
	}

	private Regrow() {
	}

	/** True if this ledger entry is a leaf he took (and may come back through the undo). */
	static boolean isLeafEntry(MinecraftServer server, TraceLedger.Entry entry) {
		return entry.kind() == TraceLedger.Kind.REMOVE && entry.state().map(s -> s.is(BlockTags.LEAVES)).orElse(false)
				&& Undo.skipReason(server, entry).isEmpty();
	}

	/** Leaves he took within {@code radius} (horizontal) of {@code center}, still in the ledger. */
	static List<TraceLedger.Entry> ledgerLeaves(ServerLevel level, BlockPos center, int radius) {
		MinecraftServer server = level.getServer();
		List<TraceLedger.Entry> found = new ArrayList<>();
		for (TraceLedger.Entry entry : TraceLedger.get(server).entries()) {
			BlockPos pos = entry.pos().pos();
			if (entry.pos().dimension().equals(level.dimension()) && horizontal(pos, center) <= (double) radius * radius && isLeafEntry(server, entry)) {
				found.add(entry);
			}
		}
		return found;
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/**
	 * The bare trunks of a grove (loaded columns only) and their crowns: a column whose top is a log with no leaves
	 * within two blocks, and no leaf of his waiting in the ledger nearby (the undo brings those back exactly).
	 */
	public static List<Crown> crowns(ServerLevel level, BlockPos center, int radius) {
		List<TraceLedger.Entry> waiting = ledgerLeaves(level, center, radius + 4);
		List<Crown> crowns = new ArrayList<>();
		for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
				if (horizontal(new BlockPos(x, 0, z), center) > (double) radius * radius || level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				BlockPos top = new BlockPos(x, y, z);
				BlockState log = level.getBlockState(top);
				if (!log.is(BlockTags.LOGS) || !level.getBlockState(top.below()).is(BlockTags.LOGS) && !level.getBlockState(top.below(2)).is(BlockTags.LOGS)) {
					continue;
				}
				if (leavesNear(level, top, 2) || waiting.stream().anyMatch(e -> e.pos().pos().distManhattan(top) <= 4)) {
					continue;
				}
				Map<BlockPos, BlockState> leaves = crown(level, top, leafFor(log));
				if (!leaves.isEmpty()) {
					crowns.add(new Crown(top, leaves));
				}
			}
		}
		return crowns;
	}

	static boolean leavesNear(ServerLevel level, BlockPos top, int r) {
		for (BlockPos pos : BlockPos.betweenClosed(top.offset(-r, -r, -r), top.offset(r, r, r))) {
			if (level.getBlockState(pos).is(BlockTags.LEAVES)) {
				return true;
			}
		}
		return false;
	}

	/** The leaves a trunk grows: poplars red and orange (alternating), others their own kind. */
	static Block leafFor(BlockState log) {
		if (log.is(Blocks.POPLAR_LOG) || log.is(Blocks.POPLAR_WOOD)) {
			return Blocks.RED_POPLAR_LEAVES;
		}
		if (log.is(Blocks.BIRCH_LOG)) {
			return Blocks.BIRCH_LEAVES;
		}
		if (log.is(Blocks.SPRUCE_LOG)) {
			return Blocks.SPRUCE_LEAVES;
		}
		if (log.is(Blocks.DARK_OAK_LOG)) {
			return Blocks.DARK_OAK_LEAVES;
		}
		if (log.is(Blocks.ACACIA_LOG)) {
			return Blocks.ACACIA_LEAVES;
		}
		if (log.is(Blocks.JUNGLE_LOG)) {
			return Blocks.JUNGLE_LEAVES;
		}
		if (log.is(Blocks.CHERRY_LOG)) {
			return Blocks.CHERRY_LEAVES;
		}
		if (log.is(Blocks.MANGROVE_LOG)) {
			return Blocks.MANGROVE_LEAVES;
		}
		if (log.is(Blocks.PALE_OAK_LOG)) {
			return Blocks.PALE_OAK_LEAVES;
		}
		return Blocks.OAK_LEAVES;
	}

	/**
	 * A small round crown on a trunk's top: two layers of radius 2 around the top log and below it, two of radius 1
	 * above. Only into air. Each leaf's distance is how far it is from the trunk, as the game would work it out.
	 */
	static Map<BlockPos, BlockState> crown(ServerLevel level, BlockPos top, Block leaf) {
		Map<BlockPos, Integer> cells = new LinkedHashMap<>();
		for (int dy = -1; dy <= 2; dy++) {
			int r = dy <= 0 ? 2 : 1;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) == r && Math.abs(dz) == r && r > 1 || dx == 0 && dz == 0 && dy <= 0) {
						continue;
					}
					BlockPos pos = top.offset(dx, dy, dz);
					if (level.getBlockState(pos).isAir()) {
						cells.put(pos, 7);
					}
				}
			}
		}
		// Distances by breadth-first search from the logs, through the new leaves.
		Deque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos pos : cells.keySet()) {
			for (Direction dir : Direction.values()) {
				if (level.getBlockState(pos.relative(dir)).is(BlockTags.LOGS)) {
					cells.put(pos, 1);
					queue.add(pos);
					break;
				}
			}
		}
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			int d = cells.get(pos);
			for (Direction dir : Direction.values()) {
				BlockPos n = pos.relative(dir);
				Integer nd = cells.get(n);
				if (nd != null && nd > d + 1) {
					cells.put(n, d + 1);
					queue.add(n);
				}
			}
		}
		Map<BlockPos, BlockState> leaves = new LinkedHashMap<>();
		cells.forEach((pos, d) -> {
			if (d < 7) {
				Block block = leaf == Blocks.RED_POPLAR_LEAVES && Math.floorMod(pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 7, 2) == 1
						? Blocks.ORANGE_POPLAR_LEAVES : leaf;
				BlockState state = block.defaultBlockState();
				if (state.hasProperty(LeavesBlock.DISTANCE)) {
					state = state.setValue(LeavesBlock.DISTANCE, d);
				}
				leaves.put(pos, state);
			}
		});
		return leaves;
	}

	/** Grows one crown back, all or nothing, out of view. */
	public static boolean grow(ServerLevel level, Crown crown, TraceService traces) {
		TraceBatch batch = traces.batch(level, CAUSE);
		crown.leaves().forEach(batch::leave);
		return batch.commit();
	}

	/** The bare groves the world recorded, nearest {@code near} first. */
	static List<SiteRegistry.Site> groves(ServerLevel level, BlockPos near) {
		List<SiteRegistry.Site> sites = new ArrayList<>();
		for (SiteRegistry.Site site : Services.sites().all()) {
			if (site.type() == com.forzacode.a1016_02.core.SiteType.BARE_GROVE && site.dimension().equals(level.dimension())) {
				sites.add(site);
			}
		}
		sites.sort(Comparator.comparingDouble(s -> horizontal(s.pos(), near)));
		return sites;
	}

	/** Cells already placed per crown (tests). */
	static Map<BlockPos, BlockState> merged(List<Crown> crowns) {
		Map<BlockPos, BlockState> all = new HashMap<>();
		crowns.forEach(c -> all.putAll(c.leaves()));
		return all;
	}
}
