package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.Scan;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Moved mob: a creeper into your closed house, a drowned into your well, a spider up under your ceiling. Mob got in
 * somehow. The clue: every door is still shut. He never spawns one; he moves one that is already out there, through
 * {@link MobTamper#moveOutOfView}. While MobTamper is the core stub this trap is skipped.
 */
public final class MovedMobTrap extends BaseTrap {
	private static final int HOUSE_RADIUS = 8;
	private static final int DOOR_RADIUS = 10;
	private static final int WELL_RADIUS = 16;

	public MovedMobTrap() {
		super("moved_mob", false, "killed", EnumSet.of(Habit.VISITOR));
	}

	@Override
	public @Nullable String blocked() {
		return Services.mobs() instanceof MobTamper.Stub ? "MobTamper is still the core stub (atmosphere's real one is not merged)" : null;
	}

	@Override
	public boolean matches(DamageSource source, ArmedTrap armed) {
		Entity attacker = source.getEntity();
		return attacker != null && armed.mob().map(id -> id.equals(attacker.getUUID())).orElse(false);
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		AccidentConfig cfg = ctx.cfg();
		BlockPos base = base(ctx);
		List<Candidate> found = new ArrayList<>();
		ServerPlayer player = ctx.player();
		Vec3 playerPos = player != null ? player.position() : Vec3.atCenterOf(ctx.center());
		AABB search = new AABB(base).inflate(cfg.mobSearchRadius);
		String clue = "Every door of the house at " + at(base) + " is still shut. Count the doors.";

		if (doorsShut(level, base)) {
			List<BlockPos> inside = interior(level, base).stream()
					.filter(p -> Vec3.atBottomCenterOf(p).distanceTo(playerPos) >= cfg.mobMinFromPlayer).toList();
			Creeper creeper = nearest(level, Creeper.class, search, base, HOUSE_RADIUS + 2);
			BlockPos spot = fitting(level, creeper, inside);
			if (creeper != null && spot != null) {
				found.add(zone(base, spot, clue + " A creeper got in somehow.").withMob(creeper, spot));
			}
			Spider spider = nearest(level, Spider.class, search, base, HOUSE_RADIUS + 2);
			List<BlockPos> high = new ArrayList<>(inside);
			high.sort(Comparator.comparingInt((BlockPos p) -> -p.getY()));
			BlockPos ceiling = fitting(level, spider, high);
			if (spider != null && ceiling != null) {
				found.add(zone(base, ceiling, clue + " A spider is up under the ceiling.").withMob(spider, ceiling));
			}
		}
		Drowned drowned = nearest(level, Drowned.class, search, base, WELL_RADIUS + 2);
		BlockPos well = well(level, base);
		if (drowned != null && well != null && Vec3.atBottomCenterOf(well).distanceTo(playerPos) >= cfg.mobMinFromPlayer
				&& level.noCollision(drowned, drowned.getDimensions(drowned.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(well)))) {
			found.add(zone(base, well, "A drowned in the well at " + at(well) + ", which has no way in from any water.").withMob(drowned, well));
		}
		return found;
	}

	private static Candidate zone(BlockPos base, BlockPos spot, String clue) {
		int r = WELL_RADIUS;
		return Candidate.of(spot, List.of(), base.offset(-r, -6, -r), base.offset(r, 8, r), clue);
	}

	@Override
	public boolean setup(TrapContext ctx, Candidate candidate) {
		Mob mob = candidate.mob;
		BlockPos to = candidate.mobTo;
		if (mob == null || to == null || !mob.isAlive() || blocked() != null) {
			return false;
		}
		if (!ctx.view().outOfView(ctx.level(), mob.getBoundingBox()) || !ctx.view().outOfView(ctx.level(), List.of(to, to.above()))) {
			return false;
		}
		return Services.mobs().moveOutOfView(mob, to);
	}

	/** The nearest living mob of this kind that is not already within {@code notWithin} of the base. */
	static <T extends Mob> @Nullable T nearest(ServerLevel level, Class<T> type, AABB search, BlockPos base, int notWithin) {
		Vec3 center = Vec3.atCenterOf(base);
		return level.getEntitiesOfClass(type, search, mob -> mob.isAlive() && !mob.isPersistenceRequired() && mob.position().distanceTo(center) > notWithin)
				.stream().min(Comparator.comparingDouble(mob -> mob.position().distanceTo(center))).orElse(null);
	}

	/** The first spot where the mob's box fits. */
	static @Nullable BlockPos fitting(ServerLevel level, @Nullable Mob mob, List<BlockPos> spots) {
		if (mob == null) {
			return null;
		}
		for (BlockPos spot : spots) {
			if (level.noCollision(mob, mob.getDimensions(mob.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(spot)))) {
				return spot;
			}
		}
		return null;
	}

	/** Floor cells inside the house: two open cells over a solid floor, roofed, no sky. */
	static List<BlockPos> interior(ServerLevel level, BlockPos base) {
		List<BlockPos> cells = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-HOUSE_RADIUS, -3, -HOUSE_RADIUS), base.offset(HOUSE_RADIUS, 3, HOUSE_RADIUS))) {
			if (Scan.open(level, pos) && Scan.open(level, pos.above()) && Scan.fullSolid(level, pos.below()) && !level.canSeeSky(pos) && roofed(level, pos)) {
				cells.add(pos.immutable());
			}
		}
		cells.sort(Comparator.comparingDouble(p -> p.distSqr(base)));
		return cells;
	}

	private static boolean roofed(ServerLevel level, BlockPos pos) {
		for (int dy = 2; dy <= 6; dy++) {
			if (Scan.fullSolid(level, pos.above(dy))) {
				return true;
			}
		}
		return false;
	}

	/** At least one door near the base, and every one of them closed. */
	static boolean doorsShut(ServerLevel level, BlockPos base) {
		int doors = 0;
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-DOOR_RADIUS, -3, -DOOR_RADIUS), base.offset(DOOR_RADIUS, 3, DOOR_RADIUS))) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				if (state.getValue(DoorBlock.OPEN)) {
					return false;
				}
				doors++;
			}
		}
		return doors > 0;
	}

	/** The bottom of a small, deep pool near the base (a well), or null. */
	static @Nullable BlockPos well(ServerLevel level, BlockPos base) {
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-WELL_RADIUS, -4, -WELL_RADIUS), base.offset(WELL_RADIUS, 4, WELL_RADIUS))) {
			if (!Scan.waterSource(level, pos) || Scan.water(level, pos.above()) || !Scan.waterSource(level, pos.below())) {
				continue;
			}
			if (FloodedTunnelTrap.sources(level, pos) > 12) {
				continue;
			}
			BlockPos bottom = pos.immutable();
			while (Scan.water(level, bottom.below())) {
				bottom = bottom.below();
			}
			return bottom;
		}
		return null;
	}
}
