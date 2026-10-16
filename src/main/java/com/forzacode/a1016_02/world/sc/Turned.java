package com.forzacode.a1016_02.world.sc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceBatch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 08 ({@code docs/SELF_CREATED.md}): one of the target's own placed things near their base faces the wrong way, the
 * way of their bed. Player-placed blocks within {@code baseRadius} of the base ({@code PlayerWatch.placedNear}) with a
 * horizontal facing (furnace, smoker, blast furnace, a single chest, a barrel facing sideways, carved pumpkin, jack
 * o'lantern, lectern, loom, stonecutter, beehive) or, with {@code signs}, a standing sign, banner or head by its
 * rotation; never a redstone part, a bed, a door, stairs or rails (they are not on the list). The new facing points
 * at the base's bed (the respawn point when it is a bed, else the base point); a block already facing it is passed
 * over. Each block out of every player's view, nobody within {@code nobodyWithin} (the target too, unless asleep:
 * the target's own rule is the next one), the target {@code awayDistance} away from it or asleep; {@code perFireTraces} in Traces, {@code perFireLater} from Proximity, one batch (a state
 * change of an existing block, ledgered CONVERT, cause {@value #CAUSE}; Ending D turns them back). Per base at most
 * {@code maxPerBase}, {@code minDaysApart} in-game days apart, saved in the tick it happens. Server thread.
 */
public final class Turned {
	public static final String ID = "sc_08";
	public static final String CAUSE = "world:sc08";
	public static final String SECTION = "sc08";

	/** {@code sections.sc08}. Version 1. */
	public static final class Config implements ModConfig.Versioned {
		public static final int CURRENT = 1;

		public int version = 0;
		public int baseRadius = 12;
		public double nobodyWithin = 16.0;
		public double awayDistance = 32.0;
		public int perFireTraces = 1;
		public int perFireLater = 2;
		public int minDaysApart = 2;
		public int maxPerBase = 8;
		public boolean signs = true;

		@Override
		public boolean upgrade() {
			if (version >= CURRENT) {
				return false;
			}
			version = CURRENT;
			return true;
		}
	}

	/** The blocks that turn by their horizontal facing. */
	private static final Set<Block> FACING_BLOCKS = Set.of(Blocks.FURNACE, Blocks.SMOKER, Blocks.BLAST_FURNACE, Blocks.CARVED_PUMPKIN,
			Blocks.JACK_O_LANTERN, Blocks.LECTERN, Blocks.LOOM, Blocks.STONECUTTER, Blocks.BEEHIVE);

	/**
	 * One placed block near the base and what happens to it.
	 *
	 * @param turned   its new state, or null if it never turns (not on the list, a double chest, already facing the bed)
	 * @param refusal  why it does not turn now, or null if it may
	 */
	public record Candidate(BlockPos pos, BlockState now, @Nullable BlockState turned, @Nullable String refusal) {
		public boolean ok() {
			return refusal == null;
		}
	}

	/** What a fire did: the blocks turned, or why none. */
	public record Outcome(FireResult result, List<BlockPos> turned, String reason) {
	}

	private Turned() {
	}

	public static Config config() {
		return ModConfig.section(SECTION, Config.class, Config::new);
	}

	// --- the rules (pure) ---

	/** True if this block is one that turns: on the list, a single chest, a barrel facing sideways, a standing rotation. */
	public static boolean turnable(BlockState state, boolean signs) {
		if (FACING_BLOCKS.contains(state.getBlock())) {
			return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING);
		}
		if (state.getBlock() instanceof ChestBlock) {
			return state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) == ChestType.SINGLE
					&& state.hasProperty(BlockStateProperties.HORIZONTAL_FACING);
		}
		if (state.is(Blocks.BARREL)) {
			return state.getValue(BlockStateProperties.FACING).getAxis().isHorizontal();
		}
		return signs && rotates(state);
	}

	private static boolean rotates(BlockState state) {
		Block block = state.getBlock();
		return (block instanceof StandingSignBlock || block instanceof BannerBlock || block instanceof SkullBlock)
				&& state.hasProperty(BlockStateProperties.ROTATION_16);
	}

	/**
	 * This block's state facing {@code target} (its front, its face or its text toward it), or null if it does not
	 * turn ({@link #turnable}), stands on the target's column, or already faces it. Signs and banners face where their
	 * rotation points; a head's face is the other way (vanilla places a head by the player's own yaw).
	 */
	public static @Nullable BlockState toward(BlockState state, BlockPos pos, Vec3 target, boolean signs) {
		if (!turnable(state, signs)) {
			return null;
		}
		double dx = target.x - (pos.getX() + 0.5);
		double dz = target.z - (pos.getZ() + 0.5);
		if (dx * dx + dz * dz < 0.25) {
			return null;
		}
		if (rotates(state)) {
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			int segment = RotationSegment.convertToSegment(state.getBlock() instanceof SkullBlock ? yaw + 180.0F : yaw);
			return segment == state.getValue(BlockStateProperties.ROTATION_16) ? null : state.setValue(BlockStateProperties.ROTATION_16, segment);
		}
		Direction facing = Direction.getApproximateNearest(dx, 0.0, dz);
		if (state.is(Blocks.BARREL)) {
			return state.getValue(BlockStateProperties.FACING) == facing ? null : state.setValue(BlockStateProperties.FACING, facing);
		}
		return state.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing ? null : state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
	}

	/**
	 * True if {@code now} is {@code old}'s block with nothing but its facing (or rotation) changed: a turned block the
	 * player has not touched since.
	 */
	public static boolean onlyFacingDiffers(BlockState now, BlockState old) {
		if (!now.is(old.getBlock())) {
			return false;
		}
		for (var property : List.of(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.FACING)) {
			if (now.hasProperty(property) && old.hasProperty(property)) {
				return now.setValue(property, old.getValue(property)) == old;
			}
		}
		if (now.hasProperty(BlockStateProperties.ROTATION_16) && old.hasProperty(BlockStateProperties.ROTATION_16)) {
			return now.setValue(BlockStateProperties.ROTATION_16, old.getValue(BlockStateProperties.ROTATION_16)) == old;
		}
		return false;
	}

	/**
	 * Where the turned things face: the base's bed (the base is the respawn point, so a bed when it is one: see
	 * {@link #isBed}), else the base point; either way the base's own cell.
	 */
	public static Vec3 bed(ServerLevel level, BlockPos base) {
		return Vec3.atCenterOf(base);
	}

	/** True if a bed stands at the base (the respawn point is a bed). */
	public static boolean isBed(ServerLevel level, BlockPos base) {
		return level.isLoaded(base) && level.getBlockState(base).getBlock() instanceof BedBlock;
	}

	/** How many blocks one fire turns at this stage. */
	public static int perFire(Stage stage, Config config) {
		return Math.max(0, stage.atLeast(Stage.PROXIMITY) ? config.perFireLater : config.perFireTraces);
	}

	/** Why this base takes no more turns now, or null: its cap, then (unless forced) the days since its last. */
	public static @Nullable String baseRefusal(ScMarks.Base marks, long today, Config config, boolean forced) {
		if (marks.turned() >= config.maxPerBase) {
			return marks.turned() + " turned at this base already (at most " + config.maxPerBase + ")";
		}
		if (!forced && marks.turnedDay() != ScMarks.NEVER && today - marks.turnedDay() < config.minDaysApart) {
			return "the last turn here was on day " + marks.turnedDay() + " (at least " + config.minDaysApart + " days apart)";
		}
		return null;
	}

	// --- live ---

	/** The target's base in this level, if any. */
	public static Optional<GlobalPos> baseOf(ScEnv env, ServerPlayer target, ServerLevel level) {
		return env.watch().base(target).filter(b -> b.dimension().equals(level.dimension()));
	}

	/**
	 * Every placed block near the base that turns, each with its refusal: protected, someone within
	 * {@code nobodyWithin} (anyone in the level, spectators included), the target nearer than {@code awayDistance} and
	 * awake (unless forced), or in view; then the ones on the list that do not turn (already facing the bed).
	 */
	public static List<Candidate> candidates(ScEnv env, ServerLevel level, ServerPlayer target, BlockPos base, Config config, boolean forced) {
		Vec3 bed = bed(level, base);
		boolean asleep = env.watch().isSleeping(target);
		// Asleep, the target's own rule lets the turn happen near them; everyone else still counts.
		List<ServerPlayer> people = asleep ? ScGates.without(env.people().in(level), target) : env.people().in(level);
		List<Candidate> found = new ArrayList<>();
		for (BlockPos pos : env.watch().placedNear(level, base, config.baseRadius, state -> turnable(state, config.signs))) {
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState now = level.getBlockState(pos);
			BlockState turned = toward(now, pos, bed, config.signs);
			String refusal = null;
			ServerPlayer near;
			if (turned == null) {
				refusal = "already faces the bed";
			} else if (env.protect().isProtected(level.dimension(), pos)) {
				refusal = "a protected area";
			} else if ((near = ScGates.within(people, List.of(pos), config.nobodyWithin)) != null) {
				refusal = ScGates.name(near) + " is within " + (int) config.nobodyWithin;
			} else if (!forced && !asleep && ScGates.horizontalSqr(target, pos) < config.awayDistance * config.awayDistance) {
				refusal = "the target is within " + (int) config.awayDistance + " and awake";
			} else if (!env.view().outOfView(level, List.of(pos))) {
				refusal = "in view";
			}
			found.add(new Candidate(pos.immutable(), now, turned, refusal));
		}
		return found;
	}

	/**
	 * The card's work for {@code target}: turns up to {@link #perFire} of the candidates that may turn, chosen at
	 * random, in one batch, and records them for the base. Stage-given so tests choose it.
	 */
	public static Outcome turn(ScEnv env, ServerPlayer target, Stage stage, RandomSource random, boolean forced) {
		return turn(env, target, stage, random, forced, config());
	}

	/** {@link #turn} with these numbers (tests). */
	public static Outcome turn(ScEnv env, ServerPlayer target, Stage stage, RandomSource random, boolean forced, Config config) {
		ServerLevel level = target.level();
		String refusal = ScGates.targetRefusal(env.state(), env.watch(), target, level, forced);
		if (refusal != null) {
			return new Outcome(FireResult.SKIPPED, List.of(), refusal);
		}
		Optional<GlobalPos> base = baseOf(env, target, level);
		if (base.isEmpty()) {
			return new Outcome(FireResult.SKIPPED, List.of(), "no base in this level");
		}
		long today = GameClock.day(env.server());
		String capped = baseRefusal(env.marks().base(base.get()), today, config, forced);
		if (capped != null) {
			return new Outcome(FireResult.SKIPPED, List.of(), capped);
		}
		List<Candidate> ok = new ArrayList<>(candidates(env, level, target, base.get().pos(), config, forced).stream().filter(Candidate::ok).toList());
		int count = Math.min(Math.min(perFire(stage, config), ok.size()), config.maxPerBase - env.marks().base(base.get()).turned());
		if (count <= 0) {
			return new Outcome(FireResult.NO_SPOT, List.of(), "nothing near the base may turn now");
		}
		Util.shuffle(ok, random);
		List<Candidate> chosen = ok.subList(0, count);
		TraceBatch batch = env.traces().batch(level, CAUSE);
		for (Candidate c : chosen) {
			batch.convert(c.pos(), c.turned());
		}
		if (!batch.commit()) {
			return new Outcome(FireResult.NO_SPOT, List.of(), "the turn was refused (in view)");
		}
		List<BlockPos> turned = chosen.stream().map(Candidate::pos).toList();
		// In this tick: a second card for this base (another player's) sees the cap and the day.
		env.marks().turned(base.get(), today, turned.size());
		A1016_02.LOGGER.info("[a1016] world: {} at {} ({} edits)", ID, base.get().pos().toShortString(), turned.size());
		return new Outcome(FireResult.FIRED, turned, "turned " + turned.size());
	}

	/** Debug lines: the base, its bed, its marks and every candidate with its refusal. */
	public static List<String> describe(ScEnv env, ServerPlayer target) {
		ServerLevel level = target.level();
		Config config = config();
		List<String> lines = new ArrayList<>();
		Optional<GlobalPos> base = baseOf(env, target, level);
		String gate = ScGates.targetRefusal(env.state(), env.watch(), target, level, false);
		lines.add("08: " + (gate == null ? "open" : gate));
		if (base.isEmpty()) {
			lines.add("08: no base in this level");
			return lines;
		}
		BlockPos at = base.get().pos();
		ScMarks.Base marks = env.marks().base(base.get());
		String capped = baseRefusal(marks, GameClock.day(env.server()), config, false);
		lines.add("08: base " + at.toShortString() + (isBed(level, at) ? " (a bed)" : " (no bed: the base point)") + ", turned " + marks.turned()
				+ " of " + config.maxPerBase + (marks.turnedDay() == ScMarks.NEVER ? "" : ", last on day " + marks.turnedDay())
				+ (capped == null ? "" : "; " + capped) + "; turns " + perFire(env.state().stage(), config) + " a fire");
		List<Candidate> all = candidates(env, level, target, at, config, false);
		if (all.isEmpty()) {
			lines.add("08: no placed block within " + config.baseRadius + " turns");
		}
		for (Candidate c : all) {
			lines.add("08: " + c.now().getBlock().getName().getString() + " at " + c.pos().toShortString() + ": " + (c.ok() ? "may turn" : c.refusal()));
		}
		return lines;
	}

	/** The card ({@value #ID}): MINOR, Traces, SCAR, VISITOR and COLLECTOR, no fake. */
	public static final class Card implements EventCard {
		@Override
		public String id() {
			return ID;
		}

		@Override
		public Tier tier() {
			return Tier.MINOR;
		}

		@Override
		public Stage earliestStage() {
			return Stage.TRACES;
		}

		@Override
		public Set<Habit> habits() {
			return Set.of(Habit.VISITOR, Habit.COLLECTOR);
		}

		@Override
		public Set<CardTag> tags() {
			return Set.of(CardTag.SCAR);
		}

		@Override
		public boolean hasFake() {
			return false;
		}

		/** Cheap: the gates, a base here, its caps, the target away from it or asleep, and a block that could turn. */
		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			ScEnv env = ScEnv.live(world.getServer());
			if (ScGates.targetRefusal(env.state(), env.watch(), player, world, false) != null) {
				return false;
			}
			Config config = config();
			Optional<GlobalPos> base = baseOf(env, player, world);
			if (base.isEmpty() || baseRefusal(env.marks().base(base.get()), GameClock.day(world.getServer()), config, false) != null) {
				return false;
			}
			BlockPos at = base.get().pos();
			if (!env.watch().isSleeping(player) && ScGates.horizontalSqr(player, at) < config.awayDistance * config.awayDistance) {
				return false;
			}
			Vec3 bed = bed(world, at);
			return env.watch().placedNear(world, at, config.baseRadius, state -> turnable(state, config.signs)).stream()
					.anyMatch(pos -> world.isLoaded(pos) && toward(world.getBlockState(pos), pos, bed, config.signs) != null);
		}

		@Override
		public FireResult fire(FireContext ctx) {
			ScEnv env = ScEnv.live(ctx.level().getServer());
			return turn(env, ctx.player(), env.state().stage(), ctx.random(), ctx.forced()).result();
		}
	}
}
