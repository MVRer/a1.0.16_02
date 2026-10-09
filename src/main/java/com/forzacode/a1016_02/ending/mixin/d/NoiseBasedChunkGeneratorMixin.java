package com.forzacode.a1016_02.ending.mixin.d;

import com.forzacode.a1016_02.ending.d.Sting;

import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ending D's sting (see {@link Sting}): once D is complete, new chunks are not carved (the carving step is skipped)
 * and, in the overworld, their noise caves are filled right after the noise fill, before the surface is built.
 * Gated on one volatile flag; existing chunks never pass through here again.
 */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {
	@Shadow
	@Final
	private Holder<NoiseGeneratorSettings> settings;

	@Inject(method = "generateCarvers", at = @At("HEAD"), cancellable = true)
	private void a1016_02$noCarvers(ChunkAccess chunk, Blender blender, NoiseChunk noiseChunk, RandomState randomState, BiomeManager biomeManager,
			@Nullable WorldGenRegion carverBiomeRegion, MaterialRule materialRule, CallbackInfo ci) {
		if (Sting.skipCarvers()) {
			ci.cancel();
		}
	}

	@Inject(method = "doFill", at = @At("RETURN"))
	private void a1016_02$noNoiseCaves(NoiseChunk noiseChunk, ChunkAccess chunk, CallbackInfo ci) {
		if (Sting.fillNoiseCaves() && (settings.is(NoiseGeneratorSettings.OVERWORLD) || settings.is(NoiseGeneratorSettings.LARGE_BIOMES)
				|| settings.is(NoiseGeneratorSettings.AMPLIFIED))) {
			Sting.fill(chunk, settings.value().defaultBlock(), noiseChunk.volume().minBlockY());
		}
	}
}
