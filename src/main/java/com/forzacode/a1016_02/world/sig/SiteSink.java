package com.forzacode.a1016_02.world.sig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.world.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/**
 * Where the signatures record their sites: {@link #LIVE} is the shared {@code SiteRegistry}; tests collect them in
 * a list so the shared registry (and lore's tests) never see them.
 */
public interface SiteSink {
	/** Records a site once; null if one of that type is already recorded at that exact spot. */
	SiteRegistry.@Nullable Site record(SiteType type, ResourceKey<Level> dimension, BlockPos pos, int size);

	/** Marks a site as taken, so lore (and the emptied-house card) leave it alone. */
	void claim(SiteRegistry.Site site, String by);

	SiteSink LIVE = new SiteSink() {
		@Override
		public SiteRegistry.@Nullable Site record(SiteType type, ResourceKey<Level> dimension, BlockPos pos, int size) {
			return WorldSites.record(type, dimension, pos, size, null);
		}

		@Override
		public void claim(SiteRegistry.Site site, String by) {
			Services.sites().claim(site, by);
		}
	};

	/** Tests: keeps the sites in {@code into} (claims replace the entry). */
	static SiteSink collecting(List<SiteRegistry.Site> into) {
		return new SiteSink() {
			@Override
			public SiteRegistry.Site record(SiteType type, ResourceKey<Level> dimension, BlockPos pos, int size) {
				SiteRegistry.Site site = new SiteRegistry.Site(into.size() + 1, type, dimension, pos.immutable(), size, Optional.empty());
				into.add(site);
				return site;
			}

			@Override
			public void claim(SiteRegistry.Site site, String by) {
				List<SiteRegistry.Site> copy = new ArrayList<>(into);
				for (int n = 0; n < copy.size(); n++) {
					if (copy.get(n).id() == site.id()) {
						into.set(n, new SiteRegistry.Site(site.id(), site.type(), site.dimension(), site.pos(), site.size(), Optional.of(by)));
					}
				}
			}
		};
	}
}
