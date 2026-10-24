package com.forzacode.a1016_02.atmosphere.sc2.s21;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.sc.EmptyNight;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What the second wave's mob behaviours (21 to 24, docs/SELF_CREATED_2.md) share: which animals may be used, the times
 * of day, outdoors, the ground a mob can stand on, and who is here. Pure where it can be; server thread otherwise.
 */
public final class Herds {
	/** 21's kinds: one kind per cluster. */
	public static final Set<EntityType<?>> RING_KINDS = Set.of(EntityTypes.COW, EntityTypes.SHEEP, EntityTypes.PIG, EntityTypes.CHICKEN,
			EntityTypes.HORSE, EntityTypes.DONKEY, EntityTypes.LLAMA);
	/** 22's passive animals (every kind of them may walk together). */
	public static final Set<EntityType<?>> PASSIVE = Set.of(EntityTypes.COW, EntityTypes.SHEEP, EntityTypes.PIG, EntityTypes.CHICKEN,
			EntityTypes.HORSE, EntityTypes.DONKEY, EntityTypes.LLAMA, EntityTypes.MOOSHROOM);
	/** 24's kinds. */
	public static final Set<EntityType<?>> FOLLOWER_KINDS = Set.of(EntityTypes.COW, EntityTypes.PIG, EntityTypes.SHEEP, EntityTypes.CHICKEN);
	/** Sky light at the eyes at least this: outdoors. */
	public static final int OUTDOORS_SKY = 10;
	public static final long DAY = 24_000L;
	public static final long DUSK_FROM = 12_000L;
	public static final long NIGHT_FROM = 13_000L;
	public static final long NIGHT_UNTIL = 23_000L;

	private Herds() {
	}

	/**
	 * An animal nothing holds and no player claims: alive, on no leash, neither riding nor ridden, without a name, not
	 * tamed or owned, and not tampered with (the empty night's own quiet aside, which holds nothing but its sound).
	 */
	public static boolean free(Mob mob) {
		if (!mob.isAlive() || mob.isRemoved() || mob.isLeashed() || mob.isPassenger() || mob.isVehicle() || mob.hasCustomName() || mob.isNoAi()) {
			return false;
		}
		if (mob instanceof OwnableEntity owned && owned.getOwnerReference() != null || mob instanceof AbstractHorse horse && horse.isTamed()) {
			return false;
		}
		return !tampered(mob);
	}

	/** Tampered with by anything but the empty night's quiet (04), which only holds its sound. */
	public static boolean tampered(Mob mob) {
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		if (!tamper.isTampered(mob)) {
			return false;
		}
		return !(EmptyNight.silencedByIt(mob) && !tamper.isFrozen(mob) && !tamper.isFacing(mob) && !tamper.isSteered(mob) && !tamper.isHunting(mob));
	}

	/** The time of day (0 to 23999) of this level's clock. */
	public static long timeOfDay(ServerLevel level) {
		return Math.floorMod(level.getDefaultClockTime(), DAY);
	}

	/** Dusk: 12000 to 13000. */
	public static boolean dusk(long timeOfDay) {
		return timeOfDay >= DUSK_FROM && timeOfDay < NIGHT_FROM;
	}

	/** Night: 13000 to 23000. */
	public static boolean night(long timeOfDay) {
		return timeOfDay >= NIGHT_FROM && timeOfDay < NIGHT_UNTIL;
	}

	/** Dusk or night. */
	public static boolean duskOrNight(long timeOfDay) {
		return timeOfDay >= DUSK_FROM && timeOfDay < NIGHT_UNTIL;
	}

	/** A fog spell that counts is on now (the world's weather fog, not the dusk haze). */
	public static boolean countingFog(MinecraftServer server) {
		Optional<FogSpell> spell = HerobrineState.get(server).effects().fogSpell();
		return spell.isPresent() && spell.get().counts(server.overworld().getDefaultClockTime());
	}

	/** Sky light at the player's eyes at least {@link #OUTDOORS_SKY}. */
	public static boolean outdoors(ServerPlayer player) {
		return skyAt(player.level(), BlockPos.containing(player.getEyePosition())) >= OUTDOORS_SKY;
	}

	/** Sky light at a cell. */
	public static int skyAt(ServerLevel level, BlockPos pos) {
		return level.getBrightness(LightLayer.SKY, pos);
	}

	/** Every living participant in this level, by the live watch. */
	public static List<ServerPlayer> here(ServerLevel level) {
		return here(Services.watch(), level);
	}

	/** Every living participant in this level, by this watch. */
	public static List<ServerPlayer> here(PlayerWatch watch, ServerLevel level) {
		List<ServerPlayer> list = new ArrayList<>();
		for (ServerPlayer player : watch.participants(level.getServer())) {
			if (player.level() == level && player.isAlive() && !player.isRemoved()) {
				list.add(player);
			}
		}
		return list;
	}

	/** The horizontal distance squared. */
	public static double flatSqr(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	/**
	 * The ground a mob can stand on in column (x, z), searched from {@code y + up} down to {@code y - down}: the Y of the
	 * first cell (feet) that is free with two free cells (no collision, no fluid) and a solid top below it that is no
	 * fluid. Empty if none (water, a wall, a cliff). Never loads a chunk: an unloaded column has no ground.
	 */
	public static OptionalInt feet(ServerLevel level, int x, int z, int y, int up, int down) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		if (!level.isLoaded(pos.set(x, y, z))) {
			return OptionalInt.empty();
		}
		for (int fy = y + up; fy >= y - down; fy--) {
			if (free(level, pos.set(x, fy, z)) && free(level, pos.set(x, fy + 1, z))) {
				BlockState below = level.getBlockState(pos.set(x, fy - 1, z));
				if (!below.getFluidState().isEmpty()) {
					return OptionalInt.empty();
				}
				if (!below.getCollisionShape(level, pos).isEmpty() && below.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP)) {
					return OptionalInt.of(fy);
				}
			}
		}
		return OptionalInt.empty();
	}

	/** No collision and no fluid. */
	public static boolean free(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty() && !state.getFluidState().is(FluidTags.WATER);
	}

	/**
	 * The switch's give-back for the animals a card took from where they stood (R13-7: 22 and 24 ledger each one's first
	 * move, {@code TraceLedger.Kind.MOVE_MOB}): every such entry of {@code cause} goes back through the ledger's own undo
	 * (out of view, never loading a chunk). Returns how many are still to give back (not loaded, in view, not found now).
	 */
	public static int giveBack(MinecraftServer server, String cause) {
		int left = 0;
		for (TraceLedger.Entry entry : List.copyOf(TraceLedger.get(server).entries())) {
			if (entry.kind() != TraceLedger.Kind.MOVE_MOB || !cause.equals(entry.cause())) {
				continue;
			}
			ServerLevel level = server.getLevel(entry.pos().dimension());
			if (level != null && Services.traces().undo(level, entry) != TraceService.UndoResult.DONE) {
				left++;
			}
		}
		return left;
	}
}
