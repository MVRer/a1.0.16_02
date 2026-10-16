package com.forzacode.a1016_02.atmosphere.card;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.atmosphere.AtmosphereData;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.sc.Sc07Config;
import com.forzacode.a1016_02.atmosphere.sc.ScGates;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.world.Bases;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * {@code sc_07} (docs/SELF_CREATED.md 07; MINOR, Proximity, MOB, COLLECTOR + STRIPPER, no fake). A mob with a custom
 * name within {@code baseRadius} of a known base ({@code world.Bases}: everyone's, offline friends' too) in the
 * target's level loses it ({@code MobTamper.unname}, cause {@link #CAUSE}, ledgered NAME; Ending D gives it back): not
 * riding or ridden, nothing holding it, its box out of everyone's view, nobody within {@code nobodyWithin}, the target
 * at least {@code awayDistance} from it or asleep, and never while its owner is online within
 * {@link #OWNER_NEAR} of it. A pet the target owns first, then the one nearest the target's base. At most
 * {@code maxPerWorld}, {@code minDaysApart} in-game days apart, world-wide (saved in the tick it happens). A forced fire
 * skips the world's caps, never a mob's rules.
 */
public final class NameGoneCard extends AtmosphereCard {
	public static final String ID = "sc_07";
	public static final String CAUSE = "atmosphere:sc07";
	/** Never while the mob's owner is online this close to it. */
	public static final double OWNER_NEAR = 48.0;

	/** The world's record (a test gives its own). */
	Function<MinecraftServer, AtmosphereData> data = AtmosphereData::get;
	/** Today's in-game day ({@code GameClock.day}). */
	ToLongFunction<MinecraftServer> day = GameClock::day;
	/** Every known base in a dimension ({@code world.Bases.all}). */
	BiFunction<MinecraftServer, ResourceKey<Level>, List<BlockPos>> bases = (server, dimension) -> Bases.live(server).all(dimension);

	/** One named mob near a base and why it may not lose its name (empty if it may). */
	public record Candidate(Mob mob, boolean owned, double toBase, Optional<String> refusal) {
	}

	public NameGoneCard() {
		super(ID, Tier.MINOR, Stage.PROXIMITY, Set.of(Habit.COLLECTOR, Habit.STRIPPER), Set.of(CardTag.MOB), false);
	}

	/** Why the world's caps refuse a name today, or empty. */
	public static Optional<String> caps(AtmosphereData.Sc07 record, long today, Sc07Config cfg) {
		if (record.count() >= cfg.maxPerWorld) {
			return Optional.of(record.count() + " of " + cfg.maxPerWorld + " in this world");
		}
		if (record.lastDay() >= 0 && today - record.lastDay() < cfg.minDaysApart) {
			return Optional.of((today - record.lastDay()) + " days since the last (at least " + cfg.minDaysApart + ")");
		}
		return Optional.empty();
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return refusal(player, world).isEmpty() && candidates(player, world).stream().anyMatch(c -> c.refusal().isEmpty());
	}

	/** The card's gates for this player now: the story, the overworld, combat and the world's caps. */
	public Optional<String> refusal(ServerPlayer player, ServerLevel level) {
		MinecraftServer server = level.getServer();
		Optional<String> story = ScGates.why(server);
		if (story.isPresent()) {
			return story;
		}
		if (!ScGates.overworld(level)) {
			return Optional.of("not in the overworld");
		}
		if (!ScGates.noCombat(watch.get().ticksSinceCombat(player))) {
			return Optional.of("in combat lately");
		}
		return caps(data.apply(server).sc07(), day.applyAsLong(server), Sc07Config.get());
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		MinecraftServer server = level.getServer();
		if (!ScGates.storyAllows(server) || !ScGates.overworld(level)) {
			return FireResult.SKIPPED;
		}
		if (!ctx.forced() && refusal(player, level).isPresent()) {
			return FireResult.SKIPPED;
		}
		for (Candidate candidate : candidates(player, level)) {
			if (candidate.refusal().isEmpty() && MobTamperImpl.INSTANCE.unname(candidate.mob(), CAUSE, outOfView)) {
				data.apply(server).recordSc07(day.applyAsLong(server));
				return FireResult.FIRED;
			}
		}
		return FireResult.NO_SPOT;
	}

	/**
	 * Every named mob within {@code baseRadius} of a known base in the target's level, each with why not; the ones that
	 * may lose their name first, a pet the target owns before the rest, then nearest the target's base.
	 */
	public List<Candidate> candidates(ServerPlayer target, ServerLevel level) {
		MinecraftServer server = level.getServer();
		Sc07Config cfg = Sc07Config.get();
		List<BlockPos> known = bases.apply(server, level.dimension());
		Optional<BlockPos> home = watch.get().base(target).filter(b -> b.dimension() == level.dimension()).map(GlobalPos::pos);
		Set<UUID> seen = new HashSet<>();
		List<Candidate> candidates = new ArrayList<>();
		double radius = cfg.baseRadius;
		for (BlockPos base : known) {
			Vec3 center = Vec3.atCenterOf(base);
			AABB box = new AABB(center, center).inflate(radius);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.hasCustomName() && m.distanceToSqr(center) <= radius * radius)) {
				if (!seen.add(mob.getUUID())) {
					continue;
				}
				UUID owner = owner(mob);
				boolean owned = target.getUUID().equals(owner);
				BlockPos from = home.orElse(base);
				candidates.add(new Candidate(mob, owned, Math.sqrt(mob.blockPosition().distSqr(from)), why(mob, owner, target, level, cfg)));
			}
		}
		candidates.sort(Comparator.<Candidate>comparingInt(c -> c.refusal().isEmpty() ? 0 : 1).thenComparingInt(c -> c.owned() ? 0 : 1)
				.thenComparingDouble(Candidate::toBase));
		return candidates;
	}

	/** Why this named mob may not lose its name now, or empty. */
	private Optional<String> why(Mob mob, @Nullable UUID owner, ServerPlayer target, ServerLevel level, Sc07Config cfg) {
		if (mob.isPassenger() || mob.isVehicle()) {
			return Optional.of("riding or ridden");
		}
		if (MobTamperImpl.INSTANCE.isTampered(mob)) {
			return Optional.of("held by something else");
		}
		if (!outOfView.test(level, mob.getBoundingBox())) {
			return Optional.of("in view");
		}
		if (!farFromEveryone(level, mob.blockPosition(), cfg.nobodyWithin)) {
			return Optional.of("someone within " + cfg.nobodyWithin);
		}
		if (!target.isSleeping() && target.distanceToSqr(mob) < (double) cfg.awayDistance * cfg.awayDistance) {
			return Optional.of("the player is within " + cfg.awayDistance + " and awake");
		}
		if (owner != null) {
			for (ServerPlayer player : everyone.apply(level)) {
				if (player.getUUID().equals(owner) && player.distanceToSqr(mob) <= OWNER_NEAR * OWNER_NEAR) {
					return Optional.of("its owner is near");
				}
			}
		}
		return Optional.empty();
	}

	/** The mob's owner, if it is a tamed or owned animal. */
	static @Nullable UUID owner(Mob mob) {
		if (mob instanceof OwnableEntity ownable) {
			EntityReference<?> reference = ownable.getOwnerReference();
			return reference == null ? null : reference.getUUID();
		}
		return null;
	}

	/** {@code /a1016 sc 07}: the caps, then each named mob near a base and why it is refused. */
	public List<String> describe(ServerPlayer target) {
		ServerLevel level = target.level();
		List<String> lines = new ArrayList<>();
		AtmosphereData.Sc07 record = data.apply(level.getServer()).sc07();
		lines.add(String.format(Locale.ROOT, "%d taken, last on day %d; today %d; now: %s", record.count(), record.lastDay(), day.applyAsLong(level.getServer()),
				refusal(target, level).orElse("the card's gates hold")));
		List<Candidate> candidates = candidates(target, level);
		if (candidates.isEmpty()) {
			lines.add("no named mob near a known base");
		}
		for (Candidate c : candidates) {
			lines.add(String.format(Locale.ROOT, "%s at %d %d %d%s, %.0f from the base: %s", c.mob().getType().getDescription().getString(), c.mob().getBlockX(),
					c.mob().getBlockY(), c.mob().getBlockZ(), c.owned() ? " (the player's)" : "", c.toBase(), c.refusal().orElse("may")));
		}
		return lines;
	}
}
