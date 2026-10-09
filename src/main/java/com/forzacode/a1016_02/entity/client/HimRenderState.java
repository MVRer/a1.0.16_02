package com.forzacode.a1016_02.entity.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Render state of the figure. */
public class HimRenderState extends HumanoidRenderState {
	/** 1 = low and still on all fours (the "cow" in the fog), 0 = standing. Blended while he rises. */
	public float lowAmount;
}
