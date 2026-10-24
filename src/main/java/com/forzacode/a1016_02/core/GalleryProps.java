package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The gallery's props (docs/SELF_CREATED_2.md, "The gallery", SC2-0c): ground, rock, hills, water, cutaways, a human
 * scale cue, test mobs, the sky and a staged walk, for {@link Gallery} exhibits. Development only: every call is for the
 * gallery, its tests and {@code /a1016 sc g}, never game code (it writes blocks directly, not through
 * {@code TraceService}, and {@link #summon} is the gallery's only spawn). Server thread. Blocks are set with
 * {@link Block#UPDATE_CLIENTS} (no neighbour updates: water stands, sand does not fall).
 */
public final class GalleryProps {
	private static final int FLAGS = Block.UPDATE_CLIENTS;

	private GalleryProps() {
	}

	/** Sets one block (the props' flags). */
	public static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, FLAGS);
	}

	/** Air everywhere in the box, and every entity in it but players gone (the staged mobs of an earlier run included). */
	public static void clear(ServerLevel level, BoundingBox box) {
		fill(level, box, Blocks.AIR.defaultBlockState());
		AABB area = AABB.of(box);
		for (Entity entity : level.getEntities((Entity) null, area, e -> !(e instanceof Player))) {
			entity.discard();
		}
	}

	/** Every cell of the box set to this block. */
	public static void fill(ServerLevel level, BoundingBox box, BlockState state) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = box.minY(); y <= box.maxY(); y++) {
					set(level, at.set(x, y, z), state);
				}
			}
		}
	}

	/**
	 * Solid rock for anything underground: stone with a few ore-free strata (andesite and tuff bands that wander a block
	 * up or down, deepslate below Y 0), every cell of the box.
	 */
	public static void rock(ServerLevel level, BoundingBox box) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				int wander = Math.floorMod(Mth.murmurHash3Mixer(x / 6 * 31 + z / 6 * 17), 3) - 1;
				for (int y = box.minY(); y <= box.maxY(); y++) {
					set(level, at.set(x, y, z), stratum(y + wander, y));
				}
			}
		}
	}

	private static BlockState stratum(int band, int y) {
		if (y < 0) {
			return Blocks.DEEPSLATE.defaultBlockState();
		}
		int m = Math.floorMod(band, 23);
		if (m == 0 || m == 1) {
			return Blocks.ANDESITE.defaultBlockState();
		}
		if (m == 12) {
			return Blocks.TUFF.defaultBlockState();
		}
		return Blocks.STONE.defaultBlockState();
	}

	/**
	 * A smooth hill: stone inside, three dirt under a grass top, rising {@code height} over {@code center} (the first air
	 * cell above the ground) and falling to the ground at {@code radius}, a little uneven so it reads as land. Returns
	 * the hill's box. A hill of radius 90 and height 100 is about 800 000 blocks: a few seconds.
	 */
	public static BoundingBox hill(ServerLevel level, BlockPos center, int radius, int height) {
		int base = center.getY();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int top = hillHeight(dx, dz, radius, height);
				if (top <= 0) {
					continue;
				}
				int x = center.getX() + dx;
				int z = center.getZ() + dz;
				// The ground's own top block under the hill turns to stone or dirt with the rest.
				for (int y = base - 1; y < base + top; y++) {
					int fromTop = base + top - 1 - y;
					set(level, at.set(x, y, z), fromTop == 0 ? grass : fromTop <= 3 ? dirt : stone);
				}
			}
		}
		return new BoundingBox(center.getX() - radius, base - 1, center.getZ() - radius, center.getX() + radius, base + height, center.getZ() + radius);
	}

	/** The hill's height over the ground {@code (dx, dz)} from its centre: a cosine dome with a slow wobble. */
	static int hillHeight(int dx, int dz, int radius, int height) {
		double angle = Math.atan2(dz, dx);
		double wobble = 1.0 + 0.07 * Math.sin(3 * angle + 0.6) + 0.04 * Math.sin(5 * angle + 2.1);
		double d = Math.sqrt(dx * dx + dz * dz) / (radius * wobble);
		if (d >= 1.0) {
			return 0;
		}
		double c = Math.cos(d * Math.PI / 2);
		return (int) Math.round(height * c * c);
	}

	/**
	 * A sea over the box's columns: water from {@code box.maxY()} down {@code depth} blocks on a sand floor, the air
	 * above the water up to the box's top cleared to {@code box.maxY() + 32}. {@code box.maxY()} is the water's top block.
	 */
	public static void sea(ServerLevel level, BoundingBox box, int depth) {
		int surface = box.maxY();
		int floor = surface - Math.max(1, depth);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockState water = Blocks.WATER.defaultBlockState();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				set(level, at.set(x, floor, z), Blocks.SAND.defaultBlockState());
				for (int y = floor + 1; y <= surface; y++) {
					set(level, at.set(x, y, z), water);
				}
				for (int y = surface + 1; y <= surface + 32; y++) {
					if (!level.getBlockState(at.set(x, y, z)).isAir()) {
						set(level, at, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	/**
	 * A section: removes everything in the box on {@code side} of the vertical plane through its middle (the plane's own
	 * cells stay: they are the cut face). Put the camera on that side ({@link Gallery#sectionView}). Returns the removed part.
	 */
	public static BoundingBox cutaway(ServerLevel level, BoundingBox box, Direction side) {
		BlockPos c = box.getCenter();
		return cutaway(level, box, side, side.getAxis() == Direction.Axis.X ? c.getX() : c.getZ());
	}

	/**
	 * A section at a plane of your own: removes everything in the box whose coordinate along {@code side}'s axis is past
	 * {@code plane} on {@code side} (the cells at {@code plane} stay). {@code side} is horizontal. Returns the removed part.
	 */
	public static BoundingBox cutaway(ServerLevel level, BoundingBox box, Direction side, int plane) {
		if (side.getAxis() == Direction.Axis.Y) {
			throw new IllegalArgumentException("a cutaway is a vertical plane: " + side);
		}
		BoundingBox removed;
		if (side.getAxis() == Direction.Axis.X) {
			removed = side.getStepX() > 0 ? new BoundingBox(plane + 1, box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())
					: new BoundingBox(box.minX(), box.minY(), box.minZ(), plane - 1, box.maxY(), box.maxZ());
		} else {
			removed = side.getStepZ() > 0 ? new BoundingBox(box.minX(), box.minY(), plane + 1, box.maxX(), box.maxY(), box.maxZ())
					: new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), plane - 1);
		}
		if (removed.maxX() >= removed.minX() && removed.maxZ() >= removed.minZ()) {
			clear(level, removed);
		}
		return removed;
	}

	/**
	 * The human scale cue: an armor stand in leather armor with a player head, standing at {@code feet} facing
	 * {@code yaw}. Put one beside every entrance.
	 */
	public static ArmorStand scaleFigure(ServerLevel level, BlockPos feet, float yaw) {
		ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
		if (stand == null) {
			throw new IllegalStateException("no armor stand");
		}
		stand.snapTo(Vec3.atBottomCenterOf(feet), yaw, 0.0F);
		stand.setYHeadRot(yaw);
		stand.setYBodyRot(yaw);
		stand.setShowArms(true);
		stand.setNoBasePlate(true);
		stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.PLAYER_HEAD));
		stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
		stand.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
		stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
		level.addFreshEntity(stand);
		return stand;
	}

	/** The tag every mob {@link #summon} makes carries (so a time-lapse finds them again: {@link #staged}). */
	public static final String TAG = "a1016_gallery";

	/**
	 * The staged mobs of this type in the area, found again. A time-lapse's {@code start} must use this, never mobs kept
	 * from placing: when the camera goes far (the far views), their chunks unload and come back as new entities.
	 */
	public static <T extends Mob> List<T> staged(ServerLevel level, BoundingBox area, EntityType<T> type) {
		List<T> mobs = new ArrayList<>(level.getEntities(type, AABB.of(area), mob -> mob.isAlive() && mob.entityTags().contains(TAG)));
		mobs.sort((a, b) -> a.getUUID().compareTo(b.getUUID()));
		return mobs;
	}

	/**
	 * Test mobs for staging a behaviour: {@code count} of {@code type} on a small ring around {@code at} (the gallery's
	 * only spawns; never game code). Each is persistent and tagged {@link #TAG}. Returns them (for setting them up now;
	 * a time-lapse finds them again with {@link #staged}).
	 */
	public static <T extends Mob> List<T> summon(ServerLevel level, EntityType<T> type, BlockPos at, int count) {
		List<T> mobs = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			double angle = 2 * Math.PI * i / Math.max(1, count);
			double r = count == 1 ? 0.0 : 1.5 + count * 0.25;
			Vec3 spot = new Vec3(at.getX() + 0.5 + Math.cos(angle) * r, at.getY(), at.getZ() + 0.5 + Math.sin(angle) * r);
			T mob = type.create(level, EntitySpawnReason.COMMAND);
			if (mob == null) {
				continue;
			}
			mob.snapTo(spot, (float) Math.toDegrees(angle) + 90.0F, 0.0F);
			mob.setPersistenceRequired();
			mob.addTag(TAG);
			level.addFreshEntity(mob);
			mobs.add(mob);
		}
		return mobs;
	}

	/** Runs a vanilla command as the server, at {@code at} in this level, without chat output (for example {@code place feature minecraft:oak ~ ~ ~}). */
	public static void command(ServerLevel level, Vec3 at, String command) {
		MinecraftServer server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withPosition(at).withSuppressedOutput(), command);
	}

	/** A vanilla tree on the ground at {@code base} (the first air cell): {@code minecraft:oak}, {@code minecraft:birch}, ... */
	public static void tree(ServerLevel level, BlockPos base, String feature) {
		command(level, Vec3.atBottomCenterOf(base), String.format(Locale.ROOT, "place feature %s %d %d %d", feature, base.getX(), base.getY(), base.getZ()));
	}

	// --- the sky ---

	/** Sets the overworld clock to this time of day on day 0 (the clock's total ticks); the gallery world's time stands still. */
	public static void time(MinecraftServer server, long dayTime) {
		Optional<Holder.Reference<WorldClock>> clock = server.registryAccess().get(WorldClocks.OVERWORLD);
		clock.ifPresent(holder -> server.clockManager().setTotalTicks(holder, Math.floorMod(dayTime, GameClock.TICKS_PER_DAY)));
	}

	/** A fog spell at full at once (ramp 0), at the stage's fog night level, for a day from now; through {@link ClientEffects}. */
	public static void fogNow(MinecraftServer server, ServerLevel level) {
		fogNow(server, level, FogWeatherRules.settings().nightLevel(HerobrineState.get(server).stage()));
	}

	/** A fog spell at full at once at this level (0 to 1, the dusk fog's scale), for a day from now. */
	public static void fogNow(MinecraftServer server, ServerLevel level, double amount) {
		long now = server.overworld().getDefaultClockTime();
		FogSpell spell = new FogSpell(now - 1, now + GameClock.TICKS_PER_DAY, (float) amount, true, 0L, 0L, 3.0);
		ClientEffects.setFogSpell(server, Optional.of(spell));
	}

	/** No fog spell, clear weather (the mod's own dusk fog stays as a player has it). */
	public static void clearSky(MinecraftServer server, ServerLevel level) {
		ClientEffects.setFogSpell(server, Optional.empty());
		command(level, Vec3.ZERO, "weather clear");
	}

	/** Dusk (12600) with a clear sky. */
	public static void dusk(MinecraftServer server, ServerLevel level) {
		time(server, Gallery.DUSK);
		clearSky(server, level);
	}

	/** A view's sky: its time of day, then the fog ({@link Gallery.Sky#FOG_NIGHT} and {@link Gallery.Sky#FOG_DAY} at full) or none. */
	public static void sky(MinecraftServer server, ServerLevel level, Gallery.Sky sky, long dayTime) {
		time(server, dayTime);
		clearSky(server, level);
		switch (sky) {
			case FOG_NIGHT -> fogNow(server, level);
			case FOG_DAY -> fogNow(server, level, FogWeatherRules.settings().dayLevel(HerobrineState.get(server).stage()));
			case CLEAR, DUSK -> {
			}
		}
	}

	// --- staging ---

	/** A staged walk: mobs that follow a path of points at a speed, until a deadline; each mob's next point and its aim. */
	private record Walk(List<Mob> mobs, List<Vec3> path, int[] next, int[] aim, double speed, long until, boolean loop) {
	}

	/**
	 * The last point of an open path counts as reached this close (blocks); the navigation aims at least {@link #AIM}
	 * ahead (a path to a point a block off is empty); a mob's place on the path is looked for {@link #LOOK} points ahead.
	 */
	private static final double PASSED = 2.0;
	private static final double AIM = 4.0;
	private static final int LOOK = 6;

	private static final List<Walk> WALKS = new ArrayList<>();
	private static boolean ticking;
	private static long tick;

	/**
	 * Stages a walk (the gallery's only mob control; game code uses {@code MobTamper}): each mob loses its goals (it no
	 * longer grazes, wanders or panics) and walks by its own navigation from point to point of {@code path} at
	 * {@code speed} (its walk speed factor), mob {@code i} starting at point {@code i * path.size() / mobs.size()} so they
	 * walk nose to tail; with {@code loop} it goes round, else it stops at the last point. For {@code ticks} server ticks.
	 * Returns what ends this walk alone ({@link #stopWalks} ends them all, at the end of a gallery run).
	 */
	public static synchronized Runnable walk(List<? extends Mob> mobs, List<Vec3> path, double speed, int ticks, boolean loop) {
		if (mobs.isEmpty() || path.isEmpty()) {
			return () -> { };
		}
		if (!ticking) {
			ticking = true;
			ServerTickEvents.END_SERVER_TICK.register(GalleryProps::tickWalks);
		}
		int[] next = new int[mobs.size()];
		int[] aim = new int[mobs.size()];
		for (int i = 0; i < mobs.size(); i++) {
			Mob mob = mobs.get(i);
			mob.removeAllGoals(goal -> true);
			next[i] = loop ? i * path.size() / mobs.size() : 0;
			aim[i] = -1;
		}
		Walk walk = new Walk(List.copyOf(mobs), List.copyOf(path), next, aim, speed, tick + ticks, loop);
		WALKS.add(walk);
		return () -> stop(walk);
	}

	private static synchronized void stop(Walk walk) {
		if (WALKS.remove(walk)) {
			walk.mobs().forEach(mob -> mob.getNavigation().stop());
		}
	}

	/** Ends every staged walk (their mobs stand): the end of a gallery run. */
	public static synchronized void stopWalks() {
		for (Walk walk : WALKS) {
			walk.mobs().forEach(mob -> mob.getNavigation().stop());
		}
		WALKS.clear();
	}

	private static synchronized void tickWalks(MinecraftServer server) {
		tick++;
		if (WALKS.isEmpty()) {
			return;
		}
		WALKS.removeIf(walk -> {
			boolean over = tick >= walk.until() || walk.mobs().stream().noneMatch(Entity::isAlive);
			if (over) {
				walk.mobs().forEach(mob -> mob.getNavigation().stop());
			}
			return over;
		});
		for (Walk walk : WALKS) {
			List<Vec3> path = walk.path();
			int size = path.size();
			for (int i = 0; i < walk.mobs().size(); i++) {
				Mob mob = walk.mobs().get(i);
				if (!mob.isAlive()) {
					continue;
				}
				// Where it is along the path: the nearest of the next few points (only ever forward; a mob that cuts a
				// corner never comes near the corner's own point, so "passing" a point is not a test).
				int n = walk.next()[i];
				double nearest = flat(mob.position(), path.get(n));
				for (int k = 1; k <= LOOK; k++) {
					int at = walk.loop() ? (n + k) % size : Math.min(size - 1, n + k);
					double d = flat(mob.position(), path.get(at));
					if (d < nearest) {
						nearest = d;
						n = at;
					}
				}
				walk.next()[i] = n;
				if (!walk.loop() && n == size - 1 && nearest < PASSED) {
					mob.getNavigation().stop();
					continue;
				}
				// Aim past it, at the first point far enough for a path to have steps.
				int aim = walk.loop() ? (n + 1) % size : Math.min(size - 1, n + 1);
				for (int k = 0; k < size && flat(mob.position(), path.get(aim)) < AIM; k++) {
					if (aim + 1 >= size && !walk.loop()) {
						break;
					}
					aim = (aim + 1) % size;
				}
				if (aim != walk.aim()[i] || mob.getNavigation().isDone() || (tick + i) % 20 == 0) {
					Vec3 target = path.get(aim);
					// Accuracy 0: the path ends on the point itself, so the walk keeps to its line instead of a block inside it.
					mob.getNavigation().moveTo(target.x, target.y, target.z, 0, walk.speed());
					walk.aim()[i] = aim;
				}
			}
		}
	}

	private static double flat(Vec3 a, Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	/** The points of a rectangle's edge at {@code y}, {@code step} apart, clockwise from above from its north-west corner. */
	public static List<Vec3> rectangle(double minX, double minZ, double maxX, double maxZ, double y, double step) {
		List<Vec3> points = new ArrayList<>();
		double[][] corners = {{minX, minZ}, {maxX, minZ}, {maxX, maxZ}, {minX, maxZ}};
		for (int c = 0; c < 4; c++) {
			double[] a = corners[c];
			double[] b = corners[(c + 1) % 4];
			double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
			int steps = Math.max(1, (int) Math.round(length / step));
			for (int s = 0; s < steps; s++) {
				double t = (double) s / steps;
				points.add(new Vec3(a[0] + (b[0] - a[0]) * t, y, a[1] + (b[1] - a[1]) * t));
			}
		}
		return points;
	}
}
