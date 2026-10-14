package com.forzacode.a1016_02.world.live;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Attention;
import com.forzacode.a1016_02.core.AttentionTrigger;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.world.Bases;
import com.forzacode.a1016_02.world.WorldConfig;
import com.forzacode.a1016_02.world.WorldData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The attention triggers world owns (D-017), the light on the mountain's clock, and the bases world remembers:
 * <ul>
 * <li>{@code DUG_INTO_PYRAMID}: a player breaks a block of a recorded ocean pyramid (once per pyramid).</li>
 * <li>{@code REPLANTED_GROVE}: anyone places leaves or saplings in a bare grove (at most once per grove per day).</li>
 * <li>{@code LEFT_GROVES_ALONE}: someone stood in a bare grove and nobody replanted it for {@code leftGrovesDays}
 * in-game days (once per grove).</li>
 * </ul>
 * Attention is one value for the world (D-058), so each grove is looked at once a pass however many participants
 * stand near it. Every pass also remembers each participant's base ({@link Bases#rememberAll}). Server thread only.
 */
public final class WorldWatch {
	/** How far from a participant bare groves are watched. */
	private static final int GROVE_SEARCH = 256;
	/** Replanting is looked for this far around the grove's center at most (placedNear is a cube). */
	private static final int GROVE_REACH = 64;

	private WorldWatch() {
	}

	/** Called every server tick. */
	public static void tick(MinecraftServer server) {
		long every = Math.max(20, Math.round(WorldConfig.get().watchSeconds * 20));
		if (server.getTickCount() % 20 == 0) {
			LightOnMountainCard.tick(server);
		}
		if (server.getTickCount() % every == 0) {
			Bases.live(server).rememberAll();
			watchGroves(server);
		}
	}

	/** Called after any player breaks a block. */
	public static void onBroken(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		for (SiteRegistry.Site site : Services.sites().find(SiteType.OCEAN_PYRAMID, GlobalPos.of(level.dimension(), pos), 16)) {
			if (!insidePyramid(site, pos)) {
				continue;
			}
			if (WorldData.get(level.getServer()).markPyramidDug(site.globalPos())) {
				Attention.trigger(level.getServer(), AttentionTrigger.DUG_INTO_PYRAMID);
				A1016_02.LOGGER.debug("[a1016] world: {} dug into the pyramid at {}", player.getName().getString(), site.pos().toShortString());
			}
			return;
		}
	}

	/** True if {@code pos} is one of the pyramid's blocks (layers of half-width size-1 down to 0 above its floor). */
	public static boolean insidePyramid(SiteRegistry.Site site, BlockPos pos) {
		int size = site.size();
		int floorY = site.pos().getY() - 1 - (size - 1) / 2;
		int layer = pos.getY() - floorY - 1;
		if (layer < -1 || layer >= size) {
			return false;
		}
		int half = Math.max(0, size - 1 - Math.max(0, layer));
		return Math.abs(pos.getX() - site.pos().getX()) <= half && Math.abs(pos.getZ() - site.pos().getZ()) <= half;
	}

	private static void watchGroves(MinecraftServer server) {
		WorldData data = WorldData.get(server);
		long today = GameClock.day(server);
		int leftDays = WorldConfig.get().leftGrovesDays;
		Set<GlobalPos> looked = new HashSet<>();
		for (ServerPlayer player : Services.watch().participants(server)) {
			ServerLevel level = player.level();
			List<SiteRegistry.Site> groves = Services.sites().find(SiteType.BARE_GROVE, GlobalPos.of(level.dimension(), player.blockPosition()), GROVE_SEARCH);
			for (SiteRegistry.Site site : groves) {
				if (!looked.add(site.globalPos())) {
					continue; // a nearer participant already looked at it this pass
				}
				int reach = Math.min(site.size(), GROVE_REACH);
				double dist = nearest(server, level, site.pos());
				Optional<WorldData.Grove> known = data.grove(site.globalPos());
				if (known.isEmpty()) {
					if (dist <= site.size()) {
						data.putGrove(new WorldData.Grove(site.globalPos(), today, replanting(level, site.pos(), reach), -1, false));
					}
					continue;
				}
				if (dist > site.size() + 32) {
					continue;
				}
				WorldData.Grove grove = known.get();
				int count = replanting(level, site.pos(), reach);
				if (count > grove.replanted()) {
					if (grove.lastReplantDay() != today) {
						Attention.trigger(server, AttentionTrigger.REPLANTED_GROVE);
						A1016_02.LOGGER.debug("[a1016] world: replanted the bare grove at {}", site.pos().toShortString());
					}
					data.putGrove(new WorldData.Grove(grove.site(), grove.firstVisitDay(), count, today, grove.leftAlone()));
				} else if (count < grove.replanted()) {
					data.putGrove(new WorldData.Grove(grove.site(), grove.firstVisitDay(), count, grove.lastReplantDay(), grove.leftAlone()));
				}
			}
		}
		for (WorldData.Grove grove : data.groves()) {
			if (!grove.leftAlone() && grove.lastReplantDay() < 0 && today - grove.firstVisitDay() >= leftDays) {
				Attention.trigger(server, AttentionTrigger.LEFT_GROVES_ALONE);
				data.putGrove(new WorldData.Grove(grove.site(), grove.firstVisitDay(), grove.replanted(), grove.lastReplantDay(), true));
				A1016_02.LOGGER.debug("[a1016] world: the bare grove at {} was left alone", grove.site().pos().toShortString());
			}
		}
	}

	/** The horizontal distance from {@code pos} to the nearest participant in this level. */
	private static double nearest(MinecraftServer server, ServerLevel level, BlockPos pos) {
		double best = Double.MAX_VALUE;
		for (ServerPlayer player : Services.watch().participants(server)) {
			if (player.level() == level) {
				double dx = player.getX() - pos.getX();
				double dz = player.getZ() - pos.getZ();
				best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
			}
		}
		return best;
	}

	/** Player-placed leaves and saplings still standing around the grove's center. */
	static int replanting(ServerLevel level, BlockPos center, int reach) {
		return Services.watch().placedNear(level, center, reach, state -> state.is(BlockTags.LEAVES) || state.is(BlockTags.SAPLINGS)).size();
	}
}
