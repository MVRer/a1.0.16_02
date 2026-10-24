package com.forzacode.a1016_02.accident.sc2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.Scan;
import com.forzacode.a1016_02.accident.sc.ScGates;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.ScLog;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import org.jspecify.annotations.Nullable;

/**
 * The rules the second wave's two trap kinds share (docs/SELF_CREATED_2.md, 25 and 26, "Fair danger", D-097), and the
 * scenes they run. The fair-danger floors here are constants a config may only make stricter: nothing turns hostile
 * before {@link #TOLD_TICKS} of tells, two of them audible at 16 blocks or more, at least one instrument seen by the
 * target as they perceive it ({@link #perceive}: at night within 20, in a fog 0.6 of the fog end their client draws) and
 * one plainly seen ({@link #plain}: the middle of their view, within 20; R10-5, R10-12); nothing starts under
 * {@link #MIN_HEALTH} health, without a way out ({@link #wayOut}), within
 * {@link #FROM_BASE} of the target's base (a forced debug arm may relax the base), with anyone within {@link #ALONE}
 * of the target (spectators count), or within {@link #APART} of another running one.
 *
 * <p>Scenes ({@link HuntScene}) are kept here by their target's UUID (one per player: a player has one armed trap) and
 * stepped every {@link #STEP} ticks from the server tick; they live in memory only. A server stop forgets them (their
 * mobs' effects are never saved); the switch going off ends them at once.
 */
public final class Hunts {
	/** D-097.1: at least this long of tells (60 s) before anything of a scene turns hostile. Never shortened by a config. */
	public static final int TOLD_TICKS = 1200;
	/** D-097.3: nothing starts under this health. */
	public static final float MIN_HEALTH = 14.0F;
	/** D-097.6: nothing starts this close to the target's base (blocks, horizontal). */
	public static final int FROM_BASE = 64;
	/** D-098, the alone rule: nobody else within this of the target (3D), spectators and the dead included. */
	public static final double ALONE = 48.0;
	/** D-097.5: never two scenes within this of each other (blocks). */
	public static final double APART = 128.0;
	/** How often a scene looks (ticks). */
	public static final int STEP = 10;
	/** A tell this loud or louder is heard at 16 blocks or more (vanilla's range: max(volume, 1) x 16). */
	public static final float AUDIBLE = 1.0F;
	/** The cone (degrees) in which the target counts as seeing an instrument. */
	public static final double SEEN_CONE = 90.0;
	/** Sky light from which a player counts as outdoors, and under which (with a roof) as covered. */
	public static final int OPEN_SKY = 10;
	/** A player must not have fought for this long (seconds), as the layer's common gates ask. */
	public static final double NO_COMBAT_SECONDS = ScGates.NO_COMBAT_SECONDS;
	/** D-097.3 at the turn (R12-8, R13-13): a told scene never turns on a player under this health. */
	public static final float TURN_HEALTH = 10.0F;
	/** At the turn: a player who fought something else this recently (ticks, 5 s) is not turned on yet. */
	public static final int TURN_NO_COMBAT_TICKS = 100;
	/**
	 * R12-6: a death its own did not deal still counts as a trap's when one of them hurt the victim this recently (ticks,
	 * vanilla's own memory of who hurt a mob): the fall, the lava or the water they drove the player into.
	 */
	public static final int CREDIT_TICKS = 100;
	/**
	 * R10-5, R10-12: "seen" is what a player perceives, not geometry. A mob standing in the dark (light under
	 * {@link #DIM_LIGHT} where its head is: night, the sky's light after the time of day) counts as seen no farther than this
	 * (blocks): a wolf or a zombie at 20 is a shape at night at render distance 12 under the mod's haze, at 36 a few grey
	 * pixels.
	 */
	public static final double NIGHT_SIGHT = 20.0;
	/** Under this light (block light, or the sky's after the time of day) where its head is, a mob stands in the dark. */
	public static final int DIM_LIGHT = 8;
	/** In a fog that counts, a mob counts as seen no farther than this share of the fog end the target's client draws. */
	public static final double FOG_SIGHT = 0.6;
	/** Without a fog, the share of the view's fog end (the render distance and the haze) inside which one counts as seen. */
	public static final double CLEAR_SIGHT = 0.85;
	/**
	 * R10-5: before anything turns, one instrument must have been plainly seen: through the middle third of the view (this
	 * cone, degrees, the middle third of a 70 degree view across a 16:9 screen), within {@link #PLAIN_DISTANCE} and within
	 * what the target perceives ({@link #perceive}).
	 */
	public static final double PLAIN_CONE = 40.0;
	public static final double PLAIN_DISTANCE = 20.0;
	/** The gallery's stand-in has no client: its fog end is the server's estimate for this render distance (chunks). */
	static final int STAND_IN_CHUNKS = 12;

	private static final Map<UUID, HuntScene> SCENES = new LinkedHashMap<>();

	private Hunts() {
	}

	// --- scenes ---

	/** Starts keeping this scene (a running one for the same target is stopped first, at once). */
	public static void start(HuntScene scene) {
		HuntScene old = SCENES.put(scene.target(), scene);
		if (old != null && old != scene) {
			old.stop(true, "replaced");
		}
	}

	/** The running scene for this target, if any. */
	public static Optional<HuntScene> of(UUID target) {
		return Optional.ofNullable(SCENES.get(target));
	}

	/** Every running scene (a copy). */
	public static List<HuntScene> running() {
		return new ArrayList<>(SCENES.values());
	}

	/** Every server tick: each scene looks when its step is due; a finished one is let go. */
	public static void tick(MinecraftServer server) {
		if (SCENES.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		for (HuntScene scene : running()) {
			if (scene.level().getServer() != server) {
				continue;
			}
			try {
				scene.flush(now);
				if (now >= scene.nextStep()) {
					scene.step(now);
				}
			} catch (RuntimeException e) {
				ScLog.error("[a1016] accident: {} failed", scene.kind(), e);
				scene.stop(true, "failed");
			}
			if (scene.done()) {
				SCENES.remove(scene.target(), scene);
			}
		}
	}

	/** A mob died: the scene it belongs to counts it. */
	public static void onMobDied(Entity entity) {
		if (SCENES.isEmpty()) {
			return;
		}
		for (HuntScene scene : running()) {
			scene.died(entity.getUUID());
		}
	}

	/** Ends every scene of this kind (null: every kind) at once: its mobs calmed and released where they are. */
	public static void endAll(@Nullable String kind, String why) {
		for (HuntScene scene : running()) {
			if (kind == null || scene.kind().equals(kind)) {
				scene.stop(true, why);
				SCENES.remove(scene.target(), scene);
			}
		}
	}

	/** Server stop: forgets every scene (nothing of theirs is saved). */
	public static void clear() {
		SCENES.clear();
	}

	/** Why a scene may not start at {@code at} for {@code target}: another running within {@link #APART}, or null. */
	public static @Nullable String tooNear(ServerLevel level, Vec3 at, UUID target) {
		for (HuntScene scene : running()) {
			if (!scene.done() && !scene.target().equals(target) && scene.level() == level && scene.where().distanceTo(at) < APART) {
				return "another is running " + (int) scene.where().distanceTo(at) + " away (under " + (int) APART + ")";
			}
		}
		return null;
	}

	// --- the shared gate ---

	/** What the shared gate reads; each trap kind builds it from its own environment. */
	public record Gate(PlayerWatch watch, Function<ServerLevel, List<ServerPlayer>> listeners, HerobrineState state, boolean enabled, float minHealth,
			int fromBase, boolean forced) {
	}

	/**
	 * Why nothing of the kind may start for this player now, before its instruments are looked for, or null: the layer
	 * off, the story's gates, not the overworld, not a living participant, combat in the last 30 s, under the health
	 * floor, no way out, not outdoors (sky light {@link #OPEN_SKY}+), not on foot, near their base (relaxed when forced),
	 * not alone, another scene within {@link #APART}.
	 */
	public static @Nullable String gate(ServerPlayer player, Gate gate) {
		if (!gate.enabled()) {
			return "the layer is switched off";
		}
		String story = ScGates.storyBlock(gate.state());
		if (story != null) {
			return story;
		}
		if (!player.level().dimension().equals(Level.OVERWORLD)) {
			return "not in the overworld";
		}
		if (!player.isAlive() || player.isRemoved() || player.isSpectator() || player.isCreative()) {
			return "not someone it can be for now";
		}
		if (!ScGates.noCombat(gate.watch().ticksSinceCombat(player))) {
			return "combat in the last 30 s";
		}
		float health = Math.max(gate.minHealth(), MIN_HEALTH);
		if (player.getHealth() < health) {
			return "health " + (int) Math.ceil(player.getHealth()) + ", under " + (int) health;
		}
		if (!wayOut(player)) {
			return "carries no way out (2 full blocks, or a pickaxe, shovel or axe)";
		}
		if (!outdoors(player)) {
			return "not outdoors";
		}
		if (!onFoot(player)) {
			return "not on foot";
		}
		if (!gate.forced()) {
			int from = Math.max(gate.fromBase(), FROM_BASE);
			double base = baseDistance(gate.watch(), player);
			if (base < from) {
				return "within " + from + " of their base (" + (int) base + ")";
			}
		}
		if (!SoundCues.alone(player, ALONE, gate.watch(), gate.listeners().apply(player.level()))) {
			return "not alone (someone within " + (int) ALONE + ")";
		}
		return tooNear(player.level(), player.position(), player.getUUID());
	}

	// --- rules ---

	/**
	 * D-097.2: the player carries a way out right now: at least two placeable full blocks (any mix), or a pickaxe, a
	 * shovel or an axe.
	 */
	public static boolean wayOut(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		int blocks = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.AXES)) {
				return true;
			}
			if (stack.getItem() instanceof BlockItem item && fullBlock(item.getBlock())) {
				blocks += stack.getCount();
				if (blocks >= 2) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * R12-7, the call's way out: a pickaxe or a shovel, to dig in and cover over. Two blocks up stop no arrow and no spider,
	 * and an axe digs no ground.
	 */
	public static boolean digOut(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Why a told scene may not turn on its target now (R12-8, R13-13: the start's floors asked again at the turn), or null:
	 * under {@link #TURN_HEALTH}, no way out any more ({@code wayOut}: the kind's own), within {@link #FROM_BASE} of their
	 * base (unless {@code baseRelaxed}, a forced debug arm), or, before its first hostile act ({@code first}), fighting
	 * something else in the last {@link #TURN_NO_COMBAT_TICKS}. A scene that hears a reason holds off and goes if it lasts.
	 * The gallery's stand-in has none.
	 */
	public static @Nullable String turnRefusal(Prey prey, PlayerWatch watch, Predicate<ServerPlayer> wayOut, boolean first, boolean baseRelaxed) {
		ServerPlayer player = prey.player();
		if (player == null) {
			return null;
		}
		if (player.getHealth() < TURN_HEALTH) {
			return "health " + (int) Math.ceil(player.getHealth()) + ", under " + (int) TURN_HEALTH;
		}
		if (!wayOut.test(player)) {
			return "no way out any more";
		}
		if (!baseRelaxed) {
			double base = baseDistance(watch, player);
			if (base < FROM_BASE) {
				return "within " + FROM_BASE + " of their base (" + (int) base + ")";
			}
		}
		if (first && watch.ticksSinceCombat(player) < TURN_NO_COMBAT_TICKS) {
			return "fighting";
		}
		return null;
	}

	/**
	 * R12-6: a death of {@code victim} its own did not deal (a fall, lava, drowning: no entity behind the damage) is still
	 * the trap's when one of its own hurt them within {@link #CREDIT_TICKS} (vanilla's memory of who last hurt a mob).
	 */
	public static boolean credited(ServerPlayer victim, DamageSource source, ArmedTrap armed) {
		if (source.getEntity() != null) {
			return false;
		}
		LivingEntity last = victim.getLastHurtByMob();
		return last != null && armed.blames(last.getUUID()) && victim.tickCount - victim.getLastHurtByMobTimestamp() <= CREDIT_TICKS;
	}

	/** A block a player can stand on top of and build up with: a full cube (sand and gravel stack too), no block entity, not leaves. */
	static boolean fullBlock(Block block) {
		BlockState state = block.defaultBlockState();
		return state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !state.hasBlockEntity()
				&& !state.is(BlockTags.LEAVES);
	}

	/** Night in the overworld (13000 to 23000), or a fog spell that counts there now (D-071). */
	public static boolean dark(ServerLevel level) {
		ServerLevel overworld = level.getServer().overworld();
		long time = Scan.timeOfDay(overworld);
		if (time >= 13000 && time < 23000) {
			return true;
		}
		return fog(overworld);
	}

	/** A fog spell counts in the overworld now (its amount at least half its level). */
	public static boolean fog(ServerLevel level) {
		ServerLevel overworld = level.getServer().overworld();
		Optional<FogSpell> spell = HerobrineState.get(overworld.getServer()).effects().fogSpell();
		return spell.isPresent() && spell.get().counts(overworld.getDefaultClockTime());
	}

	/** Night only (13000 to 23000), overworld time. */
	public static boolean night(ServerLevel level) {
		long time = Scan.timeOfDay(level.getServer().overworld());
		return time >= 13000 && time < 23000;
	}

	/** Under open sky: sky light {@link #OPEN_SKY} or more where their head is. */
	public static boolean outdoors(ServerPlayer player) {
		return skyLight(player) >= OPEN_SKY;
	}

	/** Sky light at the player's head block (the raw sky light, not the time of day's). */
	public static int skyLight(ServerPlayer player) {
		return player.level().getBrightness(LightLayer.SKY, BlockPos.containing(player.getEyePosition()));
	}

	/** On their own feet: not riding, not gliding. */
	public static boolean onFoot(ServerPlayer player) {
		return !player.isPassenger() && !player.isFallFlying();
	}

	/** Under a roof, out of the sky: sky light under {@link #OPEN_SKY} at the head and a block over it within 24. */
	public static boolean covered(ServerPlayer player) {
		if (skyLight(player) >= OPEN_SKY) {
			return false;
		}
		ServerLevel level = player.level();
		BlockPos head = BlockPos.containing(player.getEyePosition());
		for (int dy = 1; dy <= 24; dy++) {
			if (!level.getBlockState(head.above(dy)).getCollisionShape(level, head.above(dy)).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** In water two or more deep: water where their feet are and under it. */
	public static boolean deepWater(ServerPlayer player) {
		BlockPos feet = player.blockPosition();
		return Scan.water(player.level(), feet) && Scan.water(player.level(), feet.below());
	}

	/** Horizontal distance to their base in this level ({@link PlayerWatch#base}), or {@link Double#MAX_VALUE} if none here. */
	public static double baseDistance(PlayerWatch watch, ServerPlayer player) {
		GlobalPos base = watch.base(player).orElse(null);
		if (base == null || !base.dimension().equals(player.level().dimension())) {
			return Double.MAX_VALUE;
		}
		return Math.sqrt(Scan.horizontalDistSqr(base.pos(), player.blockPosition()));
	}

	/** The target sees this entity: inside {@link #SEEN_CONE} of their look, within {@code distance}, with a clear line. */
	public static boolean seen(Prey prey, Entity entity, double distance) {
		if (entity.level() != prey.level() || entity.position().distanceTo(prey.eye()) > distance) {
			return false;
		}
		return TraceService.sees(prey.level(), prey.viewer(distance), entity.getBoundingBox(), 0.0, SEEN_CONE);
	}

	/**
	 * The fog end the target's client draws now (its fresh report, else the server's estimate: the render distance, the
	 * haze, a fog spell; {@code FogEdge}, as 27 places its copy). The gallery's stand-in: the server's estimate at
	 * {@link #STAND_IN_CHUNKS} chunks.
	 */
	public static double fogEnd(Prey prey) {
		ServerPlayer player = prey.player();
		if (player != null) {
			return com.forzacode.a1016_02.entity.FogEdge.of(player).limit();
		}
		ServerLevel level = prey.level();
		MinecraftServer server = level.getServer();
		HerobrineState.Effects effects = HerobrineState.get(server).effects();
		return FogLimits.of(STAND_IN_CHUNKS, server.getPlayerList().getViewDistance(), effects.duskFogLevel(), level.getDefaultClockTime(), effects.fogSpell())
				.fogEnd();
	}

	/** R10-4: a trespass this recent (game ticks, one in-game day, the call's own: {@code CallTrap.CALL_TICKS}) gives the bearing. */
	public static final long CALLED_TICKS = 24_000L;
	/** The place of a trespass is looked for this far from the player (blocks). */
	static final int CALLED_REACH = 3000;

	/**
	 * R10-4, the scene's side of a trespass's call: the place this player was inside within the last in-game day (the nearest
	 * of his known places in their level that they visited, within {@link #CALLED_REACH}), as the point its first tell comes
	 * from; null when they trespassed in none lately.
	 */
	public static @Nullable Vec3 calledFrom(ServerPlayer player) {
		com.forzacode.a1016_02.core.Remains remains = com.forzacode.a1016_02.core.Services.remains();
		long at = remains.trespassedAt(player.getUUID());
		long now = player.level().getServer().overworld().getGameTime();
		if (at < 0 || now < at || now - at > CALLED_TICKS) {
			return null;
		}
		Vec3 feet = player.position();
		Vec3 best = null;
		double bestD = (double) CALLED_REACH * CALLED_REACH;
		for (com.forzacode.a1016_02.core.Remains.Place place : remains.known(player.level())) {
			if (place.dimension() != player.level().dimension() || !remains.visited(player.getUUID(), place)) {
				continue;
			}
			Vec3 anchor = Vec3.atCenterOf(place.anchor());
			double d = anchor.distanceToSqr(feet);
			if (d < bestD) {
				bestD = d;
				best = anchor;
			}
		}
		return best;
	}

	/**
	 * Pure: where a tell along the bearing toward {@code from} sounds: {@code distance} out from {@code feet} that way, turned
	 * {@code degrees}, at the feet's height plus 1 (a straight-up bearing: ahead of {@code look}).
	 */
	public static Vec3 toward(Vec3 feet, Vec3 from, double degrees, double distance, Vec3 look) {
		double a = Math.hypot(from.x - feet.x, from.z - feet.z) < 1.0E-6 ? angle(look) : angle(feet, from);
		return out(feet, a + Math.toRadians(degrees), distance).add(0.0, 1.0, 0.0);
	}

	/** The mob's head stands in the dark: light under {@link #DIM_LIGHT} there (the sky's after the time of day, or a torch's). */
	public static boolean dim(Entity entity) {
		BlockPos head = BlockPos.containing(entity.getX(), entity.getY() + entity.getBbHeight() * 0.8, entity.getZ());
		return entity.level().isLoaded(head) && entity.level().getMaxLocalRawBrightness(head) < DIM_LIGHT;
	}

	/**
	 * How far the target perceives this mob now (R10-5, R10-12), at most {@code cap}: in a fog that counts,
	 * {@link #FOG_SIGHT} of the fog end their client draws; else {@link #CLEAR_SIGHT} of it; and, the mob in the dark
	 * ({@link #dim}), never past {@link #NIGHT_SIGHT}.
	 */
	public static double perceive(Prey prey, Entity mob, double cap) {
		return sightFor(fog(prey.level()), dim(mob), fogEnd(prey), cap);
	}

	/** Pure: {@link #perceive}'s rule for a fog that counts or not, a mob in the dark or not, this fog end and cap. */
	public static double sightFor(boolean fog, boolean dark, double fogEnd, double cap) {
		double d = Math.min(cap, fogEnd * (fog ? FOG_SIGHT : CLEAR_SIGHT));
		return dark ? Math.min(d, NIGHT_SIGHT) : d;
	}

	/**
	 * The target plainly sees this mob (R10-5): through the middle third of their view ({@link #PLAIN_CONE}), within
	 * {@link #PLAIN_DISTANCE} and within what they perceive of it ({@code perceived}), with a clear line.
	 */
	public static boolean plain(Prey prey, Entity mob, double perceived) {
		double distance = Math.min(perceived, PLAIN_DISTANCE);
		if (mob.level() != prey.level() || mob.position().distanceTo(prey.eye()) > distance) {
			return false;
		}
		return TraceService.sees(prey.level(), prey.viewer(distance), mob.getBoundingBox(), 0.0, PLAIN_CONE);
	}

	/**
	 * The head of a mob standing at {@code feet} ({@code height} tall) is against the sky from the target's eyes: the line
	 * from the eyes through the head goes on {@code beyond} blocks without meeting a block (only over loaded land: unloaded,
	 * false). A wolf there is a silhouette (R11-3).
	 */
	public static boolean skyline(ServerLevel level, Vec3 eye, Vec3 feet, double height, double beyond) {
		Vec3 head = feet.add(0.0, height, 0.0);
		Vec3 dir = head.subtract(eye);
		if (dir.lengthSqr() < 1.0E-6) {
			return false;
		}
		Vec3 end = head.add(dir.normalize().scale(beyond));
		int steps = Math.max(1, (int) Math.ceil(beyond / 8.0));
		for (int i = 0; i <= steps; i++) {
			if (!level.isLoaded(BlockPos.containing(head.lerp(end, i / (double) steps)))) {
				return false; // never a chunk load for a look
			}
		}
		return clear(level, head.add(dir.normalize().scale(0.6)), end);
	}

	/** A clear line from {@code from} to {@code to} (blocks with a collision shape stop it). */
	public static boolean clear(ServerLevel level, Vec3 from, Vec3 to) {
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS;
	}

	/**
	 * Where a mob {@code headroom} blocks tall can stand at column ({@code x}, {@code z}) near height {@code nearY}: the
	 * feet cell of the first spot from {@code nearY + up} down to {@code nearY - down} with ground that holds (a collision
	 * shape, no fluid, no leaves, nothing that hurts) and room above (no collision, no fluid). Null if none, or the column
	 * is not loaded.
	 */
	public static @Nullable BlockPos stand(ServerLevel level, double x, double z, int nearY, int up, int down, int headroom) {
		BlockPos column = BlockPos.containing(x, nearY, z);
		if (!level.isLoaded(column)) {
			return null;
		}
		for (int y = nearY + up; y >= nearY - down; y--) {
			BlockPos feet = new BlockPos(column.getX(), y, column.getZ());
			if (holds(level, feet.below()) && room(level, feet, headroom)) {
				return feet;
			}
		}
		return null;
	}

	/** Ground a mob may stand on: a collision shape, no fluid, not leaves, nothing that burns or pricks. */
	static boolean holds(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES)) {
			return false;
		}
		return !state.is(BlockTags.FIRE) && !state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) && !state.is(net.minecraft.world.level.block.Blocks.CACTUS)
				&& !state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH) && !state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW);
	}

	/** {@code headroom} cells from {@code feet} up with no collision and no fluid. */
	static boolean room(ServerLevel level, BlockPos feet, int headroom) {
		for (int dy = 0; dy < headroom; dy++) {
			BlockPos pos = feet.above(dy);
			BlockState state = level.getBlockState(pos);
			if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** The point {@code distance} out from {@code from} along the horizontal angle {@code radians} (0 is +z, turning toward -x). */
	public static Vec3 out(Vec3 from, double radians, double distance) {
		return new Vec3(from.x - Math.sin(radians) * distance, from.y, from.z + Math.cos(radians) * distance);
	}

	/** The horizontal angle (radians, {@link #out}'s convention) from {@code from} toward {@code to}. */
	public static double angle(Vec3 from, Vec3 to) {
		return Math.atan2(-(to.x - from.x), to.z - from.z);
	}

	/** The horizontal angle (radians) a look vector points along. */
	public static double angle(Vec3 look) {
		return Math.atan2(-look.x, look.z);
	}

	/** {@code from} turned toward {@code to} by at most {@code max} radians, the short way round. */
	public static double turn(double from, double to, double max) {
		double diff = Math.atan2(Math.sin(to - from), Math.cos(to - from));
		return from + Math.max(-max, Math.min(max, diff));
	}

	/** Today's in-game day. */
	public static long day(MinecraftServer server) {
		return GameClock.day(server);
	}

	/** Every listener in a level, spectators included: the live alone rule's. */
	public static List<ServerPlayer> listeners(ServerLevel level) {
		return List.copyOf(level.players());
	}

	/** The mobs of these UUIDs that are loaded and alive in the level, in order. */
	public static <T extends Entity> List<T> loaded(ServerLevel level, Collection<UUID> ids, Class<T> type) {
		List<T> found = new ArrayList<>();
		for (UUID id : ids) {
			Entity entity = level.getEntity(id);
			if (type.isInstance(entity) && entity.isAlive() && !entity.isRemoved()) {
				found.add(type.cast(entity));
			}
		}
		return found;
	}
}
