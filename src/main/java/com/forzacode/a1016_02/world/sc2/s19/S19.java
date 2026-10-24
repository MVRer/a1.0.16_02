package com.forzacode.a1016_02.world.sc2.s19;

import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.world.sc2.kit.Interior;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Feature 19 of the self-created layer's second wave (docs/SELF_CREATED_2.md, 19), world's part: the feature type and
 * placed feature {@code a1016_02:world/s19} (every overworld biome, the last decoration step), the kind {@code s19} (no
 * structure JSON names it: it is registered so worldgen reads its config through the kit's snapshot, its recorded
 * segments have their inside, and the gallery and tests build it with {@code Live}), {@code /a1016 sc 19} and the
 * dev-only exhibit. No answers: what generated stays (D-093), and switched off no new segment is made.
 */
public final class S19 {
	/** The kind's id (the recorder's and the snapshot's). */
	public static final String ID = "s19";
	public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, A1016_02.id("world/s19"));

	/** Its inside is its plane: one stands in it only in an opening or the cut. */
	public static final Sc2Kind KIND = new Sc2Kind(ID, Remains.Kind.LINE, Sc19Config::get, query -> Optional.empty(), LineShape::fromStart,
			(level, kind, place, start, extra) -> Interior.box(place.box()), LineShape.VERSION);

	private S19() {
	}

	/** Called once from its workstream's second-wave init. */
	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, A1016_02.id("world/s19"), LineFeature.CODEC);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.TOP_LAYER_MODIFICATION, PLACED);
		Sc2Kind.register(KIND);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LineFeature.clear());
		LineCommands.register();
		if (com.forzacode.a1016_02.core.Gallery.active()) {
			LineGallery.register();
		}
	}
}
