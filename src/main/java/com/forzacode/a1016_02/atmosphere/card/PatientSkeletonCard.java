package com.forzacode.a1016_02.atmosphere.card;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Gates;
import com.forzacode.a1016_02.atmosphere.Tasks;
import com.forzacode.a1016_02.atmosphere.WorldScan;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.phys.Vec3;

/**
 * The patient skeleton: at dusk, a skeleton at the fog's edge that doesn't shoot. It stands still facing the player
 * and keeps the same distance (it is only moved while both it and its new spot are out of view), then is gone: moved
 * far off out of view. If that never works out, it is simply released and is an ordinary skeleton again.
 */
public final class PatientSkeletonCard extends AtmosphereCard {
	public static final String ID = "patient_skeleton";
	private static final int SLACK = 8;
	private static final int CLOSE = 6;
	private static final int GONE_DISTANCE = 72;
	/** Ticks between two renewals of the hold (see {@link AtmosphereConfig#skeletonHoldTicks}). */
	private static final int RENEW_TICKS = 10;

	public PatientSkeletonCard() {
		super(ID, Tier.MINOR, Stage.PROXIMITY, Set.of(Habit.WATCHER), Set.of(CardTag.MOB), false);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		AtmosphereConfig cfg = cfg();
		return Gates.hasDayCycle(world) && Gates.inWindow(Gates.timeOfDay(world), cfg.duskFrom, cfg.duskTo) && !skeletons(world, player).isEmpty();
	}

	private static List<Skeleton> skeletons(ServerLevel level, ServerPlayer player) {
		AtmosphereConfig cfg = cfg();
		double min2 = (double) cfg.skeletonMinDistance * cfg.skeletonMinDistance;
		return untampered(level, Skeleton.class, player.position(), cfg.skeletonRadius,
				s -> s.distanceToSqr(player) >= min2 && !s.isPassenger() && !s.isVehicle() && !s.isLeashed());
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerPlayer player = ctx.player();
		Skeleton skeleton = skeletons(ctx.level(), player).stream().min(Comparator.comparingDouble(s -> s.distanceToSqr(player))).orElse(null);
		if (skeleton == null) {
			return FireResult.NO_SPOT;
		}
		double keep = Math.sqrt(skeleton.distanceToSqr(player));
		if (!hold(skeleton, player)) {
			return FireResult.SKIPPED;
		}
		AtmosphereConfig cfg = cfg();
		Tasks.start(new Follow(player.getUUID(), skeleton, keep, AtmosphereConfig.ticks(cfg.skeletonFollowSeconds),
				AtmosphereConfig.ticks(cfg.skeletonGoneTriesSeconds)));
		return FireResult.FIRED;
	}

	private static boolean hold(Skeleton skeleton, ServerPlayer player) {
		int ticks = Math.max(2 * RENEW_TICKS, cfg().skeletonHoldTicks);
		return mobs().freeze(skeleton, ticks) && mobs().face(skeleton, player.getEyePosition(), ticks) && mobs().silence(skeleton, ticks);
	}

	private static final class Follow implements Tasks.Episode {
		private final UUID player;
		private final Skeleton skeleton;
		private final double keep;
		private final int length;
		private final int goneTries;
		private int age;

		Follow(UUID player, Skeleton skeleton, double keep, int length, int goneTries) {
			this.player = player;
			this.skeleton = skeleton;
			this.keep = keep;
			this.length = length;
			this.goneTries = goneTries;
		}

		@Override
		public boolean tick(MinecraftServer server) {
			age++;
			ServerPlayer subject = server.getPlayerList().getPlayer(player);
			if (subject == null || !skeleton.isAlive() || subject.level() != skeleton.level() || skeleton.hurtTime > 0
					|| skeleton.distanceToSqr(subject) < CLOSE * CLOSE) {
				mobs().release(skeleton);
				return false;
			}
			ServerLevel level = (ServerLevel) skeleton.level();
			if (age % RENEW_TICKS == 0) {
				hold(skeleton, subject);
			}
			if (age <= length) {
				if (age % 10 == 5 && Math.abs(Math.sqrt(skeleton.distanceToSqr(subject)) - keep) > SLACK) {
					moveTo(level, subject, keep);
				}
				return true;
			}
			// Done: gone, out of view, far away.
			if (age % 10 == 5 && moveTo(level, subject, GONE_DISTANCE)) {
				mobs().release(skeleton);
				return false;
			}
			if (age > length + goneTries) {
				mobs().release(skeleton);
				return false;
			}
			return true;
		}

		/** Moves the skeleton to {@code distance} from the player, on its current bearing. Only when out of view. */
		private boolean moveTo(ServerLevel level, ServerPlayer subject, double distance) {
			if (!Services.traces().isOutOfView(level, skeleton.getBoundingBox())) {
				return false;
			}
			Vec3 from = subject.position();
			double angle = Math.atan2(skeleton.getZ() - from.z, skeleton.getX() - from.x);
			BlockPos spot = WorldScan.surfaceSpotToward(level, from, angle, distance, 2);
			return spot != null && mobs().moveOutOfView(skeleton, spot);
		}

		@Override
		public void stop(MinecraftServer server) {
			mobs().release(skeleton);
		}
	}
}
