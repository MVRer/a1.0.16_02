package com.forzacode.a1016_02.world.live;

import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.world.WorldConfig;
import com.forzacode.a1016_02.world.WorldData;
import com.forzacode.a1016_02.world.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * "Emptied house" (MAJOR, Proximity): an abandoned build the player has visited is cleared inside while they are
 * away. Walls, roof and door stay; every block inside goes (the chest with its contents too, kept in the ledger).
 * The build becomes an EMPTIED_HOUSE site, and its ABANDONED_BUILD site is claimed as {@value #CLAIM} so no
 * fragment is put in a chest that is gone.
 */
public final class EmptiedHouseCard implements EventCard {
	public static final String ID = "emptied_house";
	public static final String CAUSE = "world:emptied_house";
	public static final String CLAIM = "world:emptied";

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
		return Set.of(Habit.VISITOR, Habit.COLLECTOR);
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.SCAR, CardTag.ITEM);
	}

	@Override
	public boolean hasFake() {
		return false;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		WorldConfig config = WorldConfig.get();
		return !Services.sites().findUnclaimed(SiteType.ABANDONED_BUILD, GlobalPos.of(world.dimension(), player.blockPosition()),
				config.emptiedHouseSearchBlocks).isEmpty();
	}

	@Override
	public FireResult fire(FireContext ctx) {
		return emptyNearest(ctx.player()).isPresent() ? FireResult.FIRED : FireResult.NO_SPOT;
	}

	/** Empties the nearest visited, unclaimed abandoned build the player is away from. Returns its site. */
	public static Optional<BlockPos> emptyNearest(ServerPlayer player) {
		ServerLevel level = player.level();
		WorldConfig config = WorldConfig.get();
		WorldData data = WorldData.get(level.getServer());
		for (SiteRegistry.Site site : Services.sites().findUnclaimed(SiteType.ABANDONED_BUILD, GlobalPos.of(level.dimension(), player.blockPosition()),
				config.emptiedHouseSearchBlocks)) {
			GlobalPos at = site.globalPos();
			Optional<BoundingBox> interior = data.buildInterior(at);
			if (data.isEmptied(at) || interior.isEmpty()) {
				continue;
			}
			if (Services.watch().lastVisitDay(level, ChunkPos.containing(site.pos())) < 0) {
				continue; // never visited
			}
			double dx = player.getX() - site.pos().getX();
			double dz = player.getZ() - site.pos().getZ();
			if (dx * dx + dz * dz < (double) config.emptiedHouseAwayBlocks * config.emptiedHouseAwayBlocks) {
				continue; // only while the player is away
			}
			int removed = empty(level, interior.get());
			if (removed == 0) {
				data.markEmptied(at); // nothing left inside (the player took it all): never again
				continue;
			}
			if (removed > 0) {
				data.markEmptied(at);
				Services.sites().claim(site, CLAIM);
				BoundingBox box = interior.get();
				BlockPos floor = new BlockPos(box.getCenter().getX(), box.minY(), box.getCenter().getZ());
				WorldSites.record(SiteType.EMPTIED_HOUSE, level.dimension(), floor, site.size(), box);
				A1016_02.LOGGER.info("[a1016] world: emptied the build at {}", site.pos().toShortString());
				return Optional.of(site.pos());
			}
		}
		return Optional.empty();
	}

	/**
	 * Removes every block inside the box as one out-of-view batch.
	 *
	 * @return how many blocks went, 0 if it was already empty, -1 if refused (in view)
	 */
	public static int empty(ServerLevel level, BoundingBox interior) {
		TraceBatch batch = Services.traces().batch(level, CAUSE);
		for (BlockPos pos : BlockPos.betweenClosed(interior.minX(), interior.minY(), interior.minZ(), interior.maxX(), interior.maxY(), interior.maxZ())) {
			if (!level.getBlockState(pos).isAir()) {
				batch.remove(pos.immutable());
			}
		}
		if (batch.size() == 0) {
			return 0;
		}
		return batch.commit() ? batch.size() : -1;
	}
}
