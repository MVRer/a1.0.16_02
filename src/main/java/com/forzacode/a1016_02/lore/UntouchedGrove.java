package com.forzacode.a1016_02.lore;

import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * The untouched grove: one Dappled Forest, chosen once per world and kept in {@link LoreData}. It is where the
 * brother's care once existed (F28's map points at it, F30's sign is on its oldest poplar). New scars must stay
 * out of it; until core has a contract for that, other workstreams can read it from {@link #center}.
 */
public final class UntouchedGrove {
	/** Prefer a grove at least this far from world spawn, so the player's first base is not in it. */
	private static final int MIN_FROM_SPAWN = 300;
	private static final int SEARCH_RADIUS = 1200;
	/** Scars should keep at least this far (horizontal) from the grove center. */
	public static final int RADIUS = 48;

	private UntouchedGrove() {
	}

	/** The grove's center, once chosen (overworld). */
	public static Optional<GlobalPos> center(MinecraftServer server) {
		return LoreData.get(server).anchor(LoreData.GROVE);
	}

	/** True if {@code pos} is inside the untouched grove (within {@link #RADIUS} blocks, horizontal). */
	public static boolean contains(MinecraftServer server, GlobalPos pos) {
		return center(server).filter(c -> c.dimension().equals(pos.dimension()))
				.map(c -> Builders.horizontalDistSqr(c.pos(), pos.pos()) <= (double) RADIUS * RADIUS).orElse(false);
	}

	/** Chooses the grove if there is none yet. Searches biomes only (no chunk loads). */
	static Optional<GlobalPos> ensure(ServerLevel overworld, BlockPos spawn, RandomSource random) {
		LoreData data = LoreData.get(overworld.getServer());
		Optional<GlobalPos> known = data.anchor(LoreData.GROVE);
		if (known.isPresent()) {
			return known;
		}
		BlockPos chosen = null;
		for (int attempt = 0; attempt < 4 && chosen == null; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int distance = MIN_FROM_SPAWN + SEARCH_RADIUS / 2;
			BlockPos start = spawn.offset(Mth.floor(Math.cos(angle) * distance), 0, Mth.floor(Math.sin(angle) * distance));
			BlockPos found = find(overworld, start, SEARCH_RADIUS);
			if (found != null && Builders.horizontalDistSqr(found, spawn) >= (double) MIN_FROM_SPAWN * MIN_FROM_SPAWN) {
				chosen = found;
			}
		}
		if (chosen == null) {
			chosen = find(overworld, spawn, SEARCH_RADIUS * 2);
		}
		if (chosen == null) {
			return Optional.empty();
		}
		GlobalPos grove = GlobalPos.of(overworld.dimension(), chosen);
		data.setAnchor(LoreData.GROVE, grove);
		A1016_02.LOGGER.info("[a1016] lore: the untouched grove is at {}", chosen.toShortString());
		return Optional.of(grove);
	}

	private static BlockPos find(ServerLevel level, BlockPos start, int radius) {
		Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(biome -> biome.is(Biomes.DAPPLED_FOREST), start, radius, 32, 64);
		return found == null ? null : found.getFirst();
	}
}
