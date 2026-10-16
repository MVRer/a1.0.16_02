package com.forzacode.a1016_02.world.sc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.FogPlace;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.TraceBatch;
import com.google.gson.annotations.SerializedName;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 10 ({@code docs/SELF_CREATED.md}): where he stood keeps a mark. On {@code HerobrineEvents.FIGURE_LEFT} in the
 * overworld, on the surface (sky light at his feet {@value #SURFACE_SKY} or more), seen, for a known player of the
 * story (a figure that appeared for nobody, or for someone who never took part, such as a test's own player, leaves
 * nothing), any variant but the glimpse, the last one and the Nether and End ones: at {@code standChance} two prints ({@link Prints}) under his feet, the
 * cell he stood on and the one beside it across his facing, and the short grass, flowers and ferns within
 * {@code clearRadius} gone; at {@code runChance} on top of that a print every 2 cells of his way out (his path's
 * samples), at most {@code maxRunPrints}. The prints wait in memory and are tried every
 * {@value #TRY_EVERY_TICKS} ticks until every cell is out of view (one batch, {@value #CAUSE}), and are dropped after
 * {@code retryMinutes} (a restart loses them: fine). At {@code fogChance} a fog place over where he stood (core's
 * {@code ClientEffects.addFogPlace}): radius {@code fogRadius}, level {@code fogLevel}, easing in and out over
 * {@code fogRampTicks}, for {@code fogDaysMin} to {@code fogDaysMax} in-game days, at most {@code maxFogPlaces} at
 * full at once. World-wide like weather. Server thread.
 */
public final class WhereHeStood {
	public static final String CAUSE = "world:sc10";
	public static final String SECTION = "sc10";
	/** Sky light at his feet at least this: the surface. */
	public static final int SURFACE_SKY = 10;
	/** The queue is tried this often. */
	public static final int TRY_EVERY_TICKS = 100;
	/** The variants that leave nothing: the glimpse, the last one (Ending A), the End's and the Nether's. */
	public static final Set<String> NO_MARK = Set.of("glimpse", "last_one", "among_endermen", "among_piglins");
	/** His path's samples are this far apart. */
	private static final int PATH_STEP = 2;

	/** {@code sections.sc10}. Version 2. */
	public static final class Config implements ModConfig.Versioned {
		public static final int CURRENT = 2;

		public int version = 0;
		@SerializedName(value = "a", alternate = "standChance")
		public double standChance = 0.7;
		@SerializedName(value = "b", alternate = "runChance")
		public double runChance = 0.35;
		@SerializedName(value = "c", alternate = "clearRadius")
		public int clearRadius = 2;
		@SerializedName(value = "d", alternate = "maxRunPrints")
		public int maxRunPrints = 12;
		@SerializedName(value = "e", alternate = "retryMinutes")
		public double retryMinutes = 10.0;
		@SerializedName(value = "f", alternate = "fogChance")
		public double fogChance = 0.6;
		@SerializedName(value = "g", alternate = "fogRadius")
		public float fogRadius = 40.0F;
		@SerializedName(value = "h", alternate = "fogLevel")
		public float fogLevel = 0.45F;
		@SerializedName(value = "i", alternate = "fogRampTicks")
		public int fogRampTicks = 1200;
		@SerializedName(value = "j", alternate = "fogDaysMin")
		public double fogDaysMin = 1.0;
		@SerializedName(value = "k", alternate = "fogDaysMax")
		public double fogDaysMax = 3.0;
		@SerializedName(value = "l", alternate = "maxFogPlaces")
		public int maxFogPlaces = 4;

		@Override
		public boolean upgrade() {
			if (version >= CURRENT) {
				return false;
			}
			// 1 to 2 (D-080): the keys are opaque letters; a value under an old key is read into its field (its
			// @SerializedName alternate) and only the new key is written back.
			version = CURRENT;
			return true;
		}
	}

	/** What a figure that left rolled: prints where he stood, along his way out, the fog place. */
	public record Rolls(boolean stand, boolean run, boolean fog) {
		public static final Rolls ALL = new Rolls(true, true, true);

		/** Rolled by the config's chances; the run only with the stand. */
		public static Rolls roll(RandomSource random, Config config) {
			boolean stand = random.nextDouble() < config.standChance;
			boolean run = stand && random.nextDouble() < config.runChance;
			return new Rolls(stand, run, random.nextDouble() < config.fogChance);
		}
	}

	/**
	 * Prints waiting to be out of view.
	 *
	 * @param stand       the cells he stood on: under his feet, and beside it
	 * @param clear       the cell whose plants a small circle clears around (the stand)
	 * @param clearRadius that circle's radius
	 * @param run         his way out's columns (with their heights), at most {@code maxRunPrints}
	 * @param deadline    the server tick after which it is dropped
	 */
	public record Pending(ResourceKey<Level> dimension, List<BlockPos> stand, BlockPos clear, int clearRadius, List<BlockPos> run, long deadline) {
	}

	/** The live queue: in memory, lost on a restart (fine). */
	public static final Queue LIVE = new Queue();

	/** A queue of prints (live, or a test's own). */
	public static final class Queue {
		private final List<Pending> pending = new ArrayList<>();

		public void add(Pending p) {
			pending.add(p);
		}

		public List<Pending> pending() {
			return List.copyOf(pending);
		}

		public void clear() {
			pending.clear();
		}

		/**
		 * Tries every waiting print once at server tick {@code now}: dropped past its deadline; left waiting while its
		 * cells are not loaded or not all out of view (or protected, or a player stands within 3); else applied in one
		 * batch. Returns how many were applied.
		 */
		public int tick(ScEnv env, long now) {
			int applied = 0;
			Iterator<Pending> it = pending.iterator();
			while (it.hasNext()) {
				Pending p = it.next();
				if (now > p.deadline() || !ScGates.allowsWorld(env.state())) {
					it.remove();
					continue;
				}
				ServerLevel level = env.server().getLevel(p.dimension());
				if (level == null) {
					it.remove();
					continue;
				}
				List<Prints.Edit> edits = edits(level, p);
				if (edits == null) {
					continue;
				}
				if (edits.isEmpty()) {
					it.remove();
					continue;
				}
				Set<BlockPos> cells = Prints.cells(edits);
				if (env.anyProtected(level, cells) || !env.view().outOfView(level, cells)) {
					continue;
				}
				TraceBatch batch = env.traces().batch(level, CAUSE);
				Prints.queue(batch, edits);
				if (batch.commit()) {
					it.remove();
					applied++;
					A1016_02.LOGGER.info("[a1016] world: sc_10 at {} ({} edits)", p.clear().toShortString(), edits.size());
				}
			}
			return applied;
		}
	}

	private WhereHeStood() {
	}

	public static Config config() {
		return ModConfig.section(SECTION, Config.class, Config::new);
	}

	// --- the rules ---

	/**
	 * Why a figure that left marks nothing, or null: the dimension, unseen, not for a known player, a variant that
	 * leaves none, or underground.
	 */
	public static @Nullable String refusal(ResourceKey<Level> dimension, int skyLight, boolean seen, boolean forKnownPlayer, String variant) {
		if (dimension != Level.OVERWORLD) {
			return "not the overworld";
		}
		if (!seen) {
			return "nobody saw him";
		}
		if (!forKnownPlayer) {
			return "he appeared for nobody who takes part in the story";
		}
		if (NO_MARK.contains(variant)) {
			return "the " + variant + " leaves nothing";
		}
		if (skyLight < SURFACE_SKY) {
			return "not on the surface (sky light " + skyLight + ")";
		}
		return null;
	}

	/** The cell beside {@code feet} across his facing ({@code yaw}, Minecraft's), on {@code side} (1 right, -1 left). */
	public static BlockPos beside(BlockPos feet, float yaw, int side) {
		double ox = -Mth.sin(yaw * Mth.DEG_TO_RAD);
		double oz = Mth.cos(yaw * Mth.DEG_TO_RAD);
		BlockPos next = feet.offset((int) Math.round(-oz * side), 0, (int) Math.round(ox * side));
		return next.equals(feet) ? feet.offset(side, 0, 0) : next;
	}

	/**
	 * The edits for these prints now: each stand cell's print, the plants within {@code clearRadius} of the stand,
	 * each run print; null while a stand column is not loaded (it waits), run columns not loaded are left out.
	 */
	static @Nullable List<Prints.Edit> edits(ServerLevel level, Pending p) {
		List<Prints.Edit> edits = new ArrayList<>();
		for (BlockPos column : p.stand()) {
			if (!level.isLoaded(column)) {
				return null;
			}
			BlockPos ground = Prints.ground(level, column.getX(), column.getZ(), column.getY());
			if (ground != null) {
				Prints.addAll(edits, Prints.at(level, ground));
			}
		}
		int r = Math.max(0, p.clearRadius());
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				BlockPos column = p.clear().offset(dx, 0, dz);
				if (dx * dx + dz * dz > r * r || !level.isLoaded(column)) {
					continue;
				}
				BlockPos ground = Prints.ground(level, column.getX(), column.getZ(), p.clear().getY());
				if (ground != null) {
					Prints.addAll(edits, Prints.clear(level, ground));
				}
			}
		}
		for (BlockPos column : p.run()) {
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos ground = Prints.ground(level, column.getX(), column.getZ(), column.getY());
			if (ground != null) {
				Prints.addAll(edits, Prints.at(level, ground));
			}
		}
		return edits;
	}

	/** The fog place over where he stood, from the overworld clock {@code now}, lasting {@code days} in-game days. */
	public static FogPlace fogPlace(Vec3 stoodAt, long now, double days, Config config) {
		long length = Math.max(1L, Math.round(days * 24000.0));
		int ramp = Math.max(0, config.fogRampTicks);
		return new FogPlace(Level.OVERWORLD, stoodAt, config.fogRadius, config.fogLevel, now, now + length, ramp, ramp);
	}

	/**
	 * A figure left ({@code HerobrineEvents.FIGURE_LEFT}): if it marks anything, queues its prints on {@code queue}
	 * (tried later) and adds its fog place now, by {@code rolls}. Returns what it did, in a few words.
	 */
	public static String figureLeft(ScEnv env, Queue queue, ServerLevel level, Vec3 stoodAt, float yaw, List<Vec3> path, String variant,
			@Nullable UUID appearedFor, boolean seen, Rolls rolls, RandomSource random, Config config) {
		String world = ScGates.worldRefusal(env.state());
		if (world != null) {
			return world;
		}
		BlockPos feet = BlockPos.containing(stoodAt);
		if (!level.isLoaded(feet)) {
			return "not loaded";
		}
		boolean known = appearedFor != null && env.state().player(appearedFor).isPresent();
		String refusal = refusal(level.dimension(), level.getBrightness(LightLayer.SKY, feet), seen, known, variant);
		if (refusal != null) {
			return refusal;
		}
		List<String> did = new ArrayList<>();
		if (rolls.stand()) {
			int groundY = feet.getY() - 1;
			BlockPos under = new BlockPos(feet.getX(), groundY, feet.getZ());
			List<BlockPos> stand = List.of(under, beside(under, yaw, random.nextBoolean() ? 1 : -1));
			List<BlockPos> run = new ArrayList<>();
			if (rolls.run()) {
				for (Vec3 sample : path) {
					if (run.size() >= Math.max(0, config.maxRunPrints)) {
						break;
					}
					BlockPos column = BlockPos.containing(sample).below();
					if (!stand.contains(column) && !run.contains(column)) {
						run.add(column);
					}
				}
			}
			long deadline = env.server().getTickCount() + ModConfig.realTicks(config.retryMinutes * 60.0);
			queue.add(new Pending(level.dimension(), stand, under, config.clearRadius, List.copyOf(run), deadline));
			did.add("prints queued (" + stand.size() + " where he stood" + (run.isEmpty() ? "" : ", " + run.size() + " along his way") + ")");
		}
		if (rolls.fog()) {
			double days = Mth.nextDouble(random, Math.min(config.fogDaysMin, config.fogDaysMax), Math.max(config.fogDaysMin, config.fogDaysMax));
			long now = env.server().overworld().getDefaultClockTime();
			boolean added = env.fog().add(env.server(), fogPlace(stoodAt, now, days, config), config.maxFogPlaces);
			did.add(added ? String.format(java.util.Locale.ROOT, "fog over it for %.1f days", days) : "the fog place was refused");
		}
		return did.isEmpty() ? "nothing rolled" : String.join(", ", did);
	}

	/** {@code HerobrineEvents.FIGURE_LEFT}'s live listener: cheap, it only rolls and queues. */
	public static void onFigureLeft(ServerLevel level, Vec3 stoodAt, float yaw, List<Vec3> path, String variant, @Nullable UUID appearedFor, boolean seen) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		Config config = config();
		String did = figureLeft(ScEnv.live(server), LIVE, level, stoodAt, yaw, path, variant, appearedFor, seen, Rolls.roll(random, config), random,
				config);
		A1016_02.LOGGER.debug("[a1016] world: sc_10: {}", did);
	}

	/** A test or debug path for {@code path}: samples every {@value #PATH_STEP} blocks from {@code from} going {@code yaw}. */
	public static List<Vec3> pathAway(Vec3 from, float yaw, int samples) {
		List<Vec3> path = new ArrayList<>(samples);
		double ox = -Mth.sin(yaw * Mth.DEG_TO_RAD);
		double oz = Mth.cos(yaw * Mth.DEG_TO_RAD);
		for (int i = 1; i <= samples; i++) {
			path.add(from.add(ox * PATH_STEP * i, 0.0, oz * PATH_STEP * i));
		}
		return path;
	}
}
