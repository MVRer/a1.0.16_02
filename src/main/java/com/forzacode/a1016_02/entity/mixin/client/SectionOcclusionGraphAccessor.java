package com.forzacode.a1016_02.entity.mixin.client;

import java.util.concurrent.Future;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.SectionOcclusionGraph;

/**
 * The self-created layer's 03: a camera far from the player makes vanilla rebuild its section graph in the background
 * ({@code SectionOcclusionGraph.scheduleFullUpdate}); {@code HisViewClient} waits for that rebuild between its unpresented
 * frames, and again once the camera is back, so neither its picture nor the next frame on the screen uses the other
 * camera's sections.
 */
@Mixin(SectionOcclusionGraph.class)
public interface SectionOcclusionGraphAccessor {
	@Accessor("fullUpdateTask")
	Future<?> a1016_02$fullUpdateTask();
}
