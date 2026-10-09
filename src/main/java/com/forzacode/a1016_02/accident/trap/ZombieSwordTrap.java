package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.Scan;
import com.forzacode.a1016_02.accident.StolenGear;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The zombie has your sword (D-032): a weapon or armor stack he took from your chest days ago turns up at night on a
 * zombie that already exists, out of view, 24 to 48 blocks from you ({@code TraceService.equipFromLedger}: guaranteed
 * drop, undamaged, the zombie no longer despawns, so no MobTamper is needed). It walks and fights by the game's own
 * rules; nothing is spawned, buffed or sent at you. It hits harder because it has your gear. Deniable: zombies pick up
 * loot. The clue: it's yours (renamed and enchanted stacks are picked first). Only a death by that zombie while it
 * still holds the stack counts; the list reads "own sword".
 */
public final class ZombieSwordTrap extends BaseTrap {
	public static final String CAUSE = "accident:zombie_has_your_sword";

	public ZombieSwordTrap() {
		super("zombie_has_your_sword", false, "killed", EnumSet.of(Habit.COLLECTOR));
	}

	/** At night, where there is a night (zombies walk the overworld). */
	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel level, AccidentConfig cfg) {
		return !level.dimensionType().hasFixedTime() && Scan.night(level, cfg);
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		AccidentConfig cfg = ctx.cfg();
		List<Candidate> found = new ArrayList<>();
		if (level.dimensionType().hasFixedTime()) {
			return found;
		}
		List<StolenGear.Stolen> stolen = StolenGear.usable(level, ctx.day(), cfg);
		if (stolen.isEmpty()) {
			return found;
		}
		ServerPlayer player = ctx.player();
		Vec3 from = player != null && player.level() == level ? player.position() : Vec3.atCenterOf(ctx.center());
		double min = cfg.swordZombieMinDistance;
		double max = cfg.swordZombieMaxDistance;
		List<Zombie> zombies = new ArrayList<>(level.getEntitiesOfClass(Zombie.class, new AABB(BlockPos.containing(from)).inflate(max), zombie -> {
			double d = zombie.position().distanceTo(from);
			return d >= min && d <= max && wearer(zombie);
		}));
		zombies.sort(Comparator.comparingDouble(zombie -> zombie.position().distanceTo(from)));
		for (StolenGear.Stolen gear : stolen) {
			for (Zombie zombie : zombies) {
				if (!StolenGear.fits(zombie, gear.stack()) || !zombie.getItemBySlot(gear.slot()).isEmpty()) {
					continue;
				}
				BlockPos at = zombie.blockPosition();
				int r = (int) Math.ceil(max);
				found.add(Candidate.of(at, List.of(), at.offset(-r, -r, -r), at.offset(r, r, r), clue(zombie, gear)).withStolen(zombie, gear.entry(), gear.slot()));
				if (found.size() >= cfg.swordMaxCandidates) {
					return found;
				}
			}
		}
		return found;
	}

	/**
	 * A zombie he may give it to: an ordinary one of the night. Alive, not a zombified piglin, not persistent (nobody's
	 * named pet, not one that already picked something up or already wears something of yours), not riding, not held
	 * by another card.
	 */
	static boolean wearer(Zombie zombie) {
		return zombie.isAlive() && !zombie.isRemoved() && !(zombie instanceof ZombifiedPiglin) && !zombie.hasCustomName() && !zombie.isPersistenceRequired()
				&& !zombie.isPassenger() && !Services.mobs().isTampered(zombie);
	}

	private static String clue(Zombie zombie, StolenGear.Stolen gear) {
		TraceLedger.Entry entry = gear.entry();
		String where = gear.held() ? "" : ", moved since to the chest at " + entry.to().map(BaseTrap::at).orElse("?");
		return "A " + zombie.getType().getDescription().getString().toLowerCase(Locale.ROOT) + " near " + at(zombie.blockPosition()) + " wears your "
				+ StolenGear.describe(gear.stack()) + ", gone from your chest at " + at(entry.pos().pos()) + " on day " + entry.day() + where
				+ ". It drops it exactly as it was taken.";
	}

	/** Gives the zombie the stack, only while the zombie (and a chest the stack comes out of) is out of view. */
	@Override
	public boolean setup(TrapContext ctx, Candidate candidate) {
		Mob mob = candidate.mob;
		TraceLedger.Entry entry = candidate.stolen;
		EquipmentSlot slot = candidate.slot;
		ServerLevel level = ctx.level();
		if (mob == null || entry == null || slot == null || !mob.isAlive() || mob.level() != level || !ctx.view().outOfView(level, mob.getBoundingBox())) {
			return false;
		}
		if (entry.kind() == TraceLedger.Kind.MOVE_STACK && entry.to().isPresent() && !ctx.view().outOfView(level, List.of(entry.to().get()))) {
			return false;
		}
		return Services.traces().equipFromLedger(level, entry, mob, slot, CAUSE);
	}

	/** Remembers the stack as it now is on the zombie, and its slot. */
	@Override
	public ArmedTrap onArmed(TrapContext ctx, Candidate candidate, ArmedTrap armed) {
		if (candidate.mob == null || candidate.slot == null) {
			return armed;
		}
		return armed.withWorn(new ArmedTrap.Worn(candidate.mob.getItemBySlot(candidate.slot).copy(), candidate.slot));
	}

	/** Only that zombie, and only while it still holds the stack. */
	@Override
	public boolean matches(DamageSource source, ArmedTrap armed) {
		return source.getEntity() instanceof Mob mob && armed.blames(mob.getUUID()) && holds(mob, armed);
	}

	/** True if the mob still wears the stack he gave it, in the same slot. */
	public static boolean holds(Mob mob, ArmedTrap armed) {
		return mob.isAlive() && armed.worn().map(worn -> StolenGear.same(mob.getItemBySlot(worn.slot()), worn.stack())).orElse(false);
	}

	/** Wherever the zombie catches you. */
	@Override
	public boolean anywhere() {
		return true;
	}

	@Override
	public boolean endsWithMob() {
		return true;
	}

	/** "own sword", "own helmet"... */
	@Override
	public @Nullable String word(DamageSource source, ArmedTrap armed) {
		return armed.worn().map(worn -> StolenGear.kind(worn.stack())).map(kind -> "own " + kind).orElse(null);
	}

	/** Over if the zombie lost the stack (dropped or swapped); a zombie that is not loaded right now is waited for. */
	@Override
	public @Nullable ArmedTrap tick(TrapContext ctx, ArmedTrap armed) {
		for (UUID id : armed.mobs()) {
			Entity entity = ctx.level().getEntity(id);
			if (entity instanceof Mob mob && !holds(mob, armed)) {
				return null;
			}
		}
		return armed;
	}
}
