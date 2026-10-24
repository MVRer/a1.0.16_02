package com.forzacode.a1016_02.lore.sc2.s30;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import org.jspecify.annotations.Nullable;

/**
 * 30's rules (docs/SELF_CREATED_2.md, 30). The target, in the overworld and {@code awayDistance} from their base, carries
 * a filled map of the overworld, not locked, whose area holds the base and has the base drawn; nobody holds that map
 * (any copy of it: the same id) in a hand, has it in a cartography table or hangs it in a frame within
 * {@value #FRAME_REACH} of them, so the change is never seen happening (ARCH 3). The first form: the pixels of a square
 * {@code sizeMin} to {@code sizeMax} pixels a side round the base go blank, with vanilla's broken edge round it: the
 * house is gone from the map; vanilla draws it back when the map's holder walks there with it in hand. From
 * {@code showFromStage} (Telling) the second form when it can, half the time: one of his places the map's area holds and
 * the target never went into, mostly blank on the map, is drawn in from the world as it stands (vanilla's colours, as if
 * someone had walked there with the map), pixel by pixel as its chunks are loaded by anyone, for up to
 * {@value #DRAW_DAYS} in-game days. Each map takes each form once; every pixel changed is kept with what it held, and the
 * switch writes back each one vanilla has not drawn since. A copy of a map shares the change (one id); another map of the
 * same land does not. At most {@code maxPerWorld}; a forced fire skips the distance, the stage and the cap, never the
 * map rules. Server thread.
 */
public final class Maps {
	public static final String ID = "sc_30";
	/** A frame holding the map within this many blocks of anyone shows it. */
	public static final int FRAME_REACH = 64;
	/** A drawn place waits this many in-game days for its chunks. */
	public static final long DRAW_DAYS = 3;
	/** How often a waiting place is drawn on as its chunks load (server ticks). */
	public static final int DRAW_EVERY_TICKS = 200;
	/** The second form needs this share of its place's pixels blank now. */
	static final double BLANK_SHARE = 0.6;
	/** The first form needs this share of its square drawn now. */
	static final double DRAWN_SHARE = 0.5;
	/** The story's flags under which nothing of the layer happens. */
	static final List<String> QUIET_FLAGS = List.of("director:silence_forever", "ending:path=C", "ending:ended", "ending:d_complete",
			"ending:last_minute", "director:obeyed_after_stop");
	static final double COMBAT_SECONDS = 30.0;

	/**
	 * What 30 reads: the live world's, or a test's own.
	 *
	 * @param players     everyone on the server who could see a map (in a hand, a table or a frame)
	 * @param base        a player's base (live: {@code PlayerWatch.base})
	 * @param stage       the story's stage now
	 * @param sinceCombat ticks since a player's combat
	 */
	public record Env(MinecraftServer server, PlayerWatch watch, Function<MinecraftServer, List<ServerPlayer>> players,
			Function<ServerPlayer, Optional<GlobalPos>> base, Supplier<Stage> stage, Remains remains, S30Data data, Sc30Config config,
			ToLongFunction<ServerPlayer> sinceCombat) {
		public static Env live(MinecraftServer server) {
			PlayerWatch watch = Services.watch();
			return new Env(server, watch, s -> List.copyOf(s.getPlayerList().getPlayers()), watch::base, () -> HerobrineState.get(server).stage(),
					Services.remains(), S30Data.get(server), Sc30Config.get(), watch::ticksSinceCombat);
		}

		public HerobrineState state() {
			return HerobrineState.get(server);
		}
	}

	/** A map the target carries, its data and where the base is on it. */
	public record Candidate(ItemStack stack, MapId id, MapItemSavedData data, BlockPos base) {
	}

	/** What a fire did: the form and the map, or why nothing. */
	public record Outcome(com.forzacode.a1016_02.core.FireResult result, char form, @Nullable MapId map, int pixels, String reason) {
	}

	private Maps() {
	}

	// --- the gates ---

	/** Why nothing of the layer may happen now (the switch, the story), or empty. */
	static Optional<String> storyRefusal(MinecraftServer server) {
		if (!SelfCreated.enabled()) {
			return Optional.of("the layer is off");
		}
		HerobrineState state = HerobrineState.get(server);
		for (String flag : QUIET_FLAGS) {
			if (state.hasFlag(flag)) {
				return Optional.of("the story holds it (" + flag + ")");
			}
		}
		return Optional.empty();
	}

	/** The target's base in the overworld. */
	static Optional<BlockPos> base(Env env, ServerPlayer target) {
		return env.base().apply(target).filter(b -> b.dimension().equals(Level.OVERWORLD)).map(GlobalPos::pos);
	}

	/**
	 * Why it does not fit for the target now, or empty: the story, the overworld, combat, the cap and the distance from
	 * the base (unless forced), then a map that may change.
	 */
	public static Optional<String> refusal(Env env, ServerPlayer target, boolean forced) {
		Optional<String> story = storyRefusal(env.server());
		if (story.isPresent()) {
			return story;
		}
		if (target.level().dimension() != Level.OVERWORLD) {
			return Optional.of("not in the overworld");
		}
		if (!forced && env.sinceCombat().applyAsLong(target) < ModConfig.realTicks(COMBAT_SECONDS)) {
			return Optional.of("in combat lately");
		}
		if (!forced && env.data().count() >= env.config().maxPerWorld) {
			return Optional.of(env.data().count() + " already (at most " + env.config().maxPerWorld + ")");
		}
		Optional<BlockPos> base = base(env, target);
		if (base.isEmpty()) {
			return Optional.of("no base in the overworld");
		}
		if (!forced && flat(target.blockPosition(), base.get()) < env.config().awayDistance) {
			return Optional.of("within " + (int) env.config().awayDistance + " of the base");
		}
		List<Candidate> maps = candidates(env, target, base.get());
		if (maps.isEmpty()) {
			return Optional.of(whyNoMap(env, target, base.get()));
		}
		for (Candidate map : maps) {
			if (blankable(env, map) || drawable(env, target, map, forced).isPresent()) {
				return Optional.empty();
			}
		}
		return Optional.of("no map of theirs may change (each form once a map; the base drawn on it)");
	}

	static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	// --- the maps ---

	static @Nullable MapId idOf(ItemStack stack) {
		return stack.is(Items.FILLED_MAP) ? stack.get(DataComponents.MAP_ID) : null;
	}

	/** Every filled map the target carries (any slot), unlocked, of the overworld, holding the base, not shown to anyone now. */
	public static List<Candidate> candidates(Env env, ServerPlayer target, BlockPos base) {
		List<Candidate> found = new ArrayList<>();
		List<MapId> seen = new ArrayList<>();
		ServerLevel overworld = env.server().overworld();
		for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
			ItemStack stack = target.getInventory().getItem(i);
			MapId id = idOf(stack);
			if (id == null || seen.contains(id)) {
				continue;
			}
			seen.add(id);
			MapItemSavedData data = overworld.getMapData(id);
			if (data == null || data.locked || data.dimension != Level.OVERWORLD || !MapPixels.holds(data, base) || shown(env, id)) {
				continue;
			}
			found.add(new Candidate(stack, id, data, base));
		}
		return found;
	}

	private static String whyNoMap(Env env, ServerPlayer target, BlockPos base) {
		ServerLevel overworld = env.server().overworld();
		boolean any = false;
		for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
			MapId id = idOf(target.getInventory().getItem(i));
			if (id == null) {
				continue;
			}
			any = true;
			MapItemSavedData data = overworld.getMapData(id);
			if (data != null && !data.locked && data.dimension == Level.OVERWORLD && MapPixels.holds(data, base) && shown(env, id)) {
				return "their map of home is in sight (a hand, a table or a frame)";
			}
		}
		return any ? "no unlocked map of theirs holds their base" : "no filled map carried";
	}

	/** True if anyone holds a map with this id in a hand, has it in a cartography table, or a frame holding it is near them. */
	public static boolean shown(Env env, MapId id) {
		for (ServerPlayer player : env.players().apply(env.server())) {
			if (id.equals(idOf(player.getMainHandItem())) || id.equals(idOf(player.getOffhandItem()))) {
				return true;
			}
			if (player.containerMenu instanceof CartographyTableMenu menu) {
				for (Slot slot : menu.slots) {
					if (id.equals(idOf(slot.getItem()))) {
						return true;
					}
				}
			}
			if (!player.level().getEntitiesOfClass(ItemFrame.class, player.getBoundingBox().inflate(FRAME_REACH), f -> id.equals(idOf(f.getItem()))).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** The first form may go on this map: not had yet, the base drawn and half the square too. */
	static boolean blankable(Env env, Candidate map) {
		if (env.data().had(map.id().id(), S30Data.BLANK)) {
			return false;
		}
		int px = MapPixels.pixelX(map.data(), map.base().getX());
		int py = MapPixels.pixelY(map.data(), map.base().getZ());
		if (map.data().colors[MapPixels.index(px, py)] == 0) {
			return false;
		}
		int[] drawn = MapPixels.drawnIn(map.data().colors, px, py, Math.max(1, env.config().sizeMin));
		return drawn[1] > 0 && drawn[0] >= drawn[1] * DRAWN_SHARE;
	}

	/**
	 * The place the second form would draw on this map, if any: from the stage (unless forced), not had yet; a known
	 * place of his in the overworld the target never went into, on the map, most of its pixels blank now.
	 */
	static Optional<Remains.Place> drawable(Env env, ServerPlayer target, Candidate map, boolean forced) {
		if (!forced && env.stage().get().level() < env.config().showFromStage) {
			return Optional.empty();
		}
		if (env.data().had(map.id().id(), S30Data.DRAWN)) {
			return Optional.empty();
		}
		ServerLevel overworld = env.server().overworld();
		Remains.Place best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Remains.Place place : env.remains().known(overworld)) {
			if (!place.dimension().equals(Level.OVERWORLD) || env.remains().visited(target.getUUID(), place)) {
				continue;
			}
			int[] px = MapPixels.pixelsOf(map.data(), place.box());
			if (px == null || blankShare(map.data(), px) < BLANK_SHARE) {
				continue;
			}
			double distance = place.distanceTo(map.base());
			if (distance < bestDistance) {
				best = place;
				bestDistance = distance;
			}
		}
		return Optional.ofNullable(best);
	}

	static double blankShare(MapItemSavedData data, int[] px) {
		int blank = 0;
		int all = 0;
		for (int y = px[1]; y <= px[3]; y++) {
			for (int x = px[0]; x <= px[2]; x++) {
				all++;
				if (data.colors[MapPixels.index(x, y)] == 0) {
					blank++;
				}
			}
		}
		return all == 0 ? 0 : (double) blank / all;
	}

	/** The drawn place's margin round its box, in pixels: about 24 blocks, 1 to 8 pixels. */
	static int margin(MapItemSavedData data) {
		return Math.max(1, Math.min(8, 24 / MapPixels.blocks(data)));
	}

	// --- the change ---

	/**
	 * The card's work: the gates, a map, the form (the second half the time when it can, else the first), then the
	 * change; counted once.
	 */
	public static Outcome fire(Env env, ServerPlayer target, boolean forced, RandomSource random) {
		Optional<String> refused = refusal(env, target, forced);
		if (refused.isPresent()) {
			return new Outcome(com.forzacode.a1016_02.core.FireResult.SKIPPED, ' ', null, 0, refused.get());
		}
		BlockPos base = base(env, target).orElseThrow();
		List<Candidate> maps = candidates(env, target, base);
		for (Candidate map : maps) {
			Optional<Remains.Place> place = drawable(env, target, map, forced);
			boolean blank = blankable(env, map);
			if (place.isPresent() && (!blank || random.nextBoolean())) {
				return draw(env, map, place.get());
			}
			if (blank) {
				return blank(env, map, random);
			}
		}
		return new Outcome(com.forzacode.a1016_02.core.FireResult.NO_SPOT, ' ', null, 0, "no map of theirs may change now");
	}

	/** The first form on this map now: the square round the base blank, every pixel kept. */
	public static Outcome blank(Env env, Candidate map, RandomSource random) {
		Sc30Config config = env.config();
		int lo = Math.max(1, Math.min(config.sizeMin, config.sizeMax));
		int hi = Math.max(lo, config.sizeMax);
		int size = lo + random.nextInt(hi - lo + 1);
		int px = MapPixels.pixelX(map.data(), map.base().getX());
		int py = MapPixels.pixelY(map.data(), map.base().getZ());
		List<MapPixels.Change> changes = MapPixels.blankSquare(map.data().colors, px, py, size);
		S30Data.Edit edit = env.data().edit(map.id().id(), S30Data.BLANK, GameClock.day(env.server()));
		apply(env, map.data(), edit, changes);
		env.data().counted();
		A1016_02.LOGGER.info("[a1016] lore: {} ({})", ID, changes.size());
		A1016_02.LOGGER.debug("[a1016] lore: {} on {}", ID, map.id().id());
		return new Outcome(com.forzacode.a1016_02.core.FireResult.FIRED, S30Data.BLANK, map.id(), changes.size(), "a square of " + size);
	}

	private static void apply(Env env, MapItemSavedData data, S30Data.Edit edit, List<MapPixels.Change> changes) {
		for (MapPixels.Change change : changes) {
			env.data().keep(edit, change.index(), change.before(), change.after());
			data.setColor(change.index() % MapPixels.SIZE, change.index() / MapPixels.SIZE, change.after());
		}
	}

	/** The second form on this map: the place waits for its chunks and is drawn on at once where they are loaded. */
	public static Outcome draw(Env env, Candidate map, Remains.Place place) {
		S30Data.Pending pending = new S30Data.Pending(map.id().id(), place.box(), margin(map.data()), GameClock.day(env.server()) + DRAW_DAYS);
		env.data().edit(map.id().id(), S30Data.DRAWN, GameClock.day(env.server()));
		env.data().wait(pending);
		env.data().counted();
		int drawn = drawOn(env, pending);
		A1016_02.LOGGER.info("[a1016] lore: {} (2) ({})", ID, drawn);
		A1016_02.LOGGER.debug("[a1016] lore: {} (2) on {}", ID, map.id().id());
		return new Outcome(com.forzacode.a1016_02.core.FireResult.FIRED, S30Data.DRAWN, map.id(), drawn, "a place " + (int) place.distanceTo(map.base())
				+ " from the base");
	}

	/**
	 * One pass of a waiting place: each of its pixels still blank whose chunks are loaded is drawn from the world as vanilla
	 * would draw it; nothing while that map is shown to anyone. It is done (and forgotten) once no pixel waits for a chunk,
	 * or past its day, or once its map is gone or locked. Returns the pixels drawn.
	 */
	public static int drawOn(Env env, S30Data.Pending pending) {
		ServerLevel overworld = env.server().overworld();
		MapId id = new MapId(pending.map());
		MapItemSavedData data = overworld.getMapData(id);
		if (data == null || data.locked || GameClock.day(env.server()) > pending.untilDay()) {
			env.data().done(pending);
			return 0;
		}
		if (shown(env, id)) {
			return 0;
		}
		S30Data.Edit edit = env.data().edit(pending.map(), S30Data.DRAWN, GameClock.day(env.server()));
		int drawn = 0;
		boolean waiting = false;
		for (int[] pixel : MapPixels.disc(data, pending.box(), pending.margin())) {
			int index = MapPixels.index(pixel[0], pixel[1]);
			if (data.colors[index] != 0) {
				continue;
			}
			Byte colour = MapPixels.colour(overworld, data, pixel[0], pixel[1]);
			if (colour == null) {
				waiting = true;
				continue;
			}
			if (colour != 0) {
				env.data().keep(edit, index, (byte) 0, colour);
				data.setColor(pixel[0], pixel[1], colour);
				drawn++;
			}
		}
		if (!waiting) {
			env.data().done(pending);
		}
		return drawn;
	}

	/** Every waiting place, one pass each (the tick; cheap when none waits). */
	public static void drawAll(Env env) {
		if (!SelfCreated.enabled()) {
			return;
		}
		for (S30Data.Pending pending : env.data().pending()) {
			drawOn(env, pending);
		}
	}

	/**
	 * The switch: every pixel changed written back to what it held, where it still holds what was written (vanilla has not
	 * drawn it since), then that change is forgotten; the waits are forgotten. A map in sight now ({@link #shown}) keeps
	 * its change for a later try. Returns the pixels written back.
	 */
	public static int restore(MinecraftServer server, S30Data data) {
		return restore(Env.live(server), data);
	}

	/** {@link #restore} with this env's view of who could see a map. */
	public static int restore(Env env, S30Data data) {
		ServerLevel overworld = env.server().overworld();
		int back = 0;
		data.forgetWaits();
		for (S30Data.Edit edit : data.edits()) {
			MapId id = new MapId(edit.map);
			MapItemSavedData map = overworld.getMapData(id);
			if (map == null) {
				data.forget(edit);
				continue;
			}
			if (shown(env, id)) {
				continue;
			}
			for (int i = 0; i < edit.size(); i++) {
				int index = edit.index(i);
				if (map.colors[index] == edit.after(i) && edit.after(i) != edit.before(i)) {
					// A drawn pixel holds vanilla's own colour: once anyone has been there, the holder may have drawn it for
					// real, so it stays (R12-40); a blanked one is never vanilla's and always goes back.
					if (edit.form == S30Data.DRAWN && visited(env, overworld, map, index, edit.day)) {
						continue;
					}
					map.setColor(index % MapPixels.SIZE, index / MapPixels.SIZE, edit.before(i));
					back++;
				}
			}
			data.forget(edit);
		}
		return back;
	}

	/**
	 * A participant was in (or next to) the chunk this map pixel shows on {@code since} or later ({@code PlayerWatch
	 * .lastVisitDay}; any day for a change kept before the day was).
	 */
	static boolean visited(Env env, ServerLevel level, MapItemSavedData map, int index, long since) {
		int bx = MapPixels.blockX(map, index % MapPixels.SIZE);
		int bz = MapPixels.blockZ(map, index / MapPixels.SIZE);
		long day = env.watch().lastVisitDay(level, new net.minecraft.world.level.ChunkPos(bx >> 4, bz >> 4));
		return day >= 0 && day >= since;
	}

	/** Debug lines for a target: the gate, each map carried and what each form would do. */
	public static List<String> describe(Env env, ServerPlayer target) {
		List<String> lines = new ArrayList<>();
		lines.add("30: " + env.data().count() + " of " + env.config().maxPerWorld + ", " + env.data().pending().size() + " drawing");
		lines.add("30 for " + target.getPlainTextName() + ": " + refusal(env, target, false).orElse("fits now"));
		lines.add("30 forced: " + refusal(env, target, true).orElse("would fit"));
		Optional<BlockPos> base = base(env, target);
		if (base.isPresent()) {
			for (Candidate map : candidates(env, target, base.get())) {
				lines.add(String.format(java.util.Locale.ROOT, "30: %s, scale %d, first form %s, second %s", map.id().key(), map.data().scale,
						blankable(env, map) ? "yes" : "no", drawable(env, target, map, true).map(p -> "at " + p.anchor().toShortString()).orElse("no")));
			}
		}
		return lines;
	}

	/** The second form for this target now on their first map that takes it, whatever the stage and the cap (debug). */
	public static Outcome forceDraw(Env env, ServerPlayer target) {
		Optional<BlockPos> base = base(env, target);
		if (base.isEmpty()) {
			return new Outcome(com.forzacode.a1016_02.core.FireResult.SKIPPED, ' ', null, 0, "no base in the overworld");
		}
		for (Candidate map : candidates(env, target, base.get())) {
			Optional<Remains.Place> place = drawable(env, target, map, true);
			if (place.isPresent()) {
				return draw(env, map, place.get());
			}
		}
		return new Outcome(com.forzacode.a1016_02.core.FireResult.NO_SPOT, ' ', null, 0, "no map of theirs holds a place of his they never went into");
	}

	/** The box a place covers, for tests and the gallery. */
	static BoundingBox boxOf(Remains.Place place) {
		return place.box();
	}
}
