package com.forzacode.a1016_02.entity.client;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.entity.HimEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public class HimRenderer extends HumanoidMobRenderer<HimEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURE = A1016_02.id("textures/entity/him.png");

	public HimRenderer(EntityRendererProvider.Context context) {
		// Player model layer so the skin (including the outer layer) maps exactly like a player's
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
		this.addLayer(new GlowingEyesLayer(this));
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}

	@Override
	protected boolean shouldShowName(HimEntity entity, double distanceToCameraSq) {
		return false;
	}

	private static class GlowingEyesLayer extends EyesLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
		private static final RenderType EYES = RenderTypes.eyes(A1016_02.id("textures/entity/him_eyes.png"));

		GlowingEyesLayer(RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> parent) {
			super(parent);
		}

		@Override
		public RenderType renderType() {
			return EYES;
		}
	}
}
