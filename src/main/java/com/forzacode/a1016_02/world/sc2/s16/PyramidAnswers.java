package com.forzacode.a1016_02.world.sc2.s16;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Figure;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.world.gen.Hash;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;
import com.forzacode.a1016_02.world.sc2.kit.Visits;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * How a pyramid answers a visit (docs/SELF_CREATED_2.md, 16b and 16c; D-097, D-105, D-106), through the kit's
 * {@link Answers} (out of view, ledgered, the fair-danger checks for everyone inside, the caps, the event, the tension):
 * <ul>
 *   <li><b>16b, the way in closes.</b> Once a player has been inside {@code g} seconds: when everyone inside is at least
 *   {@code h} from the inner mouth, no player stands within {@value #MOUTH_CLEAR} of it (inside or out), everyone
 *   inside carries a pickaxe and has 10 health or more, four bricks of the core's inner face beside the mouth are moved
 *   into the mouth's 2 x 2 ({@code world:s16/close}); their hollows are the clue. Once a place a day, {@code i} a place
 *   ever, one a player a day. A pickaxe and a few seconds dig it out.</li>
 *   <li><b>16c, your lights go.</b> While a player stands at least {@code j} steps down the pit, a light a player set in
 *   the core or the pit above them, {@value #DARK_FAR} or more away, is taken every {@code k} to {@code l} seconds
 *   ({@code world:s16/dark}, nothing dropped), the farthest first, at most {@code m} a visit; never one within
 *   {@value #DARK_NEAR} of a player, never a player's last light within {@value #LAST_LIGHT}. One a player a day (the
 *   first light of a visit is counted, the rest are its parts). After {@code n}, while they are in the pit's lower
 *   half, the figure's glimpse on a dark step {@value #GLIMPSE_MIN} to {@value #GLIMPSE_MAX} above them, once a
 *   visit, if every glimpse rule holds ({@link Figure#canGlimpse}).</li>
 * </ul>
 * Visits come from the kit's events ({@code REMAINS_ENTERED}, {@code REMAINS_LEFT}); a look once a second while anyone is
 * inside a pyramid, nothing otherwise. Session only; server thread.
 */
public final class PyramidAnswers {
	public static final String CLOSE = S16.CAUSE + "/close";
	public static final String DARK = S16.CAUSE + "/dark";
	/** How often the visits are looked at (ticks). */
	public static final int EVERY_TICKS = 20;
	/** 16b: nobody within this of the mouth. */
	public static final int MOUTH_CLEAR = 16;
	/** 16b: everyone inside this healthy. */
	public static final float CLOSE_HEALTH = 10.0F;
	/** 16b: a refused ask is asked again after this long (ticks). */
	public static final int CLOSE_RETRY_TICKS = 600;
	/** 16c: the light at least this far from the player it is taken for. */
	public static final double DARK_FAR = 12.0;
	/** 16c: never a light within this of any player. */
	public static final double DARK_NEAR = 3.0;
	/** 16c: never a player's last light within this. */
	public static final int LAST_LIGHT = 6;
	/** The glimpse stands this many steps above the player at least, at most {@link #GLIMPSE_MAX}. */
	public static final int GLIMPSE_MIN = 14;
	public static final int GLIMPSE_MAX = 20;

	/** The player-placed lights near a place (live: the footprint's placed blocks that are lights now). */
	@FunctionalInterface
	public interface Lights {
		List<BlockPos> placed(ServerLevel level, BlockPos center, int radius);
	}

	/**
	 * What the answers read: the kit's answers, who is around, the placed lights, the figure, the config, a place's shape.
	 *
	 * @param people everyone online (spectators included; they are skipped where it matters)
	 */
	public record Env(Answers answers, Visits.People people, Lights lights, Supplier<Figure> figure, Supplier<Sc16Config> config,
			Function<ServerLevel, Function<Remains.Place, @Nullable PyramidShape>> shapes) {
	}

	/** One player's visit to one pyramid. */
	static final class Visit {
		final Remains.Place place;
		final UUID player;
		long entered;
		long nextDark = -1;
		long closeAskAt;
		int darkened;
		boolean glimpsed;

		Visit(Remains.Place place, UUID player, long entered) {
			this.place = place;
			this.player = player;
			this.entered = entered;
			this.closeAskAt = entered;
		}
	}

	public static final PyramidAnswers LIVE = new PyramidAnswers(new Env(Answers.LIVE, server -> List.copyOf(server.getPlayerList().getPlayers()),
			PyramidAnswers::livePlaced, Services::figure, Sc16Config::get, PyramidAnswers::liveShapes));

	/** The live lights: the placed blocks the footprint knows that are lights now (any player's). */
	public static final Lights LIVE_LIGHTS = PyramidAnswers::livePlaced;

	private static final Map<Remains.Place, PyramidShape> LIVE_SHAPES = new HashMap<>();

	private final Env env;
	private final Map<UUID, Visit> visits = new LinkedHashMap<>();
	/** The clock of the last look (a waiting 16b asks it: someone inside must have been in long enough, R12-24). */
	private long lastLook;

	public PyramidAnswers(Env env) {
		this.env = env;
	}

	// --- visits ---

	/** A player came inside a place (any kind: only pyramids count). */
	public void entered(Remains.Place place, ServerPlayer player, long now) {
		if (place.kind() == Remains.Kind.PYRAMID) {
			visits.put(player.getUUID(), new Visit(place, player.getUUID(), now));
		}
	}

	/** A player left a place. */
	public void left(Remains.Place place, ServerPlayer player) {
		Visit visit = visits.get(player.getUUID());
		if (visit != null && visit.place.equals(place)) {
			visits.remove(player.getUUID());
		}
	}

	/** Forgets every visit (switch off, server stop). */
	public void clear() {
		visits.clear();
	}

	public boolean inside(UUID player) {
		return visits.containsKey(player);
	}

	/** {@code /a1016 sc 16 arm}: this player's visit runs both timers out now (every other rule kept). False if not inside one. */
	public boolean arm(ServerPlayer player, long now) {
		Visit visit = visits.get(player.getUUID());
		if (visit == null) {
			return false;
		}
		visit.entered = now - Math.round(env.config().get().closeAfterSeconds * 20);
		visit.closeAskAt = now;
		visit.nextDark = now;
		return true;
	}

	/** What this player's visit has come to, in plain words (for {@code /a1016 sc 16}). */
	public @Nullable String describe(UUID player, long now) {
		Visit visit = visits.get(player);
		if (visit == null) {
			return null;
		}
		return String.format(java.util.Locale.ROOT, "inside %ds, lights taken %d, glimpse %s, next light in %s", (now - visit.entered) / 20, visit.darkened,
				visit.glimpsed ? "came" : "not yet", visit.nextDark < 0 ? "-" : Math.max(0, (visit.nextDark - now) / 20) + "s");
	}

	/** The live tick: a look every {@value #EVERY_TICKS} ticks while anyone is inside a pyramid. */
	public void tick(MinecraftServer server) {
		if (visits.isEmpty() || server.getTickCount() % EVERY_TICKS != 11 || !SelfCreated.enabled()) {
			return;
		}
		look(server, server.getTickCount());
	}

	/** One look at every visit: 16b, 16c and the glimpse. */
	public void look(MinecraftServer server, long now) {
		lastLook = now;
		Map<UUID, ServerPlayer> online = new HashMap<>();
		for (ServerPlayer player : env.people().online(server)) {
			online.put(player.getUUID(), player);
		}
		for (Visit visit : List.copyOf(visits.values())) {
			ServerPlayer player = online.get(visit.player);
			if (player == null || player.isSpectator() || !player.isAlive()) {
				continue;
			}
			ServerLevel level = server.getLevel(visit.place.dimension());
			if (level == null || player.level() != level) {
				continue;
			}
			PyramidShape shape = env.shapes().apply(level).apply(visit.place);
			if (shape == null) {
				continue;
			}
			close(level, visit, shape, now);
			dark(level, visit, shape, player, now);
		}
	}

	// --- 16b ---

	/** The rules of 16b with this config. */
	public static Answers.Rules closeRules(Sc16Config config) {
		return Answers.Rules.of("16b", CLOSE).perPlaceEver(config.closePerPlaceMax).health(CLOSE_HEALTH).tool(Answers::pickaxe).limitSeconds(60);
	}

	private void close(ServerLevel level, Visit visit, PyramidShape shape, long now) {
		if (!shape.hasWayIn()) {
			return;
		}
		Sc16Config config = env.config().get();
		if (now - visit.entered < Math.round(config.closeAfterSeconds * 20) || now < visit.closeAskAt) {
			return;
		}
		Answers.Result result = env.answers().submit(level, visit.place, closeRules(config), (lvl, place, inside) -> closing(lvl, shape, inside, config));
		if (result.status() != Answers.Status.WAITING) {
			visit.closeAskAt = now + (result.status() == Answers.Status.DONE ? Long.MAX_VALUE / 2 : CLOSE_RETRY_TICKS);
		}
		if (result.status() == Answers.Status.DONE) {
			A1016_02.LOGGER.debug("[a1016] s2: 16b at {}", visit.place.entrance().toShortString());
		}
	}

	/**
	 * 16b's edit now, or null while it must wait: the mouth open, everyone inside far enough from it, nobody near it,
	 * four bricks beside it to move.
	 */
	Answers.@Nullable Edit closing(ServerLevel level, PyramidShape shape, List<ServerPlayer> inside, Sc16Config config) {
		List<BlockPos> mouth = shape.innerMouth();
		if (mouth.isEmpty() || !Answers.loaded(level, mouth) || !Answers.loaded(level, shape.besideMouth())) {
			return null;
		}
		for (BlockPos cell : mouth) {
			if (!level.getBlockState(cell).isAir()) {
				return null;
			}
		}
		// R12-24: someone inside now has been inside long enough (a newcomer does not inherit a waiting closing).
		long after = Math.round(config.closeAfterSeconds * 20);
		boolean due = false;
		for (ServerPlayer player : inside) {
			Visit visit = visits.get(player.getUUID());
			due |= visit != null && lastLook - visit.entered >= after;
		}
		if (!due) {
			return null;
		}
		Vec3 middle = middle(mouth);
		for (ServerPlayer player : inside) {
			if (player.position().distanceTo(middle) < config.closeMinDistance) {
				return null;
			}
		}
		for (ServerPlayer player : env.people().online(level.getServer())) {
			if (!player.isSpectator() && player.level() == level && player.position().distanceTo(middle) < MOUTH_CLEAR) {
				return null;
			}
		}
		List<BlockPos> from = new ArrayList<>();
		for (BlockPos cell : shape.besideMouth()) {
			if (level.getBlockState(cell).is(Blocks.BRICKS) && from.size() < mouth.size()) {
				from.add(cell);
			}
		}
		if (from.size() < mouth.size()) {
			return null;
		}
		List<BlockPos> cells = new ArrayList<>(from);
		cells.addAll(mouth);
		// R12-11: never into a cell a pet or anything living stands in.
		if (Answers.occupied(level, mouth)) {
			return null;
		}
		return Answers.Edit.of(cells, traces -> {
			TraceBatch batch = traces.batch(level, CLOSE);
			for (int i = 0; i < mouth.size(); i++) {
				batch.move(from.get(i), mouth.get(i));
			}
			return batch.commit();
		});
	}

	// --- 16c ---

	/** The rules of 16c: its first light counted (one a player a day), the rest of the visit's its parts. */
	public static Answers.Rules darkRules(boolean first) {
		Answers.Rules rules = Answers.Rules.of("16c", DARK).perPlaceDay(Integer.MAX_VALUE).limitSeconds(8);
		return first ? rules : rules.part();
	}

	private void dark(ServerLevel level, Visit visit, PyramidShape shape, ServerPlayer player, long now) {
		if (!shape.hasPit()) {
			return;
		}
		Sc16Config config = env.config().get();
		BlockPos feet = player.blockPosition();
		int steps = shape.baseY() - feet.getY();
		if (!shape.pit(feet.getX(), feet.getY(), feet.getZ()) || steps < config.darkStepsMin) {
			return;
		}
		glimpse(level, visit, shape, player, steps, config);
		if (visit.darkened >= config.darkPerVisit) {
			return;
		}
		if (visit.nextDark < 0) {
			visit.nextDark = now + delay(visit, config);
			return;
		}
		if (now < visit.nextDark) {
			return;
		}
		visit.nextDark = now + delay(visit, config);
		BlockPos light = nextLight(level, shape, player);
		if (light == null) {
			return;
		}
		env.answers().submit(level, visit.place, darkRules(visit.darkened == 0), (lvl, place, inside) -> {
			if (!lvl.isLoaded(light) || !isLight(lvl.getBlockState(light)) || !takeable(lvl, light, player)) {
				return null;
			}
			return Answers.Edit.of(List.of(light), traces -> {
				boolean done = traces.batch(lvl, DARK).remove(light).commit();
				if (done) {
					visit.darkened++;
				}
				return done;
			});
		});
	}

	private static long delay(Visit visit, Sc16Config config) {
		double lo = Math.max(1.0, Math.min(config.darkEveryMin, config.darkEveryMax));
		double hi = Math.max(lo, config.darkEveryMax);
		double t = Hash.unit(Hash.of(visit.player.getLeastSignificantBits(), visit.darkened, visit.entered));
		return Math.round((lo + (hi - lo) * t) * 20);
	}

	/** The light 16c takes next for this player, or null: a player's, in the core or the pit, above them, far enough. */
	@Nullable BlockPos nextLight(ServerLevel level, PyramidShape shape, ServerPlayer player) {
		BlockPos center = new BlockPos(shape.centerX(), shape.baseY(), shape.centerZ());
		List<BlockPos> found = new ArrayList<>();
		for (BlockPos pos : env.lights().placed(level, center, shape.halfWidth() + 1)) {
			if ((shape.core(pos.getX(), pos.getY(), pos.getZ()) || shape.pit(pos.getX(), pos.getY(), pos.getZ())) && pos.getY() > player.getBlockY()
					&& isLight(level.getBlockState(pos)) && takeable(level, pos, player)) {
				found.add(pos);
			}
		}
		Vec3 at = player.position();
		return found.stream().max(Comparator.comparingDouble((BlockPos pos) -> Vec3.atCenterOf(pos).distanceToSqr(at))).orElse(null);
	}

	/** Far enough from the player, never within {@value #DARK_NEAR} of anyone, never anyone's last light within {@value #LAST_LIGHT}. */
	boolean takeable(ServerLevel level, BlockPos light, ServerPlayer target) {
		Vec3 center = Vec3.atCenterOf(light);
		if (center.distanceTo(target.position()) < DARK_FAR) {
			return false;
		}
		for (ServerPlayer player : env.people().online(level.getServer())) {
			if (player.isSpectator() || player.level() != level) {
				continue;
			}
			double distance = center.distanceTo(player.position().add(0, 1, 0));
			if (distance < DARK_NEAR + 1) {
				return false;
			}
			if (distance <= LAST_LIGHT && !otherLightNear(level, player.blockPosition(), light)) {
				return false;
			}
		}
		return true;
	}

	/** True if a light other than {@code except} stands within {@value #LAST_LIGHT} of {@code at}. */
	private static boolean otherLightNear(ServerLevel level, BlockPos at, BlockPos except) {
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-LAST_LIGHT, -LAST_LIGHT, -LAST_LIGHT), at.offset(LAST_LIGHT, LAST_LIGHT, LAST_LIGHT))) {
			if (!pos.equals(except) && level.getBlockState(pos).getLightEmission() > 0) {
				return true;
			}
		}
		return false;
	}

	/** A torch or a lantern (of any kind but a redstone torch, a part of a player's circuit: R12-26). */
	public static boolean isLight(BlockState state) {
		return state.getBlock() instanceof BaseTorchBlock && !(state.getBlock() instanceof net.minecraft.world.level.block.RedstoneTorchBlock)
				|| state.getBlock() instanceof LanternBlock;
	}

	// --- the glimpse ---

	private void glimpse(ServerLevel level, Visit visit, PyramidShape shape, ServerPlayer player, int steps, Sc16Config config) {
		if (visit.glimpsed || visit.darkened < config.glimpseAfter || steps <= (shape.pitDepth() + 1) / 2) {
			return;
		}
		Figure figure = env.figure().get();
		for (int up = GLIMPSE_MIN; up <= GLIMPSE_MAX; up++) {
			for (Vec3 feet : spots(shape, steps - up, player)) {
				if (!figure.canGlimpse(player, feet)) {
					continue;
				}
				Vec3 eye = player.position();
				float yaw = (float) Math.toDegrees(Math.atan2(-(eye.x - feet.x), eye.z - feet.z));
				if (figure.glimpse(player, feet, yaw) == FireResult.FIRED) {
					visit.glimpsed = true;
					A1016_02.LOGGER.debug("[a1016] s2: 16c glimpse {} steps up", up);
				}
				return;
			}
		}
	}

	/** The feet spots on the pit's ring {@code stepsDown} down: the middles and corners of its four sides, those facing the player first. */
	static List<Vec3> spots(PyramidShape shape, int stepsDown, ServerPlayer player) {
		int ring = shape.ringAtSteps(stepsDown);
		List<Vec3> out = new ArrayList<>();
		if (ring < 1) {
			return out;
		}
		int y = shape.baseY() - stepsDown;
		int[][] offsets = {{ring, 0}, {-ring, 0}, {0, ring}, {0, -ring}, {ring, ring}, {ring, -ring}, {-ring, ring}, {-ring, -ring}};
		for (int[] o : offsets) {
			out.add(new Vec3(shape.centerX() + o[0] + 0.5, y, shape.centerZ() + o[1] + 0.5));
		}
		Vec3 at = player.position();
		Vec3 look = player.getLookAngle();
		out.sort(Comparator.comparingDouble((Vec3 spot) -> -spot.subtract(at).normalize().dot(look)));
		return out;
	}

	// --- live ---

	private static List<BlockPos> livePlaced(ServerLevel level, BlockPos center, int radius) {
		return Services.watch().placedNear(level, center, radius, PyramidAnswers::isLight);
	}

	/** The shape of a recorded pyramid (rebuilt once from its start over the noise, kept for the session). */
	private static Function<Remains.Place, @Nullable PyramidShape> liveShapes(ServerLevel level) {
		return place -> {
			PyramidShape cached = LIVE_SHAPES.get(place);
			if (cached != null) {
				return cached;
			}
			Sc2Data.Known known = RemainsImpl.LIVE.record(place).orElse(null);
			Sc2Kind kind = known == null ? null : Sc2Kind.get(known.kind());
			if (kind == null || !(kind.shape(known.start(), Terrains.noise(level)) instanceof PyramidShape shape)) {
				return null;
			}
			LIVE_SHAPES.put(place, shape);
			return shape;
		};
	}

	/** The live shape of a recorded pyramid, or null. Server thread. */
	public static @Nullable PyramidShape shapeOf(ServerLevel level, Remains.Place place) {
		return liveShapes(level).apply(place);
	}

	/** Server stop and switch off: the visits and the shapes go. */
	public static void reset() {
		LIVE.clear();
		LIVE_SHAPES.clear();
	}

	private static Vec3 middle(List<BlockPos> cells) {
		double x = 0;
		double y = 0;
		double z = 0;
		for (BlockPos cell : cells) {
			x += cell.getX() + 0.5;
			y += cell.getY() + 0.5;
			z += cell.getZ() + 0.5;
		}
		return new Vec3(x / cells.size(), y / cells.size(), z / cells.size());
	}
}
