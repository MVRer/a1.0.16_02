package com.forzacode.a1016_02.entity.client;

import com.forzacode.a1016_02.entity.EyeStyle;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import org.jspecify.annotations.Nullable;

/** Render state of the figure. */
public class HimRenderState extends HumanoidRenderState {
	/** 1 = low and still on all fours (the "cow" in the fog), 0 = standing. Blended while he rises. */
	public float lowAmount;
	/** His own eye style (the glimpse's FLAT, D-073), or null for the configured one. */
	public @Nullable EyeStyle eyeStyle;
}
