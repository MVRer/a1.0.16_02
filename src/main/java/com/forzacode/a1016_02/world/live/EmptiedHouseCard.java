package com.forzacode.a1016_02.world.live;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.world.Bases;
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
 * "Emptied house" (MAJOR, Proximity): an abandoned build anyone has visited is cleared inside while every
 * participant is away from it, unless someone lives in it: a known player's base nearby (an offline friend's too) or
 * their bed or chest inside. Walls, roof and door stay; every block inside goes (the chest with its contents too,
 * kept in the ledger). The build becomes an EMPTIED_HOUSE site, and its ABANDONED_BUILD site is claimed as
 * {@value #CLAIM} so no fragment is put in a chest that is gone.
 */
public final class EmptiedHouseCard implements EventCard {
	public static final String ID = "emptied_house";
	public static final String CAUSE = "world:emptied_house";
	public static final String CLAIM = "world:emptied";
	/** A known base this close to a build's inside (its walls, its doorstep) is someone living in it (blocks). */
	static final int LIVED_IN_MARGIN = 3;

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

	/** Empties the nearest visited, unclaimed abandoned build every participant is away from. Returns its site. */
	public static Optional<BlockPos> emptyNearest(ServerPlayer player) {
		ServerLevel level = player.level();
		List<SiteRegistry.Site> builds = Services.sites().findUnclaimed(SiteType.ABANDONED_BUILD, GlobalPos.of(level.dimension(), player.blockPosition()),
				WorldConfig.get().emptiedHouseSearchBlocks);
		Bases bases = Bases.live(level.getServer());
		return emptyNearest(bases, player, builds, bases.watch()::lastVisitDay, (site, box) -> {
			Services.sites().claim(site, CLAIM);
			BlockPos floor = new BlockPos(box.getCenter().getX(), box.minY(), box.getCenter().getZ());
			WorldSites.record(SiteType.EMPTIED_HOUSE, level.dimension(), floor, site.size(), box);
		});
	}

	/**
	 * The card's work for {@code player}: empties the first of {@code builds} (unclaimed abandoned builds, nearest
	 * first) that someone visited, every participant is away from and nobody lives in: no known player's base in it
	 * ({@link #livedIn}, an offline friend's too) and nothing players placed inside that keeps something
	 * ({@link #keptByPlayers}). {@code emptied} then claims it and records the emptied house (with its inside). Returns
	 * its site.
	 */
	public static Optional<BlockPos> emptyNearest(Bases bases, ServerPlayer player, List<SiteRegistry.Site> builds, NewScarPlacer.VisitLookup visits,
			BiConsumer<SiteRegistry.Site, BoundingBox> emptied) {
		ServerLevel level = player.level();
		WorldConfig config = WorldConfig.get();
		WorldData data = bases.data();
		List<BlockPos> everyone = bases.positionsIn(level);
		List<BlockPos> homes = bases.all(level.dimension());
		for (SiteRegistry.Site site : builds) {
			GlobalPos at = site.globalPos();
			Optional<BoundingBox> interior = data.buildInterior(at);
			if (data.isEmptied(at) || interior.isEmpty()) {
				continue;
			}
			if (visits.lastVisitDay(level, ChunkPos.containing(site.pos())) < 0) {
				continue; // never visited
			}
			if (Bases.near(everyone, site.pos(), config.emptiedHouseAwayBlocks)) {
				continue; // only while everyone is away (the player and any friend)
			}
			if (livedIn(homes, interior.get()) || keptByPlayers(level, bases.watch(), interior.get())) {
				continue; // someone lives here, or keeps things here (an offline friend too): not abandoned
			}
			int removed = empty(level, interior.get());
			if (removed == 0) {
				data.markEmptied(at); // nothing left inside (the player took it all): never again
				continue;
			}
			if (removed > 0) {
				data.markEmptied(at);
				emptied.accept(site, interior.get());
				A1016_02.LOGGER.info("[a1016] world: emptied the build at {}", site.pos().toShortString());
				return Optional.of(site.pos());
			}
		}
		return Optional.empty();
	}

	/**
	 * True if one of {@code homes} (every known player's base) is in the build: inside, in its walls or on its doorstep
	 * ({@value #LIVED_IN_MARGIN} blocks around the inside). A base farther away does not stop it: a build near someone's
	 * home is still emptied while they are away.
	 */
	static boolean livedIn(List<BlockPos> homes, BoundingBox interior) {
		BoundingBox build = interior.inflatedBy(LIVED_IN_MARGIN);
		return homes.stream().anyMatch(build::isInside);
	}

	/**
	 * True if a block players placed inside the box keeps something: a bed, a chest, a furnace, a sign (any block with
	 * a block entity). A torch or a few blocks left while exploring do not make a build someone's.
	 */
	static boolean keptByPlayers(ServerLevel level, PlayerWatch watch, BoundingBox interior) {
		for (BlockPos pos : BlockPos.betweenClosed(interior.minX(), interior.minY(), interior.minZ(), interior.maxX(), interior.maxY(), interior.maxZ())) {
			if (level.getBlockState(pos).hasBlockEntity() && watch.wasPlacedByPlayer(level, pos)) {
				return true;
			}
		}
		return false;
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
