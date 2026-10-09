package com.forzacode.a1016_02.entity.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/** {@code RenderType.create} is package-private; the figure's eyes need a render type on their own pipeline. */
@Mixin(RenderType.class)
public interface RenderTypeInvoker {
	@Invoker("create")
	static RenderType a1016_02$create(String name, RenderSetup setup) {
		throw new AssertionError("mixin invoker not applied");
	}
}
