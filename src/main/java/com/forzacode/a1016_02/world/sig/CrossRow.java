package com.forzacode.a1016_02.world.sig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Signature;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.SignatureData;
import com.forzacode.a1016_02.world.WorldConfig;
import com.forzacode.a1016_02.world.WorldData;
import com.forzacode.a1016_02.world.gen.Blueprint;
import com.forzacode.a1016_02.world.gen.Builds;
import com.forzacode.a1016_02.world.gen.Hash;
import com.forzacode.a1016_02.world.gen.Vegetation;
import com.forzacode.a1016_02.world.live.LiveTerrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * "A row of crosses with a fresh one" (SIGNATURE): on a hilltop, one cross per "gone" name of F23 ({@link #GONE})
 * and a fresh one at the end. Every cross is moved material ({@code TraceService.move}, one out-of-view batch):
 * the old ones from blocks buried under the hill (the holes stay sealed underground, so their ground is
 * undisturbed), the fresh one from the top blocks of the ground right around it, which is the clue: the ground
 * around it was freshly dug. Nothing is written on them. At most once per world. Server thread only.
 */
public final class CrossRow {
	public static final String ID = "signature_cross_row";
	public static final String CAUSE = "world:cross_row";
	/** The row's CROSS sites are claimed with this: lore leaves them alone. */
	public static final String CLAIM = "world:cross_row";
	/** F23's "gone" names, one old cross each (in-game nothing names them). */
	public static final List<String> GONE = List.of("pk_alpha", "t0rch", "dyl4n_m", "oreo_tm");
	/** The old crosses' material comes from at least this deep under their ground (blocks of cover). */
	private static final int BURIED_MIN = 3;
	/** The fresh cross's ground is taken within this many blocks around it. */
	private static final int FRESH_REACH = 2;
	/** Hilltop: the ground this far out all around lies at least {@link #HILL_DROP} lower. */
	private static final int HILL_RING = 12;
	private static final int HILL_DROP = 2;

	/** One cross: its blocks bottom-up, then the arms. */
	public record Cross(BlockPos base, int height, boolean fresh, List<BlockPos> blocks) {
	}

	/** One block moved into a cross. */
	public record Move(BlockPos from, BlockPos to) {
	}

	/** A row ready to commit: the crosses in order (the fresh one last) and where each block comes from. */
	public record Plan(Direction along, List<Cross> crosses, List<Move> moves) {
	}

	/** Why the last plan failed (debug and tests). Server thread only. */
	private static String lastRefusal = "";

	private CrossRow() {
	}

	private static @Nullable Plan refuse(String why) {
		lastRefusal = why;
		return null;
	}

	/** Why the last {@link #plan} returned null. */
	public static String lastRefusal() {
		return lastRefusal;
	}

	// --- once per world ---

	public static @Nullable String refusal(SignatureData data) {
		return data.crossRow().map(row -> "the row of crosses already stands at " + row.crosses().getFirst().pos().toShortString()).orElse(null);
	}

	// --- planning ---

	/**
	 * Plans the row along {@code along} from {@code firstBase} (the air block above the first cross's ground):
	 * {@code GONE.size() + 1} Latin crosses (D-050, {@link Builds#latinCross}) {@code crossRowSpacing} apart, arms
	 * along the row. Each base sits on its own ground within a block of the first. Null if the ground, the room or
	 * the material is missing. Every block is moved local material: never glass, which is only ever left by others
	 * (D-051) and is not ground the row can take.
	 */
	public static @Nullable Plan plan(ServerLevel level, BlockPos firstBase, Direction along, long seed, WorldConfig config) {
		int count = GONE.size() + 1;
		int spacing = Math.max(3, config.crossRowSpacing);
		List<Cross> crosses = new ArrayList<>();
		Set<BlockPos> taken = new HashSet<>();
		for (int k = 0; k < count; k++) {
			BlockPos column = firstBase.relative(along, k * spacing);
			BlockPos base = baseNear(level, column.getX(), firstBase.getY(), column.getZ());
			if (base == null) {
				return refuse("no ground for cross " + k + " near " + column.toShortString());
			}
			int height = Builds.crossHeight(Hash.of(seed, k));
			List<BlockPos> blocks = Builds.latinCross(base, height, along.getAxis());
			for (BlockPos pos : blocks) {
				BlockState there = level.getBlockState(pos);
				if (!there.canBeReplaced() || !there.getFluidState().isEmpty() || level.getBlockEntity(pos) != null || !taken.add(pos)) {
					return refuse("no room for cross " + k + " at " + pos.toShortString());
				}
			}
			crosses.add(new Cross(base, height, k == count - 1, blocks));
		}
		List<Move> moves = new ArrayList<>();
		Set<BlockPos> used = new HashSet<>();
		// The old crosses: one buried material for all of them (the hill's own stone or dirt).
		Map<Block, List<BlockPos>> buried = new HashMap<>();
		for (Cross cross : crosses.subList(0, count - 1)) {
			collectBuried(level, cross.base().below(), config.crossRowMaterialRadius, config.crossRowMaterialDepth, buried);
		}
		Block material = buried.entrySet().stream().max(Comparator.comparingInt(e -> e.getValue().size())).map(Map.Entry::getKey).orElse(null);
		if (material == null) {
			return refuse("no buried material under the old crosses");
		}
		for (Cross cross : crosses.subList(0, count - 1)) {
			List<BlockPos> pool = new ArrayList<>(buried.get(material));
			pool.sort(Comparator.comparingDouble(p -> p.distSqr(cross.base())));
			for (BlockPos to : cross.blocks()) {
				BlockPos from = takeApart(pool, used);
				if (from == null) {
					return refuse("not enough buried " + material + " near the cross at " + cross.base().toShortString());
				}
				moves.add(new Move(from, to));
			}
		}
		// The fresh cross: the top of the ground right around it, dug just now.
		Cross fresh = crosses.getLast();
		Set<BlockPos> under = new HashSet<>();
		for (Cross cross : crosses) {
			for (BlockPos pos : cross.blocks()) {
				under.add(new BlockPos(pos.getX(), 0, pos.getZ()));
			}
		}
		List<BlockPos> surface = new ArrayList<>();
		for (int dx = -FRESH_REACH; dx <= FRESH_REACH; dx++) {
			for (int dz = -FRESH_REACH; dz <= FRESH_REACH; dz++) {
				int x = fresh.base().getX() + dx;
				int z = fresh.base().getZ() + dz;
				if (under.contains(new BlockPos(x, 0, z))) {
					continue; // never under a cross
				}
				BlockPos top = surfaceBlock(level, x, fresh.base().getY(), z);
				if (top != null) {
					surface.add(top);
				}
			}
		}
		surface.sort(Comparator.comparingDouble(p -> p.distSqr(fresh.base())));
		if (surface.size() < fresh.blocks().size()) {
			return refuse("only " + surface.size() + " ground blocks around the fresh cross");
		}
		for (int n = 0; n < fresh.blocks().size(); n++) {
			moves.add(new Move(surface.get(n), fresh.blocks().get(n)));
		}
		return new Plan(along, crosses, moves);
	}

	/** The air block above natural, sturdy, dry ground in this column, within a block of {@code y}; or null. */
	static @Nullable BlockPos baseNear(ServerLevel level, int x, int y, int z) {
		for (int dy : new int[] {0, 1, -1}) {
			BlockPos base = new BlockPos(x, y + dy, z);
			BlockPos ground = base.below();
			BlockState below = level.getBlockState(ground);
			BlockState at = level.getBlockState(base);
			if (at.canBeReplaced() && at.getFluidState().isEmpty() && below.isFaceSturdy(level, ground, Direction.UP) && below.getFluidState().isEmpty()
					&& !Vegetation.isTreePart(below) && !Services.watch().wasPlacedByPlayer(level, ground) && level.getBlockEntity(ground) == null) {
				return base;
			}
		}
		return null;
	}

	/** The top ground block of a column near {@code baseY} that can be dug out for the fresh cross, or null. */
	private static @Nullable BlockPos surfaceBlock(ServerLevel level, int x, int baseY, int z) {
		for (int y = baseY + 1; y >= baseY - 2; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(pos);
			BlockState above = level.getBlockState(pos.above());
			if (!state.isAir() && (above.isAir() || above.canBeReplaced() && above.getFluidState().isEmpty()) && level.getBlockEntity(pos.above()) == null) {
				return movableGround(level, pos, state) ? pos : null;
			}
		}
		return null;
	}

	private static boolean movableGround(ServerLevel level, BlockPos pos, BlockState state) {
		return state.isCollisionShapeFullBlock(level, pos) && state.getFluidState().isEmpty() && !state.hasBlockEntity()
				&& !(state.getBlock() instanceof Fallable) && !Vegetation.isTreePart(state) && !Services.watch().wasPlacedByPlayer(level, pos)
				&& (state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.SANDSTONE) || state.is(BlockTags.TERRACOTTA)
						|| state.is(Blocks.SNOW_BLOCK));
	}

	/** Buried natural ground blocks (six opaque neighbours) under {@code ground}, by block. */
	private static void collectBuried(ServerLevel level, BlockPos ground, int radius, int maxDepth, Map<Block, List<BlockPos>> into) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int depth = BURIED_MIN; depth <= Math.max(BURIED_MIN, maxDepth); depth++) {
					BlockPos pos = ground.offset(dx, -depth, dz);
					if (!level.isLoaded(pos) || pos.getY() <= level.getMinY()) {
						continue;
					}
					BlockState state = level.getBlockState(pos);
					if (!state.isSolidRender() || Blueprint.isOre(state) || state.is(Blocks.BEDROCK) || !movableGround(level, pos, state)
							|| !sealed(level, pos)) {
						continue;
					}
					List<BlockPos> list = into.computeIfAbsent(state.getBlock(), b -> new ArrayList<>());
					if (!list.contains(pos)) {
						list.add(pos);
					}
				}
			}
		}
	}

	private static boolean sealed(ServerLevel level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			BlockPos n = pos.relative(dir);
			if (!level.isLoaded(n) || !level.getBlockState(n).isSolidRender()) {
				return false;
			}
		}
		return true;
	}

	/** The first position of the pool not next to one already used (each hole stays a sealed pocket). */
	private static @Nullable BlockPos takeApart(List<BlockPos> pool, Set<BlockPos> used) {
		for (int n = 0; n < pool.size(); n++) {
			BlockPos pos = pool.get(n);
			boolean apart = !used.contains(pos);
			for (Direction dir : Direction.values()) {
				apart &= !used.contains(pos.relative(dir));
			}
			if (apart) {
				pool.remove(n);
				used.add(pos);
				return pos;
			}
		}
		return null;
	}

	/** Moves every block of the plan as one out-of-view batch and records the CROSS sites (claimed). */
	public static boolean commit(ServerLevel level, Plan plan, TraceService traces, SiteSink sites) {
		TraceBatch batch = traces.batch(level, CAUSE);
		for (Move move : plan.moves()) {
			batch.move(move.from(), move.to());
		}
		if (!batch.commit()) {
			return false;
		}
		for (Cross cross : plan.crosses()) {
			SiteRegistry.Site site = sites.record(SiteType.CROSS, level.dimension(), cross.base(), cross.height());
			if (site != null) {
				sites.claim(site, CLAIM);
			}
		}
		return true;
	}

	// --- live ---

	/** Builds the row now on a hilltop out of view, or fails (the director keeps the card for later). */
	public static Outcome start(MinecraftServer server, boolean forced) {
		SignatureData data = WorldData.get(server).signatures();
		String no = refusal(data);
		if (no != null) {
			return Outcome.failed(no);
		}
		HerobrineState state = HerobrineState.get(server);
		if (!forced && state.profile().signature() != Signature.CROSS_ROW) {
			return Outcome.failed("the row of crosses is not this world's signature (" + state.profile().signature() + ")");
		}
		Optional<ServerPlayer> subject = Services.watch().subject(server);
		if (subject.isEmpty() || subject.get().level().dimension() != Level.OVERWORLD) {
			return Outcome.later("the subject is not in the overworld");
		}
		ServerPlayer player = subject.get();
		ServerLevel level = player.level();
		WorldConfig config = WorldConfig.get();
		RandomSource random = RandomSource.create();
		Optional<BlockPos> base = Services.watch().base(player).filter(b -> b.dimension().equals(level.dimension())).map(GlobalPos::pos);
		int tried = 0;
		for (BlockPos top : hilltops(level, player, base.orElse(null), config, random)) {
			Direction along = Direction.from2DDataValue(random.nextInt(4));
			BlockPos first = top.relative(along.getOpposite(), GONE.size() / 2 * Math.max(3, config.crossRowSpacing));
			Plan plan = plan(level, first, along, random.nextLong(), config);
			if (plan == null) {
				plan = plan(level, top.relative(along.getClockWise().getOpposite(), GONE.size() / 2 * Math.max(3, config.crossRowSpacing)),
						along.getClockWise(), random.nextLong(), config);
			}
			if (plan == null || Services.protectedAreas().intersects(level.dimension(), box(plan))) {
				continue;
			}
			tried++;
			if (!commit(level, plan, Services.traces(), SiteSink.LIVE)) {
				continue; // in view
			}
			List<GlobalPos> bases = plan.crosses().stream().map(c -> GlobalPos.of(level.dimension(), c.base())).toList();
			data.setCrossRow(new SignatureData.CrossRow(bases, GameClock.day(server)));
			String where = String.format(Locale.ROOT, "row of %d crosses on the hilltop at %s, the fresh one at %s", bases.size(),
					top.toShortString(), plan.crosses().getLast().base().toShortString());
			A1016_02.LOGGER.info("[a1016] world: {}", where);
			return Outcome.done(where);
		}
		return Outcome.later("no out-of-view hilltop with room for the row (" + tried + " planned row(s) were in view)");
	}

	private static BoundingBox box(Plan plan) {
		BoundingBox box = null;
		for (Move move : plan.moves()) {
			for (BlockPos pos : List.of(move.from(), move.to())) {
				box = box == null ? new BoundingBox(pos) : BoundingBox.encapsulating(box, new BoundingBox(pos));
			}
		}
		return box;
	}

	/**
	 * Hilltops in loaded land {@code crossRowMin..MaxDistance} from the player, behind them first (outside the view
	 * cone), away from the base: the air above ground whose ring {@value #HILL_RING} blocks out lies lower all round.
	 */
	static List<BlockPos> hilltops(ServerLevel level, ServerPlayer player, @Nullable BlockPos base, WorldConfig config, RandomSource random) {
		LiveTerrain terrain = new LiveTerrain(level);
		int max = Math.min(config.crossRowMaxDistance, level.getServer().getPlayerList().getViewDistance() * 16 - 16);
		int min = config.crossRowMinDistance;
		List<BlockPos> found = new ArrayList<>();
		if (max <= min) {
			return found;
		}
		Vec3 eye = player.getEyePosition();
		double halfCone = ModConfig.pacing().viewConeDegrees / 2.0;
		for (int attempt = 0; attempt < 160 && found.size() < 8; attempt++) {
			double rel = halfCone + 15 + random.nextDouble() * Math.max(1, 360 - 2 * (halfCone + 15));
			double angle = Math.toRadians(player.getYRot() + rel);
			int d = Mth.nextInt(random, min, max);
			int x = Mth.floor(eye.x - Math.sin(angle) * d);
			int z = Mth.floor(eye.z + Math.cos(angle) * d);
			if (!terrain.loaded(x, z) || terrain.wet(x, z)) {
				continue;
			}
			int y = terrain.ground(x, z);
			if (base != null && base.distSqr(new BlockPos(x, base.getY(), z)) < (double) config.crossRowBaseClearance * config.crossRowBaseClearance) {
				continue;
			}
			boolean hill = true;
			for (int i = 0; i < 8 && hill; i++) {
				double a = Math.PI / 4 * i;
				int nx = x + (int) Math.round(Math.cos(a) * HILL_RING);
				int nz = z + (int) Math.round(Math.sin(a) * HILL_RING);
				hill = terrain.loaded(nx, nz) && terrain.ground(nx, nz) <= y - HILL_DROP;
			}
			if (hill) {
				found.add(new BlockPos(x, y + 1, z));
			}
		}
		return found;
	}

	/** Debug: one line about the row. */
	public static String status(MinecraftServer server) {
		return WorldData.get(server).signatures().crossRow().map(row -> String.format(Locale.ROOT, "row of crosses: %d at %s (day %d), fresh one at %s",
				row.crosses().size(), row.crosses().getFirst().pos().toShortString(), row.day(), row.crosses().getLast().pos().toShortString()))
				.orElse("row of crosses: not yet");
	}
}
