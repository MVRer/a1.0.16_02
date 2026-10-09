package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.world.CrossApi;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;

/**
 * Where the lures are: the offerings cairn (F13, else the nearest ocean pyramid), the bare groves, the white eyes
 * room (F11) and his other places. Each is empty until world, dig or lore has made it.
 */
public final class Lures {
	/** "F13 the cairn": a new ocean pyramid nearest your base. */
	public static final String CAIRN_FRAGMENT = "F13";
	/** "F11 white eyes room": a cave room with a jukebox and disc 13. */
	public static final String ROOM_FRAGMENT = "F11";

	private Lures() {
	}

	/** The cairn the player leaves offerings at: lore's F13 if placed, else the ocean pyramid nearest {@code near}. */
	public static Optional<GlobalPos> cairn(MinecraftServer server, GlobalPos near, int radius) {
		Optional<GlobalPos> placed = Services.fragments().placed(server, CAIRN_FRAGMENT);
		if (placed.isPresent()) {
			return placed;
		}
		return Services.sites().find(SiteType.OCEAN_PYRAMID, near, radius).stream().findFirst().map(SiteRegistry.Site::globalPos);
	}

	/** The bare grove nearest {@code near}. */
	public static Optional<SiteRegistry.Site> grove(GlobalPos near, int radius) {
		return Services.sites().find(SiteType.BARE_GROVE, near, radius).stream().findFirst();
	}

	/** The white eyes room, if lore placed it. */
	public static Optional<GlobalPos> room(MinecraftServer server) {
		return Services.fragments().placed(server, ROOM_FRAGMENT);
	}

	/**
	 * His places a player might sleep near ("the pyramids feel like monuments"): cairns, pyramids, his crosses (never a
	 * glass memorial, which people left, D-051). Nearest first.
	 */
	public static List<GlobalPos> hisPlaces(MinecraftServer server, GlobalPos near, int radius) {
		List<GlobalPos> places = new ArrayList<>();
		for (SiteType type : new SiteType[] {SiteType.OCEAN_PYRAMID, SiteType.CROSS}) {
			for (SiteRegistry.Site site : Services.sites().find(type, near, radius)) {
				if (!CrossApi.isGlassMemorial(site)) {
					places.add(site.globalPos());
				}
			}
		}
		Services.fragments().placed(server, CAIRN_FRAGMENT).filter(p -> p.dimension().equals(near.dimension())
				&& p.pos().distSqr(near.pos()) <= (double) radius * radius).ifPresent(places::add);
		places.sort(Comparator.comparingDouble(p -> p.pos().distSqr(near.pos())));
		return places;
	}
}
