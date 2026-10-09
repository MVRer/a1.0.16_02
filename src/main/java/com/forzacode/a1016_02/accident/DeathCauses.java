package com.forzacode.a1016_02.accident;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Drowned;

import org.jspecify.annotations.Nullable;

/** The short word the list shows for a marked death: fell, lava, drowned, suffocated, froze, burned, crushed... */
public final class DeathCauses {
	private static final Map<ResourceKey<DamageType>, String> WORDS = new LinkedHashMap<>();

	static {
		put("fell", DamageTypes.FALL, DamageTypes.FELL_OUT_OF_WORLD, DamageTypes.FLY_INTO_WALL, DamageTypes.ENDER_PEARL);
		put("lava", DamageTypes.LAVA);
		put("burned", DamageTypes.IN_FIRE, DamageTypes.ON_FIRE, DamageTypes.CAMPFIRE, DamageTypes.HOT_FLOOR);
		put("drowned", DamageTypes.DROWN);
		put("suffocated", DamageTypes.IN_WALL, DamageTypes.CRAMMING);
		put("froze", DamageTypes.FREEZE);
		put("crushed", DamageTypes.FALLING_STALACTITE, DamageTypes.FALLING_BLOCK, DamageTypes.FALLING_ANVIL, DamageTypes.STALAGMITE);
		put("heard", DamageTypes.SONIC_BOOM);
		put("exploded", DamageTypes.EXPLOSION, DamageTypes.PLAYER_EXPLOSION);
	}

	private DeathCauses() {
	}

	@SafeVarargs
	private static void put(String word, ResourceKey<DamageType>... types) {
		for (ResourceKey<DamageType> type : types) {
			WORDS.put(type, word);
		}
	}

	/** The word for this death, using the trap's own word when the damage says nothing more specific. */
	public static String word(DamageSource source, @Nullable TrapKind trap) {
		for (Map.Entry<ResourceKey<DamageType>, String> entry : WORDS.entrySet()) {
			if (source.is(entry.getKey())) {
				return entry.getValue();
			}
		}
		Entity attacker = source.getEntity();
		if (attacker instanceof Phantom) {
			return "sleepless";
		}
		if (attacker instanceof Warden) {
			return "heard";
		}
		if (attacker instanceof Creeper) {
			return "exploded";
		}
		if (attacker instanceof Drowned) {
			return "drowned";
		}
		if (attacker instanceof Spider) {
			return "bitten";
		}
		return trap != null ? trap.cause() : "died";
	}
}
