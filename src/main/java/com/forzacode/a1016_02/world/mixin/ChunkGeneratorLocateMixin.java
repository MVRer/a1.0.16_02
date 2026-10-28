package com.forzacode.a1016_02.world.mixin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.forzacode.a1016_02.world.sc2.kit.KitPlacement;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code /locate} for the kit's own placements (docs/SELF_CREATED_2_R3.md, D-112): 26.3's
 * {@code findNearestMapStructure} searches only concentric rings and random spreads, so the rays (16, 16d) and the
 * line's marker (19, {@code AxesPlacement}) are never asked. At its return, for each requested structure on a
 * {@link KitPlacement}, the placement's chunks within the asked radius are tried nearest first with vanilla's own
 * {@code getStructureGeneratingAt} (its {@code StructureCheck}: saved starts, else the start's own decision, which the
 * kit keeps in {@code Decided}), and the nearer of vanilla's answer and ours is returned. {@code createReference}
 * (skip known structures) is honoured as vanilla honours it. Prints what vanilla prints.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorLocateMixin {
	@Shadow
	private static @Nullable Pair<BlockPos, Holder<Structure>> getStructureGeneratingAt(Set<Holder<Structure>> structures, LevelReader level,
			StructureManager structureManager, boolean createReference, StructurePlacement config, ChunkPos chunkTarget) {
		throw new AssertionError();
	}

	@Inject(method = "findNearestMapStructure", at = @At("RETURN"), cancellable = true)
	private void a1016_02$alsoTheKitsPlacements(ServerLevel level, HolderSet<Structure> wantedStructures, BlockPos pos, int maxSearchRadius,
			boolean createReference, CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
		if (!level.getServer().getWorldGenSettings().options().generateStructures()) {
			return;
		}
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		Map<StructurePlacement, Set<Holder<Structure>>> scans = new LinkedHashMap<>();
		for (Holder<Structure> structure : wantedStructures) {
			for (StructurePlacement placement : state.getPlacementsForStructure(structure)) {
				if (placement instanceof KitPlacement) {
					scans.computeIfAbsent(placement, p -> new LinkedHashSet<>()).add(structure);
				}
			}
		}
		if (scans.isEmpty()) {
			return;
		}
		Pair<BlockPos, Holder<Structure>> nearest = cir.getReturnValue();
		double best = nearest == null ? Double.MAX_VALUE : pos.distSqr(nearest.getFirst());
		int cx = pos.getX() >> 4;
		int cz = pos.getZ() >> 4;
		StructureManager structureManager = level.structureManager();
		for (Map.Entry<StructurePlacement, Set<Holder<Structure>>> entry : scans.entrySet()) {
			StructurePlacement placement = entry.getKey();
			List<ChunkPos> chunks = new ArrayList<>();
			for (ChunkPos chunk : ((KitPlacement) placement).chunks(state.getLevelSeed())) {
				if (Math.abs(chunk.x() - cx) <= maxSearchRadius && Math.abs(chunk.z() - cz) <= maxSearchRadius) {
					chunks.add(chunk);
				}
			}
			chunks.sort(Comparator.comparingDouble(chunk -> pos.distSqr(placement.getLocatePos(chunk))));
			for (ChunkPos chunk : chunks) {
				if (pos.distSqr(placement.getLocatePos(chunk)) >= best) {
					break;
				}
				if (!placement.isStructureChunk(state, chunk.x(), chunk.z())) {
					continue;
				}
				Pair<BlockPos, Holder<Structure>> found = getStructureGeneratingAt(entry.getValue(), level, structureManager, createReference, placement, chunk);
				if (found != null) {
					nearest = found;
					best = pos.distSqr(found.getFirst());
					break;
				}
			}
		}
		cir.setReturnValue(nearest);
	}
}
