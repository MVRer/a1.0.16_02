package com.forzacode.a1016_02.lore;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.TraceLedger;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/**
 * Where his traces are, for "written near his traces" (D-041): only what he removed or moved. His site kinds
 * (tunnels and cuts, ocean pyramids, bare groves, dead mountains, emptied houses, the Under-you network, crosses,
 * the house copy), never one lore built itself (left by people), plus every ledgered edit except what others left
 * ({@code lore:left/*}). Ledger positions are kept per chunk and brought up to date incrementally: only entries
 * added since the last look are read; a ledger that shrank or changed under the index is read again.
 */
final class TraceIndex {
	/** The site kinds that are his: what he removed or moved. */
	static final Set<SiteType> HIS_SITES = EnumSet.of(SiteType.TUNNEL_END, SiteType.CUT, SiteType.OCEAN_PYRAMID, SiteType.BARE_GROVE,
			SiteType.DEAD_MOUNTAIN, SiteType.EMPTIED_HOUSE, SiteType.UNDER_BASE, SiteType.CROSS, SiteType.HOUSE_COPY);
	/** Ledger causes of what others left: never his traces. */
	static final String LEFT_CAUSE = "lore:left/";

	private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<LongArrayList>> cells = new HashMap<>();
	private @Nullable TraceLedger source;
	private int indexed;
	private TraceLedger.@Nullable Entry last;

	/** True if the ledger entry is his (not something others left). */
	static boolean isHis(TraceLedger.Entry entry) {
		return !entry.cause().startsWith(LEFT_CAUSE);
	}

	/** True if the site is his: one of his kinds, and not one lore built itself. */
	static boolean isHis(SiteRegistry.Site site, IntPredicate loreBuilt) {
		return HIS_SITES.contains(site.type()) && !loreBuilt.test(site.id());
	}

	/** An index of these entries (tests). */
	static TraceIndex of(Collection<TraceLedger.Entry> entries) {
		TraceIndex index = new TraceIndex();
		entries.forEach(index::add);
		return index;
	}

	/** Brings the index up to date with the ledger and returns it. */
	TraceIndex sync(TraceLedger ledger) {
		List<TraceLedger.Entry> entries = ledger.entries();
		if (ledger != source || entries.size() < indexed || indexed > 0 && entries.get(indexed - 1) != last) {
			clear();
			source = ledger;
		}
		for (int n = indexed; n < entries.size(); n++) {
			add(entries.get(n));
		}
		indexed = entries.size();
		last = indexed > 0 ? entries.get(indexed - 1) : null;
		return this;
	}

	void clear() {
		cells.clear();
		source = null;
		indexed = 0;
		last = null;
	}

	/** Adds one ledgered edit (its position, and where a move went), unless others left it. */
	void add(TraceLedger.Entry entry) {
		if (!isHis(entry)) {
			return;
		}
		add(entry.pos().dimension(), entry.pos().pos());
		entry.to().ifPresent(to -> add(entry.pos().dimension(), to));
	}

	void add(ResourceKey<Level> dimension, BlockPos pos) {
		cells.computeIfAbsent(dimension, k -> new Long2ObjectOpenHashMap<>())
				.computeIfAbsent(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4), k -> new LongArrayList()).add(pos.asLong());
	}

	/** True if an indexed edit lies within {@code radius} blocks of {@code pos}. Reads only the chunks in reach. */
	boolean near(GlobalPos pos, int radius) {
		Long2ObjectOpenHashMap<LongArrayList> dimension = cells.get(pos.dimension());
		if (dimension == null || dimension.isEmpty()) {
			return false;
		}
		BlockPos p = pos.pos();
		double radiusSqr = (double) radius * radius;
		for (int cx = (p.getX() - radius) >> 4; cx <= (p.getX() + radius) >> 4; cx++) {
			for (int cz = (p.getZ() - radius) >> 4; cz <= (p.getZ() + radius) >> 4; cz++) {
				LongArrayList list = dimension.get(ChunkPos.pack(cx, cz));
				if (list == null) {
					continue;
				}
				for (int n = 0; n < list.size(); n++) {
					if (BlockPos.of(list.getLong(n)).distSqr(p) <= radiusSqr) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Positions indexed (tests and debug). */
	int size() {
		int size = 0;
		for (Long2ObjectOpenHashMap<LongArrayList> dimension : cells.values()) {
			for (LongArrayList list : dimension.values()) {
				size += list.size();
			}
		}
		return size;
	}
}
