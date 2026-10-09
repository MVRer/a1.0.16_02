package com.forzacode.a1016_02.entity.client;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.entity.EntityConfig;
import com.forzacode.a1016_02.entity.EyeStyle;
import com.forzacode.a1016_02.entity.mixin.client.RenderTypeInvoker;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * The figure's eyes on top of the skin, by {@link EntityConfig#eyeStyle()}, read every frame so
 * {@code /a1016 entity eyes} shows at once in singleplayer.
 * <ul>
 * <li>FLAT: nothing here; the eyes painted into the skin are lit like the rest of it.</li>
 * <li>BRIGHT: the four eye pixels again, opaque, unlit and unshaded (always full white), fogged at
 * {@code 1 - eyeFogResistance} strength by {@code shaders/entity/him_eyes.fsh}. No light, no bloom, no particles.</li>
 * <li>GLOW: BRIGHT plus a soft emissive spill around the eyes through the vanilla eyes render type (spider and
 * enderman style). The spill is fogged like the terrain.</li>
 * </ul>
 */
public class HimEyesLayer extends RenderLayer<HimRenderState, HimModel> {
	private static final Identifier EYES_TEXTURE = A1016_02.id("textures/entity/him_eyes.png");
	private static final Identifier GLOW_TEXTURE = A1016_02.id("textures/entity/him_eyes_glow.png");

	/**
	 * Vanilla's eyes pipeline (core/entity built with EMISSIVE, NO_OVERLAY, NO_CARDINAL_LIGHTING), but opaque with
	 * an alpha cutout and our fragment shader, which scales the fog by the vertex alpha. Compiled on first use.
	 */
	private static final RenderPipeline EYES_PIPELINE = RenderPipeline.builder()
			.withBindGroupLayout(BindGroupLayouts.GLOBALS)
			.withBindGroupLayout(BindGroupLayouts.PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
			.withBindGroupLayout(BindGroupLayouts.FOG)
			.withVertexShader("core/entity")
			.withFragmentShader(A1016_02.id("entity/him_eyes"))
			.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
			.withVertexBinding(0, DefaultVertexFormat.ENTITY)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withDepthStencilState(DepthStencilState.DEFAULT)
			.withShaderDefine("EMISSIVE")
			.withShaderDefine("NO_OVERLAY")
			.withShaderDefine("NO_CARDINAL_LIGHTING")
			.withShaderDefine("ALPHA_CUTOUT", 0.1F)
			.withLocation(A1016_02.id("pipeline/entity/him_eyes"))
			.withColorTargetState(ColorTargetState.DEFAULT)
			.build();
	private static final RenderType BRIGHT_EYES = RenderTypeInvoker.a1016_02$create("a1016_02_him_eyes",
			RenderSetup.builder(EYES_PIPELINE).withTexture("Sampler0", EYES_TEXTURE).createRenderSetup());
	private static final RenderType GLOW_SPILL = RenderTypes.eyes(GLOW_TEXTURE);

	public HimEyesLayer(RenderLayerParent<HimRenderState, HimModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, HimRenderState state, float yRot, float xRot) {
		EntityConfig config = EntityConfig.get();
		EyeStyle style = config.eyeStyle();
		if (style == EyeStyle.FLAT) {
			return;
		}
		// After the skin (order 1), on exactly its geometry, so the depth test lets the eyes through.
		OrderedSubmitNodeCollector after = collector.order(1);
		after.submitModel(getParentModel(), state, poseStack, BRIGHT_EYES, light, OverlayTexture.NO_OVERLAY, fogTint(config.eyeFogResistance()), null,
				state.outlineColor);
		if (style == EyeStyle.GLOW) {
			after.submitModel(getParentModel(), state, poseStack, GLOW_SPILL, light, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
		}
	}

	/** White, with the fog strength {@code 1 - resistance} in the alpha byte for him_eyes.fsh. */
	static int fogTint(double resistance) {
		int fogStrength = (int) Math.round((1.0 - Math.clamp(resistance, 0.0, 1.0)) * 255.0);
		return fogStrength << 24 | 0xFFFFFF;
	}
}
