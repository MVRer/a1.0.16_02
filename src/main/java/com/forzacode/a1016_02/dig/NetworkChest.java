package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jspecify.annotations.Nullable;

/**
 * Brings a real chest into the network's dead end. He never makes blocks, so it is moved with
 * {@code TraceService.move} from far away: first a chest at an unclaimed ABANDONED_BUILD or RUINED_HUT site,
 * otherwise a single chest nobody placed (a structure chest) in a loaded chunk far from the player and the base.
 */
public final class NetworkChest {
	private static final int MAX_SITES_PER_TRY = 2;

	private NetworkChest() {
	}

	/** Tries once to move a chest into the dead end. True if the network now has its chest. */
	public static boolean tryFetch(NetworkGrower.Ctx ctx) {
		Network net = ctx.net();
		if (net.chest != null) {
			return true;
		}
		ServerLevel level = ctx.level();
		if (net.alcove == null || !level.isLoaded(net.alcove) || !level.getBlockState(net.alcove).isAir()) {
			return false;
		}
		for (BlockPos source : sources(ctx)) {
			if (ctx.traces().move(level, source, net.alcove, NetworkGrower.CAUSE_CHEST)) {
				net.chest = net.alcove;
				A1016_02.LOGGER.debug("[a1016] dig: moved the chest at {} into the network at {}", source, net.alcove);
				return true;
			}
		}
		return false;
	}

	/** Candidate chests, best first. */
	static List<BlockPos> sources(NetworkGrower.Ctx ctx) {
		ServerLevel level = ctx.level();
		Network net = ctx.net();
		int minDist = ctx.config().chestSourceMinDistance;
		List<BlockPos> found = new ArrayList<>();
		GlobalPos near = GlobalPos.of(net.dimension, net.base);
		List<SiteRegistry.Site> sites = new ArrayList<>();
		sites.addAll(Services.sites().findUnclaimed(SiteType.ABANDONED_BUILD, near, ctx.config().chestSourceSiteRadius));
		sites.addAll(Services.sites().findUnclaimed(SiteType.RUINED_HUT, near, ctx.config().chestSourceSiteRadius));
		int tried = 0;
		for (SiteRegistry.Site site : sites) {
			if (tried >= MAX_SITES_PER_TRY) {
				break;
			}
			if (!farEnough(ctx, site.pos(), minDist)) {
				continue;
			}
			tried++;
			int r = Math.min(16, Math.max(4, site.size()));
			for (int cx = (site.pos().getX() - r) >> 4; cx <= (site.pos().getX() + r) >> 4; cx++) {
				for (int cz = (site.pos().getZ() - r) >> 4; cz <= (site.pos().getZ() + r) >> 4; cz++) {
					// Loads the site's chunk if it is not loaded: it is far away and only read here.
					collect(ctx, level.getChunk(cx, cz), site.pos(), r, minDist, found);
				}
			}
		}
		BlockPos center = ctx.playerPos() != null ? ctx.playerPos() : net.base;
		int maxR = level.getServer().getPlayerList().getViewDistance();
		int minR = Math.max(1, minDist >> 4);
		for (int r = minR; r <= maxR && found.size() < 8; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					LevelChunk chunk = level.getChunkSource().getChunkNow((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz);
					if (chunk != null) {
						collect(ctx, chunk, null, 0, minDist, found);
					}
				}
			}
		}
		return found;
	}

	private static void collect(NetworkGrower.Ctx ctx, LevelChunk chunk, @Nullable BlockPos around, int radius, int minDist, List<BlockPos> found) {
		PlayerWatch watch = Services.watch();
		for (BlockEntity entity : chunk.getBlockEntities().values()) {
			if (!(entity instanceof ChestBlockEntity)) {
				continue;
			}
			BlockPos pos = entity.getBlockPos();
			BlockState state = entity.getBlockState();
			if (!state.is(Blocks.CHEST) || state.getValue(ChestBlock.TYPE) != ChestType.SINGLE || watch.wasPlacedByPlayer(ctx.level(), pos)) {
				continue;
			}
			if (around != null && Tunnels.cheb(pos, around) > radius) {
				continue;
			}
			if (farEnough(ctx, pos, minDist) && !found.contains(pos)) {
				found.add(pos.immutable());
			}
		}
	}

	private static boolean farEnough(NetworkGrower.Ctx ctx, BlockPos pos, int minDist) {
		long min = (long) minDist * minDist;
		if (ctx.playerPos() != null && pos.distSqr(ctx.playerPos()) < min) {
			return false;
		}
		return pos.distSqr(ctx.net().base) >= min;
	}
}
