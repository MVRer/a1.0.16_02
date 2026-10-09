package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Goes under (D-030): a sighting that would end with him walking or running off may instead end with him digging
 * straight down where he stands. He faces down and digs a 1x1 shaft, about {@code goUnderDigSeconds} (0.4 s) a block,
 * with the arm swing and nothing else (no sound, no particles), sinking with it 4 to 6 blocks. Then the top 2 blocks
 * of the shaft are put back over him and he is gone once nobody can see him.
 *
 * <p>It is the one change he makes in view, and only through {@link TraceService}'s figure dig: one
 * {@link TraceService#startFigureDig} per exit, {@link TraceService#figureDig} for each block, and
 * {@link TraceService#figureFill} with entries of that same dig ({@link TraceService#figureDug}), never a state of
 * its own. Core only checks that a fill is within 3 blocks of the column horizontally; here every dig and every fill
 * is in his own column, and fills only go into the top 2 blocks of the shaft he dug.
 *
 * <p>The clue: if the top was grass (or podzol, mycelium), the dirt dug from under it goes back on top instead, so the
 * patch comes back as bare dirt. The grass block itself stays dug (in the ledger, for Ending D). The block under the
 * top is never sand or gravel (it would fall into the open shaft).
 *
 * <p>Only natural ground: dirt, grass, sand, gravel, stone (and the sandstone under sand), with no fluid in or beside
 * the shaft, no block entity, nothing a player placed, nothing protected; only in the overworld (never the End's void,
 * never netherrack). Below the 2 blocks put back, the shaft's walls are opaque, so once covered he is shut in.
 */
public final class GoUnder {
	/** The cause of his dig; ledger entries carry {@code entity:goes_under/<dig id>}. */
	public static final String CAUSE = "entity:goes_under";
	/** Covered by 2 blocks with his head under them: his feet at least 4 blocks down. */
	public static final int MIN_DEPTH = 4;
	public static final int MAX_DEPTH = 8;
	/** Blocks put back over him: the top 2 of the shaft. */
	public static final int COVER = 2;

	/**
	 * One planned shaft.
	 *
	 * @param top   the ground block he stands on, the first one dug
	 * @param depth blocks dug; his feet end {@code depth - 1} blocks under {@code top}
	 */
	public record Plan(BlockPos top, int depth) {
		/** True if {@code pos} is one of the shaft's blocks: his column, from {@code top} down {@code depth} blocks. */
		public boolean inShaft(BlockPos pos) {
			return pos.getX() == top.getX() && pos.getZ() == top.getZ() && pos.getY() <= top.getY() && pos.getY() > top.getY() - depth;
		}

		/** True if {@code pos} is one of the blocks put back: the top {@link #COVER} of the shaft. */
		public boolean inCover(BlockPos pos) {
			return inShaft(pos) && pos.getY() > top.getY() - COVER;
		}

		/** Where the blocks go back, in order: the lower one first, so the top one has ground under it. */
		public List<BlockPos> cover() {
			List<BlockPos> out = new ArrayList<>(COVER);
			for (int i = COVER - 1; i >= 0; i--) {
				out.add(top.below(i));
			}
			return out;
		}

		/** The block his feet stand in once he has sunk. */
		public BlockPos bottom() {
			return top.below(depth - 1);
		}
	}

	/** One block going back: {@code item}'s block (a dug block of this shaft) into {@code target}. */
	public record Fill<T>(T item, BlockPos target) {
	}

	/** Whether the column under a figure allows going under, and how deep it can go. */
	public record Check(int maxDepth, Optional<String> refusal) {
		public boolean ok() {
			return refusal.isEmpty();
		}
	}

	// --- the rules (no state) ---

	/** Ground he may dig: dirt, grass, sand, gravel and stone, the natural kinds only. Never netherrack or end stone. */
	public static boolean diggable(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
				|| state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
				|| state.is(Blocks.STONE) || state.is(Blocks.GRANITE) || state.is(Blocks.DIORITE) || state.is(Blocks.ANDESITE);
	}

	/** Grass and its kin: the top that comes back as bare dirt. */
	static boolean grassy(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM);
	}

	static boolean bareDirt(BlockState state) {
		return state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT);
	}

	/** Sand, gravel: would fall into the open shaft under it. */
	static boolean falls(BlockState state) {
		return state.getBlock() instanceof Fallable;
	}

	/** Only the overworld: never the End (the void), never the Nether (netherrack, shrine imagery). */
	public static boolean dimensionAllows(Level level) {
		return level.dimension() == Level.OVERWORLD;
	}

	/**
	 * Checks the column under {@code top} (the ground block he stands on) for a shaft of {@code minDepth} to
	 * {@code maxDepth} blocks: every block diggable, dry, without a block entity, not placed by a player, not
	 * protected; no fluid beside it; opaque walls below the cover; sturdy dry ground under the bottom; room over the
	 * top (air or a plant); and blocks to cover him with. Changes nothing.
	 */
	public static Check check(ServerLevel level, BlockPos top, int minDepth, int maxDepth) {
		if (!dimensionAllows(level)) {
			return new Check(0, Optional.of("only in the overworld"));
		}
		int lo = Math.clamp(minDepth, MIN_DEPTH, MAX_DEPTH);
		int hi = Math.clamp(maxDepth, lo, MAX_DEPTH);
		BlockState above = level.getBlockState(top.above());
		if (!above.isAir() && !(above.canBeReplaced() && above.getFluidState().isEmpty() && !above.hasBlockEntity())) {
			return new Check(0, Optional.of("something stands on the ground there: " + name(above)));
		}
		PlayerWatch watch = Services.watch();
		List<BlockState> column = new ArrayList<>();
		String stop = null;
		for (int k = 0; k < hi; k++) {
			BlockPos pos = top.below(k);
			String why = blockRefusal(level, watch, pos, k);
			if (why != null) {
				stop = why;
				break;
			}
			column.add(level.getBlockState(pos));
		}
		int best = 0;
		for (int d = Math.min(hi, column.size()); d >= lo; d--) {
			BlockPos floor = top.below(d);
			BlockState under = level.getBlockState(floor);
			if (level.isLoaded(floor) && under.getFluidState().isEmpty() && under.isFaceSturdy(level, floor, Direction.UP)
					&& !choose(new Plan(top, d), shaftOf(top, column.subList(0, d)), Dug::pos, Dug::state).isEmpty()) {
				best = d;
				break;
			}
		}
		if (best == 0) {
			return new Check(0, Optional.of(stop != null && column.size() < lo ? stop : "no ground to stand on or nothing to cover him with " + lo + " to " + hi + " down"));
		}
		return new Check(best, Optional.empty());
	}

	/** Why the {@code k}-th block of the shaft (0 = top) cannot be dug, or null. */
	private static @Nullable String blockRefusal(ServerLevel level, PlayerWatch watch, BlockPos pos, int k) {
		if (!level.isLoaded(pos)) {
			return "not loaded";
		}
		BlockState state = level.getBlockState(pos);
		if (!diggable(state)) {
			return "not diggable: " + name(state) + " at " + k + " down";
		}
		if (!state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) {
			return "fluid or block entity at " + k + " down";
		}
		if (watch.wasPlacedByPlayer(level, pos)) {
			return "placed by a player at " + k + " down";
		}
		if (Services.protectedAreas().isProtected(level.dimension(), pos)) {
			return "protected";
		}
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos n = pos.relative(dir);
			if (!level.isLoaded(n)) {
				return "not loaded";
			}
			BlockState side = level.getBlockState(n);
			if (!side.getFluidState().isEmpty()) {
				return "fluid beside the shaft at " + k + " down";
			}
			if (k >= COVER && !side.isSolidRender()) {
				return "the shaft would be open to the side at " + k + " down";
			}
		}
		return null;
	}

	private static String name(BlockState state) {
		return state.getBlock().getDescriptionId().replace("block.minecraft.", "");
	}

	/** A dug (or to be dug) block of the shaft. */
	record Dug(BlockPos pos, BlockState state) {
	}

	private static List<Dug> shaftOf(BlockPos top, List<BlockState> states) {
		List<Dug> out = new ArrayList<>(states.size());
		for (int k = 0; k < states.size(); k++) {
			out.add(new Dug(top.below(k), states.get(k)));
		}
		return out;
	}

	/** A plan at {@code top}, as deep as the column allows within {@code depths} (min, max), the depth picked at random. */
	public static Optional<Plan> plan(ServerLevel level, BlockPos top, int[] depths, RandomSource random) {
		Check check = check(level, top, depths[0], depths[1]);
		if (!check.ok()) {
			return Optional.empty();
		}
		int lo = Math.clamp(depths[0], MIN_DEPTH, check.maxDepth());
		int depth = lo + random.nextInt(check.maxDepth() - lo + 1);
		// A shallower depth must still leave something to cover him with; the check found the deepest that does.
		for (int d = depth; d <= check.maxDepth(); d++) {
			if (check(level, top, d, d).ok()) {
				return Optional.of(new Plan(top.immutable(), d));
			}
		}
		return Optional.of(new Plan(top.immutable(), check.maxDepth()));
	}

	/**
	 * Which dug blocks go back where (pure). Only blocks of this shaft ({@link Plan#inShaft}) are used, and only the
	 * top {@link #COVER} positions of the shaft are filled, lower first. The top gets a bare dirt block of the shaft if
	 * the top was grassy (the clue), else its own block. The one under it gets the nearest other block that does not
	 * fall, grass last. Empty if there is nothing to cover with.
	 *
	 * @param dug every block this dig took that is still missing (for a live dig, {@link TraceService#figureDug})
	 */
	public static <T> List<Fill<T>> choose(Plan plan, List<T> dug, Function<T, BlockPos> posOf, Function<T, @Nullable BlockState> stateOf) {
		List<T> own = dug.stream().filter(t -> stateOf.apply(t) != null && plan.inShaft(posOf.apply(t))).toList();
		BlockPos top = plan.top();
		T topItem = own.stream().filter(t -> posOf.apply(t).equals(top)).findFirst().orElse(null);
		T forTop = null;
		if (topItem != null && grassy(stateOf.apply(topItem))) {
			forTop = own.stream().filter(t -> bareDirt(stateOf.apply(t))).max(Comparator.comparingInt(t -> posOf.apply(t).getY())).orElse(null);
		}
		if (forTop == null) {
			forTop = topItem;
		}
		if (forTop == null) {
			return List.of();
		}
		T chosenTop = forTop;
		BlockPos lower = top.below(1);
		T forLower = own.stream().filter(t -> t != chosenTop && !falls(stateOf.apply(t)))
				.min(Comparator.<T>comparingInt(t -> grassy(stateOf.apply(t)) ? 1 : 0)
						.thenComparingInt(t -> Math.abs(posOf.apply(t).getY() - lower.getY()))
						.thenComparingInt(t -> -posOf.apply(t).getY()))
				.orElse(null);
		if (forLower == null) {
			return List.of();
		}
		return List.of(new Fill<>(forLower, lower), new Fill<>(chosenTop, top));
	}

	/** {@link #choose} over ledger entries. */
	public static List<Fill<TraceLedger.Entry>> chooseEntries(Plan plan, List<TraceLedger.Entry> dug) {
		return choose(plan, dug, e -> e.pos().pos(), e -> e.state().orElse(null));
	}

	// --- one figure's dig ---

	/** Where one dig stands. */
	public enum Status {
		/** Getting over the column's center. */
		CENTERING,
		DIGGING,
		COVERING,
		/** Covered: gone once out of view. */
		COVERED,
		/** Gave up before digging anything: he leaves the ordinary way. */
		ABANDONED,
		/** Gave up with a hole dug: he waits in it until out of view, then the shaft is put back as it was. */
		STUCK,
		/** Removed with the shaft open: the blocks went back where they came from ({@link #putBack}). */
		PUT_BACK
	}

	/** Getting over the center of the column takes a few ticks; longer than this and he leaves the ordinary way. */
	private static final int CENTERING_MAX_TICKS = 40;

	private final Plan plan;
	private final TraceService.FigureDig dig;
	private Status status = Status.CENTERING;
	private int dug;
	private int timer;
	private List<Fill<TraceLedger.Entry>> fills = List.of();
	private int filled;
	/** Every block he took, top first, as it was (for tests and the info line). */
	private final List<Dug> taken = new ArrayList<>();

	private GoUnder(Plan plan, TraceService.FigureDig dig) {
		this.plan = plan;
		this.dig = dig;
	}

	/** Starts one dig at {@code plan}: a new figure-dig session in core. Changes nothing yet. */
	static GoUnder begin(ServerLevel level, Plan plan) {
		return new GoUnder(plan, Services.traces().startFigureDig(level, plan.top(), CAUSE));
	}

	public Plan plan() {
		return plan;
	}

	public TraceService.FigureDig dig() {
		return dig;
	}

	public Status status() {
		return status;
	}

	public int dugCount() {
		return dug;
	}

	public int filledCount() {
		return filled;
	}

	/** The blocks he dug, top first, with the state each had. */
	public List<Dug> taken() {
		return List.copyOf(taken);
	}

	/** True while blocks of the shaft are missing and it is not covered: removing him now must put them back. */
	boolean open() {
		return dug > 0 && status != Status.COVERED && status != Status.PUT_BACK;
	}

	/**
	 * One tick of the dig. He holds over the column's center, faces down, and every {@code goUnderDigSeconds} (once
	 * he has landed) swings and digs the next block. Then he faces up and puts the top 2 back, one per interval, once
	 * he is under them and nothing living is in the way.
	 */
	Status tick(HimEntity him, ServerLevel level, EntityConfig config) {
		Vec3 center = Vec3.atBottomCenterOf(plan.top());
		if (status == Status.COVERED || status == Status.ABANDONED || status == Status.STUCK || status == Status.PUT_BACK) {
			him.holdInShaft(center, false);
			return status;
		}
		long interval = Math.max(2, ModConfig.realTicks(config.goUnderDigSeconds));
		long swingAt = Math.max(1, interval - 3);
		switch (status) {
			case CENTERING -> {
				double off = him.holdInShaft(center, false);
				if (off < 0.02 && him.onGround()) {
					him.snapToColumn(center);
					status = Status.DIGGING;
					timer = 0;
				} else if (++timer > CENTERING_MAX_TICKS) {
					giveUp("could not get over the column");
				}
			}
			case DIGGING -> {
				him.holdInShaft(center, false);
				if (dug >= plan.depth()) {
					status = Status.COVERING;
					timer = 0;
					break;
				}
				// Each block once he stands on it: his feet in the block above it.
				boolean landed = him.onGround() && him.getY() <= plan.top().getY() + 1 - dug + 0.01;
				if (!landed) {
					break;
				}
				timer++;
				if (timer == swingAt) {
					him.swingArm();
				}
				if (timer >= interval) {
					BlockPos pos = plan.top().below(dug);
					BlockState state = level.getBlockState(pos);
					if (!plan.inShaft(pos) || !diggable(state) || !Services.traces().figureDig(level, dig, pos)) {
						giveUp("could not dig " + pos.toShortString());
						break;
					}
					taken.add(new Dug(pos.immutable(), state));
					dug++;
					timer = 0;
				}
			}
			case COVERING -> {
				him.holdInShaft(center, true);
				if (fills.isEmpty()) {
					fills = chooseEntries(plan, Services.traces().figureDug(level, dig));
					if (fills.size() != COVER) {
						giveUp("nothing to cover him with");
						break;
					}
				}
				Fill<TraceLedger.Entry> next = fills.get(filled);
				// His whole body under the block going back, on the ground, and nobody else in it.
				boolean under = him.onGround() && him.getBoundingBox().maxY <= next.target().getY() + 1.0E-3;
				if (!under || somethingIn(level, him, next.target())) {
					timer = 0;
					break;
				}
				timer++;
				if (timer == swingAt) {
					him.swingArm();
				}
				if (timer >= interval) {
					if (!plan.inCover(next.target()) || !plan.inShaft(next.item().pos().pos())
							|| !Services.traces().figureFill(level, dig, next.item(), next.target())) {
						giveUp("could not cover at " + next.target().toShortString());
						break;
					}
					filled++;
					timer = 0;
					if (filled >= fills.size()) {
						status = Status.COVERED;
					}
				}
			}
			default -> {
			}
		}
		return status;
	}

	private void giveUp(String why) {
		status = dug == 0 ? Status.ABANDONED : Status.STUCK;
		A1016_02.LOGGER.debug("[a1016] figure gave up going under at {}: {}", plan.top().toShortString(), why);
	}

	/** A living thing other than him (a player, a mob) in the block about to go back. */
	private static boolean somethingIn(ServerLevel level, HimEntity him, BlockPos pos) {
		AABB box = new AABB(pos);
		return !level.getEntitiesOfClass(LivingEntity.class, box, (Entity e) -> e != him && e.isAlive() && !e.isSpectator()).isEmpty();
	}

	/**
	 * He is being removed with the shaft still open (past the render distance, a debug clear, or he gave up): every
	 * block of the dig that is still missing goes back where it came from, the deepest first. Best effort; a block
	 * that cannot go back stays dug.
	 */
	void putBack(ServerLevel level) {
		if (!open()) {
			return;
		}
		TraceService traces = Services.traces();
		for (TraceLedger.Entry entry : traces.figureDug(level, dig)) { // newest (deepest) first
			BlockPos pos = entry.pos().pos();
			if (plan.inShaft(pos)) {
				traces.figureFill(level, dig, entry, pos);
			}
		}
		status = Status.PUT_BACK;
	}
}
