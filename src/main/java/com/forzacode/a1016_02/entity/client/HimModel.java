package com.forzacode.a1016_02.entity.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The player-shaped humanoid model, plus a low pose: on all fours, torso level, limbs straight down and the head
 * raised. About a block tall and a block long, which reads as something cow-sized in the fog. Rising blends
 * from that pose back to the normal standing one.
 *
 * <p>Model space: y points down, -z is the front, feet are at y 24.
 */
public class HimModel extends HumanoidModel<HimRenderState> {
	private static final float HALF_PI = Mth.HALF_PI;

	public HimModel(ModelPart root) {
		super(root);
	}

	@Override
	public void setupAnim(HimRenderState state) {
		super.setupAnim(state);
		float t = Mth.clamp(state.lowAmount, 0.0F, 1.0F);
		if (t <= 0.0F) {
			return;
		}
		// Neck at y 12: the torso lies from the neck backward (z 0..12), its chest toward the ground.
		blend(body, 0.0F, 12.0F, 0.0F, HALF_PI, 0.0F, 0.0F, t);
		// Head just in front of the shoulders, looking ahead. Keeps its yaw so he still faces you.
		blend(head, 0.0F, 13.0F, -2.0F, 0.0F, head.yRot, 0.0F, t);
		// Arms hang from the front of the torso to the ground (arm box spans pivot -2..+10).
		blend(rightArm, -5.0F, 14.0F, 1.0F, 0.0F, 0.0F, 0.0F, t);
		blend(leftArm, 5.0F, 14.0F, 1.0F, 0.0F, 0.0F, 0.0F, t);
		// Legs hang from the back of the torso to the ground.
		blend(rightLeg, -1.9F, 12.0F, 10.0F, 0.0F, 0.0F, 0.0F, t);
		blend(leftLeg, 1.9F, 12.0F, 10.0F, 0.0F, 0.0F, 0.0F, t);
	}

	private static void blend(ModelPart part, float x, float y, float z, float xRot, float yRot, float zRot, float t) {
		part.x = Mth.lerp(t, part.x, x);
		part.y = Mth.lerp(t, part.y, y);
		part.z = Mth.lerp(t, part.z, z);
		part.xRot = Mth.lerp(t, part.xRot, xRot);
		part.yRot = Mth.lerp(t, part.yRot, yRot);
		part.zRot = Mth.lerp(t, part.zRot, zRot);
	}
}
