package com.forzacode.a1016_02.atmosphere.sc2.s24;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.sc.ScGates;
import com.forzacode.a1016_02.atmosphere.sc2.s21.FitMemo;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Herds;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Sc2xData;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code sc_24} (docs/SELF_CREATED_2.md 24; AMBIENT, Traces, MOB + UNSHARED, WATCHER + VISITOR, no fake): a cow, pig,
 * sheep or chicken within 48 of the target (out of their view if one is) follows them for an in-game day
 * ({@link Follows}). UNSHARED: only the target is followed and only they feel it; a friend sees it follow the target,
 * never them. At most {@code maxPerWorld}, {@code minDaysApart} apart, one at a time; a forced fire skips the caps.
 */
public final class FollowerCard implements EventCard {
	public static final String ID = "sc_24";

	public Supplier<PlayerWatch> watch = Services::watch;
	public Predicate<ServerPlayer> outdoors = Herds::outdoors;
	public ToLongFunction<ServerPlayer> sinceCombat = player -> Services.watch().ticksSinceCombat(player);
	public Follows follows = Follows.LIVE;
	public boolean keepsCaps = true;

	/** The last answer per player, kept two seconds (the director looks often). */
	private final FitMemo memo = new FitMemo();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Tier tier() {
		return Tier.AMBIENT;
	}

	@Override
	public Stage earliestStage() {
		return Stage.TRACES;
	}

	@Override
	public Set<Habit> habits() {
		return Set.of(Habit.WATCHER, Habit.VISITOR);
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.MOB, CardTag.UNSHARED);
	}

	@Override
	public boolean hasFake() {
		return false;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return memo.fits(player, () -> refusal(player, false).isEmpty());
	}

	public Optional<String> refusal(ServerPlayer player, boolean forced) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		Optional<String> story = ScGates.why(server);
		if (story.isPresent()) {
			return story;
		}
		if (!ScGates.overworld(level)) {
			return Optional.of("not in the overworld");
		}
		if (!ScGates.noCombat(sinceCombat.applyAsLong(player))) {
			return Optional.of("in combat lately");
		}
		if (!outdoors.test(player)) {
			return Optional.of("not outdoors");
		}
		if (!forced) {
			Sc24Config cfg = Sc24Config.get();
			Sc2xData.Caps caps = Sc2xData.get(server).caps("24");
			long day = GameClock.day(server);
			if (caps.count() >= cfg.maxPerWorld) {
				return Optional.of(caps.count() + " already (at most " + cfg.maxPerWorld + ")");
			}
			if (caps.lastDay() >= 0 && day - caps.lastDay() < cfg.minDaysApart) {
				return Optional.of("the last was on day " + caps.lastDay() + " (" + cfg.minDaysApart + " days apart)");
			}
		}
		if (follows.running()) {
			return Optional.of("one runs already");
		}
		if (MobTamperImpl.INSTANCE.steeredCount() >= MobTamperImpl.MAX_STEERED) {
			return Optional.of("too many mobs are steered now");
		}
		return candidate(player).isPresent() ? Optional.empty() : Optional.of("no cow, pig, sheep or chicken within " + (int) Sc24Config.FIND_RADIUS);
	}

	/**
	 * The animal (R10-10, R11-14): one of the player's own first (a free cow, pig, sheep or chicken within
	 * {@link Follows#BASE_NEAR} of their base, loaded and out of everyone's view: something came back with them), the one
	 * easiest to know again first; else a free one of the kinds within 48 of them, the one easiest to know again, out of
	 * their view, nearest the middle of the band first.
	 */
	public Optional<Mob> candidate(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 at = player.position();
		Followed self = Followed.player(level.getServer(), player.getUUID(), level, watch);
		Optional<Vec3> home = self.base().map(Vec3::atBottomCenterOf);
		if (home.isPresent() && level.isLoaded(BlockPos.containing(home.get()))) {
			Vec3 h = home.get();
			double b = Follows.BASE_NEAR;
			List<Mob> own = level.getEntitiesOfClass(Mob.class, new AABB(h, h).inflate(b, 16.0, b), mob -> Herds.FOLLOWER_KINDS.contains(mob.getType())
					&& Herds.free(mob) && Herds.flatSqr(mob.position(), h) <= b * b && outOfView.test(level, mob.getBoundingBox()));
			Mob best = mostRecognisable(own, h);
			if (best != null) {
				return Optional.of(best);
			}
		}
		double r = Sc24Config.FIND_RADIUS;
		Sc24Config cfg = Sc24Config.get();
		double middle = (cfg.followMin + cfg.followMax) / 2.0;
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(r, 16.0, r), mob -> Herds.FOLLOWER_KINDS.contains(mob.getType())
				&& Herds.free(mob) && Herds.flatSqr(mob.position(), at) <= r * r);
		return mobs.stream().min(Comparator.<Mob>comparingInt(mob -> -recognisable(mob, mobs))
				.thenComparingInt(mob -> self.sees(mob.getBoundingBox()) ? 1 : 0)
				.thenComparingDouble(mob -> Math.abs(Math.sqrt(Herds.flatSqr(mob.position(), at)) - middle)));
	}

	/** Whether a box is out of everyone's view (the player's own animal is taken unseen). A test swaps in its own. */
	public java.util.function.BiPredicate<ServerLevel, AABB> outOfView = (level, box) -> com.forzacode.a1016_02.core.Services.traces().isOutOfView(level, box);

	/** Of these, the one easiest to know again ({@link #recognisable}), then the nearest {@code near}; null if none. */
	static @org.jspecify.annotations.Nullable Mob mostRecognisable(List<Mob> mobs, Vec3 near) {
		return mobs.stream().min(Comparator.<Mob>comparingInt(mob -> -recognisable(mob, mobs)).thenComparingDouble(mob -> mob.position().distanceToSqr(near)))
				.orElse(null);
	}

	/**
	 * Pure-ish (R11-14): how easily a player knows this animal again, 0 to 2: a sheep of a colour other than white 2; an
	 * animal whose look (its colour or variant) differs from most of its kind among {@code among} (the local biome's) 1;
	 * else 0.
	 */
	public static int recognisable(Mob mob, List<Mob> among) {
		if (mob instanceof Sheep sheep && sheep.getColor() != DyeColor.WHITE) {
			return 2;
		}
		Object mine = look(mob);
		java.util.Map<Object, Integer> counts = new java.util.HashMap<>();
		for (Mob other : among) {
			if (other.getType() == mob.getType()) {
				counts.merge(look(other), 1, Integer::sum);
			}
		}
		Object usual = counts.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(mine);
		return counts.getOrDefault(mine, 0) < counts.getOrDefault(usual, 0) ? 1 : 0;
	}

	/** Its look: a sheep's colour, a cow's, pig's or chicken's variant, else its kind. */
	static Object look(Mob mob) {
		if (mob instanceof Sheep sheep) {
			return sheep.getColor();
		}
		Object variant = mob.get(DataComponents.COW_VARIANT);
		if (variant == null) {
			variant = mob.get(DataComponents.PIG_VARIANT);
		}
		if (variant == null) {
			variant = mob.get(DataComponents.CHICKEN_VARIANT);
		}
		return variant != null ? variant : mob.getType();
	}

	@Override
	public FireResult fire(FireContext ctx) {
		memo.clear();
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		if (refusal(player, ctx.forced()).isPresent()) {
			return FireResult.SKIPPED;
		}
		Optional<Mob> mob = candidate(player);
		if (mob.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		Followed target = Followed.player(level.getServer(), player.getUUID(), level, watch);
		if (!follows.start(mob.get(), target, Sc24Config.get().days, false)) {
			return FireResult.NO_SPOT;
		}
		if (!ctx.forced() && keepsCaps) {
			Sc2xData data = Sc2xData.get(level.getServer());
			data.setCaps("24", data.caps("24").plus(GameClock.day(level.getServer()), -1L));
		}
		A1016_02.LOGGER.info("[a1016] sc_24: fired");
		return FireResult.FIRED;
	}
}
