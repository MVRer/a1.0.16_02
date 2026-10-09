package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.accident.AccidentData;
import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.Scan;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * D-034, the End: an enderman that is already out there is moved ({@link MobTamper#moveOutOfView}) onto your 1-wide
 * bridge ahead of you, out of view. You meet its eyes on a path one block wide; the game does the rest. Endermen
 * teleport, so it is deniable. The clue: no enderman could have teleported there by itself (a vanilla random teleport
 * reaches {@code endermanTeleportReach}, 32, on x and on z separately, and drops to the ground from up to that far
 * above); this one came from farther. He never spawns one. A death counts if that enderman struck you, or knocked you
 * off into the void.
 */
public final class EndermanBridgeTrap extends BaseTrap {
	public EndermanBridgeTrap() {
		super("enderman_on_bridge", false, "fell", EnumSet.of(Habit.VISITOR));
	}

	@Override
	public @Nullable String blocked() {
		return Services.mobs() instanceof MobTamper.Stub ? "MobTamper is still the core stub (atmosphere's real one is not merged)" : null;
	}

	/** Only in the End. */
	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel level, AccidentConfig cfg) {
		return level.dimension().equals(Level.END);
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		AccidentConfig cfg = ctx.cfg();
		List<Candidate> found = new ArrayList<>();
		if (!level.dimension().equals(Level.END)) {
			return found;
		}
		List<BlockPos> spots = new ArrayList<>();
		for (BlockPos deck : Bridges.walkedDeck(ctx)) {
			Direction.Axis along = Bridges.spanAxis(level, deck);
			BlockPos feet = deck.above();
			if (along == null || Bridges.fromPlayer(ctx, feet) < Math.max(cfg.bridgeMinFromPlayer, cfg.mobMinFromPlayer) || !Bridges.oneWide(level, deck, along)
					|| !Scan.open(level, feet) || !Scan.open(level, feet.above()) || !Scan.open(level, feet.above(2)) || !Bridges.overVoid(level, deck)
					|| !Bridges.ahead(ctx, deck, Bridges.Below.VOID)) {
				continue;
			}
			spots.add(feet);
		}
		if (spots.isEmpty()) {
			return found;
		}
		AABB search = new AABB(ctx.center()).inflate(cfg.endermanSearchRadius + cfg.scanRadius);
		List<Enderman> endermen = level.getEntitiesOfClass(Enderman.class, search, e -> e.isAlive() && !e.isRemoved());
		spots.sort(Comparator.comparingDouble(p -> Bridges.fromPlayer(ctx, p)));
		for (BlockPos spot : spots) {
			Vec3 at = Vec3.atBottomCenterOf(spot);
			// The clue must hold: no enderman, the one moved included, could have teleported there by itself.
			if (endermen.stream().anyMatch(e -> canTeleportTo(e.position(), spot, cfg.endermanTeleportReach))) {
				continue;
			}
			Enderman mover = endermen.stream()
					.filter(e -> movable(e) && e.position().distanceTo(at) <= cfg.endermanSearchRadius
							&& level.noCollision(e, e.getDimensions(e.getPose()).makeBoundingBox(at)))
					.min(Comparator.comparingDouble(e -> e.position().distanceTo(at))).orElse(null);
			if (mover == null) {
				continue;
			}
			int r = cfg.bridgeZoneRadius;
			found.add(Candidate.of(spot, List.of(), new BlockPos(spot.getX() - r, level.getMinY() - 128, spot.getZ() - r), spot.offset(r, 6, r),
					"An enderman stood on your 1-wide bridge at " + at(spot) + ", though none was within its " + cfg.endermanTeleportReach
							+ "-block teleport reach; it came from " + Math.round(mover.position().distanceTo(at)) + " blocks away.").withMob(mover, spot));
			if (found.size() >= 8) {
				break;
			}
		}
		return found;
	}

	/**
	 * True if a vanilla random teleport from {@code from} can land on {@code spot}. {@code Enderman.teleport()} aims up
	 * to {@code reach} away on x and on z separately (a box, not a circle: about 45 blocks on a diagonal) and up to
	 * {@code reach} above or below, then drops to the first ground under that point, so any spot lower down is in reach
	 * too. Counted generously (a block's width of slack across), so the clue only ever errs toward "it could not".
	 */
	public static boolean canTeleportTo(Vec3 from, BlockPos spot, int reach) {
		double dx = Math.abs(spot.getX() + 0.5 - from.x);
		double dz = Math.abs(spot.getZ() + 0.5 - from.z);
		return Math.max(dx, dz) <= reach + 1 && spot.getY() - from.y <= reach;
	}

	/** An enderman he may move: nobody's named one, not riding, not held by another card. */
	static boolean movable(Enderman enderman) {
		return !enderman.hasCustomName() && !enderman.isPassenger() && !enderman.isVehicle() && !Services.mobs().isTampered(enderman);
	}

	/** Moves it onto the bridge, only while both where it stands and where it goes are out of view. */
	@Override
	public boolean setup(TrapContext ctx, Candidate candidate) {
		Mob mob = candidate.mob;
		BlockPos to = candidate.mobTo;
		ServerLevel level = ctx.level();
		if (mob == null || to == null || !mob.isAlive() || mob.level() != level || blocked() != null) {
			return false;
		}
		AABB landing = mob.getDimensions(mob.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(to));
		if (!ctx.view().outOfView(level, mob.getBoundingBox()) || !ctx.view().outOfView(level, landing)) {
			return false;
		}
		return Services.mobs().moveOutOfView(mob, to);
	}

	/** Struck by that enderman, or fell (into the void, or onto something far below) right after it hit you. */
	@Override
	public boolean claims(ServerPlayer player, DamageSource source, ArmedTrap armed, AccidentData data, long now) {
		Entity attacker = source.getEntity();
		if (attacker != null) {
			return armed.blames(attacker.getUUID());
		}
		if (!source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.FALL)) {
			return false;
		}
		LivingEntity last = player.getLastHurtByMob();
		LivingEntity credit = player.getKillCredit();
		return last != null && armed.blames(last.getUUID()) || credit != null && armed.blames(credit.getUUID());
	}

	@Override
	public boolean matches(DamageSource source, ArmedTrap armed) {
		Entity attacker = source.getEntity();
		return attacker != null && armed.blames(attacker.getUUID());
	}

	@Override
	public boolean endsWithMob() {
		return true;
	}
}
