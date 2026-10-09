package com.forzacode.a1016_02.entity.client;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.entity.HimEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/**
 * The figure: the player model with the default Steve skin and pure white eyes painted into the base texture.
 * The skin is lit and fogged like the terrain around him. The eyes on top of it follow the {@code eyeStyle}
 * setting ({@link HimEyesLayer}): FLAT leaves the painted ones, BRIGHT (default) keeps them full white in the dark and
 * lets them fade into fog later than the body. Nothing emits light and he never shows a name.
 */
public class HimRenderer extends HumanoidMobRenderer<HimEntity, HimRenderState, HimModel> {
	private static final Identifier TEXTURE = A1016_02.id("textures/entity/him.png");
	/** The player renderer's scale: a player is 1.875 blocks tall, not the 2 of the raw humanoid model. */
	private static final float PLAYER_SCALE = 0.9375F;

	public HimRenderer(EntityRendererProvider.Context context) {
		// Player model layer so the skin (including the outer layer) maps exactly like a player's
		super(context, new HimModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
		addLayer(new HimEyesLayer(this));
	}

	@Override
	public HimRenderState createRenderState() {
		return new HimRenderState();
	}

	@Override
	public void extractRenderState(HimEntity entity, HimRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.lowAmount = entity.lowAmount(partialTick);
	}

	@Override
	public Identifier getTextureLocation(HimRenderState state) {
		return TEXTURE;
	}

	@Override
	protected void scale(HimRenderState state, PoseStack poseStack) {
		poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
	}

	@Override
	protected boolean shouldShowName(HimEntity entity, double distanceToCameraSq) {
		return false;
	}
}
