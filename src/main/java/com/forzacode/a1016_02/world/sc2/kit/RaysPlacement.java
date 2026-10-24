package com.forzacode.a1016_02.world.sc2.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.world.gen.Hash;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;

/**
 * Placement on rays from the world's origin ({@code a1016_02:world/sc2r}, D-095): {@code rays} rays from (0, 0), the
 * first at a bearing hashed from the world's seed and the set's {@code salt}, the others evenly spread, each turned by
 * up to half of {@code jitter_degrees} either way (so neighbouring rays stay within {@code jitter_degrees} of even);
 * along each ray {@code slots} slots at {@code first + step * k} blocks (k from 0), each moved up to
 * {@code jitter_blocks} along and across the ray. A slot's chunk is its one placement chunk. Vanilla's
 * {@code exclusion_zone}, {@code frequency} and {@code locate_offset} work as for the random spread. Deterministic from
 * the seed (never from the server's state), so {@link #slots} answers where they are. Vanilla's locate cannot follow it
 * (nothing points the player at them). A structure set:
 *
 * <pre>{@code
 * { "structures": [ { "structure": "a1016_02:world/s16", "weight": 1 } ],
 *   "placement": { "type": "a1016_02:world/sc2r", "salt": 160721, "rays": 3, "first": 768, "step": 896,
 *                  "jitter_degrees": 8, "jitter_blocks": 48,
 *                  "exclusion_zone": { "other_set": "minecraft:villages", "chunk_count": 8 } } }
 * }</pre>
 */
public final class RaysPlacement extends AbstractSpreadingStructurePlacement {
	public static final MapCodec<RaysPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> placementCodec(i).and(i.group(
			Codec.intRange(1, 16).optionalFieldOf("rays", 3).forGetter(RaysPlacement::rays),
			Codec.intRange(0, 29_000_000).fieldOf("first").forGetter(RaysPlacement::first),
			Codec.intRange(32, 29_000_000).fieldOf("step").forGetter(RaysPlacement::step),
			Codec.floatRange(0.0F, 90.0F).optionalFieldOf("jitter_degrees", 8.0F).forGetter(RaysPlacement::jitterDegrees),
			Codec.intRange(0, 4096).optionalFieldOf("jitter_blocks", 48).forGetter(RaysPlacement::jitterBlocks),
			Codec.intRange(1, 100_000).optionalFieldOf("slots", 64).forGetter(RaysPlacement::slots)
	)).apply(i, RaysPlacement::new));

	private final int rays;
	private final int first;
	private final int step;
	private final float jitterDegrees;
	private final int jitterBlocks;
	private final int slots;

	public RaysPlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt,
			Optional<ExclusionZone> exclusionZone, int rays, int first, int step, float jitterDegrees, int jitterBlocks, int slots) {
		super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
		this.rays = rays;
		this.first = first;
		this.step = step;
		this.jitterDegrees = jitterDegrees;
		this.jitterBlocks = Math.min(jitterBlocks, step / 2 - 16);
		this.slots = slots;
	}

	/** The plain form: no exclusion zone, full frequency. */
	public RaysPlacement(int salt, int rays, int first, int step, float jitterDegrees, int jitterBlocks, int slots, Optional<ExclusionZone> exclusionZone) {
		this(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1.0F, salt, exclusionZone, rays, first, step, jitterDegrees, jitterBlocks, slots);
	}

	public int rays() {
		return rays;
	}

	public int first() {
		return first;
	}

	public int step() {
		return step;
	}

	public float jitterDegrees() {
		return jitterDegrees;
	}

	public int jitterBlocks() {
		return jitterBlocks;
	}

	public int slots() {
		return slots;
	}

	/** The set's salt. */
	public int saltValue() {
		return salt();
	}

	/** Ray {@code ray}'s bearing for this seed, degrees in [0, 360): x = cos, z = sin. */
	public double bearing(long seed, int ray) {
		double firstBearing = Hash.unit(Hash.of(seed, salt(), 0x52415953L)) * 360.0;
		double even = firstBearing + ray * 360.0 / rays;
		double turn = ray == 0 ? 0.0 : (Hash.unit(Hash.of(seed, salt(), ray, 1)) * 2.0 - 1.0) * jitterDegrees / 2.0;
		double b = (even + turn) % 360.0;
		return b < 0 ? b + 360.0 : b;
	}

	/** Slot {@code k} of ray {@code ray} for this seed: its block (y 0). */
	public BlockPos slot(long seed, int ray, int k) {
		double rad = Math.toRadians(bearing(seed, ray));
		double along = first + (double) step * k + (Hash.unit(Hash.of(seed, salt(), ray, k, 2)) * 2.0 - 1.0) * jitterBlocks;
		double across = (Hash.unit(Hash.of(seed, salt(), ray, k, 3)) * 2.0 - 1.0) * jitterBlocks;
		double x = Math.cos(rad) * along - Math.sin(rad) * across;
		double z = Math.sin(rad) * along + Math.cos(rad) * across;
		return new BlockPos((int) Math.floor(x), 0, (int) Math.floor(z));
	}

	/** Slot {@code k} of ray {@code ray}'s chunk. */
	public ChunkPos slotChunk(long seed, int ray, int k) {
		return ChunkPos.containing(slot(seed, ray, k));
	}

	/** One slot: its ray, its number, its block and chunk. */
	public record Slot(int ray, int k, BlockPos pos, ChunkPos chunk) {
	}

	/** Every slot with {@code k} under {@code count} (and under {@link #slots}), ray by ray. */
	public List<Slot> slots(long seed, int count) {
		List<Slot> out = new ArrayList<>();
		for (int ray = 0; ray < rays; ray++) {
			for (int k = 0; k < Math.min(count, slots); k++) {
				BlockPos pos = slot(seed, ray, k);
				out.add(new Slot(ray, k, pos, ChunkPos.containing(pos)));
			}
		}
		return out;
	}

	/** True if this chunk is a slot's chunk for this seed (the exclusion zone and frequency not asked). */
	public boolean isSlotChunk(long seed, int chunkX, int chunkZ) {
		double cx = chunkX * 16 + 8;
		double cz = chunkZ * 16 + 8;
		for (int ray = 0; ray < rays; ray++) {
			double rad = Math.toRadians(bearing(seed, ray));
			double along = cx * Math.cos(rad) + cz * Math.sin(rad);
			int k0 = (int) Math.round((along - first) / step);
			for (int k = k0 - 1; k <= k0 + 1; k++) {
				if (k < 0 || k >= slots) {
					continue;
				}
				ChunkPos chunk = slotChunk(seed, ray, k);
				if (chunk.x() == chunkX && chunk.z() == chunkZ) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
		return isSlotChunk(state.getLevelSeed(), sourceX, sourceZ);
	}

	@Override
	public MapCodec<RaysPlacement> codec() {
		return CODEC;
	}
}
