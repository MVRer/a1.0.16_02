package com.forzacode.a1016_02.accident;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
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

	/** Every word the list can show, in a fixed order. */
	public static final List<String> KNOWN = List.of("fell", "lava", "burned", "drowned", "suffocated", "froze", "crushed", "heard", "exploded",
			"sleepless", "bitten", "dark", "killed");

	/** The self-created layer's second wave (docs/SELF_CREATED_2.md, 25): a death by the wolves of an armed trap. */
	public static final String WOLVES = "wolves";
	/** The same wave (26): a death by an arrow of an armed trap's skeleton or stray. */
	public static final String SHOT = "shot";
	/**
	 * Words the list can show that {@code /a1016 accident mark} does not offer (D-080, D-103: its suggestions would name
	 * them before they happen). Given only by {@link #byKiller}, for the traps that use it.
	 */
	public static final List<String> UNLISTED = List.of(WOLVES, SHOT);

	private DeathCauses() {
	}

	/**
	 * The word for a death by a trap's own instruments, by the killer's kind (docs/SELF_CREATED_2.md, 25 and 26): a wolf,
	 * {@link #WOLVES}; an arrow from a skeleton or a stray, {@link #SHOT}; a spider, "bitten"; anything else
	 * {@code fallback}. Only the traps that use it ask; every other trap keeps {@link #word}.
	 */
	public static String byKiller(DamageSource source, String fallback) {
		Entity killer = source.getEntity();
		if (killer instanceof Wolf) {
			return WOLVES;
		}
		if (killer instanceof AbstractSkeleton && source.getDirectEntity() instanceof AbstractArrow) {
			return SHOT;
		}
		if (killer instanceof Spider) {
			return "bitten";
		}
		return fallback;
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
