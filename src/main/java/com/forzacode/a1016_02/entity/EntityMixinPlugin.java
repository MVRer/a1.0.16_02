package com.forzacode.a1016_02.entity;

import java.util.List;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Entity's mixin config plugin: the gallery's own mixins apply only in a development environment (R12-43), so a release
 * client never carries them (nor fails on them if their target changes). Every other mixin of the config applies as
 * listed.
 */
public final class EntityMixinPlugin implements IMixinConfigPlugin {
	/** The mixins (simple names) that serve only the dev gallery. */
	public static final Set<String> DEV_ONLY = Set.of("Sc27StandInNameMixin");

	/** Whether this mixin applies in a development environment ({@code development}) or a release. */
	public static boolean applies(String mixinClassName, boolean development) {
		String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
		return development || !DEV_ONLY.contains(simple);
	}

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return applies(mixinClassName, FabricLoader.getInstance().isDevelopmentEnvironment());
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
