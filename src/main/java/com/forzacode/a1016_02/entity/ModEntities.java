package com.forzacode.a1016_02.entity;

import com.forzacode.a1016_02.A1016_02;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private static final ResourceKey<EntityType<?>> HIM_KEY = ResourceKey.create(Registries.ENTITY_TYPE, A1016_02.id("him"));

	/**
	 * MISC: never counted toward mob caps and never spawned naturally. noSave: never written to disk, so he is gone
	 * when his chunk unloads. The tracking range (64 chunks, before the server's broadcast-range scaling) is far past
	 * any view distance, so it is always capped by the server view distance: a client never drops him while he can
	 * still be seen.
	 */
	public static final EntityType<HimEntity> HIM = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			HIM_KEY,
			EntityType.Builder.of(HimEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.clientTrackingRange(64)
					.noSave()
					.noLootTable()
					.fireImmune()
					.build(HIM_KEY)
	);

	private ModEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(HIM, HimEntity.createAttributes());
	}
}
